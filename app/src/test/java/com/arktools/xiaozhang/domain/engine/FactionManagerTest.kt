package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.FactionType
import com.arktools.xiaozhang.domain.model.Principal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactionManagerTest {

    @Test
    fun `applies selected faction choice exactly once`() {
        val manager = FactionManager()
        val principal = Principal(
            factionRelations = mutableMapOf(
                FactionType.TEACHING to 50,
                FactionType.ADMINISTRATIVE to 50,
                FactionType.REFORM to 50,
                FactionType.CONSERVATIVE to 50
            )
        )
        val event = FactionEvent(
            id = "apply-once",
            type = FactionEventType.DISSATISFACTION,
            title = "测试事件",
            message = "测试用",
            affectedFactions = listOf(FactionType.TEACHING, FactionType.ADMINISTRATIVE),
            reputationImpact = 0L,
            choices = listOf(
                FactionEventChoice(
                    text = "处理",
                    relationChanges = mapOf(
                        FactionType.TEACHING to 25,
                        FactionType.ADMINISTRATIVE to -10
                    )
                )
            )
        )

        assertTrue(manager.applyFactionEventChoice(principal, event, 0))
        assertEquals(75, principal.factionRelations[FactionType.TEACHING])
        assertEquals(40, principal.factionRelations[FactionType.ADMINISTRATIVE])

        assertFalse(manager.applyFactionEventChoice(principal, event, 0))
        assertEquals(75, principal.factionRelations[FactionType.TEACHING])
        assertEquals(40, principal.factionRelations[FactionType.ADMINISTRATIVE])
    }

    @Test
    fun `rejects invalid choice without consuming event`() {
        val manager = FactionManager()
        val principal = Principal()
        val event = FactionEvent(
            id = "invalid-index",
            type = FactionEventType.DISSATISFACTION,
            title = "测试事件",
            message = "测试用",
            affectedFactions = listOf(FactionType.TEACHING),
            reputationImpact = 0L,
            choices = listOf(
                FactionEventChoice("处理", mapOf(FactionType.TEACHING to 10))
            )
        )

        assertFalse(manager.applyFactionEventChoice(principal, event, 1))
        assertEquals(50, principal.factionRelations[FactionType.TEACHING])

        assertTrue(manager.applyFactionEventChoice(principal, event, 0))
        assertEquals(60, principal.factionRelations[FactionType.TEACHING])
    }

    @Test
    fun `clamps faction choice relation changes to valid bounds`() {
        val manager = FactionManager()
        val principal = Principal(
            factionRelations = mutableMapOf(
                FactionType.TEACHING to 95,
                FactionType.ADMINISTRATIVE to 3,
                FactionType.REFORM to 50,
                FactionType.CONSERVATIVE to 50
            )
        )
        val event = FactionEvent(
            id = "relation-bounds",
            type = FactionEventType.INTERNAL_CONFLICT,
            title = "测试事件",
            message = "测试用",
            affectedFactions = listOf(FactionType.TEACHING, FactionType.ADMINISTRATIVE),
            reputationImpact = 0L,
            choices = listOf(
                FactionEventChoice(
                    text = "处理",
                    relationChanges = mapOf(
                        FactionType.TEACHING to 20,
                        FactionType.ADMINISTRATIVE to -20
                    )
                )
            )
        )

        assertTrue(manager.applyFactionEventChoice(principal, event, 0))
        assertEquals(100, principal.factionRelations[FactionType.TEACHING])
        assertEquals(0, principal.factionRelations[FactionType.ADMINISTRATIVE])
    }
}
