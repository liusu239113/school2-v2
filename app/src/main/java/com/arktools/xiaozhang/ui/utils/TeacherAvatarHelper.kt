package com.arktools.xiaozhang.ui.utils

import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.model.Gender
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherRole

/**
 * 教师头像资源映射工具类
 * 根据教师的科目、性别和avatarIndex映射到对应的drawable资源
 */
object TeacherAvatarHelper {

    /**
     * 获取教师头像资源ID
     */
    fun getAvatarResId(teacher: Teacher): Int {
        return getAvatarResId(teacher.role, teacher.gender, teacher.avatarIndex)
    }

    /**
     * 根据科目、性别、索引获取头像资源ID
     */
    fun getAvatarResId(role: TeacherRole, gender: Gender, avatarIndex: Int): Int {
        val index = avatarIndex.coerceIn(1, 4)
        val genderStr = if (gender == Gender.MALE) "male" else "female"

        return when (role) {
            TeacherRole.CHINESE -> getChineseAvatar(genderStr, index)
            TeacherRole.MATH -> getMathAvatar(genderStr, index)
            TeacherRole.ENGLISH -> getEnglishAvatar(genderStr, index)
            TeacherRole.PHYSICS -> getPhysicsAvatar(genderStr, index)
            TeacherRole.CHEMISTRY -> getChemistryAvatar(genderStr, index)
            TeacherRole.BIOLOGY -> getBiologyAvatar(genderStr, index)
            TeacherRole.HISTORY -> getHistoryAvatar(genderStr, index)
            TeacherRole.GEOGRAPHY -> getGeographyAvatar(genderStr, index)
            TeacherRole.POLITICS -> getPoliticsAvatar(genderStr, index)
            TeacherRole.ART -> getArtAvatar(genderStr, index)
            TeacherRole.PE -> getPeAvatar(genderStr, index)
            TeacherRole.MUSIC -> getMusicAvatar(genderStr, index)
        }
    }

    private fun getChineseAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_chinese_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_chinese_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_chinese_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_chinese_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_chinese_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_chinese_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_chinese_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_chinese_female_04
            else -> R.drawable.teacher_chinese_male_01
        }
    }

    private fun getMathAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_math_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_math_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_math_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_math_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_math_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_math_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_math_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_math_female_04
            else -> R.drawable.teacher_math_male_01
        }
    }

    private fun getEnglishAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_english_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_english_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_english_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_english_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_english_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_english_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_english_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_english_female_04
            else -> R.drawable.teacher_english_male_01
        }
    }

    private fun getPhysicsAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_physics_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_physics_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_physics_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_physics_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_physics_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_physics_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_physics_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_physics_female_04
            else -> R.drawable.teacher_physics_male_01
        }
    }

    private fun getChemistryAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_chemistry_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_chemistry_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_chemistry_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_chemistry_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_chemistry_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_chemistry_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_chemistry_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_chemistry_female_04
            else -> R.drawable.teacher_chemistry_male_01
        }
    }

    private fun getBiologyAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_biology_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_biology_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_biology_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_biology_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_biology_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_biology_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_biology_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_biology_female_04
            else -> R.drawable.teacher_biology_male_01
        }
    }

    private fun getHistoryAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_history_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_history_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_history_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_history_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_history_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_history_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_history_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_history_female_04
            else -> R.drawable.teacher_history_male_01
        }
    }

    private fun getGeographyAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_geography_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_geography_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_geography_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_geography_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_geography_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_geography_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_geography_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_geography_female_04
            else -> R.drawable.teacher_geography_male_01
        }
    }

    private fun getPoliticsAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_politics_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_politics_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_politics_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_politics_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_politics_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_politics_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_politics_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_politics_female_04
            else -> R.drawable.teacher_politics_male_01
        }
    }

    private fun getArtAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_art_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_art_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_art_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_art_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_art_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_art_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_art_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_art_female_04
            else -> R.drawable.teacher_art_male_01
        }
    }

    private fun getPeAvatar(gender: String, index: Int): Int {
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_pe_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_pe_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_pe_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_pe_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_pe_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_pe_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_pe_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_pe_female_04
            else -> R.drawable.teacher_pe_male_01
        }
    }

    private fun getMusicAvatar(gender: String, index: Int): Int {
        // 复用编程教师头像资源（后续可替换为音乐教师专属头像）
        return when {
            gender == "male" && index == 1 -> R.drawable.teacher_programming_male_01
            gender == "male" && index == 2 -> R.drawable.teacher_programming_male_02
            gender == "male" && index == 3 -> R.drawable.teacher_programming_male_03
            gender == "male" && index == 4 -> R.drawable.teacher_programming_male_04
            gender == "female" && index == 1 -> R.drawable.teacher_programming_female_01
            gender == "female" && index == 2 -> R.drawable.teacher_programming_female_02
            gender == "female" && index == 3 -> R.drawable.teacher_programming_female_03
            gender == "female" && index == 4 -> R.drawable.teacher_programming_female_04
            else -> R.drawable.teacher_programming_male_01
        }
    }
}
