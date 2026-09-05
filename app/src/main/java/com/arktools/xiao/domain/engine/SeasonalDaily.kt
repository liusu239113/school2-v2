package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.GameEvent
import com.arktools.xiao.domain.model.School
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.reputation.ReputationDimension
import com.arktools.xiao.domain.reputation.ReputationManager
import com.arktools.xiao.domain.seasonal.ActivityType
import com.arktools.xiao.domain.seasonal.SeasonalActivity
import com.arktools.xiao.domain.seasonal.SeasonalActivityManager

/**
 * 季节活动日更：从 GameEngine.tick 拆出，避免单文件继续膨胀。
 */
internal object SeasonalDaily {

    fun mapDimension(activityType: ActivityType): ReputationDimension {
        return when (activityType) {
            ActivityType.SCIENCE_FAIR,
            ActivityType.DEBATE_TOURNAMENT -> ReputationDimension.ACADEMIC
            ActivityType.SPORTS_DAY,
            ActivityType.SUMMER_CAMP -> ReputationDimension.SPORTS
            ActivityType.ART_EXHIBITION,
            ActivityType.CULTURAL_FESTIVAL,
            ActivityType.NEW_YEAR_GALA -> ReputationDimension.ARTS
            ActivityType.CHARITY_EVENT,
            ActivityType.PARENT_DAY -> ReputationDimension.SOCIAL_SERVICE
            ActivityType.OPENING_CEREMONY,
            ActivityType.GRADUATION_CEREMONY,
            ActivityType.SPRING_OUTING -> ReputationDimension.MANAGEMENT
        }
    }

    suspend fun advance(
        manager: SeasonalActivityManager,
        school: School,
        schoolRepository: SchoolRepository,
        reputationManager: ReputationManager,
        emitMiniGame: suspend (SeasonalActivity) -> Unit,
        emitEvent: suspend (GameEvent, School) -> Unit
    ) {
        val dayAdvanceResult = manager.advanceDay(
            school.currentYear, school.currentMonth, school.currentDay
        )
        for (activity in dayAdvanceResult.newlyActiveActivities) {
            if (activity.type in setOf(
                    ActivityType.SPORTS_DAY,
                    ActivityType.CULTURAL_FESTIVAL,
                    ActivityType.SCIENCE_FAIR
                )
            ) {
                emitMiniGame(activity)
            }
        }
        for (result in dayAdvanceResult.completedResults) {
            schoolRepository.deductCash(result.cashSpent.toDouble() / 10000.0)
            schoolRepository.addReputation(result.reputationGain.toLong())
            emitEvent(
                GameEvent.PositiveEvent(
                    title = "${result.activity.type.displayName}圆满结束",
                    message = (result.specialMessage ?: "活动顺利完成！") +
                        " 声誉+${result.reputationGain}",
                    bonusCash = 0.0,
                    bonusReputation = 0
                ),
                school
            )
            reputationManager.addDimensionReputation(
                mapDimension(result.activity.type),
                result.reputationGain.toFloat(),
                "${result.activity.type.displayName}活动加成"
            )
        }
    }
}
