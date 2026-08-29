package com.arktools.xiaozhang.ui.external

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.competition.UniversityCompetitionManager
import com.arktools.xiaozhang.domain.competitor.CompetitorEngine
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.model.AdmissionTrack
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.policy.SchoolPolicyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

/**
 * 外联：大学排名榜（含 rival 对比）+ 校际学科竞赛报名。
 */
@HiltViewModel
class ExternalViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val policyManager: SchoolPolicyManager,
    private val competitorEngine: CompetitorEngine,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager,
    private val studentRepository: com.arktools.xiaozhang.domain.repository.StudentRepository,
    private val alumniNetwork: com.arktools.xiaozhang.domain.alumni.AlumniNetwork
) : ViewModel() {

    data class AlumniDigest(
        val total: Int = 0,
        val networkLevel: Int = 1,
        val avgSatisfaction: Float = 0f,
        val notable: List<String> = emptyList()
    )

    data class RankRow(
        val name: String,
        val reputation: Long,
        val isPlayer: Boolean,
        val isActive: Boolean,
        val studentCount: Int = 0
    )

    data class CompetitionUiState(
        val alumniDigest: AlumniDigest = AlumniDigest(),
        val ranks: List<RankRow> = emptyList(),
        val playerRank: Int = 0,
        val catalog: List<UniversityCompetitionManager.CatalogEntry> = emptyList(),
        val active: List<UniversityCompetitionManager.CompetitionState> = emptyList(),
        val lastSummary: String = "",
        val message: String? = null
    )

    private val _state = MutableStateFlow(CompetitionUiState())
    val state: StateFlow<CompetitionUiState> = _state.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            combine(
                schoolRepository.getSchoolFlow(),
                competitorEngine.competitorState,
                policyManager.policies
            ) { school, competitors, policies ->
                Triple(school, competitors, policies)
            }.collect { (school, competitors, policies) ->
                if (school == null) return@collect
                val manager = policyManager.competitionManager
                val snap = alumniNetwork.snapshotState()
                val digest = AlumniDigest(
                    total = snap.stats.totalAlumni,
                    networkLevel = snap.networkLevel,
                    avgSatisfaction = snap.stats.averageSatisfaction,
                    notable = snap.alumni
                        .sortedByDescending { it.successPotential }
                        .take(3)
                        .map { a -> a.name + "（" + a.career.displayName + "·" + a.careerLevel.displayName + "）" }
                )
                val alumniDigest = digest
                val playerStudents = runCatching {
                    studentRepository.getActiveStudentCount()
                }.getOrDefault(0)
                val rankRows = competitorEngine.getRankings(school, playerStudents).map {
                    RankRow(
                        it.name, it.reputation, it.isPlayer, it.isActive,
                        if (it.isPlayer) playerStudents else 0
                    )
                }
                _state.value = _state.value.copy(
                    ranks = rankRows,
                    playerRank = competitorEngine.getPlayerRank(school),
                    catalog = manager.getCatalog(school.campusLevel, policies.collegeDevelopment.founded),
                    active = manager.snapshotState().active,
                    lastSummary = manager.snapshotState().lastResultSummary,
                    alumniDigest = alumniDigest
                )
            }
        }
    }

    fun registerCompetition(
        track: AdmissionTrack,
        tier: UniversityCompetitionManager.CompetitionTier
    ) {
        viewModelScope.safeLaunch {
            val result = gameEngine.registerUniversityCompetition(track, tier)
            _state.value = _state.value.copy(
                message = result.message,
                active = policyManager.competitionManager.snapshotState().active
            )
            if (result.success) audioManager.playCashLose()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

@Composable
fun ExternalScreen(
    viewModel: ExternalViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1724))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("外联", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "大学排名 · 校际学科竞赛",
                color = Color(0xFFB8C7D6),
                fontSize = 13.sp
            )
        }

        // ===== 排名榜 =====
        item {
            Text("大学排名榜", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.ranks.isEmpty()) {
                    Text("暂无排名数据", fontSize = 13.sp, color = Color(0xFF617386))
                } else {
                    state.ranks.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (row.isPlayer) Color(0xFF1E96C8) else Color(0xFF617386),
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Text(
                                row.name + if (row.isPlayer) "（本校）" else "",
                                fontSize = 13.sp,
                                fontWeight = if (row.isPlayer) FontWeight.Bold else FontWeight.Normal,
                                color = Color(0xFF182635),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "生${row.studentCount} · 声誉 ${row.reputation}",
                                fontSize = 12.sp,
                                color = Color(0xFF617386)
                            )
                        }
                    }
                    Text(
                        "本校当前名次：第 ${state.playerRank} 名 · 赢竞赛/通过评估可爬榜",
                        fontSize = 12.sp,
                        color = Color(0xFF1E96C8)
                    )
                }
            }
        }

        // ===== 校友动态 =====
        item {
            Text("校友动态", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.alumniDigest.total == 0) {
                    Text(
                        "还没有毕业生。学生毕业后将进入校友网络，优秀校友会在这里亮相。",
                        fontSize = 12.sp,
                        color = Color(0xFF617386)
                    )
                } else {
                    Text(
                        "校友 " + state.alumniDigest.total + " 人 · 校友网络 Lv." + state.alumniDigest.networkLevel + " · 平均满意度 " + state.alumniDigest.avgSatisfaction.toInt() + "%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF182635)
                    )
                    state.alumniDigest.notable.forEach { name ->
                        Text(
                            "· " + name,
                            fontSize = 12.sp,
                            color = Color(0xFF617386)
                        )
                    }
                }
            }
        }

        // ===== 竞赛 =====
        item {
            Text("校际学科竞赛", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.message?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = Color(0xFFD49A45),
                        modifier = Modifier.clickable { viewModel.consumeMessage() }
                    )
                }
                if (state.catalog.isEmpty()) {
                    Text(
                        "成立学院后，对应大类学生才能代表学校参赛",
                        fontSize = 12.sp,
                        color = Color(0xFF617386)
                    )
                } else {
                    state.active.forEach { comp ->
                        Text(
                            "已报名：${comp.name}（${comp.resolveMonth}月结算）",
                            fontSize = 12.sp,
                            color = Color(0xFF2E9B78)
                        )
                    }
                    state.catalog.forEach { entry ->
                        val already = state.active.any {
                            it.trackName == entry.track.displayName && it.tier == entry.tier.name
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${entry.tier.displayName}·${entry.track.displayName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF182635)
                                )
                                Text(
                                    "报名 ${entry.entryFee.toInt()}万 · 奖金 ${entry.prize.toInt()}万 + ${entry.reputationReward}声誉",
                                    fontSize = 11.sp,
                                    color = Color(0xFF617386)
                                )
                            }
                            if (already) {
                                Text("已报名", fontSize = 12.sp, color = Color(0xFF2E9B78))
                            } else {
                                Text(
                                    "报名",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E96C8),
                                    modifier = Modifier.clickable {
                                        viewModel.registerCompetition(entry.track, entry.tier)
                                    }
                                )
                            }
                        }
                    }
                }
                if (state.lastSummary.isNotEmpty()) {
                    Text(
                        state.lastSummary,
                        fontSize = 11.sp,
                        color = Color(0xFF617386)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
