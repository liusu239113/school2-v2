package com.arktools.xiaozhang.domain.minigame

import com.arktools.xiaozhang.domain.seasonal.ActivityType

/**
 * 迷你游戏结果：影响最终活动结算奖励
 */
data class MiniGameResult(
    val activityId: String,
    val activityType: ActivityType,
    /** 表现分数 0.0~1.0，影响声誉/满意度乘数 */
    val performanceScore: Float,
    /** 是否获得特殊成就（额外声誉加成） */
    val specialAchievement: Boolean = false,
    /** 结果描述 */
    val resultMessage: String
)

// ==================== 运动会迷你游戏 ====================

/** 运动会比赛项目 - 每个项目有独特玩法 */
enum class SportsEvent(
    val displayName: String,
    val emoji: String,
    val mechanic: String,      // 玩法描述
    val idealTempo: CheerTempo // 该项目最佳节奏
) {
    SPRINT_100M("100米短跑", "🏃", "连续快速点击冲刺", CheerTempo.FAST),
    RELAY_4X100("4×100接力", "🏃‍♂️", "在接力区精准点击交棒", CheerTempo.PRECISE),
    LONG_JUMP("跳远", "🦘", "蓄力条精准释放", CheerTempo.PRECISE),
    SHOT_PUT("铅球", "💪", "节奏点击蓄力投掷", CheerTempo.RHYTHMIC),
    TUG_OF_WAR("拔河", "🪢", "节奏拉锯战", CheerTempo.RHYTHMIC),
    HIGH_JUMP("跳高", "⬆️", "精准时机起跳", CheerTempo.PRECISE),
    LONG_DISTANCE("1500米", "🏅", "体力分配管理", CheerTempo.STEADY)
}

/** 加油节奏类型 - 不同项目要求不同的点击模式 */
enum class CheerTempo(val displayName: String, val hint: String) {
    FAST("快速连击", "越快越好！疯狂点击！"),
    RHYTHMIC("节奏点击", "跟着节拍点！太快太慢都扣分"),
    PRECISE("精准时机", "等待提示出现时点击！"),
    STEADY("均匀持续", "保持稳定节奏，不要断")
}

/** 参赛班级 */
data class CompetingClass(
    val name: String,
    val baseStrength: Int,       // 基础实力 60-100
    val isPlayerClass: Boolean = false,
    val speciality: SportsEvent? = null  // 擅长项目（+15%加成）
)

/** 运动会游戏阶段 */
enum class SportsDayPhase {
    /** 选择参赛项目（选3个） */
    SELECT_EVENTS,
    /** 每场比赛前选战术卡 */
    PRE_RACE_TACTIC,
    /** 比赛进行中 */
    RACE_IN_PROGRESS,
    /** 关键时刻QTE */
    CRITICAL_MOMENT,
    /** 单场结果 */
    RACE_RESULT,
    /** 最终汇总 */
    SHOW_RESULTS
}

/** 战术卡牌 - 每场比赛前选一张使用 */
enum class TacticCard(
    val displayName: String,
    val emoji: String,
    val description: String,
    val usesLeft: Int = 1  // 每张卡整场运动会可用次数
) {
    PEP_TALK("赛前动员", "📢", "士气+15%，全队状态提升", 2),
    SUBSTITUTE("王牌替补", "🔄", "派出隐藏实力选手，基础实力+20%", 1),
    SPY("情报战术", "🕵️", "提前知道对手实力排名", 2),
    REST("充分休息", "😴", "恢复30点体力", 2),
    SNACK("能量补给", "🍫", "加油效果提升50%，持续本场", 1),
    CHEER_SQUAD("啦啦队", "📣", "被动加油效果，不消耗体力也能慢慢涨", 1)
}

/** 关键时刻事件 - 比赛中随机出现 */
data class CriticalMoment(
    val description: String,
    val emoji: String,
    /** 需要在多少毫秒内点击 */
    val windowMs: Long = 2000,
    /** 成功加分 */
    val successBonus: Float = 0.12f,
    /** 失败扣分 */
    val failPenalty: Float = 0.05f
)

/** 一场比赛的实时状态 */
data class RaceState(
    val event: SportsEvent,
    val participants: List<RaceParticipant>,
    /** 进度 0.0~1.0 */
    val progress: Float = 0f,
    val finished: Boolean = false,
    /** 节奏准确度 (仅RHYTHMIC/PRECISE模式) 0.0~1.0 */
    val rhythmAccuracy: Float = 0f,
    /** 节奏判定提示是否激活(PRECISE模式用) */
    val promptActive: Boolean = false,
    /** 当前节拍位置(RHYTHMIC模式用) 0.0~1.0循环 */
    val beatPosition: Float = 0f,
    /** 上一次点击的判定结果 */
    val lastJudgement: String = ""
)

data class RaceParticipant(
    val className: String,
    val isPlayer: Boolean,
    /** 当前位置 0.0~1.0 */
    val position: Float = 0f,
    val rank: Int = 0,
    /** 是否有擅长项目加成 */
    val hasSpecialityBonus: Boolean = false
)

/** 运动会整体游戏状态 */
data class SportsDayGameState(
    val phase: SportsDayPhase = SportsDayPhase.SELECT_EVENTS,
    val availableEvents: List<SportsEvent> = SportsEvent.entries.toList(),
    val selectedEvents: List<SportsEvent> = emptyList(),
    val classes: List<CompetingClass> = emptyList(),
    val currentRace: RaceState? = null,
    val currentRaceIndex: Int = 0,
    val raceResults: List<RaceResult> = emptyList(),
    /** 玩家加油点击次数 */
    val cheerCount: Int = 0,
    /** 体力系统：满100，加油消耗体力 */
    val stamina: Int = 100,
    val maxStamina: Int = 100,
    /** 战术卡系统 */
    val availableTactics: Map<TacticCard, Int> = TacticCard.entries.associateWith { it.usesLeft },
    val activeTacticThisRace: TacticCard? = null,
    /** 是否已展示对手情报（SPY卡效果） */
    val showOpponentStats: Boolean = false,
    /** 关键时刻 */
    val criticalMoment: CriticalMoment? = null,
    val criticalMomentActive: Boolean = false,
    val criticalMomentSuccess: Boolean? = null,
    /** 得分 */
    val totalScore: Int = 0,
    val maxScore: Int = 0,
    /** 节奏模式打击准确次数/总次数 */
    val goodHits: Int = 0,
    val totalHits: Int = 0,
    /** 连击数 */
    val combo: Int = 0,
    val maxCombo: Int = 0
)

/** 策略已废弃，改用战术卡系统 */
enum class SportsStrategy(val displayName: String, val description: String) {
    AGGRESSIVE("全力进攻", "实力+20%，但体力消耗快"),
    BALANCED("均衡发挥", "稳定发挥，无额外加成"),
    CONSERVATIVE("保守稳健", "保底不掉分，但上限降低")
}

data class RaceResult(
    val event: SportsEvent,
    val playerRank: Int,
    val totalParticipants: Int,
    val score: Int,
    val tacticUsed: TacticCard? = null,
    val criticalSuccess: Boolean? = null,
    val comboAchieved: Int = 0
)

// ==================== 辩论赛迷你游戏 ====================

/** 辩论赛游戏阶段 */
enum class DebatePhase {
    /** 选择辩题立场 */
    CHOOSE_STANCE,
    /** 攻防回合 */
    ARGUMENT_ROUND,
    /** 对手出牌后 - 选择是否反驳 */
    REBUTTAL_CHANCE,
    /** 显示比分和评判 */
    SHOW_VERDICT
}

/** 辩论环节名称（5轮） */
enum class DebateRoundType(val displayName: String, val description: String) {
    OPENING("开篇立论", "展示核心观点"),
    CROSS_EXAM("质询攻辩", "直击对方漏洞"),
    FREE_DEBATE_1("自由辩论·上", "唇枪舌剑"),
    FREE_DEBATE_2("自由辩论·下", "针锋相对"),
    CLOSING("总结陈词", "一锤定音")
}

/** 论点类别（克制关系：逻辑→数据→情感→权威→逻辑） */
enum class ArgumentCategory(val displayName: String, val emoji: String) {
    LOGIC("逻辑推理", "🧠"),
    DATA("数据实证", "📊"),
    EMOTION("情感共鸣", "💗"),
    AUTHORITY("权威引用", "📚");

    /** 获取该类别克制的类别 */
    fun beats(): ArgumentCategory = when (this) {
        LOGIC -> DATA       // 逻辑推理 克制 数据实证（找到数据背后的逻辑漏洞）
        DATA -> EMOTION     // 数据实证 克制 情感共鸣（用事实击败煽情）
        EMOTION -> AUTHORITY // 情感共鸣 克制 权威引用（动之以情打破权威崇拜）
        AUTHORITY -> LOGIC  // 权威引用 克制 逻辑推理（权威结论压过纯推理）
    }
}

/** 辩题 */
data class DebateTopic(
    val title: String,
    val proStance: String,   // 正方立场
    val conStance: String    // 反方立场
)

/** 评委偏好 */
data class JudgePreference(
    val name: String,
    val preferredCategory: ArgumentCategory,
    val description: String
)

/** 论点卡片 */
data class ArgumentCard(
    val id: Int,
    val text: String,
    /** 论点类别 */
    val category: ArgumentCategory = ArgumentCategory.LOGIC,
    /** 攻击力（说服力强度）1-10 */
    val attackPower: Int,
    /** 防御力（逻辑严密度）1-10 */
    val defensePower: Int,
    /** 是否为诡辩（高攻低防，可能被反驳） */
    val isSophistry: Boolean = false
)

/** 一个回合的结果 */
data class RoundResult(
    val roundNumber: Int,
    val roundType: DebateRoundType = DebateRoundType.OPENING,
    val playerCard: ArgumentCard,
    val opponentCard: ArgumentCard,
    val playerScore: Int,
    val opponentScore: Int,
    val commentary: String,
    /** 是否触发了克制效果 */
    val categoryAdvantage: Boolean = false,
    /** 是否使用了反驳 */
    val playerUsedRebuttal: Boolean = false
)

/** 辩论赛整体游戏状态 */
data class DebateGameState(
    val phase: DebatePhase = DebatePhase.CHOOSE_STANCE,
    val topic: DebateTopic = DebateTopic("", "", ""),
    val playerIsProSide: Boolean = true,
    val currentRound: Int = 1,
    val maxRounds: Int = 5,
    val playerHand: List<ArgumentCard> = emptyList(),
    val opponentName: String = "对方辩手",
    val roundResults: List<RoundResult> = emptyList(),
    val playerTotalScore: Int = 0,
    val opponentTotalScore: Int = 0,
    /** 当前回合对手出的牌（显示用） */
    val currentOpponentCard: ArgumentCard? = null,
    /** 评委点评 */
    val judgeCommentary: String = "",
    /** 玩家剩余反驳次数 */
    val rebuttalCharges: Int = 2,
    /** 连胜计数（连续赢回合的次数） */
    val momentum: Int = 0,
    /** 评委偏好的论点类型 */
    val judgePreference: JudgePreference = JudgePreference("评委", ArgumentCategory.LOGIC, ""),
    /** 当前辩论环节 */
    val currentRoundType: DebateRoundType = DebateRoundType.OPENING,
    /** 当前回合玩家出的牌（反驳阶段需要） */
    val currentPlayerCard: ArgumentCard? = null,
    /** 当前回合基础得分（反驳前） */
    val pendingPlayerScore: Int = 0,
    val pendingOpponentScore: Int = 0
)

// ==================== 科学展览会迷你游戏 ====================

/** 科学展览阶段 */
enum class ScienceFairPhase {
    /** 选择实验课题 */
    CHOOSE_PROJECT,
    /** 实验操作（按正确顺序选步骤） */
    EXPERIMENT,
    /** 展示答辩 */
    PRESENTATION,
    /** 评审结果 */
    SHOW_RESULTS
}

/** 实验课题 */
data class ScienceProject(
    val id: Int,
    val title: String,
    val emoji: String,
    val difficulty: Int,         // 1-3星
    val description: String,
    /** 正确实验步骤顺序 */
    val correctSteps: List<ExperimentStep>,
    /** 答辩问题 */
    val questions: List<PresentationQuestion>
)

/** 实验步骤 */
data class ExperimentStep(
    val id: Int,
    val text: String,
    val emoji: String
)

/** 答辩问题 */
data class PresentationQuestion(
    val question: String,
    val options: List<String>,
    /** 正确答案index（0-based） */
    val correctIndex: Int,
    /** 选对/选错的评语 */
    val correctComment: String,
    val wrongComment: String
)

/** 科学展览整体游戏状态 */
data class ScienceFairGameState(
    val phase: ScienceFairPhase = ScienceFairPhase.CHOOSE_PROJECT,
    val availableProjects: List<ScienceProject> = emptyList(),
    val selectedProject: ScienceProject? = null,
    /** 实验阶段：打乱后的步骤（供玩家排列） */
    val shuffledSteps: List<ExperimentStep> = emptyList(),
    /** 玩家已选择的步骤顺序 */
    val playerStepOrder: List<ExperimentStep> = emptyList(),
    /** 实验得分（步骤正确率） */
    val experimentScore: Float = 0f,
    /** 答辩阶段当前问题索引 */
    val currentQuestionIndex: Int = 0,
    /** 答辩正确数 */
    val correctAnswers: Int = 0,
    /** 上一题答题结果(-1未答,0错,1对) */
    val lastAnswerResult: Int = -1,
    val lastComment: String = "",
    /** 总评 */
    val totalScore: Float = 0f,
    val resultMessage: String = ""
)

// ==================== 文艺汇演迷你游戏 ====================

/** 文艺汇演阶段 */
enum class CulturalFestPhase {
    /** 选节目阵容（从候选中选5个） */
    SELECT_ACTS,
    /** 排节目顺序 */
    ARRANGE_ORDER,
    /** 演出进行中（逐个展示效果） */
    PERFORMING,
    /** 最终评分 */
    SHOW_RESULTS
}

/** 节目类型 */
enum class ActType(val displayName: String, val emoji: String) {
    SONG("歌曲", "🎤"),
    DANCE("舞蹈", "💃"),
    SKIT("小品", "🎭"),
    INSTRUMENT("器乐", "🎹"),
    CHOIR("合唱", "🎶"),
    ACROBATICS("杂技", "🤸"),
    MAGIC("魔术", "🪄"),
    RECITATION("朗诵", "📖")
}

/** 节目 */
data class PerformanceAct(
    val id: Int,
    val name: String,
    val type: ActType,
    /** 能量值：高能量节目带动气氛，低能量节目让观众休息 */
    val energy: Int,        // 1-5
    /** 节目质量 */
    val quality: Int,       // 60-100
    /** 最佳出场位置偏好 (1=开场, 2=中段, 3=压轴) */
    val bestPosition: Int,
    /** 演出时长描述 */
    val duration: String
)

/** 观众情绪 */
data class AudienceMood(
    val excitement: Int = 50,    // 兴奋度 0-100
    val fatigue: Int = 0,        // 疲劳度 0-100（连续高能会累）
    val satisfaction: Int = 50   // 满意度 0-100（最终评分核心）
)

/** 单个节目演出结果 */
data class ActResult(
    val act: PerformanceAct,
    val positionBonus: Boolean,      // 是否在最佳位置
    val audienceReaction: String,    // 观众反应emoji+文字
    val scoreContribution: Int       // 对总分贡献
)

/** 文艺汇演整体游戏状态 */
data class CulturalFestGameState(
    val phase: CulturalFestPhase = CulturalFestPhase.SELECT_ACTS,
    val availableActs: List<PerformanceAct> = emptyList(),
    val selectedActs: List<PerformanceAct> = emptyList(),
    /** 排好序的节目单（ARRANGE阶段产物） */
    val orderedActs: List<PerformanceAct> = emptyList(),
    /** 当前演出到第几个 */
    val currentActIndex: Int = 0,
    /** 观众情绪 */
    val audienceMood: AudienceMood = AudienceMood(),
    /** 各节目演出结果 */
    val actResults: List<ActResult> = emptyList(),
    /** 总分 */
    val totalScore: Int = 0,
    val maxScore: Int = 100,
    val resultMessage: String = ""
)
