package com.arktools.xiao.domain.employment

import com.arktools.xiao.domain.model.UniversityTier
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 升学反馈系统（原就业市场，改为追踪毕业生大学去向和后续反馈）
 *
 * 核心逻辑（参考《高考工厂模拟》）：
 * 1. 高考毕业 → 根据 universityTier 确定去向（升学/复读/就业）
 * 2. 升学生在大学期间(48个月)不产生就业数据
 * 3. 大学毕业后根据大学层次分配职业结果
 * 4. 职业结果反馈学校声誉和招生吸引力
 *
 * 简化实现：高考结束即计算长期职业前景评分，
 * 每月推进大学在读生毕业，毕业后产生职业反馈。
 */

enum class Industry(val displayName: String, val icon: String) {
    TECHNOLOGY("科技互联网", "💻"),
    FINANCE("金融投资", "💰"),
    EDUCATION("教育培训", "📚"),
    HEALTHCARE("医疗健康", "🏥"),
    MEDIA("传媒娱乐", "🎬"),
    GOVERNMENT("公务事业", "🏛️"),
    ENGINEERING("工程制造", "🏗️"),
    COMMERCE("商业零售", "🛒"),
    LAW("法律咨询", "⚖️"),
    RESEARCH("科学研究", "🔬")
}

enum class GraduateStatus(val displayName: String) {
    IN_UNIVERSITY("大学在读"),       // 正在读大学
    EMPLOYED("已就业"),              // 大学毕业后就业
    SELF_EMPLOYED("自主创业"),       // 大学毕业后创业
    FURTHER_STUDY("继续深造"),       // 读研/读博
    SEEKING("待就业"),               // 大学毕业后求职中
    NOT_ADMITTED("未升学")           // 未被大学录取（直接就业或复读）
}

enum class SalaryTier(val displayName: String, val minSalary: Int, val maxSalary: Int) {
    ENTRY("入门级", 4000, 8000),
    JUNIOR("初级", 8000, 15000),
    MID("中级", 15000, 25000),
    SENIOR("高级", 25000, 40000),
    EXECUTIVE("高管", 40000, 80000)
}

data class GraduateRecord(
    val studentName: String,
    val graduateYear: Int,
    val graduateMonth: Int,
    val gaoKaoScore: Float,
    val universityTier: UniversityTier,
    val universityName: String?,
    val satisfaction: Float,
    val studentId: String? = null,
    var status: GraduateStatus = GraduateStatus.IN_UNIVERSITY,
    var industry: Industry? = null,
    var salaryTier: SalaryTier? = null,
    var monthsInUniversity: Int = 0, // 大学已读月数
    var feedbackScore: Int = 0,      // 对母校的评价(-5到+5)
    val courseId: String = ""
) {
    /** 大学学制月数（专科36，本科48，985/211可能读研72） */
    val universityDuration: Int
        get() = when (universityTier) {
            UniversityTier.QINGBEI -> 72       // 清北大概率读研
            UniversityTier.TOP_985 -> 60       // 顶尖985多读研
            UniversityTier.NORMAL_985 -> 48
            UniversityTier.TOP_211 -> 48
            UniversityTier.NORMAL_211 -> 48
            UniversityTier.FIRST_TIER -> 48
            UniversityTier.SECOND_TIER -> 48
            UniversityTier.JUNIOR_COLLEGE -> 36
            UniversityTier.NONE -> 0           // 未录取，不读大学
        }
}

data class EmploymentStats(
    val totalGraduates: Int = 0,
    val inUniversityCount: Int = 0,
    val employedCount: Int = 0,
    val selfEmployedCount: Int = 0,
    val furtherStudyCount: Int = 0,
    val seekingCount: Int = 0,
    val notAdmittedCount: Int = 0,
    val universityRate: Float = 0f,          // 升学率
    val employmentRate: Float = 0f,          // 兼容旧系统：等同于升学率
    val averageSalary: Int = 0,
    val averageMonthsToEmploy: Float = 0f,   // 兼容旧UI
    val topIndustries: List<Pair<Industry, Int>> = emptyList(),
    val partnerCount: Int = 0,               // 兼容旧UI
    val averageFeedback: Float = 0f
)

sealed class EmploymentEvent {
    data class UniversityGraduation(
        val studentName: String,
        val universityTier: UniversityTier,
        val industry: Industry,
        val salaryTier: SalaryTier
    ) : EmploymentEvent()

    data class FeedbackReport(
        val averageScore: Float,
        val enrollmentBonus: Int
    ) : EmploymentEvent()

    data class AlumniSuccess(
        val studentName: String,
        val achievement: String,
        val reputationBonus: Long
    ) : EmploymentEvent()
}

data class EmploymentMonthlyResult(
    val newEmployments: Int = 0,
    val enrollmentBonus: Int = 0,
    val reputationBonus: Int = 0,
    val events: List<EmploymentEvent> = emptyList(),
    val currentUniversityRate: Float = 0f,
    val currentEmploymentRate: Float = 0f  // 兼容旧系统引用
)

/**
 * 历史毕业生数据校准结果。
 * 调用方应在 [changed] 为 true 时立即持久化 [EmploymentMarket.toJson]，
 * 使旧存档的修复结果在下一次读档时保持不变。
 */
data class GraduateCalibrationResult(
    val changed: Boolean = false,
    val correctedProgressCount: Int = 0,
    val graduatedCount: Int = 0,
    val deduplicatedCount: Int = 0
)

/**
 * 职业辅导项目（校长主动决策 - 保留兼容）
 */
enum class CareerProgram(
    val displayName: String,
    val description: String,
    val monthlyCost: Double,
    val employmentBoost: Float,
    val tierBoost: Int,
    val requiredSchoolLevel: Int
) {
    BASIC("升学指导", "高考志愿填报辅导+模拟", 2.0, 0.05f, 0, 1),
    INTERNSHIP("名校衔接班", "提前适应大学学习节奏", 5.0, 0.10f, 1, 2),
    CAREER_FAIR("高校招生宣讲", "邀请大学招生官到校宣讲", 8.0, 0.08f, 1, 3),
    ELITE_TRACK("强基计划培养", "对接清北强基/综评", 15.0, 0.12f, 2, 4),
    STARTUP_INCUBATOR("创新实验室", "培养学生创新能力和科研素养", 20.0, 0.06f, 0, 5)
}

data class EmploymentMarketState(
    val graduates: List<GraduateRecord> = emptyList(),
    val stats: EmploymentStats = EmploymentStats(),
    val recentEvents: List<EmploymentEvent> = emptyList(),
    val activePrograms: List<CareerProgram> = emptyList(),
    val governmentBoostFactor: Float = 1f,
    val employers: List<Employer> = emptyList()
)

@Singleton
class EmploymentMarket @Inject constructor() {

    private val _state = MutableStateFlow(EmploymentMarketState())
    val state: StateFlow<EmploymentMarketState> = _state.asStateFlow()

    data class Snapshot(
        val state: EmploymentMarketState
    )

    fun snapshotState(): Snapshot = Snapshot(
        state = _state.value.deepCopy()
    )

    fun restoreSnapshot(snapshot: Snapshot) {
        _state.value = snapshot.state.deepCopy()
    }

    private fun EmploymentMarketState.deepCopy(): EmploymentMarketState =
        copy(
            graduates = graduates.map { it.copy() },
            stats = stats.copy(
                topIndustries = stats.topIndustries.toList()
            ),
            recentEvents = recentEvents.toList(),
            activePrograms = activePrograms.toList(),
            employers = employers.map { it.copy() }
        )

    fun reset() {
        _state.value = EmploymentMarketState()
    }

    fun toJson(): String {
        return try {
            val graduates = _state.value.graduates
            val data = EmploymentPersistData(
                graduates = graduates.map { g ->
                    GraduateRecordPersist(
                        studentId = g.studentId,
                        studentName = g.studentName,
                        graduateYear = g.graduateYear,
                        graduateMonth = g.graduateMonth,
                        gaoKaoScore = g.gaoKaoScore,
                        universityTier = g.universityTier.name,
                        universityName = g.universityName,
                        satisfaction = g.satisfaction,
                        status = g.status.name,
                        industry = g.industry?.name,
                        salaryTier = g.salaryTier?.name,
                        monthsInUniversity = g.monthsInUniversity,
                        feedbackScore = g.feedbackScore,
                        courseId = g.courseId
                    )
                },
                activePrograms = _state.value.activePrograms.map { it.name },
                governmentBoostFactor = _state.value.governmentBoostFactor
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    /**
     * 从持久化数据恢复状态。
     *
     * @return 是否在恢复时移除了无效或重复的历史毕业生记录；调用方应在返回 true
     * 时将 [toJson] 的结果回写，以免每次读档重复执行同一修复。
     */
    fun restoreFromJson(json: String): Boolean {
        if (json.isBlank()) return false
        return try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<EmploymentPersistData>(json)
            val restoredGraduates = deduplicateGraduates(data.graduates.mapNotNull { gp ->
                val tier = try { UniversityTier.valueOf(gp.universityTier) } catch (_: Exception) { UniversityTier.NONE }
                val status = try { GraduateStatus.valueOf(gp.status) } catch (_: Exception) { return@mapNotNull null }
                val industry = gp.industry?.let { try { Industry.valueOf(it) } catch (_: Exception) { null } }
                val salary = gp.salaryTier?.let { try { SalaryTier.valueOf(it) } catch (_: Exception) { null } }
                GraduateRecord(
                    studentId = gp.studentId,
                    studentName = gp.studentName,
                    graduateYear = gp.graduateYear,
                    graduateMonth = gp.graduateMonth,
                    gaoKaoScore = gp.gaoKaoScore,
                    universityTier = tier,
                    universityName = gp.universityName,
                    satisfaction = gp.satisfaction,
                    status = status,
                    industry = industry,
                    salaryTier = salary,
                    monthsInUniversity = gp.monthsInUniversity,
                    feedbackScore = gp.feedbackScore,
                    courseId = gp.courseId
                )
            })
            val programs = data.activePrograms.mapNotNull { name ->
                try { CareerProgram.valueOf(name) } catch (_: Exception) { null }
            }
            _state.value = EmploymentMarketState(
                graduates = restoredGraduates,
                stats = calculateStats(restoredGraduates),
                activePrograms = programs,
                governmentBoostFactor = data.governmentBoostFactor,
                employers = generateEmployers(restoredGraduates)
            )
            restoredGraduates.size != data.graduates.size
        } catch (e: Exception) {
            throw IllegalArgumentException("EmploymentMarket.restoreFromJson failed", e)
        }
    }

    private val random = java.util.Random()

    /**
     * 校准从旧存档恢复的毕业生记录。
     *
     * 大学在读进度以毕业日期和当前游戏日期为准，避免旧版本按月累加的值在
     * 跳月、离线推进或存档恢复后失真。已超过学制的旧记录使用稳定键派生的
     * 确定性结果结业，因此同一份未及时回写的存档重复校准也不会重新随机职业。
     */
    fun calibrateHistoricalGraduates(
        currentYear: Int,
        currentMonth: Int
    ): GraduateCalibrationResult {
        while (true) {
            val state = _state.value
            val deduplicatedGraduates = deduplicateGraduates(state.graduates)
            val deduplicatedCount = state.graduates.size - deduplicatedGraduates.size
            var correctedProgressCount = 0
            var graduatedCount = 0

            val calibratedGraduates = deduplicatedGraduates.map { graduate ->
                val calibrated = when {
                    graduate.universityTier == UniversityTier.NONE -> {
                        graduate.copy(status = GraduateStatus.NOT_ADMITTED)
                    }

                    graduate.status == GraduateStatus.IN_UNIVERSITY -> {
                        val elapsedMonths = elapsedUniversityMonths(
                            currentYear = currentYear,
                            currentMonth = currentMonth,
                            graduateYear = graduate.graduateYear,
                            graduateMonth = graduate.graduateMonth
                        )
                        val canonicalMonths = elapsedMonths.coerceIn(0, graduate.universityDuration)
                        if (elapsedMonths >= graduate.universityDuration) {
                            graduatedCount++
                            assignDeterministicPostUniversityCareer(
                                graduate.copy(monthsInUniversity = graduate.universityDuration)
                            )
                        } else {
                            graduate.copy(monthsInUniversity = canonicalMonths)
                        }
                    }

                    else -> graduate
                }
                if (calibrated != graduate) correctedProgressCount++
                calibrated
            }

            val changed = deduplicatedCount > 0 || calibratedGraduates != state.graduates
            if (!changed) {
                return GraduateCalibrationResult()
            }

            val calibratedState = state.copy(
                graduates = calibratedGraduates,
                stats = calculateStats(calibratedGraduates),
                employers = generateEmployers(calibratedGraduates)
            )
            if (_state.compareAndSet(state, calibratedState)) {
                return GraduateCalibrationResult(
                    changed = true,
                    correctedProgressCount = correctedProgressCount,
                    graduatedCount = graduatedCount,
                    deduplicatedCount = deduplicatedCount
                )
            }
        }
    }

    /**
     * 注册高考毕业生（由 conductGaoKao 或历史补录调用）。
     *
     * 旧存档没有毕业生 ID，故以姓名、毕业年月、高考分数和录取层次组成兼容键。
     * 该键在正常毕业与历史补录路径中相同，重复调用不会创建第二条记录。
     */
    fun registerGraduate(
        studentId: String? = null,
        name: String,
        year: Int,
        month: Int,
        gaoKaoScore: Float,
        universityTier: UniversityTier,
        universityName: String?,
        satisfaction: Float,
        courseId: String = ""
    ): Boolean {
        val status = if (universityTier == UniversityTier.NONE) {
            GraduateStatus.NOT_ADMITTED
        } else {
            GraduateStatus.IN_UNIVERSITY
        }

        val graduate = GraduateRecord(
            studentId = studentId,
            studentName = name,
            graduateYear = year,
            graduateMonth = month,
            gaoKaoScore = gaoKaoScore,
            universityTier = universityTier,
            universityName = universityName,
            satisfaction = satisfaction,
            status = status,
            // 未升学者直接给反馈
            feedbackScore = if (universityTier == UniversityTier.NONE) -2 else 0,
            courseId = courseId
        )
        while (true) {
            val state = _state.value
            val deduplicated = deduplicateGraduates(state.graduates)
            val existingIndex = deduplicated.indexOfFirst {
                sameGraduateIdentity(it, graduate)
            }
            if (existingIndex >= 0) {
                val mergedGraduates = deduplicated.toMutableList().apply {
                    this[existingIndex] = mergeDuplicateGraduate(
                        this[existingIndex],
                        graduate
                    )
                }
                if (mergedGraduates == state.graduates) return false
                val repairedState = state.copy(
                    graduates = mergedGraduates,
                    stats = calculateStats(mergedGraduates),
                    employers = generateEmployers(mergedGraduates)
                )
                if (_state.compareAndSet(state, repairedState)) return false
            } else {
                val updatedGraduates = deduplicated + graduate
                // 注册后立即重算统计，确保"毕业生去向总览"在同一帧内即可显示正确数据
                val updatedState = state.copy(
                    graduates = updatedGraduates,
                    stats = calculateStats(updatedGraduates),
                    employers = generateEmployers(updatedGraduates)
                )
                if (_state.compareAndSet(state, updatedState)) return true
            }
        }
    }

    /**
     * 兼容旧接口（将GPA转回高考分和tier）
     */
    fun registerGraduate(name: String, year: Int, month: Int, gpa: Float, satisfaction: Float): Boolean {
        val estimatedScore = gpa * 187.5f  // GPA 4.0 → 750分
        val tier = UniversityTier.fromScore(estimatedScore)
        return registerGraduate(
            null,
            name,
            year,
            month,
            estimatedScore,
            tier,
            null,
            satisfaction
        )
    }

    private fun elapsedUniversityMonths(
        currentYear: Int,
        currentMonth: Int,
        graduateYear: Int,
        graduateMonth: Int
    ): Int = (currentYear - graduateYear) * 12 + (currentMonth - graduateMonth)

    /**
     * 为缺少 ID 的历史数据生成稳定兼容键。
     * 不包含学校名称，避免早期存档中空名称与后来补录的正式名称形成重复记录。
     */
    private fun legacyGraduateKey(graduate: GraduateRecord): String =
        listOf(
            graduate.studentName.trim().lowercase(),
            graduate.graduateYear.toString(),
            graduate.graduateMonth.toString(),
            graduate.gaoKaoScore.toBits().toString(),
            graduate.universityTier.name
        ).joinToString("|")

    private fun stableGraduateKey(graduate: GraduateRecord): String =
        graduate.studentId?.takeIf { it.isNotBlank() }
            ?.let { "id:$it" }
            ?: legacyGraduateKey(graduate)

    private fun sameGraduateIdentity(
        first: GraduateRecord,
        second: GraduateRecord
    ): Boolean {
        val firstId = first.studentId?.takeIf { it.isNotBlank() }
        val secondId = second.studentId?.takeIf { it.isNotBlank() }
        return if (firstId != null && secondId != null) {
            firstId == secondId
        } else {
            legacyGraduateKey(first) == legacyGraduateKey(second)
        }
    }

    private fun mergeDuplicateGraduate(
        existing: GraduateRecord,
        candidate: GraduateRecord
    ): GraduateRecord {
        val preferred = if (shouldReplaceDuplicate(existing, candidate)) {
            candidate
        } else {
            existing
        }
        val mergedId = preferred.studentId?.takeIf { it.isNotBlank() }
            ?: existing.studentId?.takeIf { it.isNotBlank() }
            ?: candidate.studentId?.takeIf { it.isNotBlank() }
        val mergedCourseId = preferred.courseId.ifBlank {
            existing.courseId.ifBlank { candidate.courseId }
        }
        return preferred.copy(
            studentId = mergedId,
            courseId = mergedCourseId
        )
    }

    private fun deduplicateGraduates(
        graduates: List<GraduateRecord>
    ): List<GraduateRecord> {
        val unique = mutableListOf<GraduateRecord>()
        graduates.forEach { graduate ->
            val existingIndex = unique.indexOfFirst {
                sameGraduateIdentity(it, graduate)
            }
            if (existingIndex < 0) {
                unique += graduate
            } else {
                unique[existingIndex] = mergeDuplicateGraduate(
                    unique[existingIndex],
                    graduate
                )
            }
        }
        return unique
    }

    /** 优先保留已有职业结论；同类状态下再选择职业字段或进度更完整的记录。 */
    private fun shouldReplaceDuplicate(existing: GraduateRecord, candidate: GraduateRecord): Boolean {
        fun statusPriority(record: GraduateRecord): Int = when (record.status) {
            GraduateStatus.IN_UNIVERSITY -> 0
            GraduateStatus.NOT_ADMITTED -> 1
            GraduateStatus.SEEKING -> 2
            GraduateStatus.EMPLOYED,
            GraduateStatus.SELF_EMPLOYED,
            GraduateStatus.FURTHER_STUDY -> 3
        }
        fun detailPriority(record: GraduateRecord): Int =
            (if (record.industry != null) 2 else 0) +
                (if (record.salaryTier != null) 2 else 0) +
                (if (record.feedbackScore != 0) 1 else 0)

        val existingStatus = statusPriority(existing)
        val candidateStatus = statusPriority(candidate)
        if (candidateStatus != existingStatus) return candidateStatus > existingStatus

        val existingDetail = detailPriority(existing)
        val candidateDetail = detailPriority(candidate)
        if (candidateDetail != existingDetail) return candidateDetail > existingDetail

        return candidate.monthsInUniversity.coerceIn(0, candidate.universityDuration) >
            existing.monthsInUniversity.coerceIn(0, existing.universityDuration)
    }

    private fun deterministicUnit(graduate: GraduateRecord, salt: Int): Float {
        var hash = stableGraduateKey(graduate).hashCode()
        hash = 31 * hash + salt
        return (hash.toLong() and 0x7fffffffL).toFloat() / Int.MAX_VALUE.toFloat()
    }

    private fun deterministicIndex(graduate: GraduateRecord, salt: Int, size: Int): Int {
        val hash = (deterministicUnit(graduate, salt) * Int.MAX_VALUE).toInt()
        return hash % size
    }

    private fun assignDeterministicPostUniversityCareer(graduate: GraduateRecord): GraduateRecord {
        val furtherStudyChance = when (graduate.universityTier) {
            UniversityTier.QINGBEI -> 0.5f
            UniversityTier.TOP_985 -> 0.3f
            UniversityTier.NORMAL_985 -> 0.15f
            else -> 0.05f
        }
        if (deterministicUnit(graduate, 1) < furtherStudyChance) {
            return graduate.copy(
                status = GraduateStatus.FURTHER_STUDY,
                industry = null,
                salaryTier = null,
                feedbackScore = 4
            )
        }

        val startupChance = if (graduate.universityTier.ordinal <= UniversityTier.NORMAL_985.ordinal) 0.08f else 0.03f
        if (deterministicUnit(graduate, 2) < startupChance) {
            return graduate.copy(
                status = GraduateStatus.SELF_EMPLOYED,
                industry = com.arktools.xiao.domain.model.UniversityAcademicCatalog.pickIndustryDeterministic(
                    graduate.courseId,
                    deterministicIndex(graduate, 3, 32)
                ),
                salaryTier = SalaryTier.MID,
                feedbackScore = deterministicIndex(graduate, 4, 3) + 2
            )
        }

        val salary = when (graduate.universityTier) {
            UniversityTier.QINGBEI -> SalaryTier.SENIOR
            UniversityTier.TOP_985, UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> SalaryTier.MID
            UniversityTier.NORMAL_211, UniversityTier.FIRST_TIER -> SalaryTier.JUNIOR
            UniversityTier.SECOND_TIER, UniversityTier.JUNIOR_COLLEGE, UniversityTier.NONE -> SalaryTier.ENTRY
        }
        val feedback = when (graduate.universityTier) {
            UniversityTier.QINGBEI, UniversityTier.TOP_985 -> deterministicIndex(graduate, 5, 3) + 3
            UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> deterministicIndex(graduate, 5, 3) + 1
            UniversityTier.NORMAL_211, UniversityTier.FIRST_TIER -> deterministicIndex(graduate, 5, 3)
            UniversityTier.SECOND_TIER -> deterministicIndex(graduate, 5, 3) - 1
            else -> deterministicIndex(graduate, 5, 3) - 2
        }
        return graduate.copy(
            status = GraduateStatus.EMPLOYED,
            industry = com.arktools.xiao.domain.model.UniversityAcademicCatalog.pickIndustryDeterministic(
                graduate.courseId,
                deterministicIndex(graduate, 6, 32)
            ),
            salaryTier = salary,
            feedbackScore = feedback
        )
    }

    /**
     * 设置政府评级带来的就业加成
     */
    fun setGovernmentBoostFactor(factor: Float) {
        _state.update { it.copy(governmentBoostFactor = factor) }
    }

    /**
     * 启用职业辅导项目
     */
    fun enableProgram(program: CareerProgram): Boolean {
        val current = _state.value.activePrograms
        if (program in current) return false
        _state.update { it.copy(activePrograms = current + program) }
        return true
    }

    /**
     * 停用职业辅导项目
     */
    fun disableProgram(program: CareerProgram) {
        _state.update { it.copy(activePrograms = it.activePrograms - program) }
    }

    /**
     * 获取职业辅导月费总计
     */
    fun getProgramMonthlyCost(): Double {
        return _state.value.activePrograms.sumOf { it.monthlyCost }
    }

    /**
     * 每月推进：大学在读生毕业 → 分配职业结果 → 反馈声誉
     */
    fun advanceMonth(
        schoolReputation: Long, currentYear: Int, currentMonth: Int,
        governmentBoostFactor: Float = 1f,
        schoolLevel: Int = 1,
        employmentSupportLevel: Int = 0
    ): EmploymentMonthlyResult {
        val events = mutableListOf<EmploymentEvent>()
        var newEmployments = 0
        var reputationBonus = 0

        _state.update { it.copy(governmentBoostFactor = governmentBoostFactor) }

        _state.update { state ->
            val updatedGraduates = state.graduates.map { grad ->
                when (grad.status) {
                    GraduateStatus.IN_UNIVERSITY -> {
                        val updated = grad.copy(monthsInUniversity = grad.monthsInUniversity + 1)
                        // 大学毕业检查
                        if (updated.monthsInUniversity >= updated.universityDuration) {
                            val result = assignPostUniversityCareer(
                                updated,
                                governmentBoostFactor,
                                employmentSupportLevel
                            )
                            newEmployments++
                            events.add(EmploymentEvent.UniversityGraduation(
                                studentName = result.studentName,
                                universityTier = result.universityTier,
                                industry = result.industry ?: Industry.TECHNOLOGY,
                                salaryTier = result.salaryTier ?: SalaryTier.ENTRY
                            ))
                            // 大学毕业反馈声誉
                            reputationBonus += calculateGraduationRepBonus(result.universityTier)
                            result
                        } else {
                            updated
                        }
                    }
                    else -> grad
                }
            }

            val newStats = calculateStats(updatedGraduates)
            state.copy(
                graduates = updatedGraduates,
                stats = newStats,
                recentEvents = events,
                employers = generateEmployers(updatedGraduates)
            )
        }

        // 升学率反馈招生（每季度）
        val enrollmentBonus = if (currentMonth % 3 == 0) {
            calculateEnrollmentBonus()
        } else 0

        val uniRate = _state.value.stats.universityRate
        return EmploymentMonthlyResult(
            newEmployments = newEmployments,
            enrollmentBonus = enrollmentBonus,
            reputationBonus = reputationBonus,
            events = events,
            currentUniversityRate = uniRate,
            currentEmploymentRate = uniRate  // 兼容旧系统
        )
    }

    /**
     * 获取升学率对招生的加成
     */
    fun getEnrollmentBonus(): Int {
        val stats = _state.value.stats
        return when {
            stats.universityRate >= 0.95f -> 8
            stats.universityRate >= 0.90f -> 5
            stats.universityRate >= 0.80f -> 3
            stats.universityRate >= 0.70f -> 1
            else -> 0
        }
    }

    /**
     * 大学毕业后分配职业
     */
    private fun assignPostUniversityCareer(
        graduate: GraduateRecord,
        governmentBoostFactor: Float = 1f,
        employmentSupportLevel: Int = 0
    ): GraduateRecord {
        val supportLevel = employmentSupportLevel.coerceIn(0, 3)
        val qualityBoostChance = (
            (governmentBoostFactor - 1f).coerceAtLeast(0f) * 0.7f + supportLevel * 0.06f
        ).coerceIn(0f, 0.30f)
        // 清北/顶尖985高概率继续深造
        val furtherStudyChance = when (graduate.universityTier) {
            UniversityTier.QINGBEI -> 0.5f
            UniversityTier.TOP_985 -> 0.3f
            UniversityTier.NORMAL_985 -> 0.15f
            else -> 0.05f
        }
        val boostedFurtherStudyChance = (furtherStudyChance + qualityBoostChance * 0.25f).coerceAtMost(0.75f)
        if (random.nextFloat() < boostedFurtherStudyChance) {
            return graduate.copy(
                status = GraduateStatus.FURTHER_STUDY,
                feedbackScore = 4  // 深造=对母校高评价
            )
        }

        // 创业概率
        val startupChance = if (graduate.universityTier.ordinal <= UniversityTier.NORMAL_985.ordinal) 0.08f else 0.03f
        val boostedStartupChance = (startupChance + qualityBoostChance * 0.15f).coerceAtMost(0.20f)
        if (random.nextFloat() < boostedStartupChance) {
            return graduate.copy(
                status = GraduateStatus.SELF_EMPLOYED,
                industry = com.arktools.xiao.domain.model.UniversityAcademicCatalog.pickIndustry(
                    graduate.courseId,
                    random
                ),
                salaryTier = SalaryTier.MID,
                feedbackScore = random.nextInt(3) + 2
            )
        }

        // 正常就业：大学层次决定薪资和行业
        val (industry, baseSalary) = assignIndustryAndSalary(graduate.universityTier, graduate.courseId)
        val salary = upgradeSalaryTier(baseSalary, qualityBoostChance)
        val feedback = when (graduate.universityTier) {
            UniversityTier.QINGBEI, UniversityTier.TOP_985 -> random.nextInt(3) + 3  // 3~5
            UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> random.nextInt(3) + 1  // 1~3
            UniversityTier.NORMAL_211, UniversityTier.FIRST_TIER -> random.nextInt(3)  // 0~2
            UniversityTier.SECOND_TIER -> random.nextInt(3) - 1  // -1~1
            else -> random.nextInt(3) - 2  // -2~0
        }

        return graduate.copy(
            status = GraduateStatus.EMPLOYED,
            industry = industry,
            salaryTier = salary,
            feedbackScore = feedback
        )
    }

    private fun assignIndustryAndSalary(
        tier: UniversityTier,
        courseId: String = ""
    ): Pair<Industry, SalaryTier> {
        val salary = when (tier) {
            UniversityTier.QINGBEI -> SalaryTier.SENIOR
            UniversityTier.TOP_985 -> SalaryTier.MID
            UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> SalaryTier.MID
            UniversityTier.NORMAL_211, UniversityTier.FIRST_TIER -> SalaryTier.JUNIOR
            UniversityTier.SECOND_TIER, UniversityTier.JUNIOR_COLLEGE -> SalaryTier.ENTRY
            UniversityTier.NONE -> SalaryTier.ENTRY
        }
        val industry = com.arktools.xiao.domain.model.UniversityAcademicCatalog.pickIndustry(
            courseId,
            random
        )
        return industry to salary
    }

    private fun upgradeSalaryTier(base: SalaryTier, chance: Float): SalaryTier {
        if (chance <= 0f || random.nextFloat() >= chance) return base
        val nextOrdinal = (base.ordinal + 1).coerceAtMost(SalaryTier.entries.lastIndex)
        return SalaryTier.entries[nextOrdinal]
    }

    private fun calculateGraduationRepBonus(tier: UniversityTier): Int {
        return when (tier) {
            UniversityTier.QINGBEI -> 5
            UniversityTier.TOP_985 -> 3
            UniversityTier.NORMAL_985 -> 2
            UniversityTier.TOP_211 -> 1
            else -> 0
        }
    }

    private fun calculateEnrollmentBonus(): Int {
        val stats = _state.value.stats
        val rateBonus = when {
            stats.universityRate >= 0.90f -> 5
            stats.universityRate >= 0.80f -> 3
            stats.universityRate >= 0.70f -> 1
            else -> 0
        }
        val feedbackBonus = when {
            stats.averageFeedback >= 3f -> 3
            stats.averageFeedback >= 1f -> 1
            stats.averageFeedback < -1f -> -2
            else -> 0
        }
        return rateBonus + feedbackBonus
    }

    /**
     * 获取毕业生列表（兼容旧UI，将GraduateRecord转为GraduateEmployment）
     */
    fun getGraduatesForDisplay(): List<GraduateEmployment> {
        return _state.value.graduates.map { record ->
            GraduateEmployment(
                studentName = record.studentName,
                graduateYear = record.graduateYear,
                graduateMonth = record.graduateMonth,
                gpa = record.gaoKaoScore / 187.5f,
                satisfaction = record.satisfaction,
                status = when (record.status) {
                    GraduateStatus.EMPLOYED -> EmploymentStatus.EMPLOYED
                    GraduateStatus.SELF_EMPLOYED -> EmploymentStatus.SELF_EMPLOYED
                    GraduateStatus.FURTHER_STUDY -> EmploymentStatus.FURTHER_STUDY
                    GraduateStatus.IN_UNIVERSITY -> EmploymentStatus.IN_UNIVERSITY
                    GraduateStatus.SEEKING -> EmploymentStatus.SEEKING
                    GraduateStatus.NOT_ADMITTED -> EmploymentStatus.UNEMPLOYED
                },
                industry = record.industry,
                employer = record.universityName,
                salaryTier = record.salaryTier,
                monthsToEmployment = record.monthsInUniversity,
                feedbackScore = record.feedbackScore
            )
        }
    }

    /**
     * 根据已就业毕业生生成合作企业列表。
     * 同一行业+薪资层级的毕业生会聚合为一家企业，录用人数越多、评价越高，合作星级越高。
     */
    private fun generateEmployers(graduates: List<GraduateRecord>): List<Employer> {
        val employed = graduates.filter { it.status == GraduateStatus.EMPLOYED }
        if (employed.isEmpty()) return emptyList()

        return employed
            .groupBy { Pair(it.industry ?: Industry.TECHNOLOGY, it.salaryTier ?: SalaryTier.ENTRY) }
            .map { (key, list) ->
                val (industry, salaryTier) = key
                val tier = when (salaryTier) {
                    SalaryTier.EXECUTIVE -> EmployerTier.TOP
                    SalaryTier.SENIOR -> EmployerTier.LARGE
                    SalaryTier.MID -> EmployerTier.MEDIUM
                    SalaryTier.JUNIOR -> EmployerTier.SMALL
                    SalaryTier.ENTRY -> EmployerTier.STARTUP
                }
                val avgFeedback = list.map { it.feedbackScore.toFloat() * 10f + 50f }.average().toFloat()
                Employer(
                    id = "${industry.name}_${salaryTier.name}",
                    name = "${industry.displayName}${tier.displayName}",
                    industry = industry,
                    tier = tier,
                    partnershipLevel = (list.size / 3).coerceIn(1, 5),
                    hiredCount = list.size,
                    satisfactionWithSchool = avgFeedback.coerceIn(0f, 100f),
                    lastHireYear = list.maxOfOrNull { it.graduateYear } ?: 0
                )
            }
            .sortedByDescending { it.partnershipLevel }
    }

    private fun calculateStats(graduates: List<GraduateRecord>): EmploymentStats {
        if (graduates.isEmpty()) return EmploymentStats()

        val inUni = graduates.count { it.status == GraduateStatus.IN_UNIVERSITY }
        val employed = graduates.filter { it.status == GraduateStatus.EMPLOYED }
        val selfEmployed = graduates.count { it.status == GraduateStatus.SELF_EMPLOYED }
        val furtherStudy = graduates.count { it.status == GraduateStatus.FURTHER_STUDY }
        val notAdmitted = graduates.count { it.status == GraduateStatus.NOT_ADMITTED }

        // 升学率 = (在读+已就业+深造+创业) / 总人数
        val admittedCount = graduates.size - notAdmitted
        val universityRate = if (graduates.isNotEmpty()) {
            admittedCount.toFloat() / graduates.size
        } else 0f

        val avgSalary = if (employed.isNotEmpty()) {
            employed.mapNotNull { it.salaryTier }
                .map { (it.minSalary + it.maxSalary) / 2 }
                .average().toInt()
        } else 0

        val industryCount = employed.groupBy { it.industry }
            .mapNotNull { (industry, list) -> industry?.let { it to list.size } }
            .sortedByDescending { it.second }
            .take(5)

        val avgFeedback = graduates.filter { it.feedbackScore != 0 }.let {
            if (it.isNotEmpty()) it.map { g -> g.feedbackScore }.average().toFloat() else 0f
        }

        return EmploymentStats(
            totalGraduates = graduates.size,
            inUniversityCount = inUni,
            employedCount = employed.size,
            selfEmployedCount = selfEmployed,
            furtherStudyCount = furtherStudy,
            seekingCount = graduates.count { it.status == GraduateStatus.SEEKING },
            notAdmittedCount = notAdmitted,
            universityRate = universityRate,
            employmentRate = universityRate,  // 兼容旧系统
            averageSalary = avgSalary,
            topIndustries = industryCount,
            averageFeedback = avgFeedback
        )
    }
}

// ============================================================
// 以下为兼容旧 UI 的类型定义（保留接口，语义已更新）
// ============================================================

enum class EmploymentStatus(val displayName: String) {
    EMPLOYED("已就业"),
    SELF_EMPLOYED("自主创业"),
    FURTHER_STUDY("继续深造"),
    IN_UNIVERSITY("大学在读"),
    SEEKING("求职中"),
    UNEMPLOYED("未升学")
}

data class GraduateEmployment(
    val studentName: String,
    val graduateYear: Int,
    val graduateMonth: Int,
    val gpa: Float,
    val satisfaction: Float,
    var status: EmploymentStatus = EmploymentStatus.SEEKING,
    var industry: Industry? = null,
    var employer: String? = null,
    var salaryTier: SalaryTier? = null,
    var monthsToEmployment: Int = 0,
    var feedbackScore: Int = 0
)

enum class EmployerTier(val displayName: String, val salaryTier: SalaryTier) {
    STARTUP("初创企业", SalaryTier.ENTRY),
    SMALL("中小企业", SalaryTier.JUNIOR),
    MEDIUM("知名企业", SalaryTier.MID),
    LARGE("大型集团", SalaryTier.SENIOR),
    TOP("世界500强", SalaryTier.EXECUTIVE)
}

data class Employer(
    val id: String,
    val name: String,
    val industry: Industry,
    val tier: EmployerTier,
    var partnershipLevel: Int = 0,
    var hiredCount: Int = 0,
    var satisfactionWithSchool: Float = 50f,
    var lastHireYear: Int = 0
)

/**
 * 就业市场持久化数据
 */
@Serializable
data class EmploymentPersistData(
    val graduates: List<GraduateRecordPersist> = emptyList(),
    val activePrograms: List<String> = emptyList(),
    val governmentBoostFactor: Float = 1f
)

@Serializable
data class GraduateRecordPersist(
    val studentId: String? = null,
    val studentName: String,
    val graduateYear: Int,
    val graduateMonth: Int = 6,
    val gaoKaoScore: Float,
    val universityTier: String,
    val universityName: String? = null,
    val satisfaction: Float,
    val status: String,
    val industry: String? = null,
    val salaryTier: String? = null,
    val monthsInUniversity: Int = 0,
    val feedbackScore: Int = 0,
    val courseId: String = ""
)
