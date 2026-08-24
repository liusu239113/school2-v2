package com.arktools.xiaozhang.domain.teacherdev

import com.arktools.xiaozhang.domain.model.TeacherLevel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 教师职业发展管理系统
 * - 职称晋升体系（初级→中级→高级→特级→名师）
 * - 培训进修计划
 * - 教学评估考核
 * - 离职/挖角管理
 * - 教师满意度与职业规划
 */
@Singleton
class TeacherDevelopmentManager @Inject constructor() {

    private val _state = MutableStateFlow(TeacherDevState())
    val state: StateFlow<TeacherDevState> = _state.asStateFlow()

    companion object {
        const val MAX_TRAINING_SLOTS = 5
        const val MAX_EVENTS_LOG = 30
        const val EVALUATION_INTERVAL_MONTHS = 6
    }

    /**
     * 注册教师到发展系统
     */
    fun registerTeacher(
        teacherId: String,
        name: String,
        subject: String,
        currentSkill: Float
    ) {
        val existing = _state.value.teacherProfiles
        if (existing.any { it.teacherId == teacherId }) return

        val profile = TeacherProfile(
            teacherId = teacherId,
            name = name,
            subject = subject,
            title = determineInitialTitle(currentSkill),
            skillLevel = currentSkill,
            satisfaction = 70f + Random.nextFloat() * 20f,
            yearsOfService = 0,
            monthsSinceLastPromotion = 0,
            trainingCredits = 0,
            evaluationScore = 60f + Random.nextFloat() * 30f,
            researchPoints = 0,
            teachingAwards = 0,
            isOnTraining = false,
            turnoverRisk = TurnoverRisk.LOW
        )
        _state.update { it.copy(teacherProfiles = it.teacherProfiles + profile) }
    }

    /**
     * 批量同步教师（每月开始时与主系统同步）
     */
    fun syncTeachers(teachers: List<TeacherSyncData>) {
        val profiles = _state.value.teacherProfiles.toMutableList()
        teachers.forEach { data ->
            val index = profiles.indexOfFirst { profile ->
                profile.teacherId == data.id ||
                    (
                        profile.legacyTeacherId != null &&
                            profile.legacyTeacherId == data.legacyId
                    )
            }
            val levelTitle = teacherLevelToTitle(data.level)
            if (index < 0) {
                // 新教师：以招聘时的等级为下限，避免S级教师首次月结算被降为A级
                profiles.add(TeacherProfile(
                    teacherId = data.id,
                    legacyTeacherId = null,
                    name = data.name,
                    subject = data.subject,
                    title = maxOf(determineInitialTitle(data.skill), levelTitle),
                    skillLevel = data.skill,
                    satisfaction = 70f + Random.nextFloat() * 20f,
                    yearsOfService = 0,
                    monthsSinceLastPromotion = 0,
                    trainingCredits = 0,
                    evaluationScore = 60f + Random.nextFloat() * 30f,
                    researchPoints = 0,
                    teachingAwards = 0,
                    isOnTraining = false,
                    turnoverRisk = TurnoverRisk.LOW
                ))
            } else {
                // 只向上同步实际等级，绝不从主系统等级反向降级发展档案
                val existingProfile = profiles[index]
                profiles[index] = existingProfile.copy(
                    teacherId = data.id,
                    legacyTeacherId = null,
                    name = data.name,
                    subject = data.subject,
                    title = maxOf(existingProfile.title, levelTitle),
                    skillLevel = maxOf(existingProfile.skillLevel, data.skill)
                )
            }
        }
        // 移除已不存在的教师
        val teacherIds = teachers.map { it.id }.toSet()
        val filtered = profiles.filter { it.teacherId in teacherIds }
        val migratedTrainings = _state.value.activeTrainings.mapNotNull { training ->
            val teacher = teachers.find { data ->
                training.teacherId == data.id ||
                    (
                        training.legacyTeacherId != null &&
                            training.legacyTeacherId == data.legacyId
                    )
            } ?: return@mapNotNull null
            training.copy(
                teacherId = teacher.id,
                legacyTeacherId = null,
                teacherName = teacher.name
            )
        }
        _state.update {
            it.copy(
                teacherProfiles = filtered,
                activeTrainings = migratedTrainings
            )
        }
    }

    /**
     * 给所有教师增加学分（学术会议、教研活动等集体增长来源）
     */
    fun addCreditsToAll(credits: Int) {
        if (credits <= 0) return
        _state.update { state ->
            state.copy(
                teacherProfiles = state.teacherProfiles.map { profile ->
                    profile.copy(trainingCredits = profile.trainingCredits + credits)
                }
            )
        }
    }

    /**
     * 安排教师参加培训
     */
    fun startTraining(teacherId: String, program: TrainingProgram): Boolean {
        val profile = _state.value.teacherProfiles.find { it.teacherId == teacherId } ?: return false
        if (profile.isOnTraining) return false
        val activeTrainings = _state.value.activeTrainings
        if (activeTrainings.size >= MAX_TRAINING_SLOTS) return false

        val training = ActiveTraining(
            id = System.currentTimeMillis(),
            teacherId = teacherId,
            legacyTeacherId = null,
            teacherName = profile.name,
            program = program,
            remainingMonths = program.durationMonths,
            totalMonths = program.durationMonths
        )

        _state.update { state ->
            val updatedProfiles = state.teacherProfiles.map {
                if (it.teacherId == teacherId) it.copy(isOnTraining = true) else it
            }
            state.copy(
                teacherProfiles = updatedProfiles,
                activeTrainings = state.activeTrainings + training
            )
        }
        return true
    }

    /**
     * 尝试晋升教师
     */
    fun tryPromote(teacherId: String): PromotionResult {
        val profile = _state.value.teacherProfiles.find { it.teacherId == teacherId }
            ?: return PromotionResult.NOT_FOUND
        val nextTitle = TeacherTitle.entries.getOrNull(profile.title.ordinal + 1)
            ?: return PromotionResult.MAX_LEVEL

        if (profile.monthsSinceLastPromotion < nextTitle.requiredMonths) {
            return PromotionResult.INSUFFICIENT_SENIORITY
        }
        if (profile.evaluationScore < nextTitle.requiredEvalScore) {
            return PromotionResult.LOW_EVALUATION
        }
        if (profile.trainingCredits < nextTitle.requiredCredits) {
            return PromotionResult.INSUFFICIENT_CREDITS
        }

        _state.update { state ->
            val updatedProfiles = state.teacherProfiles.map {
                if (it.teacherId == teacherId) it.copy(
                    title = nextTitle,
                    monthsSinceLastPromotion = 0,
                    satisfaction = (it.satisfaction + 15f).coerceAtMost(100f)
                ) else it
            }
            state.copy(
                teacherProfiles = updatedProfiles,
                totalPromotions = state.totalPromotions + 1
            )
        }
        return PromotionResult.SUCCESS
    }

    /**
     * 手动进行教学评估
     */
    fun conductEvaluation(teacherId: String): EvaluationResult? {
        val profile = _state.value.teacherProfiles.find { it.teacherId == teacherId } ?: return null
        val previousScore = profile.evaluationScore

        val normalizedSkill = (profile.skillLevel / 10f).coerceIn(0f, 100f)
        val normalizedCredits = profile.trainingCredits.toFloat().coerceAtMost(100f)
        val normalizedResearch = profile.researchPoints.toFloat().coerceAtMost(100f)
        val baseScore = normalizedSkill * 0.4f +
                profile.satisfaction * 0.2f +
                normalizedCredits * 0.25f +
                normalizedResearch * 0.15f
        val randomFactor = Random.nextFloat() * 20f - 10f
        val score = (baseScore + randomFactor).coerceIn(0f, 100f)

        val nextTitle = TeacherTitle.entries.getOrNull(profile.title.ordinal + 1)
        val requiredScore = nextTitle?.requiredEvalScore
        val event = TeacherDevEvent(
            title = "${profile.name}教学评估",
            description = if (requiredScore == null) {
                "评估${String.format("%.1f", score)}分，已达最高职称"
            } else if (score >= requiredScore) {
                "评估${String.format("%.1f", score)}分，已达到${nextTitle.displayName}晋升评估门槛${requiredScore.toInt()}分"
            } else {
                "评估${String.format("%.1f", score)}分，距离${nextTitle.displayName}晋升门槛还差${(requiredScore - score).coerceAtLeast(0f).toInt()}分"
            },
            type = TeacherDevEventType.EVALUATION,
            month = 0,
            year = 0
        )
        _state.update { state ->
            val updatedProfiles = state.teacherProfiles.map {
                if (it.teacherId == teacherId) it.copy(evaluationScore = score) else it
            }
            state.copy(
                teacherProfiles = updatedProfiles,
                recentEvents = (listOf(event) + state.recentEvents).take(30)
            )
        }
        return EvaluationResult(
            score = score,
            previousScore = previousScore,
            nextTitleName = nextTitle?.displayName,
            requiredScore = requiredScore,
            promotionEligibleByScore = requiredScore == null || score >= requiredScore
        )
    }

    fun hasProcessedMonth(year: Int, month: Int): Boolean {
        val state = _state.value
        return state.lastProcessedYear == year &&
            state.lastProcessedMonth == month
    }

    /**
     * 月度推进
     */
    fun advanceMonth(
        currentYear: Int,
        currentMonth: Int,
        schoolReputation: Long
    ): TeacherDevMonthlyResult {
        if (hasProcessedMonth(currentYear, currentMonth)) {
            return TeacherDevMonthlyResult()
        }
        val promotions = mutableListOf<TeacherDevTeacherChange>()
        val departures = mutableListOf<TeacherDevTeacherChange>()
        val newEvents = mutableListOf<TeacherDevEvent>()
        var trainingExpense = 0.0

        // 推进培训
        val completedTrainings = mutableListOf<ActiveTraining>()
        val updatedTrainings = _state.value.activeTrainings.mapNotNull { training ->
            val remaining = training.remainingMonths - 1
            trainingExpense += training.program.monthlyCostWan
            if (remaining <= 0) {
                completedTrainings.add(training)
                null
            } else {
                training.copy(remainingMonths = remaining)
            }
        }

        // 完成培训的教师获得学分和技能提升
        var updatedProfiles = _state.value.teacherProfiles.map { profile ->
            val completed = completedTrainings.find { it.teacherId == profile.teacherId }
            if (completed != null) {
                val creditGain = completed.program.creditReward
                val skillGain = completed.program.skillBoost
                newEvents.add(TeacherDevEvent(
                    title = "${profile.name}完成培训",
                    description = "${completed.program.displayName}结业，学分+$creditGain",
                    type = TeacherDevEventType.TRAINING_COMPLETE,
                    month = currentMonth, year = currentYear
                ))
                profile.copy(
                    isOnTraining = false,
                    trainingCredits = profile.trainingCredits + creditGain,
                    skillLevel = (profile.skillLevel + skillGain).coerceAtMost(1000f),
                    satisfaction = (profile.satisfaction + 5f).coerceAtMost(100f)
                )
            } else {
                profile
            }
        }

        // 每月更新：服务年限、满意度自然波动、离职风险评估
        updatedProfiles = updatedProfiles.map { profile ->
            val monthsService = profile.yearsOfService * 12 + 1
            val yearsUpdate = monthsService / 12
            val monthsPromo = profile.monthsSinceLastPromotion + 1

            // 满意度自然波动（入职3个月内新教师保护期，满意度只升不降）
            val isInProtectionPeriod = monthsPromo <= 3
            val satisfactionDelta = when {
                isInProtectionPeriod -> Random.nextFloat() * 1f // 保护期内只有正向波动
                monthsPromo > 24 && profile.title.ordinal < TeacherTitle.SENIOR.ordinal -> -2f
                profile.evaluationScore > 80f && profile.satisfaction < 70f -> 1f
                else -> Random.nextFloat() * 2f - 1f
            }
            val newSatisfaction = (profile.satisfaction + satisfactionDelta).coerceIn(20f, 100f)

            // 离职风险评估
            val risk = when {
                newSatisfaction < 40f -> TurnoverRisk.CRITICAL
                newSatisfaction < 55f -> TurnoverRisk.HIGH
                newSatisfaction < 70f -> TurnoverRisk.MEDIUM
                else -> TurnoverRisk.LOW
            }

            // 研究点自然积累
            val researchGain = if (profile.title.ordinal >= TeacherTitle.SENIOR.ordinal) {
                Random.nextInt(0, 3)
            } else 0

            profile.copy(
                yearsOfService = yearsUpdate,
                monthsSinceLastPromotion = monthsPromo,
                satisfaction = newSatisfaction,
                turnoverRisk = risk,
                researchPoints = profile.researchPoints + researchGain
            )
        }

        // 自动离职检查（满意度极低或被挖角）
        // 新入职保护期：入职3个月内不会离职（monthsSinceLastPromotion从0开始递增）
        updatedProfiles = updatedProfiles.filter { profile ->
            val isNewHire = profile.monthsSinceLastPromotion <= 3
            if (isNewHire) {
                true // 新教师保护期内，跳过离职检查
            } else {
                val willLeave = profile.turnoverRisk == TurnoverRisk.CRITICAL && Random.nextFloat() < 0.15f
                val poached = profile.title.ordinal >= TeacherTitle.SENIOR.ordinal &&
                        schoolReputation < 200 && Random.nextFloat() < 0.05f
                if (willLeave || poached) {
                    departures.add(
                        TeacherDevTeacherChange(
                            teacherId = profile.teacherId,
                            teacherName = profile.name
                        )
                    )
                    newEvents.add(TeacherDevEvent(
                        title = "${profile.name}离职",
                        description = if (poached) "被其他学校挖角" else "因不满离职",
                        type = TeacherDevEventType.DEPARTURE,
                        month = currentMonth, year = currentYear
                    ))
                    false
                } else {
                    true
                }
            }
        }

        // 半年评估（6月、12月）
        if (currentMonth == 6 || currentMonth == 12) {
            updatedProfiles = updatedProfiles.map { profile ->
                val normalizedSkill = (profile.skillLevel / 10f).coerceIn(0f, 100f)
                val normalizedCredits = profile.trainingCredits.toFloat().coerceAtMost(100f)
                val normalizedResearch = profile.researchPoints.toFloat().coerceAtMost(100f)
                val score = normalizedSkill * 0.35f +
                        profile.satisfaction * 0.25f +
                        normalizedCredits * 0.25f +
                        normalizedResearch * 0.15f +
                        Random.nextFloat() * 10f - 5f
                val evaluationScore = score.coerceIn(0f, 100f)

                if (evaluationScore >= 90f) {
                    newEvents.add(TeacherDevEvent(
                        title = "${profile.name}获教学优秀奖",
                        description = "评估分数${evaluationScore.toInt()}，表现突出",
                        type = TeacherDevEventType.AWARD,
                        month = currentMonth, year = currentYear
                    ))
                    profile.copy(
                        evaluationScore = evaluationScore,
                        teachingAwards = profile.teachingAwards + 1,
                        satisfaction = (profile.satisfaction + 5f).coerceAtMost(100f)
                    )
                } else {
                    profile.copy(evaluationScore = evaluationScore)
                }
            }
        }

        // 自动晋升检查（每年1月）
        if (currentMonth == 1) {
            updatedProfiles = updatedProfiles.map { profile ->
                val nextTitle = TeacherTitle.entries.getOrNull(profile.title.ordinal + 1)
                if (nextTitle != null &&
                    profile.monthsSinceLastPromotion >= nextTitle.requiredMonths &&
                    profile.evaluationScore >= nextTitle.requiredEvalScore &&
                    profile.trainingCredits >= nextTitle.requiredCredits
                ) {
                    promotions.add(
                        TeacherDevTeacherChange(
                            teacherId = profile.teacherId,
                            teacherName = profile.name
                        )
                    )
                    newEvents.add(TeacherDevEvent(
                        title = "${profile.name}晋升${nextTitle.displayName}",
                        description = "满足晋升条件，自动晋级",
                        type = TeacherDevEventType.PROMOTION,
                        month = currentMonth, year = currentYear
                    ))
                    profile.copy(
                        title = nextTitle,
                        monthsSinceLastPromotion = 0,
                        satisfaction = (profile.satisfaction + 10f).coerceAtMost(100f)
                    )
                } else {
                    profile
                }
            }
        }

        _state.update { state ->
            state.copy(
                teacherProfiles = updatedProfiles,
                activeTrainings = updatedTrainings,
                recentEvents = (newEvents + state.recentEvents).take(MAX_EVENTS_LOG),
                totalPromotions = state.totalPromotions + promotions.size,
                totalDepartures = state.totalDepartures + departures.size,
                lastProcessedYear = currentYear,
                lastProcessedMonth = currentMonth
            )
        }

        return TeacherDevMonthlyResult(
            expenses = trainingExpense,
            promotions = promotions,
            departures = departures,
            events = newEvents
        )
    }

    fun snapshotState(): TeacherDevState = _state.value.deepCopy()

    fun restoreSnapshot(snapshot: TeacherDevState) {
        _state.value = snapshot.deepCopy()
    }

    private fun TeacherDevState.deepCopy(): TeacherDevState {
        return copy(
            teacherProfiles = teacherProfiles.map { it.copy() },
            activeTrainings = activeTrainings.map { it.copy() },
            recentEvents = recentEvents.map { it.copy() }
        )
    }

    /**
     * 重置所有状态（新游戏/加载存档时调用）
     */
    fun reset() {
        _state.value = TeacherDevState()
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = TeacherDevPersistData(
                profiles = state.teacherProfiles.map { p ->
                    TeacherProfilePersist(
                        teacherId = p.legacyTeacherId,
                        teacherUuid = p.teacherId,
                        name = p.name,
                        subject = p.subject,
                        title = p.title.name,
                        skillLevel = p.skillLevel,
                        satisfaction = p.satisfaction,
                        yearsOfService = p.yearsOfService,
                        monthsSinceLastPromotion = p.monthsSinceLastPromotion,
                        trainingCredits = p.trainingCredits,
                        evaluationScore = p.evaluationScore,
                        researchPoints = p.researchPoints,
                        teachingAwards = p.teachingAwards,
                        isOnTraining = p.isOnTraining,
                        turnoverRisk = p.turnoverRisk.name
                    )
                },
                trainings = state.activeTrainings.map { t ->
                    TrainingPersist(
                        id = t.id,
                        teacherId = t.legacyTeacherId,
                        teacherUuid = t.teacherId,
                        teacherName = t.teacherName,
                        program = t.program.name,
                        remainingMonths = t.remainingMonths,
                        totalMonths = t.totalMonths
                    )
                },
                totalPromotions = state.totalPromotions,
                totalDepartures = state.totalDepartures,
                lastProcessedYear = state.lastProcessedYear,
                lastProcessedMonth = state.lastProcessedMonth
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<TeacherDevPersistData>(json)
            require(data.profiles.map { profile ->
                profile.teacherUuid ?: "legacy:${profile.teacherId}"
            }.distinct().size == data.profiles.size) {
                "Duplicate teacher development profile"
            }
            val profiles = data.profiles.map { p ->
                val teacherUuid = p.teacherUuid
                val legacyTeacherId = p.teacherId
                require(!teacherUuid.isNullOrBlank() || legacyTeacherId != null) {
                    "Missing teacher development identity"
                }
                require(p.skillLevel.isFinite() && p.skillLevel >= 0f) {
                    "Invalid teacher development skill"
                }
                require(p.satisfaction.isFinite() &&
                    p.satisfaction in 0f..100f
                ) { "Invalid teacher development satisfaction" }
                require(p.evaluationScore.isFinite() &&
                    p.evaluationScore in 0f..100f
                ) { "Invalid teacher development evaluation" }
                TeacherProfile(
                    teacherId = teacherUuid.orEmpty(),
                    legacyTeacherId = legacyTeacherId,
                    name = p.name,
                    subject = p.subject,
                    title = TeacherTitle.valueOf(p.title),
                    skillLevel = p.skillLevel,
                    satisfaction = p.satisfaction,
                    yearsOfService = p.yearsOfService,
                    monthsSinceLastPromotion = p.monthsSinceLastPromotion,
                    trainingCredits = p.trainingCredits,
                    evaluationScore = p.evaluationScore,
                    researchPoints = p.researchPoints,
                    teachingAwards = p.teachingAwards,
                    isOnTraining = p.isOnTraining,
                    turnoverRisk = TurnoverRisk.valueOf(p.turnoverRisk)
                )
            }
            val trainings = data.trainings.map { t ->
                val teacherUuid = t.teacherUuid
                val legacyTeacherId = t.teacherId
                require(!teacherUuid.isNullOrBlank() || legacyTeacherId != null) {
                    "Missing teacher training identity"
                }
                require(t.remainingMonths > 0 &&
                    t.totalMonths > 0 &&
                    t.remainingMonths <= t.totalMonths
                ) { "Invalid teacher training duration" }
                ActiveTraining(
                    id = t.id,
                    teacherId = teacherUuid.orEmpty(),
                    legacyTeacherId = legacyTeacherId,
                    teacherName = t.teacherName,
                    program = TrainingProgram.valueOf(t.program),
                    remainingMonths = t.remainingMonths,
                    totalMonths = t.totalMonths
                )
            }
            _state.value = TeacherDevState(
                teacherProfiles = profiles,
                activeTrainings = trainings,
                recentEvents = emptyList(),
                totalPromotions = data.totalPromotions,
                totalDepartures = data.totalDepartures,
                lastProcessedYear = data.lastProcessedYear,
                lastProcessedMonth = data.lastProcessedMonth
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("TeacherDevelopmentManager.restoreFromJson failed", e)
        }
    }

    fun getAvailablePrograms(): List<TrainingProgram> = TrainingProgram.entries.toList()

    fun getPromotionRequirements(teacherId: String): PromotionRequirements? {
        val profile = _state.value.teacherProfiles.find { it.teacherId == teacherId } ?: return null
        val nextTitle = TeacherTitle.entries.getOrNull(profile.title.ordinal + 1) ?: return null
        return PromotionRequirements(
            nextTitle = nextTitle,
            monthsNeeded = nextTitle.requiredMonths,
            monthsHad = profile.monthsSinceLastPromotion,
            evalNeeded = nextTitle.requiredEvalScore,
            evalHad = profile.evaluationScore,
            creditsNeeded = nextTitle.requiredCredits,
            creditsHad = profile.trainingCredits
        )
    }

    private fun teacherLevelToTitle(level: TeacherLevel): TeacherTitle {
        return when (level) {
            TeacherLevel.C -> TeacherTitle.JUNIOR
            TeacherLevel.B -> TeacherTitle.INTERMEDIATE
            TeacherLevel.A -> TeacherTitle.SENIOR
            TeacherLevel.S -> TeacherTitle.MASTER
        }
    }

    private fun determineInitialTitle(skill: Float): TeacherTitle {
        // skill 范围是 0-1000（averageSkill = 四维属性平均值）
        // C级: 100-300, B级: 250-500, A级: 450-700, S级: 650-900
        // 初始职称最高为 SENIOR（A级），MASTER/DISTINGUISHED 必须通过长期培训+学分积累晋升
        return when {
            skill >= 550f -> TeacherTitle.SENIOR       // A级以上教师 → 高级教师
            skill >= 350f -> TeacherTitle.INTERMEDIATE // B级教师 → 中级教师
            skill >= 200f -> TeacherTitle.JUNIOR       // C级教师 → 初级教师
            else -> TeacherTitle.PROBATION             // 新手 → 试用期
        }
    }
}

// ===== 数据模型 =====

data class TeacherDevState(
    val teacherProfiles: List<TeacherProfile> = emptyList(),
    val activeTrainings: List<ActiveTraining> = emptyList(),
    val recentEvents: List<TeacherDevEvent> = emptyList(),
    val totalPromotions: Int = 0,
    val totalDepartures: Int = 0,
    val lastProcessedYear: Int = 0,
    val lastProcessedMonth: Int = 0
)

data class TeacherProfile(
    val teacherId: String,
    val legacyTeacherId: Long? = null,
    val name: String,
    val subject: String,
    val title: TeacherTitle,
    val skillLevel: Float,
    val satisfaction: Float,
    val yearsOfService: Int,
    val monthsSinceLastPromotion: Int,
    val trainingCredits: Int,
    val evaluationScore: Float,
    val researchPoints: Int,
    val teachingAwards: Int,
    val isOnTraining: Boolean,
    val turnoverRisk: TurnoverRisk
)

enum class TeacherTitle(
    val displayName: String,
    val salaryMultiplier: Float,
    val requiredMonths: Int,
    val requiredEvalScore: Float,
    val requiredCredits: Int
) {
    PROBATION("试用期", 0.8f, 0, 0f, 0),
    JUNIOR("初级教师", 1.0f, 6, 50f, 5),
    INTERMEDIATE("中级教师", 1.3f, 12, 65f, 15),
    SENIOR("高级教师", 1.7f, 24, 75f, 30),
    MASTER("特级教师", 2.2f, 36, 85f, 50),
    DISTINGUISHED("名师", 3.0f, 48, 92f, 80)
}

enum class TurnoverRisk(val displayName: String, val color: Long) {
    LOW("稳定", 0xFF4CAF50),
    MEDIUM("一般", 0xFFFFC107),
    HIGH("较高", 0xFFFF9800),
    CRITICAL("危险", 0xFFF44336)
}

enum class TrainingProgram(
    val displayName: String,
    val description: String,
    val durationMonths: Int,
    val monthlyCostWan: Double,  // 单位：万元，与全系统统一
    val creditReward: Int,
    val skillBoost: Float
) {
    // monthlyCostWan 单位：万元（与 school.cash 等全系统财务单位一致）
    PEDAGOGY_BASIC("教学方法基础", "基础教学技能培训", 2, 0.8, 3, 2f),
    PEDAGOGY_ADVANCED("教学方法进阶", "高级教学策略研修", 3, 1.5, 6, 4f),
    SUBJECT_DEEP("学科深化研修", "学科专业知识深造", 4, 2.0, 8, 5f),
    EDUCATIONAL_TECH("教育技术培训", "现代教育技术应用", 2, 1.2, 4, 3f),
    LEADERSHIP("教育管理培训", "学校管理和领导力", 3, 2.5, 7, 3f),
    RESEARCH_METHOD("教育研究方法", "论文撰写与课题研究", 4, 2.2, 9, 4f),
    INTERNATIONAL("海外进修", "国际教育交流学习", 6, 5.0, 15, 8f),
    MENTAL_HEALTH("心理辅导培训", "学生心理辅导技能", 2, 1.0, 5, 3f)
}

data class ActiveTraining(
    val id: Long,
    val teacherId: String,
    val legacyTeacherId: Long? = null,
    val teacherName: String,
    val program: TrainingProgram,
    val remainingMonths: Int,
    val totalMonths: Int
)

data class TeacherDevEvent(
    val title: String,
    val description: String,
    val type: TeacherDevEventType,
    val month: Int,
    val year: Int
)

enum class TeacherDevEventType {
    TRAINING_COMPLETE,
    PROMOTION,
    DEPARTURE,
    AWARD,
    EVALUATION
}

data class TeacherDevMonthlyResult(
    val expenses: Double = 0.0,
    val promotions: List<TeacherDevTeacherChange> = emptyList(),
    val departures: List<TeacherDevTeacherChange> = emptyList(),
    val events: List<TeacherDevEvent> = emptyList()
)

data class TeacherDevTeacherChange(
    val teacherId: String,
    val teacherName: String
)

enum class PromotionResult {
    SUCCESS,
    NOT_FOUND,
    MAX_LEVEL,
    PERSISTENCE_FAILED,
    INSUFFICIENT_SENIORITY,
    LOW_EVALUATION,
    INSUFFICIENT_CREDITS
}

data class EvaluationResult(
    val score: Float,
    val previousScore: Float,
    val nextTitleName: String?,
    val requiredScore: Float?,
    val promotionEligibleByScore: Boolean
)

data class PromotionRequirements(
    val nextTitle: TeacherTitle,
    val monthsNeeded: Int,
    val monthsHad: Int,
    val evalNeeded: Float,
    val evalHad: Float,
    val creditsNeeded: Int,
    val creditsHad: Int
)

data class TeacherSyncData(
    val id: String,
    val legacyId: Long,
    val name: String,
    val subject: String,
    val skill: Float,
    val level: TeacherLevel
)

@Serializable
data class TeacherDevPersistData(
    val profiles: List<TeacherProfilePersist> = emptyList(),
    val trainings: List<TrainingPersist> = emptyList(),
    val totalPromotions: Int = 0,
    val totalDepartures: Int = 0,
    val lastProcessedYear: Int = 0,
    val lastProcessedMonth: Int = 0
)

@Serializable
data class TeacherProfilePersist(
    val teacherId: Long? = null,
    val teacherUuid: String? = null,
    val name: String,
    val subject: String,
    val title: String,
    val skillLevel: Float,
    val satisfaction: Float,
    val yearsOfService: Int,
    val monthsSinceLastPromotion: Int,
    val trainingCredits: Int,
    val evaluationScore: Float,
    val researchPoints: Int,
    val teachingAwards: Int,
    val isOnTraining: Boolean,
    val turnoverRisk: String
)

@Serializable
data class TrainingPersist(
    val id: Long,
    val teacherId: Long? = null,
    val teacherUuid: String? = null,
    val teacherName: String,
    val program: String,
    val remainingMonths: Int,
    val totalMonths: Int
)
