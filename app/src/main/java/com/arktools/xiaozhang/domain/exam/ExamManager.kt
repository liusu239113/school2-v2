package com.arktools.xiaozhang.domain.exam

import com.arktools.xiaozhang.domain.model.GradeLevel
import com.arktools.xiaozhang.domain.model.Student
import com.arktools.xiaozhang.domain.model.Subject
import com.arktools.xiaozhang.domain.model.SubjectTrack
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 考试/成绩管理器
 *
 * 自动在每学期中期和期末组织考试，根据学生属性和教师质量生成成绩。
 * 考试类型：阶段考核(3/4/5/9/10/11月)、期中(4/10月)、期末(1/7月)
 */
@Singleton
class ExamManager @Inject constructor() {

    companion object {
        /** 每个学生最多保留的成绩条数（防止数据库膨胀导致 SQLiteBlobTooBigException）
         *  6条 × 1000学生 × ~100bytes ≈ 600KB，加上其他24个JSON字段仍在2MB安全线内 */
        private const val MAX_SCORES_PER_STUDENT = 6
        private const val CURRENT_SCORE_SCHEME_VERSION = 2
        private const val LEGACY_MAX_SCORE = 100f
    }

    // 考试记录
    private val examRecords: MutableList<ExamRecord> = mutableListOf()
    // 学生成绩记录
    private val studentScores: MutableMap<String, MutableList<StudentScore>> = mutableMapOf()

    /**
     * 月度推进：判断是否需要组织考试
     * @param monthlyExamFrequency 每月额外统考次数(0-4)，影响阶段考核是否触发
     * @param intensityScoreMultiplier 教学强度对成绩的倍率
     */
    fun advanceMonth(
        year: Int,
        month: Int,
        students: List<Student>,
        teacherAvgSkill: Float,
        monthlyExamFrequency: Int = 1,
        intensityScoreMultiplier: Float = 1.0f,
        teachers: List<Teacher> = emptyList()
    ): ExamResult {
        val examType = getExamTypeForMonth(month, monthlyExamFrequency) ?: return ExamResult()

        // 生成考试记录
        val exam = ExamRecord(
            id = "${year}_${month}_${examType.name}",
            year = year,
            month = month,
            type = examType,
            participantCount = students.size
        )

        // 为每个学生生成各科成绩
        val newScores = mutableListOf<StudentScore>()
        students.forEach { student ->
            val scores = generateStudentScores(student, examType, teacherAvgSkill, intensityScoreMultiplier, exam.id, teachers)
            newScores.addAll(scores)
            val list = studentScores.getOrPut(student.id) { mutableListOf() }
            list.addAll(scores)
            // 内存中也限制每学生最多保留最近10次考试的成绩，防止内存和数据库膨胀
            if (list.size > MAX_SCORES_PER_STUDENT) {
                val excess = list.size - MAX_SCORES_PER_STUDENT
                repeat(excess) { list.removeAt(0) }
            }
        }

        // 计算班级平均分
        // 所有汇总分都使用 0..100 的归一化分，避免不同科目满分影响比较。
        val avgScore = normalizedAverage(newScores)

        exam.averageScore = avgScore
        examRecords.add(exam)

        // 限制历史记录数量
        if (examRecords.size > 24) {
            examRecords.removeAt(0)
        }

        // 将考试成绩回写到学生的 academicScore（加权移动平均）
        students.forEach { student ->
            val thisExamScores = newScores.filter { it.studentId == student.id }
            if (thisExamScores.isNotEmpty()) {
                val examAvg = normalizedAverage(thisExamScores)
                // 使用加权平均：70% 旧成绩 + 30% 新考试成绩（首次考试直接赋值）
                student.academicScore = if (student.academicScore <= 0f) {
                    examAvg
                } else {
                    student.academicScore * 0.7f + examAvg * 0.3f
                }
            }
        }

        return ExamResult(
            examHeld = true,
            examType = examType,
            participantCount = students.size,
            averageScore = avgScore,
            topStudents = getTopStudents(newScores, 3),
            examId = exam.id
        )
    }

    /**
     * 清理已不在校学生的成绩记录，防止内存无限增长。
     * 应在每月结算时调用，传入当前在校学生ID集合。
     */
    fun cleanupInactiveStudents(activeStudentIds: Set<String>) {
        val keysToRemove = studentScores.keys.filter { it !in activeStudentIds }
        keysToRemove.forEach { studentScores.remove(it) }
    }

    /**
     * 获取学生的成绩历史
     */
    fun getStudentScores(studentId: String): List<StudentScore> {
        return studentScores[studentId] ?: emptyList()
    }

    /**
     * 获取学生最近一次考试的各科成绩
     */
    fun getStudentLatestScores(studentId: String): List<StudentScore> {
        val scores = studentScores[studentId] ?: return emptyList()
        if (scores.isEmpty()) return emptyList()
        val latestExamId = scores.last().examId
        return scores.filter { it.examId == latestExamId }
    }

    /**
     * 获取学生在指定科目的归一化成绩趋势（0..100）。
     * 统一返回得分率，保证历史 100 分制记录与当前 150 分制记录可比较。
     */
    fun getStudentSubjectTrend(studentId: String, subject: Subject): List<Float> {
        return (studentScores[studentId] ?: emptyList())
            .filter { it.subject == subject }
            .map { it.normalizedScore }
    }

    /**
     * 获取考试历史
     */
    fun getExamHistory(): List<ExamRecord> = examRecords.toList()

    /**
     * 获取最近一次考试
     */
    fun getLatestExam(): ExamRecord? = examRecords.lastOrNull()

    /**
     * 获取班级在最近考试中的排名
     */
    fun getClassRanking(classId: String, students: List<Student>): Float {
        val classStudentIds = students.filter { it.classId == classId }.map { it.id }.toSet()
        if (classStudentIds.isEmpty()) return 0f

        val latestExam = examRecords.lastOrNull() ?: return 0f
        val classScores = studentScores.filterKeys { it in classStudentIds }
            .values.flatten()
            .filter { it.examId == latestExam.id }

        return normalizedAverage(classScores)
    }

    // ======= 内部方法 =======

    private fun getExamTypeForMonth(month: Int, monthlyExamFrequency: Int = 1): ExamType? {
        return when (month) {
            1 -> ExamType.FINAL_EXAM      // 上学期期末
            4 -> ExamType.MIDTERM          // 下学期期中
            7 -> ExamType.FINAL_EXAM      // 下学期期末
            10 -> ExamType.MIDTERM         // 上学期期中
            3, 5, 9, 11 -> {
                // 阶段考核频率控制：frequency=0不考, 1=正常阶段考核月才考, 2+=每月都考
                if (monthlyExamFrequency >= 1) ExamType.MONTHLY_TEST else null
            }
            2, 6, 8, 12 -> {
                // 非传统阶段考核月份：仅当频率>=2时才加考
                if (monthlyExamFrequency >= 2) ExamType.MONTHLY_TEST else null
            }
            else -> null
        }
    }

    private fun generateStudentScores(
        student: Student,
        examType: ExamType,
        teacherAvgSkill: Float,
        intensityScoreMultiplier: Float = 1.0f,
        examRecordId: String = "",
        teachers: List<Teacher> = emptyList()
    ): List<StudentScore> {
        val subjects = getExamSubjects(student.gradeLevel, examType)

        // 构建科目→教师技能映射（按TeacherRole与Subject对应）
        val subjectTeacherSkillMap = buildSubjectTeacherSkillMap(teachers)

        return subjects.map { subject ->
            // 查找该科目对应教师的教学技能，找不到则 fallback 到全局平均
            val subjectTeacherSkill = subjectTeacherSkillMap[subject] ?: teacherAvgSkill

            // === 重新设计的成绩公式 ===
            // 目标：正态分布，均值~60-65，标准差~15
            // intelligence(35~65) 是主导因素，motivation/talent 作为微调

            // 1. 智力基础分：intelligence 直接映射到 30~70 的基础分区间
            //    intelligence=35 → 基础30, intelligence=65 → 基础70, intelligence=50 → 基础50
            val intelligenceBase = (student.attributes.intelligence * 0.8f + 10f)  // 35→38, 50→50, 65→62, 范围约 [30, 62]

            // 2. 学习态度修正：motivation 贡献 ±8 分调整
            //    motivation=0.7 → -4分, motivation=0.85 → 0分, motivation=1.0 → +4分
            val motivationAdjust = (student.motivation - 0.85f) * 50f  // 范围约 [-7.5, +7.5]

            // 3. 天赋修正：talent 贡献 ±6 分
            //    talent=0.6 → -4.8分, talent=0.8 → 0分, talent=1.0 → +4.8分
            val talentAdjust = (student.talent - 0.8f) * 24f  // 范围约 [-4.8, +4.8]

            // 4. 教师质量修正（乘法因子）：好老师提升，差老师降低
            //    注意：subjectTeacherSkill 量纲 0-1000（teacher.teaching），非 0-100
            //    此前误用 *0.004 → 600技能得 3.2倍，baseAbility 被顶满 92 分（人人考满分），
            //    导致 academicScore 恒满进而把高考分推高。系数改为 *0.0004 对齐 0-1000 量纲
            //    500→×1.0, 800→×1.12, 300→×0.92
            val teacherFactor = 0.8f + subjectTeacherSkill * 0.0004f

            // 5. 基础能力值（未含随机波动）
            val baseAbility = ((intelligenceBase + motivationAdjust + talentAdjust) * teacherFactor)
                .coerceIn(20f, 92f)

            val subjectBonus = getSubjectBonus(student, subject)

            // 6. 随机波动加大：模拟真实考试中的发挥差异 ±12分
            val randomFactor = (Random.nextFloat() + Random.nextFloat() - 1f) * 12f  // 三角分布，中心0，范围±12

            val difficulty = when (examType) {
                ExamType.MONTHLY_TEST -> 0f
                ExamType.MIDTERM -> -5f
                ExamType.FINAL_EXAM -> -8f
            }
            // 教学强度倍率影响最终分数（使用递减收益，避免极端强度下全员满分）
            // 将乘法改为加权加成：基础分 + (基础分 × (倍率-1) × 衰减因子)
            val rawBase = baseAbility + subjectBonus + randomFactor + difficulty
            val intensityBonus = rawBase * (intensityScoreMultiplier - 1f) * 0.6f
            // 先生成 0..100 得分率，再按科目卷面满分换算原始分。
            val normalizedScore = (rawBase + intensityBonus).coerceIn(10f, 100f)
            val rawScore = subject.rawScoreFromNormalized(normalizedScore)

            StudentScore(
                studentId = student.id,
                studentName = student.name,
                classId = student.classId ?: "",
                examId = examRecordId,
                subject = subject,
                score = rawScore,
                rank = 0,  // 稍后计算
                maxScore = subject.maxScore
            )
        }
    }

    /**
     * 构建科目→教师教学技能映射
     * TeacherRole 与 Subject 按序号一一对应（CHINESE↔CHINESE, MATH↔MATH, ...）
     * 同一科目有多位教师时取教学技能最高者
     */
    private fun buildSubjectTeacherSkillMap(teachers: List<Teacher>): Map<Subject, Float> {
        if (teachers.isEmpty()) return emptyMap()
        val subjectValues = Subject.entries
        val roleValues = TeacherRole.entries
        val result = mutableMapOf<Subject, Float>()

        teachers.filter { it.isWorking && !it.isOnVacation }.forEach { teacher ->
            // TeacherRole 和 Subject 枚举按相同顺序定义
            val roleIndex = teacher.role.ordinal
            if (roleIndex < subjectValues.size) {
                val subject = subjectValues[roleIndex]
                val currentSkill = result[subject] ?: 0f
                val teacherSkill = teacher.teaching.toFloat()
                if (teacherSkill > currentSkill) {
                    result[subject] = teacherSkill
                }
            }
        }
        return result
    }

    private fun getSubjectBonus(student: Student, subject: Subject): Float {
        return when (subject.category) {
            com.arktools.xiaozhang.domain.model.SubjectCategory.SCIENCE ->
                (student.attributes.intelligence - 50f) * 0.2f
            com.arktools.xiaozhang.domain.model.SubjectCategory.LITERATURE ->
                (student.attributes.creativity - 50f) * 0.15f
            com.arktools.xiaozhang.domain.model.SubjectCategory.SPORTS ->
                (student.attributes.physical - 50f) * 0.3f
            com.arktools.xiaozhang.domain.model.SubjectCategory.ART ->
                (student.attributes.creativity - 50f) * 0.25f
            else -> 0f
        }
    }

    private fun getExamSubjects(gradeLevel: GradeLevel, examType: ExamType): List<Subject> {
        val core = listOf(Subject.CHINESE, Subject.MATH, Subject.ENGLISH)
        val additionalSubjects = when (gradeLevel) {
            GradeLevel.GRADE_1 -> listOf(
                Subject.PHYSICS, Subject.CHEMISTRY, Subject.BIOLOGY,
                Subject.HISTORY, Subject.GEOGRAPHY, Subject.POLITICS
            )
            GradeLevel.GRADE_2, GradeLevel.GRADE_3, GradeLevel.GRADE_4 -> SubjectTrack.DEFAULT.subjects
        }
        return when (examType) {
            ExamType.MONTHLY_TEST -> core
            ExamType.MIDTERM, ExamType.FINAL_EXAM -> core + additionalSubjects
        }
    }

    private fun normalizedAverage(scores: List<StudentScore>): Float =
        if (scores.isEmpty()) 0f else scores.map { it.normalizedScore }.average().toFloat()

    private fun getTopStudents(scores: List<StudentScore>, count: Int): List<String> {
        return scores.groupBy { it.studentId }
            .mapValues { (_, studentScores) -> normalizedAverage(studentScores) }
            .entries.sortedByDescending { it.value }
            .take(count)
            .mapNotNull { entry ->
                scores.find { it.studentId == entry.key }?.studentName
            }
    }

    // ======= 持久化 =======

    fun toJson(): String {
        val data = ExamData(
            scoreSchemeVersion = CURRENT_SCORE_SCHEME_VERSION,
            records = examRecords.map { r ->
                SerializableExamRecord(r.id, r.year, r.month, r.type.name, r.participantCount, r.averageScore)
            },
            scores = studentScores.mapValues { (_, scores) ->
                scores.takeLast(MAX_SCORES_PER_STUDENT).map { s ->
                    SerializableScore(
                        studentId = s.studentId,
                        studentName = s.studentName,
                        classId = s.classId,
                        examId = s.examId,
                        subjectName = s.subject.name,
                        score = s.score,
                        rank = s.rank,
                        maxScore = s.maxScore
                    )
                }
            }
        )
        return Json.encodeToString(data)
    }

    fun fromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json.decodeFromString<ExamData>(json)
            val restoredRecords = data.records.map { record ->
                ExamRecord(
                    record.id,
                    record.year,
                    record.month,
                    try {
                        ExamType.valueOf(record.typeName)
                    } catch (_: Exception) {
                        ExamType.MONTHLY_TEST
                    },
                    record.participantCount,
                    record.averageScore
                )
            }
            val isLegacyScheme = data.scoreSchemeVersion == null
            val restoredScores = data.scores.mapValues { (_, scores) ->
                scores.takeLast(MAX_SCORES_PER_STUDENT).map { score ->
                    val subject = try {
                        Subject.valueOf(score.subjectName)
                    } catch (_: Exception) {
                        Subject.CHINESE
                    }
                    val maxScore = if (isLegacyScheme) {
                        LEGACY_MAX_SCORE
                    } else {
                        score.maxScore?.takeIf { it > 0f } ?: subject.maxScore
                    }
                    StudentScore(
                        studentId = score.studentId,
                        studentName = score.studentName,
                        classId = score.classId,
                        examId = score.examId,
                        subject = subject,
                        score = score.score,
                        maxScore = maxScore,
                        rank = score.rank
                    )
                }.toMutableList()
            }

            examRecords.clear()
            examRecords.addAll(restoredRecords)
            studentScores.clear()
            studentScores.putAll(restoredScores)
        } catch (e: Exception) {
            throw IllegalArgumentException("ExamManager.fromJson failed", e)
        }
    }
}

// ======= 数据模型 =======

enum class ExamType(val displayName: String) {
    MONTHLY_TEST("阶段考核"),
    MIDTERM("期中考试"),
    FINAL_EXAM("期末考试")
}

data class ExamRecord(
    val id: String,
    val year: Int,
    val month: Int,
    val type: ExamType,
    val participantCount: Int,
    var averageScore: Float = 0f
) {
    val displayTitle: String get() = "${year}年${month}月${type.displayName}"
}

data class StudentScore(
    val studentId: String,
    val studentName: String,
    val classId: String,
    val examId: String,
    val subject: Subject,
    val score: Float,
    val rank: Int,
    /** 该成绩实际采用的卷面满分；旧存档由 ExamManager 显式填入 100。 */
    val maxScore: Float = subject.maxScore
) {
    /** 归一化得分率，始终为 0..100。 */
    val normalizedScore: Float
        get() = subject.normalizeScore(score, maxScore)

    val grade: String get() = when {
        normalizedScore >= 90 -> "A"
        normalizedScore >= 80 -> "B"
        normalizedScore >= 70 -> "C"
        normalizedScore >= 60 -> "D"
        else -> "F"
    }
}

data class ExamResult(
    val examHeld: Boolean = false,
    val examType: ExamType? = null,
    val participantCount: Int = 0,
    val averageScore: Float = 0f,
    val topStudents: List<String> = emptyList(),
    val examId: String = ""
)

// ======= 序列化 =======

@Serializable
data class ExamData(
    /** null means a pre-v2 all-subject 100-point record. */
    val scoreSchemeVersion: Int? = null,
    val records: List<SerializableExamRecord> = emptyList(),
    val scores: Map<String, List<SerializableScore>> = emptyMap()
)

@Serializable
data class SerializableExamRecord(
    val id: String, val year: Int, val month: Int,
    val typeName: String, val participantCount: Int, val averageScore: Float
)

@Serializable
data class SerializableScore(
    val studentId: String, val studentName: String, val classId: String,
    val examId: String, val subjectName: String, val score: Float, val rank: Int,
    /** null is supported for records saved before the per-score maximum was introduced. */
    val maxScore: Float? = null
)
