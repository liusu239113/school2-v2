package com.arktools.xiaozhang.domain.model

import java.util.UUID

// ==================== 大学教学管理系统 ====================

/**
 * 班型 - 学校的核心分层机制
 */
enum class ClassTier(
    val displayName: String,
    val maxSize: Int,
    val teacherRatio: Float,       // 师生比（越高越好）
    val scoreMultiplier: Float,    // 对成绩提升的基础倍率
    val setupCost: Double,         // 开设一个班的初始费用（万元）
    val monthlyCost: Double        // 每月维护费用（万元）
) {
    ROCKET("拔尖培养班", 30, 0.5f, 1.4f, 5.0, 0.5),
    KEY("专业核心班", 40, 0.35f, 1.2f, 3.0, 0.3),
    NORMAL("通识教学班", 50, 0.25f, 1.0f, 1.0, 0.15),
    ART("艺术方向班", 35, 0.3f, 0.8f, 4.0, 0.4),
    MUSIC("音乐方向班", 35, 0.3f, 0.8f, 4.0, 0.4),
    SPORTS("体育方向班", 40, 0.3f, 0.7f, 2.0, 0.25)
}

/**
 * 文理方向 / 选科组合
 * 大一统一学习，大二开始分科
 */
enum class SubjectTrack(
    val displayName: String,
    val subjects: List<Subject>,     // 主攻科目
    val universityBonus: Float,      // 对高考录取的加成
    val difficulty: Float            // 难度系数（影响成绩离散度）
) {
    SCIENCE("理科", listOf(Subject.PHYSICS, Subject.CHEMISTRY, Subject.BIOLOGY), 0.05f, 1.1f),
    LIBERAL_ARTS("文科", listOf(Subject.HISTORY, Subject.GEOGRAPHY, Subject.POLITICS), 0f, 0.9f),
    COMPREHENSIVE("文理兼修", listOf(Subject.PHYSICS, Subject.CHEMISTRY, Subject.HISTORY), 0.02f, 1.0f);

    companion object {
        val DEFAULT = COMPREHENSIVE
    }
}

/**
 * 教学强度 - 全校统一或按班型设置
 */
enum class TeachingIntensity(
    val displayName: String,
    val description: String,
    val scoreMultiplier: Float,       // 成绩提升倍率
    val satisfactionPenalty: Float,   // 每月满意度扣减
    val parentComplaintRate: Float,   // 家长投诉概率/月
    val teacherBurnoutRate: Float,    // 教师疲劳累积/月
    val monthlyCostMultiplier: Float  // 费用倍率
) {
    RELAXED("轻松", "快乐教育，注重素质", 0.7f, 0f, 0f, 0f, 0.8f),
    NORMAL("正常", "劳逸结合，稳步提升", 1.0f, -0.05f, 0.02f, 0.02f, 1.0f),
    INTENSIVE("加强", "多练多考，重点突破", 1.3f, -0.15f, 0.05f, 0.05f, 1.2f),
    HELLISH("魔鬼", "军事化管理，题海战术", 1.6f, -0.35f, 0.12f, 0.1f, 1.5f);

    companion object {
        val DEFAULT = NORMAL
    }
}

/**
 * 作息政策 - 可叠加启用多个
 */
enum class SchedulePolicy(
    val displayName: String,
    val description: String,
    val monthlyCostPerClass: Double,  // 每个班每月额外费用（万元）
    val scoreBonus: Float,            // 对成绩的额外加成
    val satisfactionCost: Float,      // 满意度扣减/月
    val teacherExtraLoad: Float       // 教师额外负担
) {
    MORNING_STUDY("早自习", "6:00到校早读", 0.2, 0.04f, -0.06f, 0.03f),
    EVENING_STUDY("晚自习", "晚自习到22:00", 0.3, 0.06f, -0.08f, 0.05f),
    WEEKEND_CLASS("周末补课", "周六全天上课", 0.6, 0.10f, -0.15f, 0.08f),
    HOLIDAY_CAMP("假期集训", "寒暑假补课30天", 1.2, 0.12f, -0.20f, 0.10f),
    DAILY_TEST("日日清考试", "每天放学前小测", 0.1, 0.05f, -0.10f, 0.04f),
    RANKING_POSTED("成绩排名张贴", "公开成绩排名", 0.0, 0.03f, -0.12f, 0f)
}

/**
 * 特殊项目 - 竞赛/艺考/体育特招等
 */
enum class SpecialProgram(
    val displayName: String,
    val description: String,
    val setupCost: Double,         // 开设费用（万元）
    val monthlyMaintain: Double,   // 每月维护费（万元）
    val minTeachers: Int,          // 需要最少几个相关学科教师
    val requiredSubject: Subject?, // 需要的学科教师（null=不限）
    val maxStudents: Int,          // 最大参与学生数
    val bonusType: ProgramBonusType
) {
    MATH_OLYMPIAD("数学竞赛", "冲击全国数学联赛，保送清北", 20.0, 5.0, 2, Subject.MATH, 15, ProgramBonusType.COMPETITION),
    PHYSICS_OLYMPIAD("物理竞赛", "冲击物理奥赛金牌", 20.0, 5.0, 2, Subject.PHYSICS, 15, ProgramBonusType.COMPETITION),
    CHEMISTRY_OLYMPIAD("化学竞赛", "冲击化学奥赛奖牌", 15.0, 4.0, 2, Subject.CHEMISTRY, 15, ProgramBonusType.COMPETITION),
    INFORMATICS("信息学奥赛", "NOI/IOI竞赛培训", 25.0, 6.0, 2, Subject.MATH, 20, ProgramBonusType.COMPETITION),
    ART_EXAM("美术集训", "联考/校考冲刺训练", 10.0, 3.0, 2, Subject.ART, 30, ProgramBonusType.ART_EXAM),
    MUSIC_EXAM("音乐集训", "音乐艺考专项训练", 10.0, 3.0, 2, Subject.MUSIC, 25, ProgramBonusType.ART_EXAM),
    SPORTS_ELITE("体育特训", "体育单招/高水平运动队", 8.0, 2.5, 2, Subject.PE, 25, ProgramBonusType.SPORTS),
    PSYCHOLOGY("心理辅导站", "学生心理健康辅导", 5.0, 1.5, 1, null, 0, ProgramBonusType.SATISFACTION),
    PEER_TUTORING("学生互助辅导", "优生帮扶后进生", 1.0, 0.5, 0, null, 0, ProgramBonusType.PEER_HELP)
}

enum class ProgramBonusType {
    COMPETITION,    // 保送/加分机会
    ART_EXAM,       // 艺考降分录取
    SPORTS,         // 体育特招
    SATISFACTION,   // 提升满意度
    PEER_HELP       // 提升后进生成绩
}

// ==================== 教学配置（替代旧CourseProject） ====================

/**
 * 学校的教学总配置 - 存储在School对象中或独立表
 */
data class TeachingConfig(
    val id: String = UUID.randomUUID().toString(),
    val classDistribution: Map<ClassTier, Int> = emptyMap(),  // 默认为空，要求玩家主动配置
    val subjectTrack: SubjectTrack = SubjectTrack.DEFAULT,  // 大二后生效
    val intensity: TeachingIntensity = TeachingIntensity.DEFAULT,
    val schedulePolicies: Set<SchedulePolicy> = setOf(SchedulePolicy.EVENING_STUDY),
    val specialPrograms: Set<SpecialProgram> = emptySet(),
    val scienceToArtsRatio: Float = 0.6f,  // 理科班占比（大二分科后）
    val weeklyPEHours: Int = 2,            // 每周体育课时
    val monthlyExamFrequency: Int = 1      // 每月统考次数(0-4)
) {
    /** 总班级数 */
    val totalClasses: Int get() = classDistribution.values.sum()

    /** 总容量 */
    val totalCapacity: Int get() = classDistribution.entries.sumOf { (tier, count) -> tier.maxSize * count }

    /** 每月总运营成本（万元）*/
    fun monthlyOperatingCost(): Double {
        val classCost = classDistribution.entries.sumOf { (tier, count) ->
            tier.monthlyCost * count * intensity.monthlyCostMultiplier
        }
        val scheduleCost = schedulePolicies.sumOf { it.monthlyCostPerClass } * totalClasses
        val programCost = specialPrograms.sumOf { it.monthlyMaintain }
        return classCost + scheduleCost + programCost
    }

    /** 综合教学质量评分 (0-10) */
    fun overallQuality(avgTeacherSkill: Float): Float {
        val baseQuality = avgTeacherSkill / 10f  // 教师技能0-100 → 0-10
        val intensityFactor = intensity.scoreMultiplier
        val scheduleFactor = 1f + schedulePolicies.sumOf { it.scoreBonus.toDouble() }.toFloat()
        return (baseQuality * intensityFactor * scheduleFactor).coerceIn(0f, 10f)
    }

    /** 综合满意度影响/月 */
    fun monthlySatisfactionImpact(): Float {
        val intensityPenalty = intensity.satisfactionPenalty
        val schedulePenalty = schedulePolicies.sumOf { it.satisfactionCost.toDouble() }.toFloat()
        val peBonos = (weeklyPEHours - 1).coerceAtLeast(0) * 0.02f  // 体育课加满意度
        val psychBonus = if (SpecialProgram.PSYCHOLOGY in specialPrograms) 0.05f else 0f
        return intensityPenalty + schedulePenalty + peBonos + psychBonus
    }
}

// ==================== 保留的旧枚举（向后兼容） ====================
// SubjectCategory 已定义在 Teacher.kt 中，此处不再重复

enum class Subject(val displayName: String, val category: SubjectCategory) {
    CHINESE("语文", SubjectCategory.LITERATURE),
    MATH("数学", SubjectCategory.SCIENCE),
    ENGLISH("英语", SubjectCategory.LANGUAGE),
    PHYSICS("物理", SubjectCategory.SCIENCE),
    CHEMISTRY("化学", SubjectCategory.SCIENCE),
    BIOLOGY("生物", SubjectCategory.SCIENCE),
    HISTORY("历史", SubjectCategory.LITERATURE),
    GEOGRAPHY("地理", SubjectCategory.LITERATURE),
    POLITICS("政治", SubjectCategory.LITERATURE),
    ART("美术", SubjectCategory.ART),
    PE("体育", SubjectCategory.SPORTS),
    MUSIC("音乐", SubjectCategory.ART);

    /** 原始卷面分满分：语文、数学、英语为 150，其余科目为 100。 */
    val maxScore: Float
        get() = when (this) {
            CHINESE, MATH, ENGLISH -> 150f
            else -> 100f
        }

    /**
     * 将任意卷面分按指定满分归一化为 0..100。
     * [scoreMax] 用于读取旧存档：缺失满分信息的历史记录必须按旧 100 分制处理。
     */
    fun normalizeScore(rawScore: Float, scoreMax: Float = maxScore): Float {
        val effectiveMax = scoreMax.takeIf { it > 0f } ?: maxScore
        return (rawScore / effectiveMax * 100f).coerceIn(0f, 100f)
    }

    /** 将 0..100 的得分率换算为当前科目的卷面分。 */
    fun rawScoreFromNormalized(normalizedScore: Float): Float =
        (normalizedScore.coerceIn(0f, 100f) / 100f) * maxScore
}

/**
 * CourseScale - 保留用于GameEngine中updateStudentProgress的fallback
 * 新系统不再主动使用，但避免大面积编译错误
 */
enum class CourseScale(
    val displayName: String,
    val minTeamSize: Int,
    val maxTeamSize: Int,
    val basePreparationDays: Int,
    val baseCost: Double,
    val qualityCap: Float
) {
    INTEREST("兴趣班", 1, 3, 45, 10.0, 7.5f),
    IMPROVEMENT("提高班", 3, 5, 120, 30.0, 8.0f),
    COMPETITION("竞赛班", 5, 10, 240, 80.0, 8.5f),
    FULL_TIME("全日制", 10, 20, 450, 200.0, 9.0f),
    INTERNATIONAL("国际部", 20, 50, 900, 600.0, 9.5f)
}

// ==================== 旧类型兼容桩（防止编译错误） ====================

/** @deprecated 保留供旧代码编译通过，新系统使用TeachingConfig */
data class CourseProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "通用课程",
    val subject: Subject = Subject.CHINESE,
    val theme: CourseTheme = CourseTheme.EXAM_PREP,
    val courseType: CourseType = CourseType.OFFLINE_LARGE,
    val targetDistrict: DistrictType = DistrictType.LOCAL,
    val scale: CourseScale = CourseScale.FULL_TIME,
    var preparationProgress: Float = 100f,
    var problemCount: Int = 0,
    var qualityScore: Float = 6f,
    var designScore: Float = 5f,
    var status: CourseStatus = CourseStatus.RELEASED,
    val teamIds: List<String> = emptyList(),
    val methodIds: List<String> = emptyList(),
    val ipId: String? = null,
    var enrollment: Long = 0,
    var revenue: Double = 0.0,
    var monthlyEnrollment: Long = 0,
    var releaseDate: Long? = null,
    var releaseYear: Int? = null,
    var releaseMonth: Int? = null,
    var heat: Float = 100f,
    var marketingSpend: Double = 0.0
)

/** @deprecated 兼容旧代码 */
enum class CourseTheme(val displayName: String) {
    EXAM_PREP("应试备考"), INTEREST("兴趣培养"), COMPETITION("竞赛冲刺"),
    PRACTICAL("实践应用"), CREATIVE("创新思维"), TRADITIONAL("传统文化"),
    INTERNATIONAL("国际视野"), STEM("STEM教育"), ARTISTIC("艺术修养"), SPORTS("体育健康")
}

/** @deprecated 兼容旧代码 */
enum class CourseType(val displayName: String, val baseCostMultiplier: Double) {
    OFFLINE_SMALL("线下小班", 1.0), OFFLINE_LARGE("线下大班", 1.5),
    ONLINE_LIVE("线上直播", 0.8), RECORDED("录播课", 0.5), ONE_ON_ONE("一对一", 2.0)
}

/** @deprecated 兼容旧代码 */
enum class DistrictType(
    val displayName: String, val commissionRate: Double, val baseExposure: Double,
    val reputationThreshold: Long, val maxConcurrentCourses: Int,
    val requiredSchoolLevel: Int, val description: String
) {
    LOCAL("本地学区", 0.10, 1.0, 0L, 3, 1, "本地"),
    CROSS_DISTRICT("跨区学区", 0.15, 1.8, 500L, 4, 3, "跨区"),
    ONLINE_PLATFORM("线上平台", 0.25, 2.5, 1200L, 5, 4, "线上"),
    INTERNATIONAL("国际学区", 0.20, 3.5, 5000L, 4, 5, "国际"),
    ELITE_ALLIANCE("精英联盟", 0.18, 5.0, 15000L, 6, 6, "顶尖名校合作联盟"),
    GLOBAL_NETWORK("全球教育网", 0.22, 8.0, 50000L, 8, 6, "全球化教育协作网络")
}

/** @deprecated 兼容旧代码 */
enum class CourseStatus(val displayName: String) {
    PLANNING("立项中"), PREPARING("备课中"), TESTING("试讲中"), RELEASED("已开课"), CLOSED("已结课")
}
