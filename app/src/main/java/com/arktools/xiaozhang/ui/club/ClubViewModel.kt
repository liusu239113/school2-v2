package com.arktools.xiaozhang.ui.club

import androidx.lifecycle.ViewModel
import com.arktools.xiaozhang.domain.club.*
import com.arktools.xiaozhang.domain.clubactivity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ClubViewModel @Inject constructor(
    private val clubManager: ClubManager,
    private val clubActivityManager: ClubActivityManager
) : ViewModel() {

    // === 社团管理 ===
    val clubs: StateFlow<List<Club>> = clubManager.clubs
    val recentEvents: StateFlow<List<ClubEvent>> = clubManager.recentEvents
    val pendingApplications: StateFlow<List<ClubApplication>> = clubManager.pendingApplications

    fun getAvailableTypes(): List<ClubType> = clubManager.getAvailableTypes()
    fun disbandClub(clubId: Long) = clubManager.disbandClub(clubId)
    fun getTotalSatisfactionBonus(): Float = clubManager.getTotalSatisfactionBonus()
    fun getCampusLevel(): Int = clubManager.currentCampusLevel

    // === 社团活动 ===
    val activityState: StateFlow<ClubActivityState> = clubActivityManager.state

    fun planActivity(clubId: Long, type: ActivityType, name: String, budget: Long): Boolean {
        return clubActivityManager.planActivity(clubId, type, name, budget)
    }

    fun cancelActivity(activityId: Long) {
        clubActivityManager.cancelActivity(activityId)
    }

    fun registerForCompetition(clubId: Long, competition: CompetitionInfo): Boolean {
        return clubActivityManager.registerForCompetition(clubId, competition)
    }

    fun getAvailableCompetitions(reputation: Long): List<CompetitionInfo> {
        return clubActivityManager.getAvailableCompetitions(reputation)
    }

    fun getActivityTypes(): List<ActivityType> = ActivityType.entries
}
