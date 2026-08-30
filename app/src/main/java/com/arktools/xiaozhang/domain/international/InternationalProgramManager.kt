package com.arktools.xiaozhang.domain.international

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 国际交流（Lv5 解锁，冲击 Lv6 的必要条件）：
 * - 与海外院校签订合作协议：花钱+声誉门槛，换来留学生名额与年度声誉
 * - 留学生学费高昂（每人每月 0.9 万），4 年毕业授学位 +80 声誉
 * - 交换生外派 1 年归来 +25 声誉
 * - 状态内嵌 policyJson 持久化（internationalStateJson）
 */
@Serializable
data class PartnerDef(
    val id: String,
    val name: String,
    val country: String,
    val tier: String,          // A / B / C
    val feeWan: Double,
    val repRequired: Long,
    val annualReputation: Long,
    val intlQuota: Int
)

@Serializable
data class IntlStudent(
    val id: String,
    val name: String,
    val country: String,
    val yearsLeft: Double
)

@Serializable
data class OutgoingStudent(
    val id: String,
    val name: String,
    val partnerId: String,
    val yearsLeft: Double
)

@Singleton
class InternationalProgramManager @Inject constructor() {

    @Serializable
    data class ManagerState(
        val signedPartnerIds: List<String> = emptyList(),
        val intlStudents: List<IntlStudent> = emptyList(),
        val outgoing: List<OutgoingStudent> = emptyList(),
        val totalIntlGraduated: Int = 0,
        val lastIntakeYear: Int = 0
    )

    private val _state = MutableStateFlow(ManagerState())
    val state: StateFlow<ManagerState> = _state.asStateFlow()

    companion object {
        val CATALOG: List<PartnerDef> = listOf(
            PartnerDef("P_EAST", "东海岸理工学院", "美国", "A", 300.0, 9000L, 160L, 6),
            PartnerDef("P_RHINE", "莱茵应用科学大学", "德国", "A", 260.0, 8000L, 140L, 6),
            PartnerDef("P_SAKURA", "樱丘大学", "日本", "B", 200.0, 6500L, 90L, 4),
            PartnerDef("P_LION", "狮城国立大学", "新加坡", "B", 220.0, 7000L, 100L, 4),
            PartnerDef("P_MAPLE", "枫叶理工大学", "加拿大", "B", 210.0, 6500L, 90L, 4),
            PartnerDef("P_HANNOVER", "汉诺威工业大学", "德国", "C", 150.0, 5000L, 55L, 2),
            PartnerDef("P_SOUTHERN", "全球南方联合大学", "多元", "C", 120.0, 4500L, 45L, 2),
            PartnerDef("P_POLARIS", "北极星艺术学堂", "芬兰", "C", 130.0, 4800L, 50L, 2)
        )

        fun byId(id: String): PartnerDef? = CATALOG.firstOrNull { it.id == id }

        private val FOREIGN_NAMES = listOf(
            "Michael", "Emma", "Daniel", "Sophia", "Lucas", "Olivia",
            "Kenji", "Yuki", "Aiko", "Minho", "Jiwoo", "Hana",
            "Lukas", "Mia", "Jonas", "Elena", "Mateo", "Isabella",
            "Arjun", "Priya", "Ahmed", "Layla", "Rafael", "Camila"
        )

        fun randomForeignName(rng: kotlin.random.Random): String =
            FOREIGN_NAMES[rng.nextInt(FOREIGN_NAMES.size)]
    }

    val hasAnyPartner: Boolean get() = _state.value.signedPartnerIds.isNotEmpty()

    fun intlQuota(): Int =
        _state.value.signedPartnerIds.sumOf { byId(it)?.intlQuota ?: 0 }

    fun annualReputation(): Long =
        _state.value.signedPartnerIds.sumOf { byId(it)?.annualReputation ?: 0L }

    /** 留学生学费：每人每月 0.9 万 */
    fun monthlyIncomeWan(): Double = _state.value.intlStudents.size * 0.9

    fun signPartner(id: String): Boolean {
        val st = _state.value
        if (id in st.signedPartnerIds) return false
        _state.value = st.copy(signedPartnerIds = st.signedPartnerIds + id)
        return true
    }

    /** 每月推进；返回（毕业留学生数, 归国交换生数） */
    fun advanceMonth(): Pair<Int, Int> {
        val st = _state.value
        var intlGrad = 0
        var outBack = 0
        val keptIntl = st.intlStudents.mapNotNull { s ->
            val left = s.yearsLeft - 1.0 / 12.0
            if (left <= 0) {
                intlGrad++
                null
            } else {
                s.copy(yearsLeft = left)
            }
        }
        val keptOut = st.outgoing.mapNotNull { s ->
            val left = s.yearsLeft - 1.0 / 12.0
            if (left <= 0) {
                outBack++
                null
            } else {
                s.copy(yearsLeft = left)
            }
        }
        _state.value = st.copy(
            intlStudents = keptIntl,
            outgoing = keptOut,
            totalIntlGraduated = st.totalIntlGraduated + intlGrad
        )
        return intlGrad to outBack
    }

    fun intakeIntl(list: List<IntlStudent>, year: Int) {
        if (list.isEmpty()) return
        val st = _state.value
        _state.value = st.copy(intlStudents = st.intlStudents + list, lastIntakeYear = year)
    }

    fun dispatchOutgoing(partnerId: String, names: List<String>) {
        val st = _state.value
        val fresh = names.map {
            OutgoingStudent(
                id = java.util.UUID.randomUUID().toString(),
                name = it,
                partnerId = partnerId,
                yearsLeft = 1.0
            )
        }
        _state.value = st.copy(outgoing = st.outgoing + fresh)
    }

    fun toJson(): String = runCatching { Json.encodeToString(_state.value) }.getOrDefault("")

    fun restoreFromJson(raw: String) {
        if (raw.isBlank()) return
        runCatching {
            _state.value = Json { ignoreUnknownKeys = true }.decodeFromString<ManagerState>(raw)
        }
    }

    fun reset() {
        _state.value = ManagerState()
    }
}
