package com.arktools.xiao.domain.policy

import com.arktools.xiao.ui.campus.CampusBuildTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学院施工生命周期与校园地图记录一致性测试。
 * 覆盖：开工登记、每日递减、竣工进入 founded、异常旧档拒绝、JSON 往返、设施地图记录增删。
 */
class SchoolPolicyManagerCollegeConstructionTest {

    private fun newManager(): SchoolPolicyManager = SchoolPolicyManager(
        com.arktools.xiao.domain.competition.UniversityCompetitionManager(),
        com.arktools.xiao.domain.research.ResearchChainManager(),
        com.arktools.xiao.domain.teacherdev.TeacherStoryManager(),
        com.arktools.xiao.domain.graduate.GraduateSchoolManager(),
        com.arktools.xiao.domain.international.InternationalProgramManager()
    )

    private fun placedOf(manager: SchoolPolicyManager): List<CampusBuildTypes.PlacedBuilding> =
        CampusBuildTypes.decodeBuildings(
            manager.policies.value.collegeDevelopment.placedBuildings
        )

    @Test
    fun startConstructionRegistersMapBuildingWithoutFounded() {
        val manager = newManager()

        val result = manager.startCollegeConstruction(
            CollegeType.SCIENCE, 4, "C_SCIENCE", 2 to 3
        )

        assertTrue(result.success)
        val dev = manager.policies.value.collegeDevelopment
        assertFalse(dev.founded.contains(CollegeType.SCIENCE))
        assertEquals(4, dev.constructingColleges[CollegeType.SCIENCE.name])
        val buildings = placedOf(manager)
        assertEquals(1, buildings.size)
        assertEquals("C_SCIENCE", buildings[0].key)
        assertEquals(2, buildings[0].x)
        assertEquals(3, buildings[0].y)
        assertEquals(4, buildings[0].constructionDaysLeft)
    }

    @Test
    fun startConstructionRejectsInvalidParamsAndDuplicates() {
        val manager = newManager()

        // 非法参数
        assertFalse(manager.startCollegeConstruction(CollegeType.SCIENCE, 0, "C_SCIENCE", 1 to 1).success)
        assertFalse(manager.startCollegeConstruction(CollegeType.SCIENCE, 3, "", 1 to 1).success)
        assertFalse(manager.startCollegeConstruction(CollegeType.SCIENCE, 3, "C_SCIENCE", -1 to 1).success)
        assertFalse(manager.startCollegeConstruction(CollegeType.SCIENCE, 3, "C_SCIENCE", 1 to -1).success)

        // 正常开工
        assertTrue(manager.startCollegeConstruction(CollegeType.SCIENCE, 3, "C_SCIENCE", 2 to 2).success)

        // 施工中重复开工
        assertFalse(manager.startCollegeConstruction(CollegeType.SCIENCE, 3, "C_SCIENCE", 5 to 5).success)

        // 同坐标冲突（不同学院）
        assertFalse(manager.startCollegeConstruction(CollegeType.ARTS, 3, "C_ART", 2 to 2).success)
    }

    @Test
    fun dailyAdvanceDecrementsThenCompletesCollege() {
        val manager = newManager()
        manager.startCollegeConstruction(CollegeType.SCIENCE, 3, "C_SCIENCE", 2 to 2)

        // 第 1、2 天：递减，未竣工
        assertTrue(manager.advanceCollegeConstructionDay().isEmpty())
        assertEquals(2, manager.policies.value.collegeDevelopment.constructingColleges[CollegeType.SCIENCE.name])
        assertEquals(2, placedOf(manager).single().constructionDaysLeft)
        assertFalse(manager.policies.value.collegeDevelopment.founded.contains(CollegeType.SCIENCE))

        manager.advanceCollegeConstructionDay()
        assertEquals(1, manager.policies.value.collegeDevelopment.constructingColleges[CollegeType.SCIENCE.name])

        // 第 3 天：竣工并进入 founded，地图天数控 0
        val completed = manager.advanceCollegeConstructionDay()
        assertEquals(listOf(CollegeType.SCIENCE), completed)
        val dev = manager.policies.value.collegeDevelopment
        assertTrue(dev.founded.contains(CollegeType.SCIENCE))
        assertTrue(dev.constructingColleges.isEmpty())
        assertEquals(0, placedOf(manager).single().constructionDaysLeft)
    }

    @Test
    fun cannotStartAfterCompleted() {
        val manager = newManager()
        manager.startCollegeConstruction(CollegeType.SCIENCE, 1, "C_SCIENCE", 2 to 2)
        manager.advanceCollegeConstructionDay()

        val result = manager.startCollegeConstruction(CollegeType.SCIENCE, 3, "C_SCIENCE", 6 to 6)
        assertFalse(result.success)
    }

    @Test
    fun invalidLegacyRecordSelfHealsWithoutFreezing() {
        val manager = newManager()
        val dev = manager.policies.value.collegeDevelopment
        // 旧档异常数据：未知学院名 + 非法天数（0 视为立即竣工）
        manager.replaceCollegeDevelopment(
            dev.copy(constructingColleges = mapOf("NOPE" to 1, "SCIENCE" to 0))
        )

        // 坏记录被清除，不再抛异常冻结施工；SCIENCE(0天) 直接竣工
        val completed = manager.advanceCollegeConstructionDay()
        assertTrue(completed.contains(CollegeType.SCIENCE))
        assertTrue(manager.policies.value.collegeDevelopment.founded.contains(CollegeType.SCIENCE))
        assertTrue(manager.policies.value.collegeDevelopment.constructingColleges.isEmpty())
        assertTrue(!("NOPE" in manager.policies.value.collegeDevelopment.constructingColleges))
    }

    @Test
    fun deadlineModeCompletesRegardlessOfRollbacks() {
        val manager = newManager()
        // 开工日 = 2026年9月1日 → dayKey = 2026*360 + 8*30 + 1
        val startDayKey = 2026L * 360 + 8 * 30 + 1
        assertTrue(
            manager.startCollegeConstruction(
                CollegeType.SCIENCE, 3, "C_SCIENCE", 2 to 2, startDayKey
            ).success
        )
        // 模拟状态被回滚到开工当天后继续推进：到第 3 天必须竣工
        manager.advanceCollegeConstructionDay(startDayKey + 1)
        assertEquals(2, manager.policies.value.collegeDevelopment.constructingColleges[CollegeType.SCIENCE.name])
        // 中途回滚：内存被覆盖回开工时状态（3天）
        manager.replaceCollegeDevelopment(
            manager.policies.value.collegeDevelopment.copy(
                constructingColleges = mapOf("SCIENCE" to 3),
                collegeDeadlines = mapOf("SCIENCE" to startDayKey + 3)
            )
        )
        manager.advanceCollegeConstructionDay(startDayKey + 3)
        assertTrue(manager.policies.value.collegeDevelopment.founded.contains(CollegeType.SCIENCE))
        assertTrue(manager.policies.value.collegeDevelopment.constructingColleges.isEmpty())
        assertEquals(0, placedOf(manager).single().constructionDaysLeft)
    }

    @Test
    fun emptyConstructionStateAdvancesNothing() {
        val manager = newManager()
        assertTrue(manager.advanceCollegeConstructionDay().isEmpty())
    }

    @Test
    fun constructionStateSurvivesJsonRoundTrip() {
        val manager = newManager()
        manager.startCollegeConstruction(CollegeType.SCIENCE, 4, "C_SCIENCE", 3 to 4)
        val json = manager.toJson()

        val restored = newManager()
        restored.restoreFromJson(json)

        val dev = restored.policies.value.collegeDevelopment
        assertEquals(4, dev.constructingColleges[CollegeType.SCIENCE.name])
        assertFalse(dev.founded.contains(CollegeType.SCIENCE))
        val buildings = CampusBuildTypes.decodeBuildings(dev.placedBuildings)
        assertEquals(1, buildings.size)
        assertEquals("C_SCIENCE", buildings[0].key)
    }

    @Test
    fun legacyJsonWithoutConstructionFieldRestoresEmpty() {
        val manager = newManager()
        // 模拟没有 constructingColleges 字段的旧档（默认空 map，不抛异常）
        val legacyJson = """
            {"tuitionLevel":"STANDARD","foundedColleges":["LIBERAL_ARTS"]}
        """.trimIndent()

        manager.restoreFromJson(legacyJson)

        val dev = manager.policies.value.collegeDevelopment
        assertTrue(dev.constructingColleges.isEmpty())
        assertTrue(dev.founded.contains(CollegeType.LIBERAL_ARTS))
    }

    @Test
    fun placedFacilityRecordsAddAndRemoveConsistently() {
        val manager = newManager()

        // 登记
        assertTrue(
            manager.addPlacedFacility("F_CLASSROOM", 5 to 5, 1, "room-1", 2)
        )
        assertEquals(1, placedOf(manager).size)

        // 同 facilityId 重复登记拒绝
        assertFalse(manager.addPlacedFacility("F_CLASSROOM", 8 to 8, 1, "room-1", 0))
        // 同坐标不同 key 拒绝
        assertFalse(manager.addPlacedFacility("F_CANTEEN", 5 to 5, 1, "room-2", 0))

        // 移除
        assertTrue(manager.removePlacedFacility("F_CLASSROOM", "room-1"))
        assertTrue(placedOf(manager).isEmpty())
        // 移除不存在的记录返回 false
        assertFalse(manager.removePlacedFacility("F_CLASSROOM", "room-1"))
    }

    @Test
    fun placedBuildingWithoutFacilityRejectsDuplicateKey() {
        val manager = newManager()
        assertTrue(manager.addPlacedBuilding("HOSPITAL", 9 to 9))
        assertFalse(manager.addPlacedBuilding("HOSPITAL", 10 to 10))
        // 同坐标被占
        assertFalse(manager.addPlacedBuilding("ADMIN", 9 to 9))
        assertTrue(placedOf(manager).size == 1)
    }
}
