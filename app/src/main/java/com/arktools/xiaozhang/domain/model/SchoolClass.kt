package com.arktools.xiaozhang.domain.model

import java.util.UUID

/**
 * 班级模型
 *
 * 层级关系: 学校 → 年级(GradeLevel) → 班级(SchoolClass) → 学生(Student)
 *
 * 班级是学生管理的核心组织单元:
 * - 每个班级有一个学业导师(headTeacher)
 * - 班级有容量上限(maxCapacity)
 * - 班级整体指标由学生五维属性聚合
 * - 班级之间有排名竞争
 */
data class SchoolClass(
    val id: String = UUID.randomUUID().toString(),
    val schoolId: String,
    var gradeLevel: GradeLevel,
    val classNumber: Int,               // 班号 1,2,3...
    val classTier: ClassTier = ClassTier.NORMAL,  // 班型（精英/重点/普通/特长）
    var headTeacherId: String? = null,   // 学业导师教师ID
    val maxCapacity: Int = classTier.maxSize,     // 班级最大容量，由班型决定

    // ======= 班级整体指标（由学生聚合计算）=======
    var studentCount: Int = 0,
    var avgIntelligence: Float = 50f,   // 班均智力
    var avgPhysical: Float = 50f,       // 班均体力
    var avgSocial: Float = 50f,         // 班均社交
    var avgCreativity: Float = 50f,     // 班均创造力
    var avgMorality: Float = 50f,       // 班均品德
    var avgAcademicScore: Float = 0f,   // 班均学业成绩
    var avgSatisfaction: Float = 70f,   // 班均满意度

    // ======= 班级特有属性 =======
    var classSpirit: Float = 50f,       // 班风 (0~100) — 由 social+morality 聚合 + 学业导师加成
    var disciplineScore: Float = 70f,   // 纪律分 (0~100) — morality聚合 + 事件影响
    var cohesion: Float = 50f,          // 凝聚力 (0~100) — social聚合 + 活动加成
    var gradeRanking: Int = 0,          // 年级内排名 (1-based, 按avgAcademicScore)

    // ======= 时间信息 =======
    val createdYear: Int = 0,
    val createdMonth: Int = 0
) {
    /** 班级名称，如 "大一(1)班·通识教学班" */
    val displayName: String
        get() = "${gradeLevel.displayName}(${classNumber})班·${classTier.displayName}"

    /** 是否满员 */
    val isFull: Boolean get() = studentCount >= maxCapacity

    /** 剩余容量 */
    val remainingCapacity: Int get() = (maxCapacity - studentCount).coerceAtLeast(0)

    /** 班级五维均值 */
    val averageAttributes: StudentAttributes
        get() = StudentAttributes(
            intelligence = avgIntelligence,
            physical = avgPhysical,
            social = avgSocial,
            creativity = avgCreativity,
            morality = avgMorality
        )

    /** 班级综合评分 (加权均分) */
    val overallScore: Float
        get() = avgAcademicScore * 0.4f + classSpirit * 0.2f + disciplineScore * 0.2f + cohesion * 0.2f

    /** 学业导师效果描述 */
    val hasHeadTeacher: Boolean get() = headTeacherId != null
}

/**
 * 年级枚举
 *
 * 四年制本科（大一/大二/大三/大四）
 * 每年9月新生入学，每年6月大四毕业
 */
enum class GradeLevel(
    val displayName: String,
    val order: Int,          // 排序用 (1=大一, 2=大二, 3=大三, 4=大四)
    val isGraduating: Boolean // 是否为毕业年级
) {
    GRADE_1("大一", 1, false),
    GRADE_2("大二", 2, false),
    GRADE_3("大三", 3, false),
    GRADE_4("大四", 4, true);

    /** 升入下一年级 (大四无法再升) */
    val nextGrade: GradeLevel?
        get() = when (this) {
            GRADE_1 -> GRADE_2
            GRADE_2 -> GRADE_3
            GRADE_3 -> GRADE_4
            GRADE_4 -> null
        }

    companion object {
        fun fromOrder(order: Int): GradeLevel = entries.first { it.order == order }
    }
}

/**
 * 编入教学班策略
 */
enum class ClassStrategy(val displayName: String, val description: String) {
    RANDOM("随机编入教学班", "完全随机分配，公平但差异大"),
    BALANCED("均衡编入教学班", "五维均值接近，班级实力均衡"),
    STREAMED("分层编入教学班", "按学业基础分核心班/通识班，尖子生集中但影响公平"),
    SUBJECT_BASED("选科编入教学班", "按学生强项维度编入教学班，利于特长培养")
}

/**
 * 班级事件
 */
sealed class ClassEvent(
    open val classId: String,
    open val title: String,
    open val message: String
) {
    /** 班级获奖 */
    data class AwardEvent(
        override val classId: String,
        override val title: String,
        override val message: String,
        val reputationBonus: Long,
        val spiritBonus: Float
    ) : ClassEvent(classId, title, message)

    /** 纪律问题 */
    data class DisciplineEvent(
        override val classId: String,
        override val title: String,
        override val message: String,
        val disciplinePenalty: Float,
        val reputationPenalty: Long
    ) : ClassEvent(classId, title, message)

    /** 班级活动 */
    data class ActivityEvent(
        override val classId: String,
        override val title: String,
        override val message: String,
        val cohesionBonus: Float,
        val satisfactionBonus: Float
    ) : ClassEvent(classId, title, message)

    /** 学生冲突 */
    data class ConflictEvent(
        override val classId: String,
        override val title: String,
        override val message: String,
        val satisfactionPenalty: Float,
        val needsIntervention: Boolean
    ) : ClassEvent(classId, title, message)

    /** 升级/留级通知 */
    data class PromotionEvent(
        override val classId: String,
        override val title: String,
        override val message: String,
        val promotedCount: Int,
        val heldBackCount: Int
    ) : ClassEvent(classId, title, message)
}

/**
 * 学年升级结果
 */
data class PromotionResult(
    val promotedStudents: List<String>,       // 正常升级的学生ID
    val heldBackStudents: List<String>,       // 留级的学生ID（成绩太差）
    val graduatedStudents: List<String>,      // 毕业的学生ID（大四结束）
    val newGrade1Capacity: Int                // 新大一需要多少容量
)

/**
 * 学业导师效果（根据教师属性计算）
 */
data class HeadTeacherEffect(
    val intelligenceBoost: Float = 0f,    // 智力成长加成
    val physicalBoost: Float = 0f,        // 体力成长加成
    val socialBoost: Float = 0f,          // 社交成长加成
    val creativityBoost: Float = 0f,      // 创造力成长加成
    val moralityBoost: Float = 0f,        // 品德成长加成
    val disciplineBoost: Float = 0f,      // 纪律加成
    val satisfactionBoost: Float = 0f     // 满意度加成
) {
    companion object {
        /**
         * 根据教师四维能力计算学业导师效果
         * teaching → 智力加成
         * management → 品德+纪律加成
         * psychology → 社交+满意度加成
         * research → 创造力加成
         */
        fun fromTeacher(teacher: Teacher): HeadTeacherEffect {
            val teachingFactor = teacher.teaching / 100f
            val managementFactor = teacher.management / 100f
            val psychologyFactor = teacher.psychology / 100f
            val researchFactor = teacher.research / 100f

            return HeadTeacherEffect(
                intelligenceBoost = teachingFactor * 0.03f,    // 教学能力→智力成长
                physicalBoost = 0.01f,                          // 基础
                socialBoost = psychologyFactor * 0.025f,        // 心理学→社交
                creativityBoost = researchFactor * 0.02f,       // 研究→创造力
                moralityBoost = managementFactor * 0.03f,       // 管理→品德
                disciplineBoost = managementFactor * 5f,        // 管理→纪律分
                satisfactionBoost = psychologyFactor * 0.02f    // 心理→满意度
            )
        }
    }
}
