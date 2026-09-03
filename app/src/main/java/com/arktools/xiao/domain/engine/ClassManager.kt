package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 班级管理引擎
 *
 * 负责:
 * - 新生编入教学班（按策略将新入学学生分配到班级）
 * - 班级指标聚合（从学生五维计算班级整体属性）
 * - 年级排名计算
 * - 班级事件触发
 * - 学年末升级/留级/毕业判定
 * - 学业导师分配效果计算
 */
@Singleton
class ClassManager @Inject constructor() {

    // ======= 核心: 班级列表 (StateFlow in GameEngine) =======

    /**
     * 为新入学学生执行编入教学班
     * 在每月招生后调用，将未编入教学班的学生分配到对应年级的班级中
     *
     * @param unassignedStudents 未分配班级的新生
     * @param existingClasses 现有班级列表
     * @param strategy 编入教学班策略
     * @param schoolId 学校ID
     * @param currentYear 当前年份
     * @param currentMonth 当前月份
     * @param gradeDistribution 该年级应有的各班型班级数，如 {KEY:1, NORMAL:2}
     * @return 学生→班级ID映射
     */
    fun assignNewStudents(
        unassignedStudents: List<Student>,
        existingClasses: MutableList<SchoolClass>,
        strategy: ClassStrategy,
        schoolId: String,
        currentYear: Int,
        currentMonth: Int,
        gradeDistribution: Map<ClassTier, Int>
    ): Map<String, String> {
        if (unassignedStudents.isEmpty()) return emptyMap()

        val studentClassMap = mutableMapOf<String, String>()

        // 按年级分组（新生默认大一）
        val grade1Students = unassignedStudents.filter { it.gradeLevel == GradeLevel.GRADE_1 }

        // 获取大一班级
        val grade1Classes = existingClasses.filter { it.gradeLevel == GradeLevel.GRADE_1 }

        // 确保按班型配置创建足够班级
        val targetClasses = ensureCapacityByTier(
            gradeClasses = grade1Classes.toMutableList(),
            allClasses = existingClasses,
            schoolId = schoolId,
            gradeLevel = GradeLevel.GRADE_1,
            currentYear = currentYear,
            currentMonth = currentMonth,
            gradeDistribution = gradeDistribution
        )

        // 按策略编入教学班
        when (strategy) {
            ClassStrategy.RANDOM -> assignRandom(grade1Students, targetClasses, studentClassMap)
            ClassStrategy.BALANCED -> assignBalanced(grade1Students, targetClasses, studentClassMap)
            ClassStrategy.STREAMED -> assignStreamed(grade1Students, targetClasses, studentClassMap)
            ClassStrategy.SUBJECT_BASED -> assignBySubject(grade1Students, targetClasses, studentClassMap)
        }

        return studentClassMap
    }

    /**
     * 按班型配置确保年级内有足够的班级
     * 如果现有班级不够，按 gradeDistribution 补足缺少的班型班级
     */
    private fun ensureCapacityByTier(
        gradeClasses: MutableList<SchoolClass>,
        allClasses: MutableList<SchoolClass>,
        schoolId: String,
        gradeLevel: GradeLevel,
        currentYear: Int,
        currentMonth: Int,
        gradeDistribution: Map<ClassTier, Int>
    ): MutableList<SchoolClass> {
        val maxExistingNumber = gradeClasses.maxOfOrNull { it.classNumber } ?: 0
        var nextNumber = maxExistingNumber + 1

        // 按班型逐一检查，如果该班型现有班级数不够则补足
        for ((tier, requiredCount) in gradeDistribution) {
            val existingOfTier = gradeClasses.count { it.classTier == tier }
            val needed = (requiredCount - existingOfTier).coerceAtLeast(0)
            repeat(needed) {
                val newClass = SchoolClass(
                    schoolId = schoolId,
                    gradeLevel = gradeLevel,
                    classNumber = nextNumber++,
                    classTier = tier,
                    createdYear = currentYear,
                    createdMonth = currentMonth
                )
                gradeClasses.add(newClass)
                allClasses.add(newClass)
            }
        }

        return gradeClasses
    }

    // ======= 编入教学班策略实现 =======

    private fun assignRandom(
        students: List<Student>,
        classes: List<SchoolClass>,
        mapping: MutableMap<String, String>
    ) {
        if (classes.isEmpty()) return
        val shuffled = students.shuffled()
        var classIndex = 0
        for (student in shuffled) {
            // 找到下一个有空位的班级
            while (classIndex < classes.size && classes[classIndex].isFull) {
                classIndex++
            }
            if (classIndex >= classes.size) {
                // 循环回第一个
                classIndex = 0
                while (classIndex < classes.size && classes[classIndex].isFull) classIndex++
                if (classIndex >= classes.size) break // 所有班级满了
            }
            val targetClass = classes[classIndex]
            mapping[student.id] = targetClass.id
            targetClass.studentCount++
            classIndex = (classIndex + 1) % classes.size
        }
    }

    private fun assignBalanced(
        students: List<Student>,
        classes: List<SchoolClass>,
        mapping: MutableMap<String, String>
    ) {
        if (classes.isEmpty()) return
        // 按五维均分从高到低排序，然后蛇形分配（高→低→低→高...）保证均衡
        val sorted = students.sortedByDescending { it.attributes.averageScore }
        var forward = true
        var classIndex = 0

        for (student in sorted) {
            // 跳过满员班级
            var attempts = 0
            while (classes[classIndex].isFull && attempts < classes.size) {
                classIndex = if (forward) {
                    (classIndex + 1) % classes.size
                } else {
                    (classIndex - 1 + classes.size) % classes.size
                }
                attempts++
            }
            if (attempts >= classes.size) break

            mapping[student.id] = classes[classIndex].id
            classes[classIndex].studentCount++

            // 蛇形移动
            if (forward) {
                classIndex++
                if (classIndex >= classes.size) {
                    classIndex = classes.size - 1
                    forward = false
                }
            } else {
                classIndex--
                if (classIndex < 0) {
                    classIndex = 0
                    forward = true
                }
            }
        }
    }

    private fun assignStreamed(
        students: List<Student>,
        classes: List<SchoolClass>,
        mapping: MutableMap<String, String>
    ) {
        if (classes.isEmpty()) return
        // 只有1个班时退化为均衡分配（无法分层）
        if (classes.size == 1) {
            assignBalanced(students, classes, mapping)
            return
        }

        // 分层: 按学业基础排序，前30%进核心班，其余均分到通识班
        val sorted = students.sortedByDescending { it.attributes.intelligence }
        val topTierCount = (sorted.size * 0.3f).toInt().coerceAtLeast(1)

        val topTier = sorted.take(topTierCount)
        val normalTier = sorted.drop(topTierCount)

        // 核心班(第1个班)
        for (student in topTier) {
            if (classes[0].isFull) break
            mapping[student.id] = classes[0].id
            classes[0].studentCount++
        }

        // 通识班(第2个班起)
        val normalClasses = classes.subList(1, classes.size)
        var idx = 0
        for (student in normalTier) {
            if (student.id in mapping) continue
            var attempts = 0
            while (normalClasses[idx].isFull && attempts < normalClasses.size) {
                idx = (idx + 1) % normalClasses.size
                attempts++
            }
            if (attempts >= normalClasses.size) break
            mapping[student.id] = normalClasses[idx].id
            normalClasses[idx].studentCount++
            idx = (idx + 1) % normalClasses.size
        }

        // 核心班溢出的学生也分配到通识班
        for (student in topTier) {
            if (student.id !in mapping) {
                var attempts = 0
                while (normalClasses[idx].isFull && attempts < normalClasses.size) {
                    idx = (idx + 1) % normalClasses.size
                    attempts++
                }
                if (attempts >= normalClasses.size) break
                mapping[student.id] = normalClasses[idx].id
                normalClasses[idx].studentCount++
                idx = (idx + 1) % normalClasses.size
            }
        }
    }

    private fun assignBySubject(
        students: List<Student>,
        classes: List<SchoolClass>,
        mapping: MutableMap<String, String>
    ) {
        if (classes.isEmpty()) return
        // 按最强维度分组，然后将同一特长的学生尽量放同一班
        val grouped = students.groupBy { it.attributes.strongestDimension }
        var classIndex = 0

        for ((_, group) in grouped) {
            for (student in group) {
                var attempts = 0
                while (classes[classIndex].isFull && attempts < classes.size) {
                    classIndex = (classIndex + 1) % classes.size
                    attempts++
                }
                if (attempts >= classes.size) break
                mapping[student.id] = classes[classIndex].id
                classes[classIndex].studentCount++
            }
            // 下一个特长组换班级
            classIndex = (classIndex + 1) % classes.size
        }
    }

    // ======= 班级指标更新 =======

    /**
     * 每月更新所有班级的聚合指标
     * 从班级内学生的五维属性计算班级整体属性
     */
    fun updateClassMetrics(
        classes: List<SchoolClass>,
        allStudents: List<Student>,
        teachers: List<Teacher>,
        officersByClass: Map<String, Map<ClassOfficerRole, ClassOfficer>> = emptyMap()
    ) {
        val studentsByClass = allStudents
            .filter { it.classId != null && it.status in listOf(StudentStatus.ENROLLED, StudentStatus.STUDYING) }
            .groupBy { it.classId!! }

        for (schoolClass in classes) {
            val classStudents = studentsByClass[schoolClass.id] ?: emptyList()
            schoolClass.studentCount = classStudents.size

            if (classStudents.isEmpty()) continue

            // 聚合五维均值
            schoolClass.avgIntelligence = classStudents.map { it.attributes.intelligence }.average().toFloat()
            schoolClass.avgPhysical = classStudents.map { it.attributes.physical }.average().toFloat()
            schoolClass.avgSocial = classStudents.map { it.attributes.social }.average().toFloat()
            schoolClass.avgCreativity = classStudents.map { it.attributes.creativity }.average().toFloat()
            schoolClass.avgMorality = classStudents.map { it.attributes.morality }.average().toFloat()
            schoolClass.avgAcademicScore = classStudents.map { it.academicScore }.average().toFloat()
            schoolClass.avgSatisfaction = classStudents.map { it.satisfaction }.average().toFloat()

            // 班风 = (社交均值 + 品德均值) / 2，受学业导师加成
            var baseSpirit = (schoolClass.avgSocial + schoolClass.avgMorality) / 2f
            // 凝聚力 = 社交均值 * 0.7 + 人数适中奖励
            var baseCohesion = schoolClass.avgSocial * 0.7f +
                    if (classStudents.size in 30..40) 10f else 0f
            // 纪律 = 品德均值 * 0.8
            var baseDiscipline = schoolClass.avgMorality * 0.8f

            // 学业导师加成
            val headTeacher = schoolClass.headTeacherId?.let { id ->
                teachers.find { it.id == id }
            }
            if (headTeacher != null) {
                val effect = HeadTeacherEffect.fromTeacher(headTeacher)
                baseSpirit += effect.satisfactionBoost * 100f
                baseDiscipline += effect.disciplineBoost
                baseCohesion += effect.socialBoost * 50f
            }

            val validOfficers = ClassOfficers.validForClass(
                schoolClass.id,
                officersByClass,
                classStudents
            )
            validOfficers[ClassOfficerRole.MONITOR]?.second?.let { student ->
                baseSpirit += ((student.attributes.social - 50f) / 10f).coerceIn(0f, 4f)
                baseCohesion += ((student.attributes.morality - 50f) / 8f).coerceIn(0f, 5f)
            }
            validOfficers[ClassOfficerRole.STUDY_COMMITTEE]?.second?.let { student ->
                schoolClass.avgAcademicScore = (
                    schoolClass.avgAcademicScore +
                        ((student.attributes.intelligence - 55f) / 10f).coerceIn(0f, 3f)
                    ).coerceIn(0f, 100f)
            }
            validOfficers[ClassOfficerRole.LIFE_COMMITTEE]?.second?.let { student ->
                schoolClass.avgSatisfaction = (
                    schoolClass.avgSatisfaction +
                        ((student.attributes.social - 55f) / 15f).coerceIn(0f, 2f)
                    ).coerceIn(0f, 100f)
                baseCohesion += 1f
            }
            validOfficers[ClassOfficerRole.ARTS_COMMITTEE]?.second?.let { student ->
                baseSpirit += ((student.attributes.creativity - 55f) / 12f).coerceIn(0f, 3f)
            }
            validOfficers[ClassOfficerRole.SPORTS_COMMITTEE]?.second?.let { student ->
                schoolClass.avgSatisfaction = (
                    schoolClass.avgSatisfaction +
                        ((student.attributes.physical - 55f) / 15f).coerceIn(0f, 2f)
                    ).coerceIn(0f, 100f)
                baseCohesion += 1f
            }
            validOfficers[ClassOfficerRole.MENTAL_HEALTH_COMMITTEE]?.second?.let { student ->
                schoolClass.avgSatisfaction = (
                    schoolClass.avgSatisfaction +
                        ((student.attributes.social + student.attributes.morality - 110f) / 15f)
                            .coerceIn(0f, 3f)
                    ).coerceIn(0f, 100f)
                baseDiscipline += 1f
            }

            schoolClass.classSpirit = baseSpirit.coerceIn(0f, 100f)
            schoolClass.cohesion = baseCohesion.coerceIn(0f, 100f)
            schoolClass.disciplineScore = baseDiscipline.coerceIn(0f, 100f)
        }

        // 计算年级排名
        calculateGradeRankings(classes)
    }

    /**
     * 按年级计算排名
     */
    private fun calculateGradeRankings(classes: List<SchoolClass>) {
        val byGrade = classes.groupBy { it.gradeLevel }
        for ((_, gradeClasses) in byGrade) {
            val sorted = gradeClasses.sortedByDescending { it.overallScore }
            sorted.forEachIndexed { index, schoolClass ->
                schoolClass.gradeRanking = index + 1
            }
        }
    }

    // ======= 班级事件 =======

    /**
     * 每月检查并触发班级事件
     */
    fun monthlyEvents(classes: List<SchoolClass>): List<ClassEvent> {
        val events = mutableListOf<ClassEvent>()

        for (schoolClass in classes) {
            if (schoolClass.studentCount == 0) continue

            // 班级竞赛获奖 (班均智力>75 且 排名第一)
            if (schoolClass.avgIntelligence > 75f && schoolClass.gradeRanking == 1 && Random.nextFloat() < 0.15f) {
                events.add(
                    ClassEvent.AwardEvent(
                        classId = schoolClass.id,
                        title = "${schoolClass.displayName}荣获竞赛大奖",
                        message = "${schoolClass.displayName}在学科竞赛中表现出色，为学校争光！",
                        reputationBonus = 5L,
                        spiritBonus = 5f
                    )
                )
                schoolClass.classSpirit = (schoolClass.classSpirit + 5f).coerceAtMost(100f)
            }

            // 体育比赛获奖 (班均体力>70)
            if (schoolClass.avgPhysical > 70f && Random.nextFloat() < 0.1f) {
                events.add(
                    ClassEvent.AwardEvent(
                        classId = schoolClass.id,
                        title = "${schoolClass.displayName}体育竞赛夺冠",
                        message = "${schoolClass.displayName}在校际体育比赛中取得优异成绩！",
                        reputationBonus = 3L,
                        spiritBonus = 3f
                    )
                )
            }

            // 纪律问题 (班均品德<40)
            if (schoolClass.avgMorality < 40f && Random.nextFloat() < 0.2f) {
                val penalty = (40f - schoolClass.avgMorality) / 10f
                events.add(
                    ClassEvent.DisciplineEvent(
                        classId = schoolClass.id,
                        title = "${schoolClass.displayName}出现纪律问题",
                        message = "${schoolClass.displayName}近期违纪事件频发，需要加强管理。",
                        disciplinePenalty = penalty,
                        reputationPenalty = 2L
                    )
                )
                schoolClass.disciplineScore = (schoolClass.disciplineScore - penalty).coerceAtLeast(0f)
            }

            // 班级团建活动 (班风>65 且 凝聚力>60)
            if (schoolClass.classSpirit > 65f && schoolClass.cohesion > 60f && Random.nextFloat() < 0.12f) {
                events.add(
                    ClassEvent.ActivityEvent(
                        classId = schoolClass.id,
                        title = "${schoolClass.displayName}组织班级活动",
                        message = "${schoolClass.displayName}自发组织了团建活动，班级氛围更加融洽。",
                        cohesionBonus = 3f,
                        satisfactionBonus = 2f
                    )
                )
                schoolClass.cohesion = (schoolClass.cohesion + 3f).coerceAtMost(100f)
            }

            // 学生冲突 (人数>40 且 纪律<50)
            if (schoolClass.studentCount > 40 && schoolClass.disciplineScore < 50f && Random.nextFloat() < 0.15f) {
                events.add(
                    ClassEvent.ConflictEvent(
                        classId = schoolClass.id,
                        title = "${schoolClass.displayName}发生学生冲突",
                        message = "班级人数过多且纪律松散，出现学生间矛盾冲突。",
                        satisfactionPenalty = 5f,
                        needsIntervention = schoolClass.headTeacherId == null
                    )
                )
            }

            // 文艺汇演 (班均创造力>70)
            if (schoolClass.avgCreativity > 70f && Random.nextFloat() < 0.08f) {
                events.add(
                    ClassEvent.AwardEvent(
                        classId = schoolClass.id,
                        title = "${schoolClass.displayName}文艺汇演获奖",
                        message = "创造力出众的${schoolClass.displayName}在校园文艺汇演中大放异彩！",
                        reputationBonus = 3L,
                        spiritBonus = 4f
                    )
                )
            }
        }

        return events
    }

    // ======= 学年升级 =======

    /**
     * 学年末（每年6月）执行升级/毕业
     * - 大四 → 毕业
     * - 大二 → 大四
     * - 大一 → 大二
     * - 学业成绩极差(academicScore < 20) → 留级
     */
    fun yearEndPromotion(
        students: List<Student>,
        classes: MutableList<SchoolClass>,
        schoolId: String,
        currentYear: Int,
        graduationGrade: GradeLevel = GradeLevel.GRADE_4
    ): PromotionResult {
        val promoted = mutableListOf<String>()
        val heldBack = mutableListOf<String>()
        val graduated = mutableListOf<String>()

        val activeStudents = students.filter {
            it.status == StudentStatus.STUDYING || it.status == StudentStatus.ENROLLED
        }

        for (student in activeStudents) {
            when {
                // 毕业年级毕业（本科大四 / 专科大三，按学校办学层次）
                student.gradeLevel == graduationGrade -> {
                    graduated.add(student.id)
                }
                // 成绩极差留级（学期掌握度>30表示已上课足够久，排除新入学的学生）
                student.academicScore < HELD_BACK_THRESHOLD && student.semesterMastery > 30f -> {
                    heldBack.add(student.id)
                }
                // 正常升级
                else -> {
                    promoted.add(student.id)
                }
            }
        }

        // 计算新大一需要多少容量（给下学年招生预留）
        val currentGrade1Count = activeStudents.count { it.gradeLevel == GradeLevel.GRADE_1 }
        val newGrade1Capacity = (currentGrade1Count * 1.1f).toInt()

        return PromotionResult(
            promotedStudents = promoted,
            heldBackStudents = heldBack,
            graduatedStudents = graduated,
            newGrade1Capacity = newGrade1Capacity
        )
    }

    /**
     * 分配学业导师
     * 将教师指定为某班级的学业导师
     * @param allClasses 所有班级列表，用于确保同一教师不会同时担任多个班的学业导师
     */
    fun assignHeadTeacher(
        schoolClass: SchoolClass,
        teacher: Teacher,
        allClasses: List<SchoolClass> = emptyList()
    ): HeadTeacherEffect {
        // 防御性校验：如果该教师已在其他班担任学业导师，先移除旧分配
        allClasses.forEach { cls ->
            if (cls.id != schoolClass.id && cls.headTeacherId == teacher.id) {
                cls.headTeacherId = null
            }
        }
        schoolClass.headTeacherId = teacher.id
        return HeadTeacherEffect.fromTeacher(teacher)
    }

    /**
     * 自动为无学业导师的班级分配学业导师
     * 优先分配: management和psychology能力高的教师
     */
    fun autoAssignHeadTeachers(
        classes: List<SchoolClass>,
        availableTeachers: List<Teacher>
    ): Map<String, String> {
        val assignments = mutableMapOf<String, String>()
        val assignedTeacherIds = classes.mapNotNull { it.headTeacherId }.toMutableSet()

        val classesNeedingHead = classes.filter { it.headTeacherId == null && it.studentCount > 0 }

        // 按综合管理能力排序可用教师
        val rankedTeachers = availableTeachers
            .filter { it.isWorking && !it.isOnVacation && it.id !in assignedTeacherIds }
            .sortedByDescending { it.management * 0.5 + it.psychology * 0.3 + it.teaching * 0.2 }

        for (schoolClass in classesNeedingHead) {
            val teacher = rankedTeachers.firstOrNull { it.id !in assignedTeacherIds }
                ?: break
            schoolClass.headTeacherId = teacher.id
            assignedTeacherIds.add(teacher.id)
            assignments[schoolClass.id] = teacher.id
        }

        return assignments
    }

    companion object {
        const val DEFAULT_CLASS_CAPACITY = 45
        const val HELD_BACK_THRESHOLD = 20f   // 成绩低于20分留级
    }
}
