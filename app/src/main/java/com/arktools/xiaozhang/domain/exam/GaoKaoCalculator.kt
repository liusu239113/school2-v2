package com.arktools.xiaozhang.domain.exam

import com.arktools.xiaozhang.domain.model.Student
import com.arktools.xiaozhang.domain.model.StudentTrait
import com.arktools.xiaozhang.domain.model.UniversityTier
import kotlin.random.Random

/**
 * 高考系统
 *
 * 高三6月进行高考，是学生三年学习的最终检验。
 * 高考分数(满分750)决定录取大学等级。
 *
 * 分数影响因素：
 * - 学生五维属性（智力为主）
 * - 三年考试成绩积累(academicScore)
 * - 当前学期掌握度(semesterMastery)
 * - 教师平均水平
 * - 考场发挥随机波动
 */
object GaoKaoCalculator {

    /**
     * 计算高考总分 (满分750)
     *
     * v2.8 系统级重新设计——模拟真实高考成绩分布:
     *
     * 配合递减回报机制（属性增长在60+后急剧放缓），3年后学生属性分布：
     * - 普通学生: intelligence 55~70, academicScore 45~65
     * - 优秀学生: intelligence 70~80, academicScore 65~80
     * - 顶尖学生: intelligence 80+, academicScore 80+ (极少数)
     *
     * 对应高考分数分布：
     * - 普通学生: 350~500分 → 专科/二本
     * - 优秀学生: 500~600分 → 一本/211
     * - 顶尖学生: 600~700分 → 985
     * - 清北(700+): 全年级仅0~2人
     *
     * 设计原则：
     * 1. 智力是主导因素但有递减回报（属性系统保证了不会全满）
     * 2. 学业积累体现三年努力
     * 3. 考场随机波动大（±50分），体现临场发挥
     * 4. 总分结构：智力基础(~300) + 学业(~180) + 历史(~70) + 教师(~30) + 综合(~15) + 特质(±25) + 运气(±50)
     */
    fun calculateScore(
        student: Student,
        teacherAvgSkill: Float,
        examHistory: List<StudentScore> = emptyList()
    ): Float {
        // 1. 智力基础分: 使用非线性映射
        //    intelligence=55 → 225, =65 → 280, =75 → 330, =85 → 370
        //    高区间增长放缓，避免属性差5分导致高考差50分
        val intNorm = student.attributes.intelligence / 100f  // 0~1
        val intelligenceScore = (intNorm * intNorm * 0.3f + intNorm * 0.7f) * 450f
        // intNorm=0.55 → (0.0908+0.385)*450=214, =0.65 → (0.127+0.455)*450=262
        // intNorm=0.75 → (0.169+0.525)*450=312, =0.85 → (0.217+0.595)*450=365

        // 2. 学业积累分: academicScore (一般45~70)
        //    45*2.8=126, 60*2.8=168, 75*2.8=210, 90*2.8=252
        val academicScoreVal = student.academicScore * 2.8f

        // 3. 考试历史分: 反映稳定性 (0~70)
        val historyScore = if (examHistory.isNotEmpty()) {
            val recentScores = examHistory.takeLast(20).map { it.normalizedScore }
            val avg = recentScores.average().toFloat()
            // 标准差惩罚：波动大的学生扣分
            val variance = if (recentScores.size > 1) {
                recentScores.map { (it - avg) * (it - avg) }.average().toFloat()
            } else 0f
            val stabilityPenalty = (kotlin.math.sqrt(variance) * 0.4f).coerceAtMost(15f)
            (avg * 0.7f - stabilityPenalty).coerceIn(0f, 70f)
        } else {
            student.semesterMastery * 0.5f
        }

        // 4. 教师加成 (0~30) — 教师对高考的影响主要通过日常教学间接体现
        //    注意：teacherAvgSkill 是 averageSkill，量纲 0-1000（非 0-100）
        //    此前误用 /100 导致此项最高达 300 分，把全员高考分推高 ~180+，本科率/清北率畸高
        val teacherBonus = (teacherAvgSkill / 1000f) * 30f

        // 5. 五维综合加成 (0~15) — 非智力因素的微小加成
        val attributeBonus = (
            student.attributes.creativity * 0.06f +
            student.attributes.morality * 0.04f +
            student.attributes.physical * 0.02f +
            student.attributes.social * 0.03f
        )

        // 6. 特质加成（清晰的正负差异）
        var traitBonus = 0f
        if (StudentTrait.GIFTED in student.traits) traitBonus += 25f
        if (StudentTrait.DILIGENT in student.traits) traitBonus += 15f
        if (StudentTrait.COMPETITIVE in student.traits) traitBonus += 10f
        if (StudentTrait.LAZY in student.traits) traitBonus -= 35f
        if (StudentTrait.REBELLIOUS in student.traits) traitBonus -= 20f

        // 7. 考场发挥随机波动 (±50分)
        //    使用两个随机数叠加使分布接近正态（中间概率高，极端概率低）
        val luck = (Random.nextFloat() + Random.nextFloat() - 1f) * 50f

        val total = intelligenceScore + academicScoreVal + historyScore +
                teacherBonus + attributeBonus + traitBonus + luck

        return total.coerceIn(150f, 750f)
    }

    /**
     * 根据高考分数录取大学（使用固定分数线，已废弃，保留兼容）
     */
    fun admitUniversity(score: Float): Pair<UniversityTier, String> {
        val tier = UniversityTier.fromScore(score)
        val name = getRandomUniversityName(tier)
        return tier to name
    }

    /**
     * 根据高考分数和当年动态录取线录取大学
     */
    fun admitUniversityDynamic(score: Float, scoreLines: AnnualScoreLines): Pair<UniversityTier, String> {
        val tier = scoreLines.getTierForScore(score)
        val name = getRandomUniversityName(tier)
        return tier to name
    }

    /**
     * 生成当年高考录取分数线（模拟真实高考年度波动）
     *
     * 每年各批次分数线在基准值上下浮动，幅度 ±30 分
     * 同时保持层级之间的最小间距（至少20分）
     */
    fun generateAnnualScoreLines(year: Int): AnnualScoreLines {
        val random = Random(year * 7919L) // 用年份做种子，同年结果一致
        val fluctuation = { base: Float ->
            base + random.nextFloat() * 60f - 30f // ±30
        }

        // 从高到低生成，保证层级间至少间隔20分
        val qingbei = fluctuation(700f).coerceIn(670f, 730f)
        val top985 = fluctuation(650f).coerceIn(620f, (qingbei - 20f).coerceAtLeast(620f))
        val normal985 = fluctuation(620f).coerceIn(590f, (top985 - 20f).coerceAtLeast(590f))
        val top211 = fluctuation(580f).coerceIn(550f, (normal985 - 20f).coerceAtLeast(550f))
        val normal211 = fluctuation(540f).coerceIn(510f, (top211 - 20f).coerceAtLeast(510f))
        val firstTier = fluctuation(500f).coerceIn(460f, (normal211 - 20f).coerceAtLeast(460f))
        val secondTier = fluctuation(430f).coerceIn(380f, (firstTier - 20f).coerceAtLeast(380f))
        val juniorCollege = fluctuation(350f).coerceIn(300f, (secondTier - 20f).coerceAtLeast(300f))

        return AnnualScoreLines(
            year = year,
            lines = mapOf(
                UniversityTier.QINGBEI to qingbei,
                UniversityTier.TOP_985 to top985,
                UniversityTier.NORMAL_985 to normal985,
                UniversityTier.TOP_211 to top211,
                UniversityTier.NORMAL_211 to normal211,
                UniversityTier.FIRST_TIER to firstTier,
                UniversityTier.SECOND_TIER to secondTier,
                UniversityTier.JUNIOR_COLLEGE to juniorCollege
            )
        )
    }

    /**
     * 计算升学率对应的动态声望奖惩
     *
     * 替代原来的固定 reputationBonus 累加机制
     * 根据整届毕业生的升学率综合评定，体现"教得好才有好名声"
     */
    fun calculateReputationFromGraduation(stats: GraduationStats): Long {
        if (stats.totalStudents == 0) return 0L

        var reputation = 0L

        // 基础声望：本科率决定（60%以下扣，80%以上奖）
        reputation += when {
            stats.bengkeLv >= 95f -> 30L
            stats.bengkeLv >= 85f -> 20L
            stats.bengkeLv >= 75f -> 10L
            stats.bengkeLv >= 60f -> 3L
            stats.bengkeLv >= 45f -> -5L
            stats.bengkeLv >= 30f -> -15L
            else -> -30L  // 本科率不到30%，声望重创
        }

        // 985加成：每个985学生+3声望
        reputation += stats.key985Count * 3L

        // 清北特殊加成：每个清北+15声望
        reputation += stats.qingbeiCount * 15L

        return reputation
    }

    /**
     * 计算升学成绩对应的现金奖励（政府奖金 + 校友捐赠）
     *
     * 只有985+的学生才会带来额外现金
     * 模拟：政府"高考奖金"、优秀校友捐款、社会赞助
     *
     * 递减机制：前几名全额，超出递减，防止大校后期一次性获得数亿
     * 硬上限：500万（对应现实中一所学校每年能获得的政府奖金上限）
     */
    fun calculateGraduationBonus(stats: GraduationStats): Double {
        var bonus = 0.0

        // 清北学生：前5人每人50万全额，超出部分每人10万（递减）
        val qbFull = stats.qingbeiCount.coerceAtMost(5)
        val qbExtra = (stats.qingbeiCount - 5).coerceAtLeast(0)
        bonus += qbFull * 50.0 + qbExtra * 10.0

        // 985学生（含清北）：前20人每人10万，超出部分每人2万（递减）
        val k985Full = stats.key985Count.coerceAtMost(20)
        val k985Extra = (stats.key985Count - 20).coerceAtLeast(0)
        bonus += k985Full * 10.0 + k985Extra * 2.0

        // 本科率超80%的额外奖金（教育局表彰）
        if (stats.bengkeLv >= 80f && stats.totalStudents >= 10) {
            bonus += 30.0  // 30万教育局表彰
        }

        // 硬上限：500万（防止超大学校经济失衡）
        return bonus.coerceAtMost(500.0)
    }

    /**
     * 生成随机大学名称
     */
    private fun getRandomUniversityName(tier: UniversityTier): String = when (tier) {
        UniversityTier.QINGBEI -> listOf("清华大学", "北京大学").random()
        UniversityTier.TOP_985 -> listOf(
            "复旦大学", "上海交通大学", "浙江大学",
            "中国科学技术大学", "南京大学"
        ).random()
        UniversityTier.NORMAL_985 -> listOf(
            "武汉大学", "华中科技大学", "中山大学",
            "四川大学", "西安交通大学", "哈尔滨工业大学",
            "同济大学", "东南大学", "北京理工大学"
        ).random()
        UniversityTier.TOP_211 -> listOf(
            "北京邮电大学", "上海财经大学", "中央财经大学",
            "对外经济贸易大学", "北京外国语大学", "华东理工大学",
            "南京航空航天大学", "西安电子科技大学"
        ).random()
        UniversityTier.NORMAL_211 -> listOf(
            "苏州大学", "南京师范大学", "华南师范大学",
            "郑州大学", "南昌大学", "云南大学",
            "太原理工大学", "安徽大学"
        ).random()
        UniversityTier.FIRST_TIER -> listOf(
            "浙江工业大学", "首都师范大学", "南方医科大学",
            "上海理工大学", "杭州电子科技大学", "重庆邮电大学"
        ).random()
        UniversityTier.SECOND_TIER -> listOf(
            "省属本科院校", "地方本科大学", "普通二本院校",
            "应用型本科学院"
        ).random()
        UniversityTier.JUNIOR_COLLEGE -> listOf(
            "高职院校", "职业技术学院", "专科学校"
        ).random()
        UniversityTier.NONE -> "未录取"
    }

    /**
     * 计算一届毕业生的升学统计
     */
    fun calculateGraduationStats(students: List<Student>): GraduationStats {
        if (students.isEmpty()) return GraduationStats()

        val total = students.size
        val scores = students.map { it.gaoKaoScore }
        val avgScore = scores.average().toFloat()
        val maxScore = scores.max()

        val bengkeCount = students.count {
            it.universityTier != null && it.universityTier != UniversityTier.NONE &&
            it.universityTier != UniversityTier.JUNIOR_COLLEGE
        }
        val key985Count = students.count {
            it.universityTier in listOf(
                UniversityTier.QINGBEI, UniversityTier.TOP_985, UniversityTier.NORMAL_985
            )
        }
        val qingbeiCount = students.count { it.universityTier == UniversityTier.QINGBEI }

        return GraduationStats(
            totalStudents = total,
            averageScore = avgScore,
            highestScore = maxScore,
            bengkeLv = bengkeCount.toFloat() / total * 100f,
            key985Count = key985Count,
            qingbeiCount = qingbeiCount,
            bengkeCount = bengkeCount
        )
    }
}

/**
 * 一届毕业生升学统计
 */
data class GraduationStats(
    val totalStudents: Int = 0,
    val averageScore: Float = 0f,
    val highestScore: Float = 0f,
    val bengkeLv: Float = 0f,        // 本科率(%)
    val key985Count: Int = 0,        // 985录取人数
    val qingbeiCount: Int = 0,       // 清北录取人数
    val bengkeCount: Int = 0         // 本科录取人数
)

/**
 * 当年高考录取分数线（动态波动）
 *
 * 模拟真实高考：每年各批次录取线会根据试卷难度、报名人数等因素浮动。
 * 玩家无法预测今年分数线，增加不确定性和挑战性。
 */
data class AnnualScoreLines(
    val year: Int,
    val lines: Map<UniversityTier, Float>   // 各层级对应的最低分数线
) {
    /**
     * 根据分数确定录取层级
     */
    fun getTierForScore(score: Float): UniversityTier {
        // 按分数线从高到低排序，取第一个满足的
        return lines.entries
            .sortedByDescending { it.value }
            .firstOrNull { score >= it.value }?.key
            ?: UniversityTier.NONE
    }

    /**
     * 获取指定层级的分数线
     */
    fun getLineForTier(tier: UniversityTier): Float {
        return lines[tier] ?: tier.minScore
    }

    /**
     * 格式化显示分数线（用于事件通知）
     */
    fun formatForDisplay(): String = buildString {
        append("清北线: ${lines[UniversityTier.QINGBEI]?.toInt()}分")
        append("  985线: ${lines[UniversityTier.TOP_985]?.toInt()}分")
        append("  一本线: ${lines[UniversityTier.FIRST_TIER]?.toInt()}分")
        append("  本科线: ${lines[UniversityTier.SECOND_TIER]?.toInt()}分")
    }
}
