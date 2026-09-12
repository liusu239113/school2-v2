package com.arktools.xiao.domain.autohandle

import com.arktools.xiao.domain.model.EventChoice
import com.arktools.xiao.domain.model.EventConsequence
import com.arktools.xiao.domain.model.GameEvent
import org.junit.Assert.assertEquals
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

    @Test
    fun teacherStoryGoesToPersonnel() {
        val manager = AutoHandleManager()
        val story = GameEvent.ChoiceEvent(
            title = "教师故事·青年才俊",
            message = "青年教师刘伟的教学评分已达240",
            choices = listOf(
                EventChoice("资助他读博深造", EventConsequence()),
                EventChoice("压担子多带课", EventConsequence())
            )
        )
        // 没任命人事处 → 仍要手动
        assertNull(manager.shouldAutoHandle(story))
        manager.updateConfig(
            AutoHandleConfig(
                personnelOfficerId = "teacher-1",
                teacherStoryStrategy = AutoStrategy.AUTO_APPROVE
            )
        )
        assertTrue(manager.shouldAutoHandle(story) is AutoHandleResult.AutoChoice)
    }

    @Test
    fun canteenComplaintGoesToStudentAffairs() {
        val manager = AutoHandleManager()
        val canteen = GameEvent.ChoiceEvent(
            title = "食堂吃不上饭",
            message = "食堂只有40个餐位，却有66名学生。",
            choices = listOf(
                EventChoice("加开窗口、延长供餐", EventConsequence()),
                EventChoice("先顶着，让学生错峰", EventConsequence()),
                EventChoice("公开道歉并承诺扩建食堂", EventConsequence())
            )
        )
        assertNull(manager.shouldAutoHandle(canteen))
        manager.updateConfig(
            AutoHandleConfig(
                studentAffairsOfficerId = "teacher-2",
                studentWelfareStrategy = AutoStrategy.AUTO_APPROVE
            )
        )
        assertTrue(manager.shouldAutoHandle(canteen) is AutoHandleResult.AutoChoice)
    }

    @Test
    fun monthlyDecisionFollowsStudentAffairsStrategy() {
        val manager = AutoHandleManager()
        val decision = GameEvent.ChoiceEvent(
            title = "校长月度决策：学生吃饭",
            message = "有 26 人挤食堂。",
            choices = listOf(
                EventChoice("加开窗口并补贴热菜", EventConsequence()),
                EventChoice("错峰放学", EventConsequence()),
                EventChoice("先顶着", EventConsequence())
            )
        )
        manager.updateConfig(
            AutoHandleConfig(
                studentAffairsOfficerId = "teacher-2",
                monthlyDecisionStrategy = AutoStrategy.MANUAL
            )
        )
        assertNull(manager.shouldAutoHandle(decision))
        manager.updateConfig(
            AutoHandleConfig(
                studentAffairsOfficerId = "teacher-2",
                monthlyDecisionStrategy = AutoStrategy.AUTO_REJECT
            )
        )
        val rejected = manager.shouldAutoHandle(decision)
        assertTrue(rejected is AutoHandleResult.AutoChoice)
        assertEquals(2, (rejected as AutoHandleResult.AutoChoice).choiceIndex)
    }

    @Test
    fun repairEventBelongsToLogisticsNotWelfare() {
        val manager = AutoHandleManager()
        manager.updateConfig(
            AutoHandleConfig(
                studentAffairsOfficerId = "teacher-2",
                studentWelfareStrategy = AutoStrategy.AUTO_APPROVE,
                logisticsRepairStrategy = AutoStrategy.MANUAL
            )
        )
        // 标题带「宿舍」但属于设施维修，应该走后勤处；后勤处设的是手动 → 仍弹窗
        assertNull(manager.shouldAutoHandle(repairEvent()))
    }
}
