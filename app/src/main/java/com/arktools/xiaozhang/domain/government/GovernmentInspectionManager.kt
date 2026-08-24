package com.arktools.xiaozhang.domain.government

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

// ==================== 数据模型 ====================

enum class SchoolGrade(val displayName: String, val color: Long, val reputationBonus: Int, val fundingBonus: Double) {
    AAA("AAA级示范校", 0xFFFF8F00, 20, 50.0),
    AA("AA级优秀校", 0xFF1565C0, 12, 30.0),
    A("A级达标校", 0xFF388E3C, 5, 15.0),
    B("B级合格校", 0xFF9E9E9E, 0, 0.0),
    C("C级待改进", 0xFFF57C00, -5, 0.0),
    D("D级不合格", 0xFFD32F2F, -15, 0.0)
}

enum class InspectionType(val displayName: String, val durationMonths: Int) {
    ROUTINE("常规检查", 0),
    COMPREHENSIVE("综合评估", 1),
    SPECIAL("专项督导", 0),
    ACCREDITATION("等级认定", 2),
    FOLLOW_UP("整改复查", 0)
}

enum class InspectionDimension(val displayName: String, val weight: Float) {
    TEACHING_QUALITY("教学质量", 0.22f),
    FACILITY_SAFETY("设施安全", 0.13f),
    TEACHER_QUALIFICATION("师资水平", 0.18f),
    STUDENT_DEVELOPMENT("学生发展", 0.13f),
    MANAGEMENT("学校管理", 0.12f),
    FINANCIAL_HEALTH("财务健康", 0.10f),
    EMPLOYMENT_OUTCOME("就业成果", 0.12f)  // 新增：就业率与雇主反馈
}

data class InspectionScore(
    val dimension: InspectionDimension,
    val score: Float, // 0-100
    val comment: String,
    val issues: List<String> = emptyList()
)

data class Inspection(
    val id: String = "INS-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
    val type: InspectionType,
    val year: Int,
    val month: Int,
    var isCompleted: Boolean = false,
    var scores: List<InspectionScore> = emptyList(),
    var totalScore: Float = 0f,
    var resultGrade: SchoolGrade? = null,
    var rectificationRequired: Boolean = false,
    var rectificationDeadlineMonth: Int = 0
)

enum class RectificationType(val displayName: String, val cost: Double, val improvementScore: Float) {
    UPGRADE_FACILITY("设施升级改造", 20.0, 15f),
    TEACHER_TRAINING("教师培训强化", 10.0, 10f),
    SAFETY_IMPROVEMENT("安全隐患整改", 15.0, 12f),
    MANAGEMENT_REFORM("管理制度改革", 5.0, 8f),
    CURRICULUM_ADJUSTMENT("课程体系优化", 8.0, 10f),
    FINANCIAL_AUDIT("财务规范审计", 3.0, 6f)
}

data class RectificationTask(
    val id: String = "REC-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
    val type: RectificationType,
    val inspectionId: String,
    var isCompleted: Boolean = false,
    var progress: Float = 0f, // 0-100
    val startMonth: Int,
    val deadlineMonth: Int,
    val cost: Double
)

data class GovernmentState(
    val currentGrade: SchoolGrade = SchoolGrade.B,
    val inspections: List<Inspection> = emptyList(),
    val rectificationTasks: List<RectificationTask> = emptyList(),
    val nextInspectionMonth: Int = 6, // 下次检查月份（首次延迟到次年6月，给新学校约11个月成长时间）
    val consecutiveGoodGrades: Int = 0,
    val warnings: List<String> = emptyList(),
    val subsidyReceived: Double = 0.0,
    val finesAccumulated: Double = 0.0,
    val recentEvents: List<String> = emptyList()
)

data class GovernmentMonthResult(
    val inspectionOccurred: Boolean = false,
    val newGrade: SchoolGrade? = null,
    val subsidy: Double = 0.0,
    val fine: Double = 0.0,
    val rectificationRequired: Boolean = false,
    val reputationImpact: Int = 0,
    val events: List<String> = emptyList(),
    val enrollmentCap: Int = 0,           // D/C级限制招生人数上限（0=不限制）
    val employmentBoostFactor: Float = 1f // AAA/AA给就业加成
)

// ==================== 管理器 ====================

@Singleton
class GovernmentInspectionManager @Inject constructor() {

    companion object {
        // === 评级分数线 ===
        private const val GRADE_AAA_THRESHOLD = 95f
        private const val GRADE_AA_THRESHOLD = 86f
        private const val GRADE_A_THRESHOLD = 74f
        private const val GRADE_B_THRESHOLD = 58f
        private const val GRADE_C_THRESHOLD = 42f
        // 高等级学校每级额外扣分（提高评级门槛）
        private const val LEVEL_PENALTY_PER_LEVEL = 2.5f

        // === 罚款公式 ===
        private const val FINE_D_BASE = 5.0          // D级基础罚款（万元）
        private const val FINE_D_PER_LEVEL = 3.0     // D级每级额外罚款
        private const val FINE_C_BASE = 2.0          // C级基础罚款（万元）
        private const val FINE_C_PER_LEVEL = 1.0     // C级每级额外罚款

        // === 招生限制 ===
        private const val ENROLLMENT_CAP_D_RATIO = 0.8   // D级限制为当前人数80%
        private const val ENROLLMENT_CAP_C_RATIO = 0.9   // C级限制为当前人数90%

        // === 补贴与奖励 ===
        private const val SUBSIDY_MULTIPLIER_PER_LEVEL = 0.3  // 每级补贴加成
        private const val CONSECUTIVE_GOOD_THRESHOLD = 3       // 连续优秀奖励间隔
        private const val CONSECUTIVE_BONUS_BASE = 20.0        // 连续优秀基础奖励（万元）
        private const val CONSECUTIVE_BONUS_PER_LEVEL = 5.0    // 连续优秀每级加成
        private const val CONSECUTIVE_REPUTATION_BASE = 10     // 连续优秀基础声望
        private const val CONSECUTIVE_REPUTATION_PER_LEVEL = 2

        // === 就业加成因子 ===
        private const val EMPLOYMENT_BOOST_AAA = 1.30f
        private const val EMPLOYMENT_BOOST_AA = 1.15f
        private const val EMPLOYMENT_BOOST_A = 1.05f

        // === 检查间隔（月）=== v3.0 拉长间隔，缓解"督察太频繁/查了一次一直查"
        private const val INSPECTION_INTERVAL_D = 6   // 不合格复查间隔（原3）
        private const val INSPECTION_INTERVAL_C = 8   // 待改进复查间隔（原4）
        private const val INSPECTION_INTERVAL_NORMAL = 12  // 正常检查间隔（原6，改为年度）
        private const val RECTIFICATION_DEADLINE_MONTHS = 4  // 整改期限（原3，配合拉长间隔）

        // === 财务健康评分阈值（万元） ===
        private const val FINANCIAL_EXCELLENT_CASH = 100.0
        private const val FINANCIAL_GOOD_CASH = 50.0
        private const val FINANCIAL_EXCELLENT_REVENUE = 10.0

        // === 就业率评分阈值 ===
        private const val EMPLOYMENT_EXCELLENT_RATE = 0.95f
        private const val EMPLOYMENT_GOOD_RATE = 0.85f
        private const val EMPLOYMENT_FAIR_RATE = 0.70f
        private const val EMPLOYMENT_POOR_RATE = 0.50f

        // === 整改进度 ===
        private const val RECTIFICATION_MONTHLY_PROGRESS = 35f
    }

    private val _state = MutableStateFlow(GovernmentState())
    val state: StateFlow<GovernmentState> = _state.asStateFlow()

    fun reset() {
        _state.value = GovernmentState()
    }

    /**
     * 月度推进
     * @param employmentRate 就业率(0-1)，用于就业成果评分
     * @param schoolLevel 学校等级(1-6)，影响评估标准和奖惩力度
     */
    fun advanceMonth(
        year: Int, month: Int,
        schoolReputation: Long,
        teacherCount: Int,
        teacherAvgSkill: Float,
        studentCount: Int,
        studentSatisfaction: Float,
        facilityCondition: Float,
        cashBalance: Double,
        monthlyRevenue: Double,
        employmentRate: Float = 0f,
        schoolLevel: Int = 1,
        teachingQualityScore: Float = 0f,
        weeklyPEHours: Int = 2
    ): GovernmentMonthResult {
        val events = mutableListOf<String>()
        var subsidy = 0.0
        var fine = 0.0
        var reputationImpact = 0
        var inspectionOccurred = false
        var newGrade: SchoolGrade? = null
        var rectificationRequired = false

        // 1. 推进整改任务
        advanceRectificationTasks(month)

        // 2. 检查是否到检查时间
        var enrollmentCap = 0
        var employmentBoostFactor = 1f

        if (month == _state.value.nextInspectionMonth) {
            inspectionOccurred = true
            val inspection = conductInspection(
                year, month, schoolReputation, teacherCount, teacherAvgSkill,
                studentCount, studentSatisfaction, facilityCondition, cashBalance, monthlyRevenue,
                employmentRate, schoolLevel, teachingQualityScore, weeklyPEHours
            )

            // 评定等级（高等级学校标准更严格）
            val grade = scoreToGrade(inspection.totalScore, schoolLevel)
            inspection.resultGrade = grade
            newGrade = grade

            // 是否需要整改
            if (grade.ordinal >= SchoolGrade.C.ordinal) {
                inspection.rectificationRequired = true
                inspection.rectificationDeadlineMonth = if (month + RECTIFICATION_DEADLINE_MONTHS > 12)
                    (month + RECTIFICATION_DEADLINE_MONTHS - 12) else month + RECTIFICATION_DEADLINE_MONTHS
                rectificationRequired = true
                events.add("评估结果为${grade.displayName}，需在${RECTIFICATION_DEADLINE_MONTHS}个月内完成整改")
            }

            // 发放补贴或罚款（学校等级影响补贴倍率）
            val subsidyMultiplier = 1.0 + (schoolLevel - 1) * SUBSIDY_MULTIPLIER_PER_LEVEL
            if (grade.fundingBonus > 0) {
                subsidy = grade.fundingBonus * subsidyMultiplier
                events.add("获得教育发展奖励金 ¥${subsidy.toInt()}万")
            }
            // D/C级罚款加重
            if (grade == SchoolGrade.D) {
                fine = FINE_D_BASE + schoolLevel * FINE_D_PER_LEVEL
                events.add("因不合格被处以罚款 ¥${fine.toInt()}万")
                enrollmentCap = (studentCount * ENROLLMENT_CAP_D_RATIO).toInt()
                events.add("⚠️ 教育局限制招生：上限${enrollmentCap}人")
            } else if (grade == SchoolGrade.C) {
                fine = FINE_C_BASE + schoolLevel * FINE_C_PER_LEVEL
                events.add("因评级偏低被罚款 ¥${fine.toInt()}万")
                enrollmentCap = (studentCount * ENROLLMENT_CAP_C_RATIO).toInt()
                events.add("⚠️ 教育局限制扩招：上限${enrollmentCap}人")
            }

            // AAA/AA 给就业加成（好学校就业更容易）
            employmentBoostFactor = when (grade) {
                SchoolGrade.AAA -> EMPLOYMENT_BOOST_AAA
                SchoolGrade.AA -> EMPLOYMENT_BOOST_AA
                SchoolGrade.A -> EMPLOYMENT_BOOST_A
                else -> 1.0f
            }

            reputationImpact = grade.reputationBonus

            // 更新连续优秀次数
            val consecutive = if (grade.ordinal <= SchoolGrade.A.ordinal) {
                _state.value.consecutiveGoodGrades + 1
            } else 0

            // 设定下次检查时间
            val nextMonth = when {
                grade == SchoolGrade.D -> month + INSPECTION_INTERVAL_D
                grade == SchoolGrade.C -> month + INSPECTION_INTERVAL_C
                else -> month + INSPECTION_INTERVAL_NORMAL
            }.let { if (it > 12) it - 12 else it }

            _state.update { current ->
                current.copy(
                    currentGrade = grade,
                    inspections = current.inspections + inspection,
                    nextInspectionMonth = nextMonth,
                    consecutiveGoodGrades = consecutive,
                    subsidyReceived = current.subsidyReceived + subsidy,
                    finesAccumulated = current.finesAccumulated + fine,
                    recentEvents = events
                )
            }

            // 连续优秀奖励（学校等级提升奖励金额）
            if (consecutive >= CONSECUTIVE_GOOD_THRESHOLD && consecutive % CONSECUTIVE_GOOD_THRESHOLD == 0) {
                val bonusAmount = CONSECUTIVE_BONUS_BASE + schoolLevel * CONSECUTIVE_BONUS_PER_LEVEL
                subsidy += bonusAmount
                events.add("连续${consecutive}次优秀评估，额外奖励 ¥${bonusAmount.toInt()}万")
                reputationImpact += CONSECUTIVE_REPUTATION_BASE + schoolLevel * CONSECUTIVE_REPUTATION_PER_LEVEL
            }
        } else {
            // 非检查月：检查整改超期
            checkRectificationDeadlines(month, events)
            _state.update { it.copy(recentEvents = events) }
        }

        // 3. 每年年初发放年度补贴（基于当前等级）
        if (month == 1 && _state.value.currentGrade.fundingBonus > 0) {
            val annualSubsidy = _state.value.currentGrade.fundingBonus * 0.5
            subsidy += annualSubsidy
            events.add("年度教育经费拨付 ¥${annualSubsidy.toInt()}")
            _state.update { it.copy(subsidyReceived = it.subsidyReceived + annualSubsidy) }
        }

        return GovernmentMonthResult(
            inspectionOccurred = inspectionOccurred,
            newGrade = newGrade,
            subsidy = subsidy,
            fine = fine,
            rectificationRequired = rectificationRequired,
            reputationImpact = reputationImpact,
            events = events,
            enrollmentCap = enrollmentCap,
            employmentBoostFactor = employmentBoostFactor
        )
    }

    /**
     * 开始整改任务
     */
    fun startRectification(type: RectificationType, inspectionId: String, currentMonth: Int): Double {
        val task = RectificationTask(
            type = type,
            inspectionId = inspectionId,
            startMonth = currentMonth,
            deadlineMonth = currentMonth + 2,
            cost = type.cost
        )
        _state.update { current ->
            current.copy(rectificationTasks = current.rectificationTasks + task)
        }
        return type.cost
    }

    /**
     * 获取可用的整改措施
     */
    fun getAvailableRectifications(inspectionId: String): List<RectificationType> {
        val existing = _state.value.rectificationTasks
            .filter { it.inspectionId == inspectionId }
            .map { it.type }
        return RectificationType.entries.filter { it !in existing }
    }

    /**
     * 获取最近一次检查的详细评分
     */
    fun getLatestInspectionScores(): List<InspectionScore> {
        return _state.value.inspections.lastOrNull()?.scores ?: emptyList()
    }

    // ==================== 私有方法 ====================

    private fun conductInspection(
        year: Int, month: Int,
        schoolReputation: Long,
        teacherCount: Int,
        teacherAvgSkill: Float,
        studentCount: Int,
        studentSatisfaction: Float,
        facilityCondition: Float,
        cashBalance: Double,
        monthlyRevenue: Double,
        employmentRate: Float,
        schoolLevel: Int,
        teachingQualityScore: Float = 0f,
        weeklyPEHours: Int = 2
    ): Inspection {
        val scores = mutableListOf<InspectionScore>()

        // 教学质量评分：综合教师技能、学生满意度、教学配置质量
        val configBonus = (teachingQualityScore / 10f * 15f).coerceIn(0f, 15f) // 教学配置加0-15分
        val peCompliance = if (weeklyPEHours >= 2) 5f else -10f  // PE课时达标奖5分，不达标扣10分
        val teachingScore = (teacherAvgSkill * 0.5f + studentSatisfaction * 0.3f + configBonus + peCompliance).coerceIn(0f, 100f)
        scores.add(InspectionScore(
            dimension = InspectionDimension.TEACHING_QUALITY,
            score = teachingScore,
            comment = if (teachingScore > 80) "教学质量优秀" else if (teachingScore > 60) "教学质量合格" else "教学质量待提升",
            issues = if (teachingScore < 60) listOf("部分课程教学效果不理想") else emptyList()
        ))

        // 设施安全评分
        val facilityScore = facilityCondition.coerceIn(0f, 100f)
        scores.add(InspectionScore(
            dimension = InspectionDimension.FACILITY_SAFETY,
            score = facilityScore,
            comment = if (facilityScore > 80) "设施完善安全" else if (facilityScore > 50) "设施基本达标" else "设施亟需改善",
            issues = if (facilityScore < 50) listOf("部分设施存在安全隐患", "维护保养不及时") else emptyList()
        ))

        // 师资水平评分
        val teacherRatio = if (studentCount > 0) teacherCount.toFloat() / studentCount * 100 else 50f
        val teacherScore = (teacherAvgSkill * 0.6f + teacherRatio.coerceAtMost(100f) * 0.4f).coerceIn(0f, 100f)
        scores.add(InspectionScore(
            dimension = InspectionDimension.TEACHER_QUALIFICATION,
            score = teacherScore,
            comment = if (teacherScore > 80) "师资力量雄厚" else if (teacherScore > 60) "师资配置合理" else "师资力量薄弱",
            issues = if (teacherScore < 60) listOf("教师学历结构需优化", "师生比偏低") else emptyList()
        ))

        // 学生发展评分
        val studentDevScore = (studentSatisfaction * 0.5f + (schoolReputation / 10f).coerceAtMost(50f)).coerceIn(0f, 100f)
        scores.add(InspectionScore(
            dimension = InspectionDimension.STUDENT_DEVELOPMENT,
            score = studentDevScore,
            comment = if (studentDevScore > 80) "学生全面发展" else if (studentDevScore > 60) "学生发展良好" else "学生发展需关注",
            issues = if (studentDevScore < 60) listOf("学生综合素质培养不足") else emptyList()
        ))

        // 学校管理评分
        val managementScore = ((schoolReputation / 5f).coerceAtMost(70f) + Random.nextFloat() * 30f).coerceIn(0f, 100f)
        scores.add(InspectionScore(
            dimension = InspectionDimension.MANAGEMENT,
            score = managementScore,
            comment = if (managementScore > 80) "管理规范高效" else if (managementScore > 60) "管理基本规范" else "管理亟需改进",
            issues = if (managementScore < 60) listOf("内部管理制度不完善") else emptyList()
        ))

        // 财务健康评分（cash单位是万元）
        val financialScore = when {
            cashBalance > FINANCIAL_EXCELLENT_CASH && monthlyRevenue > FINANCIAL_EXCELLENT_REVENUE -> 90f + Random.nextFloat() * 10f
            cashBalance > FINANCIAL_GOOD_CASH -> 70f + Random.nextFloat() * 15f
            cashBalance > 0 -> 50f + Random.nextFloat() * 20f
            else -> 20f + Random.nextFloat() * 20f
        }.coerceIn(0f, 100f)
        scores.add(InspectionScore(
            dimension = InspectionDimension.FINANCIAL_HEALTH,
            score = financialScore,
            comment = if (financialScore > 80) "财务状况良好" else if (financialScore > 50) "财务基本健康" else "财务状况堪忧",
            issues = if (financialScore < 50) listOf("资金紧张，存在运营风险") else emptyList()
        ))

        // 就业成果评分（新增维度）
        val employmentScore = when {
            employmentRate >= EMPLOYMENT_EXCELLENT_RATE -> 95f + Random.nextFloat() * 5f
            employmentRate >= EMPLOYMENT_GOOD_RATE -> 80f + Random.nextFloat() * 10f
            employmentRate >= EMPLOYMENT_FAIR_RATE -> 65f + Random.nextFloat() * 10f
            employmentRate >= EMPLOYMENT_POOR_RATE -> 45f + Random.nextFloat() * 15f
            employmentRate > 0f -> 25f + Random.nextFloat() * 15f
            else -> 50f + Random.nextFloat() * 10f // 无毕业生时给中等分
        }.coerceIn(0f, 100f)
        scores.add(InspectionScore(
            dimension = InspectionDimension.EMPLOYMENT_OUTCOME,
            score = employmentScore,
            comment = when {
                employmentScore > 85 -> "毕业生就业出色，雇主反馈优秀"
                employmentScore > 65 -> "就业情况良好，供需匹配"
                employmentScore > 45 -> "就业一般，需加强校企合作"
                else -> "就业率偏低，须重点改善"
            },
            issues = if (employmentScore < 50) listOf("就业率不达标，需加强职业指导", "校企合作渠道不足") else emptyList()
        ))

        // 加权总分
        val totalScore = scores.sumOf { (it.score * it.dimension.weight).toDouble() }.toFloat()

        // 整改完成加分
        val rectificationBonus = _state.value.rectificationTasks
            .filter { it.isCompleted }
            .sumOf { it.type.improvementScore.toDouble() }.toFloat() * 0.1f

        val inspection = Inspection(
            type = if (_state.value.currentGrade.ordinal >= SchoolGrade.C.ordinal)
                InspectionType.FOLLOW_UP else InspectionType.COMPREHENSIVE,
            year = year,
            month = month,
            isCompleted = true,
            scores = scores,
            totalScore = (totalScore + rectificationBonus).coerceAtMost(100f)
        )

        return inspection
    }

    private fun scoreToGrade(score: Float, schoolLevel: Int = 1): SchoolGrade {
        // 高等级学校评级标准上移
        val levelPenalty = (schoolLevel - 1) * LEVEL_PENALTY_PER_LEVEL
        val adjustedScore = score - levelPenalty
        return when {
            adjustedScore >= GRADE_AAA_THRESHOLD -> SchoolGrade.AAA
            adjustedScore >= GRADE_AA_THRESHOLD -> SchoolGrade.AA
            adjustedScore >= GRADE_A_THRESHOLD -> SchoolGrade.A
            adjustedScore >= GRADE_B_THRESHOLD -> SchoolGrade.B
            adjustedScore >= GRADE_C_THRESHOLD -> SchoolGrade.C
            else -> SchoolGrade.D
        }
    }

    private fun advanceRectificationTasks(currentMonth: Int) {
        _state.update { current ->
            val updated = current.rectificationTasks.map { task ->
                if (!task.isCompleted) {
                    val newProgress = (task.progress + RECTIFICATION_MONTHLY_PROGRESS).coerceAtMost(100f)
                    if (newProgress >= 100f) {
                        task.copy(isCompleted = true, progress = 100f)
                    } else {
                        task.copy(progress = newProgress)
                    }
                } else task
            }
            current.copy(rectificationTasks = updated)
        }
    }

    private fun checkRectificationDeadlines(currentMonth: Int, events: MutableList<String>) {
        val overdue = _state.value.rectificationTasks.filter {
            !it.isCompleted && it.deadlineMonth <= currentMonth
        }
        if (overdue.isNotEmpty()) {
            events.add("${overdue.size}项整改任务已逾期，将影响下次评估")
        }
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = GovernmentPersistData(
                currentGrade = state.currentGrade.name,
                inspections = state.inspections.map { i ->
                    InspectionPersist(
                        id = i.id,
                        type = i.type.name,
                        year = i.year,
                        month = i.month,
                        isCompleted = i.isCompleted,
                        totalScore = i.totalScore,
                        resultGrade = i.resultGrade?.name,
                        rectificationRequired = i.rectificationRequired,
                        rectificationDeadlineMonth = i.rectificationDeadlineMonth
                    )
                },
                rectificationTasks = state.rectificationTasks.map { t ->
                    RectificationPersist(
                        id = t.id,
                        type = t.type.name,
                        inspectionId = t.inspectionId,
                        isCompleted = t.isCompleted,
                        progress = t.progress,
                        startMonth = t.startMonth,
                        deadlineMonth = t.deadlineMonth,
                        cost = t.cost
                    )
                },
                nextInspectionMonth = state.nextInspectionMonth,
                consecutiveGoodGrades = state.consecutiveGoodGrades,
                warnings = state.warnings,
                subsidyReceived = state.subsidyReceived,
                finesAccumulated = state.finesAccumulated
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<GovernmentPersistData>(json)
            val grade = try { SchoolGrade.valueOf(data.currentGrade) } catch (_: Exception) { SchoolGrade.B }
            val inspections = data.inspections.map { ip ->
                Inspection(
                    id = ip.id,
                    type = try { InspectionType.valueOf(ip.type) } catch (_: Exception) { InspectionType.ROUTINE },
                    year = ip.year,
                    month = ip.month,
                    isCompleted = ip.isCompleted,
                    totalScore = ip.totalScore,
                    resultGrade = ip.resultGrade?.let { try { SchoolGrade.valueOf(it) } catch (_: Exception) { null } },
                    rectificationRequired = ip.rectificationRequired,
                    rectificationDeadlineMonth = ip.rectificationDeadlineMonth
                )
            }
            val tasks = data.rectificationTasks.mapNotNull { tp ->
                val type = try { RectificationType.valueOf(tp.type) } catch (_: Exception) { return@mapNotNull null }
                RectificationTask(
                    id = tp.id,
                    type = type,
                    inspectionId = tp.inspectionId,
                    isCompleted = tp.isCompleted,
                    progress = tp.progress,
                    startMonth = tp.startMonth,
                    deadlineMonth = tp.deadlineMonth,
                    cost = tp.cost
                )
            }
            _state.value = GovernmentState(
                currentGrade = grade,
                inspections = inspections,
                rectificationTasks = tasks,
                nextInspectionMonth = data.nextInspectionMonth,
                consecutiveGoodGrades = data.consecutiveGoodGrades,
                warnings = data.warnings,
                subsidyReceived = data.subsidyReceived,
                finesAccumulated = data.finesAccumulated,
                recentEvents = emptyList()
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("GovernmentInspectionManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class GovernmentPersistData(
    val currentGrade: String = "B",
    val inspections: List<InspectionPersist> = emptyList(),
    val rectificationTasks: List<RectificationPersist> = emptyList(),
    val nextInspectionMonth: Int = 6,
    val consecutiveGoodGrades: Int = 0,
    val warnings: List<String> = emptyList(),
    val subsidyReceived: Double = 0.0,
    val finesAccumulated: Double = 0.0
)

@Serializable
data class InspectionPersist(
    val id: String,
    val type: String,
    val year: Int,
    val month: Int,
    val isCompleted: Boolean = false,
    val totalScore: Float = 0f,
    val resultGrade: String? = null,
    val rectificationRequired: Boolean = false,
    val rectificationDeadlineMonth: Int = 0
)

@Serializable
data class RectificationPersist(
    val id: String,
    val type: String,
    val inspectionId: String,
    val isCompleted: Boolean = false,
    val progress: Float = 0f,
    val startMonth: Int,
    val deadlineMonth: Int,
    val cost: Double
)
