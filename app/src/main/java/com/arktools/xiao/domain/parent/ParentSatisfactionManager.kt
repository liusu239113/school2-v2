package com.arktools.xiao.domain.parent

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

enum class ComplaintType(val displayName: String, val severityBase: Int) {
    TEACHING_QUALITY("教学质量不满", 3),
    BULLYING("校园霸凌投诉", 5),
    FACILITY_POOR("设施环境差", 2),
    SAFETY_CONCERN("安全隐患", 4),
    FEE_TOO_HIGH("学费过高", 2),
    TEACHER_ATTITUDE("教师态度问题", 3),
    FOOD_QUALITY("食堂质量", 2),
    HOMEWORK_OVERLOAD("作业量过大", 1),
    COMMUNICATION_LACK("家校沟通不足", 2),
    UNFAIR_TREATMENT("不公平对待", 4)
}

enum class ComplaintStatus {
    PENDING, IN_PROGRESS, RESOLVED, IGNORED
}

data class Complaint(
    val id: String = "CMP-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
    val type: ComplaintType,
    val parentName: String,
    val studentName: String,
    val description: String,
    val severity: Int, // 1-5
    var status: ComplaintStatus = ComplaintStatus.PENDING,
    val createdYear: Int,
    val createdMonth: Int,
    var resolvedMonth: Int = 0,
    var satisfactionImpact: Float = 0f
)

enum class MeetingType(val displayName: String, val costPerParent: Double, val satisfactionBoost: Float) {
    REGULAR("常规家长会", 50.0, 3f),
    OPEN_DAY("校园开放日", 200.0, 8f),
    ACADEMIC_REPORT("学业汇报会", 100.0, 5f),
    CELEBRATION("表彰大会", 150.0, 10f),
    CONSULTATION("一对一面谈", 30.0, 6f)
}

data class ParentMeeting(
    val id: String = "MTG-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
    val type: MeetingType,
    val scheduledYear: Int,
    val scheduledMonth: Int,
    var isCompleted: Boolean = false,
    var attendanceRate: Float = 0f,
    var cost: Double = 0.0,
    var satisfactionGained: Float = 0f
)

enum class WordOfMouthLevel(val displayName: String, val color: Long, val enrollmentMultiplier: Float) {
    TERRIBLE("口碑极差", 0xFFD32F2F, 0.5f),
    POOR("口碑较差", 0xFFF57C00, 0.7f),
    AVERAGE("口碑一般", 0xFF9E9E9E, 1.0f),
    GOOD("口碑良好", 0xFF388E3C, 1.2f),
    EXCELLENT("口碑极佳", 0xFF1565C0, 1.5f),
    LEGENDARY("金牌口碑", 0xFFFF8F00, 2.0f)
}

data class ParentState(
    val overallSatisfaction: Float = 50f, // 0-100, 初始50（需要努力提升）
    val wordOfMouth: WordOfMouthLevel = WordOfMouthLevel.AVERAGE,
    val complaints: List<Complaint> = emptyList(),
    val meetings: List<ParentMeeting> = emptyList(),
    val recentEvents: List<String> = emptyList(),
    val monthlyTrend: Float = 0f, // positive = improving
    val satisfactionHistory: List<Float> = emptyList(), // last 12 months
    val totalComplaintsResolved: Int = 0,
    val totalMeetingsHeld: Int = 0,
    val communicationScore: Float = 50f, // 家校沟通评分
    val trustLevel: Float = 60f // 家长信任度
)

data class ParentMonthResult(
    val satisfactionChange: Float = 0f,
    val newComplaints: List<Complaint> = emptyList(),
    val resolvedComplaints: Int = 0,
    val wordOfMouthChanged: Boolean = false,
    val enrollmentBonus: Float = 0f, // 口碑带来的招生加成
    val reputationImpact: Int = 0
)

// ==================== 管理器 ====================

@Singleton
class ParentSatisfactionManager @Inject constructor() {

    private val _state = MutableStateFlow(ParentState())
    val state: StateFlow<ParentState> = _state.asStateFlow()

    fun reset() {
        _state.value = ParentState()
    }

    private val parentNames = listOf(
        "张先生", "李女士", "王先生", "刘女士", "陈先生",
        "杨女士", "赵先生", "黄女士", "周先生", "吴女士",
        "徐先生", "孙女士", "马先生", "朱女士", "胡先生",
        "郭女士", "林先生", "何女士", "高先生", "梁女士"
    )

    private val studentNames = listOf(
        "小明", "小红", "小华", "小丽", "小刚",
        "小芳", "小伟", "小燕", "小强", "小敏"
    )

    /**
     * 月度推进
     * @param intensitySatisfactionPenalty 教学强度带来的每月满意度惩罚(负值表示扣减)
     * @param intensityComplaintRate 教学强度导致的额外投诉概率(0-1)
     * @param academicPerformance 学生平均学业表现(0-100)，影响家长对学校教学质量的认可
     * @param clubActivityLevel 社团活动活跃度(0-100)，影响家长对素质教育的认可
     */
    fun advanceMonth(
        year: Int, month: Int,
        schoolReputation: Long,
        teacherAvgLoyalty: Float,
        studentSatisfaction: Float,
        facilityCondition: Float,
        intensitySatisfactionPenalty: Float = 0f,
        intensityComplaintRate: Float = 0f,
        academicPerformance: Float = 50f,
        clubActivityLevel: Float = 0f
    ): ParentMonthResult {
        var satisfactionChange = 0f
        val newComplaints = mutableListOf<Complaint>()
        var resolvedCount = 0
        var reputationImpact = 0

        // === 自然衰减：满意度越高，维持越难（需要持续努力） ===
        // 满意度70以上每月自然-0.5~-2.0，模拟家长期望递增
        val currentSatisfaction = _state.value.overallSatisfaction
        val naturalDecay = when {
            currentSatisfaction > 90f -> -2.0f   // 极高满意度快速衰减
            currentSatisfaction > 80f -> -1.5f
            currentSatisfaction > 70f -> -1.0f
            currentSatisfaction > 60f -> -0.5f
            else -> 0f                            // 60以下不衰减
        }
        satisfactionChange += naturalDecay

        // 1. 基础满意度受学校各方面影响（系数降低，不再轻易上涨）
        val teacherFactor = (teacherAvgLoyalty - 60f) * 0.015f // 基准提高到60，系数降低
        val studentFactor = (studentSatisfaction - 60f) * 0.02f // 基准提高到60
        val facilityFactor = (facilityCondition - 50f) * 0.008f
        val reputationFactor = (schoolReputation - 200) * 0.002f // 基准提高到200

        satisfactionChange += teacherFactor + studentFactor + facilityFactor + reputationFactor.toFloat()

        // 1.3 学业成绩对满意度的关键影响（家长最关心成绩！）
        // academicPerformance: 0-100, 基准60分
        // 60分以下扣分，60-80正常，80以上加分
        val academicFactor = when {
            academicPerformance >= 80f -> (academicPerformance - 80f) * 0.05f + 0.5f  // 优秀: +0.5~+1.5
            academicPerformance >= 60f -> (academicPerformance - 60f) * 0.025f         // 及格: +0~+0.5
            academicPerformance >= 40f -> (academicPerformance - 60f) * 0.04f          // 不及格: -0.8~0
            else -> (academicPerformance - 60f) * 0.06f                                // 很差: -3.6~-1.2
        }
        satisfactionChange += academicFactor

        // 1.4 社团活动对满意度的影响（素质教育加分项）
        // 有社团活动=加分，但权重低于成绩
        val clubFactor = when {
            clubActivityLevel >= 60f -> (clubActivityLevel - 50f) * 0.01f  // 活跃: +0.1~+0.5
            clubActivityLevel >= 30f -> 0f                                  // 一般: 无影响
            else -> -0.3f                                                   // 完全没有社团: 轻微扣分
        }
        satisfactionChange += clubFactor

        // 1.5 教学强度对满意度的直接影响（高强度教学→家长不满）
        satisfactionChange += intensitySatisfactionPenalty

        // 2. 处理未解决投诉的负面影响
        val pendingComplaints = _state.value.complaints.filter { it.status == ComplaintStatus.PENDING }
        pendingComplaints.forEach { complaint ->
            satisfactionChange -= complaint.severity * 0.5f
            // 长期未处理的投诉加重
            if (complaint.createdMonth < month - 1 || complaint.createdYear < year) {
                satisfactionChange -= complaint.severity * 0.3f
            }
        }

        // 3. 生成新投诉（概率基于满意度，即使高满意度也有投诉概率）
        val complaintChance = when {
            _state.value.overallSatisfaction < 30f -> 0.7f
            _state.value.overallSatisfaction < 50f -> 0.5f
            _state.value.overallSatisfaction < 60f -> 0.35f
            _state.value.overallSatisfaction < 70f -> 0.25f
            _state.value.overallSatisfaction < 80f -> 0.15f
            _state.value.overallSatisfaction < 90f -> 0.10f
            else -> 0.06f  // 即使90+也有6%概率（总有不满的家长）
        }

        val complaintCount = if (Random.nextFloat() < complaintChance) {
            if (_state.value.overallSatisfaction < 40f) Random.nextInt(1, 4) else 1
        } else 0

        repeat(complaintCount) {
            val type = ComplaintType.entries.random()
            val complaint = Complaint(
                type = type,
                parentName = parentNames.random(),
                studentName = studentNames.random(),
                description = generateComplaintDescription(type),
                severity = (type.severityBase + Random.nextInt(-1, 2)).coerceIn(1, 5),
                createdYear = year,
                createdMonth = month
            )
            newComplaints.add(complaint)
        }

        // 3.5 教学强度额外投诉（高强度教学→作业过多投诉）
        if (intensityComplaintRate > 0f && Random.nextFloat() < intensityComplaintRate) {
            val complaint = Complaint(
                type = ComplaintType.HOMEWORK_OVERLOAD,
                parentName = parentNames.random(),
                studentName = studentNames.random(),
                description = "孩子每天作业做到很晚，压力太大了，希望学校能减轻负担。",
                severity = 2,
                createdYear = year,
                createdMonth = month
            )
            newComplaints.add(complaint)
        }

        // 4. 自动解决一些简单投诉（沟通分高时）
        if (_state.value.communicationScore > 70f) {
            val autoResolve = _state.value.complaints
                .filter { it.status == ComplaintStatus.PENDING && it.severity <= 2 }
                .take(1)
            autoResolve.forEach { complaint ->
                complaint.status = ComplaintStatus.RESOLVED
                complaint.resolvedMonth = month
                complaint.satisfactionImpact = 2f
                satisfactionChange += 2f
                resolvedCount++
            }
        }

        // 5. 已完成会议的持续效果（信任度衰减慢）
        val recentMeetings = _state.value.meetings.filter { it.isCompleted }
        if (recentMeetings.isNotEmpty()) {
            satisfactionChange += 1f // 有会议记录的基础加成
        }

        // 6. 更新口碑等级
        val currentSat = (_state.value.overallSatisfaction + satisfactionChange).coerceIn(0f, 100f)
        val newWordOfMouth = calculateWordOfMouth(currentSat, _state.value.trustLevel)
        val wordOfMouthChanged = newWordOfMouth != _state.value.wordOfMouth

        // 7. 口碑影响声誉
        reputationImpact = when (newWordOfMouth) {
            WordOfMouthLevel.LEGENDARY -> 5
            WordOfMouthLevel.EXCELLENT -> 3
            WordOfMouthLevel.GOOD -> 1
            WordOfMouthLevel.AVERAGE -> 0
            WordOfMouthLevel.POOR -> -2
            WordOfMouthLevel.TERRIBLE -> -5
        }

        // 8. 信任度更新
        val trustChange = when {
            resolvedCount > 0 -> 2f
            pendingComplaints.size > 3 -> -3f
            _state.value.overallSatisfaction > 80f -> 1f
            _state.value.overallSatisfaction < 40f -> -2f
            else -> 0f
        }

        _state.update { current ->
            val updatedComplaints = current.complaints.toMutableList().apply {
                addAll(newComplaints)
                // 保留最近6个月的投诉
                val cutoff = if (month > 6) month - 6 else 0
                removeAll { it.status == ComplaintStatus.RESOLVED && it.resolvedMonth < cutoff && it.createdYear < year }
            }
            val updatedHistory = current.satisfactionHistory.toMutableList().apply {
                add(currentSat)
                if (size > 12) removeAt(0)
            }
            val events = mutableListOf<String>()
            if (newComplaints.isNotEmpty()) {
                events.add("收到${newComplaints.size}条家长投诉")
            }
            if (resolvedCount > 0) {
                events.add("成功解决${resolvedCount}条投诉")
            }
            if (wordOfMouthChanged) {
                events.add("口碑变为: ${newWordOfMouth.displayName}")
            }

            current.copy(
                overallSatisfaction = currentSat,
                wordOfMouth = newWordOfMouth,
                complaints = updatedComplaints,
                recentEvents = events,
                monthlyTrend = satisfactionChange,
                satisfactionHistory = updatedHistory,
                totalComplaintsResolved = current.totalComplaintsResolved + resolvedCount,
                communicationScore = (current.communicationScore + trustChange * 0.5f).coerceIn(0f, 100f),
                trustLevel = (current.trustLevel + trustChange).coerceIn(0f, 100f)
            )
        }

        return ParentMonthResult(
            satisfactionChange = satisfactionChange,
            newComplaints = newComplaints,
            resolvedComplaints = resolvedCount,
            wordOfMouthChanged = wordOfMouthChanged,
            enrollmentBonus = newWordOfMouth.enrollmentMultiplier - 1f,
            reputationImpact = reputationImpact
        )
    }

    /**
     * 处理投诉
     */
    fun resolveComplaint(complaintId: String) {
        _state.update { current ->
            val updated = current.complaints.map { complaint ->
                if (complaint.id == complaintId && complaint.status == ComplaintStatus.PENDING) {
                    complaint.copy(
                        status = ComplaintStatus.RESOLVED,
                        satisfactionImpact = complaint.severity * 1.5f
                    )
                } else complaint
            }
            val resolved = updated.find { it.id == complaintId }
            val satBoost = resolved?.satisfactionImpact ?: 0f
            current.copy(
                complaints = updated,
                overallSatisfaction = (current.overallSatisfaction + satBoost).coerceAtMost(100f),
                totalComplaintsResolved = current.totalComplaintsResolved + 1,
                trustLevel = (current.trustLevel + 1.5f).coerceAtMost(100f)
            )
        }
    }

    /**
     * 忽略投诉（会降低满意度）
     */
    fun ignoreComplaint(complaintId: String) {
        _state.update { current ->
            val updated = current.complaints.map { complaint ->
                if (complaint.id == complaintId && complaint.status == ComplaintStatus.PENDING) {
                    complaint.copy(status = ComplaintStatus.IGNORED, satisfactionImpact = -complaint.severity * 2f)
                } else complaint
            }
            val ignored = updated.find { it.id == complaintId }
            val satPenalty = ignored?.let { it.severity * 2f } ?: 0f
            current.copy(
                complaints = updated,
                overallSatisfaction = (current.overallSatisfaction - satPenalty).coerceAtLeast(0f),
                trustLevel = (current.trustLevel - 3f).coerceAtLeast(0f)
            )
        }
    }

    /**
     * 安排家长会
     */
    fun scheduleMeeting(type: MeetingType, year: Int, month: Int, studentCount: Int): Double {
        val cost = type.costPerParent * studentCount.coerceAtMost(200) * 0.3 // 约30%家长参加
        val meeting = ParentMeeting(
            type = type,
            scheduledYear = year,
            scheduledMonth = month,
            cost = cost
        )
        _state.update { current ->
            current.copy(meetings = current.meetings + meeting)
        }
        return cost
    }

    /**
     * 完成家长会
     */
    fun completeMeeting(meetingId: String) {
        _state.update { current ->
            val updated = current.meetings.map { meeting ->
                if (meeting.id == meetingId && !meeting.isCompleted) {
                    val attendance = Random.nextFloat() * 0.4f + 0.4f // 40%-80%
                    val satGain = meeting.type.satisfactionBoost * attendance
                    meeting.copy(
                        isCompleted = true,
                        attendanceRate = attendance,
                        satisfactionGained = satGain
                    )
                } else meeting
            }
            val completed = updated.find { it.id == meetingId }
            val satBoost = completed?.satisfactionGained ?: 0f
            current.copy(
                meetings = updated,
                overallSatisfaction = (current.overallSatisfaction + satBoost).coerceAtMost(100f),
                communicationScore = (current.communicationScore + 5f).coerceAtMost(100f),
                trustLevel = (current.trustLevel + 3f).coerceAtMost(100f),
                totalMeetingsHeld = current.totalMeetingsHeld + 1
            )
        }
    }

    /**
     * 获取当前口碑对招生的加成
     */
    fun getEnrollmentMultiplier(): Float = _state.value.wordOfMouth.enrollmentMultiplier

    /**
     * 获取待处理投诉数
     */
    fun getPendingComplaintCount(): Int =
        _state.value.complaints.count { it.status == ComplaintStatus.PENDING }

    fun getMeetingTypes(): List<MeetingType> = MeetingType.entries

    // ==================== 私有方法 ====================

    private fun calculateWordOfMouth(satisfaction: Float, trust: Float): WordOfMouthLevel {
        val combined = satisfaction * 0.7f + trust * 0.3f
        return when {
            combined >= 90f -> WordOfMouthLevel.LEGENDARY
            combined >= 78f -> WordOfMouthLevel.EXCELLENT
            combined >= 65f -> WordOfMouthLevel.GOOD
            combined >= 45f -> WordOfMouthLevel.AVERAGE
            combined >= 25f -> WordOfMouthLevel.POOR
            else -> WordOfMouthLevel.TERRIBLE
        }
    }

    private fun generateComplaintDescription(type: ComplaintType): String {
        return when (type) {
            ComplaintType.TEACHING_QUALITY -> listOf(
                "孩子反映课堂内容过于枯燥，希望能改进教学方式",
                "最近考试成绩下滑明显，怀疑教学质量有问题",
                "老师讲课速度太快，孩子跟不上进度"
            ).random()
            ComplaintType.BULLYING -> listOf(
                "孩子在学校遭到同学欺负，情绪非常低落",
                "发现孩子身上有伤痕，怀疑在学校被欺负",
                "孩子不愿意去学校，说有同学一直取笑他"
            ).random()
            ComplaintType.FACILITY_POOR -> listOf(
                "教室桌椅已经很破旧了，希望学校能更换",
                "操场跑道损坏严重，孩子运动容易受伤",
                "厕所卫生条件太差，孩子都不愿意去"
            ).random()
            ComplaintType.SAFETY_CONCERN -> listOf(
                "校门口交通混乱，接送孩子非常危险",
                "学校围墙有个缺口，外人可以随意进入",
                "消防通道被杂物堵住了，非常危险"
            ).random()
            ComplaintType.FEE_TOO_HIGH -> listOf(
                "各种课外费用太多，加起来负担很重",
                "学费年年涨，但教学质量没见提升",
                "校服、教材费用比其他学校高出很多"
            ).random()
            ComplaintType.TEACHER_ATTITUDE -> listOf(
                "老师对学生态度很冷漠，缺乏耐心",
                "孩子说老师经常在课上发脾气",
                "老师偏心严重，对成绩好的学生明显更关注"
            ).random()
            ComplaintType.FOOD_QUALITY -> listOf(
                "学校食堂饭菜质量越来越差",
                "孩子说中午经常吃不饱",
                "食堂卫生状况令人担忧"
            ).random()
            ComplaintType.HOMEWORK_OVERLOAD -> listOf(
                "每天作业写到晚上11点，孩子严重睡眠不足",
                "周末作业量太大，完全没有休息时间",
                "希望学校能减轻作业负担，注重素质教育"
            ).random()
            ComplaintType.COMMUNICATION_LACK -> listOf(
                "学校很少主动联系家长，不了解孩子在校情况",
                "出了问题才通知家长，平时沟通太少",
                "家长群里老师从来不回复消息"
            ).random()
            ComplaintType.UNFAIR_TREATMENT -> listOf(
                "学业导师对我家孩子有偏见，经常无故批评",
                "评奖评优不透明，怀疑有暗箱操作",
                "座位安排不合理，孩子视力不好却坐在后排"
            ).random()
        }
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = ParentPersistData(
                overallSatisfaction = state.overallSatisfaction,
                wordOfMouth = state.wordOfMouth.name,
                complaints = state.complaints.map { c ->
                    ComplaintPersist(
                        id = c.id,
                        type = c.type.name,
                        parentName = c.parentName,
                        studentName = c.studentName,
                        description = c.description,
                        severity = c.severity,
                        status = c.status.name,
                        createdYear = c.createdYear,
                        createdMonth = c.createdMonth,
                        resolvedMonth = c.resolvedMonth
                    )
                },
                meetings = state.meetings.map { m ->
                    MeetingPersist(
                        id = m.id,
                        type = m.type.name,
                        scheduledYear = m.scheduledYear,
                        scheduledMonth = m.scheduledMonth,
                        isCompleted = m.isCompleted,
                        attendanceRate = m.attendanceRate,
                        cost = m.cost,
                        satisfactionGained = m.satisfactionGained
                    )
                },
                monthlyTrend = state.monthlyTrend,
                satisfactionHistory = state.satisfactionHistory,
                totalComplaintsResolved = state.totalComplaintsResolved,
                totalMeetingsHeld = state.totalMeetingsHeld,
                communicationScore = state.communicationScore,
                trustLevel = state.trustLevel
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<ParentPersistData>(json)
            val wom = try { WordOfMouthLevel.valueOf(data.wordOfMouth) } catch (_: Exception) { WordOfMouthLevel.AVERAGE }
            val complaints = data.complaints.mapNotNull { cp ->
                val type = try { ComplaintType.valueOf(cp.type) } catch (_: Exception) { return@mapNotNull null }
                val status = try { ComplaintStatus.valueOf(cp.status) } catch (_: Exception) { ComplaintStatus.PENDING }
                Complaint(
                    id = cp.id,
                    type = type,
                    parentName = cp.parentName,
                    studentName = cp.studentName,
                    description = cp.description,
                    severity = cp.severity,
                    status = status,
                    createdYear = cp.createdYear,
                    createdMonth = cp.createdMonth,
                    resolvedMonth = cp.resolvedMonth
                )
            }
            val meetings = data.meetings.mapNotNull { mp ->
                val type = try { MeetingType.valueOf(mp.type) } catch (_: Exception) { return@mapNotNull null }
                ParentMeeting(
                    id = mp.id,
                    type = type,
                    scheduledYear = mp.scheduledYear,
                    scheduledMonth = mp.scheduledMonth,
                    isCompleted = mp.isCompleted,
                    attendanceRate = mp.attendanceRate,
                    cost = mp.cost,
                    satisfactionGained = mp.satisfactionGained
                )
            }
            _state.value = ParentState(
                overallSatisfaction = data.overallSatisfaction,
                wordOfMouth = wom,
                complaints = complaints,
                meetings = meetings,
                recentEvents = emptyList(),
                monthlyTrend = data.monthlyTrend,
                satisfactionHistory = data.satisfactionHistory,
                totalComplaintsResolved = data.totalComplaintsResolved,
                totalMeetingsHeld = data.totalMeetingsHeld,
                communicationScore = data.communicationScore,
                trustLevel = data.trustLevel
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("ParentSatisfactionManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class ParentPersistData(
    val overallSatisfaction: Float = 50f,
    val wordOfMouth: String = "AVERAGE",
    val complaints: List<ComplaintPersist> = emptyList(),
    val meetings: List<MeetingPersist> = emptyList(),
    val monthlyTrend: Float = 0f,
    val satisfactionHistory: List<Float> = emptyList(),
    val totalComplaintsResolved: Int = 0,
    val totalMeetingsHeld: Int = 0,
    val communicationScore: Float = 50f,
    val trustLevel: Float = 60f
)

@Serializable
data class ComplaintPersist(
    val id: String,
    val type: String,
    val parentName: String,
    val studentName: String,
    val description: String,
    val severity: Int,
    val status: String,
    val createdYear: Int,
    val createdMonth: Int,
    val resolvedMonth: Int = 0
)

@Serializable
data class MeetingPersist(
    val id: String,
    val type: String,
    val scheduledYear: Int,
    val scheduledMonth: Int,
    val isCompleted: Boolean = false,
    val attendanceRate: Float = 0f,
    val cost: Double = 0.0,
    val satisfactionGained: Float = 0f
)
