package com.arktools.xiao.domain.teacherdev

import com.arktools.xiao.domain.model.Teacher
import com.arktools.xiao.domain.model.TeacherLevel
import com.arktools.xiao.domain.model.TeacherRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 年度人才池自愈规则测试：
 * - 新年度 → 生成
 * - 同年招满 → 不重建（保住年度名额设计，防刷）
 * - 同年但池被清空（新档竞态/存档恢复覆盖）→ 自动重建，避免整年无才可招
 */
class TeacherTalentPoolRegenerationTest {

    private fun generator(level: TeacherLevel, count: Int): List<Teacher> =
        List(count) { index ->
            Teacher(
                id = "${level.name}-gen-$index",
                name = "候选$level$index",
                level = level,
                role = TeacherRole.MATH,
                teaching = 300,
                research = 300,
                management = 300,
                psychology = 300,
                salary = 1.0
            )
        }

    private fun newManager(): TeacherDevelopmentManager = TeacherDevelopmentManager()

    private fun availablePool(manager: TeacherDevelopmentManager) =
        manager.state.value.talentPool.count { it.status == TalentStatus.AVAILABLE }

    @Test
    fun freshYearGeneratesPool() {
        val manager = newManager()
        manager.ensureAnnualTalentPool(2026, 1, emptyList(), ::generator)
        assertTrue(availablePool(manager) > 0)
        // Lv1：C5+B2，广告渠道（C/B）单次最多展示 6 名候选
        assertEquals(6, manager.candidatesForChannel(TalentChannel.AD).size)
    }

    @Test
    fun sameYearKeepsQuotaAfterAllHired() {
        val manager = newManager()
        manager.ensureAnnualTalentPool(2026, 1, emptyList(), ::generator)
        // 全部录用
        manager.state.value.talentPool
            .filter { it.status == TalentStatus.AVAILABLE }
            .forEach { manager.consumeTalentCandidate(it.id) }
        assertEquals(0, availablePool(manager))
        // 同年再触发 ensure：不重建，防刷年度名额
        manager.ensureAnnualTalentPool(2026, 1, emptyList(), ::generator)
        assertEquals(0, manager.candidatesForChannel(TalentChannel.AD).size)
    }

    @Test
    fun sameYearEmptyPoolSelfHeals() {
        val manager = newManager()
        manager.ensureAnnualTalentPool(2026, 1, emptyList(), ::generator)
        assertTrue(availablePool(manager) > 0)
        // 模拟存档恢复把池覆盖为空（同年记录仍在）
        manager.restoreFromJson("""{"talentPoolYear":2026,"talentPool":[]}""")
        assertEquals(0, availablePool(manager))
        // 再触发 ensure：同年空池应自动重建
        manager.ensureAnnualTalentPool(2026, 1, emptyList(), ::generator)
        assertTrue(availablePool(manager) > 0)
    }

    @Test
    fun nextYearRefreshesPoolAndLocksChannels() {
        val manager = newManager()
        manager.ensureAnnualTalentPool(2026, 1, emptyList(), ::generator)
        manager.unlockChannel(TalentChannel.AD)
        manager.ensureAnnualTalentPool(2027, 1, emptyList(), ::generator)
        assertTrue(availablePool(manager) > 0)
        // 新一年渠道重新上锁
        assertTrue(!manager.isChannelUnlocked(TalentChannel.AD))
    }
}
