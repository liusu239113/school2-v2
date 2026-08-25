package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.StatisticsManager
import com.arktools.xiaozhang.domain.model.CourseProject
import com.arktools.xiaozhang.domain.model.CourseStatus
import com.arktools.xiaozhang.domain.model.ClassTier
import com.arktools.xiaozhang.domain.model.Facility
import com.arktools.xiaozhang.domain.model.FacilityBonusCalculator
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.model.ActivityAction
import com.arktools.xiaozhang.domain.model.ClubAction
import com.arktools.xiaozhang.domain.model.EventChoice
import com.arktools.xiaozhang.domain.model.EventConsequence
import com.arktools.xiaozhang.domain.model.TeacherAction
import com.arktools.xiaozhang.domain.model.GameEvent
import com.arktools.xiaozhang.domain.model.MarketingCalculator
import com.arktools.xiaozhang.domain.model.Principal
import com.arktools.xiaozhang.domain.model.InvestigationResult
import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.model.SubjectConfig
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherState
import com.arktools.xiaozhang.domain.model.TeacherTrait
import com.arktools.xiaozhang.domain.model.TraitCategory
import com.arktools.xiaozhang.domain.model.BonusType
import com.arktools.xiaozhang.domain.model.CourseScale
import com.arktools.xiaozhang.domain.model.Student
import com.arktools.xiaozhang.domain.model.StudentNameGenerator
import com.arktools.xiaozhang.domain.model.StudentProgressCalculator
import com.arktools.xiaozhang.domain.model.StudentSatisfactionCalculator
import com.arktools.xiaozhang.domain.model.StudentStatus
import com.arktools.xiaozhang.domain.model.StudentTraitAssigner
import com.arktools.xiaozhang.domain.model.StudentAttributes
import com.arktools.xiaozhang.domain.model.BackgroundTier
import com.arktools.xiaozhang.domain.model.GradeLevel
import com.arktools.xiaozhang.domain.model.SchoolClass
import com.arktools.xiaozhang.domain.model.ClassStrategy
import com.arktools.xiaozhang.domain.model.ClassEvent
import com.arktools.xiaozhang.domain.model.HealthStatus
import com.arktools.xiaozhang.domain.model.UniversityTier
import com.arktools.xiaozhang.domain.model.StudentReview
import com.arktools.xiaozhang.domain.exam.GaoKaoCalculator
import com.arktools.xiaozhang.domain.repository.CourseRepository
import com.arktools.xiaozhang.domain.repository.ResearchRepository
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StockRepository
import com.arktools.xiaozhang.domain.repository.StockTradeResult
import com.arktools.xiaozhang.domain.repository.EnrollmentCommitResult
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.StudentYearEndTransition
import com.arktools.xiaozhang.domain.repository.TeacherDevelopmentProfileUpdate
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.domain.policy.EnrollmentPlan
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

internal fun isMonthlySettlementDue(
    currentYear: Int,
    currentMonth: Int,
    currentDay: Int,
    lastSettlementYear: Int,
    lastSettlementMonth: Int
): Boolean {
    val current = currentYear * 12 + currentMonth
    val completed = lastSettlementYear * 12 + lastSettlementMonth
    // 不能只在1日触发：若当月1日被崩溃/读库异常打断，后续日期也必须补做月结。
    return completed < current
}

internal fun isStudentGraduationDue(
    gradeLevel: GradeLevel,
    enrollYear: Int,
    processingYear: Int,
    processingMonth: Int
): Boolean {
    if (gradeLevel != GradeLevel.GRADE_3) return false
    if (enrollYear <= 0) return processingMonth >= 6
    val enrolledYears = processingYear - enrollYear
    return enrolledYears > 3 ||
        (enrolledYears == 3 && processingMonth >= 6)
}

data class ManagedOperationResult(
    val success: Boolean,
    val message: String,
    val amount: Double = 0.0
)

private data class TeacherDevelopmentCommit<T>(
    val value: T,
    val shouldCommit: Boolean,
    val expense: Double = 0.0,
    val departedTeacherIds: List<String> = emptyList(),
    val profileUpdates: List<TeacherDevelopmentProfileUpdate> = emptyList(),
    val pressureJson: String? = null,
    val timetableJson: String? = null
)

@Singleton
class GameEngine @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val teacherRepository: TeacherRepository,
    private val courseRepository: CourseRepository,
    private val researchRepository: ResearchRepository,
    private val stockRepository: StockRepository,
    private val studentRepository: StudentRepository,
    private val eventGenerator: EventGenerator,
    private val stockEventGenerator: StockEventGenerator,
    private val settingsDataStore: SettingsDataStore,
    private val achievementManager: com.arktools.xiaozhang.domain.achievement.AchievementManager,
    private val milestoneManager: com.arktools.xiaozhang.domain.milestone.MilestoneManager,
    private val competitorEngine: com.arktools.xiaozhang.domain.competitor.CompetitorEngine,
    val gameOverDetector: GameOverDetector,
    private val notificationManager: com.arktools.xiaozhang.domain.notification.NotificationManager,
    val alumniNetwork: com.arktools.xiaozhang.domain.alumni.AlumniNetwork,
    val policyManager: com.arktools.xiaozhang.domain.policy.SchoolPolicyManager,
    val clubManager: com.arktools.xiaozhang.domain.club.ClubManager,
    val seasonalActivityManager: com.arktools.xiaozhang.domain.seasonal.SeasonalActivityManager,
    val employmentMarket: com.arktools.xiaozhang.domain.employment.EmploymentMarket,
    val reputationManager: com.arktools.xiaozhang.domain.reputation.ReputationManager,
    val studentLifeManager: com.arktools.xiaozhang.domain.studentlife.StudentLifeManager,
    val campusExpansionManager: com.arktools.xiaozhang.domain.expansion.CampusExpansionManager,
    val academicConferenceManager: com.arktools.xiaozhang.domain.conference.AcademicConferenceManager,
    val clubActivityManager: com.arktools.xiaozhang.domain.clubactivity.ClubActivityManager,
    val teacherDevelopmentManager: com.arktools.xiaozhang.domain.teacherdev.TeacherDevelopmentManager,
    val financialReportManager: com.arktools.xiaozhang.domain.finance.FinancialReportManager,
    val parentSatisfactionManager: com.arktools.xiaozhang.domain.parent.ParentSatisfactionManager,
    val governmentInspectionManager: com.arktools.xiaozhang.domain.government.GovernmentInspectionManager,
    val scholarshipManager: com.arktools.xiaozhang.domain.scholarship.ScholarshipManager,
    val corruptionManager: CorruptionManager,
    val connectionManager: ConnectionManager,
    val factionManager: FactionManager,
    val classManager: ClassManager,
    val timetableManager: com.arktools.xiaozhang.domain.timetable.TimetableManager,
    val examManager: com.arktools.xiaozhang.domain.exam.ExamManager,
    val teachingManager: com.arktools.xiaozhang.domain.teaching.TeachingManager,
    val pressureSystemManager: PressureSystemManager,
    val crisisScenarioManager: CrisisScenarioManager,
    val suggestionBoxManager: com.arktools.xiaozhang.domain.suggestion.SuggestionBoxManager
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gameLoopJob: Job? = null
    /** Serializes ticks, state flushes, and load preparation so a slot switch cannot race DB writes. */
    private val engineOperationMutex = Mutex()
    @Volatile
    private var engineStopping = false
    private val _isPaused = MutableStateFlow(true)
    val isPausedFlow: StateFlow<Boolean> = _isPaused.asStateFlow()
    private var isPaused: Boolean
        get() = _isPaused.value
        set(value) { _isPaused.value = value }

    private val baseTickIntervalMs: Long = 5000L

    // Save-in-progress flag: blocks tick without affecting UI pause state
    @Volatile
    private var isSaving: Boolean = false

    fun setSaving(saving: Boolean) {
        isSaving = saving
    }

    // 事件流必须永不阻塞游戏循环：慢/无消费者时丢弃最旧事件而不是让 tick 挂起。
    private val _events = MutableSharedFlow<GameEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    // 延迟事件队列：月初产生的非紧急事件分散到后续几天逐个释放，避免弹窗堆积
    // 使用线程安全集合，避免并发修改导致 ConcurrentModificationException
    private val _deferredEvents = java.util.concurrent.ConcurrentLinkedQueue<GameEvent>()

    /** 股价记录去重：避免每次 tick 都写一条导致表无限膨胀 */
    private var lastRecordedPriceDay = -1

    /** 游戏日推进信号：每次 tick 推进一天后发射，供 ViewModel 监听刷新 */
    private val _gameDaySignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val gameDaySignal: SharedFlow<Unit> = _gameDaySignal.asSharedFlow()

    /** 月结失败后仅由月结块内部设置的重试标记；日常异常不得触发月结重放。 */
    @Volatile
    private var pendingMonthlySettlementRetry = false

    /** 启动或运行中学生年结失败后，只重试年级、毕业与班级恢复。 */
    @Volatile
    private var pendingStudentYearEndRecovery = false

    /** 启动恢复时毕业投影提交失败后，在后续 tick 内继续幂等重试。 */
    @Volatile
    private var pendingGraduationProjectionRetry = false

    private val graduationProjectionManagerFields = setOf(
        "alumniJson",
        "employmentJson",
        "pressureJson",
        "timetableJson",
        "headTeacherMapJson",
        "classTierMapJson"
    )

    /**
     * 完成 Manager 恢复后才允许写回。单字段恢复失败时保留其原始 JSON，
     * 其他成功字段仍可正常持久化。
     */
    @Volatile
    private var managerStatesReadyForSave = false
    private val managerRestoreFailedFields = mutableSetOf<String>()

    private inline fun restoreManagerField(
        fieldName: String,
        json: String,
        restore: () -> Unit
    ) {
        if (json.isBlank()) return
        try {
            restore()
        } catch (e: Exception) {
            managerRestoreFailedFields.add(fieldName)
            android.util.Log.e(
                "GameEngine",
                "Manager restore failed for $fieldName; original JSON will be preserved",
                e
            )
        }
    }

    private inline fun protectedManagerJson(
        fieldName: String,
        previous: String,
        serialize: () -> String
    ): String {
        if (fieldName in managerRestoreFailedFields) return previous
        val serialized = try {
            serialize()
        } catch (e: Exception) {
            android.util.Log.e("GameEngine", "Manager serialization failed for $fieldName", e)
            ""
        }
        if (serialized.isBlank() && previous.isNotBlank()) {
            android.util.Log.e(
                "GameEngine",
                "Manager serialization returned blank for $fieldName; preserving previous JSON"
            )
            return previous
        }
        return serialized
    }

    private fun writeClassJsonFields(
        target: School,
        headTeacherJson: String,
        classTierJson: String
    ): Boolean {
        var changed = false
        if (
            "headTeacherMapJson" !in managerRestoreFailedFields &&
            target.headTeacherMapJson != headTeacherJson
        ) {
            target.headTeacherMapJson = headTeacherJson
            changed = true
        }
        if (
            "classTierMapJson" !in managerRestoreFailedFields &&
            target.classTierMapJson != classTierJson
        ) {
            target.classTierMapJson = classTierJson
            changed = true
        }
        return changed
    }

    private fun writeManagerJsonFields(target: School) {
        target.studentLifeJson = protectedManagerJson(
            "studentLifeJson", target.studentLifeJson, studentLifeManager::toJson
        )
        target.reputationJson = protectedManagerJson(
            "reputationJson", target.reputationJson, reputationManager::toJson
        )
        target.achievementJson = protectedManagerJson(
            "achievementJson", target.achievementJson, achievementManager::toJson
        )
        target.milestoneJson = protectedManagerJson(
            "milestoneJson", target.milestoneJson, milestoneManager::toJson
        )
        target.teacherDevJson = protectedManagerJson(
            "teacherDevJson", target.teacherDevJson, teacherDevelopmentManager::toJson
        )
        target.clubJson = protectedManagerJson(
            "clubJson", target.clubJson, clubManager::toJson
        )
        target.scholarshipJson = protectedManagerJson(
            "scholarshipJson", target.scholarshipJson, scholarshipManager::toJson
        )
        target.expansionJson = protectedManagerJson(
            "expansionJson", target.expansionJson, campusExpansionManager::toJson
        )
        target.governmentJson = protectedManagerJson(
            "governmentJson", target.governmentJson, governmentInspectionManager::toJson
        )
        target.parentJson = protectedManagerJson(
            "parentJson", target.parentJson, parentSatisfactionManager::toJson
        )
        target.policyJson = protectedManagerJson(
            "policyJson", target.policyJson, policyManager::toJson
        )
        target.seasonalJson = protectedManagerJson(
            "seasonalJson", target.seasonalJson, seasonalActivityManager::toJson
        )
        target.conferenceJson = protectedManagerJson(
            "conferenceJson", target.conferenceJson, academicConferenceManager::toJson
        )
        target.clubActivityJson = protectedManagerJson(
            "clubActivityJson", target.clubActivityJson, clubActivityManager::toJson
        )
        target.timetableJson = protectedManagerJson(
            "timetableJson", target.timetableJson, timetableManager::toJson
        )
        target.examJson = protectedManagerJson(
            "examJson", target.examJson, examManager::toJson
        )
        target.teachingConfigJson = protectedManagerJson(
            "teachingConfigJson", target.teachingConfigJson, teachingManager::toJson
        )
        target.statisticsJson = protectedManagerJson(
            "statisticsJson", target.statisticsJson, StatisticsManager::toJson
        )
        target.financialReportJson = protectedManagerJson(
            "financialReportJson", target.financialReportJson, financialReportManager::toJson
        )
        target.pressureJson = protectedManagerJson(
            "pressureJson", target.pressureJson, pressureSystemManager::toJson
        )
        target.competitorJson = protectedManagerJson(
            "competitorJson", target.competitorJson, competitorEngine::toJson
        )
        target.crisisJson = protectedManagerJson(
            "crisisJson", target.crisisJson, crisisScenarioManager::toJson
        )
        target.alumniJson = protectedManagerJson(
            "alumniJson", target.alumniJson, alumniNetwork::toJson
        )
        target.employmentJson = protectedManagerJson(
            "employmentJson", target.employmentJson, employmentMarket::toJson
        )
        target.principalJson = protectedManagerJson(
            "principalJson",
            target.principalJson
        ) {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Principal>(),
                _principal.value
            )
        }
        target.suggestionBoxJson = protectedManagerJson(
            "suggestionBoxJson",
            target.suggestionBoxJson,
            suggestionBoxManager::toJson
        )
    }

    private fun teacherTitleToLevel(
        title: com.arktools.xiaozhang.domain.teacherdev.TeacherTitle
    ): com.arktools.xiaozhang.domain.model.TeacherLevel {
        return when (title) {
            com.arktools.xiaozhang.domain.teacherdev.TeacherTitle.PROBATION,
            com.arktools.xiaozhang.domain.teacherdev.TeacherTitle.JUNIOR ->
                com.arktools.xiaozhang.domain.model.TeacherLevel.C
            com.arktools.xiaozhang.domain.teacherdev.TeacherTitle.INTERMEDIATE ->
                com.arktools.xiaozhang.domain.model.TeacherLevel.B
            com.arktools.xiaozhang.domain.teacherdev.TeacherTitle.SENIOR ->
                com.arktools.xiaozhang.domain.model.TeacherLevel.A
            com.arktools.xiaozhang.domain.teacherdev.TeacherTitle.MASTER,
            com.arktools.xiaozhang.domain.teacherdev.TeacherTitle.DISTINGUISHED ->
                com.arktools.xiaozhang.domain.model.TeacherLevel.S
        }
    }

    private fun isPrimarilyTeaching(teacher: Teacher): Boolean {
        return teacher.role.category in listOf(
            com.arktools.xiaozhang.domain.model.SubjectCategory.LITERATURE,
            com.arktools.xiaozhang.domain.model.SubjectCategory.SCIENCE,
            com.arktools.xiaozhang.domain.model.SubjectCategory.LANGUAGE
        )
    }

    private suspend fun <T> commitTeacherDevelopmentOperationLocked(
        unavailableValue: T,
        operation: suspend () -> TeacherDevelopmentCommit<T>
    ): Pair<T, Boolean> {
        if (!managerStatesReadyForSave ||
            "teacherDevJson" in managerRestoreFailedFields
        ) {
            return unavailableValue to false
        }
        return withContext(NonCancellable) {
            val teacherDevelopmentSnapshot = teacherDevelopmentManager.snapshotState()
            val pressureSnapshot = pressureSystemManager.snapshotState()
            val timetableSnapshot = timetableManager.snapshotState()
            fun restoreSnapshots() {
                teacherDevelopmentManager.restoreSnapshot(teacherDevelopmentSnapshot)
                pressureSystemManager.restoreSnapshot(pressureSnapshot)
                timetableManager.restoreSnapshot(timetableSnapshot)
            }
            try {
                val pending = operation()
                if (!pending.shouldCommit) {
                    return@withContext pending.value to true
                }
                val json = teacherDevelopmentManager.toJson()
                if (json.isBlank()) {
                    restoreSnapshots()
                    return@withContext pending.value to false
                }
                val committed = teacherRepository.commitDevelopmentState(
                    expense = pending.expense,
                    teacherDevJson = json,
                    departedTeacherIds = pending.departedTeacherIds,
                    profileUpdates = pending.profileUpdates,
                    pressureJson = pending.pressureJson,
                    timetableJson = pending.timetableJson
                )
                if (!committed) {
                    restoreSnapshots()
                }
                pending.value to committed
            } catch (e: Exception) {
                restoreSnapshots()
                throw e
            }
        }
    }

    suspend fun startAcademicConference(
        type: com.arktools.xiaozhang.domain.conference.ConferenceType,
        role: com.arktools.xiaozhang.domain.conference.ConferenceRole,
        field: com.arktools.xiaozhang.domain.conference.AcademicField
    ): ManagedOperationResult = engineOperationMutex.withLock {
        if (!managerStatesReadyForSave || "conferenceJson" in managerRestoreFailedFields) {
            return@withLock ManagedOperationResult(false, "会议状态尚未安全恢复，请稍后重试")
        }
        val snapshot = academicConferenceManager.toJson()
        var conferenceName = ""
        var expectedFinish = ""
        val committed = schoolRepository.mutateSchool { school ->
            val cost = type.baseCost * role.costMultiplier
            if (school.cash < cost) return@mutateSchool false
            val conference = academicConferenceManager.createConference(
                type = type,
                role = role,
                field = field,
                year = school.currentYear,
                month = school.currentMonth,
                schoolLevel = school.campusLevel
            ) ?: return@mutateSchool false
            school.cash -= cost
            conferenceName = conference.name
            val finishAbsoluteMonth = school.currentYear * 12 + school.currentMonth + type.duration
            expectedFinish = "${finishAbsoluteMonth / 12}年${finishAbsoluteMonth % 12}月"
            school.conferenceJson = academicConferenceManager.toJson()
            true
        }
        if (committed == null) {
            academicConferenceManager.restoreFromJson(snapshot)
            return@withLock ManagedOperationResult(false, "无法发起会议：资金不足、等级不足、冷却中或并发会议已满")
        }
        ManagedOperationResult(
            true,
            "已发起${conferenceName}，预算已锁定，预计${expectedFinish}结算成果"
        )
    }

    suspend fun startTeacherDevelopmentTraining(
        teacherId: String,
        program: com.arktools.xiaozhang.domain.teacherdev.TrainingProgram
    ): ManagedOperationResult = engineOperationMutex.withLock {
        val unavailable = ManagedOperationResult(
            false,
            "教师发展存档尚未安全恢复，暂不能安排培训"
        )
        val (result, persisted) = commitTeacherDevelopmentOperationLocked(
            unavailable
        ) {
            val teacher = teacherRepository.getTeacherById(teacherId)
            if (teacher == null || !teacher.isWorking) {
                TeacherDevelopmentCommit(
                    ManagedOperationResult(false, "教师状态已变化，无法安排培训"),
                    false
                )
            } else {
                val started = teacherDevelopmentManager.startTraining(
                    teacherId,
                    program
                )
                TeacherDevelopmentCommit(
                    ManagedOperationResult(
                        started,
                        if (started) "培训已安排" else "教师已在培训中或培训名额已满"
                    ),
                    started
                )
            }
        }
        if (persisted) result else ManagedOperationResult(
            false,
            "教师发展状态保存失败，请重试"
        )
    }

    suspend fun promoteTeacherDevelopment(
        teacherId: String
    ): com.arktools.xiaozhang.domain.teacherdev.PromotionResult =
        engineOperationMutex.withLock {
            val failure =
                com.arktools.xiaozhang.domain.teacherdev.PromotionResult.PERSISTENCE_FAILED
            val (result, persisted) = commitTeacherDevelopmentOperationLocked(
                failure
            ) {
                val teacher = teacherRepository.getTeacherById(teacherId)
                if (teacher == null || !teacher.isWorking) {
                    TeacherDevelopmentCommit(
                        com.arktools.xiaozhang.domain.teacherdev.PromotionResult.NOT_FOUND,
                        false
                    )
                } else {
                    val promotion = teacherDevelopmentManager.tryPromote(teacherId)
                    if (promotion !=
                        com.arktools.xiaozhang.domain.teacherdev.PromotionResult.SUCCESS
                    ) {
                        TeacherDevelopmentCommit(promotion, false)
                    } else {
                        val profile = teacherDevelopmentManager.state.value.teacherProfiles
                            .find { it.teacherId == teacherId }
                            ?: error("Promoted teacher profile missing")
                        TeacherDevelopmentCommit(
                            value = promotion,
                            shouldCommit = true,
                            profileUpdates = listOf(
                                TeacherDevelopmentProfileUpdate(
                                    teacherId = teacherId,
                                    level = teacherTitleToLevel(profile.title),
                                    profileSkillLevel = profile.skillLevel,
                                    primarilyTeaching = isPrimarilyTeaching(teacher)
                                )
                            )
                        )
                    }
                }
            }
            if (persisted) result else failure
        }

    suspend fun evaluateTeacherDevelopment(
        teacherId: String
    ): com.arktools.xiaozhang.domain.teacherdev.EvaluationResult? =
        engineOperationMutex.withLock {
            val (result, persisted) = commitTeacherDevelopmentOperationLocked<
                com.arktools.xiaozhang.domain.teacherdev.EvaluationResult?
            >(null) {
            val teacher = teacherRepository.getTeacherById(teacherId)
            val profileExists = teacherDevelopmentManager.state.value.teacherProfiles
                .any { it.teacherId == teacherId }
            if (teacher == null || !teacher.isWorking || !profileExists) {
                TeacherDevelopmentCommit(null, false)
            } else {
                TeacherDevelopmentCommit(
                    teacherDevelopmentManager.conductEvaluation(teacherId),
                    true
                )
            }
        }
            if (persisted) result else null
        }

    private suspend fun commitStudentLifeOperationLocked(
        operation: (School) -> ManagedOperationResult
    ): ManagedOperationResult {
        if (!managerStatesReadyForSave ||
            "studentLifeJson" in managerRestoreFailedFields
        ) {
            return ManagedOperationResult(
                false,
                "学生生活存档尚未安全恢复，暂不能执行此操作"
            )
        }
        return withContext(NonCancellable) {
            val snapshot = studentLifeManager.snapshotState()
            var result = ManagedOperationResult(false, "学校存档不可用，请重试")
            val committed = try {
                schoolRepository.mutateSchool { school ->
                    result = operation(school)
                    if (!result.success) return@mutateSchool false
                    val json = studentLifeManager.toJson()
                    if (json.isBlank()) {
                        result = ManagedOperationResult(false, "学生生活状态保存失败，请重试")
                        return@mutateSchool false
                    }
                    school.studentLifeJson = json
                    true
                }
            } catch (e: Exception) {
                studentLifeManager.restoreSnapshot(snapshot)
                throw e
            }
            if (committed == null) {
                studentLifeManager.restoreSnapshot(snapshot)
            }
            result
        }
    }

    suspend fun setStudentLifeProgramActive(
        programId: String,
        active: Boolean
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitStudentLifeOperationLocked {
            val changed = if (active) {
                studentLifeManager.activateProgram(programId)
            } else {
                studentLifeManager.deactivateProgram(programId)
            }
            if (changed) {
                ManagedOperationResult(
                    true,
                    if (active) "专项计划已开设" else "专项计划已关闭"
                )
            } else {
                ManagedOperationResult(false, "专项计划状态已变化")
            }
        }
    }

    suspend fun upgradeStudentLifeFacility(
        aspect: com.arktools.xiaozhang.domain.studentlife.LifeAspect
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitStudentLifeOperationLocked { school ->
            val cost = studentLifeManager.getUpgradeCost(aspect).toDouble()
            when {
                cost <= 0.0 -> ManagedOperationResult(false, "当前设施无法升级")
                school.cash < cost -> ManagedOperationResult(
                    false,
                    "资金不足，需要 ¥${cost.toLong()}万",
                    cost
                )
                !studentLifeManager.applyFacilityUpgrade(
                    aspect,
                    school.campusLevel,
                    cost.toLong()
                ) -> ManagedOperationResult(false, "设施状态已变化，请重试")
                else -> {
                    school.cash -= cost
                    ManagedOperationResult(
                        true,
                        "升级成功，花费 ¥${cost.toLong()}万",
                        cost
                    )
                }
            }
        }
    }

    suspend fun repairStudentLifeFacility(
        aspect: com.arktools.xiaozhang.domain.studentlife.LifeAspect
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitStudentLifeOperationLocked { school ->
            val cost = studentLifeManager.getRepairCost(aspect).toDouble()
            when {
                cost <= 0.0 -> ManagedOperationResult(false, "当前设施无需维修")
                school.cash < cost -> ManagedOperationResult(
                    false,
                    "资金不足，需要 ¥${cost.toLong()}万",
                    cost
                )
                !studentLifeManager.applyFacilityRepair(
                    aspect,
                    cost.toLong()
                ) -> ManagedOperationResult(false, "设施状态已变化，请重试")
                else -> {
                    school.cash -= cost
                    ManagedOperationResult(
                        true,
                        "维修完成，花费 ¥${cost.toLong()}万",
                        cost
                    )
                }
            }
        }
    }

    suspend fun repairAllStudentLifeFacilities(): ManagedOperationResult =
        engineOperationMutex.withLock {
            commitStudentLifeOperationLocked { school ->
                val cost = studentLifeManager.getRepairAllCost().toDouble()
                when {
                    cost <= 0.0 -> ManagedOperationResult(false, "当前设施无需维修")
                    school.cash < cost -> ManagedOperationResult(
                        false,
                        "资金不足，需要 ¥${cost.toLong()}万",
                        cost
                    )
                    !studentLifeManager.applyRepairAll(cost.toLong()) ->
                        ManagedOperationResult(false, "设施状态已变化，请重试")
                    else -> {
                        school.cash -= cost
                        ManagedOperationResult(
                            true,
                            "一键维修完成，花费 ¥${cost.toLong()}万",
                            cost
                        )
                    }
                }
            }
        }

    suspend fun expandStudentLifeCapacity(
        aspect: com.arktools.xiaozhang.domain.studentlife.LifeAspect,
        additional: Int
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitStudentLifeOperationLocked { school ->
            val cost = studentLifeManager.getExpandCost(
                aspect,
                additional
            ).toDouble()
            when {
                additional <= 0 || cost <= 0.0 ->
                    ManagedOperationResult(false, "当前无法扩容")
                school.cash < cost -> ManagedOperationResult(
                    false,
                    "资金不足，需要 ¥${cost.toLong()}万",
                    cost
                )
                !studentLifeManager.applyCapacityExpansion(
                    aspect,
                    additional,
                    cost.toLong()
                ) -> ManagedOperationResult(false, "设施状态已变化，请重试")
                else -> {
                    school.cash -= cost
                    ManagedOperationResult(
                        true,
                        "扩容+$additional 人，花费 ¥${cost.toLong()}万",
                        cost
                    )
                }
            }
        }
    }

    private suspend fun commitExpansionOperationLocked(
        operation: (School) -> ManagedOperationResult
    ): ManagedOperationResult {
        if (!managerStatesReadyForSave ||
            "expansionJson" in managerRestoreFailedFields
        ) {
            return ManagedOperationResult(
                false,
                "校区扩建存档尚未安全恢复，暂不能执行此操作"
            )
        }
        return withContext(NonCancellable) {
            val snapshot = campusExpansionManager.snapshotState()
            var result = ManagedOperationResult(false, "学校存档不可用，请重试")
            val committed = try {
                schoolRepository.mutateSchool { school ->
                    result = operation(school)
                    if (!result.success) return@mutateSchool false
                    val json = campusExpansionManager.toJson()
                    if (json.isBlank()) {
                        result = ManagedOperationResult(false, "校区扩建状态保存失败，请重试")
                        return@mutateSchool false
                    }
                    school.expansionJson = json
                    true
                }
            } catch (e: Exception) {
                campusExpansionManager.restoreSnapshot(snapshot)
                throw e
            }
            if (committed == null) {
                campusExpansionManager.restoreSnapshot(snapshot)
            }
            result
        }
    }

    suspend fun startCampusConstruction(
        type: com.arktools.xiaozhang.domain.expansion.CampusZoneType,
        name: String,
        quality: Int
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitExpansionOperationLocked {
            val zone = campusExpansionManager.startConstruction(
                type,
                name,
                quality
            )
            if (zone == null) {
                ManagedOperationResult(false, "校区建筑数量已达上限")
            } else {
                ManagedOperationResult(true, "${zone.name}项目已创建")
            }
        }
    }

    suspend fun upgradeCampusExpansionLevel(): ManagedOperationResult =
        engineOperationMutex.withLock {
            commitExpansionOperationLocked { school ->
                val state = campusExpansionManager.state.value
                val currentIndex =
                    com.arktools.xiaozhang.domain.expansion.CampusLevel.entries
                        .indexOf(state.currentLevel)
                if (currentIndex >=
                    com.arktools.xiaozhang.domain.expansion.CampusLevel.entries.size - 1
                ) {
                    return@commitExpansionOperationLocked ManagedOperationResult(
                        false,
                        "校区已达最高等级！"
                    )
                }
                val nextLevel =
                    com.arktools.xiaozhang.domain.expansion.CampusLevel.entries[
                        currentIndex + 1
                    ]
                val requiredBuildings =
                    (state.currentLevel.maxZones * 0.6).toInt().coerceAtLeast(1)
                val completedCount = state.zones.count { it.isCompleted }
                when {
                    completedCount < requiredBuildings -> ManagedOperationResult(
                        false,
                        "需先建成至少 $requiredBuildings 栋建筑（当前已建成 $completedCount 栋）"
                    )
                    school.cash < nextLevel.unlockCostWan -> ManagedOperationResult(
                        false,
                        "资金不足！需要 ${nextLevel.unlockCostWan.toInt()}万（当前 ${school.cash.toInt()}万）",
                        nextLevel.unlockCostWan
                    )
                    else -> {
                        val actualCost =
                            campusExpansionManager.upgradeCampusLevel()
                        if (abs(actualCost - nextLevel.unlockCostWan) > 0.000001) {
                            ManagedOperationResult(false, "校区状态已变化，请重试")
                        } else {
                            school.cash -= actualCost
                            ManagedOperationResult(
                                true,
                                "校区升级成功！当前等级: ${nextLevel.displayName}（最大建筑数: ${nextLevel.maxZones}）",
                                actualCost
                            )
                        }
                    }
                }
            }
        }

    suspend fun investInCampusZone(
        zoneId: String,
        requestedAmountWan: Double
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitExpansionOperationLocked { school ->
            val amount = campusExpansionManager.getInvestmentAmount(
                zoneId,
                requestedAmountWan
            )
            when {
                amount <= 0.0 -> ManagedOperationResult(
                    false,
                    "项目已完成或投资金额无效"
                )
                school.cash < amount -> ManagedOperationResult(
                    false,
                    "资金不足，无法追加投资",
                    amount
                )
                else -> {
                    val applied = campusExpansionManager.investInZone(
                        zoneId,
                        amount
                    )
                    if (abs(applied - amount) > 0.000001) {
                        ManagedOperationResult(false, "项目状态已变化，请重试")
                    } else {
                        school.cash -= applied
                        ManagedOperationResult(
                            true,
                            "已追加投资 ${String.format("%.1f", applied)} 万元",
                            applied
                        )
                    }
                }
            }
        }
    }

    suspend fun repairCampusZone(
        zoneId: String
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitExpansionOperationLocked { school ->
            val cost = campusExpansionManager.getRepairCost(zoneId)
            when {
                cost <= 0.0 -> ManagedOperationResult(false, "当前建筑无需维修")
                school.cash < cost -> ManagedOperationResult(
                    false,
                    "资金不足！维修需要 ${String.format("%.1f", cost)} 万",
                    cost
                )
                else -> {
                    val actualCost = campusExpansionManager.repairZone(zoneId)
                    if (actualCost <= 0.0 || abs(actualCost - cost) > 0.000001) {
                        ManagedOperationResult(false, "建筑状态已变化，请重试")
                    } else {
                        school.cash -= actualCost
                        ManagedOperationResult(
                            true,
                            "维修完成，支付 ${String.format("%.1f", actualCost)} 万元",
                            actualCost
                        )
                    }
                }
            }
        }
    }

    suspend fun upgradeCampusZoneQuality(
        zoneId: String
    ): ManagedOperationResult = engineOperationMutex.withLock {
        commitExpansionOperationLocked { school ->
            val cost = campusExpansionManager.getUpgradeQualityCost(zoneId)
            when {
                cost <= 0.0 -> ManagedOperationResult(false, "当前建筑无法升级")
                school.cash < cost -> ManagedOperationResult(
                    false,
                    "资金不足！升级需要 ${String.format("%.1f", cost)} 万",
                    cost
                )
                else -> {
                    val actualCost =
                        campusExpansionManager.upgradeZoneQuality(zoneId)
                    if (actualCost <= 0.0 || abs(actualCost - cost) > 0.000001) {
                        ManagedOperationResult(false, "建筑状态已变化，请重试")
                    } else {
                        school.cash -= actualCost
                        ManagedOperationResult(
                            true,
                            "建筑质量升级完成，支付 ${String.format("%.1f", actualCost)} 万元",
                            actualCost
                        )
                    }
                }
            }
        }
    }

    /** 教程期间抑制事件弹窗 */
    @Volatile
    var eventsSuppressed: Boolean = false

    // 月结算奖励信号：发出本月净收入（总收入-总支出），UI 可弹窗"看广告翻倍"
    // 必须不阻塞游戏循环：无消费者时丢弃而不是让 tick 挂起。
    private val _monthlyRevenueBonus = MutableSharedFlow<Double>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val monthlyRevenueBonus: SharedFlow<Double> = _monthlyRevenueBonus.asSharedFlow()

    data class DisciplinaryPause(
        val title: String,
        val message: String
    )

    private val _disciplinaryPause = MutableStateFlow<DisciplinaryPause?>(null)
    val disciplinaryPause: StateFlow<DisciplinaryPause?> = _disciplinaryPause.asStateFlow()

    // 迷你游戏触发信号：活动进入 ACTIVE 阶段时发出，UI 弹出对应迷你游戏
    // 必须不阻塞游戏循环：无消费者时丢弃而不是让 tick 挂起。
    private val _miniGameTrigger = MutableSharedFlow<com.arktools.xiaozhang.domain.seasonal.SeasonalActivity>(
        extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val miniGameTrigger: SharedFlow<com.arktools.xiaozhang.domain.seasonal.SeasonalActivity> = _miniGameTrigger.asSharedFlow()

    // === 校长个人系统 ===
    private val _principal = MutableStateFlow(Principal())
    val principalFlow: StateFlow<Principal> = _principal.asStateFlow()
    val principal: Principal get() = _principal.value

    /** 强制触发 principalFlow 重新发射（属性原地修改后调用） */
    fun notifyPrincipalChanged() {
        val current = _principal.value
        // 不能先 current.version++，否则 copy() 出的对象 version 与已被修改的 current 相同
        // StateFlow 用 equals 判断是否需要发射，相同就不发射
        _principal.value = current.copy(version = current.version + 1)
    }

    /**
     * 购买个人物品。Principal 只在 School 事务提交成功后替换，避免强退时扣款或物品单边落盘。
     */
    suspend fun purchasePersonalItem(
        itemName: String,
        cost: Double
    ): Boolean = engineOperationMutex.withLock {
        if (
            "principalJson" in managerRestoreFailedFields ||
            itemName.isBlank() ||
            !cost.isFinite() ||
            cost <= 0.0
        ) {
            return@withLock false
        }

        val principalCopy = copyPrincipalForPersistence(_principal.value)
        if (
            principalCopy.personalFunds < cost ||
            itemName in principalCopy.purchasedLuxuryItems
        ) {
            return@withLock false
        }

        val connectionGain = when {
            itemName.contains("送礼") || itemName.contains("年份酒") ||
                itemName.contains("会所") || itemName.contains("高尔夫") ||
                itemName.contains("字画") -> (cost / 5.0).toInt().coerceIn(3, 20)
            itemName.contains("香奈") -> 15
            itemName.contains("车") || itemName.contains("大G") ||
                itemName.contains("大牛") || itemName.contains("法拉") ||
                itemName.contains("劳斯") || itemName.contains("迈巴") ||
                itemName.contains("宾利") || itemName.contains("布加迪") ||
                itemName.contains("保时") -> (cost / 20.0).toInt().coerceIn(2, 10)
            itemName.contains("宅") || itemName.contains("平层") ||
                itemName.contains("复式") || itemName.contains("庄园") ||
                itemName.contains("古堡") || itemName.contains("别野") ->
                (cost / 40.0).toInt().coerceIn(1, 8)
            else -> (cost / 30.0).toInt().coerceIn(1, 5)
        }

        principalCopy.personalFunds -= cost
        principalCopy.connectionBonus =
            (principalCopy.connectionBonus + connectionGain).coerceAtMost(80)
        principalCopy.connectionLevel =
            (principalCopy.connectionLevel + connectionGain).coerceAtMost(100)
        principalCopy.purchasedLuxuryItems.add(itemName)
        principalCopy.version++

        schoolRepository.mutateSchool { school ->
            school.principalJson = encodePrincipal(principalCopy)
            true
        } ?: return@withLock false

        _principal.value = principalCopy
        true
    }

    /**
     * 将个人资金捐给学校。个人资金、学校现金、声望和 principalJson 同行提交。
     */
    suspend fun donatePersonalFundsToSchool(amount: Double): Boolean =
        engineOperationMutex.withLock {
            if (
                "principalJson" in managerRestoreFailedFields ||
                !amount.isFinite() ||
                amount <= 0.0
            ) {
                return@withLock false
            }

            val principalCopy = copyPrincipalForPersistence(_principal.value)
            if (principalCopy.personalFunds < amount) {
                return@withLock false
            }

            principalCopy.personalFunds -= amount
            principalCopy.version++
            val reputationGain = (amount / 10).toLong().coerceAtLeast(1)
            schoolRepository.mutateSchool { school ->
                school.cash += amount
                school.reputation += reputationGain
                school.principalJson = encodePrincipal(principalCopy)
                true
            } ?: return@withLock false

            _principal.value = principalCopy
            true
        }

    private fun copyPrincipalForPersistence(source: Principal): Principal = source.copy(
        recentCorruptActs = source.recentCorruptActs.map { it.copy() }.toMutableList(),
        connections = source.connections.map { it.copy() }.toMutableList(),
        purchasedLuxuryItems = source.purchasedLuxuryItems.toMutableList(),
        factionRelations = source.factionRelations.toMutableMap()
    )

    private fun encodePrincipal(principal: Principal): String =
        kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer<Principal>(),
            principal
        )

    suspend fun executeStockPurchase(
        stockId: String,
        shares: Int,
        maxInvestment: Double
    ): StockTradeResult = engineOperationMutex.withLock {
        if ("principalJson" in managerRestoreFailedFields) {
            return@withLock StockTradeResult(
                false,
                "校长数据恢复失败，为保护原存档，本次交易已阻止。"
            )
        }
        val principalSnapshot = _principal.value.copy(
            recentCorruptActs = _principal.value.recentCorruptActs.map { it.copy() }.toMutableList(),
            connections = _principal.value.connections.map { it.copy() }.toMutableList(),
            purchasedLuxuryItems = _principal.value.purchasedLuxuryItems.toMutableList(),
            factionRelations = _principal.value.factionRelations.toMutableMap()
        )
        val result = stockRepository.buyStock(
            stockId,
            shares,
            principalSnapshot,
            maxInvestment
        )
        if (result.success && result.principal != null) {
            _principal.value = result.principal
        }
        result
    }

    suspend fun executeStockSale(
        stockId: String,
        shares: Int
    ): StockTradeResult = engineOperationMutex.withLock {
        if ("principalJson" in managerRestoreFailedFields) {
            return@withLock StockTradeResult(
                false,
                "校长数据恢复失败，为保护原存档，本次交易已阻止。"
            )
        }
        val principalSnapshot = _principal.value.copy(
            recentCorruptActs = _principal.value.recentCorruptActs.map { it.copy() }.toMutableList(),
            connections = _principal.value.connections.map { it.copy() }.toMutableList(),
            purchasedLuxuryItems = _principal.value.purchasedLuxuryItems.toMutableList(),
            factionRelations = _principal.value.factionRelations.toMutableMap()
        )
        val result = stockRepository.sellStock(
            stockId,
            shares,
            principalSnapshot
        )
        if (result.success && result.principal != null) {
            _principal.value = result.principal
        }
        result
    }

    data class CorruptActionOutcome(
        val result: CorruptActResult,
        val investigationEvent: InvestigationEvent? = null
    )

    /**
     * 原子执行一次腐败操作。
     * Principal 使用副本计算，School 在 Repository 锁内读取最新值并同行写入 principalJson。
     */
    suspend fun executeCorruptAction(
        option: CorruptionOption
    ): CorruptActionOutcome = engineOperationMutex.withLock {
        if ("principalJson" in managerRestoreFailedFields) {
            return@withLock CorruptActionOutcome(
                CorruptActResult(
                    false,
                    0.0,
                    false,
                    "校长数据恢复失败，为保护原存档，本次操作已阻止。"
                )
            )
        }
        val principalCopy = _principal.value.copy(
            recentCorruptActs = _principal.value.recentCorruptActs.map {
                it.copy()
            }.toMutableList(),
            connections = _principal.value.connections.map {
                it.copy()
            }.toMutableList(),
            purchasedLuxuryItems = _principal.value.purchasedLuxuryItems.toMutableList(),
            factionRelations = _principal.value.factionRelations.toMutableMap()
        )

        var outcome: CorruptActionOutcome? = null
        val persistedSchool = schoolRepository.mutateSchool { school ->
            val result = corruptionManager.executeCorruptAct(
                principal = principalCopy,
                school = school,
                type = option.type,
                amount = option.amount,
                description = option.description,
                witnessCount = option.witnessCount
            )

            if (!result.success && !result.immediatelyExposed) {
                outcome = CorruptActionOutcome(result)
                return@mutateSchool false
            }

            if (option.connectionGain > 0) {
                principalCopy.connectionBonus = (
                    principalCopy.connectionBonus + option.connectionGain
                ).coerceAtMost(80)
                principalCopy.connectionLevel = (
                    principalCopy.connectionLevel + option.connectionGain
                ).coerceAtMost(100)
            }
            if (option.reputationGain > 0) {
                school.reputation += option.reputationGain
            }

            var investigation: InvestigationEvent? = null
            if (result.immediatelyExposed) {
                investigation = createImmediateInvestigation(
                    principalCopy,
                    option,
                    result
                )
                val schoolFine = corruptionManager.applyInvestigationPenalty(
                    principalCopy,
                    school,
                    investigation
                )
                if (schoolFine > 0.0) {
                    school.cash = (school.cash - schoolFine).coerceAtLeast(-100.0)
                }
                if (investigation.result == InvestigationResult.ARRESTED) {
                    school.cash = 0.0
                }
            }

            principalCopy.version++
            school.principalJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Principal>(),
                principalCopy
            )
            outcome = CorruptActionOutcome(result, investigation)
            true
        }

        val committed = outcome
            ?: CorruptActionOutcome(
                CorruptActResult(false, 0.0, false, "学校存档不可用，操作未执行。")
            )
        if (persistedSchool != null) {
            _principal.value = principalCopy
            val investigation = committed.investigationEvent
            if (investigation?.result == InvestigationResult.ARRESTED) {
                val graduateCount = studentRepository.getGraduateCount()
                gameOverDetector.confirmGameOver(
                    GameOverReason(
                        conditions = listOf(FailureCondition.PRINCIPAL_ARRESTED),
                        finalCash = persistedSchool.cash,
                        finalReputation = persistedSchool.reputation,
                        totalYearsPlayed = persistedSchool.currentYear - persistedSchool.foundedYear,
                        totalStudentsGraduated = graduateCount,
                        peakReputation = persistedSchool.reputation,
                        peakCash = persistedSchool.totalRevenue
                    )
                )
                isPaused = true
            } else if (
                investigation?.result == InvestigationResult.SUSPENSION ||
                investigation?.result == InvestigationResult.DEMOTION
            ) {
                requireDisciplinaryRecovery(
                    title = if (investigation.result == InvestigationResult.DEMOTION) {
                        "校长被免职降级"
                    } else {
                        "校长被停职调查"
                    },
                    message = investigation.message +
                        "\n\n观看完整视频并接受纪律教育后，才可恢复学校经营。"
                )
            }
        }
        committed
    }

    private data class MonthlyInvestigationOutcome(
        val event: InvestigationEvent,
        val schoolFine: Double,
        val school: School
    )

    /** 月度腐败调查的 School/Principal 变更必须同行持久化。 */
    private suspend fun processMonthlyCorruption(
        principal: Principal
    ): MonthlyInvestigationOutcome? {
        if ("principalJson" in managerRestoreFailedFields) {
            android.util.Log.e(
                "GameEngine",
                "Skip monthly corruption because principalJson restore failed"
            )
            return null
        }
        var event: InvestigationEvent? = null
        var schoolFine = 0.0
        val persistedSchool = schoolRepository.mutateSchool { latestSchool ->
            val investigation = corruptionManager.monthlyRiskCheck(
                principal,
                latestSchool
            ) ?: return@mutateSchool false

            schoolFine = corruptionManager.applyInvestigationPenalty(
                principal,
                latestSchool,
                investigation
            )
            if (schoolFine > 0.0) {
                latestSchool.cash = (
                    latestSchool.cash - schoolFine
                ).coerceAtLeast(-100.0)
            }
            if (investigation.result == InvestigationResult.ARRESTED) {
                latestSchool.cash = 0.0
            }
            latestSchool.principalJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Principal>(),
                principal
            )
            event = investigation
            true
        } ?: return null

        return MonthlyInvestigationOutcome(
            event = event ?: return null,
            schoolFine = schoolFine,
            school = persistedSchool
        )
    }

    private fun createImmediateInvestigation(
        principal: Principal,
        option: CorruptionOption,
        result: CorruptActResult
    ): InvestigationEvent = when {
        principal.totalEmbezzled >= 300.0 || principal.timesCaughtMajor >= 2 -> {
            principal.isArrested = true
            InvestigationEvent(
                result = InvestigationResult.ARRESTED,
                fineAmount = principal.personalFunds + kotlin.math.abs(option.amount) * 3,
                reputationLoss = 10000L,
                suspensionDays = 365,
                message = "${result.exposureMessage}\n当场人赃俱获！纪检监察立即介入，校长被带走调查！" +
                    "累计贪污 ${String.format("%.1f", principal.totalEmbezzled)} 万元，证据确凿！",
                discoveredActs = emptyList()
            )
        }
        principal.corruptionLevel >= 80 || principal.timesCaughtMajor >= 1 -> {
            InvestigationEvent(
                result = InvestigationResult.SUSPENSION,
                fineAmount = kotlin.math.abs(option.amount) * 2 + principal.totalEmbezzled * 0.1,
                reputationLoss = 3000L,
                suspensionDays = 60,
                message = "${result.exposureMessage}\n情节严重！纪检部门决定停职调查两个月！",
                discoveredActs = emptyList()
            )
        }
        principal.corruptionLevel >= 50 || principal.timesCaughtMinor >= 2 -> {
            InvestigationEvent(
                result = InvestigationResult.FINE,
                fineAmount = kotlin.math.abs(option.amount) * 1.5 + 10.0,
                reputationLoss = 1500L,
                suspensionDays = 0,
                message = "${result.exposureMessage}\n上级部门决定从重处罚，处以高额罚款！",
                discoveredActs = emptyList()
            )
        }
        else -> InvestigationEvent(
            result = InvestigationResult.WARNING,
            fineAmount = kotlin.math.abs(option.amount) * 1.2,
            reputationLoss = 800L,
            suspensionDays = 0,
            message = "${result.exposureMessage}\n教育局约谈警告，责令退还款项。",
            discoveredActs = emptyList()
        )
    }

    // === 班级系统 ===
    private val _classes = MutableStateFlow<List<SchoolClass>>(emptyList())
    val classesFlow: StateFlow<List<SchoolClass>> = _classes.asStateFlow()
    val classes: List<SchoolClass> get() = _classes.value

    /**
     * 强制通知 classesFlow 的观察者更新。
     * 当班级的属性（如 headTeacherId）被原地修改时，StateFlow 不会自动 emit，
     * 需要手动创建一份新 list 来触发。
     */
    fun notifyClassesChanged() {
        _classes.value = _classes.value.toList()
    }

    /**
     * 将当前班主任映射持久化到 School 的 headTeacherMapJson 字段。
     * 在分配/移除班主任后调用。
     */
    suspend fun saveHeadTeacherMap() {
        engineOperationMutex.withLock {
            saveHeadTeacherMapLocked()
        }
    }

    private suspend fun saveHeadTeacherMapLocked() {
        val map = _classes.value
            .filter { it.headTeacherId != null }
            .associate { it.id to it.headTeacherId!! }
        val headTeacherJson = org.json.JSONObject(map).toString()
        val tierMap = _classes.value.associate { it.id to it.classTier.name }
        val classTierJson = if (tierMap.isNotEmpty()) {
            org.json.JSONObject(tierMap).toString()
        } else {
            ""
        }
        schoolRepository.mutateSchool { latest ->
            writeClassJsonFields(
                latest,
                headTeacherJson,
                classTierJson
            )
        }
    }

    /**
     * 确保所有班级都有课表（懒生成兜底）。
     * 当 ViewModel 发现课表为空时调用，避免错过学期初生成时机。
     */
    suspend fun ensureTimetablesGenerated() {
        val allClasses = _classes.value
        if (allClasses.isEmpty()) return
        val allTeachers = teacherRepository.getTeachers()
        if (allTeachers.isEmpty()) return
        timetableManager.configuredPEHours = teachingManager.config.weeklyPEHours
        for (cls in allClasses) {
            // getTimetable 内部有 getOrPut 逻辑，不存在则生成
            timetableManager.getTimetable(cls, allTeachers)
        }
    }

    /**
     * 教师变动时（招聘/解雇）强制重新生成所有班级课表。
     * 确保新教师的名字出现在课表中，或解雇后变为"待聘"。
     */
    suspend fun refreshTimetablesForTeacherChange() {
        val allClasses = _classes.value
        if (allClasses.isEmpty()) return
        val allTeachers = teacherRepository.getTeachers()
        timetableManager.configuredPEHours = teachingManager.config.weeklyPEHours
        // 全校统一排课，避免同一教师在同一时段跨班冲突
        timetableManager.regenerateAllTimetables(allClasses, allTeachers)
    }

    /** 姓名（任教科目）统一展示 */
    private fun formatTeacherWithSubject(teacher: Teacher?): String {
        if (teacher == null) return "未知教师"
        return "${teacher.name}（${teacher.role.displayName}）"
    }

    private fun formatTeacherWithSubject(name: String, teacher: Teacher?): String {
        return if (teacher != null) formatTeacherWithSubject(teacher) else "$name（未知科目）"
    }

    /** 本月已发出、等待玩家选择的派系事件缓存（按 eventId） */
    private val pendingFactionEvents = mutableMapOf<String, FactionEvent>()

    /**
     * 应用派系事件选项（幂等，同一 eventId 只结算一次）。
     */
    fun applyFactionEventChoice(action: com.arktools.xiaozhang.domain.model.FactionChoiceAction): Boolean {
        val event = pendingFactionEvents[action.eventId] ?: return false
        val principal = _principal.value
        val applied = factionManager.applyFactionEventChoice(principal, event, action.choiceIndex)
        if (applied) {
            pendingFactionEvents.remove(action.eventId)
            _principal.value = principal.copy(version = principal.version + 1)
        }
        return applied
    }

    /**
     * 通知派系系统：玩家做出了某项决策
     * 各 ViewModel 在执行关键操作后调用此方法
     */
    fun notifyFactionDecision(decision: SchoolDecision) {
        factionManager.onSchoolDecision(_principal.value, decision)
    }

    /**
     * 将游戏事件同步到通知中心
     */
    private fun dispatchNotification(event: GameEvent, school: School?) {
        val year = school?.currentYear ?: 0
        val month = school?.currentMonth ?: 0
        val day = school?.currentDay ?: 0

        when (event) {
            is GameEvent.PositiveEvent -> {
                if (event.bonusCash > 50 || event.bonusReputation > 3 || event.title.contains("毕业") || event.title.contains("灵感")) {
                    notificationManager.addNotification(
                        title = event.title,
                        message = event.message,
                        type = when {
                            event.title.contains("毕业") -> com.arktools.xiaozhang.domain.model.NotificationType.STUDENT
                            event.title.contains("灵感") -> com.arktools.xiaozhang.domain.model.NotificationType.TEACHER
                            event.title.contains("里程碑") -> com.arktools.xiaozhang.domain.model.NotificationType.MILESTONE
                            else -> com.arktools.xiaozhang.domain.model.NotificationType.FINANCIAL
                        },
                        priority = com.arktools.xiaozhang.domain.model.NotificationPriority.NORMAL,
                        gameYear = year, gameMonth = month, gameDay = day
                    )
                }
            }
            is GameEvent.NegativeEvent -> {
                val type = when {
                    event.title.contains("离职") || event.title.contains("疲劳") || event.title.contains("辞职") || event.title.contains("被挖角") || event.title.contains("合同到期") -> com.arktools.xiaozhang.domain.model.NotificationType.TEACHER
                    event.title.contains("退学") -> com.arktools.xiaozhang.domain.model.NotificationType.STUDENT
                    event.title.contains("危机") || event.title.contains("警告") -> com.arktools.xiaozhang.domain.model.NotificationType.CRISIS
                    event.title.contains("下降") || event.title.contains("下跌") -> com.arktools.xiaozhang.domain.model.NotificationType.MARKET
                    else -> com.arktools.xiaozhang.domain.model.NotificationType.FINANCIAL
                }
                val priority = when {
                    event.title.contains("危机") -> com.arktools.xiaozhang.domain.model.NotificationPriority.URGENT
                    event.title.contains("离职") || event.title.contains("辞职") || event.title.contains("警告") -> com.arktools.xiaozhang.domain.model.NotificationPriority.HIGH
                    else -> com.arktools.xiaozhang.domain.model.NotificationPriority.NORMAL
                }
                notificationManager.addNotification(
                    title = event.title,
                    message = event.message,
                    type = type,
                    priority = priority,
                    gameYear = year, gameMonth = month, gameDay = day
                )
            }
            is GameEvent.MilestoneEvent -> {
                notificationManager.addNotification(
                    title = event.title,
                    message = event.message,
                    type = com.arktools.xiaozhang.domain.model.NotificationType.MILESTONE,
                    priority = com.arktools.xiaozhang.domain.model.NotificationPriority.HIGH,
                    gameYear = year, gameMonth = month, gameDay = day
                )
            }
            is GameEvent.ChoiceEvent -> { /* 选择事件通过对话框处理，不进通知 */ }
        }
    }

    /**
     * 统一事件发射：emit + 通知分发
     */
    private fun mapActivityToDimension(
        activityType: com.arktools.xiaozhang.domain.seasonal.ActivityType
    ): com.arktools.xiaozhang.domain.reputation.ReputationDimension {
        return when (activityType) {
            com.arktools.xiaozhang.domain.seasonal.ActivityType.SCIENCE_FAIR,
            com.arktools.xiaozhang.domain.seasonal.ActivityType.DEBATE_TOURNAMENT ->
                com.arktools.xiaozhang.domain.reputation.ReputationDimension.ACADEMIC
            com.arktools.xiaozhang.domain.seasonal.ActivityType.SPORTS_DAY,
            com.arktools.xiaozhang.domain.seasonal.ActivityType.SUMMER_CAMP ->
                com.arktools.xiaozhang.domain.reputation.ReputationDimension.SPORTS
            com.arktools.xiaozhang.domain.seasonal.ActivityType.ART_EXHIBITION,
            com.arktools.xiaozhang.domain.seasonal.ActivityType.CULTURAL_FESTIVAL,
            com.arktools.xiaozhang.domain.seasonal.ActivityType.NEW_YEAR_GALA ->
                com.arktools.xiaozhang.domain.reputation.ReputationDimension.ARTS
            com.arktools.xiaozhang.domain.seasonal.ActivityType.CHARITY_EVENT,
            com.arktools.xiaozhang.domain.seasonal.ActivityType.PARENT_DAY ->
                com.arktools.xiaozhang.domain.reputation.ReputationDimension.SOCIAL_SERVICE
            com.arktools.xiaozhang.domain.seasonal.ActivityType.OPENING_CEREMONY,
            com.arktools.xiaozhang.domain.seasonal.ActivityType.GRADUATION_CEREMONY,
            com.arktools.xiaozhang.domain.seasonal.ActivityType.SPRING_OUTING ->
                com.arktools.xiaozhang.domain.reputation.ReputationDimension.MANAGEMENT
        }
    }

    private suspend fun emitEvent(event: GameEvent, school: School? = null) {
        if (eventsSuppressed) return  // 教程期间不发送任何事件
        _events.emit(event)
        dispatchNotification(event, school)
    }

    /**
     * 将事件放入延迟队列，后续每天释放一个，避免月初弹窗堆积
     * 队列容量限制为20，超出后丢弃最旧事件防止堆积
     */
    private fun deferEvent(event: GameEvent) {
        if (eventsSuppressed) return
        _deferredEvents.offer(event)
        // 防止无限增长，后期事件过多时丢弃旧事件
        while (_deferredEvents.size > 20) {
            _deferredEvents.poll()
        }
    }

    /**
     * 每日 tick 中释放一个延迟事件
     */
    private suspend fun drainOneDeferredEvent(school: School?) {
        if (eventsSuppressed) return
        val event = _deferredEvents.poll() ?: return
        emitEvent(event, school)
    }

    /**
     * 重新触发季节活动审批弹窗（从 SeasonalScreen 的"审批"按钮调用）
     * 解决 ChoiceEvent 只在活动生成时发一次、玩家错过后无法再操作的问题
     */
    suspend fun retriggerActivityApproval(activityId: String) {
        val activity = seasonalActivityManager.state.value.activities
            .find { it.id == activityId } ?: return
        if (activity.phase != com.arktools.xiaozhang.domain.seasonal.ActivityPhase.PENDING_APPROVAL) return

        val costWan = activity.type.baseCost / 10000.0
        _events.emit(GameEvent.ChoiceEvent(
            title = "活动审批：${activity.type.displayName}",
            message = "${activity.type.description}\n\n" +
                "预计费用：${String.format("%.1f", costWan)}万元（标准规模）\n" +
                "预计声誉收益：+${activity.type.baseReputationGain}\n" +
                "筹备期：${activity.type.preparationDays}天 | 持续：${activity.type.durationDays}天\n\n" +
                "请选择活动规模并签字批准，或驳回此申请。\n" +
                "（未在15天内处理将自动过期）",
            choices = listOf(
                EventChoice("简朴举办（费用×0.5，收益×0.5）", EventConsequence(
                    activityAction = ActivityAction.Approve(activity.id, "MINIMAL"),
                    requiresSignature = true
                )),
                EventChoice("标准举办（费用×1.0，收益×1.0）", EventConsequence(
                    activityAction = ActivityAction.Approve(activity.id, "STANDARD"),
                    requiresSignature = true
                )),
                EventChoice("隆重举办（费用×1.8，收益×1.5）", EventConsequence(
                    activityAction = ActivityAction.Approve(activity.id, "GRAND"),
                    requiresSignature = true
                )),
                EventChoice("盛大举办（费用×3.0，收益×2.2）", EventConsequence(
                    activityAction = ActivityAction.Approve(activity.id, "SPECTACULAR"),
                    requiresSignature = true
                )),
                EventChoice("驳回申请", EventConsequence(
                    activityAction = ActivityAction.Reject(activity.id)
                ))
            )
        ))
    }

    fun start(startPaused: Boolean = false) {
        if (gameLoopJob?.isActive == true) return
        engineStopping = false
        isPaused = startPaused
        // 确保事件系统不会被教程异常退出永久静音
        eventsSuppressed = false
        gameLoopJob = engineScope.launch {
            managerRestoreFailedFields.clear()
            // 同步 schoolId：以 DB 中实际学校为准，纠正读档/多存档切换后 DataStore 里残留的旧 ID
            // 解决：读档后教师/学生/毕业生数量全为 0（schoolId 过滤查询用了错误的旧 ID）
            try {
                val dbSchool = schoolRepository.getSchool()
                if (dbSchool != null && settingsDataStore.schoolId.first() != dbSchool.id) {
                    settingsDataStore.setSchoolId(dbSchool.id)
                    android.util.Log.i("GameEngine", "Synced schoolId to DB school: ${dbSchool.id}")
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "syncSchoolId failed (non-fatal)", e)
            }
            // 从持久化的学生数据重建班级列表（解决加载存档后班级丢失的问题）
            try {
                rebuildClassesFromStudents()
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "rebuildClassesFromStudents failed, classes reset", e)
                _classes.value = emptyList()
            }
            // 兼容旧存档：如果教研数据为空，自动初始化默认教研方法
            try {
                val schoolId = settingsDataStore.schoolId.first()
                if (schoolId != null && researchRepository.getMethods().isEmpty()) {
                    researchRepository.initializeDefaultMethods(schoolId)
                    android.util.Log.i("GameEngine", "Initialized default research methods for existing save")
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "initializeDefaultMethods failed (non-fatal)", e)
            }
            // 从存档恢复所有Manager状态；恢复完成前禁止任何自动写回覆盖存档。
            managerStatesReadyForSave = false
            try {
                restoreAllManagerStates()
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "restoreAllManagerStates failed, some states may be reset", e)
            }
            // 防御性修复：如果 classDistribution 为空但实际存在班级，从班级数据自动重建配置
            // 解决读档后教学配置丢失、月收入为0、设施显示0的问题
            try {
                rebuildClassDistributionIfEmpty()
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "rebuildClassDistributionIfEmpty failed (non-fatal)", e)
            }
            // 修复设施占用显示为0：FacilityPersist 不保存 currentLoad，读档后需重新同步
            try {
                val activeCount = studentRepository.getActiveStudentCount()
                if (activeCount > 0) {
                    studentLifeManager.updateStudentCount(activeCount)
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "updateStudentCount after restore failed (non-fatal)", e)
            }
            // 先恢复遗漏的学生年结。该补偿只处理年级、毕业和班级，
            // 不能通过 pendingMonthlyProcessing 重放其他非幂等月结 Manager。
            try {
                val restoredSchool = schoolRepository.getSchool()
                if (restoredSchool != null) {
                    engineOperationMutex.withLock {
                        processStudentYearEnd(
                            school = restoredSchool,
                            currentClasses = _classes.value
                                .map { it.copy() }
                                .toMutableList(),
                            currentStudents = studentRepository
                                .getCurrentStudents(),
                            emitNotifications = false
                        )
                    }
                }
                pendingStudentYearEndRecovery = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                pendingStudentYearEndRecovery =
                    "classTierMapJson" !in managerRestoreFailedFields
                android.util.Log.e(
                    "GameEngine",
                    if (pendingStudentYearEndRecovery) {
                        "Student year-end recovery failed; will retry"
                    } else {
                        "Student year-end recovery blocked by class tier restore failure"
                    },
                    e
                )
            }
            // 先补齐已落库但尚未完成的毕业投影，再做仅面向旧档的就业兼容补录。
            // 投影失败时保留 state=0，并在后续 tick 中重试，不允许丢失奖励或衍生状态。
            try {
                val restoredSchool = schoolRepository.getSchool()
                if (restoredSchool != null) {
                    engineOperationMutex.withLock {
                        processPendingGraduationProjections(
                            restoredSchool,
                            emitNotifications = false
                        )
                    }
                }
            } catch (e: Exception) {
                val failedRequiredFields = graduationProjectionManagerFields
                    .intersect(managerRestoreFailedFields)
                pendingGraduationProjectionRetry = failedRequiredFields.isEmpty()
                android.util.Log.e(
                    "GameEngine",
                    if (pendingGraduationProjectionRetry) {
                        "Pending graduation projection recovery failed; will retry"
                    } else {
                        "Pending graduation projection blocked by manager restore failure"
                    },
                    e
                )
            }
            // 修复升学就业数据全为0：补录 schema 24 之前已标记完成的历史毕业生。
            try {
                retroactivelyRegisterGraduates()
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "retroactivelyRegisterGraduates failed (non-fatal)", e)
            }
            // 一次性纪检清算：没收旧版bug导致的非法贪污所得
            performAntiCorruptionCheckIfNeeded()
            // 恢复持久化的停职/逮捕状态，避免提交后强杀绕过纪律处分。
            restorePrincipalDisciplineState()
            // 新游戏开局欢迎事件（第1天触发）
            emitWelcomeEventIfNeeded()
            while (isActive) {
                if (!isPaused && !isSaving && !engineStopping) {
                    try {
                        engineOperationMutex.withLock {
                            if (!engineStopping && !isSaving) {
                                tick()
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        android.util.Log.e("GameEngine", "tick() exception, game loop continues", e)
                        // 月结恢复由数据库中的完成年月驱动；任意日常异常不得触发整段月结重放。
                    }
                }
                val speed = settingsDataStore.gameSpeed.first().coerceAtLeast(0.25f)
                delay((baseTickIntervalMs / speed).toLong())
            }
        }
    }

    /**
     * 从数据库中的学生数据重建班级列表。
     * 解决：班级信息仅存在内存中，读档后 _classes 为空的问题。
     * 逻辑：按学生的 classId 分组，为每组创建 SchoolClass 对象并聚合指标。
     * 同时处理 classId 为 null 的旧存档学生（自动分班）。
     */
    private suspend fun rebuildClassesFromStudents() {
        // 如果已有班级数据（新游戏刚招生），无需重建
        if (_classes.value.isNotEmpty()) return

        val students = studentRepository.getCurrentStudents()
        if (students.isEmpty()) return

        val school = schoolRepository.getSchool() ?: return
        val distribution = teachingManager.config.classDistribution

        // 按 classId 分组
        val assignedStudents = students.filter { it.classId != null }
        val unassignedStudents = students.filter { it.classId == null }

        val rebuiltClasses = mutableListOf<SchoolClass>()

        // 解析持久化的班级类型映射（优先使用，解决火箭班读档变普通班的bug）
        val savedTierMap = mutableMapOf<String, ClassTier>()
        try {
            if (school.classTierMapJson.isNotBlank()) {
                val jsonObj = org.json.JSONObject(school.classTierMapJson)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val tierName = jsonObj.getString(key)
                    savedTierMap[key] = try { ClassTier.valueOf(tierName) } catch (_: Exception) { ClassTier.NORMAL }
                }
                android.util.Log.i("GameEngine", "Loaded classTierMap: ${savedTierMap.size} entries")
            }
        } catch (e: Exception) {
            managerRestoreFailedFields.add("classTierMapJson")
            android.util.Log.e(
                "GameEngine",
                "Failed to parse classTierMapJson; original JSON will be preserved",
                e
            )
        }

        if (assignedStudents.isNotEmpty()) {
            val groupedByClass = assignedStudents.groupBy { it.classId!! }

            // 按年级分组以正确生成班号
            val classesByGrade = groupedByClass.entries.groupBy { entry ->
                entry.value.first().gradeLevel
            }

            classesByGrade.forEach { (gradeLevel, classEntries) ->
                classEntries.forEachIndexed { index, (classId, classStudents) ->
                    // 优先从持久化映射恢复班型，无记录时才按索引推断
                    val classTier = savedTierMap[classId] ?: inferClassTierByIndex(index, distribution)
                    val schoolClass = SchoolClass(
                        id = classId,
                        schoolId = school.id,
                        gradeLevel = gradeLevel,
                        classNumber = index + 1,
                        classTier = classTier,
                        studentCount = classStudents.size,
                        avgIntelligence = classStudents.map { it.attributes.intelligence }.average().toFloat(),
                        avgPhysical = classStudents.map { it.attributes.physical }.average().toFloat(),
                        avgSocial = classStudents.map { it.attributes.social }.average().toFloat(),
                        avgCreativity = classStudents.map { it.attributes.creativity }.average().toFloat(),
                        avgMorality = classStudents.map { it.attributes.morality }.average().toFloat(),
                        avgAcademicScore = classStudents.map { it.academicScore }.average().toFloat(),
                        avgSatisfaction = classStudents.map { it.satisfaction }.average().toFloat(),
                        createdYear = classStudents.minOf { it.enrollYear },
                        createdMonth = classStudents.minOf { it.enrollMonth }
                    )
                    rebuiltClasses.add(schoolClass)
                }
            }
        }

        // 处理没有 classId 的旧存档学生：按年级自动分配到班级
        if (unassignedStudents.isNotEmpty()) {
            val byGrade = unassignedStudents.groupBy { it.gradeLevel }
            byGrade.forEach { (gradeLevel, gradeStudents) ->
                // 获取该年级现有班级
                val gradeClasses = rebuiltClasses.filter { it.gradeLevel == gradeLevel }
                val maxClassNum = gradeClasses.maxOfOrNull { it.classNumber } ?: 0

                // 按班型配置计算每年级需要的班数
                val gradeDistribution = distribution.mapValues { (_, count) ->
                    (count / 3).coerceAtLeast(if (count > 0) 1 else 0)
                }.filter { it.value > 0 }
                val perGradeCapacity = gradeDistribution.entries.sumOf { (tier, count) -> tier.maxSize * count }

                // 计算需要多少个新班级
                val availableCapacity = gradeClasses.sumOf { it.remainingCapacity }
                val defaultTierSize = ClassTier.NORMAL.maxSize
                val newClassesNeeded = if (availableCapacity >= gradeStudents.size) 0
                    else ((gradeStudents.size - availableCapacity) / defaultTierSize) + 1

                // 创建新班级（如果需要），使用 NORMAL 班型作为兜底
                repeat(newClassesNeeded) { i ->
                    rebuiltClasses.add(SchoolClass(
                        schoolId = school.id,
                        gradeLevel = gradeLevel,
                        classNumber = maxClassNum + i + 1,
                        classTier = ClassTier.NORMAL,
                        createdYear = school.currentYear,
                        createdMonth = school.currentMonth
                    ))
                }

                // 均衡分配到该年级所有班级
                val targetClasses = rebuiltClasses.filter { it.gradeLevel == gradeLevel }
                    .sortedBy { it.studentCount }
                if (targetClasses.isEmpty()) return@forEach  // 防御：无班级则跳过该年级
                var classIndex = 0
                gradeStudents.forEach { student ->
                    val targetClass = targetClasses[classIndex % targetClasses.size]
                    targetClass.studentCount++
                    studentRepository.assignStudentToClass(student.id, targetClass.id, gradeLevel)
                    classIndex++
                }
            }
        }

        // 从 headTeacherMapJson 恢复班主任分配
        try {
            val headTeacherMapStr = school.headTeacherMapJson
            if (headTeacherMapStr.isNotBlank()) {
                val jsonObj = org.json.JSONObject(headTeacherMapStr)
                for (cls in rebuiltClasses) {
                    if (jsonObj.has(cls.id)) {
                        cls.headTeacherId = jsonObj.getString(cls.id)
                    }
                }
                android.util.Log.i("GameEngine", "Restored headTeacherMap for ${jsonObj.length()} classes")
            }
        } catch (e: Exception) {
            managerRestoreFailedFields.add("headTeacherMapJson")
            android.util.Log.e(
                "GameEngine",
                "Failed to restore headTeacherMap; original JSON will be preserved",
                e
            )
        }

        _classes.value = rebuiltClasses

        // 如果旧存档没有 classTierMap，重建后立即持久化当前班型（防止下次读档再次丢失）
        if (school.classTierMapJson.isBlank() && rebuiltClasses.isNotEmpty()) {
            try {
                val tierMap = rebuiltClasses.associate { it.id to it.classTier.name }
                val classTierJson = org.json.JSONObject(tierMap).toString()
                schoolRepository.mutateSchool { latest ->
                    if (latest.classTierMapJson.isNotBlank()) {
                        false
                    } else {
                        latest.classTierMapJson = classTierJson
                        true
                    }
                }
                android.util.Log.i("GameEngine", "Persisted classTierMap for ${tierMap.size} classes (first-time migration)")
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "Failed to persist classTierMap (non-fatal)", e)
            }
        }
    }

    /**
     * 补录历史毕业生到升学就业系统。
     * 解决：旧版本因升级bug导致毕业生从未被注册到 employmentMarket，升学就业页面全为0。
     */
    private suspend fun retroactivelyRegisterGraduates() {
        val school = schoolRepository.getSchool() ?: return
        val graduates = studentRepository.getGraduatedStudents()
        var registered = 0

        // 幂等补录：无论就业市场是否已有数据，都尝试注册缺失的历史毕业生
        graduates.forEach { student ->
            if (student.gaoKaoScore > 0) {
                val tier = student.universityTier ?: com.arktools.xiaozhang.domain.model.UniversityTier.NONE
                val added = employmentMarket.registerGraduate(
                    studentId = student.id,
                    name = student.name,
                    year = student.graduateYear ?: school.currentYear,
                    month = student.graduateMonth ?: 6,
                    gaoKaoScore = student.gaoKaoScore,
                    universityTier = tier,
                    universityName = student.admittedUniversity ?: "",
                    satisfaction = student.satisfaction
                )
                if (added) registered++
            }
        }

        val historicalSummaryYears = graduates
            .groupBy { it.graduateYear ?: school.currentYear }
            .filterKeys { year ->
                alumniNetwork.graduationSummaries.value.none { it.year == year }
            }
        historicalSummaryYears.forEach { (year, cohort) ->
            val stats = GaoKaoCalculator.calculateGraduationStats(cohort)
            alumniNetwork.recordGraduationBatch(
                year = year,
                totalStudents = stats.totalStudents,
                averageScore = stats.averageScore,
                highestScore = stats.highestScore,
                bengkeRate = stats.bengkeLv,
                key985Count = stats.key985Count,
                qingbeiCount = stats.qingbeiCount,
                topStudents = cohort.sortedByDescending { it.gaoKaoScore }
                    .take(5)
                    .map { student ->
                        com.arktools.xiaozhang.domain.alumni.GraduationTopStudent(
                            name = student.name,
                            score = student.gaoKaoScore,
                            university = student.admittedUniversity,
                            tierName = student.universityTier?.displayName ?: "未录取"
                        )
                    },
                universityDistribution = cohort.groupingBy {
                    it.universityTier?.displayName ?: "未录取"
                }.eachCount()
            )
            // 历史学生已在旧版本完成结算，只补总结展示，不能重复发放毕业奖励。
            alumniNetwork.completeGraduationSettlement(year, 0.0, 0L)
        }

        // 按毕业年月校准大学进度/已就业状态，修复“毕业多年仍大学在读 / 就业为0”
        val calibration = employmentMarket.calibrateHistoricalGraduates(
            currentYear = school.currentYear,
            currentMonth = school.currentMonth
        )

        if (
            (registered > 0 || calibration.changed || historicalSummaryYears.isNotEmpty()) &&
            "employmentJson" !in managerRestoreFailedFields &&
            "alumniJson" !in managerRestoreFailedFields
        ) {
            schoolRepository.mutateSchool { latest ->
                latest.employmentJson = protectedManagerJson(
                    "employmentJson",
                    latest.employmentJson,
                    employmentMarket::toJson
                )
                latest.alumniJson = protectedManagerJson(
                    "alumniJson",
                    latest.alumniJson,
                    alumniNetwork::toJson
                )
                true
            }
            android.util.Log.i(
                "GameEngine",
                "Employment market repaired: registered=$registered, " +
                    "corrected=${calibration.correctedProgressCount}, " +
                    "graduated=${calibration.graduatedCount}, " +
                    "deduped=${calibration.deduplicatedCount}"
            )
        }
    }

    /**
     * 防御性修复：如果 teachingManager.config.classDistribution 为空但实际存在班级，
     * 从内存中的 _classes 数据自动重建教学配置。
     * 解决：读档后班型配置丢失（显示全0）、月收入为0、设施0占用的级联问题。
     */
    private suspend fun rebuildClassDistributionIfEmpty() {
        val currentClasses = _classes.value
        if (currentClasses.isEmpty()) return  // 没有班级则无需重建
        if (teachingManager.config.classDistribution.isNotEmpty()) return  // 已有配置则跳过

        // 从实际班级统计各班型数量（全校总数，不分年级）
        val rebuiltDistribution = mutableMapOf<ClassTier, Int>()
        // 每个年级的班型分布可能不同，取全校总量（教学配置是全校总数/3年级）
        val classesByGrade = currentClasses.groupBy { it.gradeLevel }
        val gradeCount = classesByGrade.size.coerceAtLeast(1)

        // 统计每种班型在所有年级中的总班级数
        currentClasses.groupBy { it.classTier }.forEach { (tier, classes) ->
            // classDistribution 表示全校配置总数（招生时除以年级数分配）
            // 所以这里直接取每年级平均数再乘以年级数 = 总数
            val perGradeAvg = classes.size / gradeCount
            val totalConfigured = (perGradeAvg * gradeCount).coerceAtLeast(classes.size)
            rebuiltDistribution[tier] = totalConfigured
        }

        if (rebuiltDistribution.isNotEmpty()) {
            teachingManager.setClassDistribution(rebuiltDistribution)
            // 立即持久化到DB
            val school = schoolRepository.getSchool()
            if (school != null && "teachingConfigJson" !in managerRestoreFailedFields) {
                schoolRepository.mutateSchool { latest ->
                    latest.teachingConfigJson = protectedManagerJson(
                        "teachingConfigJson",
                        latest.teachingConfigJson,
                        teachingManager::toJson
                    )
                    true
                }
            }
            android.util.Log.i("GameEngine",
                "Rebuilt classDistribution from ${currentClasses.size} existing classes: $rebuiltDistribution")
        }
    }

    /**
     * 一次性纪检清算：对旧版本利用bug贪污的玩家执行反腐行动
     * - 检测条件：totalEmbezzled > 0 且尚未执行过清算
     * - 处罚：没收全部个人资金、清零贪污记录、重置腐败值
     * - 不结束游戏：给玩家一次改过自新的机会
     */
    private suspend fun performAntiCorruptionCheckIfNeeded() {
        if ("principalJson" in managerRestoreFailedFields) {
            android.util.Log.e(
                "GameEngine",
                "Skip anti-corruption migration because principalJson restore failed"
            )
            return
        }
        val p = _principal.value
        // 已执行过或无贪污记录 → 跳过
        if (p.antiCorruptionApplied || p.totalEmbezzled <= 0.0) return

        val confiscatedAmount = p.personalFunds
        val embezzledTotal = p.totalEmbezzled

        // 执行清算
        p.personalFunds = 0.0
        p.totalEmbezzled = 0.0
        p.corruptionLevel = 0
        p.recentCorruptActs.clear()
        p.timesInvestigated = 0
        p.timesCaughtMinor = 0
        p.timesCaughtMajor = 0
        p.isSuspended = false
        p.suspendedDaysLeft = 0
        p.antiCorruptionApplied = true
        _principal.value = p.copy(version = p.version + 1)

        // 持久化清算结果
        try {
            val principalJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Principal>(),
                _principal.value
            )
            schoolRepository.mutateSchool { latest ->
                latest.principalJson = principalJson
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("GameEngine", "Failed to persist anti-corruption result", e)
        }

        // 弹出纪检通报事件
        val message = buildString {
            append("【中共中央纪律检查委员会通报】\n\n")
            append("经群众举报及专项巡视组调查核实，")
            append("查明校长在任职期间存在严重违纪违法行为：\n\n")
            append("● 累计贪污公款：${String.format("%.1f", embezzledTotal)}万元\n")
            if (confiscatedAmount > 0) {
                append("● 违法所得已全部没收：${String.format("%.1f", confiscatedAmount)}万元\n")
            }
            append("\n鉴于当事人认罪态度良好，且未造成不可挽回的损失，")
            append("经研究决定：\n\n")
            append("一、没收全部违法所得\n")
            append("二、免予刑事处罚，保留职务\n")
            append("三、给予党内严重警告处分\n\n")
            append("望引以为戒，廉洁从教。")
        }

        _events.emit(GameEvent.NegativeEvent(
            title = "纪检监察通报",
            message = message,
            penaltyCash = 0.0,  // 不扣学校资金，只清个人资产
            penaltyReputation = 0L
        ))

        android.util.Log.i("GameEngine", "Anti-corruption check applied: confiscated=$confiscatedAmount, embezzled=$embezzledTotal")
    }

    private suspend fun restorePrincipalDisciplineState() {
        val principal = _principal.value
        val school = schoolRepository.getSchool() ?: return
        if (principal.isArrested) {
            val graduateCount = studentRepository.getGraduateCount()
            gameOverDetector.confirmGameOver(
                GameOverReason(
                    conditions = listOf(FailureCondition.PRINCIPAL_ARRESTED),
                    finalCash = school.cash,
                    finalReputation = school.reputation,
                    totalYearsPlayed = school.currentYear - school.foundedYear,
                    totalStudentsGraduated = graduateCount,
                    peakReputation = school.reputation,
                    peakCash = school.totalRevenue
                )
            )
            isPaused = true
        } else if (principal.isSuspended) {
            requireDisciplinaryRecovery(
                title = "校长仍在停职调查",
                message = "存档记录显示校长尚未完成纪律教育。\n\n" +
                    "观看完整视频并接受纪律教育后，才可恢复学校经营。"
            )
        }
    }

    /**
     * 新游戏开局欢迎事件（第1天触发）
     */
    private suspend fun emitWelcomeEventIfNeeded() {
        val school = schoolRepository.getSchool() ?: return
        // 仅在游戏第1天（刚创建学校）时触发欢迎事件
        val schoolAgeDays = (school.currentYear - school.foundedYear) * 360 +
                (school.currentMonth - 1) * 30 + school.currentDay
        if (schoolAgeDays <= 1) {
            emitEvent(GameEvent.PositiveEvent(
                title = "新学校成立",
                message = "恭喜！${school.name}正式成立。\n" +
                        "当务之急：招聘教师、招收学生。\n" +
                        "三年后将迎来第一届高考，加油！",
                bonusCash = 0.0,
                bonusReputation = 0L
            ), school)
        }
    }

    private suspend fun restoreAllManagerStates() {
        val school = schoolRepository.getSchool() ?: return

        restoreManagerField("studentLifeJson", school.studentLifeJson) {
            studentLifeManager.restoreFromJson(school.studentLifeJson)
        }
        restoreManagerField("reputationJson", school.reputationJson) {
            reputationManager.restoreFromJson(school.reputationJson)
        }
        restoreManagerField("achievementJson", school.achievementJson) {
            achievementManager.restoreFromJson(school.achievementJson)
        }
        restoreManagerField("milestoneJson", school.milestoneJson) {
            milestoneManager.restoreFromJson(school.milestoneJson)
        }
        restoreManagerField("teacherDevJson", school.teacherDevJson) {
            teacherDevelopmentManager.restoreFromJson(school.teacherDevJson)
        }
        restoreManagerField("clubJson", school.clubJson) {
            clubManager.restoreFromJson(school.clubJson)
        }
        clubManager.currentCampusLevel = school.campusLevel
        restoreManagerField("scholarshipJson", school.scholarshipJson) {
            scholarshipManager.restoreFromJson(school.scholarshipJson)
        }
        restoreManagerField("expansionJson", school.expansionJson) {
            campusExpansionManager.restoreFromJson(school.expansionJson)
        }
        restoreManagerField("governmentJson", school.governmentJson) {
            governmentInspectionManager.restoreFromJson(school.governmentJson)
        }
        restoreManagerField("parentJson", school.parentJson) {
            parentSatisfactionManager.restoreFromJson(school.parentJson)
        }
        restoreManagerField("policyJson", school.policyJson) {
            policyManager.restoreFromJson(school.policyJson)
        }
        restoreManagerField("seasonalJson", school.seasonalJson) {
            seasonalActivityManager.restoreFromJson(school.seasonalJson)
        }
        restoreManagerField("conferenceJson", school.conferenceJson) {
            academicConferenceManager.restoreFromJson(school.conferenceJson)
        }
        restoreManagerField("clubActivityJson", school.clubActivityJson) {
            clubActivityManager.restoreFromJson(school.clubActivityJson)
        }
        restoreManagerField("timetableJson", school.timetableJson) {
            timetableManager.fromJson(school.timetableJson)
        }
        restoreManagerField("examJson", school.examJson) {
            examManager.fromJson(school.examJson)
        }
        if (school.teachingConfigJson.isBlank()) {
            teachingManager.loadFromJson("")
        } else {
            restoreManagerField("teachingConfigJson", school.teachingConfigJson) {
                teachingManager.loadFromJson(school.teachingConfigJson)
            }
        }
        restoreManagerField("statisticsJson", school.statisticsJson) {
            StatisticsManager.restoreFromJson(school.statisticsJson)
        }
        restoreManagerField("financialReportJson", school.financialReportJson) {
            financialReportManager.restoreFromJson(school.financialReportJson)
        }
        restoreManagerField("pressureJson", school.pressureJson) {
            pressureSystemManager.restoreFromJson(school.pressureJson)
        }
        restoreManagerField("competitorJson", school.competitorJson) {
            competitorEngine.restoreFromJson(school.competitorJson)
        }
        restoreManagerField("crisisJson", school.crisisJson) {
            crisisScenarioManager.restoreFromJson(school.crisisJson)
        }
        restoreManagerField("alumniJson", school.alumniJson) {
            alumniNetwork.restoreFromJson(school.alumniJson)
        }

        var employmentDirty = false
        restoreManagerField("employmentJson", school.employmentJson) {
            employmentDirty = employmentMarket.restoreFromJson(school.employmentJson)
            val calibration = employmentMarket.calibrateHistoricalGraduates(
                currentYear = school.currentYear,
                currentMonth = school.currentMonth
            )
            if (employmentDirty || calibration.changed) {
                val updatedJson = employmentMarket.toJson()
                if (updatedJson.isBlank()) {
                    throw IllegalStateException("Employment calibration serialization returned blank")
                }
                schoolRepository.mutateSchool { latest ->
                    latest.employmentJson = updatedJson
                    true
                }
                android.util.Log.i(
                    "GameEngine",
                    "Employment market calibrated on restore: dirty=$employmentDirty, " +
                        "corrected=${calibration.correctedProgressCount}, " +
                        "graduated=${calibration.graduatedCount}"
                )
            }
        }

        restoreManagerField("principalJson", school.principalJson) {
            _principal.value = kotlinx.serialization.json.Json.decodeFromString<Principal>(
                school.principalJson
            )
        }
        restoreManagerField("suggestionBoxJson", school.suggestionBoxJson) {
            suggestionBoxManager.restoreFromJson(school.suggestionBoxJson)
        }

        managerStatesReadyForSave = true
        android.util.Log.i(
            "GameEngine",
            "Manager restore completed; protectedFields=${managerRestoreFailedFields.joinToString()}"
        )
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        if (_disciplinaryPause.value == null) {
            isPaused = false
        }
    }

    fun requireDisciplinaryRecovery(title: String, message: String) {
        _disciplinaryPause.value = DisciplinaryPause(title, message)
        isPaused = true
    }

    suspend fun recoverFromDisciplinaryPause(): Boolean {
        return engineOperationMutex.withLock {
            val currentPrincipal = _principal.value
            if (currentPrincipal.isArrested) return@withLock false
            val recovered = currentPrincipal.copy(
                version = currentPrincipal.version + 1,
                isSuspended = false,
                suspendedDaysLeft = 0,
                corruptionLevel =
                    (currentPrincipal.corruptionLevel - 20).coerceAtLeast(0),
                recentCorruptActs = currentPrincipal.recentCorruptActs
                    .map { it.copy() }
                    .toMutableList(),
                connections = currentPrincipal.connections
                    .map { it.copy() }
                    .toMutableList(),
                purchasedLuxuryItems = currentPrincipal.purchasedLuxuryItems
                    .toMutableList(),
                factionRelations = currentPrincipal.factionRelations
                    .toMutableMap()
            )
            val principalJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.serializer<Principal>(),
                recovered
            )
            val persisted = schoolRepository.mutateSchool { latest ->
                latest.principalJson = principalJson
                true
            } ?: return@withLock false
            _principal.value = recovered
            _disciplinaryPause.value = null
            isPaused = false
            android.util.Log.i(
                "GameEngine",
                "Disciplinary recovery persisted at revision ${persisted.lastSaveTime}"
            )
            true
        }
    }

    private fun isMonthlySettlementDue(school: School): Boolean =
        isMonthlySettlementDue(
            currentYear = school.currentYear,
            currentMonth = school.currentMonth,
            currentDay = school.currentDay,
            lastSettlementYear = school.lastMonthlySettlementYear,
            lastSettlementMonth = school.lastMonthlySettlementMonth
        )

    /**
     * 教程专用：直接跳到9月并触发招生，避免玩家在教程中傻等2.5分钟。
     * 会将日期快进到9月1日并执行一次完整的月初处理（含招生逻辑）。
     */
    suspend fun triggerEnrollmentForTutorial() {
        engineOperationMutex.withLock {
            val school = schoolRepository.getSchool() ?: return@withLock
            val enrollmentSchool = if (
                school.currentMonth < 9 ||
                (school.currentMonth == 9 && school.currentDay > 1)
            ) {
                schoolRepository.mutateSchool { latest ->
                    latest.currentMonth = 9
                    latest.currentDay = 1
                    true
                } ?: return@withLock
            } else {
                school
            }
            enrollNewStudents(enrollmentSchool)
        }
    }

    /**
     * 将所有 Manager 的内存状态刷入到 School 的 JSON 字段中，并持久化到 DB。
     * 存档前必须调用此方法，确保存档文件包含最新的游戏状态。
     */
    suspend fun flushAllManagerStates() {
        engineOperationMutex.withLock {
            flushAllManagerStatesLocked()
        }
    }

    private suspend fun flushAllManagerStatesLocked() {
        if (engineStopping) {
            android.util.Log.w("GameEngine", "Skip manager flush while engine is stopping")
            return
        }
        if (!managerStatesReadyForSave) {
            android.util.Log.w("GameEngine", "Skip manual manager flush: restore has not completed")
            return
        }
        val htMap = _classes.value
            .filter { it.headTeacherId != null }
            .associate { it.id to it.headTeacherId!! }
        val headTeacherJson = if (htMap.isNotEmpty()) {
            org.json.JSONObject(htMap).toString()
        } else {
            ""
        }
        val tierMap = _classes.value.associate { it.id to it.classTier.name }
        val classTierJson = if (tierMap.isNotEmpty()) {
            org.json.JSONObject(tierMap).toString()
        } else {
            ""
        }
        schoolRepository.mutateSchool { latest ->
            writeManagerJsonFields(latest)
            writeClassJsonFields(
                latest,
                headTeacherJson,
                classTierJson
            )
            true
        }
    }

    /**
     * Stops the loop and waits for any in-flight tick/manager write to finish.
     * SaveManager must call this before replacing the live Room database with another slot.
     */
    suspend fun stopAndJoin() {
        engineStopping = true
        isPaused = true
        val job = gameLoopJob
        job?.cancel()
        if (job != null) {
            joinAll(job)
        }
        engineOperationMutex.withLock { }
        gameLoopJob = null
        managerStatesReadyForSave = false
        pendingMonthlySettlementRetry = false
        pendingStudentYearEndRecovery = false
        pendingGraduationProjectionRetry = false
        managerRestoreFailedFields.clear()
        android.util.Log.i("GameEngine", "Game loop stopped and database writes drained")
    }

    fun stop() {
        engineStopping = true
        gameLoopJob?.cancel()
        isPaused = true
    }

    /**
     * 设置页"强制时间流动"：游戏时间异常卡住时手动恢复。
     * 重置暂停/停止标记并确保游戏循环在运行；不清空任何业务数据。
     * 游戏已结束时（校长被捕/破产等）不恢复。
     */
    fun forceResumeTimeFlow(): Boolean {
        if (gameOverDetector.crisisState.value == CrisisState.GAME_OVER) {
            android.util.Log.w("GameEngine", "Force resume rejected: game is over")
            return false
        }
        engineStopping = false
        isPaused = false
        _disciplinaryPause.value = null
        pendingMonthlySettlementRetry = false
        pendingStudentYearEndRecovery = false
        pendingGraduationProjectionRetry = false
        eventsSuppressed = false
        if (gameLoopJob?.isActive != true) {
            start()
        }
        android.util.Log.i("GameEngine", "Force resume time flow requested")
        return true
    }

    /**
     * 新游戏时重置引擎内存状态。
     * 数据库表由 SchoolRepository 的单一 Room 事务清理，避免这里二次删除或半清档。
     * 必须在 stopAndJoin() 之后、start() 之前调用。
     */
    suspend fun resetForNewGame() {
        managerStatesReadyForSave = false
        pendingMonthlySettlementRetry = false
        pendingStudentYearEndRecovery = false
        pendingGraduationProjectionRetry = false
        managerRestoreFailedFields.clear()
        // Core in-memory state
        gameOverDetector.reset()
        _principal.value = Principal()
        _disciplinaryPause.value = null
        connectionManager.initializeConnections(_principal.value)
        _classes.value = emptyList()
        isPaused = false

        // All managers with in-memory state
        achievementManager.reset()
        milestoneManager.reset()
        competitorEngine.reset()
        notificationManager.clearAll()
        alumniNetwork.clearAll()
        policyManager.resetToDefaults()
        clubManager.clearAll()
        clubActivityManager.reset()
        seasonalActivityManager.reset()
        reputationManager.reset()
        teacherDevelopmentManager.reset()
        financialReportManager.reset()
        parentSatisfactionManager.reset()
        governmentInspectionManager.reset()
        studentLifeManager.reset()
        campusExpansionManager.reset()
        academicConferenceManager.reset()
        scholarshipManager.reset()
        suggestionBoxManager.restoreFromJson("")
        factionManager.restoreRuntime(
            FactionRuntimeSnapshot(emptyMap(), emptySet())
        )
        pendingFactionEvents.clear()
        employmentMarket.reset()
        teachingManager.reset()
        pressureSystemManager.reset()
        crisisScenarioManager.reset()
        StatisticsManager.reset()
    }

    /**
     * 从危机状态恢复（接受救助后调用）
     */
    fun resumeFromCrisis() {
        isPaused = false
    }

    suspend fun handlePrincipalArrest(school: School) {
        val currentSchool = schoolRepository.getSchool() ?: school
        if (currentSchool.cash > 0) {
            schoolRepository.deductCash(currentSchool.cash)
        }
        val graduateCount = studentRepository.getGraduateCount()
        gameOverDetector.confirmGameOver(
            GameOverReason(
                conditions = listOf(FailureCondition.PRINCIPAL_ARRESTED),
                finalCash = 0.0,
                finalReputation = currentSchool.reputation,
                totalYearsPlayed = currentSchool.currentYear - currentSchool.foundedYear,
                totalStudentsGraduated = graduateCount,
                peakReputation = currentSchool.reputation,
                peakCash = currentSchool.totalRevenue
            )
        )
        isPaused = true
    }

    /**
     * 确认 GameOver 并停止引擎
     */
    fun confirmGameOver() {
        engineScope.launch {
            try {
                val school = schoolRepository.getSchool()
                if (school != null) {
                    val graduateCount = studentRepository.getGraduateCount()
                    gameOverDetector.confirmGameOver(
                        GameOverReason(
                            conditions = listOf(FailureCondition.BANKRUPT), // 会被实际条件覆盖
                            finalCash = school.cash,
                            finalReputation = school.reputation,
                            totalYearsPlayed = school.currentYear - school.foundedYear,
                            totalStudentsGraduated = graduateCount,
                            peakReputation = school.reputation, // 简化：当前即为记录
                            peakCash = school.totalRevenue
                        )
                    )
                }
                isPaused = true
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "Error in confirmGameOver", e)
                isPaused = true
            }
        }
    }

    private suspend fun tick() {
        if (pendingStudentYearEndRecovery) {
            val recoverySchool = schoolRepository.getSchool() ?: return
            try {
                val recoveredClasses = processStudentYearEnd(
                    school = recoverySchool,
                    currentClasses = _classes.value
                        .map { it.copy() }
                        .toMutableList(),
                    currentStudents = studentRepository.getCurrentStudents(),
                    emitNotifications = false
                )
                _classes.value = recoveredClasses
                pendingStudentYearEndRecovery = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e(
                    "GameEngine",
                    "Student year-end isolated retry failed; date advance blocked",
                    e
                )
                return
            }
        }

        schoolRepository.advanceDay()

        val completedResearch = researchRepository.advanceResearchDay()
        completedResearch.forEach { method ->
            deferEvent(GameEvent.PositiveEvent(
                title = "研究完成",
                message = "《${method.name}》研究完成，${method.bonusType.displayName}+${(method.bonusValue * 100).toInt()}% 已永久生效。",
                bonusCash = 0.0,
                bonusReputation = 0L
            ))
        }

        // 股票系统：先处理活跃事件 tick，再更新价格（事件影响已在 updateStockPrices 内叠加）
        stockRepository.tickActiveEvents()
        stockRepository.updateStockPrices()

        val school = schoolRepository.getSchool() ?: return

        if (pendingGraduationProjectionRetry) {
            try {
                processPendingGraduationProjections(
                    school,
                    emitNotifications = false
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "GameEngine",
                    "Pending graduation projection retry failed",
                    e
                )
            }
        }

        // 记录 K 线数据（每天仅记录一次，而非每次 tick，防止股价历史表无限膨胀）
        val totalDay = (school.currentYear - 1988) * 360 + (school.currentMonth - 1) * 30 + school.currentDay
        if (totalDay != lastRecordedPriceDay) {
            lastRecordedPriceDay = totalDay
            stockRepository.recordDailyPrice(totalDay)
        }

        // 股票市场事件生成
        updateStockMarketEvents(school)

        updateActiveCourses(school)
        updateTeacherStatus(school)
        updateMarketingCampaigns(school)
        updateStudentProgress(school)

        // 停职由“纪律处分暂停”统一处理。不能依赖 tick 倒计时，因为处分期间整个游戏循环必须冻结。

        // 季节活动每日推进
        val dayAdvanceResult = seasonalActivityManager.advanceDay(
            school.currentYear, school.currentMonth, school.currentDay
        )

        // 处理当天刚进入 ACTIVE 阶段的活动 → 触发迷你游戏
        for (activity in dayAdvanceResult.newlyActiveActivities) {
            _miniGameTrigger.emit(activity)
        }

        // 处理当天完成的活动 → 结算奖励
        for (result in dayAdvanceResult.completedResults) {
            // cashSpent 单位是元，school.cash 单位是万元，需要 /10000 转换
            schoolRepository.deductCash(result.cashSpent.toDouble() / 10000.0)
            schoolRepository.addReputation(result.reputationGain.toLong())
            emitEvent(GameEvent.PositiveEvent(
                title = "${result.activity.type.displayName}圆满结束",
                message = (result.specialMessage ?: "活动顺利完成！") +
                    " 声誉+${result.reputationGain}",
                bonusCash = 0.0,
                bonusReputation = 0  // 效果已在上方直接应用，事件仅作通知
            ), school)
            // 为多维声誉添加对应维度分数
            val repDim = mapActivityToDimension(result.activity.type)
            reputationManager.addDimensionReputation(
                repDim, result.reputationGain.toFloat(),
                "${result.activity.type.displayName}活动加成"
            )
        }

        // 社团申请每日超时检查
        clubManager.advanceDay()

        // 每日释放一个延迟事件（避免月初弹窗堆积）
        drainOneDeferredEvent(school)

        // 每月1号执行尚未完成的月结；仅月结内部失败才会触发重试标记。
        if (isMonthlySettlementDue(school) || pendingMonthlySettlementRetry) {
            if (!managerStatesReadyForSave || managerRestoreFailedFields.isNotEmpty()) {
                throw IllegalStateException(
                    "Monthly settlement blocked: manager restore is incomplete; " +
                        "failed=${managerRestoreFailedFields.joinToString()}"
                )
            }
            // 重试发生在非1日（月结在1日推进日期后执行），此时跳过收入/支出避免重复扣费。
            val isRetrySettlement =
                pendingMonthlySettlementRetry && school.currentDay != 1
            pendingMonthlySettlementRetry = false
            try {
            // 首次进入1日时，日常推进已在月结前完成。先持久化这个稳定基线，
            // 使月结失败并立即重启时不会重复或漏掉当日 Manager 推进。
            flushAllManagerStatesLocked()
            // ======= 支出追踪（用于财务报表明细）=======
            var expLifeExpenses = 0.0
            var expMaintenance = 0.0
            var expConference = 0.0
            var expClubActivity = 0.0
            var expTeacherDev = 0.0
            var expScholarship = 0.0
            var expClubMonthly = 0.0     // 社团月运营费
            var expCareerProgram = 0.0   // 就业辅导费
            var incAlumniDonation = 0.0  // 校友捐赠收入
            var incGovSubsidy = 0.0      // 政府补贴收入
            var expGovFine = 0.0         // 政府罚款支出
            var expMarketing = 0.0       // 招生宣传费（每日累计近似）

            val monthlyRevenue: Double
            val expenseBreakdown: MonthlyExpenseBreakdown
            if (!isRetrySettlement) {
                monthlyRevenue = updateReleasedCourses(school)
                expenseBreakdown = deductMonthlyExpenses(school)
            } else {
                // 重试路径：费用已在上次尝试中扣除，跳过避免重复扣费
                android.util.Log.w(
                    "GameEngine",
                    "Monthly settlement retry: skipping expense deduction to prevent double-charge"
                )
                monthlyRevenue = 0.0
                expenseBreakdown = MonthlyExpenseBreakdown(0.0, 0.0, 0.0, 0.0)
            }
            var monthlyExpenses = expenseBreakdown.total

            // 月结算统一缓存：避免同一次 tick 内重复查询数据库
            val allTeachersCache = teacherRepository.getTeachers()
            val allCurrentStudents = studentRepository.getCurrentStudents()

            // === 班级系统月度更新（必须在招生前更新班级容量指标）===
            if (allCurrentStudents.isNotEmpty()) {
                val currentClasses = _classes.value.toMutableList()
                val allTeachersForClass = allTeachersCache

                // 更新班级聚合指标（平均五维、满意度、人数等）——在招生前确保容量准确
                classManager.updateClassMetrics(currentClasses, allCurrentStudents, allTeachersForClass)

                // 清除已禁用班型的空班（用户把某班型数量设为0后，对应的无学生班级应被移除）
                val activeDistribution = teachingManager.config.classDistribution
                val disabledTierClasses = currentClasses.filter { cls ->
                    val configuredCount = activeDistribution[cls.classTier] ?: 0
                    configuredCount == 0 && cls.studentCount == 0
                }
                if (disabledTierClasses.isNotEmpty()) {
                    currentClasses.removeAll(disabledTierClasses)
                    android.util.Log.i("GameEngine", "Removed ${disabledTierClasses.size} classes of disabled tiers")
                }

                // 触发班级事件（比赛获奖、纪律问题、班级活动等）—— 延迟投递避免堆积
                val classEvents = classManager.monthlyEvents(currentClasses)
                classEvents.forEach { event ->
                    when (event) {
                        is ClassEvent.AwardEvent -> {
                            schoolRepository.addReputation(event.reputationBonus)
                            deferEvent(GameEvent.PositiveEvent(
                                title = event.title,
                                message = event.message,
                                bonusCash = 0.0,
                                bonusReputation = 0L
                            ))
                        }
                        is ClassEvent.DisciplineEvent -> {
                            schoolRepository.deductReputation(event.reputationPenalty)
                            deferEvent(GameEvent.NegativeEvent(
                                title = event.title,
                                message = event.message,
                                penaltyCash = 0.0,
                                penaltyReputation = 0L
                            ))
                        }
                        is ClassEvent.ActivityEvent -> {
                            deferEvent(GameEvent.PositiveEvent(
                                title = event.title,
                                message = event.message,
                                bonusCash = 0.0,
                                bonusReputation = 1L
                            ))
                        }
                        is ClassEvent.ConflictEvent -> {
                            deferEvent(GameEvent.NegativeEvent(
                                title = event.title,
                                message = event.message,
                                penaltyCash = 0.0,
                                penaltyReputation = 1L
                            ))
                        }
                        is ClassEvent.PromotionEvent -> {
                            emitEvent(GameEvent.PositiveEvent(
                                title = event.title,
                                message = event.message,
                                bonusCash = 0.0,
                                bonusReputation = 0L
                            ), school)
                        }
                    }
                }

                _classes.value = currentClasses
            }

            // 学生年结失败时中断整个月结；月结会在下个 tick 重试，避免部分晋级/毕业。
            try {
                val processedClasses = processStudentYearEnd(
                    school = school,
                    currentClasses = _classes.value
                        .map { it.copy() }
                        .toMutableList(),
                    currentStudents = allCurrentStudents,
                    emitNotifications = true
                )
                _classes.value = processedClasses
                pendingStudentYearEndRecovery = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                pendingStudentYearEndRecovery =
                    "classTierMapJson" !in managerRestoreFailedFields
                android.util.Log.e(
                    "GameEngine",
                    "Student year-end processing failed; rolling back monthly settlement",
                    e
                )
                throw e
            }

            // 招生：仅9月开学季招生
            if (school.currentMonth == 9) {
                enrollNewStudents(school)
            } else if (school.currentMonth == 3 || school.currentMonth == 6) {
                // 非招生季提醒：如果学校完全没有学生，引导玩家知道招生时间
                val hasStudents = studentRepository.getActiveStudents().isNotEmpty()
                if (!hasStudents) {
                    emitEvent(GameEvent.PositiveEvent(
                        title = "招生提醒",
                        message = "学校目前没有在校学生。新生招录将在每年9月进行，届时需要已建好教室并配置教学方案。请提前做好准备！",
                        bonusCash = 0.0,
                        bonusReputation = 0
                    ), school)
                }
            }

            // === 课表系统：每学期初(2月/9月)重新生成课表 + 重置学期掌握度 ===
            if (school.currentMonth == 2 || school.currentMonth == 9) {
                // 同步教学配置的PE课时到课表系统，并全校统一重排
                timetableManager.configuredPEHours = teachingManager.config.weeklyPEHours
                val allClasses = _classes.value
                timetableManager.regenerateAllTimetables(allClasses, allTeachersCache)
                // 新学期开始，重置所有学生的学期掌握度
                resetSemesterMastery()
            }

            // === 考试系统月度推进（成绩会回写到 student.academicScore）===
            val activeStudentsForExam = studentRepository.getActiveStudents()
            if (activeStudentsForExam.isNotEmpty()) {
                val teacherAvgSkill = if (allTeachersCache.isNotEmpty()) {
                    allTeachersCache.map { it.averageSkill.toFloat() }.average().toFloat()
                } else 30f
                val examResult = examManager.advanceMonth(
                    school.currentYear, school.currentMonth, activeStudentsForExam, teacherAvgSkill,
                    monthlyExamFrequency = teachingManager.config.monthlyExamFrequency,
                    intensityScoreMultiplier = teachingManager.config.intensity.scoreMultiplier,
                    teachers = allTeachersCache
                )
                // 考试结束后，将更新后的 academicScore 持久化
                if (examResult.examHeld) {
                    studentRepository.updateAcademicScores(activeStudentsForExam)
                }
                // 清理已毕业/转学学生的成绩缓存，防止内存无限增长
                examManager.cleanupInactiveStudents(activeStudentsForExam.map { it.id }.toSet())
            }

            updateSchoolReputation(school)

            // 季节活动月初触发（只有学校有学生或已开课时才触发，避免空校办活动）
            val hasStudentsOrCourses = studentRepository.getActiveStudents().isNotEmpty() ||
                    courseRepository.getReleasedCourses().isNotEmpty()
            if (hasStudentsOrCourses) {
                val newPendingActivities = seasonalActivityManager.onMonthStart(
                    school.currentYear, school.currentMonth, school.reputation.toLong()
                )
                // 为每个待审批活动发送 ChoiceEvent 通知校长（延迟投递避免堆积）
                for (activity in newPendingActivities) {
                    val costWan = activity.type.baseCost / 10000.0
                    deferEvent(GameEvent.ChoiceEvent(
                        title = "活动审批：${activity.type.displayName}",
                        message = "${activity.type.description}\n\n" +
                            "预计费用：${String.format("%.1f", costWan)}万元（标准规模）\n" +
                            "预计声誉收益：+${activity.type.baseReputationGain}\n" +
                            "筹备期：${activity.type.preparationDays}天 | 持续：${activity.type.durationDays}天\n\n" +
                            "请选择活动规模并签字批准，或驳回此申请。\n" +
                            "（未在15天内处理将自动过期）",
                        choices = listOf(
                            EventChoice("简朴举办（费用×0.5，收益×0.5）", EventConsequence(
                                activityAction = ActivityAction.Approve(activity.id, "MINIMAL"),
                                requiresSignature = true
                            )),
                            EventChoice("标准举办（费用×1.0，收益×1.0）", EventConsequence(
                                activityAction = ActivityAction.Approve(activity.id, "STANDARD"),
                                requiresSignature = true
                            )),
                            EventChoice("隆重举办（费用×1.8，收益×1.5）", EventConsequence(
                                activityAction = ActivityAction.Approve(activity.id, "GRAND"),
                                requiresSignature = true
                            )),
                            EventChoice("盛大举办（费用×3.0，收益×2.2）", EventConsequence(
                                activityAction = ActivityAction.Approve(activity.id, "SPECTACULAR"),
                                requiresSignature = true
                            )),
                            EventChoice("驳回申请", EventConsequence(
                                activityAction = ActivityAction.Reject(activity.id)
                            ))
                        )
                    ))
                }
            }

            // 校友网络月度更新：职业发展、捐赠、推荐
            val alumniResult = alumniNetwork.advanceMonth(school.campusLevel)
            if (alumniResult.totalDonation > 0) {
                // 捐赠金额单位是元，school.cash单位是万元，需要 /10000 转换
                val donationWan = alumniResult.totalDonation / 10000.0
                schoolRepository.addCash(donationWan)
                incAlumniDonation += donationWan
            }
            if (alumniResult.referralCount > 0) {
                // 校友推荐的学生在下个月自动入学（加到声誉中吸引更多学生）
                schoolRepository.addReputation(alumniResult.referralCount.toLong() * 2)
            }
            // 校友网络声誉加成
            val alumniReputationBonus = alumniNetwork.getReputationBonus()
            if (alumniReputationBonus > 0) {
                schoolRepository.addReputation(alumniReputationBonus.toLong())
            }
            // 校友捐赠事件通知（延迟投递）
            alumniResult.events.forEach { alumniEvent ->
                when (alumniEvent) {
                    is com.arktools.xiaozhang.domain.alumni.AlumniEvent.MajorDonation -> {
                        deferEvent(GameEvent.PositiveEvent(
                            title = "校友巨额捐赠",
                            message = "杰出校友${alumniEvent.alumniName}（${alumniEvent.career.displayName}）向母校捐赠了 ¥${String.format("%,.0f", alumniEvent.amount)}！",
                            bonusCash = 0.0,
                            bonusReputation = 10.toLong()
                        ))
                    }
                    is com.arktools.xiaozhang.domain.alumni.AlumniEvent.CampusSpeech -> {
                        deferEvent(GameEvent.PositiveEvent(
                            title = "校友返校演讲",
                            message = "${alumniEvent.alumniName}（${alumniEvent.career.displayName}）回校演讲，激励在校学生！",
                            bonusCash = 0.0,
                            bonusReputation = alumniEvent.reputationBonus.toLong()
                        ))
                    }
                    is com.arktools.xiaozhang.domain.alumni.AlumniEvent.Donation -> {}
                    is com.arktools.xiaozhang.domain.alumni.AlumniEvent.Referral -> {}
                }
            }

            // 社团活动月度更新（需要有学生才运作）
            val totalStudents = studentRepository.getActiveStudents().size
            if (totalStudents > 0) {
                val clubResult = clubManager.advanceMonth(totalStudents)
                if (clubResult.monthlyExpense > 0) {
                    // monthlyCost已改为万元单位（0.3-2.0），直接扣除
                    schoolRepository.deductCash(clubResult.monthlyExpense)
                    expClubMonthly += clubResult.monthlyExpense
                }
                if (clubResult.reputationBonus > 0) {
                    schoolRepository.addReputation(clubResult.reputationBonus.toLong())
                }
                // 社团满意度加成应用到学生
                if (clubResult.satisfactionBonus > 0f) {
                    studentRepository.adjustActiveStudentSatisfaction(
                        clubResult.satisfactionBonus * 0.1f
                    )
                }
                // 社团竞赛获奖通知（延迟投递）
                clubResult.events.forEach { clubEvent ->
                    when (clubEvent) {
                        is com.arktools.xiaozhang.domain.club.ClubEvent.Competition -> {
                            if (clubEvent.won) {
                                deferEvent(GameEvent.PositiveEvent(
                                    title = "社团竞赛获奖",
                                    message = "${clubEvent.clubName}在比赛中获胜！学校声望提升！",
                                    bonusCash = 0.0,
                                    bonusReputation = 0L
                                ))
                            }
                        }
                        is com.arktools.xiaozhang.domain.club.ClubEvent.Achievement -> {
                            deferEvent(GameEvent.PositiveEvent(
                                title = "社团成就",
                                message = "${clubEvent.clubName}: ${clubEvent.achievement}",
                                bonusCash = 0.0,
                                bonusReputation = 0L
                            ))
                        }
                        else -> {}
                    }
                }

                // 生成学生社团申请并发送审批事件（延迟投递）
                clubManager.currentCampusLevel = school.campusLevel
                val newApplications = clubManager.generateApplications(
                    totalStudents, school.reputation.toLong(), school.campusLevel
                )
                for (application in newApplications) {
                    val costWan = application.clubType.monthlyCost
                    deferEvent(GameEvent.ChoiceEvent(
                        title = "社团创建申请",
                        message = "学生${application.applicantName}等${application.applicantCount}人" +
                            "提交了创建「${application.clubType.defaultName}」的申请。\n\n" +
                            "申请理由：\"${application.reason}\"\n\n" +
                            "社团类型：${application.clubType.category.displayName}\n" +
                            "月度费用：${String.format("%.1f", costWan)}万元\n" +
                            "满意度加成：+${application.clubType.satisfactionBonus}/月\n" +
                            "声誉加成：+${application.clubType.reputationBonus.toInt()}/月\n\n" +
                            "是否批准创建？（未在20天内处理将自动撤回）",
                        choices = listOf(
                            EventChoice("签字批准", EventConsequence(
                                clubAction = ClubAction.Approve(application.id),
                                requiresSignature = true
                            )),
                            EventChoice("驳回申请（声誉略降）", EventConsequence(
                                clubAction = ClubAction.Reject(application.id)
                            ))
                        )
                    ))
                }
            }

            // 就业市场月度更新（传入政府评级加成和学校等级）
            val govBoostForEmployment = governmentInspectionManager.state.value.let { govState ->
                when (govState.currentGrade) {
                    com.arktools.xiaozhang.domain.government.SchoolGrade.AAA -> 1.30f
                    com.arktools.xiaozhang.domain.government.SchoolGrade.AA -> 1.15f
                    com.arktools.xiaozhang.domain.government.SchoolGrade.A -> 1.05f
                    else -> 1.0f
                }
            }
            // 学术会议对就业的累积加成
            val academicEmploymentBoost = academicConferenceManager.getCurrentEmploymentBoost()
            val totalGovBoost = govBoostForEmployment + academicEmploymentBoost
            val employmentResult = employmentMarket.advanceMonth(
                school.reputation.toLong(), school.currentYear, school.currentMonth,
                governmentBoostFactor = totalGovBoost,
                schoolLevel = school.campusLevel
            )
            // 扣除职业辅导费用
            val programCost = employmentMarket.getProgramMonthlyCost()
            if (programCost > 0) {
                schoolRepository.deductCash(programCost)
                expCareerProgram += programCost
            }
            if (employmentResult.reputationBonus > 0) {
                schoolRepository.addReputation(employmentResult.reputationBonus.toLong())
            }
            employmentResult.events.forEach { empEvent ->
                when (empEvent) {
                    is com.arktools.xiaozhang.domain.employment.EmploymentEvent.UniversityGraduation -> {
                        emitEvent(GameEvent.PositiveEvent(
                            title = "毕业生喜讯",
                            message = "${empEvent.studentName}从${empEvent.universityTier.displayName}毕业，进入${empEvent.industry.displayName}行业！",
                            bonusCash = 0.0,
                            bonusReputation = 0L
                        ), school)
                    }
                    is com.arktools.xiaozhang.domain.employment.EmploymentEvent.AlumniSuccess -> {
                        emitEvent(GameEvent.PositiveEvent(
                            title = "校友成就",
                            message = "${empEvent.studentName}${empEvent.achievement}，为学校带来声誉！",
                            bonusCash = 0.0,
                            bonusReputation = empEvent.reputationBonus
                        ), school)
                    }
                    else -> {}
                }
            }

            // 多维声誉月度推进（传入就业率和政府评级实现联动）
            val teachers = teacherRepository.getTeachers().filter { it.isWorking }
            val avgTeacherQuality = if (teachers.isNotEmpty()) {
                (teachers.sumOf { it.averageSkill } / teachers.size.toFloat() / 10f).coerceIn(0f, 100f)
            } else 0f
            // 设施等级：综合考虑 campusLevel + 实际设施数量 + 平均 condition
            val facilityConditionFactor = if (school.facilities.isNotEmpty()) {
                val avgCondition = school.facilities.map { it.condition }.average().toFloat() / 100f
                val facilityCountBonus = (school.facilities.size.toFloat() / 5f).coerceAtMost(1f)
                (school.campusLevel * avgCondition * (0.7f + 0.3f * facilityCountBonus)).toInt().coerceIn(1, 10)
            } else {
                school.campusLevel.coerceIn(1, 10)
            }
            val studentSat = parentSatisfactionManager.getEnrollmentMultiplier() * 60f  // 基于家长满意度
            val currentGovGradeOrdinal = governmentInspectionManager.state.value.currentGrade.ordinal

            // 体育/艺术投入评分：把玩家实际建设的体育/艺术设施、社团、课时转化为声誉加成
            val sportsFieldLevel = school.facilities.find { it.type == com.arktools.xiaozhang.domain.model.FacilityType.SPORTS_FIELD }?.level ?: 0
            val artStudioLevel = school.facilities.find { it.type == com.arktools.xiaozhang.domain.model.FacilityType.ART_STUDIO }?.level ?: 0
            val sportsClubs = clubManager.clubs.value.filter { it.type.category == com.arktools.xiaozhang.domain.club.ClubCategory.SPORTS }.size
            val artsClubs = clubManager.clubs.value.filter { it.type.category == com.arktools.xiaozhang.domain.club.ClubCategory.ARTS }.size
            val sportsInvestmentScore = (
                facilityConditionFactor * 5f +
                sportsFieldLevel * 10f +
                sportsClubs * 10f +
                teachingManager.config.weeklyPEHours * 5f
            ).coerceAtMost(100f)
            val artsInvestmentScore = (
                artStudioLevel * 10f +
                artsClubs * 10f
            ).coerceAtMost(100f)

            val repResult = reputationManager.advanceMonth(
                school.reputation, avgTeacherQuality, facilityConditionFactor, studentSat,
                school.currentYear, school.currentMonth,
                employmentRate = employmentResult.currentEmploymentRate,
                governmentGradeOrdinal = currentGovGradeOrdinal,
                schoolLevel = school.campusLevel,
                teachingQualityBonus = teachingManager.config.overallQuality(avgTeacherQuality),
                sportsInvestmentScore = sportsInvestmentScore,
                artsInvestmentScore = artsInvestmentScore
            )
            if (repResult.totalGrowth > 0) {
                schoolRepository.addReputation(repResult.totalGrowth.toLong())
            }
            repResult.newMilestones.forEach { milestone ->
                emitEvent(GameEvent.PositiveEvent(
                    title = "声誉里程碑",
                    message = milestone,
                    bonusCash = 0.0,
                    bonusReputation = 5.toLong()
                ), school)
            }

            // 学生生活系统月度推进：现金与月结后 JSON 同事务提交。
            val studentCount = studentRepository.getActiveStudents().size
            if (studentCount > 0 &&
                !studentLifeManager.hasProcessedMonth(
                    school.currentYear,
                    school.currentMonth
                )
            ) {
                var committedLifeResult:
                    com.arktools.xiaozhang.domain.studentlife.LifeMonthlyResult? = null
                val operationResult = commitStudentLifeOperationLocked { latest ->
                    studentLifeManager.updateStudentCount(studentCount)
                    val lifeResult = studentLifeManager.advanceMonth(
                        studentCount,
                        school.currentYear,
                        school.currentMonth
                    )
                    latest.cash = (
                        latest.cash - lifeResult.totalExpenses.toDouble()
                    ).coerceAtLeast(-100.0)
                    committedLifeResult = lifeResult
                    ManagedOperationResult(
                        true,
                        "学生生活月结完成",
                        lifeResult.totalExpenses.toDouble()
                    )
                }
                if (!operationResult.success) {
                    throw IllegalStateException(operationResult.message)
                }
                committedLifeResult?.let { lifeResult ->
                    expLifeExpenses += lifeResult.totalExpenses.toDouble()
                    lifeResult.newIssues.forEach { issue ->
                        emitEvent(GameEvent.NegativeEvent(
                            title = "生活问题: ${issue.title}",
                            message = issue.description,
                            penaltyCash = 0.0,
                            penaltyReputation = issue.satisfactionPenalty.toLong()
                        ), school)
                    }
                }
            }

            // 持久化所有Manager状态。恢复失败时绝不能用默认空状态覆盖原始存档。
            if (!managerStatesReadyForSave || managerRestoreFailedFields.isNotEmpty()) {
                throw IllegalStateException(
                    "Monthly settlement blocked: manager restore is incomplete; " +
                        "failed=${managerRestoreFailedFields.joinToString()}"
                )
            }
            val htMap = _classes.value
                .filter { it.headTeacherId != null }
                .associate { it.id to it.headTeacherId!! }
            val headTeacherJson = if (htMap.isNotEmpty()) {
                org.json.JSONObject(htMap).toString()
            } else {
                ""
            }
            val tierMap = _classes.value.associate { it.id to it.classTier.name }
            val classTierJson = if (tierMap.isNotEmpty()) {
                org.json.JSONObject(tierMap).toString()
            } else {
                ""
            }
            schoolRepository.mutateSchool { latest ->
                writeManagerJsonFields(latest)
                writeClassJsonFields(
                    latest,
                    headTeacherJson,
                    classTierJson
                )
                true
            }

            // 校区扩建月度推进：维护费与月结后 JSON 同事务提交。
            var committedExpansionResult:
                com.arktools.xiaozhang.domain.expansion.ExpansionMonthlyResult? = null
            if (!campusExpansionManager.hasProcessedMonth(
                    school.currentYear,
                    school.currentMonth
                )
            ) {
                val operationResult = commitExpansionOperationLocked { latest ->
                    val expansionResult = campusExpansionManager.advanceMonth(
                        school.currentYear,
                        school.currentMonth,
                        studentCount
                    )
                    latest.cash = (
                        latest.cash - expansionResult.maintenanceCost
                    ).coerceAtLeast(-100.0)
                    committedExpansionResult = expansionResult
                    ManagedOperationResult(
                        true,
                        "校区扩建月结完成",
                        expansionResult.maintenanceCost
                    )
                }
                if (!operationResult.success) {
                    throw IllegalStateException(operationResult.message)
                }
            }
            committedExpansionResult?.let { expansionResult ->
                expMaintenance += expansionResult.maintenanceCost
                expansionResult.newCompletions.forEach { zone ->
                    emitEvent(GameEvent.PositiveEvent(
                        title = "${zone.name}竣工！",
                        message = "新增容纳量${zone.capacity}人，校区实力再上台阶",
                        bonusCash = 0.0,
                        bonusReputation = 10.toLong()
                    ), school)
                }
            }

            // 学术会议月度推进（传入学校等级和教师数量）
            val confResult = academicConferenceManager.advanceMonth(
                school.currentYear, school.currentMonth, school.reputation.toInt(),
                schoolLevel = school.campusLevel,
                teacherCount = teacherRepository.getTeachers().size
            )
            if (confResult.expenses > 0) {
                schoolRepository.deductCash(confResult.expenses)
                expConference += confResult.expenses
            }
            if (confResult.reputationGain > 0) {
                schoolRepository.addReputation(confResult.reputationGain.toLong())
            }
            confResult.completedConferences.forEach { conf ->
                val outputText = if (conf.researchOutputs.isNotEmpty()) {
                    "\n成果: ${conf.researchOutputs.groupBy { it }.map { "${it.value.size}${it.key.displayName}" }.joinToString("、")}"
                } else ""
                emitEvent(GameEvent.PositiveEvent(
                    title = "学术会议: ${conf.name}",
                    message = "会议圆满结束，声誉+${conf.reputationGained}，就业+${String.format("%.0f", conf.employmentBoostGained * 100)}%${outputText}",
                    bonusCash = 0.0,
                    bonusReputation = 0L
                ), school)
            }

            // 学术会议教师成长池：每月消费并平均分配给在职教师
            val growthPool = academicConferenceManager.consumeTeacherGrowthPool()
            if (growthPool > 0f) {
                val workingTeachers = teacherRepository.getTeachers().filter { it.isWorking && !it.isOnVacation }
                if (workingTeachers.isNotEmpty()) {
                    val perTeacher = growthPool / workingTeachers.size
                    workingTeachers.forEach { teacher ->
                        teacherRepository.addSkillGrowth(
                            teacherId = teacher.id,
                            teachingGain = (perTeacher * 0.4f).toInt(),
                            researchGain = (perTeacher * 0.4f).toInt(),
                            managementGain = (perTeacher * 0.1f).toInt(),
                            psychologyGain = (perTeacher * 0.1f).toInt()
                        )
                    }
                    // 学术会议也给教师学分（每次按人均成长值折算，至少+1学分）
                    val creditPerTeacher = (perTeacher / 5f).toInt().coerceAtLeast(1)
                    teacherDevelopmentManager.addCreditsToAll(creditPerTeacher)
                }
            }

            // 社团活动系统月度推进（需要有学生才运作）
            if (studentCount > 0) {
                val clubActResult = clubActivityManager.advanceMonth(
                    school.currentYear, school.currentMonth, school.reputation
                )
                if (clubActResult.expenses > 0) {
                    // clubActResult.expenses 单位是元（来自UI输入"预算(元)"），需转换为万元
                    val clubActExpenseWan = clubActResult.expenses.toDouble() / 10000.0
                    schoolRepository.deductCash(clubActExpenseWan)
                    expClubActivity += clubActExpenseWan
                }
                if (clubActResult.reputationGain > 0) {
                    schoolRepository.addReputation(clubActResult.reputationGain.toLong())
                }
                clubActResult.newAwards.forEach { award ->
                    emitEvent(GameEvent.PositiveEvent(
                        title = award.title,
                        message = award.description,
                        bonusCash = 0.0,
                        bonusReputation = 0L
                    ), school)
                }
            }

            // 教师职业发展月度推进：Manager、教师表、现金和 JSON 单事务提交
            val workingTeachersForDevelopment = allTeachersCache.filter { it.isWorking }
            val teachersById = workingTeachersForDevelopment.associateBy { it.id }
            val teacherDevUnavailable =
                com.arktools.xiaozhang.domain.teacherdev.TeacherDevMonthlyResult()
            val (teacherDevResult, teacherDevCommitted) =
                commitTeacherDevelopmentOperationLocked(teacherDevUnavailable) {
                    val teacherSyncData = workingTeachersForDevelopment.map { teacher ->
                        com.arktools.xiaozhang.domain.teacherdev.TeacherSyncData(
                            id = teacher.id,
                            legacyId = teacher.id.hashCode().toLong(),
                            name = teacher.name,
                            subject = teacher.role.name,
                            skill = teacher.averageSkill.toFloat(),
                            level = teacher.level
                        )
                    }
                    teacherDevelopmentManager.syncTeachers(teacherSyncData)
                    val monthlyResult = teacherDevelopmentManager.advanceMonth(
                        school.currentYear,
                        school.currentMonth,
                        school.reputation
                    )
                    val departedIds = monthlyResult.departures
                        .map { it.teacherId }
                        .distinct()
                    val departedIdSet = departedIds.toSet()
                    val profileUpdates = teacherDevelopmentManager.state.value.teacherProfiles
                        .mapNotNull { profile ->
                            val teacher = teachersById[profile.teacherId]
                                ?: return@mapNotNull null
                            if (profile.teacherId in departedIdSet) {
                                return@mapNotNull null
                            }
                            TeacherDevelopmentProfileUpdate(
                                teacherId = profile.teacherId,
                                level = teacherTitleToLevel(profile.title),
                                profileSkillLevel = profile.skillLevel,
                                primarilyTeaching = isPrimarilyTeaching(teacher)
                            )
                        }
                    val pressureJson: String?
                    val timetableJson: String?
                    if (departedIds.isEmpty()) {
                        pressureJson = null
                        timetableJson = null
                    } else {
                        check(
                            "pressureJson" !in managerRestoreFailedFields &&
                                "timetableJson" !in managerRestoreFailedFields
                        ) {
                            "Teacher departure manager state was not safely restored"
                        }
                        departedIds.forEach(pressureSystemManager::expireContract)
                        val remainingTeachers = workingTeachersForDevelopment.filterNot {
                            it.id in departedIdSet
                        }
                        timetableManager.configuredPEHours =
                            teachingManager.config.weeklyPEHours
                        timetableManager.regenerateAllTimetables(
                            _classes.value,
                            remainingTeachers
                        )
                        pressureJson = pressureSystemManager.toJson()
                        timetableJson = timetableManager.toJson()
                        check(pressureJson.isNotBlank() && timetableJson.isNotBlank()) {
                            "Teacher departure manager serialization failed"
                        }
                    }
                    TeacherDevelopmentCommit(
                        value = monthlyResult,
                        shouldCommit = true,
                        expense = monthlyResult.expenses,
                        departedTeacherIds = departedIds,
                        profileUpdates = profileUpdates,
                        pressureJson = pressureJson,
                        timetableJson = timetableJson
                    )
                }
            if (!teacherDevCommitted) {
                throw IllegalStateException(
                    "教师职业发展月结状态未能安全提交"
                )
            }
            expTeacherDev += teacherDevResult.expenses
            teacherDevResult.departures.forEach { departure ->
                val departedTeacher = teachersById[departure.teacherId]
                val display = formatTeacherWithSubject(
                    departure.teacherName,
                    departedTeacher
                )
                emitEvent(GameEvent.NegativeEvent(
                    title = "教师离职: $display",
                    message = "${display}已离开学校",
                    penaltyCash = 0.0,
                    penaltyReputation = 3.toLong()
                ), school)
            }
            teacherDevResult.promotions.forEach { promotion ->
                emitEvent(GameEvent.PositiveEvent(
                    title = "教师晋升: ${promotion.teacherName}",
                    message = "${promotion.teacherName}获得职称晋升",
                    bonusCash = 0.0,
                    bonusReputation = 2.toLong()
                ), school)
            }

            // 财务报表系统月度结算
            financialReportManager.recordIncome(
                com.arktools.xiaozhang.domain.finance.IncomeCategory.TUITION, monthlyRevenue
            )
            financialReportManager.recordExpense(
                com.arktools.xiaozhang.domain.finance.ExpenseCategory.TEACHER_SALARY, expenseBreakdown.salary
            )
            if (expenseBreakdown.facilities > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.UTILITIES, expenseBreakdown.facilities
                )
            }
            if (expenseBreakdown.teaching > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.TEACHING_OPERATION, expenseBreakdown.teaching
                )
            }
            if (expMaintenance > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.FACILITY_MAINTENANCE, expMaintenance
                )
            }
            if (expConference + expClubActivity + expClubMonthly > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.ACTIVITY_COST,
                    expConference + expClubActivity + expClubMonthly
                )
            }
            if (expTeacherDev + expCareerProgram > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.TRAINING_COST,
                    expTeacherDev + expCareerProgram
                )
            }
            if (expLifeExpenses > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.UTILITIES, expLifeExpenses
                )
            }
            // 校友捐赠收入（已在本月确定）
            if (incAlumniDonation > 0) {
                financialReportManager.recordIncome(
                    com.arktools.xiaozhang.domain.finance.IncomeCategory.ALUMNI_CONTRIBUTION, incAlumniDonation
                )
            }
            // 注：govSubsidy/govFine/scholarship 在后续子系统计算后再录入（closeMonth前）
            // Bug 23: 招生宣传费（每日扣款的月度近似）
            expMarketing = com.arktools.xiaozhang.domain.model.MarketingCalculator.getDailyCost(school.marketingCampaigns) * 30.0
            if (expMarketing > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.MARKETING, expMarketing
                )
            }
            // Bug 29: 季节活动费（每日结算的月度累计）
            val seasonalExpenses = seasonalActivityManager.consumeMonthlyExpenses().toDouble() / 10000.0
            if (seasonalExpenses > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.ACTIVITY_COST, seasonalExpenses
                )
            }
            // closeMonth 延迟到政府补贴和奖学金计算之后（见下方）

            // 家长满意度系统月度推进（需要有学生才运作——没学生就没家长）
            // 复用月结算顶部的缓存教师数据
            val cachedTeachersForMonth = allTeachersCache
            val cachedActiveStudentsForMonth = studentRepository.getActiveStudents()

            val avgSatisfaction: Float
            // 设施安全评分：综合旧设施 condition、新校区建筑维护度、校区等级，修理后会真实提升
            val facilityCondition = run {
                val oldFacilityAvg = if (school.facilities.isNotEmpty()) {
                    school.facilities.map { it.condition }.average().toFloat()
                } else 100f
                val completedZones = campusExpansionManager.state.value.zones.filter { it.isCompleted }
                val zoneMaintAvg = if (completedZones.isNotEmpty()) {
                    completedZones.map { it.maintenanceLevel }.average().toFloat()
                } else 100f
                val baseScore = (school.campusLevel * 10f).coerceAtMost(100f)
                (oldFacilityAvg * 0.30f + zoneMaintAvg * 0.35f + baseScore * 0.35f).coerceIn(0f, 100f)
            }
            if (studentCount > 0) {
                avgSatisfaction = if (cachedActiveStudentsForMonth.isNotEmpty()) {
                    cachedActiveStudentsForMonth.map { it.satisfaction.toFloat() }.average().toFloat()
                } else 70f
                val teacherAvgLoyalty = if (cachedTeachersForMonth.isNotEmpty()) {
                    cachedTeachersForMonth.map { it.loyalty.toFloat() }.average().toFloat()
                } else 60f
                // 计算学业成绩指标（学生平均学业分，反映教学质量）
                val avgAcademicPerformance = if (cachedActiveStudentsForMonth.isNotEmpty()) {
                    // 使用 semesterMastery（实时掌握度）和 academicScore（考试成绩）综合
                    cachedActiveStudentsForMonth.map { s ->
                        val mastery = s.semesterMastery.coerceIn(0f, 100f)
                        val exam = s.academicScore.coerceIn(0f, 100f)
                        // 有考试成绩时侧重考试分，否则用掌握度
                        if (exam > 0f) exam * 0.7f + mastery * 0.3f else mastery
                    }.average().toFloat()
                } else 50f

                // 计算社团活跃度（有几个社团、社团热情度）
                val clubs = clubManager.clubs.value
                val clubActivityLevel = if (clubs.isNotEmpty()) {
                    val avgEnthusiasm = clubs.map { it.enthusiasm }.average().toFloat()
                    // 社团数量 * 10 + 平均热情度，封顶100
                    (clubs.size * 10f + avgEnthusiasm * 0.5f).coerceAtMost(100f)
                } else 0f

                val parentResult = parentSatisfactionManager.advanceMonth(
                    school.currentYear, school.currentMonth,
                    school.reputation.toLong(), teacherAvgLoyalty, avgSatisfaction, facilityCondition,
                    intensitySatisfactionPenalty = teachingManager.config.intensity.satisfactionPenalty,
                    intensityComplaintRate = teachingManager.config.intensity.parentComplaintRate,
                    academicPerformance = avgAcademicPerformance,
                    clubActivityLevel = clubActivityLevel
                )
                if (parentResult.reputationImpact != 0) {
                    if (parentResult.reputationImpact > 0) {
                        schoolRepository.addReputation(parentResult.reputationImpact.toLong())
                    } else {
                        schoolRepository.deductReputation(-parentResult.reputationImpact.toLong())
                    }
                }
                parentResult.newComplaints.forEach { complaint ->
                    emitEvent(GameEvent.NegativeEvent(
                        title = "家长投诉: ${complaint.type.displayName}",
                        message = complaint.description,
                        penaltyCash = 0.0,
                        penaltyReputation = complaint.severity.toLong()
                    ), school)
                }
            } else {
                avgSatisfaction = 70f
            }

            // 意见箱月度推进：根据实际学校状态生成建议
            val teacherAvgLoyaltyForSuggestion = if (cachedTeachersForMonth.isNotEmpty()) {
                cachedTeachersForMonth.map { it.loyalty }.average().toFloat()
            } else 75f
            suggestionBoxManager.advanceMonth(
                students = cachedActiveStudentsForMonth,
                teachers = cachedTeachersForMonth,
                facilities = school.facilities,
                teacherAvgSkill = if (cachedTeachersForMonth.isNotEmpty()) {
                    cachedTeachersForMonth.map { it.teaching }.average().toFloat()
                } else 50f,
                avgStudentSatisfaction = avgSatisfaction,
                avgTeacherLoyalty = teacherAvgLoyaltyForSuggestion,
                schoolCash = school.cash,
                currentYear = school.currentYear,
                currentMonth = school.currentMonth
            )
            // 应用忽略建议的惩罚
            val suggestionPenalties = suggestionBoxManager.getMonthlyPenalties()
            suggestionPenalties.forEach { penalty ->
                when (penalty.submitterType) {
                    com.arktools.xiaozhang.domain.suggestion.SubmitterType.STUDENT -> {
                        studentRepository.adjustStudentSatisfaction(
                            penalty.submitterId,
                            -penalty.penaltyAmount
                        )
                    }
                    com.arktools.xiaozhang.domain.suggestion.SubmitterType.TEACHER -> {
                        teacherRepository.adjustLoyalty(
                            penalty.submitterId,
                            -penalty.penaltyAmount.toInt()
                        )
                    }
                }
            }
            suggestionBoxManager.cleanupOldSuggestions(school.currentYear, school.currentMonth)

            // 采纳建议奖励：每采纳一条建议，全校满意度+1，声誉+3
            val resolvedCount = suggestionBoxManager.consumeResolvedCount()
            if (resolvedCount > 0) {
                val repBonus = resolvedCount * 3L
                schoolRepository.addReputation(repBonus)
                // 提升全校学生满意度（每条+1，上限+5）
                val satBoost = resolvedCount.coerceAtMost(5).toFloat()
                studentRepository.adjustActiveStudentSatisfaction(satBoost)
            }

            // 政府评估督导月度推进
            val teacherAvgSkill = if (cachedTeachersForMonth.isNotEmpty()) {
                cachedTeachersForMonth.map { it.averageSkill }.average().toFloat()
            } else 50f
            val govResult = governmentInspectionManager.advanceMonth(
                school.currentYear, school.currentMonth,
                school.reputation.toLong(), cachedTeachersForMonth.size, teacherAvgSkill,
                cachedActiveStudentsForMonth.size, avgSatisfaction, facilityCondition,
                school.cash, monthlyRevenue,
                employmentRate = employmentResult.currentEmploymentRate,
                schoolLevel = school.campusLevel,
                teachingQualityScore = (
                    teachingManager.config.overallQuality(teacherAvgSkill) +
                        academicConferenceManager.getResearchScore() / 20f
                    ).coerceAtMost(100f),
                weeklyPEHours = teachingManager.config.weeklyPEHours
            )
            if (govResult.subsidy > 0) {
                schoolRepository.addCash(govResult.subsidy)
                incGovSubsidy += govResult.subsidy
                emitEvent(GameEvent.PositiveEvent(
                    title = "政府补贴",
                    message = "获得年度补贴 ¥${String.format("%,.0f", govResult.subsidy)}",
                    bonusCash = 0.0,  // 效果已在上方直接应用，事件仅作通知
                    bonusReputation = 0
                ), school)
            }
            if (govResult.fine > 0) {
                schoolRepository.deductCash(govResult.fine)
                expGovFine += govResult.fine
                emitEvent(GameEvent.NegativeEvent(
                    title = "政府罚款",
                    message = "因评估不达标被罚款 ¥${String.format("%,.0f", govResult.fine)}",
                    penaltyCash = 0.0,  // 效果已在上方直接应用，事件仅作通知
                    penaltyReputation = 5.toLong()
                ), school)
            }
            if (govResult.newGrade != null) {
                emitEvent(GameEvent.PositiveEvent(
                    title = "学校评级: ${govResult.newGrade.displayName}",
                    message = "最新评估结果为${govResult.newGrade.displayName}级",
                    bonusCash = 0.0,
                    bonusReputation = 0L
                ), school)
                schoolRepository.addReputation(govResult.newGrade.reputationBonus.toLong())
            }
            // 处理政府督导的就业加成（传递给就业市场下个月使用）
            if (govResult.employmentBoostFactor != 1.0f) {
                employmentMarket.setGovernmentBoostFactor(govResult.employmentBoostFactor)
            }
            // 处理招生限制（D/C评级时限制招生人数）
            if (govResult.enrollmentCap > 0) {
                emitEvent(GameEvent.NegativeEvent(
                    title = "招生限制令",
                    message = "因评估不达标，教育局限制学校招生上限为${govResult.enrollmentCap}人",
                    penaltyCash = 0.0,
                    penaltyReputation = 3
                ), school)
            }

            // 奖学金制度月度推进（需要有学生才运作）
            if (studentCount > 0) {
                val avgGpa = if (cachedActiveStudentsForMonth.isNotEmpty()) {
                    (cachedActiveStudentsForMonth.map { it.satisfaction.toFloat() / 25f }.average().toFloat()).coerceIn(1.0f, 4.0f)
                } else 2.5f
                val scholarshipResult = scholarshipManager.advanceMonth(
                    school.currentYear, school.currentMonth,
                    studentCount, avgGpa, school.reputation.toLong()
                )
                if (scholarshipResult.expenses > 0) {
                    schoolRepository.deductCash(scholarshipResult.expenses)
                    expScholarship += scholarshipResult.expenses
                }
                if (scholarshipResult.newRecipients > 0) {
                    emitEvent(GameEvent.PositiveEvent(
                        title = "奖学金颁发",
                        message = "本期共${scholarshipResult.newRecipients}名学生获得奖学金，总额 ¥${String.format("%,.0f", scholarshipResult.expenses)}",
                        bonusCash = 0.0,
                        bonusReputation = scholarshipResult.newRecipients.toLong()
                    ), school)
                }
            }

            // === 财务报表结算（在所有收支计算完成后关闭月报）===
            if (incGovSubsidy > 0) {
                financialReportManager.recordIncome(
                    com.arktools.xiaozhang.domain.finance.IncomeCategory.GOVERNMENT_SUBSIDY, incGovSubsidy
                )
            }
            if (expGovFine > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.OTHER_EXPENSE, expGovFine
                )
            }
            if (expScholarship > 0) {
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.OTHER_EXPENSE, expScholarship
                )
            }
            // Bug fix: 将所有额外支出累加到 monthlyExpenses，使 netProfit 反映真实总支出
            // （之前 monthlyExpenses 只包含薪资+租金+教学，导致净利润虚高）
            monthlyExpenses += expClubMonthly + expCareerProgram + expLifeExpenses +
                    expMaintenance + expConference + expClubActivity + expTeacherDev +
                    expMarketing + expScholarship + expGovFine + seasonalExpenses

            // 校友捐赠计入收入
            val totalMonthlyIncome = monthlyRevenue + incAlumniDonation + incGovSubsidy

            financialReportManager.closeMonth(school.currentYear, school.currentMonth, school.cash)

            // 财务报表结算完成后，持久化最新状态（避免重启后丢失本月收支记录）
            schoolRepository.mutateSchool { latest ->
                latest.financialReportJson = protectedManagerJson(
                    "financialReportJson",
                    latest.financialReportJson,
                    financialReportManager::toJson
                )
                true
            }

            // === 校长个人系统月度更新 ===
            val principalForMonth = _principal.value

            // v2.8: 校长月薪发放（按学校等级，进入个人资金）
            // 停职期间不发工资（合理：现实中停职也暂停发薪）
            if (!principalForMonth.isSuspended && !principalForMonth.isArrested) {
                val principalSalary = GameBalanceConfig.getPrincipalMonthlySalary(school.campusLevel)
                principalForMonth.personalFunds += principalSalary
                // 校长薪资从学校公款支出
                schoolRepository.deductCash(principalSalary)
                monthlyExpenses += principalSalary
                financialReportManager.recordExpense(
                    com.arktools.xiaozhang.domain.finance.ExpenseCategory.TEACHER_SALARY, principalSalary
                )
            }

            // 腐败系统月度风险检查：School/Principal/罚款/声誉同行持久化。
            if (!principalForMonth.isSuspended && !principalForMonth.isArrested) {
                val investigation = processMonthlyCorruption(principalForMonth)
                if (investigation != null) {
                    val investigationEvent = investigation.event
                    val schoolFine = investigation.schoolFine
                    val latestSchool = investigation.school
                    monthlyExpenses += schoolFine

                    val gameEvent = when (investigationEvent.result) {
                        InvestigationResult.ARRESTED -> GameEvent.NegativeEvent(
                            title = "🚔 校长被逮捕！",
                            message = investigationEvent.message,
                            penaltyCash = 0.0,
                            penaltyReputation = 0L
                        )
                        InvestigationResult.DEMOTION -> GameEvent.NegativeEvent(
                            title = "⚠️ 校长被免职降级！",
                            message = investigationEvent.message,
                            penaltyCash = 0.0,
                            penaltyReputation = 0L
                        )
                        InvestigationResult.SUSPENSION -> GameEvent.NegativeEvent(
                            title = "纪检调查: 停职反省",
                            message = investigationEvent.message,
                            penaltyCash = 0.0,
                            penaltyReputation = 0L
                        )
                        InvestigationResult.FINE -> GameEvent.NegativeEvent(
                            title = "纪检调查: 罚款处分",
                            message = investigationEvent.message,
                            penaltyCash = 0.0,
                            penaltyReputation = 0L
                        )
                        InvestigationResult.WARNING -> GameEvent.NegativeEvent(
                            title = "纪检警告",
                            message = investigationEvent.message,
                            penaltyCash = 0.0,
                            penaltyReputation = 0L
                        )
                        InvestigationResult.CLEARED -> GameEvent.PositiveEvent(
                            title = "纪检调查通过",
                            message = investigationEvent.message,
                            bonusCash = 0.0,
                            bonusReputation = 0
                        )
                    }
                    emitEvent(gameEvent, latestSchool)
                    _principal.value = principalForMonth.copy(
                        version = principalForMonth.version + 1
                    )

                    if (investigationEvent.result == InvestigationResult.ARRESTED) {
                        handlePrincipalArrest(latestSchool)
                    } else if (
                        investigationEvent.result == InvestigationResult.SUSPENSION ||
                        investigationEvent.result == InvestigationResult.DEMOTION
                    ) {
                        requireDisciplinaryRecovery(
                            title = if (
                                investigationEvent.result == InvestigationResult.DEMOTION
                            ) {
                                "校长被免职降级"
                            } else {
                                "校长被停职调查"
                            },
                            message = investigationEvent.message +
                                "\n\n观看完整视频并接受纪律教育后，才可恢复学校经营。"
                        )
                    }
                }
            }

            // 人脉系统月度衰减
            connectionManager.monthlyDecay(principalForMonth)

            // 派系系统月度更新：有选项的事件映射为 ChoiceEvent，声誉在选择后结算
            val factionEvents = factionManager.monthlyUpdate(principalForMonth, school)
            factionEvents.forEach { facEvent ->
                pendingFactionEvents[facEvent.id] = facEvent
                val gameEvent = if (facEvent.choices.isNotEmpty()) {
                    val reputationHint = if (facEvent.reputationImpact != 0L) {
                        val sign = if (facEvent.reputationImpact > 0) "+" else ""
                        "\n\n（处理结果将影响学校声誉 ${sign}${facEvent.reputationImpact}）"
                    } else ""
                    GameEvent.ChoiceEvent(
                        title = facEvent.title,
                        message = facEvent.message + reputationHint,
                        choices = facEvent.choices.mapIndexed { index, choice ->
                            EventChoice(
                                text = choice.text,
                                consequence = EventConsequence(
                                    reputationChange = facEvent.reputationImpact,
                                    factionChoiceAction = com.arktools.xiaozhang.domain.model.FactionChoiceAction(
                                        eventId = facEvent.id,
                                        choiceIndex = index
                                    )
                                )
                            )
                        }
                    )
                } else if (facEvent.reputationImpact < 0) {
                    GameEvent.NegativeEvent(
                        title = facEvent.title,
                        message = facEvent.message,
                        penaltyCash = 0.0,
                        penaltyReputation = 0L
                    )
                } else {
                    GameEvent.PositiveEvent(
                        title = facEvent.title,
                        message = facEvent.message,
                        bonusCash = 0.0,
                        bonusReputation = 0L
                    )
                }
                emitEvent(gameEvent, school)
                // 无选项事件的声誉立即结算；有选项事件交给玩家选择后的 consequence
                if (facEvent.choices.isEmpty() && facEvent.reputationImpact != 0L) {
                    schoolRepository.addReputation(facEvent.reputationImpact)
                }
            }

            // 更新 Principal StateFlow
            _principal.value = principalForMonth

            // ══════════════════════════════════════════════
            // 经营压力系统（PressureSystemManager）月度处理
            // ══════════════════════════════════════════════
            val absMonth = (school.currentYear - school.foundedYear) * 12 + school.currentMonth
            // 复用月度缓存的教师和学生数据（压力系统不需要实时最新数据）
            val allTeachersForPressure = cachedTeachersForMonth.filter { it.isWorking }
            val activeStudentsForPressure = cachedActiveStudentsForMonth

            // P0-1: 教师涨薪需求检查
            val raiseRequests = pressureSystemManager.checkRaiseRequests(
                allTeachersForPressure,
                absMonth
            )
            for (req in raiseRequests) {
                deferEvent(GameEvent.ChoiceEvent(
                    title = "教师加薪请求",
                    message = "${req.teacherName}提出加薪要求。\n" +
                        "当前月薪：${String.format("%.2f", req.currentSalary)}万元\n" +
                        "期望涨幅：+${req.raisePercent}%（+${String.format("%.2f", req.requestedRaise)}万/月）\n\n" +
                        "若拒绝，该教师忠诚度将大幅下降，可能离职。",
                    choices = listOf(
                        EventChoice("同意加薪", EventConsequence(
                            cashChange = 0.0,
                            teacherAction = com.arktools.xiaozhang.domain.model.TeacherAction.ApproveRaise(req.teacherId, req.raisePercent)
                        )),
                        EventChoice("拒绝加薪", EventConsequence(
                            teacherAction = com.arktools.xiaozhang.domain.model.TeacherAction.RejectRaise(req.teacherId)
                        ))
                    )
                ))
            }

            // P0-2: 设施维修突发事件
            val maintenanceEvents = pressureSystemManager.checkFacilityMaintenance(
                school.facilities,
                school.campusLevel
            )
            for (mEvent in maintenanceEvents) {
                deferEvent(GameEvent.ChoiceEvent(
                    title = "设施维修：${mEvent.facilityType.displayName}",
                    message = "${mEvent.description}\n\n维修费用：${String.format("%.1f", mEvent.repairCost)}万元\n不修则设施状态持续恶化，可能停用。",
                    choices = listOf(
                        EventChoice("立即维修（${String.format("%.1f", mEvent.repairCost)}万）", EventConsequence(cashChange = -mEvent.repairCost)),
                        EventChoice("暂不处理", EventConsequence(reputationChange = -2))
                    )
                ))
            }

            // P0-3: 季度税费（3/6/9/12月扣缴）—— 重试时跳过
            try {
            if (!isRetrySettlement && pressureSystemManager.isQuarterEnd(school.currentMonth)) {
                val quarterlyTax = pressureSystemManager.calculateQuarterlyTax(monthlyRevenue * 3, school.campusLevel)
                if (quarterlyTax > 0) {
                    schoolRepository.deductCash(quarterlyTax)
                    monthlyExpenses += quarterlyTax  // Bug fix: 计入月度总支出
                    financialReportManager.recordExpense(
                        com.arktools.xiaozhang.domain.finance.ExpenseCategory.OTHER_EXPENSE, quarterlyTax
                    )
                    deferEvent(GameEvent.NegativeEvent(
                        title = "季度税费缴纳",
                        message = "本季度应缴税费${String.format("%.1f", quarterlyTax)}万元已自动扣除。",
                        penaltyCash = 0.0,  // 效果已在上方直接应用，事件仅作通知
                        penaltyReputation = 0
                    ))
                }
            }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P0-3 quarterlyTax failed", e)
                throw e
            }

            // P0-4: 学生退费检查
            val withdrawals = try {
                val tuitionPerStudent = monthlyRevenue / (activeStudentsForPressure.size.coerceAtLeast(1))
                val ws = pressureSystemManager.checkStudentWithdrawals(activeStudentsForPressure, tuitionPerStudent)
                val completedWithdrawals = mutableListOf<PressureSystemManager.StudentWithdrawal>()
                for (w in ws) {
                    if (!schoolRepository.commitStudentWithdrawal(
                            studentId = w.studentId,
                            dropYear = school.currentYear,
                            dropMonth = school.currentMonth,
                            refundAmount = w.refundAmount,
                            reputationPenalty = 2L
                        )
                    ) {
                        continue
                    }
                    completedWithdrawals.add(w)
                    monthlyExpenses += w.refundAmount  // Bug fix: 退费计入月度总支出
                    deferEvent(GameEvent.NegativeEvent(
                        title = "学生退学",
                        message = "${w.studentName}退学退费。${w.reason}\n退费金额：${String.format("%.2f", w.refundAmount)}万元",
                        penaltyCash = 0.0,  // 退款已在跨表事务中扣除，事件仅作通知
                        penaltyReputation = 0  // 声誉已在同一事务中扣除
                    ))
                }
                completedWithdrawals
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P0-4 studentWithdrawals failed", e)
                throw e
            }

            // P0-5: 竞争对手挖人 / 倦怠离职 / 合同续约
            val burnouts = try {
                val poachAttempts = pressureSystemManager.checkPoachingAttempts(
                    allTeachersForPressure, school.reputation, school.campusLevel
                )
                for (poach in poachAttempts) {
                    val poachTeacher = allTeachersForPressure.find { it.id == poach.teacherId }
                    val poachDisplay = formatTeacherWithSubject(poach.teacherName, poachTeacher)
                    val offered = String.format("%.2f", poach.offeredSalary)
                    val currentSalary = String.format("%.2f", poach.retainCost * 0.8)
                    val retainCost = String.format("%.2f", poach.retainCost)
                    deferEvent(GameEvent.ChoiceEvent(
                        title = "教师被挖角！",
                        message = "${poach.competitorName}向${poachDisplay}（${poach.teacherLevel.name}级）开出${offered}万/月高薪！\n" +
                            "当前薪资：${currentSalary}万/月\n\n" +
                            "若要留人，需将薪资提高到${retainCost}万/月。",
                        choices = listOf(
                            EventChoice(
                                "加薪留人（薪资调至${retainCost}万/月）",
                                EventConsequence(
                                    cashChange = 0.0,
                                    teacherAction = com.arktools.xiaozhang.domain.model.TeacherAction.RenewContract(
                                        poach.teacherId,
                                        poach.retainCost
                                    )
                                )
                            ),
                            EventChoice(
                                "放人离开",
                                EventConsequence(
                                    reputationChange = -5,
                                    teacherAction = TeacherAction.ApproveResignation(poach.teacherId)
                                )
                            )
                        )
                    ))
                }

                // 倦怠离职（教师直接离开）
                val bos = pressureSystemManager.checkBurnoutResignations(allTeachersForPressure)
                for (bo in bos) {
                    val burnoutTeacher = allTeachersForPressure.find { it.id == bo.teacherId }
                    val burnoutDisplay = formatTeacherWithSubject(bo.teacherName, burnoutTeacher)
                    teacherRepository.fireTeacher(bo.teacherId)
                    pressureSystemManager.expireContract(bo.teacherId)
                    schoolRepository.deductReputation(3)
                    deferEvent(GameEvent.NegativeEvent(
                        title = "教师离职",
                        message = "${burnoutDisplay}提交辞呈：${bo.reason}",
                        penaltyCash = 0.0,
                        penaltyReputation = 0  // 声誉已在上方直接扣减
                    ))
                }
                if (bos.isNotEmpty()) {
                    refreshTimetablesForTeacherChange()
                }

                // 合同到期续约
                val contractRenewals = pressureSystemManager.checkContractExpiry(allTeachersForPressure, absMonth)
                for (renewal in contractRenewals) {
                    val renewalTeacher = allTeachersForPressure.find { it.id == renewal.teacherId }
                    if (renewalTeacher == null) continue
                    val renewalDisplay = formatTeacherWithSubject(renewal.teacherName, renewalTeacher)
                    val currentSalary = String.format("%.2f", renewal.currentSalary)
                    val renewalDemand = String.format("%.2f", renewal.renewalDemand)
                    deferEvent(GameEvent.ChoiceEvent(
                        title = "教师合同到期",
                        message = "${renewalDisplay}合同到期，提出续约条件：\n" +
                            "当前薪资：${currentSalary}万/月\n" +
                            "续约要求：${renewalDemand}万/月（+${renewal.demandPercent}%）\n\n" +
                            "不续约则教师离校。",
                        choices = listOf(
                            EventChoice(
                                "同意续约",
                                EventConsequence(
                                    cashChange = 0.0,
                                    teacherAction = com.arktools.xiaozhang.domain.model.TeacherAction.RenewContract(
                                        renewal.teacherId,
                                        renewal.renewalDemand
                                    )
                                )
                            ),
                            EventChoice(
                                "不续约，放人",
                                EventConsequence(
                                    reputationChange = -3,
                                    teacherAction = com.arktools.xiaozhang.domain.model.TeacherAction.DeclineRenewal(
                                        renewal.teacherId
                                    )
                                )
                            )
                        )
                    ))
                }
                bos
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P0-5 poach/burnout/contract failed", e)
                throw e
            }

            // P1-1: 家长投诉升级
            val escalation = try {
                val avgSatForComplaint = if (activeStudentsForPressure.isNotEmpty()) {
                    activeStudentsForPressure.map { it.satisfaction }.average().toFloat()
                } else 70f
                val complaints = pressureSystemManager.generateComplaints(
                    avgSatForComplaint, activeStudentsForPressure.size, school.campusLevel
                )
                if (complaints.isNotEmpty()) {
                    val totalRepLoss = complaints.sumOf { it.reputationPenalty }
                    schoolRepository.deductReputation(totalRepLoss)
                }
                val esc = pressureSystemManager.checkComplaintEscalation(school.campusLevel)
                if (esc != null) {
                    schoolRepository.deductReputation(esc.reputationPenalty)
                    emitEvent(GameEvent.NegativeEvent(
                        title = when (esc.level) {
                            PressureSystemManager.EscalationLevel.MEDIA_EXPOSURE -> "媒体曝光！"
                            PressureSystemManager.EscalationLevel.EDUCATION_BUREAU -> "教育局介入！"
                            else -> "投诉升级"
                        },
                        message = esc.description,
                        penaltyCash = 0.0,
                        penaltyReputation = 0  // 声誉已在上方直接扣减，事件仅作通知
                    ), school)
                }
                esc
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P1-1 complaints failed", e)
                throw e
            }

            // P2-1: 学生行为事件
            try {
                val behaviorEvent = pressureSystemManager.generateBehaviorEvent(
                    activeStudentsForPressure.size, school.campusLevel
                )
                if (behaviorEvent != null) {
                    val choices = behaviorEvent.choices.map { choice ->
                        EventChoice(choice.text, EventConsequence(
                            cashChange = -choice.costWan,
                            reputationChange = choice.reputationChange
                        ))
                    }
                    deferEvent(GameEvent.ChoiceEvent(
                        title = "学生事件：${behaviorEvent.type.displayName}",
                        message = behaviorEvent.description,
                        choices = choices
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P2-1 behaviorEvent failed", e)
                throw e
            }

            // P2-2: 设施联动惩罚
            try {
                val facilityPenalties = pressureSystemManager.checkFacilityPenalties(
                    school.facilities, activeStudentsForPressure.size
                )
                for (penalty in facilityPenalties) {
                    // 设施惩罚降低全体学生满意度
                    if (penalty.satisfactionPenalty > 0f && activeStudentsForPressure.isNotEmpty()) {
                        studentRepository.adjustActiveStudentSatisfaction(
                            -penalty.satisfactionPenalty
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P2-2 facilityPenalties failed", e)
                throw e
            }

            // P3-2: 财务健康检查（netProfit 使用包含所有收支的完整计算）
            val netProfit = totalMonthlyIncome - monthlyExpenses
            val financialWarning = try {
                val fw = pressureSystemManager.checkFinancialHealth(school.cash, netProfit)
                if (fw.level != PressureSystemManager.WarningLevel.NONE) {
                    emitEvent(GameEvent.NegativeEvent(
                        title = when (fw.level) {
                            PressureSystemManager.WarningLevel.IMMINENT_BANKRUPTCY -> "破产警告！"
                            PressureSystemManager.WarningLevel.CRITICAL_DEBT -> "严重负债！"
                            PressureSystemManager.WarningLevel.DEBT -> "财务预警"
                            PressureSystemManager.WarningLevel.LOSS_STREAK -> "持续亏损"
                            else -> "财务警告"
                        },
                        message = fw.message,
                        penaltyCash = 0.0,
                        penaltyReputation = 0
                    ), school)
                }
                fw
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P3-2 financialHealth failed", e)
                throw e
            }

            // P3-1: 记录月报摘要
            try {
                val pressureBrief = PressureSystemManager.MonthlyBrief(
                    year = school.currentYear,
                    month = school.currentMonth,
                    revenue = totalMonthlyIncome,
                    expenses = monthlyExpenses,
                    netProfit = netProfit,
                    studentChange = -(withdrawals.size),
                    teacherChange = -(burnouts.size),
                    reputationChange = 0L,
                    majorEvents = buildList {
                        if (withdrawals.isNotEmpty()) add("${withdrawals.size}名学生退学")
                        if (burnouts.isNotEmpty()) add("${burnouts.size}名教师辞职")
                        if (escalation != null) add(escalation.description.take(20))
                        if (financialWarning.level != PressureSystemManager.WarningLevel.NONE) add(financialWarning.message.take(20))
                    }
                )
                pressureSystemManager.recordMonthlyBrief(pressureBrief)
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "P3-1 monthlyBrief failed", e)
                throw e
            }

            recordMonthlyStats(school, totalMonthlyIncome, monthlyExpenses)

            // 统计数据记录后立即持久化（修复：之前toJson在recordMonth之前导致当月数据丢失）
            try {
                schoolRepository.mutateSchool { latest ->
                    latest.statisticsJson = protectedManagerJson(
                        "statisticsJson",
                        latest.statisticsJson,
                        StatisticsManager::toJson
                    )
                    true
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "Statistics persist after record failed", e)
                throw e
            }

            // 发出月净收入信号（用于"看广告双倍收益"弹窗）
            // 显示净收入而非毛收入，避免玩家看到"+30万"实际却被维护费吃掉大半
            if (netProfit > 0) {
                try {
                    _monthlyRevenueBonus.emit(netProfit)
                } catch (e: Exception) {
                    android.util.Log.e("GameEngine", "monthlyRevenueBonus emit failed", e)
                }
            }

            // 里程碑检查
            val updatedSchool = schoolRepository.getSchool() ?: school
            try {
                val completedStages = milestoneManager.checkMilestones(updatedSchool)
                completedStages.forEach { completion ->
                    // 发放里程碑奖励
                    if (completion.rewardCash > 0) {
                        schoolRepository.addCash(completion.rewardCash)
                    }
                    if (completion.rewardReputation > 0) {
                        schoolRepository.addReputation(completion.rewardReputation.toLong())
                    }
                    emitEvent(com.arktools.xiaozhang.domain.model.GameEvent.MilestoneEvent(
                        title = "里程碑达成",
                        message = "${completion.milestoneTitle} - ${completion.stageDescription}！奖励：${completion.rewardCash.toInt()}万 声誉+${completion.rewardReputation}",
                        milestoneType = com.arktools.xiaozhang.domain.model.MilestoneType.MARKET_CAP_MILESTONE
                    ), updatedSchool)
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "Milestone check failed", e)
                throw e
            }

            // 竞争对手月度更新
            try {
                val competitorEvents = competitorEngine.monthlyUpdate(updatedSchool, updatedSchool.currentYear)
                competitorEvents.forEach { compEvent ->
                    val gameEvent = compEvent.toGameEvent()
                    if (gameEvent != null) {
                        // 先提交业务效果，再发送纯通知，避免通知成功但数据写入失败。
                        applyCompetitorEventEffect(compEvent)
                        emitEvent(gameEvent, updatedSchool)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "Competitor monthly update failed", e)
                throw e
            }

            // 仅清理具有明确终态年份的旧记录；NULL 历史记录保留，避免误删。
            studentRepository.cleanupOldRecords(
                beforeYear = school.currentYear - 5
            )

            val finalHeadTeacherMap = _classes.value
                .filter { it.headTeacherId != null }
                .associate { it.id to it.headTeacherId!! }
            val finalHeadTeacherJson = if (finalHeadTeacherMap.isEmpty()) {
                ""
            } else {
                org.json.JSONObject(finalHeadTeacherMap).toString()
            }
            val finalTierMap = _classes.value.associate {
                it.id to it.classTier.name
            }
            val finalClassTierJson = if (finalTierMap.isEmpty()) {
                ""
            } else {
                org.json.JSONObject(finalTierMap).toString()
            }
            checkNotNull(schoolRepository.mutateSchool { latest ->
                writeManagerJsonFields(latest)
                writeClassJsonFields(
                    latest,
                    finalHeadTeacherJson,
                    finalClassTierJson
                )
                latest.lastMonthlySettlementYear = school.currentYear
                latest.lastMonthlySettlementMonth = school.currentMonth
                true
            }) { "Monthly settlement final state commit failed" }

            // 失败检测（月度结算最后执行）
            try {
                val latestSchool = schoolRepository.getSchool() ?: updatedSchool
                val crisisResult = gameOverDetector.monthlyCheck(latestSchool)
                handleCrisisResult(crisisResult, latestSchool)
            } catch (e: Exception) {
                android.util.Log.e("GameEngine", "Crisis detection failed", e)
            }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e(
                    "GameEngine",
                    "Monthly settlement failed; will retry on next tick",
                    e
                )
                pendingMonthlySettlementRetry = true
                _gameDaySignal.tryEmit(Unit)
                return
            }
            if (isPaused) {
                _gameDaySignal.tryEmit(Unit)
                return
            }
        }

        val event = eventGenerator.generateEvent(school, _principal.value, studentRepository.getActiveStudentCount())
        if (event != null) {
            emitEvent(event, school)
        }

        // 突发危机剧本系统每日检查
        val daysSinceStart = (school.currentYear - school.foundedYear) * 360 +
            (school.currentMonth - 1) * 30 + school.currentDay
        val crisisEvent = crisisScenarioManager.dailyCheck(school, daysSinceStart)
        if (crisisEvent != null) {
            emitEvent(crisisEvent, school)
        }

        // Check achievements monthly (day 1) to reduce overhead
        if (school.currentDay == 1) {
            achievementManager.checkAchievements(school)
        }

        // 发射游戏日推进信号，供各 ViewModel 监听刷新
        try {
            _gameDaySignal.tryEmit(Unit)
        } catch (_: Exception) { }
    }

    /**
     * 生成并应用股票市场事件，同时通过 GameEvent 通知玩家
     */
    private suspend fun updateStockMarketEvents(school: School) {
        val activeCount = stockRepository.getActiveEvents().size
        val stockEvent = stockEventGenerator.tryGenerateEvent(school, activeCount) ?: return

        // 应用事件到股票系统
        stockRepository.applyMarketEvent(stockEvent)

        // 只有重大市场波动（影响>8%）才弹通知给玩家，减少打扰
        if (Math.abs(stockEvent.priceImpactPercent) >= 8.0) {
            val gameEvent = if (stockEvent.priceImpactPercent >= 0) {
                GameEvent.PositiveEvent(
                    title = stockEvent.title,
                    message = stockEvent.message,
                    bonusCash = 0.0,
                    bonusReputation = 0
                )
            } else {
                GameEvent.NegativeEvent(
                    title = stockEvent.title,
                    message = stockEvent.message,
                    penaltyCash = 0.0,
                    penaltyReputation = 0
                )
            }
            emitEvent(gameEvent, school)
        }
    }

    private suspend fun updateActiveCourses(school: School) {
        val activeCourses = courseRepository.getActiveCourses()
        activeCourses.forEach { course ->
            // 兼容旧存档：如果有课程卡在TESTING状态，自动开课
            if (course.status == CourseStatus.TESTING) {
                val released = courseRepository.releaseCourse(
                    courseId = course.id,
                    expectedStatus = CourseStatus.TESTING,
                    releaseDate = System.currentTimeMillis(),
                    releaseYear = school.currentYear,
                    releaseMonth = school.currentMonth
                )
                if (released) {
                    emitEvent(GameEvent.PositiveEvent(
                        title = "课程开课",
                        message = "《${course.name}》已自动开课招生！",
                        bonusCash = 0.0,
                        bonusReputation = 0L
                    ), school)
                    schoolRepository.addReputation(5)
                }
            } else {
                updateCourseProgress(course, school)
            }
        }
    }

    private suspend fun updateCourseProgress(course: CourseProject, school: School) {
        val teachers = teacherRepository.getTeachers().filter { it.id in course.teamIds }
        val avgSkill = if (teachers.isNotEmpty()) {
            teachers.map { it.averageSkill }.average().toFloat()
        } else {
            // 没有分配教师时，使用学校所有教师的平均技能
            val allTeachers = teacherRepository.getTeachers()
            if (allTeachers.isNotEmpty()) {
                allTeachers.map { it.averageSkill }.average().toFloat()
            } else 30f // 没有教师时使用很低的技能值
        }

        val methodBonus = researchRepository.getUnlockedMethodBonus(course.methodIds)

        // Facility bonuses: library boosts research/preparation speed
        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)
        val facilityResearchMultiplier = 1f + facilityBonuses.researchBonus

        // Trait-based preparation speed modifiers
        var traitSpeedMultiplier = 1.0f
        if (teachers.isNotEmpty()) {
            val teamSize = teachers.size
            teachers.forEach { teacher ->
                teacher.traits.forEach { trait ->
                    when (trait) {
                        TeacherTrait.HARDWORKING -> traitSpeedMultiplier += 0.25f / teamSize
                        TeacherTrait.LAZY -> traitSpeedMultiplier -= 0.20f / teamSize
                        TeacherTrait.PERFECTIONIST -> traitSpeedMultiplier -= 0.30f / teamSize  // slower but better quality
                        TeacherTrait.RESEARCHER -> traitSpeedMultiplier -= 0.10f / teamSize     // teaching speed -10%
                        TeacherTrait.INTROVERT -> {
                            // Solo +30%, team -15%
                            if (teamSize == 1) traitSpeedMultiplier += 0.30f
                            else traitSpeedMultiplier -= 0.15f / teamSize
                        }
                        TeacherTrait.IMPATIENT -> traitSpeedMultiplier -= 0.10f / teamSize  // team efficiency -10%
                        TeacherTrait.OUTDATED -> traitSpeedMultiplier -= 0.05f / teamSize   // slightly slower with new methods
                        else -> {}
                    }
                }
            }
            traitSpeedMultiplier = traitSpeedMultiplier.coerceAtLeast(0.3f)
        }

        // BonusType.RESEARCH_SPEED: 已解锁教学方法中所有备课速度加成累加
        val researchSpeedBonus = researchRepository.getUnlockedBonusByType(BonusType.RESEARCH_SPEED)

        // Subject difficulty slows preparation (harder subjects take longer)
        val subjectDifficulty = com.arktools.xiaozhang.domain.model.SubjectConfig.getProfile(course.subject).difficulty
        val dailyProgress = (GameBalanceConfig.DAILY_PROGRESS_BASE +
                avgSkill * GameBalanceConfig.DAILY_PROGRESS_SKILL_FACTOR) *
                (1 + methodBonus * GameBalanceConfig.DAILY_PROGRESS_METHOD_BONUS_MULTIPLIER) *
                facilityResearchMultiplier *
                traitSpeedMultiplier *
                (1f + researchSpeedBonus) /
                subjectDifficulty

        val projectedProgress =
            (course.preparationProgress + dailyProgress).coerceAtMost(100f)
        val shouldComplete = projectedProgress >= 100f
        val designScore = if (shouldComplete) {
            SubjectConfig.calculateDesignScore(
                subject = course.subject,
                theme = course.theme,
                courseType = course.courseType,
                scale = course.scale,
                teacherAvgSkill = avgSkill
            )
        } else {
            course.designScore
        }
        val qualityScore = if (shouldComplete) {
            calculateCourseScore(
                course.copy(designScore = designScore),
                school
            )
        } else {
            course.qualityScore
        }
        val completed = courseRepository.advancePreparation(
            courseId = course.id,
            progressDelta = dailyProgress,
            shouldComplete = shouldComplete,
            designScore = designScore,
            qualityScore = qualityScore,
            releaseDate = System.currentTimeMillis(),
            releaseYear = school.currentYear,
            releaseMonth = school.currentMonth
        )

        if (completed) {
            emitEvent(GameEvent.PositiveEvent(
                title = "课程开课",
                message = "《${course.name}》备课完成，已正式开课招生！评分: ${String.format("%.1f", qualityScore)}",
                bonusCash = 0.0,
                bonusReputation = 0L
            ), school)
            schoolRepository.addReputation(5)
        }
    }

    private suspend fun updateTeacherStatus(school: School) {
        val teachers = teacherRepository.getTeachers()
        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)

        // BonusType.TEACHER_LOYALTY: 已解锁教学方法中所有教师忠诚度加成累加
        val loyaltyBonus = researchRepository.getUnlockedBonusByType(BonusType.TEACHER_LOYALTY)

        // 获取所有活跃课程中分配的教师ID集合（只有实际在教课/备课的教师才累积疲劳）
        val activeCourses = courseRepository.getActiveCourses()
        val teachingTeacherIds = activeCourses.flatMap { it.teamIds }.toSet()

        // 季节活动期间的教师额外疲劳（预计算，避免循环内重复查询）
        val currentActiveActivities = seasonalActivityManager.getActiveActivities()
        val activityFatigueBonus = if (currentActiveActivities.isNotEmpty()) {
            currentActiveActivities.sumOf { activity ->
                val scaleFactor = activity.scale.costMultiplier  // 0.5/1.0/1.8/3.0
                (1 * scaleFactor).toInt().coerceAtLeast(1)
            }
        } else 0

        teachers.forEach { teacher ->
            if (!teacher.isWorking) {
                // Teacher has quit — skip
                return@forEach
            }
            val previousState = teacher.copy()

            if (teacher.isOnVacation) {
                // Vacation recovery: reduce fatigue rapidly
                teacher.fatigue = (teacher.fatigue - 8).coerceAtLeast(0)
                // End vacation when rested enough
                if (teacher.fatigue <= 10) {
                    teacher.isOnVacation = false
                }
                teacherRepository.saveDailyState(previousState, teacher)
                return@forEach
            }

            // === WORKING STATE ===

            // 未分配课程的教师不累积疲劳（闲置状态）
            val isTeaching = teacher.id in teachingTeacherIds
            // 寒暑假期间：即使课程仍在 PREPARING/TESTING 状态，教师也不应累积疲劳
            val isOnBreak = SemesterCalendar.isOnBreak(school.currentMonth)

            // === 新入职保护期：入职60天内不扣忠诚度 ===
            val currentGameDay = school.currentYear.toLong() * 360 + (school.currentMonth - 1) * 30 + school.currentDay
            val daysSinceHire = if (teacher.hireDate > 1_000_000L) {
                999  // 旧数据（真实时间戳）：视为老员工，不享受保护期
            } else if (teacher.hireDate > 0L) {
                (currentGameDay - teacher.hireDate).toInt().coerceAtLeast(0)
            } else {
                999  // hireDate=0：视为老员工，不享受保护期
            }
            val isInProtectionPeriod = daysSinceHire < 60

            // Fatigue: 未授课或寒暑假的教师每天自然恢复疲劳
            if (!isTeaching || isOnBreak) {
                teacher.fatigue = (teacher.fatigue - 2).coerceAtLeast(0)
            }
            // Fatigue accumulation - only for teachers with active courses AND not on break
            val effectivelyTeaching = isTeaching && !isOnBreak
            var fatigueRate = if (effectivelyTeaching) GameBalanceConfig.TEACHER_FATIGUE_DAILY_INCREASE else 0
            if (effectivelyTeaching && TeacherTrait.HARDWORKING in teacher.traits) {
                fatigueRate = (fatigueRate * 1.1f).toInt().coerceAtLeast(1)
            }
            // 教学系统额外负荷：高强度/加课排课策略增加教师疲劳
            if (effectivelyTeaching) {
                val extraLoad = teachingManager.totalTeacherExtraLoad()
                fatigueRate = (fatigueRate * (1f + extraLoad)).toInt().coerceAtLeast(fatigueRate)
            }
            // 季节活动期间教师额外疲劳（筹备+举办都消耗精力）
            if (activityFatigueBonus > 0 && !isOnBreak) {
                fatigueRate += activityFatigueBonus
            }
            // Canteen facility: 双重机制
            // 1) 高负荷时降低疲劳累积率（fatigueRate > 1 时有意义）
            // 2) 提供每日被动恢复（解决基础率=1时食堂完全无效的问题）
            val fatigueReduction = facilityBonuses.fatigueReduction
            val reducedFatigueRate = fatigueRate.toFloat() * (1f - fatigueReduction)
            // 只要原始 fatigueRate > 0，至少保留 1 点疲劳累积（教学必然有消耗）
            val finalFatigueRate = if (fatigueRate > 0) reducedFatigueRate.toInt().coerceAtLeast(1) else 0
            // 食堂被动恢复：利用确定性周期（按天取模）实现分级恢复效果
            // Lv1 (0.15): 每3天恢复1点 → 净增 +0.67/天 → 120天到80（比无食堂80天慢50%）
            // Lv2 (0.30): 每2天恢复1点 → 净增 +0.50/天 → 160天到80（翻倍）
            // Lv3 (0.45): 每天恢复1点   → 净增 +0/天  → 永不自然到80（需要额外负荷才会）
            val canteenRecovery = if (fatigueReduction > 0f && effectivelyTeaching && teacher.fatigue > 0) {
                when {
                    fatigueReduction >= 0.40f -> 1                                      // Lv3: 每天恢复
                    fatigueReduction >= 0.25f -> if (school.currentDay % 2 == 0) 1 else 0  // Lv2: 每2天恢复
                    else -> if (school.currentDay % 3 == 0) 1 else 0                        // Lv1: 每3天恢复
                }
            } else 0
            teacher.fatigue = (teacher.fatigue + finalFatigueRate - canteenRecovery).coerceIn(0, 100)

            // 记录本轮忠诚度变化前的值（用于预警检测）
            val loyaltyBefore = teacher.loyalty

            // Loyalty dynamics — 保护期内跳过所有忠诚度扣除
            if (!isInProtectionPeriod) {
                // 疲劳导致忠诚度下降（只有在教课的教师才可能疲劳超标）
                if (teacher.fatigue > GameBalanceConfig.TEACHER_FATIGUE_LOOSE_THRESHOLD) {
                    var loyaltyLoss = GameBalanceConfig.TEACHER_FATIGUE_LOYALTY_DECREASE
                    // CHARISMATIC teachers resist loyalty loss
                    if (TeacherTrait.CHARISMATIC in teacher.traits) {
                        loyaltyLoss = (loyaltyLoss * 0.5f).toInt().coerceAtLeast(0)
                    }
                    // Garden facility reduces loyalty decay
                    loyaltyLoss = (loyaltyLoss * (1f - facilityBonuses.loyaltyDecayReduction)).toInt().coerceAtLeast(0)
                    // Research bonus reduces loyalty decay
                    loyaltyLoss = (loyaltyLoss * (1f - loyaltyBonus).coerceAtLeast(0.2f)).toInt().coerceAtLeast(0)
                    teacher.loyalty = (teacher.loyalty - loyaltyLoss).coerceAtLeast(0)
                }

                // Low salary causes loyalty drain
                // 闲置教师（未分配课程）不受低薪惩罚——他们还没开始工作
                // 寒暑假期间也不扣（放假期间拿半薪是正常的）
                val schoolAgeDays = ((school.currentYear - school.foundedYear) * 360) +
                    ((school.currentMonth - 1) * 30) + school.currentDay
                if (effectivelyTeaching && schoolAgeDays > 90 && teacher.salary < getMarketSalary(teacher)) {
                    var salaryLoyaltyLoss = if (TeacherTrait.GREEDY in teacher.traits)
                        (GameBalanceConfig.TEACHER_LOW_SALARY_LOYALTY_DECREASE * 1.5f).toInt()
                    else GameBalanceConfig.TEACHER_LOW_SALARY_LOYALTY_DECREASE
                    // Research bonus reduces salary-related loyalty decay
                    salaryLoyaltyLoss = (salaryLoyaltyLoss * (1f - loyaltyBonus).coerceAtLeast(0.2f)).toInt().coerceAtLeast(0)
                    teacher.loyalty = (teacher.loyalty - salaryLoyaltyLoss).coerceAtLeast(0)
                }
            }

            // POPULAR teachers get loyalty boost from students
            if (TeacherTrait.POPULAR in teacher.traits) {
                teacher.loyalty = (teacher.loyalty + 1).coerceAtMost(100)
            }

            // Research loyalty bonus: passive daily loyalty recovery (when not at max)
            if (loyaltyBonus > 0f && teacher.loyalty < 100) {
                val passiveRecovery = (loyaltyBonus * 2).toInt().coerceIn(0, 3)
                teacher.loyalty = (teacher.loyalty + passiveRecovery).coerceAtMost(100)
            }

            // === 忠诚度预警通知（仅在数据库确认日结后投递）===
            val loyaltyWarning = if (loyaltyBefore > 50 && teacher.loyalty <= 50) {
                GameEvent.NegativeEvent(
                    title = "⚠️ 教师不满: ${teacher.name}",
                    message = "${teacher.name}的工作满意度下降到50%。\n\n可能原因：工作疲劳过高、薪资低于市场水平。\n建议：安排休假、提高薪资或改善学校设施。",
                    penaltyCash = 0.0
                )
            } else if (loyaltyBefore > 25 && teacher.loyalty <= 25) {
                GameEvent.NegativeEvent(
                    title = "🚨 教师即将离职: ${teacher.name}",
                    message = "${teacher.name}严重不满，即将提交辞呈！\n\n紧急建议：立即加薪或安排休假，否则将收到离职申请。",
                    penaltyCash = 0.0
                )
            } else {
                null
            }

            // === STATE MACHINE TRANSITIONS ===

            // TIRED: fatigue > 80 → force vacation
            if (teacher.fatigue >= 80) {
                teacher.isOnVacation = true
                if (teacherRepository.saveDailyState(previousState, teacher)) {
                    loyaltyWarning?.let { emitEvent(it, school) }
                    emitEvent(GameEvent.NegativeEvent(
                        title = "教师疲劳",
                        message = "${teacher.name}过度疲劳，强制休假恢复。",
                        penaltyCash = 0.0
                    ), school)
                }
                return@forEach
            }

            // QUIT: loyalty <= 0 → teacher submits resignation (needs principal approval)
            if (teacher.loyalty <= 0 && !teacher.pendingResignation) {
                teacher.pendingResignation = true
                teacher.loyalty = 1  // 暂设为1防止下一tick重复触发
                val retainRaiseCost = (teacher.salary * 0.3).coerceAtLeast(0.5) // 加薪30%挽留
                val resignationEvent = GameEvent.ChoiceEvent(
                    title = "📋 教师离职申请书",
                    message = "━━━━━━━━━━━━━━━━━━\n" +
                        "　　　　辞 职 申 请 书\n" +
                        "━━━━━━━━━━━━━━━━━━\n\n" +
                        "致：${school.name} ${school.principalName}校长\n\n" +
                        "本人${teacher.name}，现任${school.name}" +
                        "${teacher.role.displayName}。因个人原因，" +
                        "经慎重考虑后，决定辞去现任职务。\n\n" +
                        "在校工作期间，感谢学校与校长的关怀" +
                        "与栽培，深感不舍。但由于自身发展需要，" +
                        "实难继续履职。\n\n" +
                        "特此申请，恳请批准。\n\n" +
                        "此致\n敬礼\n\n" +
                        "　　申请人：${teacher.name}\n" +
                        "　　日　期：${school.currentYear}年${school.currentMonth}月${school.currentDay}日",
                    choices = listOf(
                        EventChoice(
                            text = "批准离职（签字）",
                            consequence = EventConsequence(
                                teacherAction = com.arktools.xiaozhang.domain.model.TeacherAction.ApproveResignation(teacher.id),
                                requiresSignature = true
                            )
                        ),
                        EventChoice(
                            text = "加薪挽留（月薪+30%）",
                            consequence = EventConsequence(
                                cashChange = -retainRaiseCost,
                                teacherAction = com.arktools.xiaozhang.domain.model.TeacherAction.RetainWithRaise(teacher.id, 0.3)
                            )
                        )
                    )
                )
                if (teacherRepository.saveDailyState(previousState, teacher)) {
                    loyaltyWarning?.let { emitEvent(it, school) }
                    emitEvent(resignationEvent, school)
                }
                return@forEach
            }

            // 已提交离职申请的教师跳过正常更新（等待审批中）
            if (teacher.pendingResignation) {
                if (teacherRepository.saveDailyState(previousState, teacher)) {
                    loyaltyWarning?.let { emitEvent(it, school) }
                }
                return@forEach
            }

            // === SKILL GROWTH ===
            val growth = com.arktools.xiaozhang.domain.model.TeacherGrowth.calculateDailyGrowth(teacher)

            // MENTOR trait: team members in same course get +20% growth
            // Check if any MENTOR teacher shares a course with this teacher
            val mentorBonus = if (teachers.any { other ->
                other.id != teacher.id &&
                TeacherTrait.MENTOR in other.traits &&
                other.isWorking && !other.isOnVacation
            }) 1.2f else 1.0f

            teacher.teaching = (teacher.teaching + growth.teachingGrowth * mentorBonus).toInt().coerceAtMost(1000)
            teacher.research = (teacher.research + growth.researchGrowth * mentorBonus).toInt().coerceAtMost(1000)
            teacher.management = (teacher.management + growth.managementGrowth * mentorBonus).toInt().coerceAtMost(1000)
            teacher.psychology = (teacher.psychology + growth.psychologyGrowth * mentorBonus).toInt().coerceAtMost(1000)
            teacher.experiencePoints++

            // Inspiration event (loyalty must be high, PASSIONATE trait boosts chance)
            val inspirationChance = if (TeacherTrait.PASSIONATE in teacher.traits)
                GameBalanceConfig.TEACHER_INSPIRATION_CHANCE * 2f
            else GameBalanceConfig.TEACHER_INSPIRATION_CHANCE

            val inspirationTriggered =
                Random.nextFloat() < inspirationChance &&
                    teacher.loyalty > GameBalanceConfig.TEACHER_INSPIRATION_LOYALTY_THRESHOLD

            if (teacherRepository.saveDailyState(previousState, teacher)) {
                loyaltyWarning?.let { emitEvent(it, school) }
                if (inspirationTriggered) {
                    emitEvent(GameEvent.PositiveEvent(
                        title = "灵感爆发",
                        message = "${teacher.name}获得了教学灵感，技能临时提升！",
                        bonusTeacherSkill = GameBalanceConfig.TEACHER_INSPIRATION_SKILL_BONUS
                    ), school)
                }
            }
        }

        schoolRepository.mutateSchool { latest ->
            if (latest.facilities.isEmpty()) {
                false
            } else {
                var shouldPersist = false
                latest.facilities.forEach { facility ->
                    facility.condition = (facility.condition - 0.1f).coerceAtLeast(0f)
                    if (facility.condition <= 20f) {
                        shouldPersist = true
                    }
                }
                shouldPersist
            }
        }
    }

    private suspend fun updateMarketingCampaigns(school: School) {
        if (school.marketingCampaigns.isEmpty()) return
        schoolRepository.mutateSchool { latest ->
            if (latest.marketingCampaigns.isEmpty()) {
                return@mutateSchool false
            }
            var changed = false
            if (latest.cash < 0) {
                latest.marketingCampaigns.filter { it.isActive }.forEach {
                    it.isActive = false
                    changed = true
                }
            }
            latest.marketingCampaigns.forEach { campaign ->
                if (campaign.isActive) {
                    campaign.daysActive++
                    val dailyCost = campaign.budget / 30.0
                    campaign.totalSpent += dailyCost
                    if (campaign.totalSpent >= campaign.budget * 6.0) {
                        campaign.isActive = false
                    }
                    changed = true
                }
            }
            val totalDailyCost = MarketingCalculator.getDailyCost(latest.marketingCampaigns)
            if (totalDailyCost > 0) {
                latest.cash = (latest.cash - totalDailyCost).coerceAtLeast(-100.0)
            }
            changed || totalDailyCost > 0
        }
    }

    private suspend fun updateReleasedCourses(school: School): Double {
        // BonusType.REVENUE: 已解锁教学方法中所有收入加成累加
        // 收入加成上限0.80（即最多+80%），防止收入爆炸。
        val rawRevenueBonus = researchRepository.getUnlockedBonusByType(BonusType.REVENUE)
        val revenueBonus = rawRevenueBonus.toDouble().coerceAtMost(0.80)
        val tuitionPolicyMultiplier = policyManager.getPolicyEffects().tuitionMultiplier
        val revenueMultiplier = (1.0 + revenueBonus) * tuitionPolicyMultiplier

        val activeStudentCount = studentRepository.getActiveStudentCount()
        val tuitionPerStudent = GameBalanceConfig.MONTHLY_TUITION_PER_STUDENT *
            GameBalanceConfig.getTuitionMultiplier(school.campusLevel)
        val totalMonthlyRevenue =
            activeStudentCount * tuitionPerStudent * revenueMultiplier

        if (totalMonthlyRevenue > 0) {
            checkNotNull(schoolRepository.mutateSchool { latest ->
                latest.cash += totalMonthlyRevenue
                latest.totalRevenue += totalMonthlyRevenue
                true
            }) { "Monthly tuition revenue commit failed" }
        } else if (activeStudentCount > 0) {
            android.util.Log.w(
                "GameEngine",
                "WARNING: $activeStudentCount active students but 0 monthly revenue. " +
                    "revenueBonus=$revenueBonus tuitionMultiplier=$tuitionPolicyMultiplier"
            )
        }
        return totalMonthlyRevenue
    }

    data class MonthlyExpenseBreakdown(
        val total: Double,
        val salary: Double,
        val facilities: Double,
        val teaching: Double
    )

    private suspend fun deductMonthlyExpenses(school: School): MonthlyExpenseBreakdown {
        val teachers = teacherRepository.getTeachers()
        val teacherProfiles = teacherDevelopmentManager.state.value.teacherProfiles
        val teacherPayPolicy = policyManager.policies.value.teacherPayPolicy

        // Bug 5 fix: 应用教师职称 salaryMultiplier
        val totalSalary = teachers.filter { it.isWorking }.sumOf { teacher ->
            val profile = teacherProfiles.find { it.teacherId == teacher.id }
            val titleMultiplier = profile?.title?.salaryMultiplier?.toDouble() ?: 1.0
            teacher.monthlySalary * titleMultiplier
        }
        val baseRent = getMonthlyRent(school.campusLevel)

        // Facility maintenance costs
        val facilityMaintenance = FacilityBonusCalculator.getTotalMaintenance(school.facilities)

        // Apply inflation based on game year (difficulty curve)
        val salaryInflation = GameBalanceConfig.getSalaryInflation(school.currentYear)
        val rentInflation = GameBalanceConfig.getRentInflation(school.currentYear)

        // During breaks, salary is reduced (teachers on half-pay vacation)
        val breakMultiplier = if (SemesterCalendar.isOnBreak(school.currentMonth)) 0.6 else 1.0

        // BonusType.COST_REDUCTION: 已解锁教学方法中所有成本削减加成累加
        val costReductionBonus = researchRepository.getUnlockedBonusByType(BonusType.COST_REDUCTION)
        val costReductionMultiplier = (1.0 - costReductionBonus).coerceAtLeast(0.5)

        // Bug 4 fix: teacherPayPolicy.expenseMultiplier 只作用于薪资部分
        val salaryExpenses = totalSalary * salaryInflation * breakMultiplier *
                teacherPayPolicy.expenseMultiplier * costReductionMultiplier
        // 其他开销使用不含教师薪资政策的基础倍率
        val otherExpenses = (baseRent * rentInflation + facilityMaintenance) * costReductionMultiplier

        // 教学系统运营成本（班级开支 + 排课政策成本 + 特色项目维护）
        val teachingExpenses = teachingManager.config.monthlyOperatingCost() * costReductionMultiplier

        // 政策综合开支乘数（课外活动、奖学金等政策对非薪资开支的影响）
        val policyExpenseMultiplier = policyManager.getPolicyEffects().expenseMultiplier
        val totalExpenses = salaryExpenses + (otherExpenses + teachingExpenses) * policyExpenseMultiplier

        schoolRepository.mutateSchool { latest ->
            latest.cash = (latest.cash - totalExpenses).coerceAtLeast(-100.0)
            if (latest.cash < -50.0 && !latest.wasNearBankrupt) {
                latest.wasNearBankrupt = true
            }
            latest.marketCap = latest.cash * 2 + latest.totalRevenue * 0.5
            true
        }

        return MonthlyExpenseBreakdown(
            total = totalExpenses,
            salary = salaryExpenses,
            facilities = otherExpenses,
            teaching = teachingExpenses
        )
    }

    private suspend fun updateSchoolReputation(school: School) {
        val totalStudents = studentRepository.getActiveStudents().size
        val teachers = teacherRepository.getTeachers()

        // 教学质量评分：基于教师能力均值（0-100）
        val teachingQuality = if (teachers.isNotEmpty()) {
            teachers.map { it.averageSkill.toFloat() }.average().toFloat()
        } else 0f

        // 声誉增长：基于教学质量和学生规模（降低系数，避免增长过快）
        val baseReputationGain = (teachingQuality * 0.12f + totalStudents * 0.04f).toLong()

        // Semester calendar bonus (graduation, exams, sports, etc.)
        val calendarBonus = SemesterCalendar.getReputationBonus(school.currentMonth)

        // Facility reputation growth bonus (auditorium + gate)
        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)
        val facilityRepMultiplier = 1.0 + facilityBonuses.reputationGrowthBonus

        // Marketing reputation boost
        val marketingRepBoost = MarketingCalculator.getReputationBoost(school.marketingCampaigns)

        // Student satisfaction & review-based reputation impact
        val avgSatisfaction = studentRepository.getAverageSatisfaction()
        val studentSatisfactionBonus = ((avgSatisfaction - 50f) * 0.1f).toLong()  // >50 positive, <50 negative

        // 奖学金声誉加成（每个奖学金项目每月+2声誉）
        val scholarshipRepBonus = scholarshipManager.state.value.reputationBonus.toLong()

        // 政策声誉修正（每月额外声誉增减，来自学费/考试/课外活动等政策组合）
        val policyRepModifier = policyManager.getPolicyEffects().reputationModifier

        val totalReputationGain = ((baseReputationGain + calendarBonus + marketingRepBoost + studentSatisfactionBonus + scholarshipRepBonus + policyRepModifier) * facilityRepMultiplier).toLong()
        if (totalReputationGain != 0L) {
            schoolRepository.addReputation(totalReputationGain)
        }

        // 更新星级评分：综合声誉、学生满意度、教师质量、学校规模
        val reputationFactor = (school.reputation.toFloat() / 2000f).coerceIn(0f, 1f)  // 声誉贡献 (0-2000映射到0-1)
        val satisfactionFactor = if (avgSatisfaction > 0f) avgSatisfaction / 100f else 0.5f
        val qualityFactor = teachingQuality / 100f  // 教学质量（0-1）
        val scaleFactor = (totalStudents.toFloat() / 200f).coerceIn(0f, 1f)  // 规模（0-200映射到0-1）

        // 星级 = 声誉30% + 满意度25% + 教学质量25% + 规模20%，映射到0-5星
        val newStarRating = ((reputationFactor * 0.3f + satisfactionFactor * 0.25f +
                qualityFactor * 0.25f + scaleFactor * 0.2f) * 5f).coerceIn(0f, 5f)

        schoolRepository.mutateSchool { latest ->
            latest.starRating = newStarRating
            true
        }
    }

    private suspend fun recordMonthlyStats(school: School, revenue: Double, expenses: Double) {
        val teachers = teacherRepository.getTeachers()
        // 使用真实学生数作为enrollment，而非旧CourseProject（已废弃，数据始终为0）
        val activeStudentCount = studentRepository.getActiveStudentCount().toLong()
        // 教学质量基于教师平均技能（averageSkill 量纲 0-1000，映射到 0-100 百分比）
        // 注意：报表图表/雷达图/汇总卡均按 0-100 渲染，这里必须存 0-100 而非 0-10
        val avgQuality = if (teachers.isNotEmpty()) {
            val avgSkill = teachers.map { (it.teaching + it.research + it.management + it.psychology) / 4f }.average().toFloat()
            (avgSkill / 10f).coerceIn(0f, 100f)
        } else 0f
        val avgSatisfaction = if (teachers.isNotEmpty())
            teachers.map { it.loyalty.toFloat() }.average().toFloat() else 0f
        // 开课科目数 = 有教师覆盖的科目数
        val activeSubjectCount = teachers.map { it.role.name }.distinct().size

        com.arktools.xiaozhang.domain.model.StatisticsManager.recordMonth(
            school = school,
            monthlyRevenue = revenue,
            monthlyExpenses = expenses,
            enrollment = activeStudentCount,
            teacherCount = teachers.size,
            courseCount = activeSubjectCount,
            avgQuality = avgQuality,
            avgSatisfaction = avgSatisfaction
        )
    }

    /**
     * 处理危机检测结果
     */
    private suspend fun handleCrisisResult(result: CrisisCheckResult, school: School) {
        when (result) {
            is CrisisCheckResult.ENTERED_WARNING -> {
                val warningMsg = result.conditions.joinToString("、") { it.toDisplayName() }
                emitEvent(GameEvent.NegativeEvent(
                    title = "经营警告",
                    message = "学校出现危险信号：$warningMsg。请尽快采取措施！",
                    penaltyCash = 0.0
                ), school)
            }
            is CrisisCheckResult.ENTERED_CRITICAL -> {
                val criticalMsg = result.conditions.joinToString("、") { it.toDisplayName() }
                emitEvent(GameEvent.NegativeEvent(
                    title = "紧急危机",
                    message = "学校面临严重危机：$criticalMsg。是否接受紧急救助？",
                    penaltyCash = 0.0
                ), school)
                // UI层会检测 crisisState 并弹出救助对话框
                isPaused = true
            }
            is CrisisCheckResult.RECOVERED -> {
                emitEvent(GameEvent.PositiveEvent(
                    title = "危机解除",
                    message = "学校经营状况有所好转，继续努力！",
                    bonusCash = 0.0
                ), school)
            }
            CrisisCheckResult.NO_CHANGE -> { /* no-op */ }
        }
    }

    /**
     * 应用竞争对手事件对玩家的影响
     */
    private suspend fun applyCompetitorEventEffect(event: com.arktools.xiaozhang.domain.competitor.CompetitorEvent) {
        when (event) {
            is com.arktools.xiaozhang.domain.competitor.CompetitorEvent.PriceWar -> {
                // 事件对象中的随机损失是唯一权威值，通知事件不再重复结算。
                schoolRepository.mutateSchool { latest ->
                    latest.reputation -= event.reputationLoss
                    true
                }
            }
            is com.arktools.xiaozhang.domain.competitor.CompetitorEvent.TalentPoaching -> {
                // 挖角：随机降低一名教师忠诚度
                val teachers = teacherRepository.getTeachers().filter { it.isWorking }
                if (teachers.isNotEmpty()) {
                    val target = teachers.random()
                    teacherRepository.adjustLoyalty(
                        teacherId = target.id,
                        delta = -event.loyaltyDamage,
                        minimum = 5
                    )
                }
            }
            is com.arktools.xiaozhang.domain.competitor.CompetitorEvent.MarketExpansion -> {
                schoolRepository.mutateSchool { latest ->
                    latest.reputation -= 2L
                    true
                }
            }
            is com.arktools.xiaozhang.domain.competitor.CompetitorEvent.CompetitorCollapse -> {
                schoolRepository.mutateSchool { latest ->
                    latest.reputation += event.reputationGain
                    latest.cash += event.studentGain * 0.5
                    true
                }
            }
            is com.arktools.xiaozhang.domain.competitor.CompetitorEvent.Partnership -> {
                schoolRepository.mutateSchool { latest ->
                    latest.reputation += event.reputationGain
                    true
                }
            }
        }
    }

    private fun FailureCondition.toDisplayName(): String = when (this) {
        FailureCondition.SUSTAINED_LOSSES -> "持续亏损"
        FailureCondition.BANKRUPT -> "资金枯竭"
        FailureCondition.REPUTATION_COLLAPSE -> "声誉崩塌"
        FailureCondition.ALL_TEACHERS_QUIT -> "教师全部离职"
        FailureCondition.NO_STUDENTS -> "学生全部流失"
        FailureCondition.PRINCIPAL_ARRESTED -> "校长被逮捕"
    }

    suspend fun calculateCourseScore(course: CourseProject, school: School): Float {
        val teachers = teacherRepository.getTeachers().filter { it.id in course.teamIds }
        val teamSkill = if (teachers.isNotEmpty()) {
            teachers.map { it.averageSkill }.average().toFloat() / 100f * 10f
        } else 5f

        val methodBonus = researchRepository.getUnlockedMethodBonus(course.methodIds)
        val designScore = course.designScore
        val bugPenalty = course.problemCount * GameBalanceConfig.COURSE_SCORE_BUG_PENALTY_PER
        val districtFit = getDistrictSubjectFit(course.targetDistrict, course.subject)

        // --- Facility bonuses ---
        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)
        val facilityQualityBonus = facilityBonuses.teachingQualityBonus

        // Subject-specific facility bonuses
        // 电脑室(programmingBonus)对理科(SCIENCE)也有加成：技术辅助教学
        val subjectFacilityBonus = when (course.subject.category) {
            com.arktools.xiaozhang.domain.model.SubjectCategory.SCIENCE ->
                facilityBonuses.scienceBonus + facilityBonuses.programmingBonus * 0.5f
            com.arktools.xiaozhang.domain.model.SubjectCategory.ART -> facilityBonuses.artBonus
            else -> 0f
        }

        // --- Trait bonuses on score ---
        var traitScoreBonus = 0f
        var traitBugReduction = 0f
        if (teachers.isNotEmpty()) {
            val teamSize = teachers.size
            teachers.forEach { teacher ->
                teacher.traits.forEach { trait ->
                    when (trait) {
                        TeacherTrait.INNOVATIVE -> traitScoreBonus += 0.5f / teamSize
                        TeacherTrait.PERFECTIONIST -> traitScoreBonus += 1.0f / teamSize
                        TeacherTrait.STRICT -> traitScoreBonus += 0.5f / teamSize  // quality+15% approximated as +0.5
                        TeacherTrait.EXPERIENCED -> traitBugReduction += 0.5f / teamSize  // finds bugs better → less penalty
                        TeacherTrait.RESEARCHER -> traitScoreBonus += 0.3f / teamSize  // research skill benefits quality
                        TeacherTrait.INTROVERT -> {
                            if (teamSize == 1) traitScoreBonus += 0.3f  // solo efficiency bonus
                            else traitScoreBonus -= 0.15f / teamSize     // team penalty
                        }
                        TeacherTrait.OUTDATED -> traitScoreBonus -= 0.3f / teamSize
                        else -> {}
                    }
                }
            }
        }

        // Effective bug penalty reduced by EXPERIENCED trait
        val effectiveBugPenalty = (bugPenalty - traitBugReduction).coerceAtLeast(0f)

        val baseScore = (
            teamSkill * GameBalanceConfig.COURSE_SCORE_TEAM_WEIGHT +
            methodBonus * 10f * GameBalanceConfig.COURSE_SCORE_METHOD_WEIGHT +
            designScore * GameBalanceConfig.COURSE_SCORE_DESIGN_WEIGHT +
            districtFit * 10f * GameBalanceConfig.COURSE_SCORE_DISTRICT_WEIGHT -
            effectiveBugPenalty
        )

        // BonusType.TEACHING_QUALITY: 已解锁教学方法中所有教学质量加成累加
        val teachingQualityBonus = researchRepository.getUnlockedBonusByType(BonusType.TEACHING_QUALITY)

        // Apply bonuses additively then facility multiplier and research quality bonus
        val finalScore = (baseScore + traitScoreBonus + subjectFacilityBonus) *
                (1f + facilityQualityBonus) * (1f + teachingQualityBonus)
        return finalScore.coerceIn(0f, 10f)
    }

    suspend fun calculateEnrollment(course: CourseProject, school: School, monthsSinceRelease: Int, traitEnrollmentMultiplier: Double = 1.0): Long {
        val score = course.qualityScore
        val baseExposure = course.targetDistrict.baseExposure * GameBalanceConfig.getDistrictExposureBonus(school.campusLevel)
        val marketTrend = getMarketTrend(course.subject, course.theme, school.currentYear)
        val reputationBonus = 1 + (school.reputation / GameBalanceConfig.ENROLLMENT_REPUTATION_DIVISOR)
        val heatDecay = GameBalanceConfig.ENROLLMENT_HEAT_DECAY_POWER.pow(monthsSinceRelease)

        // Apply semester calendar enrollment multiplier (peak during enrollment seasons)
        val semesterMultiplier = SemesterCalendar.getEnrollmentMultiplier(school.currentMonth)

        // Apply subject popularity factor
        val subjectProfile = com.arktools.xiaozhang.domain.model.SubjectConfig.getProfile(course.subject)
        val popularityFactor = subjectProfile.popularity

        // Facility enrollment bonus (classrooms, sports field, dormitory)
        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)
        val facilityEnrollmentMultiplier = 1.0 + facilityBonuses.enrollmentBonus

        // BonusType.ENROLLMENT: 已解锁教学方法中所有招生人数加成累加
        val enrollmentBonus = researchRepository.getUnlockedBonusByType(BonusType.ENROLLMENT)

        // 奖学金招生加成（每个奖学金+3%，上限30%）
        val scholarshipBonus = scholarshipManager.getEnrollmentBonus().toDouble()

        // 校友网络招生加成（校友数*0.5% + 成功校友*2%，上限50%）
        val alumniBonus = alumniNetwork.getEnrollmentBonus().toDouble()

        // 家长口碑招生乘数
        val parentMultiplier = parentSatisfactionManager.getEnrollmentMultiplier().toDouble()

        val baseEnrollment = score * baseExposure * marketTrend * GameBalanceConfig.ENROLLMENT_BASE_MULTIPLIER *
                reputationBonus * heatDecay * semesterMultiplier * popularityFactor *
                facilityEnrollmentMultiplier * traitEnrollmentMultiplier *
                (1.0 + enrollmentBonus) *
                (1.0 + scholarshipBonus) *
                (1.0 + alumniBonus) *
                parentMultiplier
        return baseEnrollment.toLong().coerceAtLeast(0)
    }

    private fun calculateMonthsSinceRelease(course: CourseProject, school: School): Int {
        val releaseYear = course.releaseYear ?: return 0
        val releaseMonth = course.releaseMonth ?: return 0
        val totalCurrentMonths = school.currentYear * 12 + school.currentMonth
        val totalReleaseMonths = releaseYear * 12 + releaseMonth
        return (totalCurrentMonths - totalReleaseMonths).coerceAtLeast(0)
    }

    private fun getMarketTrend(subject: com.arktools.xiaozhang.domain.model.Subject, theme: com.arktools.xiaozhang.domain.model.CourseTheme, year: Int): Float {
        return GameBalanceConfig.getMarketTrend(subject, theme, year)
    }

    private fun getDistrictSubjectFit(district: com.arktools.xiaozhang.domain.model.DistrictType, subject: com.arktools.xiaozhang.domain.model.Subject): Float {
        return when (district) {
            com.arktools.xiaozhang.domain.model.DistrictType.LOCAL -> 1.0f
            com.arktools.xiaozhang.domain.model.DistrictType.CROSS_DISTRICT -> 0.9f
            com.arktools.xiaozhang.domain.model.DistrictType.INTERNATIONAL ->
                if (subject == com.arktools.xiaozhang.domain.model.Subject.ENGLISH) 1.2f else 0.8f
            com.arktools.xiaozhang.domain.model.DistrictType.ONLINE_PLATFORM -> 1.1f
            com.arktools.xiaozhang.domain.model.DistrictType.ELITE_ALLIANCE -> 1.15f
            com.arktools.xiaozhang.domain.model.DistrictType.GLOBAL_NETWORK -> 1.2f
        }
    }

    private suspend fun getMarketSalary(teacher: Teacher): Double {
        val school = schoolRepository.getSchool()
        val inflation = if (school != null) GameBalanceConfig.getSalaryInflation(school.currentYear) else 1.0
        return when (teacher.level) {
            com.arktools.xiaozhang.domain.model.TeacherLevel.C -> GameBalanceConfig.MARKET_SALARY_C * inflation
            com.arktools.xiaozhang.domain.model.TeacherLevel.B -> GameBalanceConfig.MARKET_SALARY_B * inflation
            com.arktools.xiaozhang.domain.model.TeacherLevel.A -> GameBalanceConfig.MARKET_SALARY_A * inflation
            com.arktools.xiaozhang.domain.model.TeacherLevel.S -> GameBalanceConfig.MARKET_SALARY_S * inflation
        }
    }

    private fun getMonthlyRent(campusLevel: Int): Double {
        return GameBalanceConfig.getMonthlyRent(campusLevel)
    }

    // ==================== 学生系统 ====================

    /**
     * 根据学生所在班级推断其班型（ClassTier）
     * 基于教学配置的classDistribution和班级序号推断
     */
    private fun getStudentClassTier(student: Student): ClassTier {
        val studentClass = student.classId?.let { cid ->
            classes.find { it.id == cid }
        } ?: return ClassTier.NORMAL

        // 直接使用班级的 classTier 字段（新系统）
        return studentClass.classTier
    }

    /**
     * 根据班级在年级内的序号推断班型
     * 约定：按 classDistribution 中的顺序分配班型
     * 例如 {KEY:2, NORMAL:4} → 第1-2个班是KEY，第3-6个班是NORMAL
     */
    private fun inferClassTierByIndex(
        index: Int,
        distribution: Map<ClassTier, Int>
    ): ClassTier {
        var accumulated = 0
        for ((tier, count) in distribution) {
            accumulated += count
            if (index < accumulated) {
                return tier
            }
        }
        return ClassTier.NORMAL
    }

    private suspend fun updateStudentProgress(school: School) {
        val activeStudents = studentRepository.getActiveStudents()
        if (activeStudents.isEmpty()) return

        val allTeachers = teacherRepository.getTeachers()
        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)
        val policyEffects = policyManager.getPolicyEffects()
        val teachingConfig = teachingManager.config

        val updatedStudents = mutableListOf<Student>()
        val droppedStudents = mutableListOf<Student>()

        activeStudents.forEach { student ->
            // 切换到 STUDYING 状态
            if (student.status == StudentStatus.ENROLLED) {
                student.status = StudentStatus.STUDYING
            }

            // === 设施→学生个体效果（每日五维成长 + 健康 + 生活质量） ===
            val afterFacility = FacilityStudentEffect.applyDailyEffects(student, school.facilities)
            student.attributes = afterFacility.attributes
            student.healthStatus = afterFacility.healthStatus
            student.mealQuality = afterFacility.mealQuality
            student.dormSatisfaction = afterFacility.dormSatisfaction
            student.exerciseLevel = afterFacility.exerciseLevel
            student.consecutiveSickDays = afterFacility.consecutiveSickDays

            // 获取该学生班级对应的教师平均技能
            val classTeachers = allTeachers.filter { it.isWorking }
            val teacherAvgSkill = if (classTeachers.isNotEmpty()) {
                classTeachers.map { it.averageSkill }.average().toFloat()
            } else 30f

            // 使用教学配置计算教学质量参数（乘以政策质量系数）
            val studentClassTier = getStudentClassTier(student)
            val teachingQuality = teachingConfig.overallQuality(teacherAvgSkill) * policyEffects.qualityMultiplier
            val scoreGrowthMul = teachingManager.scoreGrowthMultiplier(studentClassTier)

            // 计算每日学期掌握度（基于教学配置而非旧课程）
            val dailyMastery = StudentProgressCalculator.calculateDailySemesterMastery(
                courseScale = CourseScale.FULL_TIME,  // 高中全日制
                talent = student.talent,
                courseQuality = teachingQuality,
                teacherSkill = teacherAvgSkill,
                traits = student.traits,
                attributes = student.attributes,
                healthMultiplier = student.learningMultiplier * scoreGrowthMul
            )
            student.semesterMastery = (student.semesterMastery + dailyMastery).coerceAtMost(100f)

            // 计算每日满意度变化（含教学配置的满意度影响）
            val satisfactionDelta = StudentSatisfactionCalculator.calculateDailySatisfactionDelta(
                courseQuality = teachingQuality,
                teacherAvgSkill = teacherAvgSkill,
                facilityBonus = facilityBonuses.teachingQualityBonus,
                studentMotivation = student.motivation,
                traits = student.traits,
                attributes = student.attributes,
                healthStatus = student.healthStatus
            )
            // 政策 + 教学配置对满意度的每日微调
            val policySatisfactionDaily = policyEffects.satisfactionModifier / 30f
            val teachingSatisfactionDaily = teachingConfig.monthlySatisfactionImpact() / 30f
            student.satisfaction = (student.satisfaction + satisfactionDelta + policySatisfactionDaily + teachingSatisfactionDaily).coerceIn(0f, 100f)

            // 检查退学（含特质效果 + 政策修正 + 奖学金留存加成）
            val baseDropout = StudentSatisfactionCalculator.calculateDropoutProbability(student.satisfaction, student.traits)
            val retentionReduction = scholarshipManager.getRetentionBonus() / 30f  // 月度留存加成折算到每日
            val dropoutProbability = (baseDropout + policyEffects.dropoutRateModifier / 30f - retentionReduction).coerceAtLeast(0f)
            if (dropoutProbability > 0f && Random.nextFloat() < dropoutProbability) {
                student.status = StudentStatus.DROPPED
                student.graduateYear = school.currentYear
                student.graduateMonth = school.currentMonth
                student.review = StudentSatisfactionCalculator.generateDropoutReview(student)
                droppedStudents.add(student)
            }

            updatedStudents.add(student)
        }

        val persistedStudentIds = if (updatedStudents.isNotEmpty()) {
            studentRepository.applyDailyProgress(updatedStudents)
        } else {
            emptySet()
        }
        val persistedDropouts = droppedStudents.filter {
            it.id in persistedStudentIds
        }

        // 退学事件通知
        if (persistedDropouts.size >= 3) {
            emitEvent(GameEvent.NegativeEvent(
                title = "学生退学潮",
                message = "本日有${persistedDropouts.size}名学生因满意度过低选择退学！",
                penaltyCash = 0.0,
                penaltyReputation = persistedDropouts.size.toLong() * 2L
            ), school)
        } else if (persistedDropouts.isNotEmpty()) {
            emitEvent(GameEvent.NegativeEvent(
                title = "学生退学",
                message = "${persistedDropouts.first().name}等${persistedDropouts.size}名学生选择了退学。",
                penaltyCash = 0.0,
                penaltyReputation = persistedDropouts.size.toLong()
            ), school)
        }
    }

    /**
     * 9月招生：基于学校声誉和容量计算招生人数（参考《高考工厂模拟》）
     * 招生数 = 基础招生 × 声誉加成 × 政策加成 × 校友加成，不超过容量上限
     */
    private suspend fun enrollNewStudents(school: School) {
        val policyEffects = policyManager.getPolicyEffects()
        val teachingConfig = teachingManager.config
        val distribution = teachingConfig.classDistribution  // 全校班型配置，如 {KEY:2, NORMAL:4}

        // 前置条件：必须有教室且有教学配置才能招生
        if (distribution.isEmpty()) {
            // 教学方案未配置，无法招生 —— 给玩家明确提示
            emitEvent(GameEvent.NegativeEvent(
                title = "招生失败",
                message = "尚未配置教学方案，无法招收新生！请前往「教学管理」设置班型分配（如重点班/普通班数量）。",
                penaltyCash = 0.0,
                penaltyReputation = 0
            ), school)
            return
        }

        // 1. 教室硬性约束：教室有效容量 * 3 = 最大班级总数
        //    升级教室增加有效容量：1级=1, 2级=1.5, 3级=2, 4级=2.5, 5级=3
        //    v2.8 修复：先乘3再取整，避免单间教室升级到偶数级时截断无效
        val classroomCount = school.facilities.filter {
            it.type == FacilityType.CLASSROOM && it.isOperational
        }.sumOf { ((it.level + 1) / 2.0).coerceAtLeast(1.0) * 3.0 }.toInt().coerceAtLeast(0)
        if (classroomCount <= 0) {
            // 没有教室，无法招生
            emitEvent(GameEvent.NegativeEvent(
                title = "招生失败",
                message = "学校没有可用的教室，无法招收新生！请先到「校园设施」建造教室。",
                penaltyCash = 0.0,
                penaltyReputation = 0
            ), school)
            return
        }
        // v2.8: classroomCount 现在直接是"最大总班级数"（已包含等级加成×3）
        val maxTotalClassesByRoom = classroomCount

        // 2. 按班型配置计算每年级的班级分布（全校配置 / 3 个年级，向上取整避免截断）
        val totalConfiguredClasses = distribution.values.sum().coerceAtLeast(1)
        val gradeDistribution: Map<ClassTier, Int> = distribution.mapValues { (_, count) ->
            ((count + 2) / 3).coerceAtLeast(if (count > 0) 1 else 0)  // 向上取整：7/3=3, 5/3=2, 3/3=1
        }.filter { it.value > 0 }

        // 3. 受教室约束：每年级实际可用班数不超过 maxTotalClassesByRoom / 3
        val maxClassesPerGrade = (maxTotalClassesByRoom / 3).coerceAtLeast(1)
        val gradeClassCount = gradeDistribution.values.sum().coerceAtMost(maxClassesPerGrade)

        // 4. 该年级总容量 = 各班型的 maxSize 之和
        val gradeCapacity = gradeDistribution.entries.sumOf { (tier, count) ->
            tier.maxSize * count.coerceAtMost(maxClassesPerGrade)
        }

        // 5. 计算基础招生数（声誉驱动 + 声誉维度加成）
        val reputationFactor = when {
            school.reputation >= 10000 -> 1.5f
            school.reputation >= 5000 -> 1.3f
            school.reputation >= 2000 -> 1.1f
            school.reputation >= 500 -> 1.0f
            school.reputation >= 100 -> 0.9f
            else -> 0.8f  // 新学校也能招到基本学生，不应过度惩罚
        }
        // 声誉维度加成：各维度分数越高，特定方面越吸引学生
        val repDimensions = reputationManager.state.value.dimensions
        val dimBonus = repDimensions.values.sumOf { dim ->
            (dim.score / 200.0).coerceAtMost(0.1) // 每个维度最多+10%，5维度总计最多+50%
        }.toFloat()
        val avgClassSize = if (gradeClassCount > 0) gradeCapacity / gradeClassCount else 40
        val baseEnroll = (avgClassSize * 2.5f).toInt()  // 基础：约2.5个班的量

        // 6. 校友推荐加成
        val alumniBonus = alumniNetwork.getEnrollmentBonus()

        // 7. 就业市场反馈加成
        val employmentBonus = employmentMarket.getEnrollmentBonus()

        // 8. 奖学金招生加成
        val scholarshipBonus = scholarshipManager.getEnrollmentBonus()

        // 9. 营销推广招生加成
        val marketingEnrollBonus = MarketingCalculator.getEnrollmentMultiplier(school.marketingCampaigns)

        // 10. 经营压力系统：招生季限制 + 竞争分流（使用真实竞争对手声誉）
        val topCompetitorRep = competitorEngine.competitorState.value
            .filter { it.isActive }
            .maxOfOrNull { it.reputation } ?: (school.reputation * 8 / 10)
        val pressureEnrollMultiplier = pressureSystemManager.getEnrollmentMultiplier(
            month = school.currentMonth,
            campusLevel = school.campusLevel,
            schoolRep = school.reputation,
            competitorRep = topCompetitorRep
        )

        // 11. 人脉加成：校长人脉越广，口碑传播越好，招生多5%~20%
        val connectionEnrollBonus = 1f + principal.connectionLevel * 0.002f  // 人脉100 → +20%

        // 12. 设施招生加成（教室+运动场+宿舍的 enrollmentBonus）
        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)
        val facilityEnrollMultiplier = 1f + facilityBonuses.enrollmentBonus

        // 13. 计算最终招生人数（受教室容量硬约束）
        // 注意：marketingEnrollBonus 已经是 >=1.0 的倍率，直接相乘
        // dimBonus: 声誉维度加成（各维度分数贡献）
        val rawEnroll = (baseEnroll * reputationFactor * (1f + dimBonus) * policyEffects.enrollmentMultiplier *
                (1f + alumniBonus) * (1f + scholarshipBonus) * marketingEnrollBonus.toFloat()
                * pressureEnrollMultiplier * connectionEnrollBonus * facilityEnrollMultiplier
                + employmentBonus).toInt()
        val targetEnrollCount = if (gradeCapacity <= 0) 0 else rawEnroll.coerceIn(1, gradeCapacity)
        // 高一可能因转入/异常流程提前出现少量学生；九月招生按当前真实高一人数补齐。
        // 不能只看某月是否已有1条入学记录，否则会把整批招生永久跳过。
        val existingGradeOneCount = studentRepository.getGradeStudentCount(GradeLevel.GRADE_1)
        val enrollCount = (targetEnrollCount - existingGradeOneCount).coerceAtLeast(0)

        val plan = policyManager.policies.value.enrollmentPlan
        val planName = plan.displayName
        val qualityFactor = policyEffects.enrollmentQualityMultiplier
        val specialFactor = policyEffects.specialTalentMultiplier
        val welfareBackground = if (plan == EnrollmentPlan.PUBLIC_WELFARE) {
            BackgroundTier.POOR
        } else null

        // 6. 生成新生：招生定位改变生源结构，不改变 Student 数据模型。
        val newStudents = mutableListOf<Student>()
        repeat(enrollCount) {
            val baseTraits = StudentTraitAssigner.assignTraits()
            val assignedTraits = if (
                specialFactor > 1f && Random.nextFloat() < 0.20f * specialFactor
            ) {
                (baseTraits + listOf(
                    com.arktools.xiaozhang.domain.model.StudentTrait.ARTISTIC,
                    com.arktools.xiaozhang.domain.model.StudentTrait.ATHLETIC,
                    com.arktools.xiaozhang.domain.model.StudentTrait.COMPETITIVE
                ).random()).distinct()
            } else {
                baseTraits
            }
            val background = if (
                welfareBackground != null && Random.nextFloat() < 0.55f
            ) welfareBackground else BackgroundTier.randomByProbability()
            val initialAttributes = StudentAttributes.generateForNewStudent(background)
            val qualityAttributes = initialAttributes.applyDelta(
                dIntelligence = (qualityFactor - 1f) * 20f,
                dPhysical = (qualityFactor - 1f) * 10f,
                dCreativity = (qualityFactor - 1f) * 10f
            )
            val student = Student(
                name = StudentNameGenerator.generate(),
                courseId = "GENERAL",  // 通识课程，不绑定具体课程
                traits = assignedTraits,
                schoolId = school.id,
                status = StudentStatus.ENROLLED,
                satisfaction = (65f + Random.nextFloat() * 20f),
                enrollYear = school.currentYear,
                enrollMonth = school.currentMonth,
                gradeLevel = GradeLevel.GRADE_1,
                attributes = qualityAttributes,
                backgroundTier = background,
                healthStatus = HealthStatus.HEALTHY,
                mealQuality = 50f,
                dormSatisfaction = 50f,
                exerciseLevel = 30f
            )
            newStudents.add(student)
        }

        if (newStudents.isNotEmpty()) {
            // 在隔离副本上完成分班；数据库提交成功前不污染 live 班级状态。
            val plannedClasses = _classes.value
                .map { it.copy() }
                .toMutableList()
            val assignments = classManager.assignNewStudents(
                unassignedStudents = newStudents,
                existingClasses = plannedClasses,
                strategy = ClassStrategy.BALANCED,
                schoolId = school.id,
                currentYear = school.currentYear,
                currentMonth = school.currentMonth,
                gradeDistribution = gradeDistribution
            )
            if (assignments.size != newStudents.size) {
                android.util.Log.e(
                    "GameEngine",
                    "Enrollment rejected: assigned=${assignments.size}, " +
                        "generated=${newStudents.size}"
                )
                return
            }

            val assignedStudents = newStudents.map { student ->
                student.copy(classId = assignments.getValue(student.id))
            }
            val classTierJson = org.json.JSONObject(
                plannedClasses.associate { it.id to it.classTier.name }
            ).toString()

            check(
                "classTierMapJson" !in managerRestoreFailedFields
            ) {
                "Enrollment blocked by failed class tier restore"
            }
            val commitResult = withContext(NonCancellable) {
                val result = studentRepository.enrollAssignedStudents(
                    students = assignedStudents,
                    classTierMapJson = classTierJson,
                    enrollmentYear = school.currentYear,
                    enrollmentMonth = school.currentMonth
                )
                if (result == EnrollmentCommitResult.COMMITTED) {
                    _classes.value = plannedClasses
                }
                result
            }
            if (commitResult != EnrollmentCommitResult.COMMITTED) {
                if (commitResult == EnrollmentCommitResult.REJECTED) {
                    android.util.Log.e(
                        "GameEngine",
                        "Enrollment transaction rejected for " +
                            "${school.currentYear}-${school.currentMonth}"
                    )
                }
                return
            }

            // 招生通知事件
            val actualClassCount = plannedClasses.count {
                it.gradeLevel == GradeLevel.GRADE_1
            }
            emitEvent(GameEvent.PositiveEvent(
                title = "新学年开学",
                message = "${planName}：本届补招${assignedStudents.size}名新生，当前新生共${existingGradeOneCount + assignedStudents.size}人，分入${actualClassCount}个班级。",
                bonusCash = 0.0,
                bonusReputation = (assignedStudents.size / 10).toLong() + policyEffects.welfareReputationBonus
            ), school)

            // P0-6: 最低招生线检查
            if (pressureSystemManager.isEnrollmentSeason(school.currentMonth)) {
                val metMinimum = pressureSystemManager.checkMinimumEnrollment(
                    assignedStudents.size,
                    school.campusLevel
                )
                if (!metMinimum) {
                    deferEvent(GameEvent.NegativeEvent(
                        title = "招生未达标",
                        message = "本季招生人数未达到最低招生线（等级${school.campusLevel}要求），学校口碑受损。连续未达标可能导致降级！",
                        penaltyCash = 0.0,
                        penaltyReputation = 50L * school.campusLevel
                    ))
                }
            }
        }
    }

    private fun isGraduationDue(
        student: Student,
        processingYear: Int,
        processingMonth: Int
    ): Boolean {
        return isStudentGraduationDue(
            gradeLevel = student.gradeLevel,
            enrollYear = student.enrollYear,
            processingYear = processingYear,
            processingMonth = processingMonth
        )
    }

    private suspend fun processStudentYearEnd(
        school: School,
        currentClasses: MutableList<SchoolClass>,
        currentStudents: List<Student>,
        emitNotifications: Boolean
    ): MutableList<SchoolClass> {
        val shouldRunFullYearEnd =
            school.currentMonth >= 6 &&
                school.lastYearEndProcessingYear < school.currentYear
        val overdueGraduates = currentStudents.filter { student ->
            isGraduationDue(
                student = student,
                processingYear = school.currentYear,
                processingMonth = school.currentMonth
            )
        }
        val overdueGraduateIds = overdueGraduates
            .mapTo(mutableSetOf()) { it.id }
        if (!shouldRunFullYearEnd && overdueGraduateIds.isEmpty()) {
            return currentClasses
        }

        val pendingYearEndStudents = currentStudents.filter { student ->
            (shouldRunFullYearEnd &&
                student.lastPromotionYear < school.currentYear) ||
                student.id in overdueGraduateIds
        }
        val promotionResult = classManager.yearEndPromotion(
            pendingYearEndStudents,
            currentClasses,
            school.id,
            school.currentYear
        )
        val studentsById = pendingYearEndStudents.associateBy { it.id }
        val promotedStudents = promotionResult.promotedStudents
            .mapNotNull(studentsById::get)
        val heldBackStudents = promotionResult.heldBackStudents
            .mapNotNull(studentsById::get)

        if (shouldRunFullYearEnd) {
            val plannedClasses = currentClasses
                .map { it.copy() }
                .toMutableList()
            val promotedIds = promotedStudents.map { it.id }.toSet()
            val heldBackByGrade = heldBackStudents.groupBy {
                it.gradeLevel
            }
            val affectedSourceClassIds = (
                promotedStudents + heldBackStudents
                ).mapNotNull { it.classId }.toSet()

            plannedClasses.forEach { plannedClass ->
                if (plannedClass.id in affectedSourceClassIds &&
                    plannedClass.gradeLevel.nextGrade != null
                ) {
                    val promotedCount = promotedStudents.count {
                        it.classId == plannedClass.id
                    }
                    plannedClass.gradeLevel =
                        plannedClass.gradeLevel.nextGrade!!
                    plannedClass.studentCount = promotedCount
                }
            }
            plannedClasses.removeAll {
                it.id in affectedSourceClassIds && it.studentCount == 0
            }

            val transitions = mutableListOf<StudentYearEndTransition>()
            promotedStudents.groupBy { it.gradeLevel }
                .forEach { (grade, students) ->
                    val newGrade = grade.nextGrade ?: return@forEach
                    transitions += StudentYearEndTransition(
                        studentIds = students.map { it.id },
                        expectedGrade = grade,
                        newGrade = newGrade
                    )
                }

            heldBackByGrade.forEach { (grade, students) ->
                val chunks = students.chunked(ClassTier.NORMAL.maxSize)
                var nextClassNumber = (
                    plannedClasses
                        .filter { it.gradeLevel == grade }
                        .maxOfOrNull { it.classNumber } ?: 0
                    ) + 1
                chunks.forEach { chunk ->
                    val heldBackClass = SchoolClass(
                        schoolId = school.id,
                        gradeLevel = grade,
                        classNumber = nextClassNumber++,
                        classTier = ClassTier.NORMAL,
                        studentCount = chunk.size,
                        createdYear = school.currentYear,
                        createdMonth = school.currentMonth
                    )
                    plannedClasses += heldBackClass
                    transitions += StudentYearEndTransition(
                        studentIds = chunk.map { it.id },
                        expectedGrade = grade,
                        targetClassId = heldBackClass.id
                    )
                }
            }

            check(
                transitions.flatMap { it.studentIds }.toSet() ==
                    promotedIds + heldBackStudents.map { it.id }
            ) { "Incomplete student year-end plan" }
            check("classTierMapJson" !in managerRestoreFailedFields) {
                "Student year-end blocked by failed class tier restore"
            }
            val classTierJson = org.json.JSONObject(
                plannedClasses.associate { it.id to it.classTier.name }
            ).toString()
            val committed = withContext(NonCancellable) {
                val success = studentRepository.commitYearEndTransitions(
                    transitions = transitions,
                    processingYear = school.currentYear,
                    classTierMapJson = classTierJson
                )
                if (success) {
                    currentClasses.clear()
                    currentClasses.addAll(plannedClasses)
                    _classes.value = currentClasses
                }
                success
            }
            check(committed) {
                "Student year-end transaction rejected"
            }
        }

        if (emitNotifications && heldBackStudents.isNotEmpty()) {
            val heldBackNames = heldBackStudents.map { it.name }.take(5)
            val suffix = if (heldBackStudents.size > 5) {
                "等${heldBackStudents.size}人"
            } else {
                ""
            }
            emitEvent(
                GameEvent.NegativeEvent(
                    title = "学生留级通知",
                    message = "${heldBackNames.joinToString("、")}${suffix}因学业成绩过低被留级。",
                    penaltyCash = 0.0,
                    penaltyReputation = heldBackStudents.size.toLong()
                ),
                school
            )
        }

        if (promotionResult.graduatedStudents.isNotEmpty()) {
            conductGaoKao(
                school,
                promotionResult.graduatedStudents.toSet()
            )
        }

        val activeCountByClass = studentRepository.getCurrentStudents()
            .mapNotNull { it.classId }
            .groupingBy { it }
            .eachCount()
        val cleanedClasses = currentClasses.map { schoolClass ->
            schoolClass.copy(
                studentCount = activeCountByClass[schoolClass.id] ?: 0
            )
        }.toMutableList()
        cleanedClasses.filter { it.studentCount == 0 }
            .forEach { it.headTeacherId = null }
        cleanedClasses.removeAll { it.studentCount == 0 }
        _classes.value = cleanedClasses
        saveHeadTeacherMapLocked()
        return cleanedClasses
    }

    /**
     * 高三6月高考：计算高考分数 → 录取大学 → 毕业
     * 这是唯一的正式毕业路径
     */
    private suspend fun conductGaoKao(school: School, graduatingStudentIds: Set<String>? = null) {
        val activeStudents = studentRepository.getActiveStudents()
        // 如果传入了毕业学生ID列表，则只处理这些学生（避免刚升级的高二学生被误毕业）
        val grade3Students = if (graduatingStudentIds != null) {
            activeStudents.filter { it.id in graduatingStudentIds }
        } else {
            activeStudents.filter { it.gradeLevel == GradeLevel.GRADE_3 }
        }.filter { student ->
            isGraduationDue(
                student = student,
                processingYear = school.currentYear,
                processingMonth = school.currentMonth
            )
        }
        if (grade3Students.isEmpty()) return

        val allTeachers = teacherRepository.getTeachers()
        val teacherAvgSkill = if (allTeachers.isNotEmpty()) {
            allTeachers.map { it.averageSkill }.average().toFloat()
        } else 30f

        val updatedStudents = mutableListOf<Student>()

        // 生成当年动态录取分数线（每年波动±30分）
        val scoreLines = GaoKaoCalculator.generateAnnualScoreLines(school.currentYear)

        // 教学配置对高考的综合加成（教学质量→升学率）
        val teachingQualityBonus = teachingManager.config.overallQuality(teacherAvgSkill)
        // 将 0~100 的质量分映射为 0.9~1.1 的分数系数
        val teachingScoreMultiplier = 0.9f + (teachingQualityBonus / 100f) * 0.2f
        // 政策毕业质量加成（严格考试可提升高考表现）
        val policyGradBonus = policyManager.getPolicyEffects().graduationQualityBonus

        grade3Students.forEach { student ->
            // 1. 计算高考分数
            val examHistory = examManager.getStudentScores(student.id)
            val baseScore = GaoKaoCalculator.calculateScore(
                student = student,
                teacherAvgSkill = teacherAvgSkill,
                examHistory = examHistory
            )
            // 教学配置质量加成：好的教学管理能让分数提升最多10%
            val classTier = getStudentClassTier(student)
            val tierBonus = when (classTier) {
                com.arktools.xiaozhang.domain.model.ClassTier.ROCKET -> 1.05f   // 火箭班额外+5%
                com.arktools.xiaozhang.domain.model.ClassTier.KEY -> 1.03f      // 重点班额外+3%
                else -> 1.0f
            }
            student.gaoKaoScore = (baseScore * teachingScoreMultiplier * tierBonus * (1f + policyGradBonus)).coerceIn(150f, 750f)

            // 2. 使用动态录取线录取大学（每年分数线不同！）
            val (tier, uniName) = GaoKaoCalculator.admitUniversityDynamic(student.gaoKaoScore, scoreLines)
            student.universityTier = tier
            student.admittedUniversity = uniName

            // 3. 设置毕业状态
            student.status = StudentStatus.GRADUATED
            student.graduateYear = school.currentYear
            student.graduateMonth = school.currentMonth

            // 4. 生成毕业评价（基于高考成绩和满意度）
            student.review = generateGaoKaoReview(student, tier)

            updatedStudents.add(student)
        }

        if (updatedStudents.isEmpty()) return
        check(
            withContext(NonCancellable) {
                studentRepository.commitGraduationCandidates(
                    updatedStudents
                )
            }
        ) { "Graduation candidate transaction rejected" }
        pendingGraduationProjectionRetry = true
        processPendingGraduationProjections(school, scoreLines)
    }

    private suspend fun processPendingGraduationProjections(
        school: School,
        scoreLines: com.arktools.xiaozhang.domain.exam.AnnualScoreLines? = null,
        emitNotifications: Boolean = true
    ) {
        check(managerStatesReadyForSave) {
            "Graduation projection requires completed manager restore"
        }
        val failedRequiredFields = graduationProjectionManagerFields
            .intersect(managerRestoreFailedFields)
        check(failedRequiredFields.isEmpty()) {
            "Graduation projection blocked by failed manager restore: " +
                failedRequiredFields.joinToString()
        }

        val pending = studentRepository
            .getPendingGraduationProjections()
        val yearsToProcess = (
            pending.map { it.graduateYear ?: school.currentYear } +
                alumniNetwork.getPendingGraduationSettlementYears()
            ).toSortedSet()
        if (yearsToProcess.isEmpty()) {
            pendingGraduationProjectionRetry = false
            return
        }

        val allGraduates = studentRepository.getGraduatedStudents()
        val activeStudents = studentRepository.getActiveStudents()
        yearsToProcess.forEach { graduationYear ->
                val graduates = pending.filter {
                    (it.graduateYear ?: school.currentYear) ==
                        graduationYear
                }
                val alumniSnapshot = alumniNetwork.snapshotState()
                val employmentSnapshot = employmentMarket.snapshotState()
                val pressureSnapshot = pressureSystemManager.snapshotState()
                val timetableSnapshot = timetableManager.snapshotState()
                val classesSnapshot = _classes.value
                    .map { it.copy() }
                var projectionCommitted = false

                try {
                    val expectedSchool = schoolRepository.getSchool()
                        ?: error("School missing during graduation projection")
                    val expectedLastSaveTime = expectedSchool.lastSaveTime
                    graduates.forEach { student ->
                        alumniNetwork.registerGraduate(student)
                        employmentMarket.registerGraduate(
                            studentId = student.id,
                            name = student.name,
                            year = student.graduateYear ?: graduationYear,
                            month = student.graduateMonth ?: 6,
                            gaoKaoScore = student.gaoKaoScore,
                            universityTier = student.universityTier
                                ?: UniversityTier.NONE,
                            universityName = student.admittedUniversity,
                            satisfaction = student.satisfaction
                        )
                    }

                    val graduationCohort = allGraduates.filter {
                        (it.graduateYear ?: school.currentYear) ==
                            graduationYear
                    }
                    check(graduationCohort.isNotEmpty()) {
                        "Graduation cohort missing for $graduationYear"
                    }
                    val cohortStats = GaoKaoCalculator
                        .calculateGraduationStats(graduationCohort)
                    val topStudents = graduationCohort
                        .sortedByDescending { it.gaoKaoScore }
                        .take(5)
                        .map { student ->
                            com.arktools.xiaozhang.domain.alumni
                                .GraduationTopStudent(
                                    name = student.name,
                                    score = student.gaoKaoScore,
                                    university =
                                        student.admittedUniversity,
                                    tierName = student.universityTier
                                        ?.displayName ?: "未录取"
                                )
                        }
                    alumniNetwork.recordGraduationBatch(
                        year = graduationYear,
                        totalStudents = cohortStats.totalStudents,
                        averageScore = cohortStats.averageScore,
                        highestScore = cohortStats.highestScore,
                        bengkeRate = cohortStats.bengkeLv,
                        key985Count = cohortStats.key985Count,
                        qingbeiCount = cohortStats.qingbeiCount,
                        topStudents = topStudents,
                        universityDistribution = graduationCohort.groupingBy {
                            it.universityTier?.displayName ?: "未录取"
                        }.eachCount()
                    )

                    val cohortComplete = activeStudents.none { student ->
                        student.lastPromotionYear < graduationYear &&
                            isGraduationDue(
                                student = student,
                                processingYear = graduationYear,
                                processingMonth = 6
                            )
                    }
                    val earliestPendingSettlementYear = alumniNetwork
                        .getPendingGraduationSettlementYears()
                        .minOrNull()
                    val shouldSettleYear =
                        cohortComplete &&
                            earliestPendingSettlementYear == graduationYear &&
                            !alumniNetwork
                                .isGraduationSettlementCompleted(
                                    graduationYear
                                )
                    if (graduates.isEmpty() && !shouldSettleYear) {
                        return@forEach
                    }
                    val activeCountByClass = studentRepository
                        .getCurrentStudents()
                        .mapNotNull { active -> active.classId }
                        .groupingBy { it }
                        .eachCount()
                    val plannedClasses = _classes.value
                        .map { schoolClass ->
                            schoolClass.copy(
                                studentCount = activeCountByClass[
                                    schoolClass.id
                                ] ?: 0
                            )
                        }
                        .toMutableList()
                    plannedClasses.filter { it.studentCount == 0 }
                        .forEach { it.headTeacherId = null }
                    plannedClasses.removeAll { it.studentCount == 0 }
                    timetableManager.pruneDeletedClassTimetables(
                        plannedClasses
                    )

                    val shouldNotify =
                        emitNotifications &&
                            graduationYear == school.currentYear &&
                            shouldSettleYear
                    val graduationBonus = if (shouldSettleYear) {
                        GaoKaoCalculator.calculateGraduationBonus(
                            cohortStats
                        )
                    } else {
                        0.0
                    }
                    var reputationDelta = if (shouldSettleYear) {
                        GaoKaoCalculator
                            .calculateReputationFromGraduation(
                                cohortStats
                            )
                    } else {
                        0L
                    }
                    val examConsequence = if (shouldSettleYear) {
                        pressureSystemManager.processAnnualExamResults(
                            processingYear = graduationYear,
                            currentGraduationRate =
                                cohortStats.bengkeLv / 100f,
                            campusLevel = school.campusLevel
                        )
                    } else {
                        null
                    }
                    if (examConsequence != null) {
                        reputationDelta +=
                            examConsequence.reputationChange
                    }
                    if (shouldSettleYear &&
                        cohortStats.totalStudents >= 5 &&
                        cohortStats.bengkeLv < 40f
                    ) {
                        reputationDelta -= when {
                            cohortStats.bengkeLv < 20f -> 20L
                            cohortStats.bengkeLv < 30f -> 10L
                            else -> 5L
                        }
                    }
                    if (shouldSettleYear) {
                        check(
                            alumniNetwork.completeGraduationSettlement(
                                year = graduationYear,
                                cashBonus = graduationBonus,
                                reputationDelta = reputationDelta
                            )
                        ) {
                            "Graduation settlement state missing for " +
                                graduationYear
                        }
                    }

                    val headTeacherJson = org.json.JSONObject(
                        plannedClasses
                            .filter { it.headTeacherId != null }
                            .associate { it.id to it.headTeacherId!! }
                    ).toString()
                    val classTierJson = org.json.JSONObject(
                        plannedClasses.associate {
                            it.id to it.classTier.name
                        }
                    ).toString()
                    val commit = com.arktools.xiaozhang.domain.repository
                        .GraduationProjectionCommit(
                            studentIds = graduates.map { it.id },
                            cashBonus = graduationBonus,
                            reputationDelta = reputationDelta,
                            expectedLastSaveTime = expectedLastSaveTime,
                            alumniJson = alumniNetwork.toJson(),
                            employmentJson = employmentMarket.toJson(),
                            pressureJson = pressureSystemManager.toJson(),
                            timetableJson = timetableManager.toJson(),
                            headTeacherMapJson = headTeacherJson,
                            classTierMapJson = classTierJson
                        )
                    check(
                        commit.alumniJson.isNotBlank() &&
                            commit.employmentJson.isNotBlank() &&
                            commit.pressureJson.isNotBlank() &&
                            commit.timetableJson.isNotBlank()
                    ) { "Graduation projection serialization failed" }
                    val committed = withContext(NonCancellable) {
                        val success = studentRepository
                            .commitGraduationProjection(commit)
                        if (success) {
                            _classes.value = plannedClasses
                            projectionCommitted = true
                        }
                        success
                    }
                    check(committed) {
                        "Graduation projection transaction rejected"
                    }

                    if (shouldNotify && examConsequence != null) {
                        if (examConsequence.isPositive) {
                            deferEvent(GameEvent.PositiveEvent(
                                title = "高考排名上升",
                                message = examConsequence.description,
                                bonusCash = 0.0,
                                bonusReputation = 0
                            ))
                        } else {
                            deferEvent(GameEvent.NegativeEvent(
                                title = "高考排名下滑",
                                message = examConsequence.description,
                                penaltyCash = 0.0,
                                penaltyReputation = 0
                            ))
                        }
                    }
                    if (shouldNotify) {
                        val lines = scoreLines
                            ?: GaoKaoCalculator.generateAnnualScoreLines(
                                graduationYear
                            )
                        val message = buildString {
                            append("本届${cohortStats.totalStudents}名考生高考放榜！\n")
                            append("今年录取线：${lines.formatForDisplay()}\n")
                            append("平均分${cohortStats.averageScore.toInt()}，")
                            append("最高分${cohortStats.highestScore.toInt()}。")
                            append("本科率${cohortStats.bengkeLv.toInt()}%")
                            if (cohortStats.key985Count > 0) {
                                append("，985录取${cohortStats.key985Count}人")
                            }
                            if (cohortStats.qingbeiCount > 0) {
                                append("，清北${cohortStats.qingbeiCount}人！")
                            }
                            if (graduationBonus > 0.0) {
                                append("\n获得升学奖金")
                                append(String.format("%.1f", graduationBonus))
                                append("万元！")
                            }
                        }
                        emitEvent(GameEvent.PositiveEvent(
                            title = "高考放榜",
                            message = message,
                            bonusCash = 0.0,
                            bonusReputation = 0
                        ), school)
                    }
                } catch (error: Throwable) {
                    if (!projectionCommitted) {
                        alumniNetwork.restoreSnapshot(alumniSnapshot)
                        employmentMarket.restoreSnapshot(employmentSnapshot)
                        pressureSystemManager.restoreSnapshot(pressureSnapshot)
                        timetableManager.restoreSnapshot(timetableSnapshot)
                        _classes.value = classesSnapshot
                        throw error
                    }
                    if (error is kotlinx.coroutines.CancellationException) {
                        throw error
                    }
                    android.util.Log.e(
                        "GameEngine",
                        "Graduation projection committed but notification failed",
                        error
                    )
                }
            }
        pendingGraduationProjectionRetry =
            studentRepository.getPendingGraduationProjections().isNotEmpty() ||
                alumniNetwork
                    .getPendingGraduationSettlementYears()
                    .isNotEmpty()
    }

    /**
     * 根据高考成绩和大学录取等级生成毕业评价
     */
    private fun generateGaoKaoReview(student: Student, tier: UniversityTier): StudentReview {
        val rating = when (tier) {
            UniversityTier.QINGBEI, UniversityTier.TOP_985 -> 5
            UniversityTier.NORMAL_985, UniversityTier.TOP_211 -> 4
            UniversityTier.NORMAL_211, UniversityTier.FIRST_TIER -> 3
            UniversityTier.SECOND_TIER -> 2
            UniversityTier.JUNIOR_COLLEGE, UniversityTier.NONE -> 1
        }

        val comment = when (rating) {
            5 -> listOf(
                "感谢母校三年培养，考上了${student.admittedUniversity}！",
                "三年努力没有白费，${student.admittedUniversity}我来了！",
                "老师们的教导铭记于心，以${student.gaoKaoScore.toInt()}分圆梦名校！"
            ).random()
            4 -> listOf(
                "考上了${student.admittedUniversity}，感谢学校的培养。",
                "高考${student.gaoKaoScore.toInt()}分，对得起三年的付出。",
                "录取${student.admittedUniversity}，继续努力！"
            ).random()
            3 -> listOf(
                "考上了${student.admittedUniversity}，虽然不是顶尖但也满意。",
                "高考发挥一般，但总算有学上。",
                "还行吧，${student.admittedUniversity}也不错。"
            ).random()
            2 -> listOf(
                "只考上了二本，有点遗憾。",
                "发挥失常了，对学校教学有些失望。",
                "成绩不理想，希望学校能提高教学质量。"
            ).random()
            else -> listOf(
                "高考失利，非常失望。",
                "三年白读了，成绩太差。",
                "对学校的教育质量严重不满。"
            ).random()
        }

        return StudentReview(
            rating = rating,
            comment = comment,
            reputationImpact = tier.reputationBonus
        )
    }

    /**
     * 每学期初重置所有在读学生的学期掌握度
     */
    private suspend fun resetSemesterMastery() {
        studentRepository.resetSemesterMastery()
    }

    suspend fun fastForward(days: Int) {
        require(days >= 0) { "Fast-forward days must be non-negative" }
        engineOperationMutex.withLock {
            repeat(days) {
                if (engineStopping || isSaving) return@withLock
                tick()
            }
        }
    }


}
