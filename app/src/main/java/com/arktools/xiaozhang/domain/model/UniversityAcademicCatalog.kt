package com.arktools.xiaozhang.domain.model

import com.arktools.xiaozhang.domain.policy.CollegeType
import kotlin.random.Random

/**
 * 大学招生到培养的学术目录。
 * 大一按报考大类入学，大二进入具体专业；专业归属学院。
 * 学生专业存在现有 courseId 字段中，不改数据库结构。
 */
enum class AdmissionTrack(
    val displayName: String,
    val icon: String,
    val description: String,
    val college: CollegeType,
    val freshmanLabel: String
) {
    LIBERAL(
        "文史大类", "📖",
        "文史哲基础培养，大二进入人文学院专业。",
        CollegeType.LIBERAL_ARTS,
        "文史大类（未分专业）"
    ),
    SCIENCE(
        "理学大类", "🔬",
        "数理化基础培养，大二进入理学院专业。",
        CollegeType.SCIENCE,
        "理学大类（未分专业）"
    ),
    ENGINEERING(
        "工学大类", "🛠️",
        "工程基础培养，大二进入工学院专业。",
        CollegeType.ENGINEERING,
        "工学大类（未分专业）"
    ),
    BUSINESS(
        "经管大类", "💼",
        "经管基础培养，大二进入商学院专业。",
        CollegeType.BUSINESS,
        "经管大类（未分专业）"
    );

    val courseId: String get() = "TRACK_$name"
}

enum class UniversityMajor(
    val displayName: String,
    val track: AdmissionTrack,
    val description: String
) {
    CHINESE_LIT("汉语言文学", AdmissionTrack.LIBERAL, "文本解读与写作训练"),
    HISTORY("历史学", AdmissionTrack.LIBERAL, "史料与社会研究"),
    PHILOSOPHY("哲学", AdmissionTrack.LIBERAL, "逻辑、伦理与思想史"),
    MATHEMATICS("数学", AdmissionTrack.SCIENCE, "分析与建模"),
    PHYSICS("物理学", AdmissionTrack.SCIENCE, "实验与理论物理"),
    CHEMISTRY("化学", AdmissionTrack.SCIENCE, "物质结构与实验"),
    COMPUTER("计算机科学", AdmissionTrack.ENGINEERING, "程序、系统与智能"),
    MECHANICAL("机械工程", AdmissionTrack.ENGINEERING, "设计、制造与控制"),
    CIVIL("土木工程", AdmissionTrack.ENGINEERING, "结构与城市建设"),
    FINANCE("金融学", AdmissionTrack.BUSINESS, "市场、投资与风险"),
    MANAGEMENT("工商管理", AdmissionTrack.BUSINESS, "组织、运营与决策"),
    MARKETING("市场营销", AdmissionTrack.BUSINESS, "品牌、渠道与用户");

    val courseId: String get() = "MAJOR_$name"
    val college: CollegeType get() = track.college
}

object UniversityAcademicCatalog {

    fun displayName(courseId: String): String {
        parseMajor(courseId)?.let { return it.displayName }
        parseTrack(courseId)?.let { return it.freshmanLabel }
        return "大学通识"
    }

    fun collegeName(courseId: String): String {
        parseMajor(courseId)?.let { return it.college.displayName }
        parseTrack(courseId)?.let { return it.college.displayName }
        return "未分院"
    }

    fun pathLabel(gradeLevel: GradeLevel, courseId: String): String {
        return "${gradeLevel.displayName} · ${collegeName(courseId)} · ${displayName(courseId)}"
    }

    fun parseTrack(courseId: String): AdmissionTrack? {
        parseMajor(courseId)?.let { return it.track }
        if (!courseId.startsWith("TRACK_")) return null
        return try {
            AdmissionTrack.valueOf(courseId.removePrefix("TRACK_"))
        } catch (_: Exception) {
            null
        }
    }

    fun parseMajor(courseId: String): UniversityMajor? {
        if (!courseId.startsWith("MAJOR_")) return null
        return try {
            UniversityMajor.valueOf(courseId.removePrefix("MAJOR_"))
        } catch (_: Exception) {
            null
        }
    }

    fun majorsFor(track: AdmissionTrack): List<UniversityMajor> {
        return UniversityMajor.entries.filter { it.track == track }
    }

    fun pickFreshmanTrack(
        weights: AdmissionTrackPlan,
        founded: List<CollegeType>,
        random: Random = Random.Default
    ): AdmissionTrack {
        val scored = AdmissionTrack.entries.map { track ->
            val planWeight = weights.weightOf(track).coerceAtLeast(0)
            val collegeBonus = if (founded.contains(track.college)) 2 else 0
            track to (planWeight + collegeBonus).coerceAtLeast(1)
        }
        val total = scored.sumOf { it.second }
        var roll = random.nextInt(total)
        scored.forEach { (track, weight) ->
            if (roll < weight) return track
            roll -= weight
        }
        return AdmissionTrack.LIBERAL
    }

    fun pickMajor(
        track: AdmissionTrack,
        attributes: StudentAttributes,
        founded: List<CollegeType>,
        random: Random = Random.Default
    ): UniversityMajor? {
        if (!founded.contains(track.college)) return null
        val majors = majorsFor(track)
        val scored = majors.map { major ->
            val affinity = majorAffinity(major, attributes)
            major to (10 + (affinity * 20).toInt()).coerceAtLeast(1)
        }
        val total = scored.sumOf { it.second }
        var roll = random.nextInt(total)
        scored.forEach { (major, weight) ->
            if (roll < weight) return major
            roll -= weight
        }
        return majors.first()
    }

    private fun majorAffinity(major: UniversityMajor, attributes: StudentAttributes): Float {
        return when (major) {
            UniversityMajor.CHINESE_LIT, UniversityMajor.HISTORY, UniversityMajor.PHILOSOPHY ->
                (attributes.creativity + attributes.morality) / 200f
            UniversityMajor.MATHEMATICS, UniversityMajor.PHYSICS, UniversityMajor.CHEMISTRY ->
                attributes.intelligence / 100f
            UniversityMajor.COMPUTER, UniversityMajor.MECHANICAL, UniversityMajor.CIVIL ->
                (attributes.intelligence + attributes.physical) / 200f
            UniversityMajor.FINANCE, UniversityMajor.MANAGEMENT, UniversityMajor.MARKETING ->
                (attributes.social + attributes.intelligence) / 200f
        }
    }
}

data class AdmissionTrackPlan(
    val liberalWeight: Int = 3,
    val scienceWeight: Int = 3,
    val engineeringWeight: Int = 2,
    val businessWeight: Int = 2
) {
    fun totalPoints(): Int = liberalWeight + scienceWeight + engineeringWeight + businessWeight

    fun weightOf(track: AdmissionTrack): Int = when (track) {
        AdmissionTrack.LIBERAL -> liberalWeight
        AdmissionTrack.SCIENCE -> scienceWeight
        AdmissionTrack.ENGINEERING -> engineeringWeight
        AdmissionTrack.BUSINESS -> businessWeight
    }

    fun normalized(): AdmissionTrackPlan {
        val total = totalPoints().coerceAtLeast(1)
        if (total == TOTAL_POINTS) {
            return copy(
                liberalWeight = liberalWeight.coerceIn(0, TOTAL_POINTS),
                scienceWeight = scienceWeight.coerceIn(0, TOTAL_POINTS),
                engineeringWeight = engineeringWeight.coerceIn(0, TOTAL_POINTS),
                businessWeight = businessWeight.coerceIn(0, TOTAL_POINTS)
            )
        }
        val liberal = ((liberalWeight.toFloat() / total) * TOTAL_POINTS).toInt().coerceIn(0, TOTAL_POINTS)
        val science = ((scienceWeight.toFloat() / total) * TOTAL_POINTS).toInt().coerceIn(0, TOTAL_POINTS - liberal)
        val engineering = ((engineeringWeight.toFloat() / total) * TOTAL_POINTS).toInt()
            .coerceIn(0, TOTAL_POINTS - liberal - science)
        val business = (TOTAL_POINTS - liberal - science - engineering).coerceIn(0, TOTAL_POINTS)
        return AdmissionTrackPlan(liberal, science, engineering, business)
    }

    fun adjust(track: AdmissionTrack, delta: Int): AdmissionTrackPlan {
        val current = weightOf(track)
        val next = (current + delta).coerceIn(0, TOTAL_POINTS)
        val spentWithout = totalPoints() - current
        if (spentWithout + next > TOTAL_POINTS) return this
        return when (track) {
            AdmissionTrack.LIBERAL -> copy(liberalWeight = next)
            AdmissionTrack.SCIENCE -> copy(scienceWeight = next)
            AdmissionTrack.ENGINEERING -> copy(engineeringWeight = next)
            AdmissionTrack.BUSINESS -> copy(businessWeight = next)
        }
    }

    companion object {
        const val TOTAL_POINTS = 10
    }
}
