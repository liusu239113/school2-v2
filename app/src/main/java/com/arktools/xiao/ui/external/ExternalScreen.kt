package com.arktools.xiao.ui.external

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.audio.AudioManager
import com.arktools.xiao.domain.competition.UniversityCompetitionManager
import com.arktools.xiao.domain.competitor.CompetitorEngine
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.model.AdmissionTrack
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.policy.SchoolPolicyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch

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
    private val studentRepository: com.arktools.xiao.domain.repository.StudentRepository,
    private val alumniNetwork: com.arktools.xiao.domain.alumni.AlumniNetwork
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

    fun raidRivalTeachers() {
        viewModelScope.safeLaunch {
            val cost = 6.0
            val result = schoolRepository.mutateSchool { school ->
                if (school.cash < cost) {
                    _state.value = _state.value.copy(message = "挖人需要 6 万，经费不够")
                    return@mutateSchool false
                }
                school.cash -= cost
                school.reputation += 80
                true
            }
            if (result != null) {
                competitorEngine.hurtStrongestRival(moraleDelta = -0.08f, reputationDelta = -120L)
                audioManager.playCashLose()
                _state.value = _state.value.copy(message = "挖到对方一名骨干。立刻 +80 声誉，对手士气下降。")
            }
        }
    }

    fun raidRivalStudents() {
        viewModelScope.safeLaunch {
            val cost = 8.0
            val result = schoolRepository.mutateSchool { school ->
                if (school.cash < cost) {
                    _state.value = _state.value.copy(message = "抢生源需要 8 万，经费不够")
                    return@mutateSchool false
                }
                school.cash -= cost
                school.reputation += 40
                true
            }
            if (result != null) {
                competitorEngine.hurtStrongestRival(moraleDelta = -0.05f, studentDelta = -20)
                audioManager.playCashLose()
                _state.value = _state.value.copy(message = "对方学区被你砸穿。对手少了生源，本校下季更好招。")
            }
        }
    }
}

@Composable
fun ExternalScreen(
    viewModel: ExternalViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onNavigateTo: (Int) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(1) }

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
                "委托赚钱 · 排名看口碑 · 合作挖人和抢生源",
                color = Color(0xFFB8C7D6),
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("排名" to 0, "委托" to 1, "合作" to 2).forEach { (label, index) ->
                    val selected = tab == index
                    Text(
                        label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(if (selected) Color(0xFF1E96C8) else Color(0xFF24384C))
                            .clickable { tab = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Text(
                "企业委托是外联真正能赚钱的地方。排名只是结果，合作用来打对手。",
                color = Color(0xFF8AA0B4),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "社会合作 / 扩建",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF1E96C8))
                        .clickable { onNavigateTo(4) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    "国际交流",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF1E96C8))
                        .clickable { onNavigateTo(47) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        if (tab == 1) item {
            val commissionViewModel: com.arktools.xiao.ui.district.CommissionViewModel =
                androidx.hilt.navigation.compose.hiltViewModel()
            val commissionState by commissionViewModel.state.collectAsState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("企业合作委托", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182635))
                Text(
                    "执行中 ${commissionState.active.size}/2 · 结项 ${commissionState.completedCount} · 失败 ${commissionState.failedCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF617386)
                )
                Text(
                    "每月企业送来要约：接单付启动资金，到期按师资/学院/设施结算。这是外联收入、就业和生源加成的主要来源。",
                    fontSize = 12.sp,
                    color = Color(0xFF617386)
                )
                commissionState.message?.let { msg ->
                    Text(
                        msg,
                        fontSize = 12.sp,
                        color = Color(0xFF1E96C8),
                        modifier = Modifier.clickable { commissionViewModel.consumeMessage() }
                    )
                }
                commissionState.active.forEach { commission ->
                    Text(
                        "▶ ${commission.title} · 剩余 ${commission.remainingMonths} 月 · 每月 +${commission.monthlyCashWan.toInt()}万",
                        fontSize = 12.sp,
                        color = Color(0xFF2E9B78)
                    )
                }
                if (commissionState.offers.isEmpty()) {
                    Text("本月暂无新要约，下月初企业会送来新委托。", fontSize = 12.sp, color = Color(0xFF617386))
                } else {
                    commissionState.offers.forEach { commission ->
                        val blocked = commissionViewModel.requirementBlocked(commission)
                        Text(
                            commission.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF182635)
                        )
                        Text(
                            "${commission.partner} · ${commission.durationMonths} 月 · 投入 ${commission.upfrontCostWan.toInt()}万 · 每月 +${commission.monthlyCashWan.toInt()}万 · 结项 +${commission.completionCashWan.toInt()}万",
                            fontSize = 11.sp,
                            color = Color(0xFF617386)
                        )
                        if (blocked != null) {
                            Text("条件不足：$blocked", fontSize = 11.sp, color = Color(0xFFB0413E))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                "谢绝",
                                fontSize = 12.sp,
                                color = Color(0xFF617386),
                                modifier = Modifier.clickable { commissionViewModel.decline(commission.id) }
                            )
                            Text(
                                "接单",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (blocked == null) Color(0xFF1E96C8) else Color(0xFFAAAAAA),
                                modifier = Modifier.clickable(enabled = blocked == null) {
                                    commissionViewModel.accept(commission.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (tab == 0) item {
            Text("大学排名榜", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (tab == 0) item {
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

        if (tab == 2) item {
            Text("合作：挖人 / 抢生源", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (tab == 2) item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "高风险动作。成功会伤对手、抬本校；失败会掉口碑。",
                    fontSize = 12.sp,
                    color = Color(0xFF617386)
                )
                Text(
                    "挖对方老师（6万）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E96C8),
                    modifier = Modifier.clickable { viewModel.raidRivalTeachers() }
                )
                Text("高薪挖一名对方骨干，本校声誉立刻上升，对手士气下降。", fontSize = 11.sp, color = Color(0xFF617386))
                Text(
                    "抢对方生源（8万）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E96C8),
                    modifier = Modifier.clickable { viewModel.raidRivalStudents() }
                )
                Text("在对方学区砸招生广告。下季招生更容易，但可能被反噬。", fontSize = 11.sp, color = Color(0xFF617386))
            }
        }
        if (tab == 2) item {
            Text("校友动态", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (tab == 2) item {
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

        if (tab == 0) item {
            Text("校际学科竞赛", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (tab == 0) item {
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
