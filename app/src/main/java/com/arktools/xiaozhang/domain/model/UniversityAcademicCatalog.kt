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
    ),
    ART(
        "艺术大类", "🎨",
        "美术音乐设计基础培养，大二进入艺术学院专业。",
        CollegeType.ARTS,
        "艺术大类（未分专业）"
    ),
    MEDICINE(
        "医学大类", "🩺",
        "医学基础培养，大二进入医学院专业。",
        CollegeType.MEDICINE,
        "医学大类（未分专业）"
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
    MARKETING("市场营销", AdmissionTrack.BUSINESS, "品牌、渠道与用户"),
    FINE_ARTS("美术学", AdmissionTrack.ART, "绘画与造型基础"),
    MUSIC_PERFORMANCE("音乐表演", AdmissionTrack.ART, "声乐器乐与舞台实践"),
    VISUAL_DESIGN("视觉设计", AdmissionTrack.ART, "品牌视觉与数字媒体"),
    CLINICAL("临床医学", AdmissionTrack.MEDICINE, "诊断治疗与临床实习"),
    NURSING("护理学", AdmissionTrack.MEDICINE, "临床护理与健康照护"),
    PHARMACY("药学", AdmissionTrack.MEDICINE, "药物研发与临床药学");

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

    fun collegeOf(courseId: String): CollegeType? {
        parseMajor(courseId)?.let { return it.college }
        return parseTrack(courseId)?.college
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

    fun requiredRoles(college: CollegeType): List<TeacherRole> = when (college) {
        CollegeType.LIBERAL_ARTS -> listOf(TeacherRole.CHINESE, TeacherRole.HISTORY, TeacherRole.POLITICS)
        CollegeType.SCIENCE -> listOf(TeacherRole.MATH, TeacherRole.PHYSICS, TeacherRole.CHEMISTRY)
        CollegeType.ENGINEERING -> listOf(TeacherRole.MATH, TeacherRole.PHYSICS, TeacherRole.GEOGRAPHY)
        CollegeType.BUSINESS -> listOf(TeacherRole.ENGLISH, TeacherRole.POLITICS, TeacherRole.MATH)
        CollegeType.ARTS -> listOf(TeacherRole.ART, TeacherRole.MUSIC, TeacherRole.CHINESE)
        CollegeType.MEDICINE -> listOf(TeacherRole.BIOLOGY, TeacherRole.CHEMISTRY, TeacherRole.MATH)
    }

    fun requiredRoles(courseId: String): List<TeacherRole> {
        parseMajor(courseId)?.let { return requiredRoles(it.college) }
        parseTrack(courseId)?.let { return requiredRoles(it.college) }
        return listOf(TeacherRole.CHINESE, TeacherRole.MATH, TeacherRole.ENGLISH)
    }

    fun matchingTeachers(courseId: String, teachers: List<Teacher>): List<Teacher> {
        val roles = requiredRoles(courseId)
        val matched = teachers.filter { it.isWorking && it.role in roles }
        return matched.ifEmpty { teachers.filter { it.isWorking } }
    }

    fun facultyCoverage(
        founded: List<CollegeType>,
        teachers: List<Teacher>
    ): FacultyCoverage {
        val working = teachers.filter { it.isWorking }
        val colleges = founded.ifEmpty { listOf(CollegeType.LIBERAL_ARTS) }
        val lines = colleges.map { college ->
            val roles = requiredRoles(college)
            val covered = roles.count { role -> working.any { it.role == role } }
            FacultyCoverageLine(
                college = college,
                required = roles.size,
                covered = covered,
                missingRoles = roles.filter { role -> working.none { it.role == role } }
            )
        }
        val totalRequired = lines.sumOf { it.required }
        val totalCovered = lines.sumOf { it.covered }
        return FacultyCoverage(
            lines = lines,
            coverageRatio = if (totalRequired <= 0) 1f else totalCovered.toFloat() / totalRequired.toFloat()
        )
    }

    fun preferredIndustries(courseId: String): List<IndustryPreference> {
        val major = parseMajor(courseId)
        val track = parseTrack(courseId)
        return when (major ?: return defaultIndustries(track)) {
            UniversityMajor.CHINESE_LIT, UniversityMajor.HISTORY, UniversityMajor.PHILOSOPHY ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 4),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.MEDIA, 3),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.GOVERNMENT, 2),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.LAW, 1)
                )
            UniversityMajor.MATHEMATICS, UniversityMajor.PHYSICS, UniversityMajor.CHEMISTRY ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.RESEARCH, 4),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 3),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.TECHNOLOGY, 2),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.HEALTHCARE, 1)
                )
            UniversityMajor.COMPUTER ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.TECHNOLOGY, 5),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.FINANCE, 2),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.RESEARCH, 1)
                )
            UniversityMajor.MECHANICAL, UniversityMajor.CIVIL ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.ENGINEERING, 5),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.COMMERCE, 2),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.GOVERNMENT, 1)
                )
            UniversityMajor.FINANCE ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.FINANCE, 5),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.COMMERCE, 2),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.GOVERNMENT, 1)
                )
            UniversityMajor.MANAGEMENT, UniversityMajor.MARKETING ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.COMMERCE, 4),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.MEDIA, 2),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.FINANCE, 2)
                )
            UniversityMajor.FINE_ARTS, UniversityMajor.MUSIC_PERFORMANCE ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.MEDIA, 4),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 3),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.COMMERCE, 1)
                )
            UniversityMajor.VISUAL_DESIGN ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.TECHNOLOGY, 3),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.MEDIA, 3),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.COMMERCE, 2)
                )
            UniversityMajor.CLINICAL, UniversityMajor.NURSING, UniversityMajor.PHARMACY ->
                listOf(
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.HEALTHCARE, 5),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.RESEARCH, 2),
                    IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 1)
                )
        }
    }

    private fun defaultIndustries(track: AdmissionTrack?): List<IndustryPreference> {
        return when (track) {
            AdmissionTrack.LIBERAL -> listOf(
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 3),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.MEDIA, 2),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.GOVERNMENT, 2)
            )
            AdmissionTrack.SCIENCE -> listOf(
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.RESEARCH, 3),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 2),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.TECHNOLOGY, 2)
            )
            AdmissionTrack.ENGINEERING -> listOf(
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.ENGINEERING, 4),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.TECHNOLOGY, 3)
            )
            AdmissionTrack.BUSINESS -> listOf(
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.FINANCE, 3),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.COMMERCE, 3)
            )
            AdmissionTrack.ART -> listOf(
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.MEDIA, 4),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 2)
            )
            AdmissionTrack.MEDICINE -> listOf(
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.HEALTHCARE, 4),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.RESEARCH, 2)
            )
            null -> listOf(
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.COMMERCE, 2),
                IndustryPreference(com.arktools.xiaozhang.domain.employment.Industry.EDUCATION, 2)
            )
        }
    }

    fun pickIndustry(courseId: String, random: Random): com.arktools.xiaozhang.domain.employment.Industry {
        val prefs = preferredIndustries(courseId)
        val total = prefs.sumOf { it.weight }
        var roll = random.nextInt(total.coerceAtLeast(1))
        prefs.forEach { pref ->
            if (roll < pref.weight) return pref.industry
            roll -= pref.weight
        }
        return prefs.first().industry
    }

    fun pickIndustry(courseId: String, random: java.util.Random): com.arktools.xiaozhang.domain.employment.Industry {
        val prefs = preferredIndustries(courseId)
        val total = prefs.sumOf { it.weight }
        var roll = random.nextInt(total.coerceAtLeast(1))
        prefs.forEach { pref ->
            if (roll < pref.weight) return pref.industry
            roll -= pref.weight
        }
        return prefs.first().industry
    }

    fun pickIndustryDeterministic(courseId: String, index: Int): com.arktools.xiaozhang.domain.employment.Industry {
        val prefs = preferredIndustries(courseId)
        val weighted = prefs.flatMap { pref -> List(pref.weight) { pref.industry } }
        if (weighted.isEmpty()) return com.arktools.xiaozhang.domain.employment.Industry.COMMERCE
        return weighted[kotlin.math.abs(index) % weighted.size]
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

    fun affinityScore(major: UniversityMajor, attributes: StudentAttributes): Float {
        return majorAffinity(major, attributes)
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
            UniversityMajor.FINE_ARTS, UniversityMajor.MUSIC_PERFORMANCE, UniversityMajor.VISUAL_DESIGN ->
                (attributes.creativity * 0.7f + attributes.social * 0.3f) / 100f
            UniversityMajor.CLINICAL, UniversityMajor.NURSING, UniversityMajor.PHARMACY ->
                (attributes.intelligence * 0.5f + attributes.morality * 0.3f + attributes.physical * 0.2f) / 100f
        }
    }
}

data class AdmissionTrackPlan(
    val liberalWeight: Int = 2,
    val scienceWeight: Int = 2,
    val engineeringWeight: Int = 2,
    val businessWeight: Int = 2,
    val artsWeight: Int = 1,
    val medicineWeight: Int = 1
) {
    fun totalPoints(): Int =
        liberalWeight + scienceWeight + engineeringWeight + businessWeight + artsWeight + medicineWeight

    fun weightOf(track: AdmissionTrack): Int = when (track) {
        AdmissionTrack.LIBERAL -> liberalWeight
        AdmissionTrack.SCIENCE -> scienceWeight
        AdmissionTrack.ENGINEERING -> engineeringWeight
        AdmissionTrack.BUSINESS -> businessWeight
        AdmissionTrack.ART -> artsWeight
        AdmissionTrack.MEDICINE -> medicineWeight
    }

    fun normalized(): AdmissionTrackPlan {
        val total = totalPoints().coerceAtLeast(1)
        if (total == TOTAL_POINTS) {
            return copy(
                liberalWeight = liberalWeight.coerceIn(0, TOTAL_POINTS),
                scienceWeight = scienceWeight.coerceIn(0, TOTAL_POINTS),
                engineeringWeight = engineeringWeight.coerceIn(0, TOTAL_POINTS),
                businessWeight = businessWeight.coerceIn(0, TOTAL_POINTS),
                artsWeight = artsWeight.coerceIn(0, TOTAL_POINTS),
                medicineWeight = medicineWeight.coerceIn(0, TOTAL_POINTS)
            )
        }
        var remaining = TOTAL_POINTS
        fun scale(weight: Int): Int {
            val v = if (remaining <= 0) 0
            else ((weight.toFloat() / total) * TOTAL_POINTS).toInt().coerceIn(0, remaining)
            remaining -= v
            return v
        }
        val liberal = scale(liberalWeight)
        val science = scale(scienceWeight)
        val engineering = scale(engineeringWeight)
        val business = scale(businessWeight)
        val arts = scale(artsWeight)
        val medicine = scale(medicineWeight).coerceAtLeast(0)
        return AdmissionTrackPlan(liberal, science, engineering, business, arts, medicine)
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
            AdmissionTrack.ART -> copy(artsWeight = next)
            AdmissionTrack.MEDICINE -> copy(medicineWeight = next)
        }
    }

    companion object {
        const val TOTAL_POINTS = 10
    }
}

data class FacultyCoverage(
    val lines: List<FacultyCoverageLine>,
    val coverageRatio: Float
) {
    val missingSummary: String
        get() {
            val missing = lines.filter { it.missingRoles.isNotEmpty() }
            if (missing.isEmpty()) return "各学院核心师资已配齐"
            return missing.joinToString("；") { line ->
                "${line.college.displayName}缺${line.missingRoles.joinToString("、") { it.displayName }}"
            }
        }
}

data class FacultyCoverageLine(
    val college: CollegeType,
    val required: Int,
    val covered: Int,
    val missingRoles: List<TeacherRole>
)

data class IndustryPreference(
    val industry: com.arktools.xiaozhang.domain.employment.Industry,
    val weight: Int
)
