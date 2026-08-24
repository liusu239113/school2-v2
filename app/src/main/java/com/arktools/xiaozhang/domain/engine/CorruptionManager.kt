package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 腐败系统管理器
 * 处理所有灰色操作的执行、风险计算、暴露检测
 */
@Singleton
class CorruptionManager @Inject constructor() {

    /**
     * 执行贪污操作
     * @return 实际获得的个人收益，null表示操作失败
     */
    fun executeCorruptAct(
        principal: Principal,
        school: School,
        type: CorruptionType,
        amount: Double,
        description: String,
        witnessCount: Int = 1
    ): CorruptActResult {
        if (principal.isSuspended || principal.isArrested) {
            return CorruptActResult(false, 0.0, false, "当前处分期间不能执行任何校长操作。")
        }
        if (!amount.isFinite() || (type == CorruptionType.EMBEZZLE && (amount <= 0.0 || amount > school.cash))) {
            return CorruptActResult(false, 0.0, false, "资金状态异常，操作未执行。")
        }
        if (amount < 0.0 && principal.personalFunds < -amount) {
            return CorruptActResult(false, 0.0, false, "个人资金不足，操作未执行。")
        }

        val currentGameDay = calculateGameDay(school)
        val currentMonth = school.currentYear * 12 + school.currentMonth

        // 更新月度计数
        if (principal.lastCorruptMonth != currentMonth) {
            principal.lastCorruptMonth = currentMonth
            principal.corruptActsThisMonth = 0
        }
        principal.corruptActsThisMonth++

        // 记录腐败行为
        val act = CorruptAct(
            type = type,
            amount = amount,
            gameDay = currentGameDay,
            description = description,
            witnessCount = witnessCount
        )
        principal.recentCorruptActs.add(act)

        // 增加腐败值
        val corruptionGain = (type.severity * 2).coerceIn(2, 12)
        principal.corruptionLevel = (principal.corruptionLevel + corruptionGain).coerceIn(0, 100)

        // 个人获利
        principal.personalFunds += amount
        principal.totalEmbezzled += amount

        // 贪污公款类型：从学校资金中扣除（钱是从公账挪走的）
        if (type == CorruptionType.EMBEZZLE) {
            school.cash -= amount
        }

        // 降低理想值
        principal.idealismLevel = (principal.idealismLevel - type.severity).coerceAtLeast(0)

        // 即时暴露检查（高腐败值大幅提高暴露概率）
        val immediateExposureChance = calculateImmediateExposureChance(principal, type, witnessCount)
        if (Random.nextFloat() < immediateExposureChance) {
            act.isDiscovered = true
            return CorruptActResult(
                success = false,
                personalGain = 0.0,
                immediatelyExposed = true,
                exposureMessage = getImmediateExposureMessage(type)
            )
        }

        return CorruptActResult(
            success = true,
            personalGain = amount,
            immediatelyExposed = false
        )
    }

    /**
     * 每月风险检查 - 是否有旧案被翻出来
     */
    fun monthlyRiskCheck(principal: Principal, school: School): InvestigationEvent? {
        if (principal.isSuspended) return null
        if (principal.isArrested) return null
        if (principal.recentCorruptActs.isEmpty() && principal.totalEmbezzled <= 0) return null

        // 基础暴露概率
        var exposureChance = 0.05f  // 5%基础概率（提高基准）

        // 腐败值越高越容易被查（大幅加权）
        exposureChance += when {
            principal.corruptionLevel >= 90 -> 0.60f  // 90+：60%额外概率
            principal.corruptionLevel >= 70 -> 0.35f  // 70+：35%
            principal.corruptionLevel >= 50 -> 0.20f  // 50+：20%
            principal.corruptionLevel >= 30 -> 0.10f  // 30+：10%
            else -> principal.corruptionLevel * 0.003f
        }

        // 历史贪污总额越高越危险（基于逮捕阈值的比例来判断，适配学校规模）
        val arrestTh = getArrestThreshold(school)
        exposureChance += when {
            principal.totalEmbezzled >= arrestTh * 0.8 -> 0.30f  // 接近逮捕线：审计必查
            principal.totalEmbezzled >= arrestTh * 0.5 -> 0.15f  // 超过一半：高风险
            principal.totalEmbezzled >= arrestTh * 0.25 -> 0.08f // 四分之一
            principal.totalEmbezzled >= arrestTh * 0.1 -> 0.04f  // 十分之一
            else -> 0.0f
        }

        // 未发现的腐败行为越多越危险
        val undiscoveredActs = principal.recentCorruptActs.count { !it.isDiscovered }
        exposureChance += undiscoveredActs * 0.03f  // 提高每条证据的权重

        // 之前被调查过，后续更容易被盯上（累积效应）
        exposureChance += principal.timesInvestigated * 0.05f

        // 被抓过大问题的，直接高概率复查
        exposureChance += principal.timesCaughtMajor * 0.15f

        // 人脉高可以降低风险（但效果封顶）
        exposureChance *= (1.0f - principal.connectionLevel * 0.004f).coerceAtLeast(0.4f)

        // 学校声誉极高有保护效果（但腐败值过高时失效）
        if (school.reputation > 5000 && principal.corruptionLevel < 60) {
            exposureChance *= 0.7f
        }

        // 教师忠诚度低会增加举报风险
        val avgFactionRelation = principal.factionRelations.values.average().toFloat()
        if (avgFactionRelation < 30) {
            exposureChance *= 1.5f
        }

        // 腐败值100时保底50%检查概率
        if (principal.corruptionLevel >= 100) {
            exposureChance = exposureChance.coerceAtLeast(0.50f)
        }

        // 上限95%（留一丝侥幸）
        exposureChance = exposureChance.coerceAtMost(0.95f)

        if (Random.nextFloat() < exposureChance) {
            return triggerInvestigation(principal, school)
        }

        return null
    }

    /**
     * 触发调查事件
     */
    private fun triggerInvestigation(principal: Principal, school: School): InvestigationEvent {
        principal.timesInvestigated++

        // 确定调查发现的严重程度
        val undiscoveredActs = principal.recentCorruptActs.filter { !it.isDiscovered }
        val totalSeverity = undiscoveredActs.sumOf { it.type.severity }

        // 标记大部分行为被发现（调查越深入发现越多）
        val discoverRatio = when {
            principal.timesInvestigated >= 3 -> 0.9f  // 反复被查，几乎全暴露
            principal.timesInvestigated >= 2 -> 0.75f
            else -> 0.6f
        }
        val discoveredThisTime = undiscoveredActs.take((undiscoveredActs.size * discoverRatio).toInt().coerceAtLeast(1))
        discoveredThisTime.forEach { it.isDiscovered = true }

        val discoveredAmount = discoveredThisTime.sumOf { it.amount }

        // === 逮捕条件（任一满足即触发） ===
        // 逮捕阈值随学校规模提升（大学校资金流大，更难被发现异常）
        val arrestThreshold = getArrestThreshold(school)
        val shouldArrest = principal.totalEmbezzled >= arrestThreshold ||  // 累计贪污超阈值
                principal.timesCaughtMajor >= 3 ||               // 已经被重处3次还不收手
                (totalSeverity >= 25 && principal.corruptionLevel >= 85) ||  // 极其恶劣
                (principal.timesInvestigated >= 5 && principal.corruptionLevel >= 70)  // 屡教不改

        if (shouldArrest) {
            principal.isArrested = true
            return InvestigationEvent(
                result = InvestigationResult.ARRESTED,
                fineAmount = principal.personalFunds + discoveredAmount * 3,  // 没收全部个人资产+三倍罚款
                reputationLoss = 10000L,
                suspensionDays = 365,  // 一年（实际代表被带走）
                message = "纪检监察机关对校长立案审查！经调查发现累计贪污 ${String.format("%.1f", principal.totalEmbezzled)} 万元，" +
                        "证据确凿，已被移送司法机关处理。校长被警察带走！",
                discoveredActs = discoveredThisTime
            )
        }

        // === 降级条件 ===
        val demoteThreshold = arrestThreshold * 0.5  // 逮捕阈值的50%触发降级
        val shouldDemote = totalSeverity >= 15 && principal.timesCaughtMajor >= 1 ||
                principal.totalEmbezzled >= demoteThreshold

        if (shouldDemote) {
            return InvestigationEvent(
                result = InvestigationResult.DEMOTION,
                fineAmount = discoveredAmount * 2.5,
                reputationLoss = 5000L,
                suspensionDays = 180,  // 半年停职+降级
                message = "纪委深入调查发现严重违纪问题，涉案金额巨大！校长被免职降级，停职半年接受审查！再犯将移送司法！",
                discoveredActs = discoveredThisTime
            )
        }

        return when {
            totalSeverity >= 15 -> InvestigationEvent(
                result = InvestigationResult.SUSPENSION,
                fineAmount = discoveredAmount * 2,
                reputationLoss = 3000L,
                suspensionDays = 90,
                message = "纪委调查发现重大违纪违规行为，校长被停职反省三个月！",
                discoveredActs = discoveredThisTime
            )
            totalSeverity >= 8 -> InvestigationEvent(
                result = InvestigationResult.FINE,
                fineAmount = discoveredAmount * 1.5,
                reputationLoss = 1500L,
                suspensionDays = 0,
                message = "上级调查发现经济问题，处以罚款并通报批评。",
                discoveredActs = discoveredThisTime
            )
            totalSeverity >= 4 -> InvestigationEvent(
                result = InvestigationResult.WARNING,
                fineAmount = discoveredAmount * 0.5,
                reputationLoss = 500L,
                suspensionDays = 0,
                message = "教育局约谈校长，就部分管理问题提出警告。",
                discoveredActs = discoveredThisTime
            )
            else -> InvestigationEvent(
                result = InvestigationResult.CLEARED,
                fineAmount = 0.0,
                reputationLoss = 100L,
                suspensionDays = 0,
                message = "有人匿名举报，但调查未发现实质问题，虚惊一场。",
                discoveredActs = emptyList()
            )
        }
    }

    /**
     * 应用调查结果的惩罚
     * @return 需要从学校资金扣除的金额（万元），调用者应通过 schoolRepository.deductCash 执行
     */
    fun applyInvestigationPenalty(principal: Principal, school: School, event: InvestigationEvent): Double {
        var schoolFineAmount = 0.0

        when (event.result) {
            InvestigationResult.ARRESTED -> {
                // 逮捕：没收全部个人资产，学校也被巨额罚款
                principal.personalFunds = 0.0
                principal.isArrested = true
                principal.isSuspended = true
                principal.suspendedDaysLeft = event.suspensionDays
                principal.timesCaughtMajor++
                principal.personalReputation = 0
                schoolFineAmount = event.fineAmount  // 全部从学校扣
            }
            InvestigationResult.DEMOTION -> {
                // 降级：没收大部分个人资产
                val confiscated = (principal.personalFunds * 0.8).coerceAtLeast(0.0)
                principal.personalFunds -= confiscated
                schoolFineAmount = (event.fineAmount - confiscated).coerceAtLeast(0.0)
                principal.isSuspended = true
                principal.suspendedDaysLeft = event.suspensionDays
                principal.timesCaughtMajor++
                principal.personalReputation = (principal.personalReputation - 40).coerceAtLeast(0)
            }
            InvestigationResult.SUSPENSION -> {
                // 停职：罚款从个人资金扣，不够从学校扣
                if (principal.personalFunds >= event.fineAmount) {
                    principal.personalFunds -= event.fineAmount
                } else {
                    val remaining = event.fineAmount - principal.personalFunds
                    principal.personalFunds = 0.0
                    schoolFineAmount = remaining
                }
                principal.isSuspended = true
                principal.suspendedDaysLeft = event.suspensionDays
                principal.timesCaughtMajor++
                principal.personalReputation = (principal.personalReputation - 30).coerceAtLeast(0)
            }
            InvestigationResult.FINE -> {
                if (principal.personalFunds >= event.fineAmount) {
                    principal.personalFunds -= event.fineAmount
                } else {
                    val remaining = event.fineAmount - principal.personalFunds
                    principal.personalFunds = 0.0
                    schoolFineAmount = remaining
                }
                principal.timesCaughtMinor++
                principal.personalReputation = (principal.personalReputation - 15).coerceAtLeast(0)
            }
            InvestigationResult.WARNING -> {
                if (principal.personalFunds >= event.fineAmount) {
                    principal.personalFunds -= event.fineAmount
                } else {
                    val remaining = event.fineAmount - principal.personalFunds
                    principal.personalFunds = 0.0
                    schoolFineAmount = remaining
                }
                principal.timesCaughtMinor++
                principal.personalReputation = (principal.personalReputation - 10).coerceAtLeast(0)
            }
            InvestigationResult.CLEARED -> {
                // 无罪释放，无惩罚
            }
        }

        // 声誉损失（学校层面）
        school.reputation = (school.reputation - event.reputationLoss).coerceAtLeast(0)

        // 清除已发现的腐败记录（已处理）
        principal.recentCorruptActs.removeAll { it.isDiscovered }

        return schoolFineAmount
    }

    /**
     * 停职期间每日更新
     * @return true = 复职了; false = 还在停职/被逮捕
     */
    fun updateSuspension(principal: Principal): Boolean {
        if (!principal.isSuspended) return false
        // 被逮捕的永远不能复职
        if (principal.isArrested) return false

        principal.suspendedDaysLeft--
        if (principal.suspendedDaysLeft <= 0) {
            principal.isSuspended = false
            // 复职后腐败值下降（经历过打击会收敛）
            principal.corruptionLevel = (principal.corruptionLevel - 20).coerceAtLeast(0)
            return true  // 复职了
        }
        return false
    }

    /**
     * 获取当前可执行的灰色操作列表
     */
    fun getAvailableCorruptActions(principal: Principal, school: School): List<CorruptionOption> {
        if (principal.isSuspended) return emptyList()
        if (principal.isArrested) return emptyList()

        // 每月最多贪污2次（腐败值高时限制更严格）
        val currentMonth = school.currentYear * 12 + school.currentMonth
        if (principal.lastCorruptMonth == currentMonth) {
            val maxActsThisMonth = when {
                principal.corruptionLevel >= 80 -> 1  // 高腐败值时每月只能贪1次
                principal.corruptionLevel >= 50 -> 2  // 中等每月2次
                else -> 3  // 初期每月最多3次
            }
            if (principal.corruptActsThisMonth >= maxActsThisMonth) return emptyList()
        }

        val options = mutableListOf<CorruptionOption>()
        val currentGameDay = calculateGameDay(school)

        // 贪污公款 - 总是可用（有钱就能贪）
        // 贪污金额上限随学校等级/规模提升（大学校=更大的可操作空间）
        if (school.cash > 10.0) {
            val maxEmbezzle = getMaxEmbezzleAmount(school)
            val amount = (school.cash * 0.05).coerceIn(1.0, maxEmbezzle)
            options.add(CorruptionOption(
                type = CorruptionType.EMBEZZLE,
                description = "从学校账目中挪用 ${String.format("%.1f", amount)} 万到个人账户",
                amount = amount,
                riskLevel = calculateRiskLevel(principal, CorruptionType.EMBEZZLE, 2),
                witnessCount = 2
            ))
        }

        // 收受回扣 - 有采购/建设活动时
        if (school.facilities.isNotEmpty() || school.campusLevel >= 2) {
            val amount = Random.nextDouble(2.0, 8.0)
            options.add(CorruptionOption(
                type = CorruptionType.KICKBACK,
                description = "在设备采购中收取供应商回扣 ${String.format("%.1f", amount)} 万",
                amount = amount,
                riskLevel = calculateRiskLevel(principal, CorruptionType.KICKBACK, 1),
                witnessCount = 1
            ))
        }

        // 卖学位 - 招生季或有名气时
        if (school.reputation > 1000) {
            val amount = Random.nextDouble(5.0, 15.0)
            options.add(CorruptionOption(
                type = CorruptionType.SELL_ADMISSION,
                description = "收钱让一名成绩不达标的学生入学（收取 ${String.format("%.1f", amount)} 万）",
                amount = amount,
                riskLevel = calculateRiskLevel(principal, CorruptionType.SELL_ADMISSION, 3),
                witnessCount = 3
            ))
        }

        // 虚报人数 - 有政府补贴时
        if (school.campusLevel >= 3) {
            val amount = Random.nextDouble(3.0, 10.0)
            options.add(CorruptionOption(
                type = CorruptionType.FAKE_NUMBERS,
                description = "虚报在校生人数，多领取政府补贴 ${String.format("%.1f", amount)} 万",
                amount = amount,
                riskLevel = calculateRiskLevel(principal, CorruptionType.FAKE_NUMBERS, 2),
                witnessCount = 2
            ))
        }

        // 安插关系户 - 有空位时
        if (school.maxTeachers > 5) {
            options.add(CorruptionOption(
                type = CorruptionType.NEPOTISM,
                description = "安排亲属进学校任职（不花钱但占编制，获得人情）",
                amount = 0.0,
                riskLevel = calculateRiskLevel(principal, CorruptionType.NEPOTISM, 3),
                witnessCount = 3,
                connectionGain = 15
            ))
        }

        // 克扣工资 - 有教师时
        if (school.maxTeachers >= 5) {
            val amount = Random.nextDouble(1.0, 5.0)
            options.add(CorruptionOption(
                type = CorruptionType.WAGE_SKIM,
                description = "以'绩效考核'名义克扣部分教师工资 ${String.format("%.1f", amount)} 万入个人账户",
                amount = amount,
                riskLevel = calculateRiskLevel(principal, CorruptionType.WAGE_SKIM, 4),
                witnessCount = 4,
                loyaltyPenalty = -15
            ))
        }

        // 成绩造假 - 有课程时
        if (school.totalCoursesReleased >= 3) {
            options.add(CorruptionOption(
                type = CorruptionType.GRADE_FRAUD,
                description = "美化学生成绩数据以提升学校排名",
                amount = 0.0,
                riskLevel = calculateRiskLevel(principal, CorruptionType.GRADE_FRAUD, 3),
                witnessCount = 3,
                reputationGain = 500
            ))
        }

        // 掩盖事故 - 有设施时
        if (school.facilities.any { it.condition < 50 }) {
            val savingAmount = Random.nextDouble(3.0, 10.0)
            options.add(CorruptionOption(
                type = CorruptionType.COVER_UP,
                description = "隐瞒设施安全隐患不报告，省下维修费 ${String.format("%.1f", savingAmount)} 万",
                amount = savingAmount,
                riskLevel = calculateRiskLevel(principal, CorruptionType.COVER_UP, 2),
                witnessCount = 2
            ))
        }

        // 行贿检查人员 - 检查前可用
        if (school.campusLevel >= 3) {
            options.add(CorruptionOption(
                type = CorruptionType.BRIBE_INSPECTOR,
                description = "提前打点检查人员确保过关（花费个人资金5万）",
                amount = -5.0,  // 负数表示要花钱
                riskLevel = calculateRiskLevel(principal, CorruptionType.BRIBE_INSPECTOR, 1),
                witnessCount = 1,
                connectionGain = 10
            ))
        }

        // 挪用研究经费
        if (school.hasOwnTextbook || school.hasOwnTech) {
            val amount = Random.nextDouble(3.0, 8.0)
            options.add(CorruptionOption(
                type = CorruptionType.MISUSE_RESEARCH_FUNDS,
                description = "将部分研究经费挪作私用 ${String.format("%.1f", amount)} 万",
                amount = amount,
                riskLevel = calculateRiskLevel(principal, CorruptionType.MISUSE_RESEARCH_FUNDS, 2),
                witnessCount = 2
            ))
        }

        return options
    }

    private fun calculateImmediateExposureChance(
        principal: Principal,
        type: CorruptionType,
        witnessCount: Int
    ): Float {
        var chance = type.baseRisk
        chance += witnessCount * 0.02f

        // 腐败值对即时暴露的影响大幅增强
        chance += when {
            principal.corruptionLevel >= 90 -> 0.35f  // 90+：几乎一定被发现
            principal.corruptionLevel >= 70 -> 0.20f
            principal.corruptionLevel >= 50 -> 0.12f
            principal.corruptionLevel >= 30 -> 0.06f
            else -> principal.corruptionLevel * 0.002f
        }

        // 之前被调查过，所有人都在盯着你
        chance += principal.timesInvestigated * 0.05f

        // 人脉降低即时暴露（但效果有限）
        chance *= (1.0f - principal.connectionLevel * 0.003f).coerceAtLeast(0.5f)

        // 上限60%（不是之前的25%），下限1%
        return chance.coerceIn(0.01f, 0.60f)
    }

    private fun calculateRiskLevel(principal: Principal, type: CorruptionType, witnessCount: Int): RiskLevel {
        val risk = calculateImmediateExposureChance(principal, type, witnessCount) +
                principal.corruptionLevel * 0.005f +
                principal.timesInvestigated * 0.05f
        return when {
            risk < 0.1f -> RiskLevel.LOW
            risk < 0.2f -> RiskLevel.MEDIUM
            risk < 0.35f -> RiskLevel.HIGH
            else -> RiskLevel.EXTREME
        }
    }

    private fun getImmediateExposureMessage(type: CorruptionType): String {
        return when (type) {
            CorruptionType.EMBEZZLE -> "会计发现账目异常，向上级报告了！"
            CorruptionType.KICKBACK -> "供应商被税务检查，牵出了回扣记录！"
            CorruptionType.SELL_ADMISSION -> "被拒绝的家长一气之下在网上发帖曝光！"
            CorruptionType.FAKE_NUMBERS -> "教育局抽查核实人数，发现数据不符！"
            CorruptionType.NEPOTISM -> "教师们私下议论纷纷，有人向教育局反映！"
            CorruptionType.WAGE_SKIM -> "教师比对工资条发现异常，联名投诉！"
            CorruptionType.GRADE_FRAUD -> "学生家长拿到真实排名数据，发现与学校公布不一致！"
            CorruptionType.COVER_UP -> "隐患终于出事了，有学生在劣质设施处受伤！"
            CorruptionType.BRIBE_INSPECTOR -> "检查人员被纪委约谈，供出了行贿者名单！"
            CorruptionType.MISUSE_RESEARCH_FUNDS -> "课题组教师发现经费到账金额与审批不一致！"
        }
    }

    /**
     * 获取贪污单次上限（万元）—— 随学校等级/资金规模动态调整
     * 玩家反馈：学校资产几十亿时，每次只能贪20万太荒谬
     */
    private fun getMaxEmbezzleAmount(school: School): Double {
        // 基于学校等级的基础上限
        val baseCap = when (school.campusLevel) {
            1 -> 20.0       // 乡镇学校：最多20万
            2 -> 50.0       // 区级学校：最多50万
            3 -> 150.0      // 市重点：最多150万
            4 -> 500.0      // 省示范：最多500万
            5 -> 1500.0     // 国家名校：最多1500万
            6 -> 5000.0     // 世界学府：最多5000万
            else -> 20.0
        }
        // 额外：如果现金特别多，允许贪更多（但不超过等级上限的2倍）
        val cashBonus = (school.cash * 0.01).coerceAtMost(baseCap)
        return baseCap + cashBonus
    }

    /**
     * 获取逮捕阈值（万元）—— 累计贪污超过此值即被逮捕
     * 大学校资金流大，审计难度高，阈值相应提高
     */
    private fun getArrestThreshold(school: School): Double {
        return when (school.campusLevel) {
            1 -> 300.0       // 乡镇学校：300万
            2 -> 800.0       // 区级学校：800万
            3 -> 2000.0      // 市重点：2000万
            4 -> 6000.0      // 省示范：6000万
            5 -> 20000.0     // 国家名校：2亿
            6 -> 60000.0     // 世界学府：6亿
            else -> 300.0
        }
    }

    private fun calculateGameDay(school: School): Int {
        return (school.currentYear - 1988) * 360 + (school.currentMonth - 1) * 30 + school.currentDay
    }
}

/**
 * 灰色操作选项（UI展示用）
 */
data class CorruptionOption(
    val type: CorruptionType,
    val description: String,
    val amount: Double,
    val riskLevel: RiskLevel,
    val witnessCount: Int = 1,
    val connectionGain: Int = 0,
    val loyaltyPenalty: Int = 0,
    val reputationGain: Long = 0
)

enum class RiskLevel(val displayName: String, val color: String) {
    LOW("低风险", "#4CAF50"),
    MEDIUM("中等风险", "#FF9800"),
    HIGH("高风险", "#F44336"),
    EXTREME("极高风险", "#9C27B0")
}

/**
 * 腐败操作执行结果
 */
data class CorruptActResult(
    val success: Boolean,
    val personalGain: Double,
    val immediatelyExposed: Boolean,
    val exposureMessage: String = ""
)

/**
 * 调查事件
 */
data class InvestigationEvent(
    val result: InvestigationResult,
    val fineAmount: Double,
    val reputationLoss: Long,
    val suspensionDays: Int,
    val message: String,
    val discoveredActs: List<CorruptAct>
)
