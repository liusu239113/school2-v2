package com.arktools.xiaozhang.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val courseId: String,
    val schoolId: String,

    // 组织归属
    val classId: String? = null,
    val gradeLevel: String = "GRADE_1",  // GradeLevel enum name

    // 五维属性
    val intelligence: Float = 50f,
    val physical: Float = 50f,
    val social: Float = 50f,
    val creativity: Float = 50f,
    val morality: Float = 50f,
    val backgroundTier: String = "NORMAL",  // BackgroundTier enum name

    // 旧属性（兼容）
    val talent: Float = 0.8f,
    val motivation: Float = 0.85f,
    val traitsJson: String = "[]",  // List<StudentTrait> serialized as JSON

    // 状态
    val status: String = "ENROLLED",  // StudentStatus name

    // 学习进度
    val semesterMastery: Float = 0f,
    val satisfaction: Float = 70f,
    val academicScore: Float = 0f,

    // 高考与大学录取
    val gaoKaoScore: Float = 0f,
    val admittedUniversity: String? = null,
    val universityTier: String? = null,  // UniversityTier enum name

    // 健康与生活
    val healthStatus: String = "HEALTHY",  // HealthStatus enum name
    val mealQuality: Float = 50f,
    val dormSatisfaction: Float = 50f,
    val exerciseLevel: Float = 30f,
    val consecutiveSickDays: Int = 0,

    // 时间
    val enrollYear: Int = 0,
    val enrollMonth: Int = 0,
    val lastPromotionYear: Int = 0,
    val graduateYear: Int? = null,
    val graduateMonth: Int? = null,
    val graduationProjectionState: Int = 0,

    // 口碑评价
    val reviewRating: Int? = null,
    val reviewComment: String? = null,
    val reviewReputationImpact: Long? = null
)
