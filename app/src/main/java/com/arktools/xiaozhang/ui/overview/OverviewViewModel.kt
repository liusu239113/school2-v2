package com.arktools.xiaozhang.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.repository.CourseRepository
import com.arktools.xiaozhang.domain.repository.ResearchRepository
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.domain.model.CourseStatus
import com.arktools.xiaozhang.domain.model.Subject
import com.arktools.xiaozhang.domain.model.TeacherLevel
import com.arktools.xiaozhang.domain.model.StatisticsManager
import com.arktools.xiaozhang.domain.engine.GameOverDetector
import com.arktools.xiaozhang.domain.engine.HealthReport
import com.arktools.xiaozhang.domain.engine.HealthStatus
import com.arktools.xiaozhang.domain.engine.SemesterCalendar
import com.arktools.xiaozhang.domain.notification.NotificationManager
import com.arktools.xiaozhang.domain.suggestion.SuggestionBoxManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val courseRepository: CourseRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val researchRepository: ResearchRepository,
    private val gameOverDetector: GameOverDetector,
    private val notificationManager: NotificationManager,
    private val suggestionBoxManager: SuggestionBoxManager
) : ViewModel() {

    data class SchoolOverviewStats(
        val totalStudents: Int = 0,
        val monthlyNewStudents: Int = 0,
        val averageSatisfaction: Float = 0f,
        val monthlyRevenue: Double = 0.0,
        val monthlyExpenses: Double = 0.0,
        val activeCourses: Int = 0,
        val teacherCount: Int = 0,
        val maxTeachers: Int = 3,
        val reputation: Long = 0,
        val starRating: Float = 0f,
        val campusLevel: Int = 1,
        val researchUnlocked: Int = 0,
        val totalResearch: Int = 0,
        // 健康度面板
        val healthReport: HealthReport? = null,
        // 季节信息
        val currentSeason: String = "",
        val seasonEmoji: String = "",
        // 教师团队概览
        val teacherAvgSkill: Float = 0f,
        val teacherSCount: Int = 0,
        val teacherACount: Int = 0,
        val teacherBCount: Int = 0,
        val teacherCCount: Int = 0,
        val avgFatigue: Float = 0f,
        val avgLoyalty: Float = 0f,
        // 教学质量概览
        val avgCourseQuality: Float = 0f,
        val topCourseQuality: Float = 0f,
        val totalCourseRevenue: Double = 0.0,
        val coursePreparingCount: Int = 0
    )

    private val _schoolStats = MutableStateFlow(SchoolOverviewStats())
    val schoolStats: StateFlow<SchoolOverviewStats> = _schoolStats

    private val _currentTips = MutableStateFlow<List<String>>(emptyList())
    val currentTips: StateFlow<List<String>> = _currentTips

    /** 通知中心未读数 */
    val unreadNotificationCount: StateFlow<Int> = notificationManager.unreadCount

    /** 意见箱待处理数 */
    val pendingSuggestionCount: StateFlow<Int> = suggestionBoxManager.pendingCount

    init {
        viewModelScope.safeLaunch {
            try { combine(
                schoolRepository.getSchoolFlow(),
                courseRepository.getCoursesFlow(),
                teacherRepository.getTeachersFlow(),
                studentRepository.observeActiveStudentCount()
            ) { school, courses, teachers, activeStudentCount ->
                if (school == null) return@combine SchoolOverviewStats()

                val releasedCourses = courses.filter { it.status == CourseStatus.RELEASED }
                val totalStudents = activeStudentCount
                // 使用StatisticsManager最近一个月的真实数据
                val lastMonth = StatisticsManager.getRecentMonths(1).lastOrNull()
                val monthlyNew = lastMonth?.enrollment?.toInt() ?: 0
                val avgSatisfaction = studentRepository.getAverageSatisfaction()

                val monthlyRevenue = lastMonth?.revenue ?: 0.0
                val monthlyExpenses = lastMonth?.expenses
                    ?: (teachers.sumOf { it.salary } + com.arktools.xiaozhang.domain.engine.GameBalanceConfig.getMonthlyRent(school.campusLevel))

                // 研发进度
                val allMethods = researchRepository.getMethods()
                val unlockedMethods = researchRepository.getUnlockedMethods()

                // 教师团队概览
                val teacherAvgSkill = if (teachers.isNotEmpty()) {
                    teachers.map { (it.teaching + it.research + it.management + it.psychology) / 4f }.average().toFloat()
                } else 0f
                val teacherSCount = teachers.count { it.level == TeacherLevel.S }
                val teacherACount = teachers.count { it.level == TeacherLevel.A }
                val teacherBCount = teachers.count { it.level == TeacherLevel.B }
                val teacherCCount = teachers.count { it.level == TeacherLevel.C }
                val avgFatigue = if (teachers.isNotEmpty()) teachers.map { it.fatigue }.average().toFloat() else 0f
                val avgLoyalty = if (teachers.isNotEmpty()) teachers.map { it.loyalty }.average().toFloat() else 0f

                // 教学质量概览 - 使用新教学系统的数据
                // 教学质量 = 教师平均技能/10 × 教学强度 × 作息加成（与TeachingConfig.overallQuality一致）
                val avgCourseQuality = if (teachers.isNotEmpty()) {
                    (teacherAvgSkill / 10f).coerceIn(0f, 10f)
                } else 0f
                val topCourseQuality = if (teachers.isNotEmpty()) {
                    val topSkill = teachers.maxOf { (it.teaching + it.research + it.management + it.psychology) / 4f }
                    (topSkill / 10f).coerceIn(0f, 10f)
                } else 0f
                // 总收入使用学校累计收入（真实经营数据）
                val totalCourseRevenue = school.totalRevenue
                val coursePreparingCount = courses.count { it.status == CourseStatus.PREPARING }

                // 学校健康度
                val healthReport = gameOverDetector.getHealthReport(school)

                // 季节
                val season = SemesterCalendar.getSeason(school.currentMonth)
                val seasonEmoji = when (season) {
                    SemesterCalendar.Season.SPRING_SEMESTER -> "spring"
                    SemesterCalendar.Season.SUMMER_BREAK -> "summer"
                    SemesterCalendar.Season.FALL_SEMESTER -> "autumn"
                    SemesterCalendar.Season.WINTER_BREAK -> "winter"
                }

                // 实际开课科目数 = 有教师覆盖的科目数量（教师role对应的Subject）
                val activeSubjectCount = teachers.map { it.role.name }
                    .distinct()
                    .count { roleName -> Subject.entries.any { it.name == roleName } }

                SchoolOverviewStats(
                    totalStudents = totalStudents,
                    monthlyNewStudents = monthlyNew,
                    averageSatisfaction = avgSatisfaction,
                    monthlyRevenue = monthlyRevenue,
                    monthlyExpenses = monthlyExpenses,
                    activeCourses = activeSubjectCount,
                    teacherCount = teachers.size,
                    maxTeachers = school.maxTeachers,
                    reputation = school.reputation,
                    starRating = school.starRating,
                    campusLevel = school.campusLevel,
                    researchUnlocked = unlockedMethods.size,
                    totalResearch = allMethods.size,
                    healthReport = healthReport,
                    currentSeason = season.displayName,
                    seasonEmoji = seasonEmoji,
                    teacherAvgSkill = teacherAvgSkill,
                    teacherSCount = teacherSCount,
                    teacherACount = teacherACount,
                    teacherBCount = teacherBCount,
                    teacherCCount = teacherCCount,
                    avgFatigue = avgFatigue,
                    avgLoyalty = avgLoyalty,
                    avgCourseQuality = avgCourseQuality,
                    topCourseQuality = topCourseQuality,
                    totalCourseRevenue = totalCourseRevenue,
                    coursePreparingCount = coursePreparingCount
                )
            }.collect { stats ->
                _schoolStats.value = stats
                _currentTips.value = generateTips(stats)
            }
            } catch (e: Exception) {
                android.util.Log.e("OverviewVM", "Error in stats collection", e)
            }
        }
    }

    private fun generateTips(stats: SchoolOverviewStats): List<String> {
        val tips = mutableListOf<String>()

        if (stats.totalStudents == 0) {
            tips.add("招生与迎新集中在每年9月，提前准备专业、宿舍和导师")
            tips.add("学费与科研经费共同支撑大学现金流，别把预算全部投入扩建")
        }
        if (stats.teacherCount == 0) {
            tips.add("去「教师」页面招聘老师，提高教学质量")
        }
        if (stats.totalStudents > 0 && stats.monthlyExpenses > stats.monthlyRevenue) {
            tips.add("当前入不敷出！可减少作息政策或降低教学强度节省开支")
        }
        if (stats.totalStudents > 0 && stats.totalStudents < 50) {
            tips.add("提升声誉和教学质量可以在下个9月招到更多学生")
        }
        if (stats.teacherCount > 0 && stats.totalStudents == 0) {
            tips.add("招聘了老师但还没有学生，通过营销提升声誉吸引生源")
        }
        if (stats.reputation > 500 && stats.campusLevel < 3) {
            tips.add("声誉不错了！升级校园可招更多教师、开更多班")
        }
        if (stats.researchUnlocked == 0) {
            tips.add("「科研」可以解锁研究方法，提升教学质量和招生效率")
        }
        if (stats.monthlyRevenue > stats.monthlyExpenses * 2) {
            tips.add("资金充裕！考虑「股市」投资或升级校园扩大规模")
        }

        if (tips.isEmpty()) {
            tips.add("办学顺利！继续提升科研、就业质量和社会影响力，向世界一流大学进军")
            tips.add("支出明细可在「数据报表」→「财务管理」中查看")
        }

        return tips.take(4)
    }
}
