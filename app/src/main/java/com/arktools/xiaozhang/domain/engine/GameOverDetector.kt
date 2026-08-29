package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.model.SchoolOwnership
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.schoolOwnership
import com.arktools.xiaozhang.domain.model.schoolTier
import com.arktools.xiaozhang.domain.model.promotionHistoryText
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 失败条件检测器
 * 
 * 检测学校是否满足 GameOver 条件，并提供救助机制。
 * 
 * == 失败条件（必须同时满足多条才触发，避免因单次波动导致结束） ==
 * 1. 持续亏损：连续3个月现金为负数
 * 2. 资金枯竭：现金低于破产阈值（-100）
 * 3. 声誉崩塌：声誉连续3个月低于10
 * 4. 全员离职：所有教师离职且无现金招新
 * 5. 学生流失：所有在读学生退学或毕业后无新生
 * 
 * == 危机等级 ==
 * - STABLE:   经营正常
 * - WARNING:  出现危险信号（单条件触发），给予提醒
 * - CRITICAL: 多条件同时满足，进入紧急救助窗口
 * - GAME_OVER: 玩家拒绝救助或救助后未恢复
 */
@Singleton
class GameOverDetector @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository
) {
    // 状态追踪
    private var consecutiveNegativeCashMonths = 0
    private var consecutiveLowReputationMonths = 0
    private var bailoutUsed = 0
    private var lastCheckedMonth = -1
    /** CRITICAL 状态持续月数：防止 UI 丢失弹窗导致永久卡住 */
    private var criticalStateMonths = 0

    // 救助系统配置
    companion object {
        const val MAX_BAILOUTS = 2 // 最多使用2次救助
        const val BAILOUT_CASH_GRANT = 120.0 // 救助金额（提高广告奖励价值）
        const val BAILOUT_REPUTATION_GRANT = 50L // 救助声誉（提高广告奖励价值）
        const val NEGATIVE_CASH_MONTHS_THRESHOLD = 3
        const val LOW_REPUTATION_THRESHOLD = 10L
        const val LOW_REPUTATION_MONTHS_THRESHOLD = 3
        const val MIN_CASH_FOR_RECOVERY = 20.0 // 判断"无现金招新"的阈值
        /** CRITICAL 状态最长持续月数，超过则强制 GameOver（防止软锁） */
        const val CRITICAL_STATE_TIMEOUT_MONTHS = 3
    }

    // 当前危机状态
    private val _crisisState = MutableStateFlow(CrisisState.STABLE)
    val crisisState: StateFlow<CrisisState> = _crisisState.asStateFlow()

    // 当前活动的失败条件（供UI展示详情）
    private val _activeConditions = MutableStateFlow<List<FailureCondition>>(emptyList())
    val activeConditions: StateFlow<List<FailureCondition>> = _activeConditions.asStateFlow()

    // 失败原因（GAME_OVER时有值）
    private val _gameOverReason = MutableStateFlow<GameOverReason?>(null)
    val gameOverReason: StateFlow<GameOverReason?> = _gameOverReason.asStateFlow()

    // 救助可用状态
    val isBailoutAvailable: Boolean
        get() = bailoutUsed < MAX_BAILOUTS

    val bailoutsRemaining: Int
        get() = MAX_BAILOUTS - bailoutUsed

    /**
     * 每月检测（在月度结算后调用）
     * 返回是否触发了危机/GameOver
     */
    suspend fun monthlyCheck(school: School): CrisisCheckResult {
        // 避免同一月重复检查
        val currentAbsMonth = school.currentYear * 12 + school.currentMonth
        if (currentAbsMonth == lastCheckedMonth) return CrisisCheckResult.NO_CHANGE
        lastCheckedMonth = currentAbsMonth

        // 新学校前6个月宽限期：不做破产检测，让玩家有时间建立收入
        val schoolAgeMonths = (school.currentYear - school.foundedYear) * 12 + (school.currentMonth - 1)
        if (schoolAgeMonths < 6) return CrisisCheckResult.NO_CHANGE

        val teachers = teacherRepository.getTeachers()
        val activeStudentCount = studentRepository.getActiveStudentCount()

        // === 更新连续追踪计数器 ===
        if (school.cash < 0) {
            consecutiveNegativeCashMonths++
        } else {
            consecutiveNegativeCashMonths = 0
        }

        if (school.reputation < LOW_REPUTATION_THRESHOLD) {
            consecutiveLowReputationMonths++
        } else {
            consecutiveLowReputationMonths = 0
        }

        // === 检测各项失败条件 ===
        val conditions = mutableListOf<FailureCondition>()

        // 条件1: 持续亏损
        if (consecutiveNegativeCashMonths >= NEGATIVE_CASH_MONTHS_THRESHOLD) {
            conditions.add(FailureCondition.SUSTAINED_LOSSES)
        }

        // 条件2: 资金枯竭（已低于破产线）。公办院校有财政兜底，破产线更宽
        val bankruptcyLine = if (school.schoolOwnership() == SchoolOwnership.PUBLIC) {
            GameBalanceConfig.BANKRUPTCY_THRESHOLD * 3.0
        } else {
            GameBalanceConfig.BANKRUPTCY_THRESHOLD
        }
        if (school.cash < bankruptcyLine) {
            conditions.add(FailureCondition.BANKRUPT)
        }

        // 条件3: 声誉崩塌
        if (consecutiveLowReputationMonths >= LOW_REPUTATION_MONTHS_THRESHOLD) {
            conditions.add(FailureCondition.REPUTATION_COLLAPSE)
        }

        // 条件4: 全员离职且无钱招人
        val workingTeachers = teachers.filter { it.isWorking }
        if (workingTeachers.isEmpty() && school.cash < MIN_CASH_FOR_RECOVERY) {
            conditions.add(FailureCondition.ALL_TEACHERS_QUIT)
        }

        // 条件5: 学生全部流失，且声誉过低无法在下次招生季吸引新生
        if (activeStudentCount == 0 && school.reputation < 30L && school.currentYear > school.foundedYear) {
            conditions.add(FailureCondition.NO_STUDENTS)
        }

        // === 判定危机等级 ===
        val newState = when {
            conditions.size >= 2 -> CrisisState.CRITICAL
            conditions.isNotEmpty() -> CrisisState.WARNING
            else -> CrisisState.STABLE
        }

        _activeConditions.value = conditions

        val previousState = _crisisState.value

        // CRITICAL 状态持续计数（防止软锁）
        if (newState == CrisisState.CRITICAL) {
            criticalStateMonths++
            // 超时强制 GameOver：避免 UI 弹窗丢失导致永久卡在 CRITICAL
            if (criticalStateMonths > CRITICAL_STATE_TIMEOUT_MONTHS && !isBailoutAvailable) {
                val reason = GameOverReason(
                    conditions = conditions,
                    finalCash = school.cash,
                    finalReputation = school.reputation,
                    totalYearsPlayed = school.currentYear - school.foundedYear,
                    totalStudentsGraduated = 0,
                    peakReputation = school.reputation,
                    peakCash = school.cash,
                    schoolTypeName = school.schoolTier().displayName + "·" +
                        school.schoolOwnership().displayName,
                    promotionHistoryText = school.promotionHistoryText()
                )
                confirmGameOver(reason)
                return CrisisCheckResult.ENTERED_CRITICAL(conditions)
            }
        } else {
            criticalStateMonths = 0
        }

        _crisisState.value = newState

        return when {
            newState == CrisisState.CRITICAL && previousState != CrisisState.CRITICAL -> {
                CrisisCheckResult.ENTERED_CRITICAL(conditions)
            }
            // 持续 CRITICAL 时也重新发出信号，让 UI 有机会重新弹出救助窗口
            newState == CrisisState.CRITICAL && previousState == CrisisState.CRITICAL -> {
                CrisisCheckResult.ENTERED_CRITICAL(conditions)
            }
            newState == CrisisState.WARNING && previousState == CrisisState.STABLE -> {
                CrisisCheckResult.ENTERED_WARNING(conditions)
            }
            newState == CrisisState.STABLE && previousState != CrisisState.STABLE -> {
                CrisisCheckResult.RECOVERED
            }
            else -> CrisisCheckResult.NO_CHANGE
        }
    }

    /**
     * 执行救助操作（玩家主动触发）
     * 给予现金和声誉帮助学校度过危机
     */
    suspend fun executeBailout(): BailoutResult {
        if (!isBailoutAvailable) {
            return BailoutResult.NO_BAILOUTS_LEFT
        }

        val school = schoolRepository.getSchool() ?: return BailoutResult.FAILED

        bailoutUsed++

        // 注入救助资源
        schoolRepository.addCash(BAILOUT_CASH_GRANT)
        schoolRepository.addReputation(BAILOUT_REPUTATION_GRANT)

        // 重置连续计数器（给玩家喘息空间）
        consecutiveNegativeCashMonths = 0
        consecutiveLowReputationMonths = 0
        criticalStateMonths = 0

        // 降级危机状态
        _crisisState.value = CrisisState.WARNING

        return BailoutResult.SUCCESS(
            cashGrant = BAILOUT_CASH_GRANT,
            reputationGrant = BAILOUT_REPUTATION_GRANT,
            bailoutsRemaining = bailoutsRemaining
        )
    }

    /**
     * 玩家拒绝救助，确认 GameOver
     */
    fun confirmGameOver(reason: GameOverReason) {
        _crisisState.value = CrisisState.GAME_OVER
        _gameOverReason.value = reason
    }

    /**
     * 新游戏时重置所有状态
     */
    fun reset() {
        consecutiveNegativeCashMonths = 0
        consecutiveLowReputationMonths = 0
        bailoutUsed = 0
        lastCheckedMonth = -1
        criticalStateMonths = 0
        _crisisState.value = CrisisState.STABLE
        _activeConditions.value = emptyList()
        _gameOverReason.value = null
    }

    /**
     * 获取当前健康诊断报告（供UI展示）
     */
    suspend fun getHealthReport(school: School): HealthReport {
        val teachers = teacherRepository.getTeachers()
        val workingTeachers = teachers.filter { it.isWorking }
        val activeStudentCount = studentRepository.getActiveStudentCount()

        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // 资金警告
        if (school.cash < 0) {
            warnings.add("学校连续${consecutiveNegativeCashMonths}个月亏损")
            suggestions.add("尝试削减开支或调整教学强度")
        }
        if (school.cash < 30.0 && school.cash >= 0) {
            warnings.add("资金即将耗尽")
            suggestions.add("考虑暂停营销活动或降低运营成本")
        }

        // 声誉警告
        if (school.reputation < 30L) {
            warnings.add("学校声誉过低（${school.reputation}）")
            suggestions.add("提高教学质量和学生满意度")
        }

        // 教师警告
        if (workingTeachers.isEmpty()) {
            warnings.add("没有在职教师")
            suggestions.add("立即招聘新教师")
        } else {
            val lowLoyaltyCount = workingTeachers.count { it.loyalty < 20 }
            if (lowLoyaltyCount > workingTeachers.size / 2) {
                warnings.add("${lowLoyaltyCount}名教师忠诚度极低，随时可能离职")
                suggestions.add("给教师加薪或安排休假")
            }
        }

        // 学生警告
        if (activeStudentCount == 0 && school.reputation >= 30L) {
            warnings.add("当前没有在读学生")
            suggestions.add("等待9月招生季或通过营销提升声誉")
        }

        return HealthReport(
            cashStatus = when {
                school.cash > 50.0 -> HealthStatus.GOOD
                school.cash > 0.0 -> HealthStatus.FAIR
                school.cash > GameBalanceConfig.BANKRUPTCY_THRESHOLD -> HealthStatus.POOR
                else -> HealthStatus.CRITICAL
            },
            reputationStatus = when {
                school.reputation > 500L -> HealthStatus.GOOD
                school.reputation > 100L -> HealthStatus.FAIR
                school.reputation > LOW_REPUTATION_THRESHOLD -> HealthStatus.POOR
                else -> HealthStatus.CRITICAL
            },
            teacherStatus = when {
                workingTeachers.size >= 3 -> HealthStatus.GOOD
                workingTeachers.size >= 1 -> HealthStatus.FAIR
                else -> HealthStatus.CRITICAL
            },
            studentStatus = when {
                activeStudentCount >= 20 -> HealthStatus.GOOD
                activeStudentCount >= 5 -> HealthStatus.FAIR
                activeStudentCount >= 1 -> HealthStatus.POOR
                else -> HealthStatus.CRITICAL
            },
            overallCrisis = _crisisState.value,
            warnings = warnings,
            suggestions = suggestions,
            bailoutsRemaining = bailoutsRemaining
        )
    }
}

// === 枚举和数据类 ===

enum class CrisisState {
    STABLE,     // 经营正常
    WARNING,    // 警告：出现危险信号
    CRITICAL,   // 紧急：多条件同时满足，弹出救助窗口
    GAME_OVER   // 游戏结束
}

enum class FailureCondition {
    SUSTAINED_LOSSES,      // 连续亏损
    BANKRUPT,              // 资金枯竭（低于破产线）
    REPUTATION_COLLAPSE,   // 声誉崩塌
    ALL_TEACHERS_QUIT,     // 全员离职
    NO_STUDENTS,           // 无学生且无课程
    PRINCIPAL_ARRESTED     // 校长被逮捕（立即结束）
}

sealed class CrisisCheckResult {
    data object NO_CHANGE : CrisisCheckResult()
    data object RECOVERED : CrisisCheckResult()
    data class ENTERED_WARNING(val conditions: List<FailureCondition>) : CrisisCheckResult()
    data class ENTERED_CRITICAL(val conditions: List<FailureCondition>) : CrisisCheckResult()
}

sealed class BailoutResult {
    data object NO_BAILOUTS_LEFT : BailoutResult()
    data object FAILED : BailoutResult()
    data class SUCCESS(
        val cashGrant: Double,
        val reputationGrant: Long,
        val bailoutsRemaining: Int
    ) : BailoutResult()
}

data class GameOverReason(
    val conditions: List<FailureCondition>,
    val finalCash: Double,
    val finalReputation: Long,
    val totalYearsPlayed: Int,
    val totalStudentsGraduated: Int,
    val peakReputation: Long,
    val peakCash: Double,
    val schoolTypeName: String = "",
    val promotionHistoryText: String = ""
) {
    val primaryReason: String
        get() = when {
            FailureCondition.PRINCIPAL_ARRESTED in conditions -> "校长因贪腐被逮捕入狱，学校公款被冻结，游戏结束"
            FailureCondition.BANKRUPT in conditions -> "资金彻底枯竭，学校无法继续运营"
            FailureCondition.SUSTAINED_LOSSES in conditions -> "连续亏损导致学校难以为继"
            FailureCondition.REPUTATION_COLLAPSE in conditions -> "学校声誉降至冰点，无人问津"
            FailureCondition.ALL_TEACHERS_QUIT in conditions -> "所有教师纷纷离去，学校名存实亡"
            FailureCondition.NO_STUDENTS in conditions -> "生源枯竭，教室空无一人"
            else -> "学校经营困难重重"
        }
}

data class HealthReport(
    val cashStatus: HealthStatus,
    val reputationStatus: HealthStatus,
    val teacherStatus: HealthStatus,
    val studentStatus: HealthStatus,
    val overallCrisis: CrisisState,
    val warnings: List<String>,
    val suggestions: List<String>,
    val bailoutsRemaining: Int
)

enum class HealthStatus {
    GOOD, FAIR, POOR, CRITICAL
}
