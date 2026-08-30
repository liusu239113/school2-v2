package com.arktools.xiaozhang.ui.utils

import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.model.FacilityType

/**
 * 设施建筑图片资源映射工具类
 * 根据设施类型映射到对应的drawable资源
 */
object FacilityImageHelper {

    /**
     * 获取设施图片资源ID
     */
    fun getImageResId(facilityType: FacilityType): Int {
        return when (facilityType) {
            FacilityType.CLASSROOM -> R.drawable.bld_classroom
            FacilityType.MULTIMEDIA_ROOM -> R.drawable.bld_multimedia
            FacilityType.LABORATORY -> R.drawable.bld_lab
            FacilityType.COMPUTER_LAB -> R.drawable.bld_computer
            FacilityType.ART_STUDIO -> R.drawable.bld_studio
            FacilityType.LIBRARY -> R.drawable.bld_library
            FacilityType.SPORTS_FIELD -> R.drawable.bld_sports
            FacilityType.CANTEEN -> R.drawable.bld_canteen
            FacilityType.DORMITORY -> R.drawable.bld_dorm
            FacilityType.AUDITORIUM -> R.drawable.bld_auditorium
            FacilityType.GARDEN -> R.drawable.bld_garden
            FacilityType.GATE -> R.drawable.bld_gate
            FacilityType.CONFERENCE_CENTER -> R.drawable.bld_conference
            FacilityType.EMPLOYMENT_CENTER -> R.drawable.bld_employment
        }
    }
}
