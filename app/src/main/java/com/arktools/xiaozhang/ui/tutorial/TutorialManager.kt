package com.arktools.xiaozhang.ui.tutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 教程引导管理器 — 管理剧情式分步新手引导的全部状态
 *
 * 三种引导模式：
 * 1. STORY — 剧情对话（底部对话框，打字机效果，点击继续）
 * 2. HIGHLIGHT — 高亮指引（框选某个区域，旁边浮窗解释）
 * 3. ACTION — 操作等待（提示玩家执行某个操作，完成后自动进入下一步）
 */

enum class TutorialMode {
    STORY,      // 剧情对话
    HIGHLIGHT,  // 高亮指引
    ACTION      // 等待操作
}

/**
 * 高亮目标区域标识
 */
enum class HighlightTarget {
    NONE,
    TOP_BAR,            // 顶部状态栏
    STATUS_BAR,         // 资金/声誉/收入条
    TAB_OVERVIEW,       // 底部-总览
    TAB_TEACHING,       // 底部-教学
    TAB_TEACHER,        // 底部-教师
    TAB_RESEARCH,       // 底部-科研
    TAB_DISTRICT,       // 底部-合作区
    PAUSE_BUTTON,       // 暂停按钮
    SPEED_BUTTON,       // 速度按钮
    FULL_SCREEN         // 全屏（不需要具体高亮区域）
}

/**
 * 完成条件类型
 */
enum class CompletionCondition {
    TAP_CONTINUE,       // 点击任意继续
    TAP_TAB_TEACHER,    // 点击教师tab
    TAP_TAB_TEACHING,   // 点击教学tab
    TAP_TAB_RESEARCH,   // 点击科研tab
    TAP_TAB_OVERVIEW,   // 点击总览tab
    TAP_TAB_DISTRICT,   // 点击合作区tab
    HIRE_TEACHER,       // 完成招聘教师
    CONFIGURE_TEACHING, // 完成教学配置（班型确认）
    NAVIGATE_STUDENT,   // 进入学生管理页
    NAVIGATE_FACILITY,  // 进入设施管理页
    NAVIGATE_REPORT,    // 进入数据报表页
    WAIT_ENROLLMENT,    // 等待学生入学（学生数 > 0）
    WAIT_GAME_RESUME,   // 等待游戏恢复运行（玩家点了继续/加速）
    AUTO_NEXT           // 自动继续（有延时的）
}

/**
 * 单步教程数据
 */
data class TutorialStepData(
    val mode: TutorialMode,
    val speaker: String? = null,          // 对话人（STORY模式）
    val text: String,                     // 主文本
    val subText: String? = null,          // 副文本/提示
    val highlightTarget: HighlightTarget = HighlightTarget.NONE,  // 高亮目标
    val completionCondition: CompletionCondition = CompletionCondition.TAP_CONTINUE,
    val arrowDirection: ArrowDirection = ArrowDirection.NONE,  // 箭头指向
    val unpauseGame: Boolean = false      // 此步骤开始时取消暂停，让游戏跑起来
)

enum class ArrowDirection {
    NONE, UP, DOWN, LEFT, RIGHT
}

/**
 * 全部教程步骤定义 — 大学经营模拟完整引导（实操版）
 *
 * 核心设计理念：
 * - 每个功能先介绍再实操
 * - 让玩家亲手操作，完成后才能继续
 * - 包含完整的第一个月游戏循环体验
 * - 剧情丰富有代入感
 */
val ALL_TUTORIAL_STEPS: List<TutorialStepData> = buildList {

    // ═══════════════════════════════════════════════
    // 第一幕：剧情开场（建立代入感）
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "旁白",
        text = "新学年伊始，一纸任命改变了你的命运——\n\n你被委任为一所全新的大学校长，\n从零开始，建设学院、科研平台与校园共同体。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "校长，社会对这所新大学寄予厚望！\n启动资金500万，校区已经批好了。\n\n第一届毕业生的就业质量、科研成果和社会贡献，\n将决定这所大学能走多远。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "你（内心）",
        text = "500万启动资金……要招教师、建学院、做科研、\n还要给学生提供完整的校园生活。\n\n压力山大，但这正是我梦想中的舞台！\n一步一步来，把大学办出自己的特色。"
    ))

    // ═══════════════════════════════════════════════
    // 第二幕：界面认知（认识你的工具）
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "这是学校状态栏",
        subText = "资金：大学的现金流，红色代表持续亏损\n学术声誉：影响招生、师资与合作\n校园等级：影响容量、专业和设施\n\n时刻关注资金与声誉，把每一笔预算投入长期能力！",
        highlightTarget = HighlightTarget.STATUS_BAR,
        arrowDirection = ArrowDirection.UP
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "底部五大核心功能",
        subText = "「总览」— 大学经营全局\n「学院」— 专业、课程与人才培养\n「师资」— 招聘与发展教师团队\n「科研」— 研究项目与学术成果\n「社会」— 校友、企业和城市合作",
        highlightTarget = HighlightTarget.TAB_OVERVIEW,
        arrowDirection = ArrowDirection.DOWN
    ))

    // ═══════════════════════════════════════════════
    // 第三幕：实操——招聘教师
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "校长，大学办学第一步是组建师资与研究团队！\n\n没有教师就没有专业质量，没有研究者就没有学术竞争力。\n建议先招4~6位覆盖基础专业和科研方向的教师。\n\n我带您去「师资」页面看看。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "点击底部「师资」标签",
        subText = "去师资管理页面招聘你的第一批教师",
        highlightTarget = HighlightTarget.TAB_TEACHER,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_TEACHER
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "这里展示了所有在职教师和研究人员的信息。\n\n点击右下角的「+」按钮开始招聘：\n• 选择招聘渠道（社招/校招/猎头）\n• 查看教学、研究、管理和心理能力\n• 确认录用\n\n注意：薪资是长期支出，但人才是大学最重要的资产！"
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "请招聘至少1位教师或研究人员",
        subText = "点击右下角「+」→ 选择渠道 → 选择一位教师或研究人员录用\n招聘成功后教程自动继续",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.HIRE_TEACHER
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "太好了！第一位教师已经到岗！\n\n大学师资的关键属性：\n• 教学能力 — 影响人才培养质量\n• 研究能力 — 决定科研项目与学术声誉\n• 疲劳度 — 过高会降低产出\n• 忠诚度 — 影响团队稳定和人才流失\n\n后续还要搭建完整的学院梯队。"
    ))

    // ═══════════════════════════════════════════════
    // 第四幕：实操——配置学院与专业
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "后勤主管",
        text = "后勤主管，校区已经有基础教学空间。\n\n大学的第一块硬资产建议优先投入学院教室，\n之后再扩建实验室、图书馆、宿舍和创新中心。\n\n设施决定专业容量，也决定学生体验和科研上限。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "有了基础师资和教学空间，接下来配置专业与培养方案！\n\n大学经营的核心在这里：\n• 开设哪些专业，形成什么学科特色？\n• 理论、实践、创新和国际化如何平衡？\n• 资源优先投给教学还是科研？\n\n这些决定学生质量、科研成果和未来声誉！"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "点击底部「学院」标签",
        subText = "去配置你的专业与人才培养方案：专业规模、培养强度、资源配置",
        highlightTarget = HighlightTarget.TAB_TEACHING,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_TEACHING
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "大学培养方案三大核心：\n\n① 专业结构\n   应用型、研究型、交叉学科逐步布局\n\n② 教学强度\n   轻松→正常→加强→高压\n   强度越高产出越快，但满意度与师生健康会下降\n\n③ 资源配置\n   实验室、图书馆和企业项目需要持续投入\n   好的方案要兼顾现金流与长期竞争力！"
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "请完成教学配置",
        subText = "用 +/- 按钮设置学院和专业的培养规模\n当前基础空间有限，建议先开1~2个特色专业\n确认后教程自动继续",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.CONFIGURE_TEACHING
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "专业与培养方案已经配置完毕！\n\n现在大学具备了基本办学条件：\n✓ 师资已到岗\n✓ 基础空间已就绪\n✓ 专业与培养方案已确认\n\n9月份迎新季，第一批本科生就会入学了！"
    ))

    // ═══════════════════════════════════════════════
    // 第五幕：实操——等待入学（核心体验！）
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "基本准备已就绪。\n\n师资到岗、空间开放、专业备案——\n万事俱备，马上进入9月迎新季！\n\n让我们看看第一批学生和他们的专业选择吧。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "正在招收第一批学生……",
        subText = "9月开学季，学生正在报名入学中\n稍等片刻，教程将自动继续",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.WAIT_ENROLLMENT,
        unpauseGame = true
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "年级主任",
        text = "校长！好消息！第一批新生已经完成报到！\n\n他们已经按照您配置的专业分流。\n以后每年9月都会有新生入学，\n毕业季则会检验大学的人才培养成果。\n\n现在去「总览」看看大学运营数据吧！"
    ))

    // ═══════════════════════════════════════════════
    // 第六幕：认识总览页面（各功能入口）
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "回到「总览」看看全局",
        subText = "总览页展示经营数据和各功能模块入口",
        highlightTarget = HighlightTarget.TAB_OVERVIEW,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_OVERVIEW
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "总览页分为几个区域：\n\n① 顶部状态栏 — 资金、学术声誉等关键数据\n② 办学财务 — 学费、科研经费与运营支出\n③ 教师与研究团队 — 师资和研究能力\n④ 大学成长 — 学术声誉、星级和校园等级\n⑤ 更多功能 — 专业、设施、校友、合作等入口"
    ))

    // ═══════════════════════════════════════════════
    // 第七幕：实操——学生事务
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "年级主任",
        text = "学生是大学的核心！\n\n在「学生事务」中你可以查看：\n• 各专业学生数量和名单\n• 学业表现、满意度与健康\n• 兴趣、创造力、社交和发展方向\n\n学生体验过低会退学或转专业，人才培养需要长期经营。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "请在总览页点击「在校生概况」区域进入",
        subText = "在总览页上方找到「在校生概况」卡片并点击\n进入后可以查看学生详细信息",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.NAVIGATE_STUDENT
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "年级主任",
        text = "影响人才培养的关键因素：\n\n• 专业与课程 — 决定学习路径\n• 教师水平 — 教学与研究能力越高越好\n• 生源质量 — 招生传播和奖助政策共同影响\n• 设施条件 — 实验室、图书馆和宿舍提升体验\n\n高压能短期提升产出，但会伤害满意度与长期留存。"
    ))

    // ═══════════════════════════════════════════════
    // 第八幕：实操——校园设施
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "后勤主管",
        text = "大学硬件设施直接影响办学质量：\n\n• 学院教学楼 — 决定专业容量\n• 实验室 — 提升科研和实践教学\n• 图书馆 — 加速知识生产\n• 宿舍与食堂 — 影响学生体验和招生\n\n我带您去看看。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "请在总览页「更多功能」区找到「校园设施」并点击",
        subText = "往下滑找到「更多功能」区域\n点击「校园设施」卡片进入设施管理",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.NAVIGATE_FACILITY
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "后勤主管",
        text = "设施投资建议（按大学成长阶段）：\n\n① 学院教室 — 人才培养的容量基础\n② 图书馆 — 提升课程和科研效率\n③ 实验室 — 解锁科研项目和产业合作\n④ 宿舍食堂 — 稳定学生体验与招生\n\n建设、升级和维护都要费用，要结合现金流分阶段投入！"
    ))

    // ═══════════════════════════════════════════════
    // 第九幕：实操——数据报表
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "财务主管",
        text = "大学经营同样最重要的是现金流管理！\n\n收入来源：学费、科研经费、企业合作和校友支持\n支出大头：教师薪资 + 设施维护 + 学生服务 + 研究投入\n\n额外费用提醒：\n• 高水平研究项目需要持续经费\n• 宿舍食堂影响学生留存\n• 高压办学会带来长期健康成本\n\n招生与迎新：每年9月，需提前准备专业和师资"
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "请在「更多功能」找到「数据报表」并点击",
        subText = "回到总览页，往下滑找到「数据报表」卡片\n这里可以查看收支趋势和财务分析",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.NAVIGATE_REPORT
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "财务主管",
        text = "温馨提示：\n\n• 前6个月是保护期，不会破产\n• 连续3个月欠款超100万才触发危机\n• 危机时有紧急救助机会\n\n定期来看看报表，确保量入为出！"
    ))

    // ═══════════════════════════════════════════════
    // 第十幕：科研系统介绍
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "科研主任",
        text = "科研是大学的第二增长曲线！\n\n科研点数每天积累，用来解锁研究方法、项目和产业合作。\n学术成果会提升大学声誉，带来更好的生源和人才。\n\n优先建设基础研究能力，再根据特色选择重点方向。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "点击底部「科研」标签",
        subText = "科研是大学的长期投资，早布局早受益\n研究成果会提升学术声誉并解锁社会合作",
        highlightTarget = HighlightTarget.TAB_RESEARCH,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_RESEARCH
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "科研主任",
        text = "科研小贴士：\n\n• 图书馆和实验室可以加速科研积累\n• 学术会议能带来合作与声誉\n• 企业项目现金流好，但要承担交付压力\n• 国际合作需要足够的师资和大学等级\n\n让研究成果真正服务社会，才能形成长期正循环！"
    ))

    // ═══════════════════════════════════════════════
    // 第十一幕：社会合作介绍
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "最后看看「社会」板块：\n\n这里连接校友、企业、城市、政府与行业伙伴。\n合作能带来项目、捐赠、实习岗位和社会声誉，\n但也会带来舆论、合规和交付风险。\n\n大学不是封闭校园，社会连接决定你的上限。",
        highlightTarget = HighlightTarget.TAB_DISTRICT,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_DISTRICT
    ))

    // ═══════════════════════════════════════════════
    // 第十二幕：核心循环总结 + 更多功能提示
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "大学办学核心循环：\n\n每年9月 → 新生入学与专业分流\n每年6—7月 → 毕业与就业季\n\n人才培养 + 科研成果 → 学术声誉\n→ 更好的生源、师资与合作 → 更强的大学\n\n每一届学生，都会留下你的办学答案。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "影响大学竞争力的五大关键：\n\n① 专业结构（特色与就业方向）\n② 师资质量（教学、研究、管理能力）\n③ 生源与体验（招生、奖助、校园生活）\n④ 科研成果（项目、论文、转化和会议）\n⑤ 社会连接（校友、企业、城市与国际合作）\n\n五管齐下，把大学办成一座真正有影响力的知识共同体！"
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "总览页「更多功能」还有很多系统等你探索：\n\n• 大学政策 — 招生、奖助和资源配置\n• 学生社团 — 校园文化与综合发展\n• 校友与就业 — 毕业生反馈和社会网络\n• 招生传播 — 提升大学曝光与生源质量\n• 产业与社会合作 — 项目、实习与城市共建\n\n随着大学发展，更多研究和社会系统会逐步解锁。"
    ))

    // ═══════════════════════════════════════════════
    // 第十三幕：正式开始！
    // ═══════════════════════════════════════════════

    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "好了！基础你已经全部掌握了：\n\n✓ 招师资 → ✓ 建学院 → ✓ 配专业 → ✓ 等迎新\n✓ 看财务 → ✓ 做科研 → ✓ 扩设施 → ✓ 连社会\n\n接下来正式经营你的大学！\n让每一届毕业生，都成为校园最好的名片。\n\n祝你桃李满天下，学术通四海！"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "教程完成！正式开始经营",
        subText = "游戏已恢复运行，尽情享受校长生涯吧！\n\n💡 建议先用1倍速熟悉节奏，\n熟练后可以加速跳过等待期。",
        highlightTarget = HighlightTarget.TAB_OVERVIEW,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_OVERVIEW
    ))
}

/**
 * 教程状态管理器
 */
class TutorialManager {
    var currentStepIndex by mutableIntStateOf(0)
        private set

    var isActive by mutableStateOf(true)
        private set

    val currentStep: TutorialStepData
        get() = ALL_TUTORIAL_STEPS[currentStepIndex]

    val totalSteps: Int
        get() = ALL_TUTORIAL_STEPS.size

    val progress: Float
        get() = (currentStepIndex + 1f) / totalSteps

    val isLastStep: Boolean
        get() = currentStepIndex >= ALL_TUTORIAL_STEPS.size - 1

    /**
     * 前进到下一步
     */
    fun nextStep() {
        if (currentStepIndex < ALL_TUTORIAL_STEPS.size - 1) {
            currentStepIndex++
        } else {
            dismiss()
        }
    }

    /**
     * 跳过/关闭教程
     */
    fun dismiss() {
        isActive = false
    }

    /**
     * 通知教程某个动作已完成
     */
    fun notifyAction(condition: CompletionCondition) {
        if (!isActive) return
        val step = currentStep
        if (step.completionCondition == condition) {
            nextStep()
        }
    }

    /**
     * 通知tab切换
     */
    fun notifyTabChanged(tabIndex: Int) {
        if (!isActive) return
        when (tabIndex) {
            0 -> notifyAction(CompletionCondition.TAP_TAB_OVERVIEW)
            1 -> notifyAction(CompletionCondition.TAP_TAB_TEACHING)
            2 -> notifyAction(CompletionCondition.TAP_TAB_TEACHER)
            3 -> notifyAction(CompletionCondition.TAP_TAB_RESEARCH)
            4 -> notifyAction(CompletionCondition.TAP_TAB_DISTRICT)
        }
    }

    /**
     * 通知导航到子页面
     */
    fun notifyNavigateToSubPage(tabIndex: Int) {
        if (!isActive) return
        when (tabIndex) {
            8 -> notifyAction(CompletionCondition.NAVIGATE_STUDENT)
            7 -> notifyAction(CompletionCondition.NAVIGATE_FACILITY)
            11 -> notifyAction(CompletionCondition.NAVIGATE_REPORT)
        }
    }
}
