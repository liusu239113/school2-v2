package com.arktools.xiao.domain.suggestion

import com.arktools.xiao.domain.model.BackgroundTier
import com.arktools.xiao.domain.model.Facility
import com.arktools.xiao.domain.model.FacilityType
import com.arktools.xiao.domain.model.HealthStatus
import com.arktools.xiao.domain.model.Student
import com.arktools.xiao.domain.model.Teacher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 意见箱管理器
 *
 * 学生和教师可以通过意见箱反映学校实际存在的问题。
 * 校长需要处理这些建议，否则提建议者的忠诚度/满意度会下降。
 *
 * 生成逻辑：每月根据学校实际状态检测问题，生成对应的建议。
 * 建议可以是匿名或实名的。
 */
@Singleton
class SuggestionBoxManager @Inject constructor() {

    private val _suggestions = mutableListOf<Suggestion>()
    val suggestions: List<Suggestion> get() = _suggestions.toList()

    private val _pendingCount = MutableStateFlow(0)
    /** 待处理建议数量（响应式） */
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var nextId = 1

    private fun updatePendingCount() {
        _pendingCount.value = _suggestions.count { it.status == SuggestionStatus.PENDING }
    }

    /**
     * 月度推进：根据学校实际状态生成新的建议
     */
    fun advanceMonth(
        students: List<Student>,
        teachers: List<Teacher>,
        facilities: List<Facility>,
        teacherAvgSkill: Float,
        avgStudentSatisfaction: Float,
        avgTeacherLoyalty: Float,
        schoolCash: Double,
        currentYear: Int,
        currentMonth: Int
    ) {
        // 先对过期未处理的建议施加惩罚标记
        markOverdueSuggestions(currentYear, currentMonth)

        // 根据实际问题生成建议（每月最多生成3条新建议）
        val newSuggestions = mutableListOf<Suggestion>()

        // 1. 设施相关问题
        checkFacilityIssues(facilities, students, teachers, currentYear, currentMonth)?.let {
            newSuggestions.add(it)
        }

        // 2. 教学质量问题
        checkTeachingQuality(teachers, teacherAvgSkill, students, currentYear, currentMonth)?.let {
            newSuggestions.add(it)
        }

        // 3. 学生满意度问题
        checkStudentWelfare(students, avgStudentSatisfaction, currentYear, currentMonth)?.let {
            newSuggestions.add(it)
        }

        // 4. 教师待遇问题
        checkTeacherWelfare(teachers, avgTeacherLoyalty, currentYear, currentMonth)?.let {
            newSuggestions.add(it)
        }

        // 5. 财务问题（学费太贵等）
        checkFinancialIssues(schoolCash, students, currentYear, currentMonth)?.let {
            newSuggestions.add(it)
        }

        // 6. 日常建议（保底生成，确保早期也能收到建议）
        if (newSuggestions.isEmpty()) {
            generateRoutineSuggestion(students, teachers, facilities, currentYear, currentMonth)?.let {
                newSuggestions.add(it)
            }
        }

        // 限制每月最多3条新建议
        val toAdd = newSuggestions.shuffled().take(3)
        _suggestions.addAll(toAdd)

        // 保持总量上限（最多保留20条未处理建议）
        val currentPending = _suggestions.count { it.status == SuggestionStatus.PENDING }
        if (currentPending > 20) {
            val excess = currentPending - 20
            repeat(excess) {
                val oldest = _suggestions.firstOrNull { s -> s.status == SuggestionStatus.PENDING }
                oldest?.let { s -> s.status = SuggestionStatus.EXPIRED }
            }
        }

        updatePendingCount()
    }

    /**
     * 处理建议（校长采纳）
     * @return 处理结果消息
     */
    fun resolveSuggestion(suggestionId: Int): String {
        val suggestion = _suggestions.find { it.id == suggestionId } ?: return "建议不存在"
        if (suggestion.status != SuggestionStatus.PENDING) return "该建议已被处理"

        suggestion.status = SuggestionStatus.RESOLVED
        updatePendingCount()

        // 采纳建议的效果描述（实际数值由 GameEngine 月结算时根据已解决数量发放）
        val effect = when (suggestion.category) {
            SuggestionCategory.TEACHING_QUALITY -> "教学满意度+3%"
            SuggestionCategory.FACILITY -> "设施满意度+3%"
            SuggestionCategory.STUDENT_WELFARE -> "学生满意度+2%"
            SuggestionCategory.TEACHER_WELFARE -> "教师忠诚度+2%"
            SuggestionCategory.CAMPUS_SAFETY -> "校园安全+5"
            SuggestionCategory.FINANCIAL -> "财务效率+2%"
            SuggestionCategory.OTHER -> "声誉+5"
        }
        // 记录本月已采纳数量（供 GameEngine 发放奖励）
        _resolvedThisMonth++
        return "已采纳「${suggestion.title}」→ $effect"
    }

    /** 本月已采纳建议数（月底清零） */
    private var _resolvedThisMonth: Int = 0

    /** 消费本月采纳数（GameEngine 月结算时调用，清零并返回） */
    fun consumeResolvedCount(): Int {
        val count = _resolvedThisMonth
        _resolvedThisMonth = 0
        return count
    }

    /**
     * 忽略建议
     * @return 被忽略建议的惩罚信息
     */
    fun ignoreSuggestion(suggestionId: Int): SuggestionPenalty? {
        val suggestion = _suggestions.find { it.id == suggestionId } ?: return null
        if (suggestion.status != SuggestionStatus.PENDING) return null

        suggestion.status = SuggestionStatus.IGNORED
        updatePendingCount()
        return calculatePenalty(suggestion)
    }

    /**
     * 获取未处理的建议列表
     */
    fun getPendingSuggestions(): List<Suggestion> {
        return _suggestions.filter { it.status == SuggestionStatus.PENDING }
            .sortedByDescending { it.urgency }
    }

    /**
     * 获取所有建议（含历史）
     */
    fun getAllSuggestions(): List<Suggestion> {
        return _suggestions.sortedByDescending { it.createdYear * 12 + it.createdMonth }
    }

    /**
     * 计算本月因忽略建议而应施加的惩罚
     * 调用方需要将惩罚应用到对应的学生/教师
     */
    fun getMonthlyPenalties(): List<SuggestionPenalty> {
        return _suggestions
            .filter { it.status == SuggestionStatus.IGNORED || it.status == SuggestionStatus.EXPIRED }
            .filter { !it.penaltyApplied }
            .mapNotNull { suggestion ->
                suggestion.penaltyApplied = true
                calculatePenalty(suggestion)
            }
    }

    /**
     * 清理已处理超过3个月的历史记录
     */
    fun cleanupOldSuggestions(currentYear: Int, currentMonth: Int) {
        val currentAbsMonth = currentYear * 12 + currentMonth
        _suggestions.removeAll { suggestion ->
            val suggestionAbsMonth = suggestion.createdYear * 12 + suggestion.createdMonth
            val age = currentAbsMonth - suggestionAbsMonth
            age > 3 && suggestion.status != SuggestionStatus.PENDING
        }
    }

    // ======= 内部方法：检测各类问题并生成建议 =======

    private fun checkFacilityIssues(
        facilities: List<Facility>,
        students: List<Student>,
        teachers: List<Teacher>,
        year: Int, month: Int
    ): Suggestion? {
        // 检查设施状况（阈值60，让早期也能触发）
        val poorFacilities = facilities.filter { it.condition < 60 }
        if (poorFacilities.isNotEmpty()) {
            val facility = poorFacilities.random()
            val submitter = pickRandomSubmitter(students, teachers)
            return Suggestion(
                id = nextId++,
                title = "${facility.type.displayName}设施老化严重",
                content = "校长您好，${facility.type.displayName}的设备已经很老旧了，经常出故障影响正常使用，希望能尽快维修或更换。",
                category = SuggestionCategory.FACILITY,
                submitterName = submitter.first,
                submitterType = submitter.second,
                submitterId = submitter.third,
                isAnonymous = Random.nextFloat() < 0.3f,
                urgency = SuggestionUrgency.HIGH,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "设施状况: ${facility.condition}/100"
            )
        }

        // 检查是否缺少关键设施
        val hasLibrary = facilities.any { it.type == FacilityType.LIBRARY }
        val hasLab = facilities.any { it.type == FacilityType.LABORATORY }
        if (!hasLibrary && students.size > 15) {
            val submitter = pickRandomStudentSubmitter(students)
            return Suggestion(
                id = nextId++,
                title = "希望学校能建图书馆",
                content = "学校现在连个图书馆都没有，想自习都找不到安静的地方，希望学校能考虑建一个图书馆。",
                category = SuggestionCategory.FACILITY,
                submitterName = submitter.first,
                submitterType = SubmitterType.STUDENT,
                submitterId = submitter.second,
                isAnonymous = false,
                urgency = SuggestionUrgency.MEDIUM,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "学生人数: ${students.size}, 无图书馆"
            )
        }

        return null
    }

    private fun checkTeachingQuality(
        teachers: List<Teacher>,
        teacherAvgSkill: Float,
        students: List<Student>,
        year: Int, month: Int
    ): Suggestion? {
        // 教师水平偏低（阈值55，概率55%，早期更容易触发）
        val weakTeachers = teachers.filter { it.teaching < 55 }
        if (weakTeachers.isNotEmpty() && Random.nextFloat() < 0.55f) {
            val weakTeacher = weakTeachers.random()
            val submitter = pickRandomStudentSubmitter(students)
            return Suggestion(
                id = nextId++,
                title = "${weakTeacher.role.displayName}教学水平有待提高",
                content = "我们觉得${weakTeacher.name}老师上课经常照本宣科，很多同学都听不懂，希望学校能加强师资培训。",
                category = SuggestionCategory.TEACHING_QUALITY,
                submitterName = submitter.first,
                submitterType = SubmitterType.STUDENT,
                submitterId = submitter.second,
                isAnonymous = Random.nextFloat() < 0.5f,
                urgency = SuggestionUrgency.MEDIUM,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "教师教学: ${weakTeacher.teaching}/100"
            )
        }

        // 整体教学水平偏低（阈值55，概率45%）
        if (teacherAvgSkill < 55f && Random.nextFloat() < 0.45f) {
            val submitter = pickRandomSubmitter(students, teachers)
            return Suggestion(
                id = nextId++,
                title = "整体教学质量需要提升",
                content = "最近感觉学校的教学质量下降了不少，建议学校加大对教师培训的投入，引进更优秀的师资。",
                category = SuggestionCategory.TEACHING_QUALITY,
                submitterName = submitter.first,
                submitterType = submitter.second,
                submitterId = submitter.third,
                isAnonymous = true,
                urgency = SuggestionUrgency.HIGH,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "平均教学水平: ${teacherAvgSkill.toInt()}/100"
            )
        }

        return null
    }

    private fun checkStudentWelfare(
        students: List<Student>,
        avgSatisfaction: Float,
        year: Int, month: Int
    ): Suggestion? {
        // 学生满意度偏低（阈值60，比例20%即触发）
        val unhappyStudents = students.filter { it.satisfaction < 60f }
        if (unhappyStudents.size > students.size * 0.2f && students.isNotEmpty()) {
            val submitter = unhappyStudents.random()
            return Suggestion(
                id = nextId++,
                title = "学生学习压力太大",
                content = "最近很多同学都反映压力很大，考试太多休息时间太少，希望学校能适当减轻课业负担，增加课外活动时间。",
                category = SuggestionCategory.STUDENT_WELFARE,
                submitterName = submitter.name,
                submitterType = SubmitterType.STUDENT,
                submitterId = submitter.id,
                isAnonymous = Random.nextFloat() < 0.4f,
                urgency = SuggestionUrgency.MEDIUM,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "不满意学生比例: ${(unhappyStudents.size * 100 / students.size)}%"
            )
        }

        // 健康问题
        val sickStudents = students.filter { it.healthStatus != HealthStatus.HEALTHY }
        if (sickStudents.size > students.size * 0.2f && students.size > 5) {
            val submitter = pickRandomStudentSubmitter(students)
            return Suggestion(
                id = nextId++,
                title = "学生健康状况堪忧",
                content = "最近身体不舒服的同学越来越多了，建议学校重视学生健康管理，增加体育锻炼时间，改善食堂伙食。",
                category = SuggestionCategory.STUDENT_WELFARE,
                submitterName = submitter.first,
                submitterType = SubmitterType.STUDENT,
                submitterId = submitter.second,
                isAnonymous = false,
                urgency = SuggestionUrgency.HIGH,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "生病学生比例: ${(sickStudents.size * 100 / students.size)}%"
            )
        }

        return null
    }

    private fun checkTeacherWelfare(
        teachers: List<Teacher>,
        avgLoyalty: Float,
        year: Int, month: Int
    ): Suggestion? {
        // 教师薪资不满（阈值60，概率45%）
        val underpaidTeachers = teachers.filter { it.loyalty < 60 }
        if (underpaidTeachers.isNotEmpty() && Random.nextFloat() < 0.45f) {
            val teacher = underpaidTeachers.random()
            return Suggestion(
                id = nextId++,
                title = "教师待遇问题反映",
                content = "校长，我想反映一下教师待遇问题。目前薪资水平与工作量不匹配，很多同事都有怨言，建议学校适当调整薪酬结构。",
                category = SuggestionCategory.TEACHER_WELFARE,
                submitterName = teacher.name,
                submitterType = SubmitterType.TEACHER,
                submitterId = teacher.id,
                isAnonymous = Random.nextFloat() < 0.6f,  // 教师更倾向匿名
                urgency = SuggestionUrgency.HIGH,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "该教师忠诚度: ${teacher.loyalty}/100"
            )
        }

        // 教师疲劳问题
        val tiredTeachers = teachers.filter { it.fatigue > 70 }
        if (tiredTeachers.size > teachers.size * 0.4f && teachers.isNotEmpty()) {
            val teacher = tiredTeachers.random()
            return Suggestion(
                id = nextId++,
                title = "工作强度过大的反映",
                content = "最近工作量实在太大了，每天都加班到很晚，周末也经常需要来学校。建议学校合理安排教学任务，给教师更多休息时间。",
                category = SuggestionCategory.TEACHER_WELFARE,
                submitterName = teacher.name,
                submitterType = SubmitterType.TEACHER,
                submitterId = teacher.id,
                isAnonymous = Random.nextFloat() < 0.5f,
                urgency = SuggestionUrgency.MEDIUM,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "该教师疲劳度: ${teacher.fatigue}/100"
            )
        }

        return null
    }

    private fun checkFinancialIssues(
        schoolCash: Double,
        students: List<Student>,
        year: Int, month: Int
    ): Suggestion? {
        // 学费问题（通过学生满意度间接反映）
        val poorStudents = students.filter {
            it.backgroundTier == BackgroundTier.POOR && it.satisfaction < 65f
        }
        if (poorStudents.isNotEmpty() && Random.nextFloat() < 0.4f) {
            val student = poorStudents.random()
            return Suggestion(
                id = nextId++,
                title = "关于学费减免的请求",
                content = "校长您好，我家经济条件比较困难，学费对我们来说是很大的负担，想请问学校是否有助学金或学费减免政策？",
                category = SuggestionCategory.FINANCIAL,
                submitterName = student.name,
                submitterType = SubmitterType.STUDENT,
                submitterId = student.id,
                isAnonymous = false,
                urgency = SuggestionUrgency.LOW,
                createdYear = year,
                createdMonth = month,
                relatedInfo = "学生家庭背景: 困难"
            )
        }

        return null
    }

    private fun markOverdueSuggestions(currentYear: Int, currentMonth: Int) {
        val currentAbsMonth = currentYear * 12 + currentMonth
        _suggestions.filter { it.status == SuggestionStatus.PENDING }.forEach { suggestion ->
            val suggestionAbsMonth = suggestion.createdYear * 12 + suggestion.createdMonth
            // 超过2个月未处理的建议标记为过期
            if (currentAbsMonth - suggestionAbsMonth >= 2) {
                suggestion.status = SuggestionStatus.EXPIRED
            }
        }
    }

    private fun calculatePenalty(suggestion: Suggestion): SuggestionPenalty {
        val basePenalty = when (suggestion.urgency) {
            SuggestionUrgency.LOW -> 3f
            SuggestionUrgency.MEDIUM -> 5f
            SuggestionUrgency.HIGH -> 8f
            SuggestionUrgency.CRITICAL -> 12f
        }

        return SuggestionPenalty(
            suggestionId = suggestion.id,
            submitterId = suggestion.submitterId,
            submitterType = suggestion.submitterType,
            submitterName = if (suggestion.isAnonymous) "匿名" else suggestion.submitterName,
            penaltyAmount = basePenalty,
            description = "「${suggestion.title}」未被处理"
        )
    }

    private fun pickRandomSubmitter(
        students: List<Student>,
        teachers: List<Teacher>
    ): Triple<String, SubmitterType, String> {
        return if (Random.nextFloat() < 0.6f && students.isNotEmpty()) {
            val student = students.random()
            Triple(student.name, SubmitterType.STUDENT, student.id)
        } else if (teachers.isNotEmpty()) {
            val teacher = teachers.random()
            Triple(teacher.name, SubmitterType.TEACHER, teacher.id)
        } else {
            Triple("匿名", SubmitterType.STUDENT, "")
        }
    }

    private fun pickRandomStudentSubmitter(students: List<Student>): Pair<String, String> {
        if (students.isEmpty()) return Pair("匿名学生", "")
        val student = students.random()
        return Pair(student.name, student.id)
    }

    /**
     * 日常/保底建议生成
     * 即使学校没有明显问题，也能产出一些日常建议，让意见箱不至于完全空置
     */
    private fun generateRoutineSuggestion(
        students: List<Student>,
        teachers: List<Teacher>,
        facilities: List<Facility>,
        year: Int, month: Int
    ): Suggestion? {
        if (students.isEmpty() && teachers.isEmpty()) return null

        // 70% 概率生成日常建议（保底）
        if (Random.nextFloat() > 0.7f) return null

        data class RoutineTemplate(
            val title: String,
            val content: String,
            val category: SuggestionCategory,
            val urgency: SuggestionUrgency,
            val preferStudent: Boolean = true
        )

        val templates = mutableListOf(
            RoutineTemplate(
                "希望增加课外活动",
                "校长您好，我们希望学校能多组织一些课外活动，比如运动会、文艺汇演什么的，现在课余生活太单调了。",
                SuggestionCategory.STUDENT_WELFARE,
                SuggestionUrgency.LOW
            ),
            RoutineTemplate(
                "建议美化校园环境",
                "学校操场旁边光秃秃的，能不能种点树、放几把椅子？课间想出去坐坐都没地方。",
                SuggestionCategory.FACILITY,
                SuggestionUrgency.LOW
            ),
            RoutineTemplate(
                "食堂伙食能否改善",
                "食堂天天都是那几个菜，能不能换换花样？隔壁学校都有水果酸奶了，我们连个汤都不稳定供应。",
                SuggestionCategory.STUDENT_WELFARE,
                SuggestionUrgency.LOW
            ),
            RoutineTemplate(
                "建议开设兴趣社团",
                "我想参加编程社团/绘画社团，但是学校好像没有这方面的安排，希望学校能支持学生发展兴趣爱好。",
                SuggestionCategory.STUDENT_WELFARE,
                SuggestionUrgency.LOW
            ),
            RoutineTemplate(
                "教室空调/暖气问题",
                "天气越来越热（冷）了，教室里没有空调（暖气），上课很难集中注意力，建议学校改善教室环境。",
                SuggestionCategory.FACILITY,
                SuggestionUrgency.MEDIUM
            ),
            RoutineTemplate(
                "希望延长图书借阅时间",
                "现在图书馆借书只能借两周，根本看不完，建议延长到一个月，方便同学们多读书。",
                SuggestionCategory.OTHER,
                SuggestionUrgency.LOW
            ),
            RoutineTemplate(
                "建议增加自习室开放时间",
                "晚上想留校自习但教室关门太早了，建议学校延长自习室开放时间到晚上9点。",
                SuggestionCategory.STUDENT_WELFARE,
                SuggestionUrgency.LOW
            ),
            RoutineTemplate(
                "校服款式太丑了",
                "校长，说句实话，我们校服实在是太丑了……能不能重新设计一下？好看的校服同学们才愿意穿。",
                SuggestionCategory.OTHER,
                SuggestionUrgency.LOW,
                true
            ),
            RoutineTemplate(
                "建议增设心理咨询室",
                "有些同学心理压力大但不知道找谁倾诉，建议学校设立心理咨询室，配备专业心理老师。",
                SuggestionCategory.STUDENT_WELFARE,
                SuggestionUrgency.MEDIUM
            ),
            RoutineTemplate(
                "厕所卫生需要改善",
                "学校厕所经常没有纸，地面也很滑，希望学校能加强厕所的清洁和维护。",
                SuggestionCategory.FACILITY,
                SuggestionUrgency.LOW
            ),
            RoutineTemplate(
                "教师办公设备老旧",
                "校长，办公室的电脑已经用了好几年了，开机都要五分钟，严重影响备课效率，建议更换。",
                SuggestionCategory.TEACHER_WELFARE,
                SuggestionUrgency.LOW,
                false
            ),
            RoutineTemplate(
                "建议组织教师团建",
                "老师们平时工作忙，彼此交流不多，建议学校定期组织教师活动，增进同事感情。",
                SuggestionCategory.TEACHER_WELFARE,
                SuggestionUrgency.LOW,
                false
            ),
            RoutineTemplate(
                "关于校门口交通安全",
                "每天放学时校门口车特别多，很不安全，建议学校协调一下交通管理或者设置安全通道。",
                SuggestionCategory.CAMPUS_SAFETY,
                SuggestionUrgency.MEDIUM
            ),
            RoutineTemplate(
                "操场跑道需要翻新",
                "操场的跑道已经有好多地方开裂了，下雨天特别滑，跑步容易摔跤，请学校尽快修整。",
                SuggestionCategory.FACILITY,
                SuggestionUrgency.MEDIUM
            ),
            RoutineTemplate(
                "建议设立学生意见反馈日",
                "平时想跟学校反映问题不太方便，建议每月设一天让学生代表跟校长面对面交流。",
                SuggestionCategory.OTHER,
                SuggestionUrgency.LOW,
                true
            )
        )

        val template = templates.random()
        val submitter = if (template.preferStudent && students.isNotEmpty()) {
            val s = students.random()
            Triple(s.name, SubmitterType.STUDENT, s.id)
        } else if (!template.preferStudent && teachers.isNotEmpty()) {
            val t = teachers.random()
            Triple(t.name, SubmitterType.TEACHER, t.id)
        } else {
            pickRandomSubmitter(students, teachers)
        }

        return Suggestion(
            id = nextId++,
            title = template.title,
            content = template.content,
            category = template.category,
            submitterName = submitter.first,
            submitterType = submitter.second,
            submitterId = submitter.third,
            isAnonymous = Random.nextFloat() < 0.3f,
            urgency = template.urgency,
            createdYear = year,
            createdMonth = month,
            relatedInfo = "日常建议"
        )
    }

    // ======= 序列化支持 =======

    fun getState(): SuggestionBoxState {
        return SuggestionBoxState(
            suggestions = _suggestions.toList(),
            nextId = nextId,
            resolvedThisMonth = _resolvedThisMonth
        )
    }

    fun restoreState(state: SuggestionBoxState) {
        _suggestions.clear()
        _suggestions.addAll(state.suggestions)
        nextId = state.nextId
        _resolvedThisMonth = state.resolvedThisMonth
        updatePendingCount()
    }

    fun toJson(): String = Json.encodeToString(getState())

    fun restoreFromJson(json: String) {
        if (json.isBlank()) {
            restoreState(SuggestionBoxState())
            return
        }
        restoreState(Json.decodeFromString(json))
    }
}

// ======= 数据模型 =======

@Serializable
data class Suggestion(
    val id: Int,
    val title: String,
    val content: String,
    val category: SuggestionCategory,
    val submitterName: String,
    val submitterType: SubmitterType,
    val submitterId: String,
    val isAnonymous: Boolean,
    val urgency: SuggestionUrgency,
    val createdYear: Int,
    val createdMonth: Int,
    val relatedInfo: String = "",
    var status: SuggestionStatus = SuggestionStatus.PENDING,
    var penaltyApplied: Boolean = false
)

@Serializable
enum class SuggestionCategory(val displayName: String, val icon: String) {
    FACILITY("设施问题", "🏚️"),
    TEACHING_QUALITY("教学质量", "📚"),
    STUDENT_WELFARE("学生福利", "🎒"),
    TEACHER_WELFARE("教师待遇", "👨‍🏫"),
    FINANCIAL("财务问题", "💰"),
    CAMPUS_SAFETY("校园安全", "🛡️"),
    OTHER("其他", "📝")
}

@Serializable
enum class SubmitterType(val displayName: String) {
    STUDENT("学生"),
    TEACHER("教师")
}

@Serializable
enum class SuggestionUrgency(val displayName: String, val colorArgb: Int) {
    LOW("一般", 0xFF4CAF50.toInt()),       // 绿色
    MEDIUM("重要", 0xFFFF9800.toInt()),    // 橙色
    HIGH("紧急", 0xFFF44336.toInt()),      // 红色
    CRITICAL("危急", 0xFF9C27B0.toInt())   // 紫色
}

@Serializable
enum class SuggestionStatus(val displayName: String) {
    PENDING("待处理"),
    RESOLVED("已采纳"),
    IGNORED("已忽略"),
    EXPIRED("已过期")
}

data class SuggestionPenalty(
    val suggestionId: Int,
    val submitterId: String,
    val submitterType: SubmitterType,
    val submitterName: String,
    val penaltyAmount: Float,
    val description: String
)

@Serializable
data class SuggestionBoxState(
    val suggestions: List<Suggestion> = emptyList(),
    val nextId: Int = 1,
    val resolvedThisMonth: Int = 0
)
