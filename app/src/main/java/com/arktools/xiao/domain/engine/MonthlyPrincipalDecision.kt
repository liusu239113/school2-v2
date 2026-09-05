package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.EventChoice
import com.arktools.xiao.domain.model.EventConsequence
import com.arktools.xiao.domain.model.FacilityCapacity
import com.arktools.xiao.domain.model.FacilityType
import com.arktools.xiao.domain.model.GameEvent
import com.arktools.xiao.domain.model.School

internal object MonthlyPrincipalDecision {
    fun build(
        school: School,
        studentCount: Int,
        latestCash: Double,
        netProfit: Double,
        extraWindows: Int
    ): GameEvent.ChoiceEvent {
        val beds = FacilityCapacity.totalBeds(school.facilities)
        val seats = FacilityCapacity.totalCanteenSeats(school.facilities, extraWindows)
        val classroomSeats = school.facilities
            .filter { it.type == FacilityType.CLASSROOM && it.isOperational }
            .sumOf { FacilityCapacity.classSlots(it.level) * 30 }
        val bedGap = (studentCount - beds).coerceAtLeast(0)
        val mealGap = (studentCount - seats).coerceAtLeast(0)
        val seatGap = (studentCount - classroomSeats).coerceAtLeast(0)
        val title: String
        val message: String
        val choices: List<EventChoice>
        when {
            latestCash < 0 || netProfit < -8.0 -> {
                title = "校长月度决策：保运转"
                message = "账上吃紧。这个月必须拍板：是砍专项保工资，还是硬撑口碑。不做决定，下月声誉和招生都会掉。"
                choices = listOf(
                    EventChoice("砍掉一半专项活动，先把工资发出去", EventConsequence(cashChange = 6.0, reputationChange = -40)),
                    EventChoice("对外解释困难，请家长再给学校一点时间", EventConsequence(cashChange = 2.0, reputationChange = -20)),
                    EventChoice("继续硬撑，对外装作一切正常", EventConsequence(cashChange = -4.0, reputationChange = 15))
                )
            }
            mealGap > 0 -> {
                title = "校长月度决策：学生吃饭"
                message = "有 $mealGap 人挤食堂。窗口不够，月底已经有投诉。这个月必须处理吃饭问题。"
                choices = listOf(
                    EventChoice("加开窗口并补贴热菜", EventConsequence(cashChange = -5.0, reputationChange = 50)),
                    EventChoice("错峰放学，让一半学生晚去食堂", EventConsequence(cashChange = -1.0, reputationChange = 10)),
                    EventChoice("先顶着，等有钱再建食堂", EventConsequence(reputationChange = -80))
                )
            }
            bedGap > 0 -> {
                title = "校长月度决策：床位"
                message = "宿舍缺 $bedGap 张床。不住下的学生下季不会来报到。"
                choices = listOf(
                    EventChoice("腾教室改临时宿舍", EventConsequence(cashChange = -3.0, reputationChange = -10)),
                    EventChoice("联系校外公寓过渡", EventConsequence(cashChange = -6.0, reputationChange = 30)),
                    EventChoice("让学生自己想办法", EventConsequence(reputationChange = -90))
                )
            }
            seatGap > 0 || classroomSeats <= studentCount -> {
                title = "校长月度决策：教室学位"
                message = "教室学位已经顶满。9月想再招人，这个月必须决定是挤一挤还是停招。"
                choices = listOf(
                    EventChoice("大班授课，先把人塞进去", EventConsequence(cashChange = -2.0, reputationChange = -25)),
                    EventChoice("公开说明学位已满，控制招生", EventConsequence(reputationChange = 20)),
                    EventChoice("不管，继续按报名人数收", EventConsequence(reputationChange = -70))
                )
            }
            else -> {
                title = "校长月度决策：下月重心"
                message = "账上还能转。这个月选一个重心，选完立刻见账，不做等于默认混日子。"
                choices = listOf(
                    EventChoice("把精力放在招生宣传上", EventConsequence(cashChange = -3.0, reputationChange = 70)),
                    EventChoice("把精力放在课堂和科研上", EventConsequence(cashChange = -2.0, reputationChange = 40)),
                    EventChoice("把精力放在学生吃饭和住宿上", EventConsequence(cashChange = -4.0, reputationChange = 35))
                )
            }
        }
        return GameEvent.ChoiceEvent(title = title, message = message, choices = choices)
    }
}
