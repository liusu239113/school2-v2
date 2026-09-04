package com.arktools.xiao.domain.scholarship

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

// ==================== 数据模型 ====================

enum class ScholarshipTier(val displayName: String, val color: Long) {
    FULL("全额奖学金", 0xFFFF8F00),
    HALF("半额奖学金", 0xFF1565C0),
    PARTIAL("部分奖学金", 0xFF388E3C),
    HONORARY("荣誉奖学金", 0xFF7B1FA2)
}

enum class ScholarshipCriteria(val displayName: String) {
    ACADEMIC_EXCELLENCE("学业优秀"),
    SPORTS_TALENT("体育特长"),
    ART_TALENT("艺术特长"),
    LEADERSHIP("领导力"),
    COMMUNITY_SERVICE("社会服务"),
    INNOVATION("创新创业"),
    FINANCIAL_NEED("助学金"),
    ALL_ROUND("全面发展")
}

data class Scholarship(
    val id: String = "SCH-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
    val name: String,
    val tier: ScholarshipTier,
    val criteria: ScholarshipCriteria,
    val amountPerStudent: Double,
    val maxRecipients: Int,
    var currentRecipients: Int = 0,
    val isActive: Boolean = true,
    val createdYear: Int,
    val description: String
)

data class ScholarshipRecipient(
    val studentName: String,
    val scholarshipId: String,
    val scholarshipName: String,
    val amount: Double,
    val year: Int,
    val month: Int,
    val gpa: Float
)

data class ScholarshipState(
    val scholarships: List<Scholarship> = emptyList(),
    val recipients: List<ScholarshipRecipient> = emptyList(),
    val totalBudgetAllocated: Double = 0.0,
    val totalAwarded: Double = 0.0,
    val studentAttractionBonus: Float = 0f, // 招生加成
    val retentionBonus: Float = 0f, // 留存加成（降低退学率）
    val reputationBonus: Int = 0,
    val recentEvents: List<String> = emptyList(),
    val yearlyStats: YearlyScholarshipStats = YearlyScholarshipStats()
)

data class YearlyScholarshipStats(
    val totalRecipients: Int = 0,
    val totalAmount: Double = 0.0,
    val avgGpa: Float = 0f,
    val topStudentName: String = ""
)

data class ScholarshipMonthResult(
    val expenses: Double = 0.0,
    val newRecipients: Int = 0,
    val retentionEffect: Float = 0f,
    val enrollmentBonus: Float = 0f,
    val reputationGain: Int = 0
)

// ==================== 预设奖学金模板 ====================

object ScholarshipTemplates {
    fun getTemplates(year: Int): List<Scholarship> = listOf(
        Scholarship(
            name = "校长特等奖学金",
            tier = ScholarshipTier.FULL,
            criteria = ScholarshipCriteria.ALL_ROUND,
            amountPerStudent = 1.0,
            maxRecipients = 3,
            createdYear = year,
            description = "授予全面发展的顶尖学生，全额免除学费"
        ),
        Scholarship(
            name = "学业精英奖",
            tier = ScholarshipTier.HALF,
            criteria = ScholarshipCriteria.ACADEMIC_EXCELLENCE,
            amountPerStudent = 0.5,
            maxRecipients = 10,
            createdYear = year,
            description = "奖励GPA排名前10%的优秀学生"
        ),
        Scholarship(
            name = "体育之星奖学金",
            tier = ScholarshipTier.PARTIAL,
            criteria = ScholarshipCriteria.SPORTS_TALENT,
            amountPerStudent = 0.3,
            maxRecipients = 8,
            createdYear = year,
            description = "鼓励体育竞赛中表现突出的学生"
        ),
        Scholarship(
            name = "艺术新星奖",
            tier = ScholarshipTier.PARTIAL,
            criteria = ScholarshipCriteria.ART_TALENT,
            amountPerStudent = 0.3,
            maxRecipients = 8,
            createdYear = year,
            description = "支持在艺术领域展现天赋的学生"
        ),
        Scholarship(
            name = "创新创业奖",
            tier = ScholarshipTier.HONORARY,
            criteria = ScholarshipCriteria.INNOVATION,
            amountPerStudent = 0.4,
            maxRecipients = 5,
            createdYear = year,
            description = "鼓励创新思维和创业精神"
        ),
        Scholarship(
            name = "志愿服务奖学金",
            tier = ScholarshipTier.PARTIAL,
            criteria = ScholarshipCriteria.COMMUNITY_SERVICE,
            amountPerStudent = 0.2,
            maxRecipients = 12,
            createdYear = year,
            description = "表彰热心公益的优秀学生"
        ),
        Scholarship(
            name = "助学金",
            tier = ScholarshipTier.HALF,
            criteria = ScholarshipCriteria.FINANCIAL_NEED,
            amountPerStudent = 0.4,
            maxRecipients = 15,
            createdYear = year,
            description = "帮助家庭经济困难的优秀学生"
        ),
        Scholarship(
            name = "学生领袖奖",
            tier = ScholarshipTier.HONORARY,
            criteria = ScholarshipCriteria.LEADERSHIP,
            amountPerStudent = 0.25,
            maxRecipients = 5,
            createdYear = year,
            description = "奖励在学生组织中表现突出的领导者"
        )
    )
}

// ==================== 管理器 ====================

@Singleton
class ScholarshipManager @Inject constructor() {

    private val _state = MutableStateFlow(ScholarshipState())
    val state: StateFlow<ScholarshipState> = _state.asStateFlow()

    fun reset() {
        _state.value = ScholarshipState()
    }

    /**
     * 设立新奖学金
     */
    fun createScholarship(
        name: String,
        tier: ScholarshipTier,
        criteria: ScholarshipCriteria,
        amountPerStudent: Double,
        maxRecipients: Int,
        year: Int,
        description: String
    ) {
        val scholarship = Scholarship(
            name = name,
            tier = tier,
            criteria = criteria,
            amountPerStudent = amountPerStudent,
            maxRecipients = maxRecipients,
            createdYear = year,
            description = description
        )
        _state.update { current ->
            current.copy(
                scholarships = current.scholarships + scholarship,
                totalBudgetAllocated = current.totalBudgetAllocated + amountPerStudent * maxRecipients
            )
        }
        recalcBonuses()
    }

    /**
     * 从模板快速设立奖学金
     */
    fun createFromTemplate(templateIndex: Int, year: Int) {
        val templates = ScholarshipTemplates.getTemplates(year)
        if (templateIndex in templates.indices) {
            val template = templates[templateIndex]
            // 避免重复创建
            if (_state.value.scholarships.none { it.name == template.name }) {
                _state.update { current ->
                    current.copy(
                        scholarships = current.scholarships + template,
                        totalBudgetAllocated = current.totalBudgetAllocated +
                                template.amountPerStudent * template.maxRecipients
                    )
                }
                recalcBonuses()
            }
        }
    }

    /**
     * 月度推进 - 每学期初（3月/9月）发放奖学金
     */
    fun advanceMonth(
        year: Int, month: Int,
        studentCount: Int,
        avgStudentGpa: Float,
        schoolReputation: Long
    ): ScholarshipMonthResult {
        if (_state.value.scholarships.isEmpty()) {
            return ScholarshipMonthResult()
        }

        var totalExpenses = 0.0
        var newRecipients = 0
        var reputationGain = 0
        val events = mutableListOf<String>()

        // 每学期初发放奖学金（3月和9月）
        if (month == 3 || month == 9) {
            val activeScholarships = _state.value.scholarships.filter { it.isActive }
            val newRecipientList = mutableListOf<ScholarshipRecipient>()

            activeScholarships.forEach { scholarship ->
                // 计算本期实际获奖人数
                val eligible = calculateEligibleCount(scholarship, studentCount, avgStudentGpa)
                val recipients = eligible.coerceAtMost(scholarship.maxRecipients)

                if (recipients > 0) {
                    val cost = scholarship.amountPerStudent * recipients
                    totalExpenses += cost
                    newRecipients += recipients

                    repeat(recipients) { i ->
                        newRecipientList.add(
                            ScholarshipRecipient(
                                studentName = generateRecipientName(),
                                scholarshipId = scholarship.id,
                                scholarshipName = scholarship.name,
                                amount = scholarship.amountPerStudent,
                                year = year,
                                month = month,
                                gpa = (avgStudentGpa + Random.nextFloat() * 0.5f).coerceAtMost(4.0f)
                            )
                        )
                    }

                    // 更新获奖人数
                    scholarship.currentRecipients = recipients
                }
            }

            if (newRecipients > 0) {
                events.add("本期共${newRecipients}名学生获得奖学金，总计¥${totalExpenses.toInt()}")
                reputationGain = (newRecipients / 3).coerceIn(1, 10)
            }

            _state.update { current ->
                current.copy(
                    recipients = (current.recipients + newRecipientList).takeLast(100),
                    totalAwarded = current.totalAwarded + totalExpenses,
                    recentEvents = events,
                    yearlyStats = YearlyScholarshipStats(
                        totalRecipients = newRecipientList.size,
                        totalAmount = totalExpenses,
                        avgGpa = if (newRecipientList.isNotEmpty())
                            newRecipientList.map { it.gpa }.average().toFloat() else 0f,
                        topStudentName = newRecipientList.maxByOrNull { it.gpa }?.studentName ?: ""
                    )
                )
            }
        }

        recalcBonuses()

        return ScholarshipMonthResult(
            expenses = totalExpenses,
            newRecipients = newRecipients,
            retentionEffect = _state.value.retentionBonus,
            enrollmentBonus = _state.value.studentAttractionBonus,
            reputationGain = reputationGain
        )
    }

    /** 按在设奖项的名额与金额重算招生/留存/声誉，设立或取消后立刻生效。 */
    fun recalcBonuses() {
        val active = _state.value.scholarships.filter { it.isActive }
        val slots = active.sumOf { it.maxRecipients }
        val yearlyBudget = active.sumOf { it.amountPerStudent * it.maxRecipients }
        val enrollmentBonus = (slots * 0.012f + yearlyBudget.toFloat() * 0.04f).coerceIn(0f, 0.28f)
        val retentionEffect = (slots * 0.008f).coerceAtMost(0.18f)
        val reputationBonus = (slots / 2).coerceAtMost(18)
        _state.update { current ->
            current.copy(
                studentAttractionBonus = enrollmentBonus,
                retentionBonus = retentionEffect,
                reputationBonus = reputationBonus
            )
        }
    }

    /**
     * 取消奖学金
     */
    fun cancelScholarship(scholarshipId: String) {
        _state.update { current ->
            current.copy(
                scholarships = current.scholarships.filter { it.id != scholarshipId }
            )
        }
        recalcBonuses()
    }

    /** 加/减名额：立刻改招生加成和学期发放开支。 */
    fun adjustRecipients(scholarshipId: String, delta: Int) {
        _state.update { current ->
            current.copy(
                scholarships = current.scholarships.map { item ->
                    if (item.id != scholarshipId) item
                    else item.copy(maxRecipients = (item.maxRecipients + delta).coerceIn(1, 40))
                }
            )
        }
        recalcBonuses()
    }

    /**
     * 获取模板列表
     */
    fun getTemplates(year: Int): List<Scholarship> = ScholarshipTemplates.getTemplates(year)

    /**
     * 获取招生加成
     */
    fun getEnrollmentBonus(): Float = _state.value.studentAttractionBonus

    /**
     * 获取留存加成
     */
    fun getRetentionBonus(): Float = _state.value.retentionBonus

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = ScholarshipPersistData(
                scholarships = state.scholarships.map { s ->
                    ScholarshipPersist(
                        id = s.id, name = s.name, tier = s.tier.name,
                        criteria = s.criteria.name, amountPerStudent = s.amountPerStudent,
                        maxRecipients = s.maxRecipients, currentRecipients = s.currentRecipients,
                        isActive = s.isActive, createdYear = s.createdYear, description = s.description
                    )
                },
                recipients = state.recipients.map { r ->
                    RecipientPersist(
                        studentName = r.studentName, scholarshipId = r.scholarshipId,
                        scholarshipName = r.scholarshipName, gpa = r.gpa,
                        year = r.year, month = r.month, amount = r.amount
                    )
                },
                totalBudgetAllocated = state.totalBudgetAllocated,
                totalAwarded = state.totalAwarded,
                studentAttractionBonus = state.studentAttractionBonus,
                retentionBonus = state.retentionBonus,
                reputationBonus = state.reputationBonus
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<ScholarshipPersistData>(json)
            val scholarships = data.scholarships.mapNotNull { sp ->
                val tier = try { ScholarshipTier.valueOf(sp.tier) } catch (_: Exception) { return@mapNotNull null }
                val criteria = try { ScholarshipCriteria.valueOf(sp.criteria) } catch (_: Exception) { ScholarshipCriteria.ACADEMIC_EXCELLENCE }
                Scholarship(
                    id = sp.id, name = sp.name, tier = tier, criteria = criteria,
                    amountPerStudent = sp.amountPerStudent, maxRecipients = sp.maxRecipients,
                    currentRecipients = sp.currentRecipients, isActive = sp.isActive,
                    createdYear = sp.createdYear, description = sp.description
                )
            }
            val recipients = data.recipients.map { rp ->
                ScholarshipRecipient(
                    studentName = rp.studentName, scholarshipId = rp.scholarshipId,
                    scholarshipName = rp.scholarshipName, gpa = rp.gpa,
                    year = rp.year, month = rp.month, amount = rp.amount
                )
            }
            _state.value = ScholarshipState(
                scholarships = scholarships, recipients = recipients,
                totalBudgetAllocated = data.totalBudgetAllocated,
                totalAwarded = data.totalAwarded,
                studentAttractionBonus = data.studentAttractionBonus,
                retentionBonus = data.retentionBonus,
                reputationBonus = data.reputationBonus
            )
            recalcBonuses()
        } catch (e: Exception) {
            throw IllegalArgumentException("ScholarshipManager.restoreFromJson failed", e)
        }
    }

    // ==================== 私有方法 ====================

    private fun calculateEligibleCount(
        scholarship: Scholarship,
        studentCount: Int,
        avgGpa: Float
    ): Int {
        if (studentCount == 0) return 0
        val baseEligible = when (scholarship.criteria) {
            ScholarshipCriteria.ACADEMIC_EXCELLENCE -> (studentCount * 0.10f).toInt() // top 10%
            ScholarshipCriteria.SPORTS_TALENT -> (studentCount * 0.05f).toInt()
            ScholarshipCriteria.ART_TALENT -> (studentCount * 0.05f).toInt()
            ScholarshipCriteria.LEADERSHIP -> (studentCount * 0.03f).toInt()
            ScholarshipCriteria.COMMUNITY_SERVICE -> (studentCount * 0.08f).toInt()
            ScholarshipCriteria.INNOVATION -> (studentCount * 0.04f).toInt()
            ScholarshipCriteria.FINANCIAL_NEED -> (studentCount * 0.12f).toInt()
            ScholarshipCriteria.ALL_ROUND -> (studentCount * 0.02f).toInt()
        }
        return baseEligible.coerceAtLeast(1)
    }

    private val recipientNamePool = listOf(
        "张三", "李四", "王五", "赵六", "钱七", "孙八",
        "周明", "吴芳", "郑浩", "王丽", "冯伟", "陈静",
        "褚强", "卫华", "蒋磊", "沈洁", "韩雪", "杨帆",
        "朱婷", "秦凯", "许明", "何欢", "吕超", "施雨"
    )

    private fun generateRecipientName(): String = recipientNamePool.random()
}

@Serializable
data class ScholarshipPersistData(
    val scholarships: List<ScholarshipPersist> = emptyList(),
    val recipients: List<RecipientPersist> = emptyList(),
    val totalBudgetAllocated: Double = 0.0,
    val totalAwarded: Double = 0.0,
    val studentAttractionBonus: Float = 0f,
    val retentionBonus: Float = 0f,
    val reputationBonus: Int = 0
)

@Serializable
data class ScholarshipPersist(
    val id: String,
    val name: String,
    val tier: String,
    val criteria: String,
    val amountPerStudent: Double,
    val maxRecipients: Int,
    val currentRecipients: Int,
    val isActive: Boolean,
    val createdYear: Int,
    val description: String
)

@Serializable
data class RecipientPersist(
    val studentName: String,
    val scholarshipId: String,
    val scholarshipName: String,
    val gpa: Float,
    val year: Int,
    val month: Int,
    val amount: Double
)
