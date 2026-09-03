package com.arktools.xiao.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Marketing campaign for a course.
 * Players can invest in marketing to boost enrollment.
 */
@Serializable
data class MarketingCampaign(
    val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val channel: MarketingChannel,
    val budget: Double,            // monthly budget in 万元
    var daysActive: Int = 0,       // how many days the campaign has run
    var totalSpent: Double = 0.0,  // total amount spent so far
    var isActive: Boolean = true
)

enum class MarketingChannel(
    val displayName: String,
    val description: String,
    val minBudget: Double,          // minimum monthly budget (万元)
    val maxBudget: Double,          // maximum monthly budget (万元)
    val enrollmentMultiplier: Float, // base enrollment boost per 万元 spent
    val reputationBoost: Float,     // reputation gain per month
    val decayRate: Float,           // how fast effect wears off after stopping (0-1, lower = longer effect)
    val rampUpDays: Int             // days to reach full effectiveness
) {
    FLYER(
        displayName = "传单派发",
        description = "低成本本地推广，见效快",
        minBudget = 0.5,
        maxBudget = 5.0,
        enrollmentMultiplier = 0.08f,
        reputationBoost = 0.5f,
        decayRate = 0.9f,
        rampUpDays = 3
    ),
    NEWSPAPER(
        displayName = "报纸广告",
        description = "覆盖面广，适合品牌建设",
        minBudget = 2.0,
        maxBudget = 15.0,
        enrollmentMultiplier = 0.05f,
        reputationBoost = 2.0f,
        decayRate = 0.7f,
        rampUpDays = 7
    ),
    SOCIAL_MEDIA(
        displayName = "社交媒体",
        description = "精准投放，年轻群体效果好",
        minBudget = 1.0,
        maxBudget = 20.0,
        enrollmentMultiplier = 0.10f,
        reputationBoost = 1.5f,
        decayRate = 0.85f,
        rampUpDays = 5
    ),
    WORD_OF_MOUTH(
        displayName = "口碑营销",
        description = "依靠学生推荐，成本低效果持久",
        minBudget = 0.2,
        maxBudget = 3.0,
        enrollmentMultiplier = 0.12f,
        reputationBoost = 3.0f,
        decayRate = 0.5f,
        rampUpDays = 14
    ),
    TV_AD(
        displayName = "电视广告",
        description = "高端品牌形象，巨额投入",
        minBudget = 10.0,
        maxBudget = 100.0,
        enrollmentMultiplier = 0.04f,
        reputationBoost = 5.0f,
        decayRate = 0.6f,
        rampUpDays = 14
    ),
    ONLINE_AD(
        displayName = "线上广告",
        description = "搜索引擎与信息流广告",
        minBudget = 3.0,
        maxBudget = 50.0,
        enrollmentMultiplier = 0.07f,
        reputationBoost = 1.0f,
        decayRate = 0.92f,
        rampUpDays = 3
    )
}

/**
 * Calculate total marketing enrollment multiplier for a course.
 * The multiplier is additive: base 1.0 + sum of channel boosts.
 */
object MarketingCalculator {

    /**
     * Get the enrollment multiplier contributed by active marketing campaigns.
     * @param campaigns list of campaigns for this course
     * @return multiplier >= 1.0
     */
    fun getEnrollmentMultiplier(campaigns: List<MarketingCampaign>): Double {
        var bonus = 0.0
        campaigns.filter { it.isActive }.forEach { campaign ->
            val channel = campaign.channel
            // Ramp-up: effectiveness increases linearly over rampUpDays
            val rampFactor = (campaign.daysActive.toFloat() / channel.rampUpDays).coerceAtMost(1.0f)
            // Budget effectiveness: diminishing returns (sqrt scaling)
            val budgetFactor = kotlin.math.sqrt(campaign.budget / channel.minBudget).coerceAtMost(5.0)
            bonus += channel.enrollmentMultiplier * rampFactor * budgetFactor
        }
        return 1.0 + bonus
    }

    /**
     * Get the monthly reputation boost from all active campaigns.
     */
    fun getReputationBoost(campaigns: List<MarketingCampaign>): Long {
        var totalBoost = 0f
        campaigns.filter { it.isActive }.forEach { campaign ->
            val channel = campaign.channel
            val rampFactor = (campaign.daysActive.toFloat() / channel.rampUpDays).coerceAtMost(1.0f)
            totalBoost += channel.reputationBoost * rampFactor
        }
        return totalBoost.toLong()
    }

    /**
     * Calculate the daily cost of all active campaigns for a course.
     */
    fun getDailyCost(campaigns: List<MarketingCampaign>): Double {
        return campaigns.filter { it.isActive }.sumOf { it.budget / 30.0 }
    }

    /**
     * Get total monthly marketing cost across all campaigns.
     */
    fun getTotalMonthlyCost(allCampaigns: List<MarketingCampaign>): Double {
        return allCampaigns.filter { it.isActive }.sumOf { it.budget }
    }
}
