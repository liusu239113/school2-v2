package com.arktools.xiaozhang.domain.achievement

import com.arktools.xiaozhang.domain.model.Facility
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.model.School
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就解锁节奏测试：开局不秒解一批、年份残留成就已移除、条件与文案一致。
 */
class AchievementPacingTest {

    private fun newSchool(): School = School().apply {
        currentYear = 2026
        currentMonth = 8
        // cash/marketCap/称号等沿用 GameBalanceConfig 默认（现金500万、市值约1000万）
    }

    private fun ids(unlocked: List<Achievement>) = unlocked.map { it.id }.toSet()

    @Test
    fun staleYearAchievementsRemovedFromRegistry() {
        val registry = AchievementRegistry.getAllAchievements().map { it.id }.toSet()
        // 2026 年开局会立即满足的旧年份成就必须移除
        assertFalse("year_2000" in registry)
        assertFalse("year_2010" in registry)
        assertFalse("year_2020" in registry)
        assertTrue("year_2030" in registry)
    }

    @Test
    fun financeLadderRebasedAboveStartingCash() {
        val school = newSchool()
        // 开局现金 500 万、市值约 1000 万：这些档位不应开局即解
        val registry = AchievementRegistry.getAllAchievements()
        val cash100 = registry.firstOrNull { it.id == "cash_100" }
        val cash500 = registry.firstOrNull { it.id == "cash_500" }
        val marketcap500 = registry.firstOrNull { it.id == "marketcap_500" }
        assertEquals(null, cash100)
        assertEquals(null, cash500)
        assertEquals(null, marketcap500)
        // 新首档在开局之上
        val cash1000 = registry.first { it.id == "cash_1000" }
        assertFalse(cash1000.condition(school))
        val marketcap2000 = registry.first { it.id == "marketcap_2000" }
        assertFalse(marketcap2000.condition(school))
    }

    @Test
    fun firstMonthDoesNotSpamAchievements() {
        val manager = AchievementManager()
        val school = newSchool()
        // 开局状态（未建任何设施、未招生）：第一轮检查最多解锁 2 项
        val first = runBlocking { manager.checkAchievements(school) }
        assertTrue("开局不应刷屏成就，实际：${ids(first)}", first.size <= 2)
    }

    @Test
    fun frugalStartRequiresRealGrowth() {
        val registry = AchievementRegistry.getAllAchievements().first { it.id == "frugal_start" }
        val school = newSchool().apply { currentYear = 2027 }
        // 第一年结束时现金 560 万（低于 600）不解锁；超过 600 才解锁
        school.cash = 560.0
        assertFalse(registry.condition(school))
        school.cash = 650.0
        assertTrue(registry.condition(school))
    }

    @Test
    fun fullTeachersDescriptionMatchesCondition() {
        val achievement = AchievementRegistry.getAllAchievements().first { it.id == "full_teachers" }
        // 条件实际为 等级2+有设施：文案必须与条件一致（不再声称“教师数达上限”）
        assertTrue(achievement.description.contains("2级"))
        assertFalse(achievement.description.contains("上限"))
        val school = newSchool().apply {
            campusLevel = 2
            facilities.add(Facility(type = FacilityType.CLASSROOM))
        }
        assertTrue(achievement.condition(school))
    }

    @Test
    fun unlockIsStickyAndResetClears() {
        val manager = AchievementManager()
        val school = newSchool().apply {
            facilities.add(Facility(type = FacilityType.CLASSROOM))
        }
        val first = runBlocking { manager.checkAchievements(school) }
        assertTrue(first.isNotEmpty())
        // 再次检查不重复解锁
        val second = runBlocking { manager.checkAchievements(school) }
        assertTrue(second.isEmpty())
        assertTrue(manager.getUnlocked().isNotEmpty())
        manager.reset()
        assertTrue(manager.getUnlocked().isEmpty())
    }
}
