package com.arktools.xiaozhang.domain.partner

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 企业/机构合作委托系统：外联的常驻决策来源。
 * - 每月刷新 2 条委托要约（按校园等级/声誉/学院加权挑选模板）
 * - 校长最多同时推进 2 项委托；接单即付启动资金，到期结算
 * - 结算成功率受师资均分、相关学院、相关设施影响
 * - 收益：现金 / 声誉 / 就业质量加成 / 招生加成 / 额外科研推进日
 */

@Serializable
enum class CommissionStatus { OFFERED, ACTIVE, COMPLETED, FAILED, EXPIRED }

@Serializable
enum class CommissionKind(val displayName: String) {
    FUNDING("经费赞助"),
    EMPLOYMENT("就业合作"),
    RESEARCH("横向课题"),
    ENROLLMENT("生源共建"),
    REPUTATION("社会公益")
}

@Serializable
data class PartnerCommission(
    val id: String,
    val kind: CommissionKind,
    val partner: String,
    val title: String,
    val description: String,
    val requiredReputation: Long = 0L,
    val requiredCollege: String? = null,
    val requiredFacility: String? = null,
    val upfrontCostWan: Double = 0.0,
    val durationMonths: Int = 2,
    val monthlyCashWan: Double = 0.0,
    val completionCashWan: Double = 0.0,
    val completionReputation: Long = 0L,
    val employmentBoost: Float = 0f,
    val enrollmentBonus: Float = 0f,
    val researchDaysBonus: Int = 0,
    var status: CommissionStatus = CommissionStatus.OFFERED,
    var remainingMonths: Int = 0
) {
    val isRewardEmployment: Boolean get() = employmentBoost > 0f
}

data class PartnerMonthlyResult(
    val upfrontPaid: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val completions: List<PartnerCommission> = emptyList(),
    val failures: List<PartnerCommission> = emptyList(),
    val newOfferCount: Int = 0,
    val expiredOfferCount: Int = 0
)

data class CompletionContext(
    val avgFacultySkill: Double,
    val foundedColleges: Set<String>,
    val hasOperationalFacility: (String) -> Boolean
)

@Serializable
data class PartnerPersistData(
    val offers: List<PartnerCommission> = emptyList(),
    val active: List<PartnerCommission> = emptyList(),
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val nextId: Int = 1,
    val lastOfferYear: Int = 0,
    val lastOfferMonth: Int = 0,
    val lastProcessedYear: Int = 0,
    val lastProcessedMonth: Int = 0
)

@Singleton
class PartnerCommissionManager @Inject constructor() {

    @Serializable
    data class ManagerState(
        val offers: List<PartnerCommission> = emptyList(),
        val active: List<PartnerCommission> = emptyList(),
        val completedCount: Int = 0,
        val failedCount: Int = 0,
        val nextId: Int = 1,
        val lastOfferYear: Int = 0,
        val lastOfferMonth: Int = 0,
        val pendingEmploymentBoost: Float = 0f,
        val pendingEnrollmentBonus: Float = 0f,
        val lastProcessedYear: Int = 0,
        val lastProcessedMonth: Int = 0
    )

    private val _state = MutableStateFlow(ManagerState())
    val state: StateFlow<ManagerState> = _state.asStateFlow()

    fun reset() {
        _state.value = ManagerState()
    }

    // ===== 委托模板库 =====
    // 每条模板声明档位要求；monthlyOffers 按当前条件过滤后随机抽取
    private data class Template(
        val kind: CommissionKind,
        val partner: String,
        val title: String,
        val description: String,
        val requiredReputation: Long = 0L,
        val requiredCollege: String? = null,
        val requiredFacility: String? = null,
        val minCampusLevel: Int = 1,
        val upfrontCostWan: Double,
        val durationMonths: Int,
        val monthlyCashWan: Double,
        val completionCashWan: Double,
        val completionReputation: Long,
        val employmentBoost: Float = 0f,
        val enrollmentBonus: Float = 0f,
        val researchDaysBonus: Int = 0
    )

    private val templates = listOf(
        Template(
            CommissionKind.FUNDING, "宏远食品集团", "食堂冠名与食材直供协议",
            "集团冠名第一食堂并直供食材，要求 upkeep 达标；完成获三年期赞助与一次性捐赠。",
            requiredFacility = "CANTEEN", minCampusLevel = 1,
            upfrontCostWan = 8.0, durationMonths = 2, monthlyCashWan = 6.0,
            completionCashWan = 30.0, completionReputation = 6L
        ),
        Template(
            CommissionKind.FUNDING, "城西建设集团", "教学楼命名的捐赠谈判",
            "建设集团拟捐资命名一栋教学楼，需先完成礼仪接待与方案评审。",
            minCampusLevel = 2, upfrontCostWan = 5.0, durationMonths = 2,
            monthlyCashWan = 0.0, completionCashWan = 60.0, completionReputation = 10L
        ),
        Template(
            CommissionKind.EMPLOYMENT, "启明星软件园", "订单班共建与实习输送",
            "园区企业组团开设订单班，需工学院支撑；完成大幅提升毕业去向质量。",
            requiredCollege = "ENGINEERING", minCampusLevel = 2,
            upfrontCostWan = 20.0, durationMonths = 3, monthlyCashWan = 4.0,
            completionCashWan = 15.0, completionReputation = 12L, employmentBoost = 0.12f
        ),
        Template(
            CommissionKind.EMPLOYMENT, "康宁医疗集团", "医护人才定向培养",
            "医疗集团定向培养护理与康复人才，要求医学院已竣工。",
            requiredCollege = "MEDICINE", minCampusLevel = 3,
            upfrontCostWan = 30.0, durationMonths = 3, monthlyCashWan = 6.0,
            completionCashWan = 20.0, completionReputation = 14L, employmentBoost = 0.15f
        ),
        Template(
            CommissionKind.RESEARCH, "省属产业技术研究院", "横向课题：产线降耗优化",
            "企业出题、学校解题。理学院师生联合攻关，结题奖励科研经费与推进日。",
            requiredCollege = "SCIENCE", minCampusLevel = 2,
            upfrontCostWan = 15.0, durationMonths = 2, monthlyCashWan = 3.0,
            completionCashWan = 40.0, completionReputation = 10L, researchDaysBonus = 3
        ),
        Template(
            CommissionKind.RESEARCH, "数联云科技", "大数据联合实验室首期项目",
            "共建联合实验室承接数据分析项目，需机房投入运营。",
            requiredFacility = "COMPUTER_LAB", minCampusLevel = 2,
            upfrontCostWan = 18.0, durationMonths = 2, monthlyCashWan = 5.0,
            completionCashWan = 35.0, completionReputation = 9L, researchDaysBonus = 2
        ),
        Template(
            CommissionKind.ENROLLMENT, "市教育局", "社区职业教育公益培训",
            "承接社区公益培训项目，换取教育局对本校招生的宣传倾斜。",
            minCampusLevel = 1,
            upfrontCostWan = 6.0, durationMonths = 2, monthlyCashWan = 2.0,
            completionCashWan = 10.0, completionReputation = 8L, enrollmentBonus = 0.06f
        ),
        Template(
            CommissionKind.ENROLLMENT, "新航道教育集团", "县域生源基地共建",
            "与三所县域中学共建生源基地，需商学院团队做升学规划。",
            requiredCollege = "BUSINESS", requiredReputation = 150L, minCampusLevel = 2,
            upfrontCostWan = 12.0, durationMonths = 2, monthlyCashWan = 3.0,
            completionCashWan = 12.0, completionReputation = 10L, enrollmentBonus = 0.08f
        ),
        Template(
            CommissionKind.REPUTATION, "市文化馆", "校地文化艺术季联办",
            "联办公益文化艺术季，艺术工作室排练演出，显著提升社会口碑。",
            requiredFacility = "ART_STUDIO", minCampusLevel = 2,
            upfrontCostWan = 10.0, durationMonths = 1, monthlyCashWan = 0.0,
            completionCashWan = 8.0, completionReputation = 18L
        ),
        Template(
            CommissionKind.REPUTATION, "市体育局", "全民健身体测进校园",
            "承接全市体质测评志愿服务，需运动场支撑，赢在城市形象曝光。",
            requiredFacility = "SPORTS_FIELD", minCampusLevel = 2,
            upfrontCostWan = 8.0, durationMonths = 1, monthlyCashWan = 0.0,
            completionCashWan = 10.0, completionReputation = 15L
        )
    )

    // ===== 查询 =====

    fun canAccept(
        commission: PartnerCommission,
        reputation: Long,
        foundedColleges: Set<String>,
        hasOperationalFacility: (String) -> Boolean
    ): String? {
        if (commission.requiredReputation > 0 && reputation < commission.requiredReputation) {
            return "声誉不足（需 ${commission.requiredReputation}）"
        }
        val college = commission.requiredCollege
        if (college != null && college !in foundedColleges) {
            return "需要相关学院竣工"
        }
        val facility = commission.requiredFacility
        if (facility != null && !hasOperationalFacility(facility)) {
            return "需要相关设施投入运营"
        }
        return null
    }

    // ===== 变更 =====

    /** 每月刷新要约：过滤可接模板后随机抽 2 条；未接走的旧要约过期。 */
    fun refreshOffers(
        year: Int,
        month: Int,
        campusLevel: Int,
        foundedColleges: Set<String>
    ) {
        val cur = _state.value
        if (cur.lastOfferYear == year && cur.lastOfferMonth == month) return
        val rng = kotlin.random.Random(year * 100 + month)
        val eligible = templates.filter { t ->
            campusLevel >= t.minCampusLevel &&
                (t.requiredCollege == null || t.requiredCollege in foundedColleges)
        }
        val picked = eligible.shuffled(rng).take(2).mapIndexed { idx, t ->
            PartnerCommission(
                id = "c${cur.nextId + idx}",
                kind = t.kind,
                partner = t.partner,
                title = t.title,
                description = t.description,
                requiredReputation = t.requiredReputation,
                requiredCollege = t.requiredCollege,
                requiredFacility = t.requiredFacility,
                upfrontCostWan = t.upfrontCostWan,
                durationMonths = t.durationMonths,
                monthlyCashWan = t.monthlyCashWan,
                completionCashWan = t.completionCashWan,
                completionReputation = t.completionReputation,
                employmentBoost = t.employmentBoost,
                enrollmentBonus = t.enrollmentBonus,
                researchDaysBonus = t.researchDaysBonus
            )
        }
        _state.value = cur.copy(
            offers = picked,
            nextId = cur.nextId + picked.size,
            lastOfferYear = year,
            lastOfferMonth = month
        )
    }

    fun accept(id: String, year: Int, month: Int): PartnerCommission? {
        val cur = _state.value
        if (cur.active.size >= MAX_ACTIVE) return null
        val offer = cur.offers.firstOrNull { it.id == id && it.status == CommissionStatus.OFFERED }
            ?: return null
        val activated = offer.copy(
            status = CommissionStatus.ACTIVE,
            remainingMonths = offer.durationMonths
        )
        _state.value = cur.copy(
            offers = cur.offers - offer,
            active = cur.active + activated
        )
        return activated
    }

    fun decline(id: String): Boolean {
        val cur = _state.value
        val offer = cur.offers.firstOrNull { it.id == id } ?: return false
        _state.value = cur.copy(offers = cur.offers - offer)
        return true
    }

    /**
     * 月度推进：在执行用收取月度分成；到期按条件掷成功率结算。
     * 声誉/现金等由引擎在同一学校事务中应用，管理器只负责状态推进与结果输出。
     */
    fun advanceMonth(context: CompletionContext, year: Int, month: Int): PartnerMonthlyResult {
        val cur = _state.value
        if (cur.lastProcessedYear == year && cur.lastProcessedMonth == month) {
            return PartnerMonthlyResult()
        }
        var monthlyIncome = 0.0
        val completions = mutableListOf<PartnerCommission>()
        val failures = mutableListOf<PartnerCommission>()
        val stillActive = mutableListOf<PartnerCommission>()

        cur.active.forEach { commission ->
            val remaining = commission.remainingMonths - 1
            monthlyIncome += commission.monthlyCashWan
            if (remaining > 0) {
                stillActive += commission.copy(remainingMonths = remaining)
                return@forEach
            }
            // 到期结算：基础 55% + 师资加成 + 学院支撑加成
            var chance = 0.55f
            chance += (context.avgFacultySkill.coerceIn(0.0, 100.0) / 100.0).toFloat() * 0.25f
            val college = commission.requiredCollege
            if (college != null && college in context.foundedColleges) chance += 0.12f
            val facility = commission.requiredFacility
            if (facility != null && context.hasOperationalFacility(facility)) chance += 0.12f
            val success = kotlin.random.Random.nextDouble() < chance.coerceAtMost(0.95f)
            if (success) {
                completions += commission.copy(status = CommissionStatus.COMPLETED)
            } else {
                failures += commission.copy(status = CommissionStatus.FAILED)
            }
        }

        var pendingEmploy = cur.pendingEmploymentBoost
        var pendingEnroll = cur.pendingEnrollmentBonus
        completions.forEach {
            pendingEmploy += it.employmentBoost
            pendingEnroll += it.enrollmentBonus
        }

        _state.value = cur.copy(
            active = stillActive,
            completedCount = cur.completedCount + completions.size,
            failedCount = cur.failedCount + failures.size,
            pendingEmploymentBoost = pendingEmploy,
            pendingEnrollmentBonus = pendingEnroll,
            lastProcessedYear = year,
            lastProcessedMonth = month
        )
        return PartnerMonthlyResult(
            monthlyIncome = monthlyIncome,
            completions = completions,
            failures = failures
        )
    }

    /** 消耗挂起的就业质量加成（就业月结时读取一次）。 */
    fun consumeEmploymentBoost(): Float {
        val v = _state.value.pendingEmploymentBoost
        if (v != 0f) {
            _state.value = _state.value.copy(pendingEmploymentBoost = 0f)
        }
        return v
    }

    /** 消耗挂起的招生加成（9月招生时读取一次）。 */
    fun consumeEnrollmentBonus(): Float {
        val v = _state.value.pendingEnrollmentBonus
        if (v != 0f) {
            _state.value = _state.value.copy(pendingEnrollmentBonus = 0f)
        }
        return v
    }

    fun toJson(): String = try {
        val s = _state.value
        Json.encodeToString(
            PartnerPersistData(
                offers = s.offers,
                active = s.active,
                completedCount = s.completedCount,
                failedCount = s.failedCount,
                nextId = s.nextId,
                lastOfferYear = s.lastOfferYear,
                lastOfferMonth = s.lastOfferMonth,
                lastProcessedYear = s.lastProcessedYear,
                lastProcessedMonth = s.lastProcessedMonth
            )
        )
    } catch (_: Exception) { "" }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }
                .decodeFromString<PartnerPersistData>(json)
            _state.value = ManagerState(
                offers = data.offers,
                active = data.active,
                completedCount = data.completedCount,
                failedCount = data.failedCount,
                nextId = data.nextId,
                lastOfferYear = data.lastOfferYear,
                lastOfferMonth = data.lastOfferMonth,
                lastProcessedYear = data.lastProcessedYear,
                lastProcessedMonth = data.lastProcessedMonth
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("PartnerCommissionManager.restoreFromJson failed", e)
        }
    }

    companion object {
        const val MAX_ACTIVE = 2
    }
}
