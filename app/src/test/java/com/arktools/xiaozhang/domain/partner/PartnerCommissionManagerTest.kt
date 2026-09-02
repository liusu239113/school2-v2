package com.arktools.xiaozhang.domain.partner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 企业合作委托系统测试：要约刷新、接单上限、条件校验、到期结算与防重复结算。
 */
class PartnerCommissionManagerTest {

    private fun newManager(): PartnerCommissionManager = PartnerCommissionManager()

    private fun founded(): Set<String> = setOf("ENGINEERING", "SCIENCE")

    private fun context(avgSkill: Double = 60.0) = CompletionContext(
        avgFacultySkill = avgSkill,
        foundedColleges = founded(),
        hasOperationalFacility = { true }
    )

    @Test
    fun monthlyOffersRefreshOnceAndReplaceNextMonth() {
        val manager = newManager()
        manager.refreshOffers(2026, 9, campusLevel = 2, foundedColleges = founded())
        val first = manager.state.value.offers
        assertEquals(2, first.size)

        // 同月重复刷新不覆盖
        manager.refreshOffers(2026, 9, campusLevel = 2, foundedColleges = founded())
        assertEquals(first.map { it.id }, manager.state.value.offers.map { it.id })

        // 次月刷新替换要约
        manager.refreshOffers(2026, 10, campusLevel = 2, foundedColleges = founded())
        assertEquals(2, manager.state.value.offers.size)
        assertTrue(manager.state.value.offers.none { old -> first.any { it.id == old.id } })
    }

    @Test
    fun acceptMovesOfferToActiveAndRespectsCap() {
        val manager = newManager()
        manager.refreshOffers(2026, 9, campusLevel = 2, foundedColleges = founded())
        val firstId = manager.state.value.offers[0].id
        assertNotNull(manager.accept(firstId, 2026, 9))
        assertEquals(1, manager.state.value.active.size)
        assertEquals(1, manager.state.value.offers.size)

        // 第二个月再接一单 → 满 2 个
        manager.refreshOffers(2026, 10, campusLevel = 2, foundedColleges = founded())
        val secondId = manager.state.value.offers[0].id
        assertNotNull(manager.accept(secondId, 2026, 10))
        assertEquals(2, manager.state.value.active.size)

        // 第三单应被上限拦截
        manager.refreshOffers(2026, 11, campusLevel = 2, foundedColleges = founded())
        val thirdId = manager.state.value.offers[0].id
        assertNull(manager.accept(thirdId, 2026, 11))
        assertEquals(2, manager.state.value.active.size)
    }

    @Test
    fun canAcceptChecksReputationCollegeAndFacility() {
        val manager = newManager()
        val commission = PartnerCommission(
            id = "t1", kind = CommissionKind.EMPLOYMENT, partner = "测试", title = "订单班",
            description = "", requiredCollege = "ENGINEERING"
        )
        assertNull(manager.canAccept(commission, 100L, founded()) { true })
        assertNotNull(manager.canAccept(commission, 100L, emptySet()) { true })

        val facilityCommission = commission.copy(requiredFacility = "LABORATORY")
        assertNotNull(manager.canAccept(facilityCommission, 100L, founded()) { false })
        assertNull(manager.canAccept(facilityCommission, 100L, founded()) { true })

        val repCommission = commission.copy(requiredReputation = 500L)
        assertNotNull(manager.canAccept(repCommission, 100L, founded()) { true })
    }

    @Test
    fun advanceMonthCompletesAtDeadlineAndGrantsBoosts() {
        val manager = newManager()
        manager.refreshOffers(2026, 9, campusLevel = 3, foundedColleges = founded())
        val offer = manager.state.value.offers.first()
        assertNotNull(manager.accept(offer.id, 2026, 9))

        // 委托期内不结算，只扣减剩余月数
        val mid = manager.advanceMonth(context(), 2026, 10)
        assertTrue(mid.completions.isEmpty() && mid.failures.isEmpty())
        assertEquals(offer.durationMonths - 1, manager.state.value.active.single().remainingMonths)

        // 到期月结算：高师资 + 学院 + 设施给足成功条件
        val final = manager.advanceMonth(context(avgSkill = 90.0), 2026, 11)
        val settled = final.completions + final.failures
        assertEquals(1, settled.size)
        assertEquals(0, manager.state.value.active.size)

        // 若为就业类委托，结项应挂起就业加成，消费一次后清零
        val completed = final.completions.firstOrNull()
        if (completed != null && completed.employmentBoost > 0f) {
            assertEquals(completed.employmentBoost, manager.consumeEmploymentBoost(), 1e-6f)
            assertEquals(0f, manager.consumeEmploymentBoost(), 1e-6f)
        }
    }

    @Test
    fun advanceMonthGuardsDuplicateSameMonth() {
        val manager = newManager()
        manager.refreshOffers(2026, 9, campusLevel = 2, foundedColleges = founded())
        manager.state.value.offers.firstOrNull()?.let { manager.accept(it.id, 2026, 9) }
        manager.advanceMonth(context(), 2026, 10)
        val sizeAfterFirst = manager.state.value.active.size
        val remainingAfterFirst = manager.state.value.active.singleOrNull()?.remainingMonths

        // 同月重复结算被守卫拦截：活跃委托不推进
        manager.advanceMonth(context(), 2026, 10)
        assertEquals(sizeAfterFirst, manager.state.value.active.size)
        assertEquals(remainingAfterFirst, manager.state.value.active.singleOrNull()?.remainingMonths)
    }

    @Test
    fun jsonRoundTripPreservesOffersAndActive() {
        val manager = newManager()
        manager.refreshOffers(2026, 9, campusLevel = 2, foundedColleges = founded())
        manager.state.value.offers.firstOrNull()?.let { manager.accept(it.id, 2026, 9) }
        val json = manager.toJson()

        val restored = newManager()
        restored.restoreFromJson(json)
        assertEquals(manager.state.value.active.size, restored.state.value.active.size)
        assertEquals(manager.state.value.offers.size, restored.state.value.offers.size)

        // 空 JSON 视为无操作：保留已恢复的状态，不清空也不抛异常
        restored.restoreFromJson("")
        assertEquals(manager.state.value.active.size, restored.state.value.active.size)
        assertEquals(manager.state.value.offers.size, restored.state.value.offers.size)
    }

    @Test
    fun declinedOfferIsRemoved() {
        val manager = newManager()
        manager.refreshOffers(2026, 9, campusLevel = 2, foundedColleges = founded())
        val id = manager.state.value.offers[0].id
        assertTrue(manager.decline(id))
        assertFalse(manager.state.value.offers.any { it.id == id })
        assertFalse(manager.decline(id))
    }
}
