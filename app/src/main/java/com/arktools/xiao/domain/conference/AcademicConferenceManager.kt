package com.arktools.xiao.domain.conference

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 学术会议系统（升级版）
 * 
 * 核心改动：
 * 1. 会议类型与学校等级挂钩（高等级才能举办大会议）
 * 2. 费用大幅提高（策略决策而非随意举办）
 * 3. 增加冷却期（每季度最多1次同类会议）
 * 4. 会议效果与就业/政府评级/声誉系统联动
 * 5. 增加"研究成果"维度（影响政府学术评分）
 * 6. 合作伙伴等级影响可举办会议质量
 */

enum class ConferenceType(
    val displayName: String,
    val icon: String,
    val baseCost: Double,       // 单位：万元（大幅提高）
    val baseReputation: Int,
    val teacherGrowth: Float,
    val duration: Int,          // 月
    val cooldownMonths: Int,    // 冷却月数
    val requiredSchoolLevel: Int,
    val employmentBoost: Float, // 对就业率的加成
    val govScoreBoost: Float,   // 对政府学术评分的加成
    val description: String
) {
    WORKSHOP(
        "学术研讨会", "📝", 8.0, 8, 3f, 1, 2,
        1, 0.02f, 1f,
        "小规模专题讨论，聚焦特定研究方向"
    ),
    SYMPOSIUM(
        "学术论坛", "🎤", 20.0, 18, 6f, 1, 3,
        2, 0.04f, 3f,
        "中等规模学术交流，涵盖多个议题"
    ),
    CONFERENCE(
        "学术大会", "🏛️", 50.0, 40, 12f, 2, 4,
        3, 0.06f, 5f,
        "大型正式学术会议，邀请知名学者"
    ),
    SUMMIT(
        "学术峰会", "🌟", 120.0, 80, 20f, 2, 6,
        5, 0.10f, 8f,
        "顶级学术盛会，汇聚领域权威"
    ),
    INTERNATIONAL(
        "国际学术大会", "🌍", 250.0, 150, 35f, 3, 8,
        6, 0.15f, 12f,
        "国际级学术会议，全球学者参与"
    )
}

enum class ConferenceRole(
    val displayName: String,
    val costMultiplier: Float,
    val reputationMultiplier: Float,
    val employmentMultiplier: Float
) {
    HOST("主办方", 1.0f, 1.5f, 1.2f),
    CO_HOST("协办方", 0.6f, 1.0f, 1.0f),
    PARTICIPANT("参会方", 0.25f, 0.5f, 0.7f),
    KEYNOTE_SPEAKER("主讲嘉宾", 0.15f, 1.3f, 1.1f)
}

enum class ConferenceStatus(val displayName: String) {
    PLANNING("筹备中"),
    IN_PROGRESS("进行中"),
    COMPLETED("已结束"),
    CANCELLED("已取消")
}

enum class AcademicField(val displayName: String, val icon: String) {
    NATURAL_SCIENCE("自然科学", "🔬"),
    ENGINEERING("工程技术", "⚙️"),
    MEDICINE("医学健康", "💉"),
    HUMANITIES("人文社科", "📖"),
    ARTS("艺术设计", "🎨"),
    EDUCATION("教育学", "📚"),
    ECONOMICS("经济管理", "📊"),
    INFORMATION("信息技术", "💻")
}

/**
 * 研究成果类型（会议产出）
 */
enum class ResearchOutput(
    val displayName: String,
    val reputationValue: Int,
    val govScoreValue: Float
) {
    PAPER("学术论文", 3, 1f),
    PATENT("专利申请", 8, 3f),
    COLLABORATION("合作项目", 5, 2f),
    AWARD("学术奖项", 15, 5f)
}

data class Conference(
    val id: String,
    val name: String,
    val type: ConferenceType,
    val role: ConferenceRole,
    val field: AcademicField,
    var status: ConferenceStatus = ConferenceStatus.PLANNING,
    val scheduledYear: Int,
    val scheduledMonth: Int,
    val remainingMonths: Int = 0,     // 剩余进行月数
    val participantCount: Int = 0,
    val paperCount: Int = 0,
    val keynoteCount: Int = 0,
    var reputationGained: Int = 0,
    var teacherGrowthGained: Float = 0f,
    var networkingGain: Int = 0,
    var employmentBoostGained: Float = 0f,
    val totalCost: Double = 0.0,
    val researchOutputs: List<ResearchOutput> = emptyList()
)

data class AcademicPartner(
    val name: String,
    val field: AcademicField,
    val prestige: Int,                  // 1-5
    val collaborationCount: Int = 0,
    val yearJoined: Int = 0,
    val bonusRepMultiplier: Float = 1f  // 合作方声望加成
)

data class ConferenceEvent(
    val title: String,
    val message: String,
    val year: Int,
    val month: Int,
    val reputationChange: Int = 0,
    val isPositive: Boolean = true
)

data class AcademicConferenceState(
    val conferences: List<Conference> = emptyList(),
    val academicPartners: List<AcademicPartner> = emptyList(),
    val totalConferencesHosted: Int = 0,
    val totalConferencesAttended: Int = 0,
    val academicInfluence: Int = 0,         // 学术影响力(0-1000)
    val networkSize: Int = 0,               // 学术网络规模
    val totalPapersPresented: Int = 0,
    val totalPatents: Int = 0,
    val totalAwards: Int = 0,
    val monthlyBudget: Double = 0.0,
    val events: List<ConferenceEvent> = emptyList(),
    val teacherGrowthPool: Float = 0f,
    val researchScore: Float = 0f,          // 研究积分（影响政府评分）
    val employmentBoostPool: Float = 0f,    // 累积就业加成
    val lastConferenceMonth: Map<ConferenceType, Int> = emptyMap() // 冷却追踪(类型→上次举办的绝对月份)
)

data class ConferenceMonthlyResult(
    val expenses: Double = 0.0,
    val reputationGain: Int = 0,
    val teacherGrowth: Float = 0f,
    val employmentBoost: Float = 0f,        // 本月就业加成
    val researchScoreGain: Float = 0f,      // 研究分增加
    val completedConferences: List<Conference> = emptyList(),
    val events: List<ConferenceEvent> = emptyList(),
    val newPartners: List<AcademicPartner> = emptyList()
)

@Singleton
class AcademicConferenceManager @Inject constructor() {

    private val _state = MutableStateFlow(AcademicConferenceState())
    val state: StateFlow<AcademicConferenceState> = _state.asStateFlow()

    fun reset() {
        _state.value = AcademicConferenceState()
        nextConferenceId = 1
    }

    private val random = java.util.Random()
    private var nextConferenceId = 1

    // 预设学术合作方（按声望排序，高声望的需要更高的学术影响力才能解锁）
    private val potentialPartners = listOf(
        AcademicPartner("地区教育联盟", AcademicField.EDUCATION, 1, bonusRepMultiplier = 1.05f),
        AcademicPartner("市科技协会", AcademicField.NATURAL_SCIENCE, 2, bonusRepMultiplier = 1.08f),
        AcademicPartner("省教育研究院", AcademicField.EDUCATION, 2, bonusRepMultiplier = 1.10f),
        AcademicPartner("企业研发中心", AcademicField.INFORMATION, 2, bonusRepMultiplier = 1.08f),
        AcademicPartner("国家自科基金", AcademicField.NATURAL_SCIENCE, 3, bonusRepMultiplier = 1.15f),
        AcademicPartner("首尔国立大学", AcademicField.ARTS, 3, bonusRepMultiplier = 1.12f),
        AcademicPartner("复旦大学", AcademicField.MEDICINE, 4, bonusRepMultiplier = 1.20f),
        AcademicPartner("浙江大学", AcademicField.INFORMATION, 4, bonusRepMultiplier = 1.20f),
        AcademicPartner("东京大学", AcademicField.ENGINEERING, 4, bonusRepMultiplier = 1.22f),
        AcademicPartner("清华大学", AcademicField.ENGINEERING, 5, bonusRepMultiplier = 1.30f),
        AcademicPartner("北京大学", AcademicField.HUMANITIES, 5, bonusRepMultiplier = 1.30f),
        AcademicPartner("中国科学院", AcademicField.NATURAL_SCIENCE, 5, bonusRepMultiplier = 1.35f),
        AcademicPartner("MIT合作项目", AcademicField.ENGINEERING, 5, bonusRepMultiplier = 1.40f),
        AcademicPartner("剑桥研究中心", AcademicField.NATURAL_SCIENCE, 5, bonusRepMultiplier = 1.40f),
        AcademicPartner("哈佛教育学院", AcademicField.EDUCATION, 5, bonusRepMultiplier = 1.45f)
    )

    /**
     * 检查是否可以举办指定类型的会议
     * 返回: null = 可以举办; String = 不能举办的原因
     */
    fun canCreateConference(type: ConferenceType, schoolLevel: Int, currentAbsMonth: Int): String? {
        // 学校等级检查
        if (schoolLevel < type.requiredSchoolLevel) {
            return "需要学校等级${type.requiredSchoolLevel}级（当前${schoolLevel}级）"
        }
        // 冷却检查
        val lastMonth = _state.value.lastConferenceMonth[type]
        if (lastMonth != null && (currentAbsMonth - lastMonth) < type.cooldownMonths) {
            val remaining = type.cooldownMonths - (currentAbsMonth - lastMonth)
            return "冷却中（还需${remaining}个月）"
        }
        // 并发会议上限（同时最多2个进行中/筹备中）
        val activeCount = _state.value.conferences.count {
            it.status == ConferenceStatus.PLANNING || it.status == ConferenceStatus.IN_PROGRESS
        }
        if (activeCount >= 2) {
            return "同时最多进行2个会议（当前${activeCount}个）"
        }
        return null
    }

    /**
     * 举办/参加学术会议
     */
    fun createConference(
        type: ConferenceType,
        role: ConferenceRole,
        field: AcademicField,
        name: String = "${type.displayName} · ${field.displayName}",
        year: Int,
        month: Int,
        schoolLevel: Int = 1
    ): Conference? {
        val absMonth = year * 12 + month
        val reason = canCreateConference(type, schoolLevel, absMonth)
        if (reason != null) return null

        val actualCost = type.baseCost * role.costMultiplier
        val participantCount = when (type) {
            ConferenceType.WORKSHOP -> random.nextInt(30) + 20
            ConferenceType.SYMPOSIUM -> random.nextInt(80) + 50
            ConferenceType.CONFERENCE -> random.nextInt(200) + 100
            ConferenceType.SUMMIT -> random.nextInt(500) + 300
            ConferenceType.INTERNATIONAL -> random.nextInt(1000) + 500
        }

        // 合作伙伴加成（相关领域合作方增加参会人数和论文数）
        val partnerBonus = _state.value.academicPartners
            .filter { it.field == field }
            .sumOf { it.prestige } * 0.1f
        val boostedParticipants = (participantCount * (1f + partnerBonus)).toInt()

        val conference = Conference(
            id = "conf_${nextConferenceId++}",
            name = name,
            type = type,
            role = role,
            field = field,
            scheduledYear = year,
            scheduledMonth = month,
            remainingMonths = type.duration,
            participantCount = boostedParticipants,
            paperCount = boostedParticipants / 5,
            keynoteCount = type.ordinal + 2,
            totalCost = actualCost
        )

        _state.update { state ->
            state.copy(
                conferences = state.conferences + conference,
                lastConferenceMonth = state.lastConferenceMonth + (type to absMonth)
            )
        }
        return conference
    }

    /**
     * 月度推进（增加联动参数）
     */
    fun advanceMonth(
        currentYear: Int,
        currentMonth: Int,
        schoolReputation: Int,
        schoolLevel: Int = 1,
        teacherCount: Int = 0
    ): ConferenceMonthlyResult {
        var totalExpenses = 0.0
        var totalReputationGain = 0
        var totalTeacherGrowth = 0f
        var totalEmploymentBoost = 0f
        var totalResearchScore = 0f
        val completedConfs = mutableListOf<Conference>()
        val events = mutableListOf<ConferenceEvent>()
        val newPartners = mutableListOf<AcademicPartner>()

        _state.update { state ->
            val updatedConferences = state.conferences.map { conf ->
                val scheduledAbsMonth = conf.scheduledYear * 12 + conf.scheduledMonth
                val currentAbsMonth = currentYear * 12 + currentMonth
                when {
                    // 到达（或已超过）举办月份 → 开始进行
                    // 用 >= 比较而非精确匹配，避免创建当月的推进已跑过后永久卡在"筹备中"
                    conf.status == ConferenceStatus.PLANNING &&
                    currentAbsMonth >= scheduledAbsMonth -> {
                        totalExpenses += conf.totalCost
                        events.add(ConferenceEvent(
                            title = "${conf.name}正式开幕",
                            message = "预计${conf.participantCount}人参会，为期${conf.type.duration}个月",
                            year = currentYear, month = currentMonth,
                            isPositive = true
                        ))
                        conf.copy(status = ConferenceStatus.IN_PROGRESS, remainingMonths = conf.type.duration)
                    }
                    // 进行中 → 倒计时
                    conf.status == ConferenceStatus.IN_PROGRESS && conf.remainingMonths > 1 -> {
                        // 每月进行中的会议也有少量持续费用
                        val ongoingCost = conf.totalCost * 0.1
                        totalExpenses += ongoingCost
                        conf.copy(remainingMonths = conf.remainingMonths - 1)
                    }
                    // 进行中 → 完成（最后一个月）
                    conf.status == ConferenceStatus.IN_PROGRESS && conf.remainingMonths <= 1 -> {
                        // 计算最终成果
                        val partnerMultiplier = state.academicPartners
                            .filter { it.field == conf.field }
                            .maxOfOrNull { it.bonusRepMultiplier } ?: 1f
                        
                        val repGain = (conf.type.baseReputation * conf.role.reputationMultiplier * partnerMultiplier).toInt()
                        val teachGrowth = conf.type.teacherGrowth * conf.role.reputationMultiplier *
                            (1f + teacherCount * 0.01f).coerceAtMost(1.5f)
                        val empBoost = conf.type.employmentBoost * conf.role.employmentMultiplier
                        val researchGain = conf.type.govScoreBoost * conf.role.reputationMultiplier

                        totalReputationGain += repGain
                        totalTeacherGrowth += teachGrowth
                        totalEmploymentBoost += empBoost
                        totalResearchScore += researchGain

                        // 生成研究成果
                        val outputs = generateResearchOutputs(conf, schoolLevel)
                        outputs.forEach { output ->
                            totalReputationGain += output.reputationValue
                            totalResearchScore += output.govScoreValue
                        }

                        val completed = conf.copy(
                            status = ConferenceStatus.COMPLETED,
                            remainingMonths = 0,
                            reputationGained = repGain,
                            teacherGrowthGained = teachGrowth,
                            networkingGain = conf.participantCount / 10,
                            employmentBoostGained = empBoost,
                            researchOutputs = outputs
                        )
                        completedConfs.add(completed)

                        // 成果汇总事件
                        val outputSummary = if (outputs.isNotEmpty()) {
                            "\n研究成果: ${outputs.groupBy { it }.map { "${it.value.size}${it.key.displayName}" }.joinToString("、")}"
                        } else ""
                        events.add(ConferenceEvent(
                            title = "${conf.name}圆满结束",
                            message = "参会${conf.participantCount}人，发表论文${conf.paperCount}篇\n" +
                                "声誉+${repGain} · 教师成长+${teachGrowth.toInt()} · 就业+${String.format("%.0f", empBoost * 100)}%${outputSummary}",
                            year = currentYear, month = currentMonth,
                            reputationChange = repGain,
                            isPositive = true
                        ))

                        // 获得新合作伙伴概率（主办方优先，学术影响力越高概率越大）
                        val partnerChance = when (conf.role) {
                            ConferenceRole.HOST -> 0.35f
                            ConferenceRole.CO_HOST -> 0.20f
                            ConferenceRole.KEYNOTE_SPEAKER -> 0.15f
                            ConferenceRole.PARTICIPANT -> 0.08f
                        } + (state.academicInfluence / 2000f)

                        if (random.nextFloat() < partnerChance) {
                            val existingNames = state.academicPartners.map { it.name }.toSet()
                            // 只能获得声望 <= schoolLevel+1 的合作方
                            val candidate = potentialPartners
                                .filter { it.name !in existingNames && it.prestige <= schoolLevel + 1 }
                                .filter { it.field == conf.field || random.nextFloat() < 0.3f }
                                .randomOrNull()
                            if (candidate != null) {
                                val partner = candidate.copy(yearJoined = currentYear, collaborationCount = 1)
                                newPartners.add(partner)
                                events.add(ConferenceEvent(
                                    title = "新学术合作",
                                    message = "与${partner.name}(${partner.prestige}星)建立学术合作",
                                    year = currentYear, month = currentMonth,
                                    reputationChange = partner.prestige * 3,
                                    isPositive = true
                                ))
                                totalReputationGain += partner.prestige * 3
                            }
                        }

                        completed
                    }
                    else -> conf
                }
            }

            // 随机生成邀请参会机会（概率随学校等级和影响力增加）
            val inviteChance = 0.05f + schoolLevel * 0.02f + (state.academicInfluence / 3000f)
            if (random.nextFloat() < inviteChance && schoolReputation > 100) {
                // 只生成学校等级能参加的类型
                val availableTypes = ConferenceType.entries.filter { it.requiredSchoolLevel <= schoolLevel }
                if (availableTypes.isNotEmpty()) {
                    val inviteType = availableTypes[random.nextInt(availableTypes.size)]
                    val inviteField = AcademicField.entries[random.nextInt(AcademicField.entries.size)]
                    val discountedCost = String.format("%.1f", inviteType.baseCost * 0.15)
                    events.add(ConferenceEvent(
                        title = "会议邀请",
                        message = "收到${inviteType.displayName}(${inviteField.displayName})参会邀请，费用仅¥${discountedCost}万",
                        year = currentYear, month = currentMonth,
                        isPositive = true
                    ))
                }
            }

            // 研究分自然衰减（需要持续举办会议维持）
            val decayedResearchScore = (state.researchScore * 0.98f)

            val hosted = updatedConferences.count { it.status == ConferenceStatus.COMPLETED && it.role == ConferenceRole.HOST }
            val attended = updatedConferences.count { it.status == ConferenceStatus.COMPLETED }
            val influence = (state.academicInfluence + totalReputationGain + completedConfs.sumOf { it.networkingGain })
                .coerceAtMost(1000)
            val network = state.networkSize + completedConfs.sumOf { it.networkingGain }
            val papers = state.totalPapersPresented + completedConfs.sumOf { it.paperCount }
            val patents = state.totalPatents + completedConfs.flatMap { it.researchOutputs }.count { it == ResearchOutput.PATENT }
            val awards = state.totalAwards + completedConfs.flatMap { it.researchOutputs }.count { it == ResearchOutput.AWARD }

            state.copy(
                conferences = updatedConferences,
                academicPartners = state.academicPartners + newPartners,
                totalConferencesHosted = hosted,
                totalConferencesAttended = attended,
                academicInfluence = influence,
                networkSize = network,
                totalPapersPresented = papers,
                totalPatents = patents,
                totalAwards = awards,
                monthlyBudget = totalExpenses,
                events = (events + state.events).take(50),
                teacherGrowthPool = state.teacherGrowthPool + totalTeacherGrowth,
                researchScore = decayedResearchScore + totalResearchScore,
                employmentBoostPool = (state.employmentBoostPool * 0.9f + totalEmploymentBoost).coerceAtMost(0.3f)
            )
        }

        return ConferenceMonthlyResult(
            expenses = totalExpenses,
            reputationGain = totalReputationGain,
            teacherGrowth = totalTeacherGrowth,
            employmentBoost = totalEmploymentBoost,
            researchScoreGain = totalResearchScore,
            completedConferences = completedConfs,
            events = events,
            newPartners = newPartners
        )
    }

    /**
     * 生成会议研究成果
     */
    private fun generateResearchOutputs(conf: Conference, schoolLevel: Int): List<ResearchOutput> {
        val outputs = mutableListOf<ResearchOutput>()
        
        // 论文产出（基本保底）
        val paperChance = 0.6f + schoolLevel * 0.05f
        if (random.nextFloat() < paperChance) {
            outputs.add(ResearchOutput.PAPER)
            if (conf.type.ordinal >= 2 && random.nextFloat() < 0.4f) {
                outputs.add(ResearchOutput.PAPER) // 大型会议有机会多篇论文
            }
        }

        // 专利（需要工程/信息/医学领域+一定规模）
        if (conf.field in listOf(AcademicField.ENGINEERING, AcademicField.INFORMATION, AcademicField.MEDICINE)) {
            val patentChance = 0.1f + conf.type.ordinal * 0.05f + schoolLevel * 0.03f
            if (random.nextFloat() < patentChance) {
                outputs.add(ResearchOutput.PATENT)
            }
        }

        // 合作项目（主办/协办更容易产生）
        if (conf.role in listOf(ConferenceRole.HOST, ConferenceRole.CO_HOST)) {
            val collabChance = 0.2f + conf.type.ordinal * 0.08f
            if (random.nextFloat() < collabChance) {
                outputs.add(ResearchOutput.COLLABORATION)
            }
        }

        // 学术奖项（顶级会议+高学校等级才有机会）
        if (conf.type.ordinal >= 3 && schoolLevel >= 4) {
            val awardChance = 0.05f + (schoolLevel - 4) * 0.03f
            if (random.nextFloat() < awardChance) {
                outputs.add(ResearchOutput.AWARD)
            }
        }

        return outputs
    }

    /**
     * 获取可参加的会议类型（与学校等级挂钩）
     */
    fun getAvailableConferenceTypes(schoolLevel: Int, currentAbsMonth: Int): List<Pair<ConferenceType, String?>> {
        return ConferenceType.entries.map { type ->
            type to canCreateConference(type, schoolLevel, currentAbsMonth)
        }
    }

    /**
     * 获取当前累积的就业加成（供就业市场使用）
     */
    fun getCurrentEmploymentBoost(): Float = _state.value.employmentBoostPool

    /**
     * 获取当前研究积分（供政府评估使用）
     */
    fun getResearchScore(): Float = _state.value.researchScore

    /**
     * 消费累积的教师成长池（清零并返回值）
     * 每月由 GameEngine 调用，分配给在职教师
     */
    fun consumeTeacherGrowthPool(): Float {
        val pool = _state.value.teacherGrowthPool
        if (pool > 0f) {
            _state.update { it.copy(teacherGrowthPool = 0f) }
        }
        return pool
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = ConferencePersistData(
                conferences = state.conferences.map { c ->
                    ConferencePersist(
                        id = c.id,
                        name = c.name,
                        type = c.type.name,
                        role = c.role.name,
                        field = c.field.name,
                        status = c.status.name,
                        scheduledYear = c.scheduledYear,
                        scheduledMonth = c.scheduledMonth,
                        remainingMonths = c.remainingMonths,
                        participantCount = c.participantCount,
                        paperCount = c.paperCount,
                        keynoteCount = c.keynoteCount,
                        totalCost = c.totalCost,
                        reputationGained = c.reputationGained,
                        teacherGrowthGained = c.teacherGrowthGained,
                        networkingGain = c.networkingGain,
                        employmentBoostGained = c.employmentBoostGained,
                        researchOutputs = c.researchOutputs.map { it.name }
                    )
                },
                partners = state.academicPartners.map { p ->
                    PartnerPersist(
                        name = p.name,
                        field = p.field.name,
                        prestige = p.prestige,
                        collaborationCount = p.collaborationCount,
                        yearJoined = p.yearJoined,
                        bonusRepMultiplier = p.bonusRepMultiplier
                    )
                },
                totalHosted = state.totalConferencesHosted,
                totalAttended = state.totalConferencesAttended,
                academicInfluence = state.academicInfluence,
                networkSize = state.networkSize,
                totalPapers = state.totalPapersPresented,
                totalPatents = state.totalPatents,
                totalAwards = state.totalAwards,
                monthlyBudget = state.monthlyBudget,
                teacherGrowthPool = state.teacherGrowthPool,
                researchScore = state.researchScore,
                employmentBoostPool = state.employmentBoostPool,
                lastConferenceMonth = state.lastConferenceMonth.map { (k, v) -> k.name to v }.toMap(),
                nextConferenceId = nextConferenceId
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<ConferencePersistData>(json)
            val conferences = data.conferences.mapNotNull { cp ->
                val type = try { ConferenceType.valueOf(cp.type) } catch (_: Exception) { return@mapNotNull null }
                val role = try { ConferenceRole.valueOf(cp.role) } catch (_: Exception) { ConferenceRole.PARTICIPANT }
                val field = try { AcademicField.valueOf(cp.field) } catch (_: Exception) { AcademicField.EDUCATION }
                val status = try { ConferenceStatus.valueOf(cp.status) } catch (_: Exception) { ConferenceStatus.PLANNING }
                Conference(
                    id = cp.id,
                    name = cp.name,
                    type = type,
                    role = role,
                    field = field,
                    status = status,
                    scheduledYear = cp.scheduledYear,
                    scheduledMonth = cp.scheduledMonth,
                    remainingMonths = cp.remainingMonths,
                    participantCount = cp.participantCount,
                    paperCount = cp.paperCount,
                    keynoteCount = cp.keynoteCount,
                    totalCost = cp.totalCost,
                    reputationGained = cp.reputationGained,
                    teacherGrowthGained = cp.teacherGrowthGained,
                    networkingGain = cp.networkingGain,
                    employmentBoostGained = cp.employmentBoostGained,
                    researchOutputs = cp.researchOutputs.mapNotNull { output ->
                        runCatching { ResearchOutput.valueOf(output) }.getOrNull()
                    }
                )
            }
            val partners = data.partners.mapNotNull { pp ->
                val field = try { AcademicField.valueOf(pp.field) } catch (_: Exception) { return@mapNotNull null }
                AcademicPartner(
                    name = pp.name,
                    field = field,
                    prestige = pp.prestige,
                    collaborationCount = pp.collaborationCount,
                    yearJoined = pp.yearJoined,
                    bonusRepMultiplier = pp.bonusRepMultiplier
                )
            }
            val lastConfMonth = data.lastConferenceMonth.mapNotNull { (k, v) ->
                val type = try { ConferenceType.valueOf(k) } catch (_: Exception) { return@mapNotNull null }
                type to v
            }.toMap()
            nextConferenceId = data.nextConferenceId
            _state.value = AcademicConferenceState(
                conferences = conferences,
                academicPartners = partners,
                totalConferencesHosted = data.totalHosted,
                totalConferencesAttended = data.totalAttended,
                academicInfluence = data.academicInfluence,
                networkSize = data.networkSize,
                totalPapersPresented = data.totalPapers,
                totalPatents = data.totalPatents,
                totalAwards = data.totalAwards,
                monthlyBudget = data.monthlyBudget,
                events = emptyList(),
                teacherGrowthPool = data.teacherGrowthPool,
                researchScore = data.researchScore,
                employmentBoostPool = data.employmentBoostPool,
                lastConferenceMonth = lastConfMonth
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("AcademicConferenceManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class ConferencePersistData(
    val conferences: List<ConferencePersist> = emptyList(),
    val partners: List<PartnerPersist> = emptyList(),
    val totalHosted: Int = 0,
    val totalAttended: Int = 0,
    val academicInfluence: Int = 0,
    val networkSize: Int = 0,
    val totalPapers: Int = 0,
    val totalPatents: Int = 0,
    val totalAwards: Int = 0,
    val monthlyBudget: Double = 0.0,
    val teacherGrowthPool: Float = 0f,
    val researchScore: Float = 0f,
    val employmentBoostPool: Float = 0f,
    val lastConferenceMonth: Map<String, Int> = emptyMap(),
    val nextConferenceId: Int = 1
)

@Serializable
data class ConferencePersist(
    val id: String,
    val name: String,
    val type: String,
    val role: String,
    val field: String,
    val status: String,
    val scheduledYear: Int,
    val scheduledMonth: Int,
    val remainingMonths: Int,
    val participantCount: Int,
    val paperCount: Int,
    val keynoteCount: Int = 0,
    val totalCost: Double,
    val reputationGained: Int,
    val teacherGrowthGained: Float = 0f,
    val networkingGain: Int = 0,
    val employmentBoostGained: Float,
    val researchOutputs: List<String> = emptyList()
)

@Serializable
data class PartnerPersist(
    val name: String,
    val field: String,
    val prestige: Int,
    val collaborationCount: Int = 0,
    val yearJoined: Int = 0,
    val bonusRepMultiplier: Float = 1f
)
