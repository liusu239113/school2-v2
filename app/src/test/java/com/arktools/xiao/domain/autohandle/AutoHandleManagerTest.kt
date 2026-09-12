package com.arktools.xiao.domain.autohandle

import com.arktools.xiao.domain.model.EventChoice
import com.arktools.xiao.domain.model.EventConsequence
import com.arktools.xiao.domain.model.GameEvent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoHandleManagerTest {

    private fun raiseEvent(): GameEvent.ChoiceEvent = GameEvent.ChoiceEvent(
        title = "教师请求加薪",
        message = "张老师请求加薪",
        choices = listOf(
            EventChoice("同意", EventConsequence()),
            EventChoice("拒绝", EventConsequence())
        )
    )

    private fun repairEvent(): GameEvent.ChoiceEvent = GameEvent.ChoiceEvent(
        title = "设施维修：宿舍楼",
        message = "宿舍水管爆裂急需修理",
        choices = listOf(
            EventChoice("立即维修", EventConsequence()),
            EventChoice("暂不处理", EventConsequence())
        )
    )

    @Test
    fun vacantOfficeDoesNotAutoHandle() {
        val manager = AutoHandleManager()
        assertNull(manager.shouldAutoHandle(raiseEvent()))
        assertNull(manager.shouldAutoHandle(repairEvent()))
    }

    @Test
    fun appointedPersonnelAutoApprovesRaise() {
        val manager = AutoHandleManager()
        manager.updateConfig(
            AutoHandleConfig(
                personnelOfficerId = "teacher-1",
                teacherRaiseStrategy = AutoStrategy.AUTO_APPROVE
            )
        )
        val result = manager.shouldAutoHandle(raiseEvent())
        assertTrue(result is AutoHandleResult.AutoChoice)
        assertTrue((result as AutoHandleResult.AutoChoice).choiceIndex == 0)
        assertNull(manager.shouldAutoHandle(repairEvent()))
    }

    @Test
    fun appointedLogisticsAutoApprovesRepair() {
        val manager = AutoHandleManager()
        manager.updateConfig(
            AutoHandleConfig(
                logisticsOfficerId = "teacher-2",
                logisticsRepairStrategy = AutoStrategy.AUTO_APPROVE
            )
        )
        assertNotNull(manager.shouldAutoHandle(repairEvent()))
        assertNull(manager.shouldAutoHandle(raiseEvent()))
    }

    @Test
    fun manualStrategyStillPopsEvenWhenAppointed() {
        val manager = AutoHandleManager()
        manager.updateConfig(
            AutoHandleConfig(
                personnelOfficerId = "teacher-1",
                teacherRaiseStrategy = AutoStrategy.MANUAL
            )
        )
        assertNull(manager.shouldAutoHandle(raiseEvent()))
    }
}
