package com.arktools.xiaozhang.domain.alumni

import com.arktools.xiaozhang.domain.model.Student
import com.arktools.xiaozhang.domain.model.StudentReview
import com.arktools.xiaozhang.domain.model.UniversityTier
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
 * 校友网络系统 v2
 * - 追踪毕业生职业发展
 * - 校友定期捐赠（基于评价和职业成就）
 * - 校友推荐新生（提升招生）
 * - 校友事件（回校演讲、捐赠设施等）
 * - 【新增】行业人脉网络 → 对应科目加成
 * - 【新增】校友会等级与学校等级联动
 * - 【新增】主动运营功能（举办校友活动消耗资金换取效果）
 */
@Singleton
class AlumniNetwork @Inject constructor() {

    private val _alumni = MutableStateFlow<List<Alumnus>>(emptyList())
    val alumni: StateFlow<List<Alumnus>> = _alumni.asStateFlow()

    private val _stats = MutableStateFlow(AlumniStats())
    val stats: StateFlow<AlumniStats> = _stats.asStateFlow()

    private val _networkLevel = MutableStateFlow(1)
    val networkLevel: StateFlow<Int> = _networkLevel.asStateFlow()

    // 行业人脉积累（影响对应科目收益）
    private val _industryConnections = MutableStateFlow<Map<CareerPath, Int>>(emptyMap())
    val industryConnections: StateFlow<Map<CareerPath, Int>> = _industryConnections.asStateFlow()

    // 毕业生批次总结（历届高考数据）
    private val _graduationSummaries = MutableStateFlow<List<GraduationBatchSummary>>(emptyList())
    val graduationSummaries: StateFlow<List<GraduationBatchSummary>> = _graduationSummaries.asStateFlow()

    // 校友活动冷却（月数）
    private var activityCooldown = 0

    companion object {
        const val MAX_TRACKED_ALUMNI = 200

        /** 校友网络等级条件（需满足的高管+资深数量） */
        val NETWORK_LEVEL_REQUIREMENTS = listOf(
            0,    // Lv1: 初始
            3,    // Lv2: 3名高级以上校友
            8,    // Lv3: 8名
            15,   // Lv4: 15名
            30,   // Lv5: 30名
            50    // Lv6: 50名
        )

        /** 校友网络等级需要的学校等级门槛 */
        val NETWORK_SCHOOL_LEVEL_REQ = listOf(1, 1, 2, 3, 4, 5)

        /** 各等级解锁功能 */
        val LEVEL_FEATURES = listOf(
            "基础校友追踪",           // Lv1
            "行业人脉加成生效",       // Lv2
            "解锁校友活动（聚会）",   // Lv3
            "解锁校友活动（论坛）",   // Lv4
            "行业人脉加成翻倍",       // Lv5
            "解锁校友基金（被动收入）" // Lv6
        )
    }

    data class Snapshot(
        val alumni: List<Alumnus>,
        val stats: AlumniStats,
        val networkLevel: Int,
        val industryConnections: Map<CareerPath, Int>,
        val graduationSummaries: List<GraduationBatchSummary>,
        val activityCooldown: Int
    )

    fun snapshotState(): Snapshot = Snapshot(
        alumni = _alumni.value.map { it.copy() },
        stats = _stats.value.copy(
            careerDistribution = _stats.value.careerDistribution.toMap()
        ),
        networkLevel = _networkLevel.value,
        industryConnections = _industryConnections.value.toMap(),
        graduationSummaries = _graduationSummaries.value.map { summary ->
            summary.copy(
                topStudents = summary.topStudents.toList(),
                universityDistribution =
                    summary.universityDistribution.toMap()
            )
        },
        activityCooldown = activityCooldown
    )

    fun restoreSnapshot(snapshot: Snapshot) {
        _alumni.value = snapshot.alumni.map { it.copy() }
        _stats.value = snapshot.stats.copy(
            careerDistribution = snapshot.stats.careerDistribution.toMap()
        )
        _networkLevel.value = snapshot.networkLevel
        _industryConnections.value =
            snapshot.industryConnections.toMap()
        _graduationSummaries.value =
            snapshot.graduationSummaries.map { summary ->
                summary.copy(
                    topStudents = summary.topStudents.toList(),
                    universityDistribution =
                        summary.universityDistribution.toMap()
                )
            }
        activityCooldown = snapshot.activityCooldown
    }

    /**
     * 学生毕业时注册为校友（基于大学录取层次影响职业前景）
     */
    fun registerGraduate(student: Student): Boolean {
        if (_alumni.value.any { it.id == student.id }) return false
        val career = assignCareerBasedOnStudent(student)
        val successPotential = calculateSuccessPotential(student)

        // 大学层次影响初始职业等级：清北/985直接跳过ENTRY
        val initialCareerLevel = when (student.universityTier) {
            UniversityTier.QINGBEI -> CareerLevel.MIDDLE        // 清北校友起步高
            UniversityTier.TOP_985 -> CareerLevel.JUNIOR        // 顶尖985起步中
            UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> CareerLevel.JUNIOR
            else -> CareerLevel.ENTRY
        }

        // 大学层次加成捐赠意愿
        val tierDonationBonus = when (student.universityTier) {
            UniversityTier.QINGBEI -> 0.2f
            UniversityTier.TOP_985 -> 0.1f
            UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> 0.05f
            else -> 0f
        }

        val alumnus = Alumnus(
            id = student.id,
            name = student.name,
            graduationRating = (student.review?.rating?.toFloat()) ?: 3f,
            career = career,
            careerLevel = initialCareerLevel,
            successPotential = successPotential,
            satisfaction = student.satisfaction,
            donationWillingness = (calculateDonationWillingness(student) + tierDonationBonus).coerceAtMost(1.0f),
            monthsSinceGraduation = 0,
            universityTier = student.universityTier
        )

        _alumni.update { current ->
            val updated = current + alumnus
            if (updated.size > MAX_TRACKED_ALUMNI) {
                updated.sortedByDescending { it.careerLevel.ordinal * 100 + (MAX_TRACKED_ALUMNI - it.monthsSinceGraduation) }
                    .take(MAX_TRACKED_ALUMNI)
            } else {
                updated
            }
        }

        // 累积行业人脉
        _industryConnections.update { connections ->
            connections.toMutableMap().apply {
                this[career] = (this[career] ?: 0) + 1
            }
        }

        updateStats()
        checkNetworkLevelUp(1) // 默认学校等级1，实际由外部调用带入
        return true
    }

    /**
     * 记录一届毕业生总结（由 conductGaoKao 调用）
     */
    fun recordGraduationBatch(
        year: Int,
        totalStudents: Int,
        averageScore: Float,
        highestScore: Float,
        bengkeRate: Float,
        key985Count: Int,
        qingbeiCount: Int,
        topStudents: List<GraduationTopStudent>,
        universityDistribution: Map<String, Int>
    ) {
        val existing = _graduationSummaries.value.firstOrNull {
            it.year == year
        }
        val summary = GraduationBatchSummary(
            year = year,
            totalStudents = totalStudents,
            averageScore = averageScore,
            highestScore = highestScore,
            bengkeRate = bengkeRate,
            key985Count = key985Count,
            qingbeiCount = qingbeiCount,
            topStudents = topStudents.take(5),
            universityDistribution = universityDistribution,
            settlementCompleted = if (existing == null) {
                false
            } else {
                existing.settlementCompleted
            },
            settledCashBonus = existing?.settledCashBonus ?: 0.0,
            settledReputationDelta =
                existing?.settledReputationDelta ?: 0L
        )
        _graduationSummaries.update { current ->
            (current.filterNot { it.year == year } + summary)
                .sortedByDescending { it.year }
        }
    }

    fun getPendingGraduationSettlementYears(): Set<Int> {
        return _graduationSummaries.value
            .filter { it.settlementCompleted == false }
            .mapTo(mutableSetOf()) { it.year }
    }

    fun isGraduationSettlementCompleted(year: Int): Boolean {
        return _graduationSummaries.value
            .firstOrNull { it.year == year }
            ?.settlementCompleted != false
    }

    fun completeGraduationSettlement(
        year: Int,
        cashBonus: Double,
        reputationDelta: Long
    ): Boolean {
        var changed = false
        _graduationSummaries.update { current ->
            current.map { summary ->
                if (summary.year == year &&
                    summary.settlementCompleted == false
                ) {
                    changed = true
                    summary.copy(
                        settlementCompleted = true,
                        settledCashBonus = cashBonus,
                        settledReputationDelta = reputationDelta
                    )
                } else {
                    summary
                }
            }
        }
        return changed
    }

    /**
     * 每月推进校友职业发展
     */
    fun advanceMonth(schoolLevel: Int = 1): AlumniMonthlyResult {
        var totalDonation = 0.0
        var referrals = 0
        val events = mutableListOf<AlumniEvent>()

        if (activityCooldown > 0) activityCooldown--

        _alumni.update { current ->
            current.map { alumnus ->
                val updated = alumnus.copy(monthsSinceGraduation = alumnus.monthsSinceGraduation + 1)

                // 职业晋升检查（每12个月有机会晋升）
                val promoted = if (updated.monthsSinceGraduation % 12 == 0) {
                    tryPromote(updated)
                } else {
                    updated
                }

                // 捐赠检查（每月有小概率触发）
                val donationChance = promoted.donationWillingness * 0.02f * promoted.careerLevel.donationMultiplier
                if (Random.nextFloat() < donationChance) {
                    val amount = calculateDonation(promoted)
                    totalDonation += amount
                    events.add(AlumniEvent.Donation(promoted.name, amount, promoted.career))
                }

                // 推荐学生检查
                val referralChance = promoted.satisfaction / 100f * 0.01f * promoted.careerLevel.referralMultiplier
                if (Random.nextFloat() < referralChance) {
                    referrals++
                    if (events.size < 5) {
                        events.add(AlumniEvent.Referral(promoted.name, promoted.career))
                    }
                }

                // 特殊事件（高管重大捐赠）
                if (promoted.careerLevel == CareerLevel.EXECUTIVE && promoted.monthsSinceGraduation % 24 == 0) {
                    if (Random.nextFloat() < 0.1f) {
                        val specialDonation = Random.nextDouble(50000.0, 200000.0)
                        totalDonation += specialDonation
                        events.add(AlumniEvent.MajorDonation(promoted.name, specialDonation, promoted.career))
                    }
                }

                promoted
            }
        }

        // Lv6 解锁校友基金被动收入（每月自动）
        val fundIncome = if (_networkLevel.value >= 6) {
            val executives = _alumni.value.count { it.careerLevel == CareerLevel.EXECUTIVE }
            executives * 20000.0 // 每位高管贡献2万/月基金收入
        } else 0.0
        totalDonation += fundIncome

        updateStats()
        checkNetworkLevelUp(schoolLevel)

        return AlumniMonthlyResult(
            totalDonation = totalDonation,
            referralCount = referrals,
            events = events,
            fundIncome = fundIncome
        )
    }

    /**
     * 获取校友网络提供的招生加成
     */
    fun getEnrollmentBonus(): Float {
        val alumniCount = _alumni.value.size
        val successfulCount = _alumni.value.count { it.careerLevel.ordinal >= CareerLevel.SENIOR.ordinal }
        return (alumniCount * 0.005f + successfulCount * 0.02f).coerceAtMost(0.5f)
    }

    /**
     * 获取校友网络提供的声誉加成
     */
    fun getReputationBonus(): Long {
        val executives = _alumni.value.count { it.careerLevel == CareerLevel.EXECUTIVE }
        val seniors = _alumni.value.count { it.careerLevel == CareerLevel.SENIOR }
        return (executives * 5L + seniors * 2L)
    }

    /**
     * 【新增】获取行业人脉对特定科目的收益加成
     * 行业人脉达到一定数量后，对应科目的课程收入会增加
     * 需 networkLevel >= 2 才生效
     */
    fun getIndustryBonus(career: CareerPath): Float {
        if (_networkLevel.value < 2) return 0f
        val count = _industryConnections.value[career] ?: 0
        val baseBonus = when {
            count >= 20 -> 0.20f  // 20+ 人脉 → +20% 收入
            count >= 10 -> 0.12f  // 10+ 人脉 → +12% 收入
            count >= 5 -> 0.06f   // 5+ 人脉 → +6% 收入
            count >= 2 -> 0.03f   // 2+ 人脉 → +3% 收入
            else -> 0f
        }
        // Lv5 翻倍
        return if (_networkLevel.value >= 5) baseBonus * 2f else baseBonus
    }

    /**
     * 【新增】举办校友活动（消耗资金换取效果）
     * @return 活动费用（万元），null 表示无法举办
     */
    fun canHostActivity(activityType: AlumniActivityType): Boolean {
        if (activityCooldown > 0) return false
        return when (activityType) {
            AlumniActivityType.REUNION -> _networkLevel.value >= 3
            AlumniActivityType.INDUSTRY_FORUM -> _networkLevel.value >= 4
            AlumniActivityType.FUNDRAISING_GALA -> _networkLevel.value >= 5
        }
    }

    /**
     * 获取活动费用
     */
    fun getActivityCost(activityType: AlumniActivityType): Long {
        return activityType.baseCost
    }

    /**
     * 执行校友活动
     * @return 活动结果
     */
    fun hostActivity(activityType: AlumniActivityType): AlumniActivityResult? {
        if (!canHostActivity(activityType)) return null

        activityCooldown = activityType.cooldownMonths

        return when (activityType) {
            AlumniActivityType.REUNION -> {
                // 校友聚会 → 提升捐赠意愿 + 小额即时捐赠
                val boost = 0.05f + Random.nextFloat() * 0.05f
                _alumni.update { current ->
                    current.map { it.copy(donationWillingness = (it.donationWillingness + boost).coerceAtMost(1.0f)) }
                }
                val instantDonation = _alumni.value.size * 500.0 * (1 + Random.nextDouble())
                AlumniActivityResult(
                    type = activityType,
                    description = "校友聚会成功！${_alumni.value.size}位校友回校交流",
                    donationGained = instantDonation,
                    reputationGained = 5L,
                    extraEffect = "全体校友捐赠意愿+${(boost * 100).toInt()}%"
                )
            }
            AlumniActivityType.INDUSTRY_FORUM -> {
                // 行业论坛 → 大幅提升声誉 + 提升推荐概率
                val topCareer = _industryConnections.value.maxByOrNull { it.value }?.key
                val repBonus = 15L + (_alumni.value.count { it.careerLevel.ordinal >= CareerLevel.MIDDLE.ordinal } * 2L)
                AlumniActivityResult(
                    type = activityType,
                    description = "行业论坛：${topCareer?.displayName ?: "跨行业"}专场",
                    donationGained = 0.0,
                    reputationGained = repBonus,
                    extraEffect = "声誉+${repBonus}，当月招生加成翻倍"
                )
            }
            AlumniActivityType.FUNDRAISING_GALA -> {
                // 募捐晚会 → 大额捐赠（费用高但回报可观）
                val executives = _alumni.value.count { it.careerLevel == CareerLevel.EXECUTIVE }
                val seniors = _alumni.value.count { it.careerLevel == CareerLevel.SENIOR }
                val raised = executives * Random.nextDouble(30000.0, 80000.0) +
                        seniors * Random.nextDouble(10000.0, 30000.0)
                AlumniActivityResult(
                    type = activityType,
                    description = "募捐晚会成功！${executives}位高管、${seniors}位资深出席",
                    donationGained = raised,
                    reputationGained = 10L,
                    extraEffect = "筹得 ¥${String.format("%.1f", raised / 10000)}万捐款"
                )
            }
        }
    }

    fun getAlumniByCareer(career: CareerPath): List<Alumnus> {
        return _alumni.value.filter { it.career == career }
    }

    fun getTopAlumni(count: Int = 10): List<Alumnus> {
        return _alumni.value.sortedByDescending { it.careerLevel.ordinal }.take(count)
    }

    fun getNetworkLevelProgress(): Pair<Int, Int> {
        val current = _alumni.value.count { it.careerLevel.ordinal >= CareerLevel.SENIOR.ordinal }
        val nextReq = NETWORK_LEVEL_REQUIREMENTS.getOrNull(_networkLevel.value) ?: Int.MAX_VALUE
        return current to nextReq
    }

    fun getActivityCooldown(): Int = activityCooldown

    // ==================== 持久化 ====================

    fun toJson(): String {
        return try {
            val data = AlumniPersistData(
                alumni = _alumni.value.map { a ->
                    AlumnusPersist(
                        id = a.id, name = a.name, graduationRating = a.graduationRating,
                        career = a.career.name, careerLevel = a.careerLevel.name,
                        successPotential = a.successPotential, satisfaction = a.satisfaction,
                        donationWillingness = a.donationWillingness,
                        monthsSinceGraduation = a.monthsSinceGraduation,
                        universityTier = a.universityTier?.name
                    )
                },
                networkLevel = _networkLevel.value,
                industryConnections = _industryConnections.value.map { (k, v) -> k.name to v },
                activityCooldown = activityCooldown,
                graduationSummaries = _graduationSummaries.value
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<AlumniPersistData>(json)
            val restoredAlumni = data.alumni.mapNotNull { ap ->
                val career = try { CareerPath.valueOf(ap.career) } catch (_: Exception) { return@mapNotNull null }
                val careerLevel = try { CareerLevel.valueOf(ap.careerLevel) } catch (_: Exception) { CareerLevel.ENTRY }
                val tier = ap.universityTier?.let { try { UniversityTier.valueOf(it) } catch (_: Exception) { null } }
                Alumnus(
                    id = ap.id, name = ap.name, graduationRating = ap.graduationRating,
                    career = career, careerLevel = careerLevel,
                    successPotential = ap.successPotential, satisfaction = ap.satisfaction,
                    donationWillingness = ap.donationWillingness,
                    monthsSinceGraduation = ap.monthsSinceGraduation,
                    universityTier = tier
                )
            }
            _alumni.value = restoredAlumni
            _networkLevel.value = data.networkLevel.coerceIn(1, 6)
            _industryConnections.value = data.industryConnections.mapNotNull { (k, v) ->
                try { CareerPath.valueOf(k) to v } catch (_: Exception) { null }
            }.toMap()
            activityCooldown = data.activityCooldown
            _graduationSummaries.value = data.graduationSummaries
            updateStats()
        } catch (e: Exception) {
            throw IllegalArgumentException("AlumniNetwork.restoreFromJson failed", e)
        }
    }

    fun clearAll() {
        _alumni.value = emptyList()
        _stats.value = AlumniStats()
        _networkLevel.value = 1
        _industryConnections.value = emptyMap()
        _graduationSummaries.value = emptyList()
        activityCooldown = 0
    }

    private fun checkNetworkLevelUp(schoolLevel: Int) {
        val seniorPlusCount = _alumni.value.count { it.careerLevel.ordinal >= CareerLevel.SENIOR.ordinal }
        for (level in NETWORK_LEVEL_REQUIREMENTS.indices.reversed()) {
            if (seniorPlusCount >= NETWORK_LEVEL_REQUIREMENTS[level] && schoolLevel >= NETWORK_SCHOOL_LEVEL_REQ[level]) {
                if (level + 1 > _networkLevel.value) {
                    _networkLevel.value = level + 1
                }
                break
            }
        }
    }

    /**
     * 根据大学层次+学生属性分配职业（universityTier为主要决定因素）
     */
    private fun assignCareerBasedOnStudent(student: Student): CareerPath {
        // 20% 纯随机（任何层次都有意外）
        if (Random.nextFloat() < 0.2f) return CareerPath.entries.random()

        // 大学层次决定职业分布
        val tier = student.universityTier ?: UniversityTier.NONE
        return when (tier) {
            UniversityTier.QINGBEI -> {
                // 清北：科研、科技、金融为主
                listOf(CareerPath.RESEARCH, CareerPath.RESEARCH, CareerPath.TECH, CareerPath.TECH,
                    CareerPath.FINANCE, CareerPath.GOVERNMENT).random()
            }
            UniversityTier.TOP_985, UniversityTier.NORMAL_985 -> {
                // 985：科技、金融、法律、医疗
                listOf(CareerPath.TECH, CareerPath.FINANCE, CareerPath.LAW,
                    CareerPath.MEDICAL, CareerPath.RESEARCH, CareerPath.GOVERNMENT).random()
            }
            UniversityTier.TOP_211, UniversityTier.NORMAL_211 -> {
                // 211：各行业均衡分布
                listOf(CareerPath.TECH, CareerPath.FINANCE, CareerPath.EDUCATION,
                    CareerPath.BUSINESS, CareerPath.GOVERNMENT, CareerPath.LAW).random()
            }
            UniversityTier.FIRST_TIER, UniversityTier.SECOND_TIER -> {
                // 一本/二本：教育、商业、政府为主
                listOf(CareerPath.EDUCATION, CareerPath.BUSINESS, CareerPath.GOVERNMENT,
                    CareerPath.ARTS, CareerPath.SPORTS).random()
            }
            UniversityTier.JUNIOR_COLLEGE, UniversityTier.NONE -> {
                // 专科/未升学：商业、体育、文艺
                listOf(CareerPath.BUSINESS, CareerPath.SPORTS, CareerPath.ARTS,
                    CareerPath.EDUCATION).random()
            }
        }
    }

    private fun tryPromote(alumnus: Alumnus): Alumnus {
        val nextLevel = CareerLevel.entries.getOrNull(alumnus.careerLevel.ordinal + 1) ?: return alumnus
        // 大学层次加速晋升
        val tierBonus = when (alumnus.universityTier ?: UniversityTier.NONE) {
            UniversityTier.QINGBEI -> 0.15f
            UniversityTier.TOP_985 -> 0.10f
            UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> 0.05f
            else -> 0f
        }
        val promotionChance = alumnus.successPotential * 0.3f + alumnus.graduationRating / 5f * 0.2f + tierBonus
        return if (Random.nextFloat() < promotionChance) {
            alumnus.copy(careerLevel = nextLevel)
        } else {
            alumnus
        }
    }

    private fun calculateDonation(alumnus: Alumnus): Double {
        val base = when (alumnus.careerLevel) {
            CareerLevel.ENTRY -> 500.0
            CareerLevel.JUNIOR -> 2000.0
            CareerLevel.MIDDLE -> 5000.0
            CareerLevel.SENIOR -> 15000.0
            CareerLevel.EXECUTIVE -> 50000.0
        }
        return base * (0.8 + Random.nextDouble() * 0.4) * alumnus.donationWillingness
    }

    private fun calculateSuccessPotential(student: Student): Float {
        val basePotential = student.talent * 0.3f + student.motivation * 0.2f
        val scoreFactor = (student.academicScore / 100f) * 0.2f
        // 大学层次是成功潜力的最大决定因素
        val tierFactor = when (student.universityTier ?: UniversityTier.NONE) {
            UniversityTier.QINGBEI -> 0.35f
            UniversityTier.TOP_985 -> 0.28f
            UniversityTier.NORMAL_985 -> 0.22f
            UniversityTier.TOP_211 -> 0.18f
            UniversityTier.NORMAL_211 -> 0.15f
            UniversityTier.FIRST_TIER -> 0.12f
            UniversityTier.SECOND_TIER -> 0.08f
            UniversityTier.JUNIOR_COLLEGE -> 0.05f
            UniversityTier.NONE -> 0.02f
        }
        return (basePotential + scoreFactor + tierFactor).coerceIn(0.1f, 1.0f)
    }

    private fun calculateDonationWillingness(student: Student): Float {
        val satisfactionFactor = student.satisfaction / 100f
        val reviewFactor = ((student.review?.rating?.toFloat()) ?: 3f) / 5f
        return (satisfactionFactor * 0.6f + reviewFactor * 0.4f).coerceIn(0.1f, 1.0f)
    }

    private fun updateStats() {
        val alumniList = _alumni.value
        _stats.value = AlumniStats(
            totalAlumni = alumniList.size,
            executiveCount = alumniList.count { it.careerLevel == CareerLevel.EXECUTIVE },
            seniorCount = alumniList.count { it.careerLevel == CareerLevel.SENIOR },
            averageSatisfaction = if (alumniList.isNotEmpty()) alumniList.map { it.satisfaction }.average().toFloat() else 0f,
            careerDistribution = CareerPath.entries.associateWith { career ->
                alumniList.count { it.career == career }
            },
            networkLevel = _networkLevel.value
        )
    }
}

/**
 * 校友数据
 */
data class Alumnus(
    val id: String,
    val name: String,
    val graduationRating: Float,  // 1-5星
    val career: CareerPath,
    val careerLevel: CareerLevel,
    val successPotential: Float,  // 0-1
    val satisfaction: Float,      // 毕业时的满意度
    val donationWillingness: Float, // 0-1
    val monthsSinceGraduation: Int = 0,
    val universityTier: UniversityTier? = null  // 大学录取层次
)

/**
 * 职业路径
 */
enum class CareerPath(val displayName: String, val icon: String) {
    TECH("科技行业", "💻"),
    FINANCE("金融行业", "💰"),
    EDUCATION("教育行业", "📚"),
    MEDICAL("医疗行业", "🏥"),
    LAW("法律行业", "⚖️"),
    ARTS("文艺行业", "🎨"),
    BUSINESS("商业创业", "🏢"),
    GOVERNMENT("政府机关", "🏛️"),
    RESEARCH("科学研究", "🔬"),
    SPORTS("体育行业", "⚽")
}

/**
 * 职业等级
 */
enum class CareerLevel(
    val displayName: String,
    val donationMultiplier: Float,
    val referralMultiplier: Float
) {
    ENTRY("初入职场", 0.2f, 0.5f),
    JUNIOR("初级职员", 0.5f, 0.8f),
    MIDDLE("中级骨干", 1.0f, 1.0f),
    SENIOR("高级管理", 2.0f, 1.5f),
    EXECUTIVE("企业高管", 5.0f, 2.0f)
}

/**
 * 【新增】校友活动类型
 */
enum class AlumniActivityType(
    val displayName: String,
    val description: String,
    val baseCost: Long,        // 举办费用（万元）
    val cooldownMonths: Int    // 冷却月数
) {
    REUNION("校友聚会", "邀请校友回校交流，提升捐赠意愿并获得即时捐赠",
        15L, 3),
    INDUSTRY_FORUM("行业论坛", "邀请行业校友开办论坛，大幅提升声誉和招生",
        30L, 4),
    FUNDRAISING_GALA("募捐晚会", "举办高端晚会向成功校友募捐，高投入高回报",
        50L, 6)
}

/**
 * 【新增】校友活动结果
 */
data class AlumniActivityResult(
    val type: AlumniActivityType,
    val description: String,
    val donationGained: Double = 0.0,
    val reputationGained: Long = 0L,
    val extraEffect: String = ""
)

/**
 * 校友事件
 */
sealed class AlumniEvent {
    data class Donation(val alumniName: String, val amount: Double, val career: CareerPath) : AlumniEvent()
    data class Referral(val alumniName: String, val career: CareerPath) : AlumniEvent()
    data class MajorDonation(val alumniName: String, val amount: Double, val career: CareerPath) : AlumniEvent()
    data class CampusSpeech(val alumniName: String, val career: CareerPath, val reputationBonus: Long) : AlumniEvent()
}

/**
 * 校友月度结算结果
 */
data class AlumniMonthlyResult(
    val totalDonation: Double = 0.0,
    val referralCount: Int = 0,
    val events: List<AlumniEvent> = emptyList(),
    val fundIncome: Double = 0.0
)

/**
 * 校友统计数据
 */
data class AlumniStats(
    val totalAlumni: Int = 0,
    val executiveCount: Int = 0,
    val seniorCount: Int = 0,
    val averageSatisfaction: Float = 0f,
    val careerDistribution: Map<CareerPath, Int> = emptyMap(),
    val networkLevel: Int = 1
)

/**
 * 校友持久化数据
 */
@Serializable
data class AlumniPersistData(
    val alumni: List<AlumnusPersist> = emptyList(),
    val networkLevel: Int = 1,
    val industryConnections: List<Pair<String, Int>> = emptyList(),
    val activityCooldown: Int = 0,
    val graduationSummaries: List<GraduationBatchSummary> = emptyList()
)

@Serializable
data class AlumnusPersist(
    val id: String,
    val name: String,
    val graduationRating: Float,
    val career: String,
    val careerLevel: String,
    val successPotential: Float,
    val satisfaction: Float,
    val donationWillingness: Float,
    val monthsSinceGraduation: Int,
    val universityTier: String? = null
)

/**
 * 一届毕业生批次总结
 */
@Serializable
data class GraduationBatchSummary(
    val year: Int,                              // 毕业年份
    val totalStudents: Int,                     // 毕业生总数
    val averageScore: Float,                    // 平均高考分
    val highestScore: Float,                    // 最高分
    val bengkeRate: Float,                      // 本科率(%)
    val key985Count: Int,                       // 985录取人数
    val qingbeiCount: Int,                      // 清北录取人数
    val topStudents: List<GraduationTopStudent> = emptyList(), // 优秀毕业生（前5名）
    val universityDistribution: Map<String, Int> = emptyMap(), // 大学层次分布
    // null 表示旧版本记录，历史奖励状态不可证明，必须按已结算处理。
    val settlementCompleted: Boolean? = null,
    val settledCashBonus: Double = 0.0,
    val settledReputationDelta: Long = 0L
)

/**
 * 毕业生总结中的优秀学生
 */
@Serializable
data class GraduationTopStudent(
    val name: String,
    val score: Float,
    val university: String?,
    val tierName: String        // 大学层次名称
)
