package com.arktools.xiaozhang.domain.competition

import com.arktools.xiaozhang.domain.model.AdmissionTrack
import com.arktools.xiaozhang.domain.model.FacultyCoverage
import com.arktools.xiaozhang.domain.policy.CollegeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 校际学科竞赛：与报考大类/学院/师资覆盖咬合的长线玩法。
 * - 每年 9 月按已建学院生成邀请，报名费进、奖金和声誉出
 * - 2 个月后结算，胜率由该学院师资覆盖 + 声誉 + 学院质量加成决定
 * - 状态通过 policyJson 内嵌 JSON 持久化，不改数据库结构
 */
@Singleton
class UniversityCompetitionManager @Inject constructor() {

    @Serializable
    data class CompetitionState(
        val id: String,
        val name: String,
        val trackName: String,
        val tier: String,
        val entryFee: Double,
        val prize: Double,
        val reputationReward: Long,
        val registerYear: Int,
        val registerMonth: Int,
        val resolveYear: Int,
        val resolveMonth: Int
    )

    @Serializable
    data class ManagerState(
        val active: List<CompetitionState> = emptyList(),
        val lastResultSummary: String = "",
        val winsThisYear: Int = 0,
        val totalWins: Int = 0
    )

    data class CatalogEntry(
        val tier: CompetitionTier,
        val track: AdmissionTrack,
        val entryFee: Double,
        val prize: Double,
        val reputationReward: Long
    )

    enum class CompetitionTier(
        val displayName: String,
        val unlockLevel: Int,
        val repBase: Long
    ) {
        CITY("市际联赛", 2, 40L),
        PROVINCE("省赛", 3, 120L),
        NATIONAL("全国赛", 5, 400L)
    }

    private var state = ManagerState()
    private val random = Random(System.currentTimeMillis())

    fun snapshotState(): ManagerState = state

    fun toJson(): String = runCatching { Json.encodeToString(state) }.getOrDefault("")

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        runCatching { state = Json.decodeFromString<ManagerState>(json) }
    }

    fun reset() {
        state = ManagerState()
    }

    fun getCatalog(
        campusLevel: Int,
        founded: List<CollegeType>
    ): List<CatalogEntry> {
        val entries = mutableListOf<CatalogEntry>()
        val tracks = AdmissionTrack.entries.filter { founded.contains(it.college) }
        tracks.forEach { track ->
            CompetitionTier.entries
                .filter { campusLevel >= it.unlockLevel }
                .forEach { tier ->
                    entries.appendCatalog(track, tier)
                }
        }
        return entries
    }

    private fun MutableList<CatalogEntry>.appendCatalog(track: AdmissionTrack, tier: CompetitionTier) {
        val baseFee = when (tier) {
            CompetitionTier.CITY -> 12.0
            CompetitionTier.PROVINCE -> 30.0
            CompetitionTier.NATIONAL -> 80.0
        }
        entries.add(
            CatalogEntry(
                tier = tier,
                track = track,
                entryFee = baseFee,
                prize = baseFee * 2.0,
                reputationReward = (tier.repBase * (1 + track.ordinal * 0.1)).toLong()
            )
        )
    }

    fun register(
        entry: CatalogEntry,
        year: Int,
        month: Int
    ): CompetitionState? {
        if (state.active.any { it.trackName == entry.track.displayName && it.tier == entry.tier.name }) {
            return null
        }
        val resolveAbs = year * 12 + month + 2
        val comp = CompetitionState(
            id = "COMP_${year}_${month}_${entry.track.name}_${entry.tier.name}",
            name = "${entry.tier.displayName}·${entry.track.displayName}学科竞赛",
            trackName = entry.track.displayName,
            tier = entry.tier.name,
            entryFee = entry.entryFee,
            prize = entry.prize,
            reputationReward = entry.reputationReward,
            registerYear = year,
            registerMonth = month,
            resolveYear = resolveAbs / 12,
            resolveMonth = resolveAbs % 12
        )
        state = state.copy(active = state.active + comp)
        return comp
    }

    /**
     * 月度推进：结算到期的竞赛。
     * winChance 由该学院师资覆盖(0-1) + 声誉加成 + 基础成功率构成。
     */
    fun resolveDue(
        year: Int,
        month: Int,
        reputation: Long,
        coverageByCollege: Map<CollegeType, Float>,
        rivalEdge: Float = 0f,
        rivalName: String = ""
    ): List<Pair<CompetitionState, Boolean>> {
        val due = state.active.filter { it.resolveYear == year && it.resolveMonth == month }
        if (due.isEmpty()) return emptyList()
        val results = mutableListOf<Pair<CompetitionState, Boolean>>()
        due.forEach { comp ->
            val track = AdmissionTrack.entries.firstOrNull { it.displayName == comp.trackName }
            val coverage = track?.let { coverageByCollege[it.college] } ?: 0.6f
            val repBonus = (reputation.toFloat() / 20000f).coerceAtMost(0.20f)
            val tierPenalty = when (comp.tier) {
                CompetitionTier.CITY.name -> 0.00f
                CompetitionTier.PROVINCE.name -> -0.10f
                else -> -0.20f
            }
            val winChance = (0.30f + coverage * 0.45f + repBonus + tierPenalty + rivalEdge)
                .coerceIn(0.10f, 0.90f)
            val win = random.nextFloat() < winChance
            results.add(comp to win)
        }
        val wonIds = results.filter { it.second }.map { it.first.id }
        state = state.copy(
            active = state.active.filter { active -> due.none { it.id == active.id } },
            lastResultSummary = results.joinToString("；") { (comp, win) ->
                val versus = if (rivalName.isNotBlank()) "（对手：$rivalName）" else ""
                "${comp.name}${if (win) "夺冠" else "止步"}$versus"
            },
            winsThisYear = state.winsThisYear + wonIds.size,
            totalWins = state.totalWins + wonIds.size
        )
        return results
    }

    fun newYearReset(year: Int) {
        state = state.copy(winsThisYear = 0)
    }

    /** 学年总结：竞赛战绩 */
    fun yearlySummary(): String {
        return if (state.totalWins == 0 && state.lastResultSummary.isEmpty()) {
            "本学年没有竞赛获奖记录"
        } else {
            "本学年竞赛夺冠${state.winsThisYear}次（累计${state.totalWins}）。${state.lastResultSummary}"
        }
    }
}
