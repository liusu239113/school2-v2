package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.EventChoice
import com.arktools.xiaozhang.domain.model.EventConsequence
import com.arktools.xiaozhang.domain.model.GameEvent
import com.arktools.xiaozhang.domain.model.School
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 突发危机剧本管理器
 *
 * 提供多阶段、多选项的危机事件剧本，增加游戏紧张感和可玩性。
 * 每个危机剧本包含 2-4 个阶段，玩家在每个阶段做出决策，影响后续发展。
 *
 * 危机类型：
 * - FOOD_SAFETY: 食品安全事故
 * - EPIDEMIC: 突发传染病
 * - TEACHER_STRIKE: 教师集体抗议
 * - MEDIA_SCANDAL: 媒体负面曝光
 * - NATURAL_DISASTER: 自然灾害
 * - CYBER_BULLYING: 校园网络暴力
 * - PARENT_PROTEST: 家长集体维权
 * - FINANCIAL_FRAUD: 财务造假举报
 */
@Singleton
class CrisisScenarioManager @Inject constructor() {

    private val _activeCrisis = MutableStateFlow<ActiveCrisis?>(null)
    val activeCrisis: StateFlow<ActiveCrisis?> = _activeCrisis.asStateFlow()

    // 冷却：上次危机结束后至少间隔60天
    private var lastCrisisEndDay: Int = -999
    private var totalCrisisCount: Int = 0

    /**
     * 每日检查是否应触发新危机（由 GameEngine 的日循环调用）
     * @return 如果触发了新危机，返回第一阶段的 GameEvent；否则返回 null
     */
    fun dailyCheck(school: School, daysSinceStart: Int): GameEvent? {
        // 已有活跃危机，不触发新的
        if (_activeCrisis.value != null) return null

        // 冷却期
        if (daysSinceStart - lastCrisisEndDay < 60) return null

        // 前6个月（180天）不触发危机
        val schoolAgeDays = (school.currentYear - school.foundedYear) * 360 +
                (school.currentMonth - 1) * 30 + school.currentDay
        if (schoolAgeDays < 180) return null

        // 每天0.3%概率触发危机（约每11个月一次）
        if (Random.nextFloat() > 0.003f) return null

        // 根据学校状态选择合适的危机类型
        val scenario = selectScenario(school) ?: return null
        val crisis = ActiveCrisis(
            scenario = scenario,
            currentPhase = 0,
            startDay = daysSinceStart,
            phaseHistory = mutableListOf()
        )
        _activeCrisis.value = crisis

        return buildPhaseEvent(scenario, 0)
    }

    /**
     * 玩家在危机中做出选择后，推进到下一阶段
     * @param choiceIndex 玩家选择的选项索引
     * @return 下一阶段事件，或 null 表示危机结束
     */
    fun advancePhase(choiceIndex: Int, daysSinceStart: Int): Pair<GameEvent?, EventConsequence> {
        val crisis = _activeCrisis.value ?: return null to EventConsequence()
        val scenario = crisis.scenario
        val phase = scenario.phases[crisis.currentPhase]

        val safeIndex = choiceIndex.coerceIn(0, phase.choices.size - 1)
        val choice = phase.choices[safeIndex]

        crisis.phaseHistory.add(safeIndex)

        val nextPhaseIndex = crisis.currentPhase + 1
        if (nextPhaseIndex >= scenario.phases.size) {
            // 危机结束
            _activeCrisis.value = null
            lastCrisisEndDay = daysSinceStart
            totalCrisisCount++

            // 生成结局总结
            val summary = generateSummary(scenario, crisis.phaseHistory)
            return summary to choice.consequence
        }

        // 推进到下一阶段
        crisis.currentPhase = nextPhaseIndex
        _activeCrisis.value = crisis

        val nextEvent = buildPhaseEvent(scenario, nextPhaseIndex)
        return nextEvent to choice.consequence
    }

    /**
     * 检查当前是否有活跃危机需要推进（用于恢复存档后）
     */
    fun hasActiveCrisis(): Boolean = _activeCrisis.value != null

    fun getCurrentPhaseEvent(): GameEvent? {
        val crisis = _activeCrisis.value ?: return null
        return buildPhaseEvent(crisis.scenario, crisis.currentPhase)
    }

    // ========== 危机剧本定义 ==========

    private fun selectScenario(school: School): CrisisScenario? {
        val candidates = mutableListOf<CrisisScenario>()

        // 食品安全 - 有食堂设施时
        if (school.campusLevel >= 2) {
            candidates.add(foodSafetyCrisis())
        }

        // 传染病 - 学生多时
        if (school.campusLevel >= 3) {
            candidates.add(epidemicCrisis())
        }

        // 教师集体抗议 - 教师多时
        if (school.maxTeachers >= 10) {
            candidates.add(teacherStrikeCrisis())
        }

        // 媒体负面曝光 - 声誉高时更可能被盯上
        if (school.reputation > 3000) {
            candidates.add(mediaScandalCrisis())
        }

        // 自然灾害 - 任何时候都可能
        if (school.campusLevel >= 2) {
            candidates.add(naturalDisasterCrisis())
        }

        // 网络暴力 - 现代化学校
        if (school.campusLevel >= 4 && school.currentYear >= 2010) {
            candidates.add(cyberBullyingCrisis())
        }

        // 家长集体维权 - 规模较大时
        if (school.campusLevel >= 3 && school.reputation > 1000) {
            candidates.add(parentProtestCrisis())
        }

        // 财务造假举报 - 资金量大时
        if (school.totalRevenue > 500.0) {
            candidates.add(financialFraudCrisis())
        }

        return candidates.randomOrNull()
    }

    private fun buildPhaseEvent(scenario: CrisisScenario, phaseIndex: Int): GameEvent {
        val phase = scenario.phases[phaseIndex]
        val prefix = if (phaseIndex == 0) "[突发危机] " else "[危机进展] "
        return GameEvent.ChoiceEvent(
            title = "$prefix${scenario.title}",
            message = phase.description,
            choices = phase.choices.map { choice ->
                EventChoice(choice.text, choice.consequence)
            }
        )
    }

    private fun generateSummary(scenario: CrisisScenario, choices: List<Int>): GameEvent {
        // 根据选择计算总体结果
        var totalCash = 0.0
        var totalRep = 0L
        choices.forEachIndexed { i, choiceIdx ->
            val phase = scenario.phases[i]
            val safeIdx = choiceIdx.coerceIn(0, phase.choices.size - 1)
            totalCash += phase.choices[safeIdx].consequence.cashChange
            totalRep += phase.choices[safeIdx].consequence.reputationChange
        }

        val outcome = when {
            totalRep > 0 -> "经过妥善处理，学校声誉不降反升，危机变成了展示实力的机会！"
            totalRep > -500 -> "危机已平息，学校损失可控，运营恢复正常。"
            totalRep > -1500 -> "危机造成了不小的损失，需要一段时间恢复。"
            else -> "危机处理不理想，学校遭受重创，需要很长时间重建声誉。"
        }

        val cashImpact = when {
            totalCash > 0.0 -> "+${String.format("%.1f", totalCash)}万"
            totalCash < 0.0 -> "${String.format("%.1f", totalCash)}万"
            else -> "0.0万"
        }
        val impactMessage =
            "$outcome\n\n最终影响：现金${cashImpact}，声誉${if (totalRep >= 0) "+" else ""}${totalRep}"

        return if (totalRep >= 0) {
            GameEvent.PositiveEvent(
                title = "[危机结束] ${scenario.title}",
                message = impactMessage,
                bonusReputation = 0L,
                bonusCash = 0.0
            )
        } else {
            GameEvent.NegativeEvent(
                title = "[危机结束] ${scenario.title}",
                message = impactMessage,
                penaltyReputation = 0L,
                penaltyCash = 0.0
            )
        }
    }

    // ========== 8个危机剧本 ==========

    private fun foodSafetyCrisis() = CrisisScenario(
        id = "food_safety",
        title = "食品安全事故",
        phases = listOf(
            CrisisPhase(
                description = "紧急！学校食堂今天中午有12名学生出现呕吐、腹痛症状，" +
                        "已有家长在微信群里激烈讨论。初步怀疑是食材变质引起的食物中毒。\n\n" +
                        "你必须立刻做出反应：",
                choices = listOf(
                    CrisisChoice(
                        text = "立即停业整顿食堂 + 送学生就医 + 主动报告卫生局",
                        consequence = EventConsequence(cashChange = -8.0, reputationChange = -200)
                    ),
                    CrisisChoice(
                        text = "先私下联系家长安抚，暂不声张",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = -100)
                    ),
                    CrisisChoice(
                        text = "推卸责任给食堂外包商，与学校切割",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -300)
                    )
                )
            ),
            CrisisPhase(
                description = "事件发酵到第二天。当地媒体记者已经在校门口蹲守，" +
                        "家长群里的截图在朋友圈传播。卫生局通知明天上午来校检查。\n\n" +
                        "你接下来怎么做？",
                choices = listOf(
                    CrisisChoice(
                        text = "召开新闻发布会公开信息，承诺彻查整改",
                        consequence = EventConsequence(cashChange = -5.0, reputationChange = 200)
                    ),
                    CrisisChoice(
                        text = "只接受卫生局检查，拒绝媒体采访",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -400)
                    ),
                    CrisisChoice(
                        text = "联系媒体朋友试图压下新闻",
                        consequence = EventConsequence(cashChange = -10.0, reputationChange = -600)
                    )
                )
            ),
            CrisisPhase(
                description = "一周后，卫生局出具了检查报告。所有患病学生已康复出院。\n" +
                        "现在需要决定后续的善后方案：",
                choices = listOf(
                    CrisisChoice(
                        text = "全额赔偿医疗费 + 全校免费体检 + 升级食堂设备",
                        consequence = EventConsequence(cashChange = -15.0, reputationChange = 600)
                    ),
                    CrisisChoice(
                        text = "赔偿医疗费 + 更换食堂承包商",
                        consequence = EventConsequence(cashChange = -8.0, reputationChange = 200)
                    ),
                    CrisisChoice(
                        text = "只赔偿医疗费，其他不变",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = -200)
                    )
                )
            )
        )
    )

    private fun epidemicCrisis() = CrisisScenario(
        id = "epidemic",
        title = "突发传染病疫情",
        phases = listOf(
            CrisisPhase(
                description = "校医室报告：今天有23名学生出现发热症状，初步判断为流感集中爆发。" +
                        "疾控中心建议密切关注，如果确认为甲流可能需要停课。\n\n" +
                        "紧急决策：",
                choices = listOf(
                    CrisisChoice(
                        text = "立即全校停课一周，启动线上教学",
                        consequence = EventConsequence(cashChange = -5.0, reputationChange = 100)
                    ),
                    CrisisChoice(
                        text = "受影响班级停课，其他班级加强防护继续上课",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = -100)
                    ),
                    CrisisChoice(
                        text = "加强消毒通风，暂不停课观察",
                        consequence = EventConsequence(cashChange = -1.0, reputationChange = -300)
                    )
                )
            ),
            CrisisPhase(
                description = "三天后，确诊病例增加到47人（含3名教师）。" +
                        "部分家长在教育局投诉，要求学校承担治疗费用并延长假期。\n" +
                        "教育局来电询问情况：",
                choices = listOf(
                    CrisisChoice(
                        text = "主动提交详细防控报告，申请延长停课并承担部分费用",
                        consequence = EventConsequence(cashChange = -10.0, reputationChange = 300)
                    ),
                    CrisisChoice(
                        text = "按最低要求汇报，强调已采取措施",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -200)
                    ),
                    CrisisChoice(
                        text = "聘请公关团队帮助应对舆论",
                        consequence = EventConsequence(cashChange = -8.0, reputationChange = 0)
                    )
                )
            ),
            CrisisPhase(
                description = "两周后疫情基本控制，准备复课。家长对复课安全仍有顾虑。\n" +
                        "如何恢复正常教学秩序？",
                choices = listOf(
                    CrisisChoice(
                        text = "邀请疾控专家到校检查并出具安全证明，召开家长会说明",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = 400)
                    ),
                    CrisisChoice(
                        text = "发通知宣布复课，提供消毒记录",
                        consequence = EventConsequence(cashChange = -1.0, reputationChange = 0)
                    ),
                    CrisisChoice(
                        text = "分批复课，持续一周过渡期",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = 200)
                    )
                )
            )
        )
    )

    private fun teacherStrikeCrisis() = CrisisScenario(
        id = "teacher_strike",
        title = "教师集体抗议",
        phases = listOf(
            CrisisPhase(
                description = "今早，超过半数教师联名提交了一份请愿书，要求涨薪20%、" +
                        "增加带薪假期、改善办公环境。他们表示如果一周内得不到回复，" +
                        "将集体拒绝加班和课外辅导。\n\n领头的是两位资深骨干教师。",
                choices = listOf(
                    CrisisChoice(
                        text = "立即与教师代表谈判，承诺涨薪15%",
                        consequence = EventConsequence(cashChange = -12.0, teacherLoyaltyChange = 20)
                    ),
                    CrisisChoice(
                        text = "召开全体大会倾听诉求，但暂不承诺具体方案",
                        consequence = EventConsequence(cashChange = 0.0, teacherLoyaltyChange = -5)
                    ),
                    CrisisChoice(
                        text = "警告带头教师注意职业操守，否则按旷工处理",
                        consequence = EventConsequence(cashChange = 0.0, teacherLoyaltyChange = -25, reputationChange = -200)
                    )
                )
            ),
            CrisisPhase(
                description = "第三天，事态升级。有教师开始在社交媒体上发布'学校压榨教师'的帖子，" +
                        "被本地教育圈大量转发。同时有两位骨干教师已收到竞争学校的offer。\n\n" +
                        "局面如何控制？",
                choices = listOf(
                    CrisisChoice(
                        text = "个别约谈骨干教师，开出优厚留任条件",
                        consequence = EventConsequence(cashChange = -8.0, teacherLoyaltyChange = 15, reputationChange = -100)
                    ),
                    CrisisChoice(
                        text = "公开发布教师福利改善计划（涨薪+假期+培训基金）",
                        consequence = EventConsequence(cashChange = -15.0, teacherLoyaltyChange = 30, reputationChange = 200)
                    ),
                    CrisisChoice(
                        text = "招聘新教师替代，不向威胁妥协",
                        consequence = EventConsequence(cashChange = -5.0, teacherLoyaltyChange = -30, reputationChange = -300)
                    )
                )
            ),
            CrisisPhase(
                description = "一周后，需要公布最终解决方案。教师们等待你的正式答复。",
                choices = listOf(
                    CrisisChoice(
                        text = "发布全面改革方案：涨薪+绩效奖金+职业发展通道",
                        consequence = EventConsequence(cashChange = -20.0, teacherLoyaltyChange = 35, reputationChange = 300)
                    ),
                    CrisisChoice(
                        text = "涨薪10%，改善办公环境，其他待议",
                        consequence = EventConsequence(cashChange = -10.0, teacherLoyaltyChange = 10, reputationChange = 0)
                    ),
                    CrisisChoice(
                        text = "维持现状，接受部分教师离职",
                        consequence = EventConsequence(cashChange = 0.0, teacherLoyaltyChange = -20, reputationChange = -500)
                    )
                )
            )
        )
    )

    private fun mediaScandalCrisis() = CrisisScenario(
        id = "media_scandal",
        title = "媒体负面曝光",
        phases = listOf(
            CrisisPhase(
                description = "一篇题为《名校的背后：XX学校管理乱象调查》的文章在社交媒体刷屏，" +
                        "文中引用了'匿名教师'和'离校家长'的说法，指控学校存在过度收费、" +
                        "教学注水等问题。虽然多处失实，但传播量已超10万+。\n\n" +
                        "你的第一反应是：",
                choices = listOf(
                    CrisisChoice(
                        text = "发布官方声明逐条驳斥不实内容，附上证据",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = -300)
                    ),
                    CrisisChoice(
                        text = "聘请律师发律师函要求撤稿道歉",
                        consequence = EventConsequence(cashChange = -5.0, reputationChange = -500)
                    ),
                    CrisisChoice(
                        text = "邀请家长和媒体来校实地参观，用事实说话",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = 100)
                    )
                )
            ),
            CrisisPhase(
                description = "事件持续发酵第三天。已有电视台记者来校门口拍摄，" +
                        "部分在读学生家长开始动摇。校内有教师疑似是'匿名爆料人'。\n\n" +
                        "你需要同时处理内外两个战场：",
                choices = listOf(
                    CrisisChoice(
                        text = "对外：接受权威媒体专访澄清；对内：安抚教师不搞清查",
                        consequence = EventConsequence(cashChange = -5.0, reputationChange = 300, teacherLoyaltyChange = 10)
                    ),
                    CrisisChoice(
                        text = "对外：保持沉默等热度过去；对内：追查爆料人",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -600, teacherLoyaltyChange = -15)
                    ),
                    CrisisChoice(
                        text = "对外：家长开放日+教学成果展；对内：全员沟通会坦诚相对",
                        consequence = EventConsequence(cashChange = -8.0, reputationChange = 500, teacherLoyaltyChange = 15)
                    )
                )
            ),
            CrisisPhase(
                description = "第七天，舆论热度开始降温。但这次事件暴露出学校在公关和沟通方面的不足。\n" +
                        "是否建立长效机制？",
                choices = listOf(
                    CrisisChoice(
                        text = "成立品牌公关部，建立舆情监控机制",
                        consequence = EventConsequence(cashChange = -10.0, reputationChange = 400)
                    ),
                    CrisisChoice(
                        text = "加强家校沟通渠道，定期发布学校动态",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = 300)
                    ),
                    CrisisChoice(
                        text = "事情过了就过了，不额外投入",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -100)
                    )
                )
            )
        )
    )

    private fun naturalDisasterCrisis() = CrisisScenario(
        id = "natural_disaster",
        title = "暴雨洪涝灾害",
        phases = listOf(
            CrisisPhase(
                description = "气象台发布红色暴雨预警！连续三天强降雨导致学校低层教室严重积水，" +
                        "操场变成'泳池'，部分教学设备受损。目前仍有200多名学生滞留校内。\n\n" +
                        "紧急决策：",
                choices = listOf(
                    CrisisChoice(
                        text = "立即启动应急预案：安排车辆接送学生，教师护送到安全区域",
                        consequence = EventConsequence(cashChange = -5.0, reputationChange = 200)
                    ),
                    CrisisChoice(
                        text = "就地安置：让学生留在高层教室过夜，联系家长说明情况",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = 0)
                    ),
                    CrisisChoice(
                        text = "让学生自行联系家长来接",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -400)
                    )
                )
            ),
            CrisisPhase(
                description = "暴雨过后，校园一片狼藉。初步统计：3间教室设备受损，体育馆屋顶漏水，" +
                        "操场跑道被毁。修复预估需要30-50万。\n" +
                        "保险公司表示部分损失可理赔，但流程需要1-2个月。",
                choices = listOf(
                    CrisisChoice(
                        text = "自费立即全面修复，不等保险（尽快恢复上课）",
                        consequence = EventConsequence(cashChange = -40.0, reputationChange = 400)
                    ),
                    CrisisChoice(
                        text = "先修复核心教学区，其他等保险理赔后再修",
                        consequence = EventConsequence(cashChange = -15.0, reputationChange = 100)
                    ),
                    CrisisChoice(
                        text = "发起社会捐赠倡议，借此机会筹集资金",
                        consequence = EventConsequence(cashChange = 5.0, reputationChange = -100)
                    )
                )
            ),
            CrisisPhase(
                description = "修复工作进行中，但下周就要期中考试了。部分教室仍在施工，" +
                        "学生无处自习，家长焦虑情绪升温。",
                choices = listOf(
                    CrisisChoice(
                        text = "租用附近会议中心作为临时教室，考试如期进行",
                        consequence = EventConsequence(cashChange = -8.0, reputationChange = 300)
                    ),
                    CrisisChoice(
                        text = "延期一周考试，加速修复",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = 0)
                    ),
                    CrisisChoice(
                        text = "改为线上考试",
                        consequence = EventConsequence(cashChange = -1.0, reputationChange = -100)
                    )
                )
            )
        )
    )

    private fun cyberBullyingCrisis() = CrisisScenario(
        id = "cyber_bullying",
        title = "校园网络暴力事件",
        phases = listOf(
            CrisisPhase(
                description = "一名初二女生的家长紧急来电：她的女儿被同学在社交平台上匿名辱骂、" +
                        "造谣长达两周，已经出现厌学和自残倾向。家长情绪激动，扬言要报警并向媒体曝光。\n\n" +
                        "这件事你怎么处理？",
                choices = listOf(
                    CrisisChoice(
                        text = "立即约见双方家长，启动校内调查，安排心理辅导",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = 0)
                    ),
                    CrisisChoice(
                        text = "先安抚受害者家长，私下处理施暴学生",
                        consequence = EventConsequence(cashChange = -1.0, reputationChange = -200)
                    ),
                    CrisisChoice(
                        text = "召开全校反霸凌大会，公开表态零容忍",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = 100)
                    )
                )
            ),
            CrisisPhase(
                description = "调查发现参与网暴的有5名学生，其中2人是'学霸'，" +
                        "家长背景较为复杂。受害女生家长坚持要求学校开除施暴者。\n" +
                        "施暴者家长则威胁：如果处分他们的孩子就'走关系搞你'。",
                choices = listOf(
                    CrisisChoice(
                        text = "按校规严肃处理：记过处分+强制转学（最严重的2人）",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = 300, teacherLoyaltyChange = 10)
                    ),
                    CrisisChoice(
                        text = "全部批评教育+写检讨+家长监督，不作正式处分",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -300)
                    ),
                    CrisisChoice(
                        text = "引入第三方调解机构处理，学校不直接做决定",
                        consequence = EventConsequence(cashChange = -5.0, reputationChange = 0)
                    )
                )
            ),
            CrisisPhase(
                description = "事件处理后的善后阶段。受害学生目前情绪稍有好转但仍不愿回校。\n" +
                        "这件事提醒你：学校缺乏系统的反霸凌机制。",
                choices = listOf(
                    CrisisChoice(
                        text = "建立完整反霸凌体系：匿名举报箱+心理咨询室+定期教育",
                        consequence = EventConsequence(cashChange = -10.0, reputationChange = 500)
                    ),
                    CrisisChoice(
                        text = "增加班级管理力度，班主任加强监督",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = 100)
                    ),
                    CrisisChoice(
                        text = "事情已经处理了，回归正常",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -200)
                    )
                )
            )
        )
    )

    private fun parentProtestCrisis() = CrisisScenario(
        id = "parent_protest",
        title = "家长集体维权",
        phases = listOf(
            CrisisPhase(
                description = "今天早上，30多名家长拉着横幅聚集在校门口，" +
                        "抗议学校近期的学费上涨和课后服务收费。有家长拍摄视频正在网上直播。\n" +
                        "现场情绪激动，已经影响到其他学生正常入校。\n\n" +
                        "你如何应对？",
                choices = listOf(
                    CrisisChoice(
                        text = "校长亲自到门口与家长对话，邀请代表进会议室协商",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -100)
                    ),
                    CrisisChoice(
                        text = "让副校长出面接待，自己在后方协调",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -300)
                    ),
                    CrisisChoice(
                        text = "报警处理，要求家长不要影响正常教学秩序",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -800)
                    )
                )
            ),
            CrisisPhase(
                description = "与家长代表谈判进入第二天。家长的核心诉求：\n" +
                        "1. 学费恢复原价\n2. 课后服务费减半\n3. 公开学校财务支出明细\n\n" +
                        "教育局也打来电话了解情况，要求尽快妥善解决。",
                choices = listOf(
                    CrisisChoice(
                        text = "全部接受家长诉求，息事宁人",
                        consequence = EventConsequence(cashChange = -15.0, reputationChange = 200)
                    ),
                    CrisisChoice(
                        text = "部分让步：学费不调，课后服务费降30%，公开部分支出",
                        consequence = EventConsequence(cashChange = -8.0, reputationChange = 100)
                    ),
                    CrisisChoice(
                        text = "解释涨价原因（成本上升），提供分期付款方案",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = -200)
                    )
                )
            ),
            CrisisPhase(
                description = "纠纷基本平息，但事件暴露了学校在定价和沟通上的问题。\n" +
                        "如何防止类似事件再次发生？",
                choices = listOf(
                    CrisisChoice(
                        text = "成立家长委员会，重大决策前听取家长意见",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = 500)
                    ),
                    CrisisChoice(
                        text = "每学期公布一次财务摘要，增加透明度",
                        consequence = EventConsequence(cashChange = -1.0, reputationChange = 300)
                    ),
                    CrisisChoice(
                        text = "加强事前沟通，涨价前先发通知征求意见",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = 200)
                    )
                )
            )
        )
    )

    private fun financialFraudCrisis() = CrisisScenario(
        id = "financial_fraud",
        title = "财务造假举报",
        phases = listOf(
            CrisisPhase(
                description = "教育局转来一封匿名举报信，指控学校存在'虚报支出、套取财政补贴'的问题。" +
                        "虽然学校并没有收到财政补贴（纯民办），但举报内容涉及一些模棱两可的账目处理。\n" +
                        "教育局要求你三天内提交书面说明。\n\n" +
                        "你怎么处理？",
                choices = listOf(
                    CrisisChoice(
                        text = "立即请专业会计师事务所做审计，用第三方报告自证清白",
                        consequence = EventConsequence(cashChange = -8.0, reputationChange = 200)
                    ),
                    CrisisChoice(
                        text = "自行整理账目写一份详细说明提交",
                        consequence = EventConsequence(cashChange = -1.0, reputationChange = -100)
                    ),
                    CrisisChoice(
                        text = "找关系打听是谁举报的，私下沟通解决",
                        consequence = EventConsequence(cashChange = -5.0, reputationChange = -400)
                    )
                )
            ),
            CrisisPhase(
                description = "教育局审查后认为'部分账目记录不够规范但不构成违法'。\n" +
                        "然而这件事不知怎么传到了家长耳中，'学校财务有问题'的传言开始流传。\n" +
                        "已有3位家长要求退学退费。",
                choices = listOf(
                    CrisisChoice(
                        text = "主动公布审计结论，开办家长说明会",
                        consequence = EventConsequence(cashChange = -3.0, reputationChange = 300)
                    ),
                    CrisisChoice(
                        text = "个别联系退学家长挽留",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = -100)
                    ),
                    CrisisChoice(
                        text = "不做公开回应，让谣言自然消散",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = -400)
                    )
                )
            ),
            CrisisPhase(
                description = "风波渐平。回顾此次事件，发现学校财务管理确实有些粗放。\n" +
                        "是否借此机会升级财务管理体系？",
                choices = listOf(
                    CrisisChoice(
                        text = "引入专业财务系统 + 聘请兼职财务顾问",
                        consequence = EventConsequence(cashChange = -10.0, reputationChange = 400)
                    ),
                    CrisisChoice(
                        text = "规范报销流程和审批制度",
                        consequence = EventConsequence(cashChange = -2.0, reputationChange = 200)
                    ),
                    CrisisChoice(
                        text = "维持现状，以后注意",
                        consequence = EventConsequence(cashChange = 0.0, reputationChange = 0)
                    )
                )
            )
        )
    )

    // ========== 序列化 ==========

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("lastCrisisEndDay", lastCrisisEndDay)
        obj.put("totalCrisisCount", totalCrisisCount)

        val crisis = _activeCrisis.value
        if (crisis != null) {
            val crisisObj = JSONObject()
            crisisObj.put("scenarioId", crisis.scenario.id)
            crisisObj.put("currentPhase", crisis.currentPhase)
            crisisObj.put("startDay", crisis.startDay)
            val historyArr = JSONArray()
            crisis.phaseHistory.forEach { historyArr.put(it) }
            crisisObj.put("phaseHistory", historyArr)
            obj.put("activeCrisis", crisisObj)
        }

        return obj.toString()
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val obj = JSONObject(json)
            lastCrisisEndDay = obj.optInt("lastCrisisEndDay", -999)
            totalCrisisCount = obj.optInt("totalCrisisCount", 0)

            if (obj.has("activeCrisis")) {
                val crisisObj = obj.getJSONObject("activeCrisis")
                val scenarioId = crisisObj.getString("scenarioId")
                val scenario = getScenarioById(scenarioId)
                if (scenario != null) {
                    val history = mutableListOf<Int>()
                    val historyArr = crisisObj.optJSONArray("phaseHistory")
                    if (historyArr != null) {
                        for (i in 0 until historyArr.length()) {
                            history.add(historyArr.getInt(i))
                        }
                    }
                    _activeCrisis.value = ActiveCrisis(
                        scenario = scenario,
                        currentPhase = crisisObj.getInt("currentPhase"),
                        startDay = crisisObj.getInt("startDay"),
                        phaseHistory = history
                    )
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("CrisisScenarioManager.restoreFromJson failed", e)
        }
    }

    private fun getScenarioById(id: String): CrisisScenario? {
        return when (id) {
            "food_safety" -> foodSafetyCrisis()
            "epidemic" -> epidemicCrisis()
            "teacher_strike" -> teacherStrikeCrisis()
            "media_scandal" -> mediaScandalCrisis()
            "natural_disaster" -> naturalDisasterCrisis()
            "cyber_bullying" -> cyberBullyingCrisis()
            "parent_protest" -> parentProtestCrisis()
            "financial_fraud" -> financialFraudCrisis()
            else -> null
        }
    }

    fun reset() {
        _activeCrisis.value = null
        lastCrisisEndDay = -999
        totalCrisisCount = 0
    }
}

// ========== 数据模型 ==========

data class CrisisScenario(
    val id: String,
    val title: String,
    val phases: List<CrisisPhase>
)

data class CrisisPhase(
    val description: String,
    val choices: List<CrisisChoice>
)

data class CrisisChoice(
    val text: String,
    val consequence: EventConsequence
)

data class ActiveCrisis(
    val scenario: CrisisScenario,
    var currentPhase: Int,
    val startDay: Int,
    val phaseHistory: MutableList<Int>
)
