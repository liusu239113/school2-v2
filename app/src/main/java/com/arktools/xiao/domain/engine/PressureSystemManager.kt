package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 经营压力系统管理器
 * 集中管理：教师涨薪/流失、设施维修、税费、学生退费、招生季限制、家长投诉升级、考试后果等
 * 目标：让玩家每个月都有决策压力，不能"挂机躺赚"
 */
@Singleton
class PressureSystemManager @Inject constructor() {

    // ═══════════════════════════════════════════
    // 状态数据
    // ═══════════════════════════════════════════

    /** 教师上次涨薪的游戏月份 (teacherId -> absoluteMonth) */
    private val lastRaiseMonth = mutableMapOf<String, Int>()

    /** 教师合同到期月份 (teacherId -> absoluteMonth) */
    private val contractExpiry = mutableMapOf<String, Int>()

    /** 已生成但尚未处理的续约事件，防止每月重复弹窗 */
    private val renewalPending = mutableSetOf<String>()

    /** 设施上次维修月份 (facilityType.name -> absoluteMonth) */
    private val lastMaintenanceMonth = mutableMapOf<String, Int>()

    /** 累计家长投诉数（每月重置） */
    var monthlyComplaintCount: Int = 0
        private set

    /** 连续亏损月数 */
    var consecutiveLossMonths: Int = 0
        private set

    /** 连续毕业评估排名下降年数 */
    var consecutiveRankingDecline: Int = 0
        private set

    /** 上一年毕业深造就业率 */
    var lastYearGraduationRate: Float = -1f
        private set

    /** 最近一次完成年度毕业评估结算的游戏年份。 */
    var lastExamProcessingYear: Int = 0
        private set

    /** 累计未达招生线学期数 */
    var failedEnrollmentSeasons: Int = 0
        private set

    data class Snapshot(
        val lastRaiseMonth: Map<String, Int>,
        val contractExpiry: Map<String, Int>,
        val renewalPending: Set<String>,
        val lastMaintenanceMonth: Map<String, Int>,
        val monthlyComplaintCount: Int,
        val consecutiveLossMonths: Int,
        val consecutiveRankingDecline: Int,
        val lastYearGraduationRate: Float,
        val lastExamProcessingYear: Int,
        val failedEnrollmentSeasons: Int
    )

    fun snapshotState(): Snapshot = Snapshot(
        lastRaiseMonth = lastRaiseMonth.toMap(),
        contractExpiry = contractExpiry.toMap(),
        renewalPending = renewalPending.toSet(),
        lastMaintenanceMonth = lastMaintenanceMonth.toMap(),
        monthlyComplaintCount = monthlyComplaintCount,
        consecutiveLossMonths = consecutiveLossMonths,
        consecutiveRankingDecline = consecutiveRankingDecline,
        lastYearGraduationRate = lastYearGraduationRate,
        lastExamProcessingYear = lastExamProcessingYear,
        failedEnrollmentSeasons = failedEnrollmentSeasons
    )

    fun restoreSnapshot(snapshot: Snapshot) {
        lastRaiseMonth.clear()
        lastRaiseMonth.putAll(snapshot.lastRaiseMonth)
        contractExpiry.clear()
        contractExpiry.putAll(snapshot.contractExpiry)
        renewalPending.clear()
        renewalPending.addAll(snapshot.renewalPending)
        lastMaintenanceMonth.clear()
        lastMaintenanceMonth.putAll(snapshot.lastMaintenanceMonth)
        monthlyComplaintCount = snapshot.monthlyComplaintCount
        consecutiveLossMonths = snapshot.consecutiveLossMonths
        consecutiveRankingDecline = snapshot.consecutiveRankingDecline
        lastYearGraduationRate = snapshot.lastYearGraduationRate
        lastExamProcessingYear = snapshot.lastExamProcessingYear
        failedEnrollmentSeasons = snapshot.failedEnrollmentSeasons
    }

    // ═══════════════════════════════════════════
    // P0-1: 教师涨薪机制
    // ═══════════════════════════════════════════

    data class RaiseRequest(
        val teacherId: String,
        val teacherName: String,
        val currentSalary: Double,
        val requestedRaise: Double, // 万元增量
        val raisePercent: Int       // 百分比
    )

    /**
     * 检查是否有教师需要涨薪（每月调用）
     * 规则：教师工作满6个月未涨薪 → 概率提出涨薪需求
     * 限制：每月最多返回1个涨薪请求，避免后期教师多时弹窗堆积
     */
    fun checkRaiseRequests(teachers: List<Teacher>, currentAbsMonth: Int): List<RaiseRequest> {
        val requests = mutableListOf<RaiseRequest>()
        // 打乱教师顺序，保证公平性
        val shuffledTeachers = teachers.shuffled()
        for (teacher in shuffledTeachers) {
            if (!teacher.isWorking || teacher.pendingResignation) continue
            val lastRaise = lastRaiseMonth[teacher.id] ?: (currentAbsMonth - 3) // 新教师入职3月后才开始计算
            val monthsSinceRaise = currentAbsMonth - lastRaise
            if (monthsSinceRaise < 10) continue

            // 概率大幅降低：10个月=8%, 15个月=15%, 20个月=22%, 30个月=30%（上限30%）
            val chance = ((monthsSinceRaise - 10) * 0.015f + 0.08f).coerceAtMost(0.30f)
            if (Random.nextFloat() > chance) continue

            // 涨薪幅度：5%-15%，高级别教师要求更高
            val basePercent = when (teacher.level) {
                TeacherLevel.S -> Random.nextInt(10, 16)
                TeacherLevel.A -> Random.nextInt(8, 14)
                TeacherLevel.B -> Random.nextInt(6, 12)
                TeacherLevel.C -> Random.nextInt(5, 10)
            }
            val raiseAmount = teacher.salary * basePercent / 100.0

            requests.add(RaiseRequest(
                teacherId = teacher.id,
                teacherName = teacher.name,
                currentSalary = teacher.salary,
                requestedRaise = raiseAmount,
                raisePercent = basePercent
            ))
            // 每月最多1个涨薪请求
            if (requests.size >= 1) break
        }
        return requests
    }

    /** 批准涨薪 */
    fun approveRaise(teacherId: String, currentAbsMonth: Int) {
        lastRaiseMonth[teacherId] = currentAbsMonth
    }

    /** 拒绝涨薪（返回忠诚度惩罚值） */
    fun rejectRaise(teacherId: String): Int {
        // 拒绝涨薪：忠诚度-15~-25
        return Random.nextInt(15, 26)
    }

    // ═══════════════════════════════════════════
    // P0-2: 设施维修突发事件
    // ═══════════════════════════════════════════

    data class MaintenanceEvent(
        val facilityType: FacilityType,
        val description: String,
        val repairCost: Double,    // 万元
        val conditionLoss: Float   // 不修则扣除的condition值
    )

    /**
     * 检查设施是否需要突发维修（每月调用）
     * condition自然衰减 + 概率触发维修事件
     * 限制：每月最多返回1个维修事件，避免设施多时弹窗堆积
     */
    fun checkFacilityMaintenance(facilities: List<Facility>, campusLevel: Int): List<MaintenanceEvent> {
        val events = mutableListOf<MaintenanceEvent>()
        for (facility in facilities) {
            if (facility.isConstructing) continue
            // 自然衰减：每月 condition -1.5~-3（等级越高衰减越慢但维修费越高）
            val decay = (3.5f - facility.level * 0.3f).coerceAtLeast(1.0f)
            facility.condition = (facility.condition - decay).coerceAtLeast(0f)

            // 已有事件时跳过后续概率检查（但衰减照样计算）
            if (events.size >= 1) continue

            // 突发维修概率大幅降低：condition越低概率越高
            val breakChance = when {
                facility.condition < 30f -> 0.15f
                facility.condition < 50f -> 0.07f
                facility.condition < 70f -> 0.03f
                else -> 0.01f
            }

            if (Random.nextFloat() < breakChance) {
                val costMultiplier = campusLevel * 0.8 + facility.level * 0.5
                val baseCost = facility.type.baseCost * 0.15 * costMultiplier
                val description = getMaintenanceDescription(facility.type)

                events.add(MaintenanceEvent(
                    facilityType = facility.type,
                    description = description,
                    repairCost = baseCost,
                    conditionLoss = 15f
                ))
            }
        }
        return events
    }

    /** 执行维修（恢复condition） */
    fun repairFacility(facility: Facility) {
        facility.condition = (facility.condition + 30f).coerceAtMost(100f)
    }

    /** 忽略维修（condition继续下降） */
    fun ignoreMaintenance(facility: Facility, conditionLoss: Float) {
        facility.condition = (facility.condition - conditionLoss).coerceAtLeast(0f)
    }

    private fun getMaintenanceDescription(type: FacilityType): String = when (type) {
        FacilityType.CONFERENCE_CENTER -> listOf("会议厅音响系统故障", "同传设备需要检修", "会场地毯需要更换").random()
        FacilityType.EMPLOYMENT_CENTER -> listOf("招聘信息屏故障", "面试间桌椅损坏", "就业档案柜需要更换").random()
        FacilityType.INCUBATOR -> listOf("孵化工位设备损坏", "路演厅灯光故障", "校企合作展板需要更换").random()
        FacilityType.INTERNATIONAL_CENTER -> listOf("同传耳机需要检修", "国际会议室投影故障", "外事接待区需要整修").random()
        FacilityType.LOGISTICS_CENTER -> listOf("后勤调度系统故障", "仓库货架需要加固", "能源计量设备需要更换").random()
        FacilityType.CLASSROOM -> listOf("教室天花板漏水需要修缮", "教室门窗老化需要更换", "教室地板开裂需要维修").random()
        FacilityType.MULTIMEDIA_ROOM -> listOf("投影仪故障需要维修", "多媒体设备老化需要升级", "音响系统故障").random()
        FacilityType.LABORATORY -> listOf("实验器材损耗需要补充", "实验室通风系统故障", "实验台面损坏需更换").random()
        FacilityType.COMPUTER_LAB -> listOf("多台电脑需要更换硬盘", "网络设备故障需维修", "机房空调故障").random()
        FacilityType.ART_STUDIO -> listOf("画架和画板需要补充", "工作室照明系统故障", "通风设备需维修").random()
        FacilityType.LIBRARY -> listOf("书架松动需要加固", "图书馆屋顶渗水", "空调系统需要维护").random()
        FacilityType.SPORTS_FIELD -> listOf("跑道破损需要翻新", "篮球架锈蚀需更换", "排水系统堵塞").random()
        FacilityType.CANTEEN -> listOf("厨房设备老化需更换", "食堂排烟系统故障", "餐桌椅批量损坏").random()
        FacilityType.DORMITORY -> listOf("宿舍水管爆裂急需修理", "热水器批量故障", "宿舍门锁需要更换").random()
        FacilityType.AUDITORIUM -> listOf("舞台灯光系统故障", "座椅大面积损坏", "音响系统需要大修").random()
        FacilityType.GARDEN -> listOf("花园灌溉系统漏水", "园艺设施损坏", "景观照明需维修").random()
        FacilityType.GATE -> listOf("校门电动门故障", "门卫室设施损坏", "校门LED屏幕故障").random()
    }

    // ═══════════════════════════════════════════
    // P0-3: 季度税费
    // ═══════════════════════════════════════════

    /**
     * 计算季度税费（每季度末月调用，即3/6/9/12月）
     * 
     * 采用累进税率制度（类似现实个人所得税），防止高收入学校被单一税率压垮：
     * - 第一档：季度收入 0~3000万 部分，按基础税率
     * - 第二档：3000~15000万 部分，按基础税率×0.7
     * - 第三档：15000万以上部分，按基础税率×0.4
     * 
     * 效果：Lv6满配从原来 ~26600万 降至 ~8700万（仍是大额支出但不再离谱）
     */
    fun calculateQuarterlyTax(quarterlyRevenue: Double, campusLevel: Int): Double {
        val baseTaxRate = when (campusLevel) {
            1 -> 0.05
            2 -> 0.07
            3 -> 0.09
            4 -> 0.10
            5 -> 0.11
            6 -> 0.12
            else -> 0.05
        }
        
        // 累进税率分档（单位：万元）
        val bracket1Limit = 3000.0   // 第一档上限
        val bracket2Limit = 15000.0  // 第二档上限
        val bracket2Discount = 0.7   // 第二档税率折扣
        val bracket3Discount = 0.4   // 第三档税率折扣
        
        val tax: Double = if (quarterlyRevenue <= bracket1Limit) {
            quarterlyRevenue * baseTaxRate
        } else if (quarterlyRevenue <= bracket2Limit) {
            bracket1Limit * baseTaxRate +
            (quarterlyRevenue - bracket1Limit) * baseTaxRate * bracket2Discount
        } else {
            bracket1Limit * baseTaxRate +
            (bracket2Limit - bracket1Limit) * baseTaxRate * bracket2Discount +
            (quarterlyRevenue - bracket2Limit) * baseTaxRate * bracket3Discount
        }
        
        return tax.coerceAtLeast(0.0)
    }

    fun isQuarterEnd(month: Int): Boolean = month in listOf(3, 6, 9, 12)

    // ═══════════════════════════════════════════
    // P0-4: 学生退费机制
    // ═══════════════════════════════════════════

    data class StudentWithdrawal(
        val studentId: String,
        val studentName: String,
        val refundAmount: Double, // 退费金额（万元）
        val reason: String
    )

    /**
     * 检查不满意学生退学（每月调用）
     * 满意度<40有退学概率，退学时退还部分学费
     * 限制：每月最多2人退学，避免集中退学弹窗轰炸
     */
    fun checkStudentWithdrawals(students: List<Student>, tuitionPerStudent: Double): List<StudentWithdrawal> {
        val withdrawals = mutableListOf<StudentWithdrawal>()
        // 打乱顺序保证公平
        val shuffled = students.shuffled()
        for (student in shuffled) {
            if (student.status != StudentStatus.ENROLLED) continue
            if (student.satisfaction >= 40f) continue

            // 退学概率降低：满意度越低概率越高
            val dropChance = when {
                student.satisfaction < 15f -> 0.12f
                student.satisfaction < 25f -> 0.06f
                student.satisfaction < 35f -> 0.03f
                else -> 0.015f
            }

            if (Random.nextFloat() < dropChance) {
                // 退还剩余月份的学费（假设学年10个月，退当月之后的）
                val refund = tuitionPerStudent * 0.4 // 退40%学费
                val reason = when {
                    student.satisfaction < 15f -> "对学校极度不满，家长强烈要求退学"
                    student.satisfaction < 25f -> "学习环境恶劣，转去其他学校"
                    else -> "对教学质量不满意，选择离开"
                }
                withdrawals.add(StudentWithdrawal(
                    studentId = student.id,
                    studentName = student.name,
                    refundAmount = refund,
                    reason = reason
                ))
                // 每月最多2人退学
                if (withdrawals.size >= 2) break
            }
        }
        return withdrawals
    }

    // ═══════════════════════════════════════════
    // P0-5: 教师竞争与流失
    // ═══════════════════════════════════════════

    data class PoachingAttempt(
        val teacherId: String,
        val teacherName: String,
        val teacherLevel: TeacherLevel,
        val competitorName: String,
        val offeredSalary: Double,  // 对手开出的薪资（万元/月）
        val retainCost: Double     // 留人需要加薪到的金额
    )

    data class BurnoutResignation(
        val teacherId: String,
        val teacherName: String,
        val reason: String
    )

    /**
     * 竞争对手挖人（每月检查高级教师）
     * 限制：每月最多1次挖人事件
     */
    fun checkPoachingAttempts(teachers: List<Teacher>, schoolReputation: Long, campusLevel: Int): List<PoachingAttempt> {
        val attempts = mutableListOf<PoachingAttempt>()
        val highValueTeachers = teachers.filter {
            it.isWorking && !it.pendingResignation &&
            (it.level == TeacherLevel.S || it.level == TeacherLevel.A)
        }.shuffled()

        for (teacher in highValueTeachers) {
            // 挖人概率降低：声誉低的学校更容易被挖
            val poachChance = when (teacher.level) {
                TeacherLevel.S -> 0.05f - (campusLevel * 0.008f)
                TeacherLevel.A -> 0.03f - (campusLevel * 0.004f)
                else -> 0f
            }.coerceAtLeast(0.005f)

            if (Random.nextFloat() > poachChance) continue

            val competitorNames = listOf("育才学校", "新世纪学院", "蓝天中学", "精英教育集团", "启明星学校", "博雅中学")
            val offeredMultiplier = 1.3 + Random.nextFloat() * 0.4 // 对手开130%-170%薪资
            val offeredSalary = teacher.salary * offeredMultiplier
            val retainCost = teacher.salary * (1.0 + Random.nextFloat() * 0.2 + 0.1) // 留人需加薪10%-30%

            attempts.add(PoachingAttempt(
                teacherId = teacher.id,
                teacherName = teacher.name,
                teacherLevel = teacher.level,
                competitorName = competitorNames.random(),
                offeredSalary = offeredSalary,
                retainCost = retainCost
            ))
            // 每月最多1次挖人
            if (attempts.size >= 1) break
        }
        return attempts
    }

    /**
     * 倦怠离职检查：忠诚度<30且连续低满意状态的教师
     * 限制：每月最多1人倦怠离职
     */
    fun checkBurnoutResignations(teachers: List<Teacher>): List<BurnoutResignation> {
        val resignations = mutableListOf<BurnoutResignation>()
        for (teacher in teachers.shuffled()) {
            if (!teacher.isWorking || teacher.pendingResignation) continue
            if (teacher.loyalty >= 30) continue

            // 忠诚度<30时，每月8%概率辞职（降低）
            if (Random.nextFloat() < 0.08f) {
                val reason = when {
                    teacher.loyalty < 10 -> "工作压力过大，身心俱疲决定辞职"
                    teacher.fatigue > 80 -> "长期超负荷工作，不堪重负选择离开"
                    else -> "对学校发展前景失去信心，另谋高就"
                }
                resignations.add(BurnoutResignation(
                    teacherId = teacher.id,
                    teacherName = teacher.name,
                    reason = reason
                ))
                // 每月最多1人倦怠离职
                if (resignations.size >= 1) break
            }
        }
        return resignations
    }

    /**
     * 教师合同到期检查
     * @return 需要续约的教师列表及续约成本
     */
    data class ContractRenewal(
        val teacherId: String,
        val teacherName: String,
        val currentSalary: Double,
        val renewalDemand: Double, // 续约要求的新薪资
        val demandPercent: Int
    )

    fun checkContractExpiry(teachers: List<Teacher>, currentAbsMonth: Int): List<ContractRenewal> {
        val renewals = mutableListOf<ContractRenewal>()
        for (teacher in teachers) {
            if (!teacher.isWorking || teacher.pendingResignation) continue
            val expiry = contractExpiry[teacher.id]
            if (expiry == null) {
                // 首次：设置12-24个月合同
                contractExpiry[teacher.id] = currentAbsMonth + Random.nextInt(12, 25)
                continue
            }
            if (currentAbsMonth < expiry) continue

            // 已到期的教师只生成一次续约事件，等待玩家选择期间不重复弹窗
            if (renewalPending.contains(teacher.id)) continue
            renewalPending.add(teacher.id)

            // 合同到期，提出续约条件
            val demandPercent = Random.nextInt(5, 20) // 加薪5%-20%
            renewals.add(ContractRenewal(
                teacherId = teacher.id,
                teacherName = teacher.name,
                currentSalary = teacher.salary,
                renewalDemand = teacher.salary * (1.0 + demandPercent / 100.0),
                demandPercent = demandPercent
            ))
            // 每月最多1个合同续约事件弹窗，剩余的下个月再处理
            if (renewals.size >= 1) break
        }
        return renewals
    }

    /** 续约成功 */
    fun renewContract(teacherId: String, currentAbsMonth: Int) {
        renewalPending.remove(teacherId)
        contractExpiry[teacherId] = currentAbsMonth + Random.nextInt(12, 25)
        lastRaiseMonth[teacherId] = currentAbsMonth
    }

    /** 不续约（教师离开） */
    fun expireContract(teacherId: String) {
        renewalPending.remove(teacherId)
        contractExpiry.remove(teacherId)
        lastRaiseMonth.remove(teacherId)
    }

    // ═══════════════════════════════════════════
    // P0-6: 招生季限制
    // ═══════════════════════════════════════════

    /**
     * 判断当前月份是否是招生季
     * 3月(春季招生)和9月(秋季招生)
     */
    fun isEnrollmentSeason(month: Int): Boolean = month == 3 || month == 9

    /**
     * 计算招生数量（招生季外大幅减少）
     * @return 招生倍率 (1.0=正常, 0.1=非招生季几乎无新生)
     */
    fun getEnrollmentMultiplier(month: Int, campusLevel: Int, schoolRep: Long, competitorRep: Long): Float {
        if (!isEnrollmentSeason(month)) return 0.08f // 非招生季只有零星转学生

        // 招生季竞争分流：对手声誉高时分走学生（缓和惩罚，保证新学校也能招到人）
        val repDiff = competitorRep - schoolRep
        val competitionDrain = when {
            repDiff > 2000 -> 0.6f  // 对手远超你，分走40%
            repDiff > 500 -> 0.75f  // 对手强于你，分走25%
            repDiff > 200 -> 0.85f  // 对手略强，分走15%
            repDiff > 0 -> 0.92f    // 对手稍强，分走8%
            else -> 1.0f            // 你更强或持平
        }

        // 等级加成
        val levelBonus = 1.0f + (campusLevel - 1) * 0.1f

        return (1.5f * competitionDrain * levelBonus) // 招生季有1.5x基础加成
    }

    /**
     * 检查是否达到最低招生线
     * @return true=达标, false=未达标
     */
    fun checkMinimumEnrollment(newStudentsThisSeason: Int, campusLevel: Int): Boolean {
        val minimum = when (campusLevel) {
            1 -> 3
            2 -> 8
            3 -> 15
            4 -> 25
            5 -> 40
            6 -> 60
            else -> 3
        }
        val met = newStudentsThisSeason >= minimum
        if (!met) failedEnrollmentSeasons++ else failedEnrollmentSeasons = 0
        return met
    }

    // ═══════════════════════════════════════════
    // P1-1: 家长投诉升级
    // ═══════════════════════════════════════════

    enum class ComplaintType(val displayName: String, val reputationPenalty: Long) {
        FOOD_QUALITY("伙食质量差", 3),
        BULLYING("校园霸凌", 8),
        TEACHING_QUALITY("教学质量差", 5),
        HIGH_FEES("收费过高", 4),
        SAFETY("安全隐患", 10),
        OVERWORK("学生负担过重", 4)
    }

    data class ComplaintEscalation(
        val level: EscalationLevel,
        val description: String,
        val reputationPenalty: Long
    )

    enum class EscalationLevel { INTERNAL, EDUCATION_BUREAU, MEDIA_EXPOSURE }

    /**
     * 生成家长投诉（每月调用）
     * @return 投诉数量和类型
     */
    fun generateComplaints(avgSatisfaction: Float, totalStudents: Int, campusLevel: Int): List<ComplaintType> {
        monthlyComplaintCount = 0
        if (totalStudents < 10) return emptyList()

        val complaints = mutableListOf<ComplaintType>()
        // 满意度越低投诉越多
        val baseCount = when {
            avgSatisfaction < 30f -> Random.nextInt(3, 7)
            avgSatisfaction < 45f -> Random.nextInt(2, 5)
            avgSatisfaction < 60f -> Random.nextInt(0, 3)
            avgSatisfaction < 75f -> Random.nextInt(0, 2)
            else -> 0
        }

        repeat(baseCount) {
            complaints.add(ComplaintType.entries.toTypedArray().random())
        }
        monthlyComplaintCount = complaints.size
        return complaints
    }

    /**
     * 检查投诉是否升级
     */
    fun checkComplaintEscalation(campusLevel: Int): ComplaintEscalation? {
        val threshold = campusLevel * 3
        return when {
            monthlyComplaintCount >= threshold * 2 -> ComplaintEscalation(
                EscalationLevel.MEDIA_EXPOSURE,
                "大量家长联名投诉引发媒体关注！记者到校采访，负面报道迅速传播！",
                80L + campusLevel * 10L
            )
            monthlyComplaintCount >= threshold -> ComplaintEscalation(
                EscalationLevel.EDUCATION_BUREAU,
                "家长投诉过多，教育局派督察组进驻调查！",
                30L + campusLevel * 5L
            )
            else -> null
        }
    }

    // ═══════════════════════════════════════════
    // P1-3: 考试后果放大
    // ═══════════════════════════════════════════

    data class ExamConsequence(
        val rankChange: Int,      // 排名变化（正=下降，负=上升）
        val reputationChange: Long,
        val description: String,
        val isPositive: Boolean
    )

    /**
     * 年度毕业评估结果处理
     */
    fun processAnnualExamResults(
        processingYear: Int,
        currentGraduationRate: Float, // 0-1
        campusLevel: Int
    ): ExamConsequence? {
        if (processingYear <= lastExamProcessingYear) return null
        lastExamProcessingYear = processingYear

        // 与上一年对比
        if (lastYearGraduationRate < 0f) {
            lastYearGraduationRate = currentGraduationRate
            return null
        }

        val diff = currentGraduationRate - lastYearGraduationRate
        lastYearGraduationRate = currentGraduationRate

        return when {
            // 状元级表现（深造就业率>90%且上升）
            currentGraduationRate > 0.9f && diff > 0.05f -> {
                consecutiveRankingDecline = 0
                ExamConsequence(
                    rankChange = -2, reputationChange = 300L,
                    description = "毕业质量卓越！深造就业率${String.format("%.1f", currentGraduationRate * 100)}%，引发社会关注！",
                    isPositive = true
                )
            }
            // 明显上升
            diff > 0.1f -> {
                consecutiveRankingDecline = 0
                ExamConsequence(
                    rankChange = -1, reputationChange = 100L,
                    description = "毕业质量显著提升！深造就业率比去年提高${String.format("%.1f", diff * 100)}个百分点",
                    isPositive = true
                )
            }
            // 明显下降
            diff < -0.1f -> {
                consecutiveRankingDecline++
                val penalty = if (consecutiveRankingDecline >= 2) 200L else 80L
                ExamConsequence(
                    rankChange = 1, reputationChange = -penalty,
                    description = if (consecutiveRankingDecline >= 2)
                        "毕业评估排名连续${consecutiveRankingDecline}年下滑！教育厅约谈校长！"
                    else
                        "毕业深造就业率下降${String.format("%.1f", -diff * 100)}个百分点，家长不满情绪上升",
                    isPositive = false
                )
            }
            // 轻微下降
            diff < -0.03f -> {
                consecutiveRankingDecline++
                ExamConsequence(
                    rankChange = 0, reputationChange = -30L,
                    description = "毕业评估略有下滑，需引起重视",
                    isPositive = false
                )
            }
            else -> {
                consecutiveRankingDecline = 0
                null // 持平，无特殊后果
            }
        }
    }

    // ═══════════════════════════════════════════
    // P2-1: 学生行为事件链
    // ═══════════════════════════════════════════

    data class StudentBehaviorEvent(
        val type: BehaviorEventType,
        val description: String,
        val choices: List<BehaviorChoice>
    )

    data class BehaviorChoice(
        val text: String,
        val satisfactionChange: Float,
        val reputationChange: Long,
        val costWan: Double,
        val followUp: String? // 后续事件描述
    )

    enum class BehaviorEventType(val displayName: String) {
        BULLYING("校园欺凌"),
        MENTAL_HEALTH("心理健康"),
        ROMANCE("学生早恋"),
        INTERNET_ADDICTION("网络沉迷"),
        CHEATING("考试作弊")
    }

    /**
     * 生成学生行为事件（每月概率触发）
     */
    fun generateBehaviorEvent(totalStudents: Int, campusLevel: Int): StudentBehaviorEvent? {
        if (totalStudents < 30) return null
        // 概率：学生越多越容易出问题
        val chance = (totalStudents / 500f).coerceAtMost(0.3f) + 0.05f
        if (Random.nextFloat() > chance) return null

        return when (BehaviorEventType.entries.toTypedArray().random()) {
            BehaviorEventType.BULLYING -> StudentBehaviorEvent(
                type = BehaviorEventType.BULLYING,
                description = "有家长反映其孩子在学校遭到同学欺凌，要求学校处理。",
                choices = listOf(
                    BehaviorChoice("严肃处分欺凌者+心理辅导", 4f, 160L, 4.0, "家长表示满意，事件平息"),
                    BehaviorChoice("召集双方家长调解", 1f, 40L, 1.5, "调解基本成功，但暗流涌动"),
                    BehaviorChoice("轻描淡写处理", -8f, -220L, 0.0, "受害者家长可能向教育局投诉")
                )
            )
            BehaviorEventType.MENTAL_HEALTH -> StudentBehaviorEvent(
                type = BehaviorEventType.MENTAL_HEALTH,
                description = "学业导师报告有学生出现严重心理问题，情绪低落、不愿上课。",
                choices = listOf(
                    BehaviorChoice("聘请专业心理咨询师（长期）", 6f, 180L, 8.0, "建立心理援助体系，学生感受到关怀"),
                    BehaviorChoice("安排学业导师一对一谈心", 2f, 40L, 0.0, "有所缓解但非专业处理"),
                    BehaviorChoice("通知家长自行处理", -6f, -200L, 0.0, "学生状况恶化，家长不满")
                )
            )
            BehaviorEventType.ROMANCE -> StudentBehaviorEvent(
                type = BehaviorEventType.ROMANCE,
                description = "发现多对学生早恋，部分家长来电询问学校态度。",
                choices = listOf(
                    BehaviorChoice("开展青春期教育讲座", 3f, 120L, 2.0, "引导学生正确看待感情"),
                    BehaviorChoice("严令禁止，违者处分", -6f, -40L, 0.0, "引发学生逆反，但短期纪律好转"),
                    BehaviorChoice("默许不管", -2f, -150L, 0.0, "部分家长不满学校放任态度")
                )
            )
            BehaviorEventType.INTERNET_ADDICTION -> StudentBehaviorEvent(
                type = BehaviorEventType.INTERNET_ADDICTION,
                description = "多名学生沉迷手机游戏，上课频繁走神，成绩明显下滑。",
                choices = listOf(
                    BehaviorChoice("上课期间统一收手机+家校联动", 4f, 140L, 2.0, "成绩逐步回升"),
                    BehaviorChoice("没收手机、严厉惩罚", -5f, -30L, 0.0, "立竿见影但学生怨声载道"),
                    BehaviorChoice("不做强制规定", -4f, -160L, 0.0, "问题持续蔓延")
                )
            )
            BehaviorEventType.CHEATING -> StudentBehaviorEvent(
                type = BehaviorEventType.CHEATING,
                description = "期中考试发现大面积作弊行为，涉及多个班级。",
                choices = listOf(
                    BehaviorChoice("全部成绩作废、重新考试+处分", -2f, 160L, 3.0, "维护了公平，但学生压力增大"),
                    BehaviorChoice("个别处理，不公开", -1f, -140L, 0.0, "未能彻底解决，下次可能再犯"),
                    BehaviorChoice("加强监考制度+诚信教育", 3f, 120L, 2.0, "长远效果好，树立校风")
                )
            )
        }
    }

    // ═══════════════════════════════════════════
    // P2-2: 设施联动深化（效果已在 FacilityBonusCalculator 中实现）
    // 这里添加设施对特定系统的门槛限制
    // ═══════════════════════════════════════════

    /**
     * 检查设施对招生上限的影响
     * 没有宿舍 → 外地生源上限为0
     * 食堂等级低 → 家长满意度惩罚
     */
    data class FacilityPenalty(val description: String, val satisfactionPenalty: Float, val enrollmentCap: Int?)

    fun checkFacilityPenalties(facilities: List<Facility>, totalStudents: Int): List<FacilityPenalty> {
        val penalties = mutableListOf<FacilityPenalty>()

        val canteen = facilities.find { it.type == FacilityType.CANTEEN }
        if (canteen == null && totalStudents > 50) {
            penalties.add(FacilityPenalty("学校没有食堂，大量学生用餐不便", 3f, null))
        } else if (canteen != null && !canteen.isOperational) {
            penalties.add(FacilityPenalty("食堂设施损坏停用，师生怨声载道", 5f, null))
        }

        val classroom = facilities.find { it.type == FacilityType.CLASSROOM }
        if (classroom == null && totalStudents > 30) {
            penalties.add(FacilityPenalty("教室不足，部编入教学班级过度拥挤", 4f, totalStudents))
        }

        return penalties
    }

    // ═══════════════════════════════════════════
    // P3-1: 月报数据（供UI弹窗使用）
    // ═══════════════════════════════════════════

    @Serializable
    data class MonthlyBrief(
        val year: Int = 0,
        val month: Int = 0,
        val revenue: Double = 0.0,
        val expenses: Double = 0.0,
        val netProfit: Double = 0.0,
        val studentChange: Int = 0,    // 本月学生变动
        val teacherChange: Int = 0,    // 本月教师变动
        val reputationChange: Long = 0, // 本月声誉变动
        val majorEvents: List<String> = emptyList()
    )

    var lastMonthBrief: MonthlyBrief = MonthlyBrief()
        private set

    fun recordMonthlyBrief(brief: MonthlyBrief) {
        lastMonthBrief = brief
    }

    // ═══════════════════════════════════════════
    // P1-2: 政策决策代价（延时生效 + 副作用提示）
    // ═══════════════════════════════════════════

    data class PolicyChangeResult(
        val delayMonths: Int,           // 生效延迟（月）
        val sideEffect: String?,        // 副作用描述
        val sideEffectPenalty: Double    // 副作用经济惩罚（万元）
    )

    /**
     * 政策变更时调用：返回延迟月数+副作用
     * @param policyName 政策名称
     * @param isUpgrade true=提升/加强，false=削减/降低
     */
    fun evaluatePolicyChange(policyName: String, isUpgrade: Boolean): PolicyChangeResult {
        val delay = when (policyName) {
            "tuition" -> if (isUpgrade) 2 else 1     // 涨学费需2个月过渡
            "teacherPay" -> if (isUpgrade) 1 else 3  // 降薪需3个月逐步执行
            "classSize" -> 2                          // 班额调整需要2个月
            "scholarship" -> 1                        // 奖学金1个月生效
            else -> 1
        }
        val (sideEffect, penalty) = when {
            policyName == "tuition" && isUpgrade -> Pair(
                "学费上涨导致部分家长不满，短期内退学风险增加10%", 0.0
            )
            policyName == "teacherPay" && !isUpgrade -> Pair(
                "降低教师薪资将导致教师忠诚度下降，可能引发离职潮", 0.0
            )
            policyName == "classSize" && isUpgrade -> Pair(
                "扩大班额将增加教学压力，教学质量暂时下降5%", 0.0
            )
            policyName == "extracurricular" && !isUpgrade -> Pair(
                "削减课外活动引发学生不满，满意度降低", 0.0
            )
            else -> Pair(null, 0.0)
        }
        return PolicyChangeResult(delay, sideEffect, penalty)
    }

    // ═══════════════════════════════════════════
    // P2-3: 教师分级培养路径
    // ═══════════════════════════════════════════

    data class TeacherTrainingAdvice(
        val teacherId: String,
        val teacherName: String,
        val currentLevel: String,
        val suggestedPath: String,    // 推荐培养方向
        val estimatedMonths: Int,     // 预计所需月数
        val investmentCost: Double    // 培养投入（万元）
    )

    /**
     * 根据教师当前状态给出培养建议
     */
    fun getTrainingAdvice(teachers: List<Teacher>, campusLevel: Int): List<TeacherTrainingAdvice> {
        return teachers.filter { it.isWorking }.take(5).mapNotNull { teacher ->
            val level = teacher.level
            val (path, months, cost) = when {
                level == TeacherLevel.C && teacher.averageSkill < 40 -> Triple(
                    "基础教研能力提升", 6, 2.0
                )
                level == TeacherLevel.C && teacher.averageSkill >= 40 -> Triple(
                    "晋升B级教师考核准备", 4, 3.0
                )
                level == TeacherLevel.B && teacher.averageSkill < 60 -> Triple(
                    "学科带头人培养", 8, 5.0
                )
                level == TeacherLevel.B && teacher.averageSkill >= 60 -> Triple(
                    "冲刺A级教师评定", 6, 8.0
                )
                level == TeacherLevel.A -> Triple(
                    "名师工作室建设", 12, 15.0
                )
                level == TeacherLevel.S -> Triple(
                    "特级教师/教育家型教师", 24, 30.0
                )
                else -> return@mapNotNull null
            }
            TeacherTrainingAdvice(
                teacherId = teacher.id,
                teacherName = teacher.name,
                currentLevel = level.name,
                suggestedPath = path,
                estimatedMonths = months,
                investmentCost = cost * (1.0 + (campusLevel - 1) * 0.2) // 高等级学校培养成本更高
            )
        }
    }

    // ═══════════════════════════════════════════
    // P3-2: 负债/破产
    // ═══════════════════════════════════════════

    data class FinancialWarning(
        val level: WarningLevel,
        val message: String,
        val monthsRemaining: Int? // 剩余月数（破产倒计时）
    )

    enum class WarningLevel { NONE, LOSS_STREAK, DEBT, CRITICAL_DEBT, IMMINENT_BANKRUPTCY }

    fun checkFinancialHealth(cash: Double, monthlyProfit: Double): FinancialWarning {
        if (monthlyProfit < 0) consecutiveLossMonths++ else consecutiveLossMonths = 0

        return when {
            cash < -100.0 -> FinancialWarning(
                WarningLevel.IMMINENT_BANKRUPTCY,
                "负债超过100万！学校将在6个月内破产！立即裁员或寻求资金！",
                6
            )
            cash < -50.0 -> FinancialWarning(
                WarningLevel.CRITICAL_DEBT,
                "严重负债！必须立即削减开支或增加收入！",
                null
            )
            cash < 0.0 -> FinancialWarning(
                WarningLevel.DEBT,
                "学校已经负债${String.format("%.1f", -cash)}万元，请尽快扭亏为盈。",
                null
            )
            consecutiveLossMonths >= 3 -> FinancialWarning(
                WarningLevel.LOSS_STREAK,
                "连续${consecutiveLossMonths}个月亏损！财务状况堪忧，建议调整策略。",
                null
            )
            else -> FinancialWarning(WarningLevel.NONE, "", null)
        }
    }

    // ═══════════════════════════════════════════
    // 持久化
    // ═══════════════════════════════════════════

    fun toJson(): String {
        return try {
            val data = PressureState(
                lastRaiseMonths = lastRaiseMonth.toMap(),
                contractExpiries = contractExpiry.toMap(),
                consecutiveLossMonths = consecutiveLossMonths,
                consecutiveRankingDecline = consecutiveRankingDecline,
                lastYearGraduationRate = lastYearGraduationRate,
                lastExamProcessingYear = lastExamProcessingYear,
                failedEnrollmentSeasons = failedEnrollmentSeasons
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<PressureState>(json)
            val restoredLastRaiseMonths = data.lastRaiseMonths.toMap()
            val restoredContractExpiries = data.contractExpiries.toMap()

            lastRaiseMonth.clear()
            lastRaiseMonth.putAll(restoredLastRaiseMonths)
            contractExpiry.clear()
            contractExpiry.putAll(restoredContractExpiries)
            renewalPending.clear()
            consecutiveLossMonths = data.consecutiveLossMonths
            consecutiveRankingDecline = data.consecutiveRankingDecline
            lastYearGraduationRate = data.lastYearGraduationRate
            lastExamProcessingYear = data.lastExamProcessingYear
            failedEnrollmentSeasons = data.failedEnrollmentSeasons
        } catch (e: Exception) {
            throw IllegalArgumentException("PressureSystemManager.restoreFromJson failed", e)
        }
    }

    fun reset() {
        lastRaiseMonth.clear()
        contractExpiry.clear()
        monthlyComplaintCount = 0
        consecutiveLossMonths = 0
        consecutiveRankingDecline = 0
        lastYearGraduationRate = -1f
        lastExamProcessingYear = 0
        failedEnrollmentSeasons = 0
    }

    @Serializable
    data class PressureState(
        val lastRaiseMonths: Map<String, Int> = emptyMap(),
        val contractExpiries: Map<String, Int> = emptyMap(),
        val consecutiveLossMonths: Int = 0,
        val consecutiveRankingDecline: Int = 0,
        val lastYearGraduationRate: Float = -1f,
        val lastExamProcessingYear: Int = 0,
        val failedEnrollmentSeasons: Int = 0
    )
}
