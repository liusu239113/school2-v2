package com.arktools.xiao.ui.reputation

import androidx.lifecycle.ViewModel
import com.arktools.xiao.domain.reputation.ReputationBreakdown
import com.arktools.xiao.domain.reputation.ReputationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ReputationViewModel @Inject constructor(
    private val reputationManager: ReputationManager
) : ViewModel() {

    val state: StateFlow<ReputationBreakdown> = reputationManager.state
}
