package com.arktools.xiao.domain.model

import com.arktools.xiao.domain.policy.CollegeType
import com.arktools.xiao.ui.campus.CampusBuildTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第二批科技树建筑（校企合作中心/国际交流中心/后勤保障中心）效果与新装饰经济参数测试。
 */
class CampusTechTreeBatchTwoTest {

    @Test
    fun logisticsCenterDiscountIsCapped() {
        assertEquals(1.0, FacilityCapacity.logisticsMaintenanceFactor(0), 1e-9)
        assertEquals(0.94, FacilityCapacity.logisticsMaintenanceFactor(1), 1e-9)
        assertEquals(0.88, FacilityCapacity.logisticsMaintenanceFactor(2), 1e-9)
        assertEquals(0.82, FacilityCapacity.logisticsMaintenanceFactor(3), 1e-9)
        // 超出最大等级按封底处理
        assertEquals(0.82, FacilityCapacity.logisticsMaintenanceFactor(9), 1e-9)
        // 异常输入不放大
        assertEquals(1.0, FacilityCapacity.logisticsMaintenanceFactor(-2), 1e-9)
    }

    @Test
    fun internationalCenterIncomeMultiplierScalesWithLevel() {
        assertEquals(1.0, FacilityCapacity.internationalIncomeMultiplier(0), 1e-9)
        assertEquals(1.1, FacilityCapacity.internationalIncomeMultiplier(1), 1e-9)
        assertEquals(1.2, FacilityCapacity.internationalIncomeMultiplier(2), 1e-9)
        // 超出最大等级封顶
        assertEquals(1.2, FacilityCapacity.internationalIncomeMultiplier(5), 1e-9)
        assertEquals(1.0, FacilityCapacity.internationalIncomeMultiplier(-1), 1e-9)
    }

    @Test
    fun batchTwoSpecsExposePrerequisitesAndDownstream() {
        val incubator = CampusBuildTypes.facilitySpec(FacilityType.INCUBATOR)
        val intl = CampusBuildTypes.facilitySpec(FacilityType.INTERNATIONAL_CENTER)
        val logistics = CampusBuildTypes.facilitySpec(FacilityType.LOGISTICS_CENTER)

        // 校企合作中心依赖就业指导中心投入运营
        assertTrue(incubator!!.prerequisiteFacilities.contains(FacilityType.EMPLOYMENT_CENTER))
        assertTrue(incubator.downstream.contains("创业"))

        // 国际交流中心依赖商学院竣工
        assertTrue(intl!!.prerequisiteColleges.contains(CollegeType.BUSINESS))
        assertTrue(intl.downstream.contains("国际生"))

        // 后勤保障中心依赖食堂
        assertTrue(logistics!!.prerequisiteFacilities.contains(FacilityType.CANTEEN))
        assertTrue(logistics.downstream.contains("维护"))
    }

    @Test
    fun batchTwoFacilitiesHaveSaneEconomy() {
        // 校企/国际/后勤均为不可重复建筑，成本与维护费为正
        listOf(
            FacilityType.INCUBATOR,
            FacilityType.INTERNATIONAL_CENTER,
            FacilityType.LOGISTICS_CENTER
        ).forEach { type ->
            assertTrue("${type.name} 应不可重复", !type.repeatable)
            assertTrue("${type.name} 建设成本应>0", type.baseCost > 0)
            assertTrue("${type.name} 月维护应>0", type.baseMaintenance > 0)
            assertEquals(
                "${type.name} 单座造价不应被递增",
                type.baseCost,
                FacilityCapacity.repeatCost(type, 1),
                1e-9
            )
        }
    }

    @Test
    fun newDecorationsCarryEconomyEffects() {
        // 新装饰必须带维护费与满意度，保证经济闭环（有产出也有开销）
        listOf(
            CampusBuildTypes.TileKind.GINKGO,
            CampusBuildTypes.TileKind.BAMBOO,
            CampusBuildTypes.TileKind.LAMP,
            CampusBuildTypes.TileKind.PAVILION,
            CampusBuildTypes.TileKind.PARCEL,
            CampusBuildTypes.TileKind.FITNESS
        ).forEach { tile ->
            assertTrue("${tile.name} 应有月维护费", tile.monthlyMaintenanceWan > 0)
            assertTrue("${tile.name} 应有满意度产出", tile.satisfactionBonus > 0f)
            assertTrue("${tile.name} 建设成本应为正", tile.costWan > 0)
        }
        // 高级装饰带声誉
        assertTrue(CampusBuildTypes.TileKind.PAVILION.reputationBonus > 0)
        assertTrue(CampusBuildTypes.TileKind.LAMP.reputationBonus > 0)
    }
}
