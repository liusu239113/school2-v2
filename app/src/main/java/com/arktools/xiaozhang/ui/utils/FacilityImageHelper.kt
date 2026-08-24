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
            FacilityType.CLASSROOM -> R.drawable.facility_classroom
            FacilityType.MULTIMEDIA_ROOM -> R.drawable.facility_multimedia_room
            FacilityType.LABORATORY -> R.drawable.facility_laboratory
            FacilityType.COMPUTER_LAB -> R.drawable.facility_computer_lab
            FacilityType.ART_STUDIO -> R.drawable.facility_art_studio
            FacilityType.LIBRARY -> R.drawable.facility_library
            FacilityType.SPORTS_FIELD -> R.drawable.facility_sports_field
            FacilityType.CANTEEN -> R.drawable.facility_canteen
            FacilityType.DORMITORY -> R.drawable.facility_dormitory
            FacilityType.AUDITORIUM -> R.drawable.facility_auditorium
            FacilityType.GARDEN -> R.drawable.facility_garden
            FacilityType.GATE -> R.drawable.facility_gate
        }
    }
}
