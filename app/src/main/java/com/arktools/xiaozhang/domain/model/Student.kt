package com.arktools.xiaozhang.domain.model

import java.util.UUID
import kotlin.random.Random

/**
 * 学生实体 —— 学校大亨2 核心实体
 *
 * 生命周期: ENROLLED → STUDYING → GRADUATED / DROPPED
 * 组织层级: 学校 → 年级(GradeLevel) → 班级(SchoolClass) → 学生(Student)
 *
 * 每个学生拥有五维属性，绑定到一个班级和课程，
 * 设施、教师、班风会持续影响五维成长。
 * 毕业后产生口碑(正面/负面)，影响学校声誉。
 */
data class Student(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val courseId: String,
    val schoolId: String,

    // ======= 组织归属 =======
    val classId: String? = null,                        // 所属班级ID
    val gradeLevel: GradeLevel = GradeLevel.GRADE_1,   // 年级

    // ======= 五维属性系统 =======
    var attributes: StudentAttributes = StudentAttributes(),
    val backgroundTier: BackgroundTier = BackgroundTier.NORMAL,  // 家庭背景

    // ======= 旧属性（兼容保留） =======
    val talent: Float = Random.nextFloat() * 0.4f + 0.6f,        // 天赋 0.6~1.0
    val motivation: Float = Random.nextFloat() * 0.3f + 0.7f,    // 学习动力 0.7~1.0
    val traits: List<StudentTrait> = emptyList(),                 // 学生特质

    // ======= 状态机 =======
    var status: StudentStatus = StudentStatus.ENROLLED,

    // ======= 学期掌握度 (0~100, 每学期重置, 影响考试成绩) =======
    var semesterMastery: Float = 0f,

    // ======= 高考与升学 =======
    var gaoKaoScore: Float = 0f,                        // 高考总分(0~750)
    var admittedUniversity: String? = null,              // 录取大学名称
    var universityTier: UniversityTier? = null,          // 录取大学等级

    // ======= 满意度 (0~100, 影响口碑和退学概率) =======
    var satisfaction: Float = 70f,

    // ======= 成绩分 (毕业时结算, 0~100) =======
    var academicScore: Float = 0f,

    // ======= 健康与生活 =======
    var healthStatus: HealthStatus = HealthStatus.HEALTHY,
    var mealQuality: Float = 50f,              // 饮食质量 (0~100, 食堂影响)
    var dormSatisfaction: Float = 50f,         // 住宿满意度 (0~100, 宿舍影响)
    var exerciseLevel: Float = 30f,            // 运动量 (0~100, 运动场影响)
    var consecutiveSickDays: Int = 0,          // 连续生病天数（超过阈值自动请假）

    // ======= 入学时间 =======
    val enrollYear: Int = 0,
    val enrollMonth: Int = 0,
    val lastPromotionYear: Int = 0,

    // ======= 毕业时间 =======
    var graduateYear: Int? = null,
    var graduateMonth: Int? = null,
    val graduationProjectionState: Int = 0,

    // ======= 口碑评价 (毕业/退学时生成) =======
    var review: StudentReview? = null
) {
    // ======= 兼容属性（新逻辑优先使用 attributes） =======

    /** 有效智力（兼容旧talent用法） */
    val effectiveIntelligence: Float
        get() = attributes.intelligence / 100f

    /** 有效学习动力（兼容旧motivation用法） */
    val effectiveMotivation: Float
        get() = (attributes.social + attributes.morality) / 200f

    /** 是否能正常上课（健康状态检查） */
    val canAttendClass: Boolean
        get() = healthStatus.canAttendClass

    /** 学习效率倍率（基于健康状态） */
    val learningMultiplier: Float
        get() = healthStatus.learningMultiplier

    /** 五维综合评级 */
    val attributeGrade: AttributeGrade
        get() = attributes.grade
}

enum class StudentStatus(val displayName: String) {
    ENROLLED("已入学"),      // 刚入学，等待分配
    STUDYING("学习中"),      // 正在上课学习
    GRADUATED("已毕业"),     // 毕业
    DROPPED("已退学")        // 中途退学（满意度过低）
}

/**
 * 学生口碑评价
 */
data class StudentReview(
    val rating: Int,           // 1~5星
    val comment: String,       // 评语
    val reputationImpact: Long // 对学校声誉的影响值
)

/**
 * 学生特质 (少量关键特质，影响学习和满意度 + 五维成长速率)
 */
enum class StudentTrait(
    val displayName: String,
    val description: String,
    val isPositive: Boolean,
    // 五维成长修正系数 (1.0=无影响, >1加速, <1减速)
    val intelligenceMod: Float = 1.0f,
    val physicalMod: Float = 1.0f,
    val socialMod: Float = 1.0f,
    val creativityMod: Float = 1.0f,
    val moralityMod: Float = 1.0f
) {
    DILIGENT("勤奋", "学习速度+20%, 智力成长+15%", true,
        intelligenceMod = 1.15f, moralityMod = 1.1f),
    GIFTED("天才", "天赋值额外+0.2, 智力成长+25%", true,
        intelligenceMod = 1.25f, creativityMod = 1.1f),
    SOCIAL("社交达人", "满意度衰减-30%, 社交成长+20%", true,
        socialMod = 1.2f),
    COMPETITIVE("好胜", "高质量课程额外满意度加成, 智力+10%", true,
        intelligenceMod = 1.1f),
    WEALTHY("富家子弟", "退学阈值更高，初始属性+", true),
    ATHLETIC("运动健将", "体力成长+25%, 体育活动加成", true,
        physicalMod = 1.25f),
    ARTISTIC("文艺青年", "创造力成长+20%, 艺术课加成", true,
        creativityMod = 1.2f),
    SENSITIVE("敏感", "满意度波动更大, 社交成长-10%", false,
        socialMod = 0.9f),
    LAZY("懒惰", "学习速度-15%, 体力成长-10%", false,
        intelligenceMod = 0.9f, physicalMod = 0.9f),
    REBELLIOUS("叛逆", "品德成长-20%, 但创造力+10%", false,
        moralityMod = 0.8f, creativityMod = 1.1f)
}

/**
 * 学生特质分配器 — 入学时随机分配0~3个特质
 */
object StudentTraitAssigner {
    private val positiveTraits = StudentTrait.entries.filter { it.isPositive }
    private val negativeTraits = StudentTrait.entries.filter { !it.isPositive }

    fun assignTraits(): List<StudentTrait> {
        val result = mutableListOf<StudentTrait>()
        // 35% 概率获得一个正面特质
        if (Random.nextFloat() < 0.35f) {
            result.add(positiveTraits.random())
        }
        // 20% 概率获得第二个正面特质（不重复）
        if (Random.nextFloat() < 0.20f) {
            val extra = positiveTraits.filter { it !in result }.randomOrNull()
            if (extra != null) result.add(extra)
        }
        // 15% 概率获得一个负面特质
        if (Random.nextFloat() < 0.15f) {
            result.add(negativeTraits.random())
        }
        // 5% 概率额外获得一个特质(任意)
        if (Random.nextFloat() < 0.05f) {
            val extra = StudentTrait.entries.filter { it !in result }.randomOrNull()
            if (extra != null) result.add(extra)
        }
        return result
    }
}

/**
 * 学生名字生成器 - 中国姓名
 */
object StudentNameGenerator {
    private val surnames = listOf(
        "王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴",
        "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗",
        "梁", "宋", "郑", "谢", "韩", "唐", "冯", "于", "董", "萧",
        "程", "曹", "袁", "邓", "许", "傅", "沈", "曾", "彭", "吕"
    )

    private val maleNames = listOf(
        "伟", "强", "磊", "军", "勇", "杰", "涛", "明", "超", "华",
        "志远", "天宇", "浩然", "子轩", "一鸣", "思源", "博文", "嘉诚",
        "俊熙", "宇航", "泽宇", "晨阳", "瑞祥", "文昊", "鹏程"
    )

    private val femaleNames = listOf(
        "芳", "娟", "静", "敏", "燕", "丽", "艳", "玲", "婷", "慧",
        "诗涵", "梦琪", "雨桐", "子萱", "欣怡", "思彤", "佳颖", "若溪",
        "雅琳", "紫萱", "语嫣", "沐晨", "芷若", "清雅", "月华"
    )

    fun generate(): String {
        val surname = surnames.random()
        val isMale = Random.nextBoolean()
        val given = if (isMale) maleNames.random() else femaleNames.random()
        return "$surname$given"
    }
}

/**
 * 学生满意度计算器（已升级支持五维属性）
 */
object StudentSatisfactionCalculator {

    /**
     * 计算每日满意度变化
     * 正面因素: 课程质量高、教师技能高、设施好、五维高
     * 负面因素: 课程质量低、教师频繁请假、设施差、生病
     */
    fun calculateDailySatisfactionDelta(
        courseQuality: Float,
        teacherAvgSkill: Float,
        facilityBonus: Float,
        studentMotivation: Float,
        traits: List<StudentTrait> = emptyList(),
        attributes: StudentAttributes = StudentAttributes(),
        healthStatus: HealthStatus = HealthStatus.HEALTHY
    ): Float {
        // 基线: 高质量课程(>5.5分)满意度正增长
        val qualityEffect = (courseQuality - 5.5f) * 0.3f
        val teacherEffect = (teacherAvgSkill - 50f) / 200f
        val facilityEffect = facilityBonus * 0.2f
        val motivationEffect = (studentMotivation - 0.8f) * 0.1f

        // 五维对满意度的额外影响
        val socialEffect = (attributes.social - 50f) / 500f        // 社交高→满意度更稳
        val moralityEffect = (attributes.morality - 50f) / 800f    // 品德高→少抱怨

        // 健康对满意度的影响
        val healthEffect = when (healthStatus) {
            HealthStatus.HEALTHY -> 0f
            HealthStatus.FATIGUED -> -0.2f
            HealthStatus.SICK -> -0.8f
            HealthStatus.INJURED -> -0.5f
        }

        var delta = qualityEffect + teacherEffect + facilityEffect +
                motivationEffect + socialEffect + moralityEffect + healthEffect

        // 特质修正
        if (StudentTrait.SOCIAL in traits && delta < 0) {
            delta *= 0.7f  // 社交达人减少负面满意度衰减
        }
        if (StudentTrait.SENSITIVE in traits) {
            delta *= 1.5f  // 敏感→波动大
        }
        if (StudentTrait.COMPETITIVE in traits && courseQuality > 7f) {
            delta += 0.3f  // 好胜→高质量课程加成
        }

        return delta
    }

    /**
     * 计算退学概率 (每日检查)
     */
    fun calculateDropoutProbability(satisfaction: Float, traits: List<StudentTrait> = emptyList()): Float {
        val wealthyBonus = if (StudentTrait.WEALTHY in traits) 10f else 0f
        val effectiveSatisfaction = satisfaction + wealthyBonus

        return when {
            effectiveSatisfaction <= 10f -> 0.05f
            effectiveSatisfaction <= 20f -> 0.02f
            effectiveSatisfaction <= 30f -> 0.005f
            effectiveSatisfaction <= 40f -> 0.001f
            else -> 0f
        }
    }

    /**
     * 生成毕业评价
     */
    fun generateReview(student: Student, courseQuality: Float): StudentReview {
        val rating = when {
            student.satisfaction >= 85f && student.academicScore >= 80f -> 5
            student.satisfaction >= 70f && student.academicScore >= 60f -> 4
            student.satisfaction >= 50f -> 3
            student.satisfaction >= 30f -> 2
            else -> 1
        }

        val comment = when (rating) {
            5 -> listOf(
                "非常感谢学校的培养，收获满满！",
                "老师教学质量很高，强烈推荐！",
                "三年学习改变了我的人生轨迹。",
                "学校管理有方，设施完善，极力推荐。",
                "五维均衡发展，综合素质大幅提升。"
            ).random()
            4 -> listOf(
                "整体很满意，学到了不少东西。",
                "教学质量不错，性价比高。",
                "老师认真负责，推荐报名。",
                "环境不错，课程设计合理。",
                "班级氛围很好，同学关系融洽。"
            ).random()
            3 -> listOf(
                "一般般吧，中规中矩。",
                "还行，但有改进空间。",
                "课程内容可以，但服务一般。",
                "没有太大惊喜，也没太大失望。"
            ).random()
            2 -> listOf(
                "不太满意，期望有落差。",
                "老师经常请假，影响学习进度。",
                "设施陈旧，管理混乱。",
                "性价比不高，不太推荐。"
            ).random()
            else -> listOf(
                "非常失望，浪费时间和金钱。",
                "教学质量堪忧，强烈不推荐。",
                "管理混乱，学不到东西。",
                "后悔选择这所学校。"
            ).random()
        }

        val reputationImpact = when (rating) {
            5 -> 5L
            4 -> 2L
            3 -> 0L
            2 -> -3L
            else -> -8L
        }

        return StudentReview(
            rating = rating,
            comment = comment,
            reputationImpact = reputationImpact
        )
    }

    /**
     * 退学时的负面评价
     */
    fun generateDropoutReview(student: Student): StudentReview {
        val comment = listOf(
            "实在无法忍受，选择退学。",
            "教学质量太差，果断退出。",
            "浪费了我的时间，非常不满。",
            "管理问题严重，不得不离开。"
        ).random()

        return StudentReview(
            rating = 1,
            comment = comment,
            reputationImpact = -12L
        )
    }
}

/**
 * 大学录取等级
 */
enum class UniversityTier(val displayName: String, val minScore: Float, val reputationBonus: Long) {
    QINGBEI("清华/北大", 700f, 50L),
    TOP_985("顶尖985", 650f, 20L),
    NORMAL_985("985", 620f, 10L),
    TOP_211("重点211", 580f, 5L),
    NORMAL_211("211", 540f, 3L),
    FIRST_TIER("一本", 500f, 1L),
    SECOND_TIER("二本", 430f, 0L),
    JUNIOR_COLLEGE("专科", 350f, -2L),
    NONE("未上线", 0f, -5L);

    companion object {
        fun fromScore(score: Float): UniversityTier {
            return entries.sortedByDescending { it.minScore }.first { score >= it.minScore }
        }
    }
}

/**
 * 学生学期掌握度计算（重构：不再控制毕业，仅影响考试成绩）
 */
object StudentProgressCalculator {

    /**
     * 计算每日学期掌握度增长
     * 每学期约150天(5个月)，从0涨到60~95取决于教学质量和学生能力
     * 不再控制毕业！毕业由年级制+高考决定
     */
    fun calculateDailySemesterMastery(
        courseScale: CourseScale,
        talent: Float,
        courseQuality: Float,
        teacherSkill: Float,
        traits: List<StudentTrait> = emptyList(),
        attributes: StudentAttributes = StudentAttributes(),
        healthMultiplier: Float = 1.0f
    ): Float {
        // 基础掌握度增长：每学期(~150天)期望达到约70%掌握度
        val baseMastery = when (courseScale) {
            CourseScale.INTEREST -> 0.35f       // 兴趣培养：慢但轻松
            CourseScale.IMPROVEMENT -> 0.45f    // 提高教学：标准速度
            CourseScale.COMPETITION -> 0.55f    // 竞赛强化：快速
            CourseScale.FULL_TIME -> 0.50f      // 全日制：稳定
            CourseScale.INTERNATIONAL -> 0.48f  // 国际课程：中等
        }

        // 使用五维中的智力替代原talent
        val effectiveIntelligence = if (attributes.intelligence != 50f) {
            attributes.intelligence / 100f
        } else {
            talent
        }

        // 天赋+GIFTED特质
        val effectiveTalent = if (StudentTrait.GIFTED in traits) {
            (effectiveIntelligence + 0.2f).coerceAtMost(1.2f)
        } else {
            effectiveIntelligence
        }
        val talentMultiplier = 0.8f + effectiveTalent * 0.4f

        // v2.8: 降低courseQuality权重，避免semesterMastery过早满100
        // 原来 /50f 范围[0.9, 2.9]太高；改为 /80f 范围[0.9, 2.15]
        val qualityMultiplier = 0.9f + courseQuality / 80f
        val teacherMultiplier = 0.9f + teacherSkill / 500f

        // 特质速度修正
        var traitMultiplier = 1.0f
        if (StudentTrait.DILIGENT in traits) traitMultiplier *= 1.15f
        if (StudentTrait.LAZY in traits) traitMultiplier *= 0.85f

        return baseMastery * talentMultiplier * qualityMultiplier *
                teacherMultiplier * traitMultiplier * healthMultiplier
    }

    /**
     * 计算学业成绩（考试时使用，基于五维+掌握度）
     * 目标分布：均值60~65, 标准差约15, 范围[10, 100]
     */
    fun calculateAcademicScore(
        talent: Float,
        satisfaction: Float,
        courseQuality: Float,
        motivation: Float,
        attributes: StudentAttributes = StudentAttributes(),
        semesterMastery: Float = 50f
    ): Float {
        // 智力为主导因子：intelligence(35~65) → 贡献 21~39 分
        val intellFactor = if (attributes.intelligence != 50f) {
            attributes.intelligence * 0.6f  // 35→21, 50→30, 65→39
        } else {
            talent * 35f  // 0.6→21, 0.8→28, 1.0→35
        }
        // 掌握度：semesterMastery(0~100) → 贡献 0~25 分
        val masteryFactor = semesterMastery * 0.25f

        // 其他因素微调（共贡献约 ±5 分）
        val creativityFactor = (attributes.creativity - 50f) * 0.04f  // ±2分
        val moralityFactor = (attributes.morality - 50f) * 0.03f      // ±1.5分
        val satisfactionAdjust = (satisfaction - 70f) * 0.02f          // ±0.6分
        val courseAdjust = (courseQuality - 50f) * 0.06f                // ±3分
        val motivationAdjust = (motivation - 0.85f) * 8f               // ±1.2分

        val base = intellFactor + masteryFactor + creativityFactor + moralityFactor +
                satisfactionAdjust + courseAdjust + motivationAdjust
        val noise = (Random.nextFloat() + Random.nextFloat() - 1f) * 10f  // 三角分布 ±10
        return (base + noise).coerceIn(10f, 100f)
    }
}
