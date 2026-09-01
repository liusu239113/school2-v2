package com.arktools.xiaozhang.domain.clubactivity

import com.arktools.xiaozhang.domain.club.ClubManager
import com.arktools.xiaozhang.domain.club.ClubType
import com.arktools.xiaozhang.domain.club.ClubCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 社团活动管理系统（增强版）
 * - 活动策划与执行
 * - 校际竞赛与锦标赛
 * - 奖项与荣誉管理
 * - 活动预算与赞助
 * - 年度社团评比
 */
@Singleton
class ClubActivityManager @Inject constructor(
    private val clubManager: ClubManager
) {
    private val _state = MutableStateFlow(ClubActivityState())
    val state: StateFlow<ClubActivityState> = _state.asStateFlow()

    fun reset() {
        _state.value = ClubActivityState()
    }

    companion object {
        const val MAX_PLANNED_ACTIVITIES = 8
        const val MAX_COMPETITIONS = 5
        const val MAX_AWARDS_HISTORY = 50
        const val MAX_EVENTS_LOG = 30
    }

    /**
     * 策划新活动
     */
    fun planActivity(
        clubId: Long,
        type: ActivityType,
        name: String,
        budgetAllocated: Long
    ): Boolean {
        val clubs = clubManager.clubs.value
        val club = clubs.find { it.id == clubId } ?: return false
        val currentActivities = _state.value.plannedActivities
        if (currentActivities.size >= MAX_PLANNED_ACTIVITIES) return false

        val activity = PlannedActivity(
            id = System.currentTimeMillis(),
            clubId = clubId,
            clubName = club.name,
            clubType = club.type,
            activityType = type,
            name = name,
            status = ActivityStatus.PLANNING,
            budgetAllocated = budgetAllocated,
            budgetRequired = type.baseCost,
            preparationMonths = type.prepMonths,
            remainingPrepMonths = type.prepMonths,
            expectedParticipants = (club.memberCount * type.participantRatio).toInt().coerceAtLeast(5),
            qualityScore = 0f,
            createdMonth = 0
        )
        _state.update { it.copy(plannedActivities = it.plannedActivities + activity) }
        return true
    }

    /**
     * 取消活动
     */
    fun cancelActivity(activityId: Long) {
        _state.update { state ->
            state.copy(
                plannedActivities = state.plannedActivities.filter { it.id != activityId }
            )
        }
    }

    /**
     * 报名参加校际竞赛
     */
    fun registerForCompetition(
        clubId: Long,
        competition: CompetitionInfo
    ): Boolean {
        val clubs = clubManager.clubs.value
        val club = clubs.find { it.id == clubId } ?: return false
        val currentComps = _state.value.activeCompetitions
        if (currentComps.size >= MAX_COMPETITIONS) return false
        if (currentComps.any { it.competitionId == competition.id && it.clubId == clubId }) return false

        val entry = CompetitionEntry(
            id = System.currentTimeMillis(),
            competitionId = competition.id,
            competitionName = competition.name,
            clubId = clubId,
            clubName = club.name,
            clubType = club.type,
            level = competition.level,
            field = competition.field,
            status = CompetitionStatus.REGISTERED,
            registrationFee = competition.registrationFee,
            remainingRounds = competition.totalRounds,
            currentRound = 0,
            score = 0f
        )
        _state.update { it.copy(activeCompetitions = it.activeCompetitions + entry) }
        return true
    }

    /**
     * 获取当前可报名的竞赛列表
     */
    fun getAvailableCompetitions(schoolReputation: Long): List<CompetitionInfo> {
        val available = mutableListOf<CompetitionInfo>()
        CompetitionLevel.entries.forEach { level ->
            if (schoolReputation >= level.reputationRequired) {
                level.generateCompetitions().forEach { comp ->
                    if (_state.value.activeCompetitions.none { it.competitionId == comp.id }) {
                        available.add(comp)
                    }
                }
            }
        }
        return available
    }

    /**
     * 月度推进
     */
    fun advanceMonth(currentYear: Int, currentMonth: Int, schoolReputation: Long): ClubActivityMonthlyResult {
        var activityBudgetYuan = 0L
        var competitionRegistrationWan = 0L
        var totalReputationGain = 0L
        val newAwards = mutableListOf<Award>()
        val newEvents = mutableListOf<ActivityEvent>()

        // 推进策划活动
        val updatedActivities = _state.value.plannedActivities.mapNotNull { activity ->
            when (activity.status) {
                ActivityStatus.PLANNING -> {
                    val remaining = activity.remainingPrepMonths - 1
                    if (remaining <= 0) {
                        // 开始执行
                        val executing = activity.copy(
                            status = ActivityStatus.IN_PROGRESS,
                            remainingPrepMonths = 0
                        )
                        newEvents.add(ActivityEvent(
                            title = "${activity.clubName}: ${activity.name}开始",
                            description = "活动正式开始执行",
                            type = ActivityEventType.ACTIVITY_START,
                            month = currentMonth, year = currentYear
                        ))
                        executing
                    } else {
                        activity.copy(remainingPrepMonths = remaining)
                    }
                }
                ActivityStatus.IN_PROGRESS -> {
                    // 执行完成 → 结算
                    activityBudgetYuan += activity.budgetAllocated
                    val quality = calculateActivityQuality(activity)
                    val repGain = (quality * activity.activityType.reputationMultiplier).toLong()
                    totalReputationGain += repGain

                    if (quality >= 80f) {
                        val award = Award(
                            id = System.currentTimeMillis() + Random.nextLong(1000),
                            title = "${activity.name}圆满成功",
                            description = "活动质量评分${quality.toInt()}分，获得好评",
                            clubName = activity.clubName,
                            type = AwardType.ACTIVITY_EXCELLENCE,
                            reputationGained = repGain,
                            year = currentYear, month = currentMonth
                        )
                        newAwards.add(award)
                    }

                    newEvents.add(ActivityEvent(
                        title = "${activity.clubName}: ${activity.name}结束",
                        description = "质量评分${quality.toInt()}分，声誉+$repGain",
                        type = ActivityEventType.ACTIVITY_COMPLETE,
                        month = currentMonth, year = currentYear
                    ))
                    null // 移除已完成活动
                }
                else -> activity
            }
        }

        // 推进竞赛
        val updatedCompetitions = _state.value.activeCompetitions.mapNotNull { entry ->
            when (entry.status) {
                CompetitionStatus.REGISTERED -> {
                    // 进入第一轮
                    entry.copy(status = CompetitionStatus.IN_PROGRESS, currentRound = 1)
                }
                CompetitionStatus.IN_PROGRESS -> {
                    val club = clubManager.clubs.value.find { it.id == entry.clubId }
                    val roundScore = calculateRoundScore(entry, club?.enthusiasm ?: 50f, club?.level?.ordinal ?: 0)
                    val newScore = entry.score + roundScore
                    val newRound = entry.currentRound + 1

                    if (newRound > entry.remainingRounds) {
                        // 竞赛结束 → 结算
                        val placement = determinePlacement(newScore, entry.level)
                        val repGain = placement.reputationReward * (entry.level.ordinal + 1)
                        totalReputationGain += repGain
                        competitionRegistrationWan += entry.registrationFee

                        if (placement != CompetitionPlacement.ELIMINATED) {
                            val award = Award(
                                id = System.currentTimeMillis() + Random.nextLong(1000),
                                title = "${entry.competitionName} - ${placement.displayName}",
                                description = "${entry.clubName}在${entry.level.displayName}中获${placement.displayName}",
                                clubName = entry.clubName,
                                type = when (placement) {
                                    CompetitionPlacement.GOLD -> AwardType.COMPETITION_GOLD
                                    CompetitionPlacement.SILVER -> AwardType.COMPETITION_SILVER
                                    CompetitionPlacement.BRONZE -> AwardType.COMPETITION_BRONZE
                                    else -> AwardType.COMPETITION_PARTICIPATION
                                },
                                reputationGained = repGain,
                                year = currentYear, month = currentMonth
                            )
                            newAwards.add(award)
                        }

                        newEvents.add(ActivityEvent(
                            title = "${entry.competitionName}结果",
                            description = "${entry.clubName}获得${placement.displayName}，声誉+$repGain",
                            type = ActivityEventType.COMPETITION_RESULT,
                            month = currentMonth, year = currentYear
                        ))
                        null // 移除已完成
                    } else {
                        entry.copy(score = newScore, currentRound = newRound)
                    }
                }
                else -> entry
            }
        }

        // 年度社团评比（每年12月）
        if (currentMonth == 12) {
            val annualAwards = conductAnnualReview(currentYear)
            newAwards.addAll(annualAwards)
            totalReputationGain += annualAwards.sumOf { it.reputationGained }
            newEvents.add(ActivityEvent(
                title = "年度社团评比",
                description = "评选出${annualAwards.size}个优秀社团",
                type = ActivityEventType.ANNUAL_REVIEW,
                month = currentMonth, year = currentYear
            ))
        }

        // 随机竞赛邀请（基于声誉）
        if (Random.nextFloat() < 0.3f && schoolReputation > 50) {
            val invite = generateCompetitionInvite(schoolReputation)
            if (invite != null) {
                newEvents.add(ActivityEvent(
                    title = "竞赛邀请: ${invite.name}",
                    description = "${invite.level.displayName}，报名费${invite.registrationFee}万",
                    type = ActivityEventType.COMPETITION_INVITE,
                    month = currentMonth, year = currentYear
                ))
            }
        }

        _state.update { state ->
            state.copy(
                plannedActivities = updatedActivities,
                activeCompetitions = updatedCompetitions,
                awardsHistory = (newAwards + state.awardsHistory).take(MAX_AWARDS_HISTORY),
                recentEvents = (newEvents + state.recentEvents).take(MAX_EVENTS_LOG),
                totalAwardsCount = state.totalAwardsCount + newAwards.size,
                totalReputationFromActivities = state.totalReputationFromActivities + totalReputationGain,
                monthlyBudgetSpent = activityBudgetYuan + competitionRegistrationWan * 10000L
            )
        }

        return ClubActivityMonthlyResult(
            activityBudgetYuan = activityBudgetYuan,
            competitionRegistrationWan = competitionRegistrationWan.toDouble(),
            reputationGain = totalReputationGain,
            newAwards = newAwards,
            events = newEvents
        )
    }

    private fun calculateActivityQuality(activity: PlannedActivity): Float {
        val budgetRatio = (activity.budgetAllocated.toFloat() / activity.budgetRequired.coerceAtLeast(1))
            .coerceIn(0.5f, 2.0f)
        val club = clubManager.clubs.value.find { it.id == activity.clubId }
        val enthusiasm = club?.enthusiasm ?: 50f
        val levelBonus = (club?.level?.ordinal ?: 0) * 5f

        val base = 50f + enthusiasm * 0.2f + levelBonus
        val adjusted = base * budgetRatio
        return adjusted.coerceIn(20f, 100f) + Random.nextFloat() * 10f - 5f
    }

    private fun calculateRoundScore(entry: CompetitionEntry, enthusiasm: Float, clubLevel: Int): Float {
        val base = 40f + enthusiasm * 0.3f + clubLevel * 8f
        val levelDifficulty = entry.level.ordinal * 5f
        return (base - levelDifficulty + Random.nextFloat() * 20f - 10f).coerceAtLeast(10f)
    }

    private fun determinePlacement(totalScore: Float, level: CompetitionLevel): CompetitionPlacement {
        val threshold = 60f + level.ordinal * 10f
        return when {
            totalScore >= threshold * 1.5f -> CompetitionPlacement.GOLD
            totalScore >= threshold * 1.2f -> CompetitionPlacement.SILVER
            totalScore >= threshold -> CompetitionPlacement.BRONZE
            totalScore >= threshold * 0.7f -> CompetitionPlacement.PARTICIPATION
            else -> CompetitionPlacement.ELIMINATED
        }
    }

    private fun conductAnnualReview(year: Int): List<Award> {
        val clubs = clubManager.clubs.value
        if (clubs.isEmpty()) return emptyList()

        val awards = mutableListOf<Award>()

        // 最佳社团
        val bestClub = clubs.maxByOrNull { it.enthusiasm * it.level.multiplier + it.trophyCount * 10 }
        if (bestClub != null) {
            awards.add(Award(
                id = System.currentTimeMillis(),
                title = "年度最佳社团",
                description = "${bestClub.name}被评为${year}年度最佳社团",
                clubName = bestClub.name,
                type = AwardType.ANNUAL_BEST,
                reputationGained = 15L,
                year = year, month = 12
            ))
        }

        // 最活跃社团
        val mostActive = clubs.maxByOrNull { it.monthsActive * it.memberCount }
        if (mostActive != null && mostActive.id != bestClub?.id) {
            awards.add(Award(
                id = System.currentTimeMillis() + 1,
                title = "最活跃社团",
                description = "${mostActive.name}全年活动最为积极",
                clubName = mostActive.name,
                type = AwardType.ANNUAL_ACTIVE,
                reputationGained = 10L,
                year = year, month = 12
            ))
        }

        // 最多获奖
        val topTrophy = clubs.maxByOrNull { it.trophyCount }
        if (topTrophy != null && topTrophy.trophyCount > 0 && topTrophy.id != bestClub?.id) {
            awards.add(Award(
                id = System.currentTimeMillis() + 2,
                title = "最多荣誉社团",
                description = "${topTrophy.name}全年获得${topTrophy.trophyCount}项荣誉",
                clubName = topTrophy.name,
                type = AwardType.ANNUAL_TROPHY,
                reputationGained = 12L,
                year = year, month = 12
            ))
        }

        return awards
    }

    private fun generateCompetitionInvite(reputation: Long): CompetitionInfo? {
        val maxLevel = when {
            reputation >= 500 -> CompetitionLevel.NATIONAL
            reputation >= 300 -> CompetitionLevel.PROVINCIAL
            reputation >= 150 -> CompetitionLevel.CITY
            reputation >= 50 -> CompetitionLevel.DISTRICT
            else -> return null
        }
        val level = CompetitionLevel.entries.filter { it.ordinal <= maxLevel.ordinal }.random()
        return level.generateCompetitions().randomOrNull()
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = ClubActivityPersistData(
                plannedActivities = state.plannedActivities.map { a ->
                    PlannedActivityPersist(
                        id = a.id,
                        clubId = a.clubId,
                        clubName = a.clubName,
                        clubType = a.clubType.name,
                        activityType = a.activityType.name,
                        name = a.name,
                        status = a.status.name,
                        budgetAllocated = a.budgetAllocated,
                        budgetRequired = a.budgetRequired,
                        preparationMonths = a.preparationMonths,
                        remainingPrepMonths = a.remainingPrepMonths,
                        expectedParticipants = a.expectedParticipants,
                        qualityScore = a.qualityScore,
                        createdMonth = a.createdMonth
                    )
                },
                activeCompetitions = state.activeCompetitions.map { c ->
                    CompetitionEntryPersist(
                        id = c.id,
                        competitionId = c.competitionId,
                        competitionName = c.competitionName,
                        clubId = c.clubId,
                        clubName = c.clubName,
                        clubType = c.clubType.name,
                        level = c.level.name,
                        field = c.field.name,
                        status = c.status.name,
                        registrationFee = c.registrationFee,
                        remainingRounds = c.remainingRounds,
                        currentRound = c.currentRound,
                        score = c.score
                    )
                },
                awardsHistory = state.awardsHistory.map { aw ->
                    AwardPersist(
                        id = aw.id,
                        title = aw.title,
                        description = aw.description,
                        clubName = aw.clubName,
                        type = aw.type.name,
                        reputationGained = aw.reputationGained,
                        year = aw.year,
                        month = aw.month
                    )
                },
                totalAwardsCount = state.totalAwardsCount,
                totalReputationFromActivities = state.totalReputationFromActivities
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<ClubActivityPersistData>(json)
            val activities = data.plannedActivities.mapNotNull { ap ->
                val clubType = try { ClubType.valueOf(ap.clubType) } catch (_: Exception) { return@mapNotNull null }
                val actType = try { ActivityType.valueOf(ap.activityType) } catch (_: Exception) { return@mapNotNull null }
                val status = try { ActivityStatus.valueOf(ap.status) } catch (_: Exception) { ActivityStatus.PLANNING }
                PlannedActivity(
                    id = ap.id,
                    clubId = ap.clubId,
                    clubName = ap.clubName,
                    clubType = clubType,
                    activityType = actType,
                    name = ap.name,
                    status = status,
                    budgetAllocated = ap.budgetAllocated,
                    budgetRequired = ap.budgetRequired,
                    preparationMonths = ap.preparationMonths,
                    remainingPrepMonths = ap.remainingPrepMonths,
                    expectedParticipants = ap.expectedParticipants,
                    qualityScore = ap.qualityScore,
                    createdMonth = ap.createdMonth
                )
            }
            val competitions = data.activeCompetitions.mapNotNull { cp ->
                val clubType = try { ClubType.valueOf(cp.clubType) } catch (_: Exception) { return@mapNotNull null }
                val level = try { CompetitionLevel.valueOf(cp.level) } catch (_: Exception) { return@mapNotNull null }
                val field = try { CompetitionField.valueOf(cp.field) } catch (_: Exception) { CompetitionField.SCIENCE }
                val status = try { CompetitionStatus.valueOf(cp.status) } catch (_: Exception) { CompetitionStatus.REGISTERED }
                CompetitionEntry(
                    id = cp.id,
                    competitionId = cp.competitionId,
                    competitionName = cp.competitionName,
                    clubId = cp.clubId,
                    clubName = cp.clubName,
                    clubType = clubType,
                    level = level,
                    field = field,
                    status = status,
                    registrationFee = cp.registrationFee,
                    remainingRounds = cp.remainingRounds,
                    currentRound = cp.currentRound,
                    score = cp.score
                )
            }
            val awards = data.awardsHistory.mapNotNull { awp ->
                val type = try { AwardType.valueOf(awp.type) } catch (_: Exception) { return@mapNotNull null }
                Award(
                    id = awp.id,
                    title = awp.title,
                    description = awp.description,
                    clubName = awp.clubName,
                    type = type,
                    reputationGained = awp.reputationGained,
                    year = awp.year,
                    month = awp.month
                )
            }
            _state.value = ClubActivityState(
                plannedActivities = activities,
                activeCompetitions = competitions,
                awardsHistory = awards,
                recentEvents = emptyList(),
                totalAwardsCount = data.totalAwardsCount,
                totalReputationFromActivities = data.totalReputationFromActivities,
                monthlyBudgetSpent = 0L
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("ClubActivityManager.restoreFromJson failed", e)
        }
    }
}

// ===== 数据模型 =====

data class ClubActivityState(
    val plannedActivities: List<PlannedActivity> = emptyList(),
    val activeCompetitions: List<CompetitionEntry> = emptyList(),
    val awardsHistory: List<Award> = emptyList(),
    val recentEvents: List<ActivityEvent> = emptyList(),
    val totalAwardsCount: Int = 0,
    val totalReputationFromActivities: Long = 0L,
    val monthlyBudgetSpent: Long = 0L
)

data class PlannedActivity(
    val id: Long,
    val clubId: Long,
    val clubName: String,
    val clubType: ClubType,
    val activityType: ActivityType,
    val name: String,
    val status: ActivityStatus,
    val budgetAllocated: Long,
    val budgetRequired: Long,
    val preparationMonths: Int,
    val remainingPrepMonths: Int,
    val expectedParticipants: Int,
    val qualityScore: Float,
    val createdMonth: Int
)

enum class ActivityType(
    val displayName: String,
    val description: String,
    val baseCost: Long,
    val prepMonths: Int,
    val participantRatio: Float,
    val reputationMultiplier: Float
) {
    WORKSHOP("工作坊", "小型技能培训活动", 1L, 1, 0.5f, 0.3f),
    EXHIBITION("展览会", "成果展示活动", 1L, 2, 1.0f, 0.5f),
    PERFORMANCE("演出活动", "文艺汇演/音乐会", 1L, 2, 1.5f, 0.6f),
    OPEN_DAY("社团开放日", "对外招新展示", 1L, 1, 2.0f, 0.4f),
    CHARITY_EVENT("公益活动", "社区服务/慈善义演", 1L, 1, 1.2f, 0.7f),
    INTER_SCHOOL("校际交流", "与其他学校联合活动", 2L, 3, 0.8f, 0.8f),
    FESTIVAL("社团文化节", "大型综合性活动", 3L, 3, 3.0f, 1.0f),
    COMPETITION_HOST("承办竞赛", "承办区级/市级竞赛", 5L, 4, 2.0f, 1.5f)
}

enum class ActivityStatus(val displayName: String) {
    PLANNING("筹备中"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    CANCELLED("已取消")
}

data class CompetitionEntry(
    val id: Long,
    val competitionId: String,
    val competitionName: String,
    val clubId: Long,
    val clubName: String,
    val clubType: ClubType,
    val level: CompetitionLevel,
    val field: CompetitionField,
    val status: CompetitionStatus,
    val registrationFee: Long,
    val remainingRounds: Int,
    val currentRound: Int,
    val score: Float
)

enum class CompetitionLevel(
    val displayName: String,
    val reputationRequired: Long,
    val baseRegistrationFee: Long  // 单位：万元（与全系统财务单位一致）
) {
    // v2.8 修复：报名费按竞赛级别合理化（原来全是1-5万，区分度不够）
    // 实际参考：区级0.2万，市级0.5万，省级2万，国家级8万，国际级20万
    DISTRICT("区级", 30L, 1L),       // 区级比较便宜，1万含差旅
    CITY("市级", 80L, 3L),           // 市级3万（原1万太低）
    PROVINCIAL("省级", 200L, 8L),    // 省级8万（含培训+差旅+报名）
    NATIONAL("国家级", 400L, 20L),   // 国家级20万（大型比赛）
    INTERNATIONAL("国际级", 800L, 50L); // 国际级50万（含国际差旅）

    fun generateCompetitions(): List<CompetitionInfo> {
        return when (this) {
            DISTRICT -> listOf(
                CompetitionInfo("d_sci", "区科学创新赛", this, CompetitionField.SCIENCE, baseRegistrationFee, 2),
                CompetitionInfo("d_art", "区艺术展评", this, CompetitionField.ARTS, baseRegistrationFee, 2),
                CompetitionInfo("d_spo", "区中学生运动会", this, CompetitionField.SPORTS, baseRegistrationFee, 3)
            )
            CITY -> listOf(
                CompetitionInfo("c_deb", "市辩论邀请赛", this, CompetitionField.DEBATE, baseRegistrationFee, 3),
                CompetitionInfo("c_tec", "市编程马拉松", this, CompetitionField.TECHNOLOGY, baseRegistrationFee, 2),
                CompetitionInfo("c_mus", "市校园音乐节", this, CompetitionField.MUSIC, baseRegistrationFee, 2),
                CompetitionInfo("c_lit", "市作文大赛", this, CompetitionField.LITERATURE, baseRegistrationFee, 2)
            )
            PROVINCIAL -> listOf(
                CompetitionInfo("p_oli", "省学科奥林匹克", this, CompetitionField.SCIENCE, baseRegistrationFee, 4),
                CompetitionInfo("p_rob", "省机器人挑战赛", this, CompetitionField.TECHNOLOGY, baseRegistrationFee, 3),
                CompetitionInfo("p_dra", "省戏剧节", this, CompetitionField.DRAMA, baseRegistrationFee, 3)
            )
            NATIONAL -> listOf(
                CompetitionInfo("n_inn", "全国青少年创新大赛", this, CompetitionField.SCIENCE, baseRegistrationFee, 5),
                CompetitionInfo("n_art", "全国校园文化艺术节", this, CompetitionField.ARTS, baseRegistrationFee, 4),
                CompetitionInfo("n_spo", "全国中学生运动会", this, CompetitionField.SPORTS, baseRegistrationFee, 4)
            )
            INTERNATIONAL -> listOf(
                CompetitionInfo("i_imo", "国际数学奥赛选拔", this, CompetitionField.SCIENCE, baseRegistrationFee, 5),
                CompetitionInfo("i_rob", "国际机器人大赛", this, CompetitionField.TECHNOLOGY, baseRegistrationFee, 4)
            )
        }
    }
}

enum class CompetitionField(val displayName: String) {
    SCIENCE("理科"),
    TECHNOLOGY("科技"),
    ARTS("艺术"),
    MUSIC("音乐"),
    LITERATURE("文学"),
    DEBATE("辩论"),
    DRAMA("戏剧"),
    SPORTS("体育")
}

enum class CompetitionStatus(val displayName: String) {
    REGISTERED("已报名"),
    IN_PROGRESS("比赛中"),
    COMPLETED("已结束")
}

data class CompetitionInfo(
    val id: String,
    val name: String,
    val level: CompetitionLevel,
    val field: CompetitionField,
    val registrationFee: Long,
    val totalRounds: Int
)

enum class CompetitionPlacement(val displayName: String, val reputationReward: Long) {
    GOLD("金奖", 20L),
    SILVER("银奖", 12L),
    BRONZE("铜奖", 6L),
    PARTICIPATION("优秀参与", 2L),
    ELIMINATED("未入围", 0L)
}

data class Award(
    val id: Long,
    val title: String,
    val description: String,
    val clubName: String,
    val type: AwardType,
    val reputationGained: Long,
    val year: Int,
    val month: Int
)

enum class AwardType(val displayName: String, val icon: String) {
    COMPETITION_GOLD("金奖", "🥇"),
    COMPETITION_SILVER("银奖", "🥈"),
    COMPETITION_BRONZE("铜奖", "🥉"),
    COMPETITION_PARTICIPATION("优秀参与", "🏅"),
    ACTIVITY_EXCELLENCE("活动优秀", "⭐"),
    ANNUAL_BEST("年度最佳", "🏆"),
    ANNUAL_ACTIVE("最活跃", "🔥"),
    ANNUAL_TROPHY("最多荣誉", "👑")
}

data class ActivityEvent(
    val title: String,
    val description: String,
    val type: ActivityEventType,
    val month: Int,
    val year: Int
)

enum class ActivityEventType {
    ACTIVITY_START,
    ACTIVITY_COMPLETE,
    COMPETITION_RESULT,
    COMPETITION_INVITE,
    ANNUAL_REVIEW
}

data class ClubActivityMonthlyResult(
    val activityBudgetYuan: Long = 0L,
    val competitionRegistrationWan: Double = 0.0,
    val reputationGain: Long = 0L,
    val newAwards: List<Award> = emptyList(),
    val events: List<ActivityEvent> = emptyList()
) {
    val totalExpenseWan: Double
        get() = activityBudgetYuan.toDouble() / 10000.0 + competitionRegistrationWan
}

@Serializable
data class ClubActivityPersistData(
    val plannedActivities: List<PlannedActivityPersist> = emptyList(),
    val activeCompetitions: List<CompetitionEntryPersist> = emptyList(),
    val awardsHistory: List<AwardPersist> = emptyList(),
    val totalAwardsCount: Int = 0,
    val totalReputationFromActivities: Long = 0L
)

@Serializable
data class PlannedActivityPersist(
    val id: Long,
    val clubId: Long,
    val clubName: String,
    val clubType: String,
    val activityType: String,
    val name: String,
    val status: String,
    val budgetAllocated: Long,
    val budgetRequired: Long,
    val preparationMonths: Int,
    val remainingPrepMonths: Int,
    val expectedParticipants: Int,
    val qualityScore: Float,
    val createdMonth: Int
)

@Serializable
data class CompetitionEntryPersist(
    val id: Long,
    val competitionId: String,
    val competitionName: String,
    val clubId: Long,
    val clubName: String,
    val clubType: String,
    val level: String,
    val field: String,
    val status: String,
    val registrationFee: Long,
    val remainingRounds: Int,
    val currentRound: Int,
    val score: Float
)

@Serializable
data class AwardPersist(
    val id: Long,
    val title: String,
    val description: String,
    val clubName: String,
    val type: String,
    val reputationGained: Long,
    val year: Int,
    val month: Int
)
