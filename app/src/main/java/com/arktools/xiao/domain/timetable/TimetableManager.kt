package com.arktools.xiao.domain.timetable

import com.arktools.xiao.domain.model.ClassTier
import com.arktools.xiao.domain.model.GradeLevel
import com.arktools.xiao.domain.model.SchoolClass
import com.arktools.xiao.domain.model.Subject
import com.arktools.xiao.domain.model.Teacher
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课表管理器
 *
 * 每个班级根据年级和班型自动生成周课表（周一到周五，每天8节课）。
 * 课表基于已有教师的科目分配，确保教师不冲突。
 * 支持按班型差异化（特长班增加对应科目课时）和自定义课时调整。
 */
@Singleton
class TimetableManager @Inject constructor() {

    // 每天的课时数
    private val PERIODS_PER_DAY = 8
    // 每周的天数
    private val DAYS_PER_WEEK = 5

    // 课表缓存：classId -> 周课表
    private val timetables: MutableMap<String, WeeklyTimetable> = mutableMapOf()

    // 自定义课时覆盖：classId -> (Subject -> hours)，优先级最高
    private val customSubjectHours: MutableMap<String, MutableMap<Subject, Int>> = mutableMapOf()

    // 可配置的每周体育课时数（从教学配置读取）
    var configuredPEHours: Int = 2

    data class Snapshot(
        val timetables: Map<String, WeeklyTimetable>,
        val customSubjectHours: Map<String, Map<Subject, Int>>,
        val teacherAssignments: Map<String, Map<LessonTime, String>>,
        val configuredPEHours: Int
    )

    fun snapshotState(): Snapshot = Snapshot(
        timetables = timetables.mapValues { (_, timetable) ->
            timetable.copy(
                days = timetable.days.map { day ->
                    day.copy(periods = day.periods.map { it.copy() })
                }
            )
        },
        customSubjectHours = customSubjectHours.mapValues { it.value.toMap() },
        teacherAssignments = teacherAssignments.mapValues { it.value.toMap() },
        configuredPEHours = configuredPEHours
    )

    fun restoreSnapshot(snapshot: Snapshot) {
        timetables.clear()
        timetables.putAll(snapshot.timetables)
        customSubjectHours.clear()
        snapshot.customSubjectHours.forEach { (classId, hours) ->
            customSubjectHours[classId] = hours.toMutableMap()
        }
        teacherAssignments.clear()
        snapshot.teacherAssignments.forEach { (classId, assignments) ->
            teacherAssignments[classId] = assignments.toMutableMap()
        }
        configuredPEHours = snapshot.configuredPEHours
    }

    /**
     * 获取班级的周课表（如果不存在则生成）。
     * 单班生成会避开当前已缓存的其他班级课表；全校重排请使用 [regenerateAllTimetables]。
     */
    fun getTimetable(schoolClass: SchoolClass, teachers: List<Teacher>): WeeklyTimetable {
        return timetables[schoolClass.id]
            ?: regenerateTimetableInternal(
                schoolClass,
                teachers,
                headTeacherCounts = schoolClass.headTeacherId?.let { mapOf(it to 1) } ?: emptyMap()
            )
    }

    /**
     * 重新生成一个班级的课表（教师变动时调用）。
     *
     * 该兼容 API 只保证新生成的班级不与其余缓存课表发生教师撞课。
     * 需要同时重新平衡全校教师负载时应使用 [regenerateAllTimetables]。
     */
    fun regenerateTimetable(schoolClass: SchoolClass, teachers: List<Teacher>) {
        regenerateTimetableInternal(
            schoolClass,
            teachers,
            headTeacherCounts = schoolClass.headTeacherId?.let { mapOf(it to 1) } ?: emptyMap()
        )
    }

    /**
     * 一次性重排全校班级课表。
     *
     * 所有课表在同一个排课状态中生成：同一教师在同一星期/节次只会出现在一个班级。
     * 同科教师按当前已分配课时与学业导师工作量选择，避免总是使用列表中的第一位教师。
     *
     * @return 新生成的全部课表快照
     */
    fun regenerateAllTimetables(
        classes: Collection<SchoolClass>,
        teachers: List<Teacher>
    ): Map<String, WeeklyTimetable> {
        val activeClasses = classes.distinctBy { it.id }.sortedBy { it.id }
        val headTeacherCounts = activeClasses
            .mapNotNull { it.headTeacherId }
            .groupingBy { it }
            .eachCount()
        val state = SchedulingState()
        val generated = linkedMapOf<String, WeeklyTimetable>()

        activeClasses.forEach { schoolClass ->
            val timetable = generateTimetable(schoolClass, teachers, state, headTeacherCounts)
            generated[schoolClass.id] = timetable
        }

        timetables.clear()
        timetables.putAll(generated)
        teacherAssignments.clear()
        state.assignmentsByClass.forEach { (classId, assignments) ->
            teacherAssignments[classId] = assignments.toMutableMap()
        }
        return getAllTimetables()
    }

    /** 获取所有已生成的课表。 */
    fun getAllTimetables(): Map<String, WeeklyTimetable> = timetables.toMap()

    /**
     * 删除一个班级的课表及其自定义课时配置。
     * 班级被删除后调用，防止旧缓存参与后续排课或写回存档。
     */
    fun removeClassTimetable(classId: String): Boolean {
        val removedTimetable = timetables.remove(classId) != null
        val removedAssignments = teacherAssignments.remove(classId) != null
        val removedCustomHours = customSubjectHours.remove(classId) != null
        return removedTimetable || removedAssignments || removedCustomHours
    }

    /**
     * 清理不再存在的班级数据（课表缓存和自定义课时配置）。
     * @return 已清理的班级 ID
     */
    fun pruneDeletedClassTimetables(classes: Collection<SchoolClass>): Set<String> {
        val validClassIds = classes.mapTo(mutableSetOf()) { it.id }
        val obsoleteClassIds = (timetables.keys + customSubjectHours.keys)
            .filterTo(mutableSetOf()) { it !in validClassIds }
        obsoleteClassIds.forEach(::removeClassTimetable)
        return obsoleteClassIds
    }

    /** 只清除已生成的课表缓存，保留用户设置的自定义课时配置。 */
    fun clearTimetableCache() {
        timetables.clear()
        teacherAssignments.clear()
    }

    /**
     * 列出当前所有跨班教师撞课，供 UI 提示和单元测试验证。
     * “待聘”、空教师和自习不视为教师占用。
     */
    fun listConflicts(): List<TimetableConflict> {
        val assignments = mutableMapOf<LessonTime, MutableMap<String, MutableSet<String>>>()
        val displayNames = mutableMapOf<String, String>()
        timetables.forEach { (classId, timetable) ->
            timetable.days.forEach { day ->
                day.periods.forEachIndexed { periodIndex, slot ->
                    val teacherName = slot.teacherName ?: return@forEachIndexed
                    if (!isAssignedTeacher(teacherName)) return@forEachIndexed
                    val time = LessonTime(day.dayOfWeek, periodIndex)
                    // 新生成课表使用教师 ID，避免重名但不同人的误报；旧 JSON 无 ID 时按姓名保守检查。
                    val teacherKey = teacherAssignments[classId]?.get(time) ?: "name:$teacherName"
                    displayNames.putIfAbsent(teacherKey, teacherName)
                    assignments
                        .getOrPut(time) { mutableMapOf() }
                        .getOrPut(teacherKey) { mutableSetOf() }
                        .add(classId)
                }
            }
        }
        return assignments.flatMap { (time, teacherClasses) ->
            teacherClasses
                .filterValues { it.size > 1 }
                .map { (teacherKey, classIds) ->
                    TimetableConflict(
                        teacherName = displayNames[teacherKey] ?: teacherKey.removePrefix("name:"),
                        dayOfWeek = time.dayOfWeek,
                        period = time.period,
                        classIds = classIds.sorted()
                    )
                }
        }.sortedWith(compareBy<TimetableConflict> { it.dayOfWeek }.thenBy { it.period }.thenBy { it.teacherName })
    }

    /**
     * 交换同一班级课表中两个时段的课程（调课）。
     * 交换后若任一教师会与其他班级同一时段撞课，则拒绝本次交换。
     * @return 交换后的新课表，如果参数无效或会造成撞课则返回 null
     */
    fun swapSlots(classId: String, dayA: Int, periodA: Int, dayB: Int, periodB: Int): WeeklyTimetable? {
        val timetable = timetables[classId] ?: return null
        if (dayA == dayB && periodA == periodB) return timetable // 同一格，不操作

        val days = timetable.days.toMutableList()
        val dayAIndex = days.indexOfFirst { it.dayOfWeek == dayA }
        val dayBIndex = days.indexOfFirst { it.dayOfWeek == dayB }
        if (dayAIndex < 0 || dayBIndex < 0) return null

        val slotsA = days[dayAIndex].periods.toMutableList()
        val slotsB = days[dayBIndex].periods.toMutableList()
        if (periodA !in slotsA.indices || periodB !in slotsB.indices) return null

        if (dayAIndex == dayBIndex) {
            val temp = slotsA[periodA]
            slotsA[periodA] = slotsA[periodB]
            slotsA[periodB] = temp
            days[dayAIndex] = days[dayAIndex].copy(periods = slotsA)
        } else {
            val temp = slotsA[periodA]
            slotsA[periodA] = slotsB[periodB]
            slotsB[periodB] = temp
            days[dayAIndex] = days[dayAIndex].copy(periods = slotsA)
            days[dayBIndex] = days[dayBIndex].copy(periods = slotsB)
        }

        val updated = timetable.copy(days = days)
        val updatedAssignments = teacherAssignments[classId].orEmpty().toMutableMap().apply {
            val timeA = LessonTime(dayA, periodA)
            val timeB = LessonTime(dayB, periodB)
            val teacherA = this[timeA]
            val teacherB = this[timeB]
            if (teacherB == null) remove(timeA) else this[timeA] = teacherB
            if (teacherA == null) remove(timeB) else this[timeB] = teacherA
        }
        if (hasCrossClassTeacherConflict(updated, classId, updatedAssignments)) return null

        timetables[classId] = updated
        teacherAssignments[classId] = updatedAssignments
        return updated
    }

    /**
     * 根据年级和班型获取标准课时分配
     * PE 课时使用教学配置中的 configuredPEHours，大四减少1节
     * 特长班会强化对应科目并适度减少部分文化课
     */
    private fun getSubjectHoursForGrade(gradeLevel: GradeLevel, classTier: ClassTier): Map<Subject, Int> {
        val peHours = when (gradeLevel) {
            GradeLevel.GRADE_1 -> configuredPEHours.coerceIn(1, 5)
            GradeLevel.GRADE_2 -> (configuredPEHours - 1).coerceIn(1, 4)
            GradeLevel.GRADE_3 -> (configuredPEHours - 2).coerceIn(1, 3)
            GradeLevel.GRADE_4 -> 1
        }
        val base = when (gradeLevel) {
            GradeLevel.GRADE_1 -> mutableMapOf(
                Subject.CHINESE to 6, Subject.MATH to 6, Subject.ENGLISH to 5,
                Subject.PHYSICS to 4, Subject.CHEMISTRY to 3, Subject.BIOLOGY to 3,
                Subject.HISTORY to 3, Subject.GEOGRAPHY to 2, Subject.POLITICS to 2,
                Subject.PE to peHours, Subject.ART to 2, Subject.MUSIC to 1
            )
            GradeLevel.GRADE_4 -> mutableMapOf(
                Subject.CHINESE to 2, Subject.MATH to 2, Subject.ENGLISH to 3,
                Subject.PE to 1
            )
            GradeLevel.GRADE_2 -> mutableMapOf(
                Subject.CHINESE to 6, Subject.MATH to 7, Subject.ENGLISH to 5,
                Subject.PHYSICS to 4, Subject.CHEMISTRY to 4, Subject.BIOLOGY to 3,
                Subject.HISTORY to 3, Subject.GEOGRAPHY to 2, Subject.POLITICS to 2,
                Subject.PE to peHours, Subject.ART to 1, Subject.MUSIC to 1
            )
            GradeLevel.GRADE_3 -> mutableMapOf(
                Subject.CHINESE to 7, Subject.MATH to 7, Subject.ENGLISH to 6,
                Subject.PHYSICS to 5, Subject.CHEMISTRY to 4, Subject.BIOLOGY to 3,
                Subject.HISTORY to 3, Subject.GEOGRAPHY to 2, Subject.POLITICS to 2,
                Subject.PE to peHours
            )
        }

        // 根据教学班调整课时：方向班强化对应科目，拔尖/核心班强化主科
        when (classTier) {
            ClassTier.ART -> {
                base[Subject.ART] = (base.getOrDefault(Subject.ART, 0) + 3).coerceAtMost(6)
                base[Subject.GEOGRAPHY] = (base.getOrDefault(Subject.GEOGRAPHY, 0) - 1).coerceAtLeast(0)
                base[Subject.POLITICS] = (base.getOrDefault(Subject.POLITICS, 0) - 1).coerceAtLeast(0)
            }
            ClassTier.MUSIC -> {
                base[Subject.MUSIC] = (base.getOrDefault(Subject.MUSIC, 0) + 3).coerceAtMost(6)
                base[Subject.GEOGRAPHY] = (base.getOrDefault(Subject.GEOGRAPHY, 0) - 1).coerceAtLeast(0)
                base[Subject.POLITICS] = (base.getOrDefault(Subject.POLITICS, 0) - 1).coerceAtLeast(0)
            }
            ClassTier.SPORTS -> {
                base[Subject.PE] = (base.getOrDefault(Subject.PE, 0) + 4).coerceAtMost(8)
                base[Subject.HISTORY] = (base.getOrDefault(Subject.HISTORY, 0) - 1).coerceAtLeast(1)
                base[Subject.GEOGRAPHY] = (base.getOrDefault(Subject.GEOGRAPHY, 0) - 1).coerceAtLeast(0)
            }
            ClassTier.ROCKET -> {
                base[Subject.MATH] = (base.getOrDefault(Subject.MATH, 0) + 1).coerceAtMost(8)
                base[Subject.PHYSICS] = (base.getOrDefault(Subject.PHYSICS, 0) + 1).coerceAtMost(7)
                base[Subject.MUSIC] = (base.getOrDefault(Subject.MUSIC, 0) - 1).coerceAtLeast(0)
            }
            ClassTier.KEY -> {
                base[Subject.CHINESE] = (base.getOrDefault(Subject.CHINESE, 0) + 1).coerceAtMost(8)
                base[Subject.ENGLISH] = (base.getOrDefault(Subject.ENGLISH, 0) + 1).coerceAtMost(7)
                base[Subject.MUSIC] = (base.getOrDefault(Subject.MUSIC, 0) - 1).coerceAtLeast(0)
            }
            ClassTier.NORMAL -> { /* 标准分配 */ }
        }

        return base.filter { it.value > 0 }
    }

    /**
     * 获取班级最终课时分配：优先使用自定义覆盖，否则按班型生成
     */
    fun getSubjectHoursForClass(schoolClass: SchoolClass): Map<Subject, Int> {
        val custom = customSubjectHours[schoolClass.id]
        if (!custom.isNullOrEmpty()) {
            return custom.toMap()
        }
        return getSubjectHoursForGrade(schoolClass.gradeLevel, schoolClass.classTier)
    }

    /**
     * 设置班级的自定义课时分配
     * @return 是否设置成功（总课时不得超过每周总课时）
     */
    fun setCustomSubjectHours(classId: String, hours: Map<Subject, Int>): Boolean {
        val total = hours.values.sum()
        if (total > PERIODS_PER_DAY * DAYS_PER_WEEK) return false
        customSubjectHours[classId] = hours.toMutableMap()
        return true
    }

    /**
     * 重置班级的自定义课时，恢复按班型自动分配
     */
    fun resetCustomSubjectHours(classId: String) {
        customSubjectHours.remove(classId)
    }

    /**
     * 获取班级的自定义课时（无则为空）
     */
    fun getCustomSubjectHours(classId: String): Map<Subject, Int> {
        return customSubjectHours[classId]?.toMap() ?: emptyMap()
    }

    /**
     * 在不改变持久化结构的前提下，保存运行期的教师 ID 分配。
     * JSON 中仍只保存 teacherName；从旧存档恢复后无法唯一识别重名教师时，会保守地按姓名检查冲突。
     */
    private val teacherAssignments: MutableMap<String, MutableMap<LessonTime, String>> = mutableMapOf()

    /**
     * 学业导师每带一个班，排课时视为额外承担四节常规课的工作量。
     * 这是优先级折减而非硬上限：师资紧缺时仍会安排其授课，并不会无故产生“待聘”。
     */
    private val HEAD_TEACHER_LOAD_PENALTY = 4

    private fun regenerateTimetableInternal(
        schoolClass: SchoolClass,
        teachers: List<Teacher>,
        headTeacherCounts: Map<String, Int>
    ): WeeklyTimetable {
        // 重新生成时先移除本班旧占用，再以其余缓存课表作为不可冲突的排课基础。
        timetables.remove(schoolClass.id)
        teacherAssignments.remove(schoolClass.id)
        val state = schedulingStateFromCachedTimetables(teachers)
        val timetable = generateTimetable(schoolClass, teachers, state, headTeacherCounts)
        timetables[schoolClass.id] = timetable
        teacherAssignments[schoolClass.id] = state.assignmentsByClass[schoolClass.id]?.toMutableMap() ?: mutableMapOf()
        return timetable
    }

    /**
     * 生成周课表，并在 [state] 中即时登记教师占用，供随后班级避开。
     */
    private fun generateTimetable(
        schoolClass: SchoolClass,
        teachers: List<Teacher>,
        state: SchedulingState,
        headTeacherCounts: Map<String, Int>
    ): WeeklyTimetable {
        val totalSlots = PERIODS_PER_DAY * DAYS_PER_WEEK
        val lessons = getSubjectHoursForClass(schoolClass)
            .toSortedMap(compareBy { it.name })
            .flatMap { (subject, hours) -> List(hours.coerceAtLeast(0)) { subject } }
            .take(totalSlots)
            .shuffled()
        val times = (1..DAYS_PER_WEEK)
            .flatMap { day -> (0 until PERIODS_PER_DAY).map { period -> LessonTime(day, period) } }
            .shuffled()
        val slotsByTime = mutableMapOf<LessonTime, TimetableSlot>()

        lessons.forEachIndexed { index, subject ->
            val time = times[index]
            val teacher = chooseTeacher(subject, teachers, state, time, headTeacherCounts)
            if (teacher == null) {
                slotsByTime[time] = TimetableSlot(subject = subject, teacherName = "待聘")
            } else {
                slotsByTime[time] = TimetableSlot(subject = subject, teacherName = teacher.name)
                state.reserve(schoolClass.id, time, teacher.id)
            }
        }

        times.drop(lessons.size).forEach { time ->
            slotsByTime[time] = TimetableSlot(subject = null, teacherName = null)
        }

        return WeeklyTimetable(
            classId = schoolClass.id,
            className = schoolClass.displayName,
            gradeLevel = schoolClass.gradeLevel,
            days = (1..DAYS_PER_WEEK).map { day ->
                DaySchedule(
                    dayOfWeek = day,
                    periods = (0 until PERIODS_PER_DAY).map { period ->
                        slotsByTime.getValue(LessonTime(day, period))
                    }
                )
            }
        )
    }

    /**
     * 仅从在岗且未休假的同科教师中选择候选人。
     * 先比较实际已排课时，再叠加学业导师折减，最后按 ID 固定排序以保证平局时的稳定结果。
     */
    private fun chooseTeacher(
        subject: Subject,
        teachers: List<Teacher>,
        state: SchedulingState,
        time: LessonTime,
        headTeacherCounts: Map<String, Int>
    ): Teacher? {
        return teachers.asSequence()
            .filter { it.role.name == subject.name }
            .filter { it.isWorking && !it.isOnVacation }
            .filterNot { state.isBusy(it.id, time) }
            .sortedWith(
                compareBy<Teacher> {
                    state.teacherLessonCounts.getOrDefault(it.id, 0) +
                        headTeacherCounts.getOrDefault(it.id, 0) * HEAD_TEACHER_LOAD_PENALTY
                }.thenBy { state.teacherLessonCounts.getOrDefault(it.id, 0) }
                    .thenBy { it.id }
            )
            .firstOrNull()
    }

    /** 从其余已缓存课表恢复教师占用，确保单班重排不会引入新的跨班撞课。 */
    private fun schedulingStateFromCachedTimetables(teachers: List<Teacher>): SchedulingState {
        val state = SchedulingState()
        timetables.forEach { (classId, timetable) ->
            timetable.days.forEach { day ->
                day.periods.forEachIndexed { period, slot ->
                    val teacherName = slot.teacherName ?: return@forEachIndexed
                    if (!isAssignedTeacher(teacherName)) return@forEachIndexed
                    val time = LessonTime(day.dayOfWeek, period)
                    val knownTeacherId = teacherAssignments[classId]?.get(time)
                    val teacherIds = knownTeacherId?.let(::listOf)
                        ?: teacherIdsForPersistedSlot(slot, teachers)
                    teacherIds.forEach { teacherId -> state.reserve(classId, time, teacherId) }
                }
            }
        }
        return state
    }

    /**
     * 旧 JSON 只保存姓名。姓名和科目能唯一匹配时恢复该教师 ID；
     * 若有重名教师则保守地占用所有同名候选人，宁可待聘也不制造潜在撞课。
     */
    private fun teacherIdsForPersistedSlot(slot: TimetableSlot, teachers: List<Teacher>): List<String> {
        val teacherName = slot.teacherName ?: return emptyList()
        return teachers.asSequence()
            .filter { it.name == teacherName }
            .filter { slot.subject == null || it.role.name == slot.subject.name }
            .map { it.id }
            .toList()
    }

    private fun isAssignedTeacher(teacherName: String): Boolean {
        return teacherName.isNotBlank() && teacherName != "待聘"
    }

    /**
     * 校验候选课表是否会与其余班级在同一时段占用同一教师。
     * 对无法从旧 JSON 唯一恢复 ID 的记录，按同名教师作保守判定。
     */
    private fun hasCrossClassTeacherConflict(
        candidate: WeeklyTimetable,
        classId: String,
        candidateAssignments: Map<LessonTime, String> = teacherAssignments[classId].orEmpty()
    ): Boolean {
        candidate.days.forEach { day ->
            day.periods.forEachIndexed { period, slot ->
                val teacherName = slot.teacherName ?: return@forEachIndexed
                if (!isAssignedTeacher(teacherName)) return@forEachIndexed
                val time = LessonTime(day.dayOfWeek, period)
                val candidateTeacherId = candidateAssignments[time]

                timetables.forEach { (otherClassId, otherTimetable) ->
                    if (otherClassId == classId) return@forEach
                    val otherSlot = otherTimetable.days
                        .firstOrNull { it.dayOfWeek == day.dayOfWeek }
                        ?.periods
                        ?.getOrNull(period)
                        ?: return@forEach
                    val otherTeacherName = otherSlot.teacherName ?: return@forEach
                    if (!isAssignedTeacher(otherTeacherName)) return@forEach
                    val otherTeacherId = teacherAssignments[otherClassId]?.get(time)

                    val sameTeacher = when {
                        candidateTeacherId != null && otherTeacherId != null -> candidateTeacherId == otherTeacherId
                        else -> teacherName == otherTeacherName
                    }
                    if (sameTeacher) return true
                }
            }
        }
        return false
    }

    private data class SchedulingState(
        val busyTeachers: MutableMap<LessonTime, MutableSet<String>> = mutableMapOf(),
        val teacherLessonCounts: MutableMap<String, Int> = mutableMapOf(),
        val assignmentsByClass: MutableMap<String, MutableMap<LessonTime, String>> = mutableMapOf()
    ) {
        fun isBusy(teacherId: String, time: LessonTime): Boolean = teacherId in busyTeachers[time].orEmpty()

        fun reserve(classId: String, time: LessonTime, teacherId: String) {
            busyTeachers.getOrPut(time) { mutableSetOf() }.add(teacherId)
            teacherLessonCounts[teacherId] = teacherLessonCounts.getOrDefault(teacherId, 0) + 1
            assignmentsByClass.getOrPut(classId) { mutableMapOf() }[time] = teacherId
        }
    }

    // ======= 持久化 =======

    fun toJson(): String {
        val data = TimetableData(
            timetables = timetables.mapValues { (_, tt) ->
                SerializableTimetable(
                    classId = tt.classId,
                    className = tt.className,
                    gradeLevelName = tt.gradeLevel.name,
                    days = tt.days.map { day ->
                        SerializableDay(
                            dayOfWeek = day.dayOfWeek,
                            periods = day.periods.map { slot ->
                                SerializableSlot(
                                    subjectName = slot.subject?.name,
                                    teacherName = slot.teacherName
                                )
                            }
                        )
                    }
                )
            },
            customHours = customSubjectHours.mapValues { (_, hours) ->
                hours.mapKeys { it.key.name }
            }
        )
        return Json.encodeToString(data)
    }

    fun fromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json.decodeFromString<TimetableData>(json)
            val restoredTimetables = data.timetables.mapValues { (_, st) ->
                val gradeLevel = try {
                    GradeLevel.valueOf(st.gradeLevelName)
                } catch (_: Exception) {
                    GradeLevel.GRADE_1
                }
                WeeklyTimetable(
                    classId = st.classId,
                    className = st.className,
                    gradeLevel = gradeLevel,
                    days = st.days.map { day ->
                        DaySchedule(
                            dayOfWeek = day.dayOfWeek,
                            periods = day.periods.map { slot ->
                                TimetableSlot(
                                    subject = slot.subjectName?.let { name ->
                                        try {
                                            Subject.valueOf(name)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    },
                                    teacherName = slot.teacherName
                                )
                            }
                        )
                    }
                )
            }
            val restoredCustomHours = data.customHours.orEmpty().mapNotNull { (classId, hours) ->
                val parsed = hours.mapNotNull { (name, count) ->
                    try {
                        Subject.valueOf(name) to count
                    } catch (_: Exception) {
                        null
                    }
                }.toMap()
                parsed.takeIf { it.isNotEmpty() }?.let { classId to it.toMutableMap() }
            }.toMap()

            timetables.clear()
            timetables.putAll(restoredTimetables)
            teacherAssignments.clear()
            customSubjectHours.clear()
            customSubjectHours.putAll(restoredCustomHours)
        } catch (e: Exception) {
            throw IllegalArgumentException("TimetableManager.fromJson failed", e)
        }
    }
}

// ======= 数据模型 =======

/** 课表中的唯一时间位置，period 使用 0-based 索引。 */
data class LessonTime(
    val dayOfWeek: Int,
    val period: Int
)

/** 同一教师在同一时间被多个班级占用的诊断信息。 */
data class TimetableConflict(
    val teacherName: String,
    val dayOfWeek: Int,
    val period: Int,
    val classIds: List<String>
)

data class WeeklyTimetable(
    val classId: String,
    val className: String,
    val gradeLevel: GradeLevel,
    val days: List<DaySchedule>
)

data class DaySchedule(
    val dayOfWeek: Int, // 1=周一, 5=周五
    val periods: List<TimetableSlot>
) {
    val dayName: String get() = when (dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"
        else -> "周$dayOfWeek"
    }
}

data class TimetableSlot(
    val subject: Subject?,       // null = 自习
    val teacherName: String?     // null = 自习
) {
    val displayName: String get() = subject?.displayName ?: "自习"
}

// ======= 序列化用 =======

@Serializable
data class TimetableData(
    val timetables: Map<String, SerializableTimetable> = emptyMap(),
    val customHours: Map<String, Map<String, Int>>? = emptyMap()
)

@Serializable
data class SerializableTimetable(
    val classId: String,
    val className: String,
    val gradeLevelName: String,
    val days: List<SerializableDay>
)

@Serializable
data class SerializableDay(
    val dayOfWeek: Int,
    val periods: List<SerializableSlot>
)

@Serializable
data class SerializableSlot(
    val subjectName: String?,
    val teacherName: String?
)
