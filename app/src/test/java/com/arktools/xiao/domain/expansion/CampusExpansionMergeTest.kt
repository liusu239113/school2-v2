package com.arktools.xiao.domain.expansion

import com.arktools.xiao.domain.model.Facility
import com.arktools.xiao.domain.model.FacilityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校区扩建与瓦片地图合并测试：
 * 区片（宏观承载）开工必须以校园地图设施（具体容量）为地基。
 */
class CampusExpansionMergeTest {

    private fun facility(type: FacilityType, condition: Float = 100f, daysLeft: Int = 0) =
        Facility(type = type, level = 1, condition = condition, constructionDaysLeft = daysLeft)

    @Test
    fun teachingZoneRequiresTwoOperationalClassrooms() {
        val blocked = CampusExpansionManager.checkPrerequisites(
            CampusZoneType.TEACHING_BUILDING,
            listOf(facility(FacilityType.CLASSROOM))
        )
        assertTrue(blocked!!.contains("教学区开发"))
        assertTrue(blocked.contains("标准教室"))

        val passes = CampusExpansionManager.checkPrerequisites(
            CampusZoneType.TEACHING_BUILDING,
            listOf(facility(FacilityType.CLASSROOM), facility(FacilityType.CLASSROOM))
        )
        assertNull(passes)
    }

    @Test
    fun constructingFacilitiesDoNotCountAsOperational() {
        val blocked = CampusExpansionManager.checkPrerequisites(
            CampusZoneType.DORMITORY,
            listOf(
                facility(FacilityType.DORMITORY),
                facility(FacilityType.DORMITORY, daysLeft = 3)
            )
        )
        assertTrue(blocked != null)
    }

    @Test
    fun deterioratedFacilitiesDoNotCountAsOperational() {
        val blocked = CampusExpansionManager.checkPrerequisites(
            CampusZoneType.LIBRARY,
            listOf(facility(FacilityType.LIBRARY, condition = 15f))
        )
        assertTrue(blocked != null)
    }

    @Test
    fun researchBaseNeedsTwoLabsAndLibrary() {
        val onlyOneLab = CampusExpansionManager.checkPrerequisites(
            CampusZoneType.RESEARCH_CENTER,
            listOf(
                facility(FacilityType.LABORATORY),
                facility(FacilityType.LIBRARY)
            )
        )
        assertTrue(onlyOneLab != null)

        val passes = CampusExpansionManager.checkPrerequisites(
            CampusZoneType.RESEARCH_CENTER,
            listOf(
                facility(FacilityType.LABORATORY),
                facility(FacilityType.LABORATORY),
                facility(FacilityType.LIBRARY)
            )
        )
        assertNull(passes)
    }

    @Test
    fun adminAndGardenZonesHaveNoPrerequisites() {
        assertNull(CampusExpansionManager.checkPrerequisites(CampusZoneType.ADMIN_BUILDING, emptyList()))
        assertNull(CampusExpansionManager.checkPrerequisites(CampusZoneType.GARDEN, emptyList()))
        assertEquals(0, CampusExpansionManager.requiredFacilities(CampusZoneType.ADMIN_BUILDING).size)
        assertEquals(0, CampusExpansionManager.requiredFacilities(CampusZoneType.GARDEN).size)
    }

    @Test
    fun zoneTypesAreReframedAsDistrictDevelopment() {
        // 消除与地图建筑的概念重叠：区片不再叫"楼/馆"，而是承载区开发
        listOf(
            CampusZoneType.TEACHING_BUILDING,
            CampusZoneType.DORMITORY,
            CampusZoneType.LIBRARY,
            CampusZoneType.CAFETERIA,
            CampusZoneType.SPORTS_CENTER
        ).forEach { type ->
            assertTrue(
                "${type.name} 显示名应体现区片开发: ${type.displayName}",
                type.displayName.contains("区") || type.displayName.contains("基地")
            )
        }
    }
}
