package com.arktools.xiao.domain.club

import com.arktools.xiao.domain.engine.GameBalanceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 社团活动管理系统（审批制）
 * - 学生自动提交社团创建申请
 * - 校长签字审批或驳回
 * - 社团定期举办活动
 * - 社团影响学生满意度和学校声誉
 * - 社团竞赛获奖增加声誉
 */
@Singleton
class ClubManager @Inject constructor() {

    private val _clubs = MutableStateFlow<List<Club>>(emptyList())
    val clubs: StateFlow<List<Club>> = _clubs.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<ClubEvent>>(emptyList())
    val recentEvents: StateFlow<List<ClubEvent>> = _recentEvents.asStateFlow()

    private val _pendingApplications = MutableStateFlow<List<ClubApplication>>(emptyList())
    val pendingApplications: StateFlow<List<ClubApplication>> = _pendingApplications.asStateFlow()

    /** 当前学校等级（由外部在 restore/tick 时更新） */
    var currentCampusLevel: Int = 1

    companion object {
        const val MAX_RECENT_EVENTS = 20
        const val APPLICATION_TIMEOUT_DAYS = 20

        /** 根据学校等级获取社团数量上限 */
        fun getMaxClubs(campusLevel: Int): Int =
            GameBalanceConfig.getMaxClubsForLevel(campusLevel)

        /** 根据学校等级获取待审批申请上限 */
        fun getMaxPendingApplications(campusLevel: Int): Int = when (campusLevel) {
            1 -> 1
            2 -> 2
            3 -> 3
            4 -> 4
            5 -> 5
            6 -> 5
            else -> 1
        }
    }

    /**
     * 生成学生社团申请（每月调用，返回新生成的申请列表用于发送审批事件）
     * @param campusLevel 学校等级(1-6)，用于限制社团上限和申请频率
     */
    fun generateApplications(totalStudents: Int, schoolReputation: Long, campusLevel: Int = 1): List<ClubApplication> {
        val maxClubs = getMaxClubs(campusLevel)
        val maxPending = getMaxPendingApplications(campusLevel)

        if (_clubs.value.size >= maxClubs) return emptyList()
        if (_pendingApplications.value.size >= maxPending) return emptyList()
        if (totalStudents < 20) return emptyList()

        // 申请概率：低等级学校概率大幅降低，避免频繁打扰
        val levelFactor = when (campusLevel) {
            1 -> 0.15f   // 乡镇：很少有社团申请
            2 -> 0.25f
            3 -> 0.35f
            4 -> 0.45f
            5 -> 0.55f
            6 -> 0.65f
            else -> 0.15f
        }
        val applicationChance = levelFactor + (totalStudents / 500f).coerceAtMost(0.15f) + (schoolReputation / 1000f).coerceAtMost(0.1f)
        if (Random.nextFloat() > applicationChance) return emptyList()

        val availableTypes = getAvailableTypes().filter { type ->
            _pendingApplications.value.none { it.clubType == type }
        }
        if (availableTypes.isEmpty()) return emptyList()

        // 生成申请数量：低等级学校最多1个，高等级才可能同时2个
        val count = if (campusLevel >= 4 && Random.nextFloat() < 0.25f && availableTypes.size >= 2) 2 else 1
        val newApplications = availableTypes.shuffled().take(count).map { type ->
            ClubApplication(
                id = "${type.name}_${System.currentTimeMillis()}_${Random.nextInt(1000)}",
                clubType = type,
                applicantName = generateStudentName(),
                applicantCount = Random.nextInt(5, 12),
                reason = generateApplicationReason(type),
                waitDays = 0,
                status = ApplicationStatus.PENDING
            )
        }

        _pendingApplications.update { it + newApplications }
        return newApplications
    }

    /**
     * 批准社团申请（校长签字后调用）
     */
    fun approveApplication(applicationId: String): Boolean {
        val application = _pendingApplications.value.find { it.id == applicationId } ?: return false
        if (application.status != ApplicationStatus.PENDING) return false
        if (_clubs.value.size >= getMaxClubs(currentCampusLevel)) return false

        // 创建社团
        val club = Club(
            id = System.currentTimeMillis(),
            name = application.clubType.defaultName,
            type = application.clubType,
            memberCount = application.applicantCount + Random.nextInt(2, 6),
            level = ClubLevel.BEGINNER,
            enthusiasm = 75f + Random.nextFloat() * 20f,
            monthsActive = 0,
            trophyCount = 0
        )
        // 原子更新，避免并发丢失
        _clubs.update { it + club }

        // 标记申请状态为已批准，再从待处理列表移除
        _pendingApplications.update { apps ->
            apps.map {
                if (it.id == applicationId) it.copy(status = ApplicationStatus.APPROVED) else it
            }.filter { it.status == ApplicationStatus.PENDING }
        }
        return true
    }

    /**
     * 驳回社团申请
     * @return 声誉惩罚值（驳回会降低学生积极性）
     */
    fun rejectApplication(applicationId: String): Int {
        val application = _pendingApplications.value.find { it.id == applicationId } ?: return 0
        if (application.status != ApplicationStatus.PENDING) return 0

        // 标记为驳回再移除
        _pendingApplications.update { apps ->
            apps.map {
                if (it.id == applicationId) it.copy(status = ApplicationStatus.REJECTED) else it
            }.filter { it.status == ApplicationStatus.PENDING }
        }

        // 驳回惩罚：学生失望，轻微声誉损失
        return Random.nextInt(1, 4)
    }

    /**
     * 校长手动发起创建社团（也需走签字流程，由外部ChoiceEvent触发后调用）
     */
    fun createClubDirectly(type: ClubType): Boolean {
        if (_clubs.value.size >= getMaxClubs(currentCampusLevel)) return false
        if (_clubs.value.any { it.type == type }) return false

        val club = Club(
            id = System.currentTimeMillis(),
            name = type.defaultName,
            type = type,
            memberCount = Random.nextInt(5, 15),
            level = ClubLevel.BEGINNER,
            enthusiasm = 70f + Random.nextFloat() * 20f,
            monthsActive = 0,
            trophyCount = 0
        )
        _clubs.update { it + club }
        return true
    }

    /**
     * 每日推进：检查申请超时
     */
    fun advanceDay() {
        _pendingApplications.update { apps ->
            apps.mapNotNull { app ->
                if (app.status != ApplicationStatus.PENDING) return@mapNotNull null
                val updated = app.copy(waitDays = app.waitDays + 1)
                if (updated.waitDays >= APPLICATION_TIMEOUT_DAYS) {
                    null // 超时自动撤回
                } else {
                    updated
                }
            }
        }
    }

    /**
     * 解散社团
     */
    fun disbandClub(clubId: Long) {
        _clubs.update { it.filter { club -> club.id != clubId } }
    }

    /**
     * 每月社团更新
     */
    fun advanceMonth(totalStudents: Int): ClubMonthlyResult {
        var totalSatisfactionBonus = 0f
        var totalReputationBonus = 0L
        val events = mutableListOf<ClubEvent>()
        var monthlyExpense = 0.0

        _clubs.value = _clubs.value.map { club ->
            val updated = club.copy(monthsActive = club.monthsActive + 1)

            // 社团成员自然增长（随学生总数）
            val maxMembers = (totalStudents * 0.15f).toInt().coerceAtLeast(10)
            val newMembers = if (updated.memberCount < maxMembers) {
                (Random.nextInt(0, 3) * (updated.enthusiasm / 100f)).toInt()
            } else 0
            val memberUpdated = updated.copy(memberCount = (updated.memberCount + newMembers).coerceAtMost(maxMembers))

            // 社团升级检查
            val promoted = checkPromotion(memberUpdated)

            // 计算社团贡献
            totalSatisfactionBonus += promoted.type.satisfactionBonus * promoted.level.multiplier
            totalReputationBonus += (promoted.type.reputationBonus * promoted.level.multiplier).toLong()
            monthlyExpense += promoted.type.monthlyCost * promoted.level.multiplier

            // 活动事件触发（每3个月有概率触发活动）
            if (promoted.monthsActive % 3 == 0 && promoted.monthsActive > 0) {
                val event = tryGenerateEvent(promoted)
                if (event != null) {
                    events.add(event)
                    when (event) {
                        is ClubEvent.Competition -> {
                            if (event.won) {
                                totalReputationBonus += event.reputationReward
                                val trophied = promoted.copy(trophyCount = promoted.trophyCount + 1)
                                return@map trophied
                            }
                        }
                        is ClubEvent.Exhibition -> {
                            totalReputationBonus += event.reputationReward
                        }
                        is ClubEvent.Recruitment -> {
                            val recruited = promoted.copy(memberCount = promoted.memberCount + event.newMembers)
                            return@map recruited
                        }
                        is ClubEvent.Achievement -> {
                            totalReputationBonus += event.reputationReward
                        }
                    }
                }
            }

            // 热情衰减
            val enthusiasmDecay = if (promoted.level.ordinal >= ClubLevel.ADVANCED.ordinal) 0.5f else 1f
            promoted.copy(enthusiasm = (promoted.enthusiasm - enthusiasmDecay).coerceAtLeast(30f))
        }

        // 更新最近事件
        if (events.isNotEmpty()) {
            _recentEvents.value = (events + _recentEvents.value).take(MAX_RECENT_EVENTS)
        }

        return ClubMonthlyResult(
            satisfactionBonus = totalSatisfactionBonus,
            reputationBonus = totalReputationBonus,
            monthlyExpense = monthlyExpense,
            events = events
        )
    }

    /**
     * 获取当前社团提供的总满意度加成
     */
    fun getTotalSatisfactionBonus(): Float {
        return _clubs.value.sumOf { (it.type.satisfactionBonus * it.level.multiplier).toDouble() }.toFloat()
    }

    /**
     * 获取所有可创建的社团类型（排除已有的）
     */
    fun getAvailableTypes(): List<ClubType> {
        val existing = _clubs.value.map { it.type }.toSet()
        return ClubType.entries.filter { it !in existing }
    }

    private fun checkPromotion(club: Club): Club {
        val nextLevel = ClubLevel.entries.getOrNull(club.level.ordinal + 1) ?: return club
        val meetsMonths = club.monthsActive >= nextLevel.requiredMonths
        val meetsMembers = club.memberCount >= nextLevel.requiredMembers
        val meetsEnthusiasm = club.enthusiasm >= 60f

        return if (meetsMonths && meetsMembers && meetsEnthusiasm) {
            club.copy(level = nextLevel)
        } else {
            club
        }
    }

    private fun tryGenerateEvent(club: Club): ClubEvent? {
        val roll = Random.nextFloat()
        val eventChance = 0.4f + club.level.ordinal * 0.1f

        if (roll > eventChance) return null

        return when {
            roll < eventChance * 0.35f -> {
                // 竞赛
                val winChance = 0.3f + club.level.ordinal * 0.12f + club.enthusiasm / 200f
                val won = Random.nextFloat() < winChance
                ClubEvent.Competition(
                    clubName = club.name,
                    clubType = club.type,
                    won = won,
                    reputationReward = if (won) (5L + club.level.ordinal * 3L) else 0L
                )
            }
            roll < eventChance * 0.6f -> {
                // 展览/演出
                ClubEvent.Exhibition(
                    clubName = club.name,
                    clubType = club.type,
                    reputationReward = 2L + club.level.ordinal * 2L
                )
            }
            roll < eventChance * 0.85f -> {
                // 招新活动
                ClubEvent.Recruitment(
                    clubName = club.name,
                    clubType = club.type,
                    newMembers = Random.nextInt(3, 8)
                )
            }
            else -> {
                // 特殊成就
                ClubEvent.Achievement(
                    clubName = club.name,
                    clubType = club.type,
                    achievement = getRandomAchievement(club.type),
                    reputationReward = 8L + club.level.ordinal * 4L
                )
            }
        }
    }

    private fun getRandomAchievement(type: ClubType): String {
        return when (type) {
            ClubType.SCIENCE -> listOf("获得科学奥赛银牌", "研究项目入选国赛", "论文发表在学术期刊")[Random.nextInt(3)]
            ClubType.LITERATURE -> listOf("校刊获省级大奖", "诗歌比赛一等奖", "学生作品出版")[Random.nextInt(3)]
            ClubType.ART -> listOf("画展获全市好评", "设计作品获奖", "壁画项目完成")[Random.nextInt(3)]
            ClubType.MUSIC -> listOf("乐队获校际比赛冠军", "合唱团获金奖", "原创歌曲获奖")[Random.nextInt(3)]
            ClubType.SPORTS -> listOf("获得区运动会冠军", "篮球赛季冠军", "田径破校记录")[Random.nextInt(3)]
            ClubType.DEBATE -> listOf("辩论赛全市冠军", "模联最佳代表", "演讲比赛特等奖")[Random.nextInt(3)]
            ClubType.TECHNOLOGY -> listOf("编程大赛一等奖", "机器人比赛冠军", "APP上线获好评")[Random.nextInt(3)]
            ClubType.VOLUNTEER -> listOf("获社区服务表彰", "志愿时长达标获奖", "公益项目获媒体报道")[Random.nextInt(3)]
            ClubType.DRAMA -> listOf("话剧公演座无虚席", "获戏剧节最佳剧目", "学生编导获奖")[Random.nextInt(3)]
            ClubType.CHESS -> listOf("棋类锦标赛冠军", "围棋段位提升", "国际象棋区赛夺冠")[Random.nextInt(3)]
        }
    }

    fun toJson(): String {
        return try {
            val data = ClubPersistData(
                clubs = _clubs.value.map { c ->
                    ClubPersist(
                        id = c.id, name = c.name, type = c.type.name,
                        memberCount = c.memberCount, level = c.level.name,
                        enthusiasm = c.enthusiasm, monthsActive = c.monthsActive,
                        trophyCount = c.trophyCount
                    )
                },
                applications = _pendingApplications.value.map { a ->
                    ApplicationPersist(
                        id = a.id, clubType = a.clubType.name,
                        applicantName = a.applicantName, applicantCount = a.applicantCount,
                        reason = a.reason, waitDays = a.waitDays, status = a.status.name
                    )
                }
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<ClubPersistData>(json)
            val clubs = data.clubs.mapNotNull { cp ->
                val type = try { ClubType.valueOf(cp.type) } catch (_: Exception) { return@mapNotNull null }
                val level = try { ClubLevel.valueOf(cp.level) } catch (_: Exception) { ClubLevel.BEGINNER }
                Club(id = cp.id, name = cp.name, type = type, memberCount = cp.memberCount,
                    level = level, enthusiasm = cp.enthusiasm, monthsActive = cp.monthsActive,
                    trophyCount = cp.trophyCount)
            }
            val applications = data.applications.mapNotNull { ap ->
                val clubType = try { ClubType.valueOf(ap.clubType) } catch (_: Exception) { return@mapNotNull null }
                val status = try { ApplicationStatus.valueOf(ap.status) } catch (_: Exception) { ApplicationStatus.PENDING }
                ClubApplication(id = ap.id, clubType = clubType, applicantName = ap.applicantName,
                    applicantCount = ap.applicantCount, reason = ap.reason, waitDays = ap.waitDays, status = status)
            }
            _clubs.value = clubs
            _pendingApplications.value = applications
        } catch (e: Exception) {
            throw IllegalArgumentException("ClubManager.restoreFromJson failed", e)
        }
    }

    fun clearAll() {
        _clubs.value = emptyList()
        _recentEvents.value = emptyList()
        _pendingApplications.value = emptyList()
    }

    private fun generateStudentName(): String {
        val surnames = listOf("张", "李", "王", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "马", "朱", "胡")
        val givenNames = listOf("文博", "雨涵", "子轩", "梓萱", "浩然", "思琪", "宇辰", "欣怡", "俊杰", "诗涵",
            "明哲", "雅琪", "嘉豪", "梦瑶", "天佑", "语彤", "志远", "静怡", "晨曦", "雪薇")
        return surnames.random() + givenNames.random()
    }

    private fun generateApplicationReason(type: ClubType): String {
        return when (type) {
            ClubType.SCIENCE -> listOf(
                "我们对科学实验充满热情，希望能有一个交流和探索的平台",
                "想组织同学们一起做物理和化学实验，培养科学精神",
                "我们有一群热爱科学的同学，希望能参加各种科学竞赛"
            ).random()
            ClubType.LITERATURE -> listOf(
                "我们想创办校刊，分享同学们的优秀作品",
                "热爱阅读和写作的同学们想聚在一起交流文学",
                "希望能组织读书会和写作比赛，提高大家的文学素养"
            ).random()
            ClubType.TECHNOLOGY -> listOf(
                "我们对编程和机器人很感兴趣，想组队参加比赛",
                "希望能学习AI和编程技术，为未来做准备",
                "想组建创客空间，一起做有趣的科技项目"
            ).random()
            ClubType.DEBATE -> listOf(
                "我们想锻炼口才和逻辑思维能力",
                "希望能参加辩论赛和模拟联合国活动",
                "想通过辩论提高思辨能力和表达技巧"
            ).random()
            ClubType.ART -> listOf(
                "我们热爱画画和设计，想办画展展示作品",
                "希望能有一个自由创作和交流的艺术空间",
                "想组织美术比赛和手工活动，丰富校园文化"
            ).random()
            ClubType.MUSIC -> listOf(
                "我们想组建乐队，在校园活动中表演",
                "热爱音乐的同学想一起练习和创作",
                "希望能组织合唱团参加市级比赛"
            ).random()
            ClubType.DRAMA -> listOf(
                "我们想排演话剧，在学校艺术节上演出",
                "对表演艺术很感兴趣，想尝试编剧和导演",
                "希望能通过戏剧锻炼表达力和团队协作"
            ).random()
            ClubType.SPORTS -> listOf(
                "想组织课余体育活动，强身健体",
                "我们想参加校际体育比赛，为学校争光",
                "希望能让更多同学参与到运动中来"
            ).random()
            ClubType.CHESS -> listOf(
                "喜欢下棋的同学越来越多，想组织正式社团",
                "希望能参加棋类锦标赛，提高我们的水平",
                "想通过棋艺培养逻辑思维和耐心"
            ).random()
            ClubType.VOLUNTEER -> listOf(
                "想组织同学参与社区志愿服务活动",
                "希望能定期去敬老院和福利院做公益",
                "想让更多同学体验志愿精神，回馈社会"
            ).random()
        }
    }
}

/**
 * 社团类型
 */
enum class ClubType(
    val defaultName: String,
    val icon: String,
    val description: String,
    val satisfactionBonus: Float,
    val reputationBonus: Float,
    val monthlyCost: Double,
    val category: ClubCategory
) {
    SCIENCE("科学探索社", "🔬", "培养科学兴趣和研究能力",
        3f, 2f, 1.5, ClubCategory.ACADEMIC),       // 1.5万/月（实验耗材、设备维护）
    LITERATURE("文学社", "📖", "阅读写作，培养文学素养",
        2.5f, 1.5f, 0.5, ClubCategory.ACADEMIC),   // 0.5万/月（图书采购）
    TECHNOLOGY("科技创新社", "💻", "编程、机器人、AI 技术探索",
        3.5f, 3f, 2.5, ClubCategory.ACADEMIC),     // 2.5万/月（设备、零件、云服务）
    DEBATE("辩论社", "🎤", "辩论和演讲，锻炼表达能力",
        2f, 2.5f, 0.8, ClubCategory.ACADEMIC),     // 0.8万/月（场地、教练）
    ART("美术社", "🎨", "绘画、雕塑、设计创作",
        3f, 1.5f, 1.5, ClubCategory.ARTS),         // 1.5万/月（颜料画材）
    MUSIC("音乐社", "🎵", "乐器演奏、合唱、音乐创作",
        3.5f, 2f, 2.0, ClubCategory.ARTS),         // 2万/月（乐器维护）
    DRAMA("戏剧社", "🎭", "话剧表演、导演、编剧",
        3f, 2.5f, 1.5, ClubCategory.ARTS),         // 1.5万/月（服装道具）
    SPORTS("体育社", "⚽", "各类体育运动和竞技",
        4f, 2f, 1.2, ClubCategory.SPORTS),         // 1.2万/月（器材、场地）
    CHESS("棋艺社", "♟️", "围棋、象棋、国际象棋",
        1.5f, 1.5f, 0.3, ClubCategory.SPORTS),    // 0.3万/月（棋具）
    VOLUNTEER("志愿者社", "🤝", "社区服务和公益活动",
        2f, 3f, 0.8, ClubCategory.SERVICE)         // 0.8万/月（交通、物资）
}

enum class ClubCategory(val displayName: String) {
    ACADEMIC("学术类"),
    ARTS("艺术类"),
    SPORTS("体育类"),
    SERVICE("公益类")
}

/**
 * 社团等级
 */
enum class ClubLevel(
    val displayName: String,
    val multiplier: Float,
    val requiredMonths: Int,
    val requiredMembers: Int
) {
    BEGINNER("新建社团", 1.0f, 0, 5),
    DEVELOPING("发展中", 1.3f, 3, 10),
    ESTABLISHED("已成熟", 1.6f, 8, 20),
    ADVANCED("优秀社团", 2.0f, 15, 30),
    ELITE("明星社团", 2.5f, 24, 40)
}

/**
 * 社团数据
 */
data class Club(
    val id: Long,
    val name: String,
    val type: ClubType,
    val memberCount: Int,
    val level: ClubLevel,
    val enthusiasm: Float,
    val monthsActive: Int,
    val trophyCount: Int
)

/**
 * 社团事件
 */
sealed class ClubEvent {
    data class Competition(
        val clubName: String,
        val clubType: ClubType,
        val won: Boolean,
        val reputationReward: Long
    ) : ClubEvent()

    data class Exhibition(
        val clubName: String,
        val clubType: ClubType,
        val reputationReward: Long
    ) : ClubEvent()

    data class Recruitment(
        val clubName: String,
        val clubType: ClubType,
        val newMembers: Int
    ) : ClubEvent()

    data class Achievement(
        val clubName: String,
        val clubType: ClubType,
        val achievement: String,
        val reputationReward: Long
    ) : ClubEvent()
}

/**
 * 社团月度结算结果
 */
data class ClubMonthlyResult(
    val satisfactionBonus: Float = 0f,
    val reputationBonus: Long = 0L,
    val monthlyExpense: Double = 0.0,
    val events: List<ClubEvent> = emptyList()
)

/**
 * 社团申请状态
 */
enum class ApplicationStatus(val displayName: String) {
    PENDING("待审批"),
    APPROVED("已批准"),
    REJECTED("已驳回"),
    EXPIRED("已过期")
}

/**
 * 学生社团创建申请
 */
data class ClubApplication(
    val id: String,
    val clubType: ClubType,
    val applicantName: String,
    val applicantCount: Int,
    val reason: String,
    val waitDays: Int = 0,
    val status: ApplicationStatus = ApplicationStatus.PENDING
)

@Serializable
data class ClubPersistData(
    val clubs: List<ClubPersist> = emptyList(),
    val applications: List<ApplicationPersist> = emptyList()
)

@Serializable
data class ClubPersist(
    val id: Long,
    val name: String,
    val type: String,
    val memberCount: Int,
    val level: String,
    val enthusiasm: Float,
    val monthsActive: Int,
    val trophyCount: Int
)

@Serializable
data class ApplicationPersist(
    val id: String,
    val clubType: String,
    val applicantName: String,
    val applicantCount: Int,
    val reason: String,
    val waitDays: Int,
    val status: String
)
