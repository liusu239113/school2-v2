package com.arktools.xiao.domain.model

import com.arktools.xiao.domain.policy.CollegeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 学科与专业建设：
 * - 每个学院 2 个学科，学院成立后可投钱建设（等级 1~5）
 * - 两年一次（偶数年 6 月）学科评估，按建设度定级 D/C/B/A/A+
 * - 评级反哺：招生生源质量、声誉、财政评估奖励
 * - 状态内嵌 policyJson 持久化（CollegeDevelopment.disciplinesJson），不改数据库
 */
object DisciplineCatalog {

    @Serializable
    data class Def(
        val id: String,
        val name: String,
        val college: CollegeType,
        val desc: String
    )

    @Serializable
    data class State(
        val level: Int = 0,              // 0 = 未开设，1~5 建设等级
        val investWan: Double = 0.0,     // 累计投入
        val lastRating: String = "NONE", // D/C/B/A/A+/NONE
        val lastEvalYear: Int = 0
    )

    val ALL: List<Def> = listOf(
        Def("H_CHINESE", "汉语言文学", CollegeType.LIBERAL_ARTS, "基础文科门面，稳生源、利声誉"),
        Def("H_HISTORY", "历史学", CollegeType.LIBERAL_ARTS, "冷门但评估容易出精品"),
        Def("S_MATH", "数学与应用数学", CollegeType.SCIENCE, "全校科研的地基，评估权重高"),
        Def("S_PHYSICS", "应用物理学", CollegeType.SCIENCE, "对接工科，实验室越多越强"),
        Def("E_COMPUTER", "计算机科学与技术", CollegeType.ENGINEERING, "就业率发动机，生员抢手"),
        Def("E_MECHANIC", "机械设计制造", CollegeType.ENGINEERING, "校企合作友好，就业稳定"),
        Def("B_MGMT", "工商管理", CollegeType.BUSINESS, "招生大户，评估靠社会合作"),
        Def("B_FINANCE", "金融学", CollegeType.BUSINESS, "高分生源，声誉转化快"),
        Def("A_DESIGN", "视觉设计", CollegeType.ARTS, "竞赛露脸机会多"),
        Def("A_MUSIC", "音乐表演", CollegeType.ARTS, "校园文化氛围担当"),
        Def("M_CLINICAL", "临床医学", CollegeType.MEDICINE, "附属医院加成，评估最难"),
        Def("M_NURSING", "护理学", CollegeType.MEDICINE, "就业率极高，投入回本快")
    )

    fun byId(id: String): Def? = ALL.firstOrNull { it.id == id }
    fun byCollege(college: CollegeType): List<Def> = ALL.filter { it.college == college }

    const val MAX_LEVEL = 5

    /** 升到 level+1 的费用（万元） */
    fun upgradeCostWan(currentLevel: Int): Double {
        if (currentLevel >= MAX_LEVEL) return 0.0
        return (25.0 * (currentLevel + 1) * Math.pow(1.4, currentLevel.toDouble())).toInt().toDouble()
    }

    /** 单学科建设对生源质量的加成（乘法增量） */
    fun enrollBonus(state: State): Float = when (state.level) {
        0 -> 0f
        1 -> 0.01f
        2 -> 0.025f
        3 -> 0.045f
        4 -> 0.07f
        else -> 0.10f
    }

    fun bonusLabel(state: State): String = when (state.level) {
        0 -> "未开设"
        else -> "生源 +${(enrollBonus(state) * 100).toInt()}%"
    }

    /** 评估定级：主要看建设等级，带少量波动（0~7） */
    fun evaluate(state: State, roll: Int): String {
        val score = state.level * 20 + (roll % 8) - 3
        return when {
            state.level <= 0 -> "D"
            score >= 100 -> "A+"
            score >= 80 -> "A"
            score >= 60 -> "B"
            score >= 40 -> "C"
            else -> "D"
        }
    }

    data class EvalOutcome(
        val rating: String,
        val reputation: Long,
        val grantWan: Double,
        val headline: String
    )

    fun outcomeOf(rating: String): EvalOutcome = when (rating) {
        "A+" -> EvalOutcome("A+", 400, 120.0, "国家级一流学科")
        "A" -> EvalOutcome("A", 220, 70.0, "省级一流学科")
        "B" -> EvalOutcome("B", 100, 35.0, "评估良好")
        "C" -> EvalOutcome("C", 30, 10.0, "评估合格")
        else -> EvalOutcome("D", -40, 0.0, "评估预警，需加大建设")
    }

    // ===== 持久化 =====
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(states: Map<String, State>): String =
        runCatching { json.encodeToString(states) }.getOrDefault("")

    fun decode(raw: String): Map<String, State> =
        if (raw.isBlank()) emptyMap()
        else runCatching { json.decodeFromString<Map<String, State>>(raw) }.getOrDefault(emptyMap())
}
