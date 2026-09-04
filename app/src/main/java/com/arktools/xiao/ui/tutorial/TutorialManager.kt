package com.arktools.xiao.ui.tutorial

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
    STORY,
    HIGHLIGHT,
    ACTION
}

/**
 * 高亮目标。底部四主区沿用旧枚举名，避免覆盖层大改：
 * TAB_OVERVIEW = 校园，TAB_TEACHING = 治院，TAB_TEACHER = 人事，TAB_DISTRICT = 外联
 */
enum class HighlightTarget {
    NONE,
    TOP_BAR,
    STATUS_BAR,
    TAB_OVERVIEW,
    TAB_TEACHING,
    TAB_TEACHER,
    TAB_RESEARCH,
    TAB_DISTRICT,
    PAUSE_BUTTON,
    SPEED_BUTTON,
    FULL_SCREEN
}

enum class CompletionCondition {
    TAP_CONTINUE,
    TAP_TAB_TEACHER,
    TAP_TAB_TEACHING,
    TAP_TAB_RESEARCH,
    TAP_TAB_OVERVIEW,
    TAP_TAB_DISTRICT,
    HIRE_TEACHER,
    CONFIGURE_TEACHING,
    NAVIGATE_STUDENT,
    NAVIGATE_FACILITY,
    NAVIGATE_REPORT,
    WAIT_ENROLLMENT,
    WAIT_GAME_RESUME,
    AUTO_NEXT
}

data class TutorialStepData(
    val mode: TutorialMode,
    val speaker: String? = null,
    val text: String,
    val subText: String? = null,
    val highlightTarget: HighlightTarget = HighlightTarget.NONE,
    val completionCondition: CompletionCondition = CompletionCondition.TAP_CONTINUE,
    val arrowDirection: ArrowDirection = ArrowDirection.NONE,
    val unpauseGame: Boolean = false
)

enum class ArrowDirection {
    NONE, UP, DOWN, LEFT, RIGHT
}

/**
 * 对齐当前四主区：校园 / 治院 / 人事 / 外联。
 * 建筑自由建造是主循环，点建筑进入对应系统。
 */
val ALL_TUTORIAL_STEPS: List<TutorialStepData> = buildList {
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "旁白",
        text = "一纸任命，你成为这所新大学的校长。\n\n8月建校，9月迎新。先把校园铺开，再把人请来，大学才会真正运转起来。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "办学层次和公办/民办已经定了，规则完全不同。\n专科看就业，本科看培养与科研，公办有拨款但学费受管。\n\n先把第一周过好：建教室、招教师、配宿舍，9月才接得住学生。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "这是学校状态栏",
        subText = "经费：现金流，红了就要砍开支或等拨款\n声誉：影响招生、师资和排名\n校园等级：决定可建区域和设施上限",
        highlightTarget = HighlightTarget.STATUS_BAR,
        arrowDirection = ArrowDirection.UP
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "底部四个主区",
        subText = "「校园」— 大地图自由建造，点建筑进入系统\n「治院」— 预算、方针、教学科研\n「人事」— 招聘与教师发展\n「外联」— 排名、竞赛、校友",
        highlightTarget = HighlightTarget.TAB_OVERVIEW,
        arrowDirection = ArrowDirection.DOWN
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "后勤主管",
        text = "校园是你的工作台，不是背景图。\n\n点右下角「建造」：教室决定班容量，宿舍和食堂决定学生体验，图书馆加速科研。\n花坛、长椅、雕像不是纯装饰——每 8 件装扮大约 +0.2 满意度。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "先回到「校园」",
        subText = "点底部「校园」。点行政楼看全校数据并升级校园；点宿舍看每层住了谁。",
        highlightTarget = HighlightTarget.TAB_OVERVIEW,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_OVERVIEW
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "没有教师就开不了课。去「人事」发布招聘，从三名候选人里录用第一位。\n\n薪资是长期支出，但教学能力和研究能力会直接决定培养质量和课题。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "点击底部「人事」",
        subText = "去招聘你的第一位教师",
        highlightTarget = HighlightTarget.TAB_TEACHER,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_TEACHER
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "请招聘至少 1 位教师",
        subText = "点右下角「+」→ 选渠道 → 录用。成功后教程自动继续。",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.HIRE_TEACHER
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教务主任",
        text = "教师到岗了。接下来必须开班：治院 → 教学配置，点右边那个加号。\n\n加号不是装饰。点一次立刻加一个班的新生学位，并扣开办费。一个班都不开，9月招不到人。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "点击底部「治院」",
        subText = "进去后点「教学配置（开班/强度）」。加号就在每一行班型右边。",
        highlightTarget = HighlightTarget.TAB_TEACHING,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_TEACHING
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "请点加号开出至少一个教学班",
        subText = "通识班点一次 +50 学位，专业核心班 +40，拔尖班 +30。点完立刻扣开办费。不开班招不到人。",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.CONFIGURE_TEACHING
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "师资和培养方案齐了。9 月迎新会按专业分流。\n\n学校已经备好一栋宿舍楼和一间教室，床位和班位都有底。想扩大招生规模，以后再多建宿舍和教室。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.ACTION,
        text = "正在招收第一批学生……",
        subText = "宿舍与教室已就绪，系统正在自动迎新，完成后继续。",
        highlightTarget = HighlightTarget.FULL_SCREEN,
        completionCondition = CompletionCondition.WAIT_ENROLLMENT,
        unpauseGame = true
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "年级主任",
        text = "新生报到了！左下角能看到在校人数、办学层次和上月收入。点教室看班主任头像和本班学生，点宿舍看每层住了谁。\n\n开局选的专科/本科会锁学院目录；公办有拨款、民办全靠学费。钱从哪来：学费月底入账，企业委托去外联接单。校园升级点行政楼。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "最后看「外联」",
        subText = "排名、竞赛、企业委托都在这里。接企业单是外联真正能赚钱的地方。",
        highlightTarget = HighlightTarget.TAB_DISTRICT,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_DISTRICT
    ))
    add(TutorialStepData(
        mode = TutorialMode.STORY,
        speaker = "教育局长",
        text = "办学循环：\n8月基建 → 9月招生 → 日常建造与人事 → 月底周报/月结 → 6月评估 → 毕业就业。\n\n建筑连系统，系统连钱和声誉。把第一周过扎实，后面七年都有得玩。"
    ))
    add(TutorialStepData(
        mode = TutorialMode.HIGHLIGHT,
        text = "教程完成，开始经营",
        subText = "用地不够就去点行政楼升级校园。可在设置里重玩教程。",
        highlightTarget = HighlightTarget.TAB_OVERVIEW,
        arrowDirection = ArrowDirection.DOWN,
        completionCondition = CompletionCondition.TAP_TAB_OVERVIEW
    ))
}

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

    fun nextStep() {
        if (currentStepIndex < ALL_TUTORIAL_STEPS.size - 1) {
            currentStepIndex++
        } else {
            dismiss()
        }
    }

    fun dismiss() {
        isActive = false
    }

    fun reset() {
        currentStepIndex = 0
        isActive = true
    }

    fun notifyAction(condition: CompletionCondition) {
        if (!isActive) return
        val step = currentStep
        if (step.completionCondition == condition) {
            nextStep()
        }
    }

    fun notifyTabChanged(tabIndex: Int) {
        if (!isActive) return
        when (tabIndex) {
            0 -> notifyAction(CompletionCondition.TAP_TAB_OVERVIEW)
            1 -> notifyAction(CompletionCondition.TAP_TAB_TEACHING)
            2 -> notifyAction(CompletionCondition.TAP_TAB_TEACHER)
            3 -> notifyAction(CompletionCondition.TAP_TAB_DISTRICT)
        }
    }

    fun notifyNavigateToSubPage(tabIndex: Int) {
        if (!isActive) return
        when (tabIndex) {
            8, 21 -> notifyAction(CompletionCondition.NAVIGATE_STUDENT)
            7 -> notifyAction(CompletionCondition.NAVIGATE_FACILITY)
            11, 44 -> notifyAction(CompletionCondition.NAVIGATE_REPORT)
        }
    }
}
