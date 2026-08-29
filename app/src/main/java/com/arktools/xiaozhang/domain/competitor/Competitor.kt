package com.arktools.xiaozhang.domain.competitor

import kotlin.random.Random

/**
 * AI 竞争对手 —— 学校大亨2 动态竞争系统
 *
 * 每个AI对手有独立的属性、策略和成长轨迹。
 * AI对手的实力会根据游戏年份和自身策略动态变化。
 */
data class Competitor(
    val id: String,
    val name: String,
    val motto: String,
    val strategy: CompetitorStrategy,
    val personality: CompetitorPersonality,

    // 核心指标
    var reputation: Long = 0,
    var cash: Double = 0.0,
    var studentCount: Int = 0,
    var courseCount: Int = 0,
    var teacherCount: Int = 0,
    var campusLevel: Int = 1,
    var starRating: Float = 0f,

    // 成长参数
    val baseGrowthRate: Float = 1.0f,  // 基础成长速度 (0.5~1.5)
    val aggressiveness: Float = 0.5f,  // 侵略性 (0~1), 影响对玩家的反应
    var morale: Float = 0.8f,          // 士气 (0~1), 受竞争结果影响

    // 状态
    var isActive: Boolean = true,      // 是否仍在竞争
    var eliminatedYear: Int? = null,    // 被淘汰的年份
    var specialEventCooldown: Int = 0,  // 特殊事件冷却(月)
    /** 对手池：MAIN=通用教育机构；RESEARCH=研究型大学同侪（仅研究型层次可见） */
    val pool: String = "MAIN"
)

enum class CompetitorStrategy(val displayName: String, val description: String) {
    AGGRESSIVE("激进扩张", "快速扩张规模，高风险高回报"),
    STEADY("稳健发展", "平衡发展各项指标"),
    QUALITY("精品路线", "注重教学质量，缓慢但声誉高"),
    BUDGET("低价竞争", "低学费高招生，薄利多销"),
    INNOVATION("创新驱动", "投入研发，后期爆发力强")
}

enum class CompetitorPersonality(val displayName: String) {
    FRIENDLY("友善"),       // 不会主动攻击玩家
    NEUTRAL("中立"),        // 正常竞争
    HOSTILE("敌对"),        // 会针对玩家做负面动作
    CUNNING("狡猾")        // 会利用时机给对手制造困难
}

/**
 * AI 竞争对手注册表 - 预定义18个AI对手（三个梯队）
 *
 * T1 (顶级): 声誉5000+, 学生200+, 校舍4-5级 (3个)
 * T2 (中坚): 声誉2000-4500, 学生80-180, 校舍2-3级 (8个)
 * T3 (新秀): 声誉300-1500, 学生15-60, 校舍1-2级 (7个)
 */
object CompetitorRegistry {

    fun createCompetitors(): List<Competitor> = listOf(
        // ===== T1 顶级学府 =====
        Competitor(
            id = "qingyunAcademy",
            name = "青云书院",
            motto = "志存高远，学达青云",
            strategy = CompetitorStrategy.QUALITY,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 6000,
            cash = 900.0,
            studentCount = 250,
            courseCount = 10,
            teacherCount = 15,
            campusLevel = 5,
            starRating = 4.3f,
            baseGrowthRate = 1.2f,
            aggressiveness = 0.6f
        ),
        Competitor(
            id = "xingchenGroup",
            name = "星辰教育集团",
            motto = "点亮每一颗星",
            strategy = CompetitorStrategy.AGGRESSIVE,
            personality = CompetitorPersonality.HOSTILE,
            reputation = 5500,
            cash = 850.0,
            studentCount = 220,
            courseCount = 9,
            teacherCount = 14,
            campusLevel = 4,
            starRating = 3.9f,
            baseGrowthRate = 1.25f,
            aggressiveness = 0.8f
        ),
        Competitor(
            id = "mingdeSchool",
            name = "明德学堂",
            motto = "明德至善，博学笃行",
            strategy = CompetitorStrategy.INNOVATION,
            personality = CompetitorPersonality.CUNNING,
            reputation = 5000,
            cash = 780.0,
            studentCount = 200,
            courseCount = 8,
            teacherCount = 12,
            campusLevel = 4,
            starRating = 4.1f,
            baseGrowthRate = 1.18f,
            aggressiveness = 0.65f
        ),

        // ===== T2 中坚力量 =====
        Competitor(
            id = "chunhuiEdu",
            name = "春晖教育",
            motto = "春风化雨，润物无声",
            strategy = CompetitorStrategy.STEADY,
            personality = CompetitorPersonality.FRIENDLY,
            reputation = 4200,
            cash = 650.0,
            studentCount = 170,
            courseCount = 7,
            teacherCount = 10,
            campusLevel = 3,
            starRating = 4.0f,
            baseGrowthRate = 1.05f,
            aggressiveness = 0.3f
        ),
        Competitor(
            id = "longmenTraining",
            name = "龙门培优",
            motto = "鱼跃龙门，金榜题名",
            strategy = CompetitorStrategy.AGGRESSIVE,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 3800,
            cash = 550.0,
            studentCount = 160,
            courseCount = 7,
            teacherCount = 9,
            campusLevel = 3,
            starRating = 3.7f,
            baseGrowthRate = 1.12f,
            aggressiveness = 0.6f
        ),
        Competitor(
            id = "zhihuiTree",
            name = "智慧树学园",
            motto = "用科技浇灌智慧",
            strategy = CompetitorStrategy.INNOVATION,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 3500,
            cash = 500.0,
            studentCount = 130,
            courseCount = 6,
            teacherCount = 8,
            campusLevel = 3,
            starRating = 3.6f,
            baseGrowthRate = 1.15f,
            aggressiveness = 0.5f
        ),
        Competitor(
            id = "boYaSchool",
            name = "博雅学府",
            motto = "博学雅正，全面发展",
            strategy = CompetitorStrategy.QUALITY,
            personality = CompetitorPersonality.FRIENDLY,
            reputation = 3200,
            cash = 480.0,
            studentCount = 110,
            courseCount = 5,
            teacherCount = 8,
            campusLevel = 3,
            starRating = 4.0f,
            baseGrowthRate = 0.98f,
            aggressiveness = 0.25f
        ),
        Competitor(
            id = "jinpaiClass",
            name = "金牌课堂",
            motto = "名师出高徒",
            strategy = CompetitorStrategy.STEADY,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 2800,
            cash = 420.0,
            studentCount = 100,
            courseCount = 5,
            teacherCount = 7,
            campusLevel = 2,
            starRating = 3.5f,
            baseGrowthRate = 1.0f,
            aggressiveness = 0.4f
        ),
        Competitor(
            id = "lezhiEdu",
            name = "乐知教育",
            motto = "快乐学习，智慧成长",
            strategy = CompetitorStrategy.BUDGET,
            personality = CompetitorPersonality.CUNNING,
            reputation = 2500,
            cash = 380.0,
            studentCount = 140,
            courseCount = 6,
            teacherCount = 6,
            campusLevel = 2,
            starRating = 3.2f,
            baseGrowthRate = 1.08f,
            aggressiveness = 0.7f
        ),
        Competitor(
            id = "hongriSchool",
            name = "红日学堂",
            motto = "朝气蓬勃，如日方升",
            strategy = CompetitorStrategy.AGGRESSIVE,
            personality = CompetitorPersonality.HOSTILE,
            reputation = 2200,
            cash = 350.0,
            studentCount = 90,
            courseCount = 4,
            teacherCount = 6,
            campusLevel = 2,
            starRating = 3.3f,
            baseGrowthRate = 1.1f,
            aggressiveness = 0.75f
        ),
        Competitor(
            id = "muxiangEdu",
            name = "沐翔教育",
            motto = "沐浴阳光，展翅翱翔",
            strategy = CompetitorStrategy.QUALITY,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 2000,
            cash = 300.0,
            studentCount = 80,
            courseCount = 4,
            teacherCount = 5,
            campusLevel = 2,
            starRating = 3.7f,
            baseGrowthRate = 0.95f,
            aggressiveness = 0.35f
        ),
        Competitor(
            id = "qixingClass",
            name = "启星课堂",
            motto = "启迪智慧，星光闪耀",
            strategy = CompetitorStrategy.INNOVATION,
            personality = CompetitorPersonality.CUNNING,
            reputation = 2000,
            cash = 320.0,
            studentCount = 85,
            courseCount = 4,
            teacherCount = 5,
            campusLevel = 2,
            starRating = 3.4f,
            baseGrowthRate = 1.13f,
            aggressiveness = 0.55f
        ),

        // ===== T3 新秀小机构 =====
        Competitor(
            id = "xiaoheSchool",
            name = "小荷学堂",
            motto = "小荷才露尖尖角",
            strategy = CompetitorStrategy.STEADY,
            personality = CompetitorPersonality.FRIENDLY,
            reputation = 1200,
            cash = 200.0,
            studentCount = 50,
            courseCount = 3,
            teacherCount = 4,
            campusLevel = 2,
            starRating = 3.5f,
            baseGrowthRate = 0.9f,
            aggressiveness = 0.15f
        ),
        Competitor(
            id = "weidaoClass",
            name = "味道补习班",
            motto = "学出味道来",
            strategy = CompetitorStrategy.BUDGET,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 1000,
            cash = 180.0,
            studentCount = 60,
            courseCount = 3,
            teacherCount = 3,
            campusLevel = 1,
            starRating = 2.8f,
            baseGrowthRate = 1.0f,
            aggressiveness = 0.5f
        ),
        Competitor(
            id = "xiaoyuanEdu",
            name = "小园教育",
            motto = "呵护成长的小花园",
            strategy = CompetitorStrategy.QUALITY,
            personality = CompetitorPersonality.FRIENDLY,
            reputation = 800,
            cash = 150.0,
            studentCount = 35,
            courseCount = 2,
            teacherCount = 3,
            campusLevel = 1,
            starRating = 3.6f,
            baseGrowthRate = 0.85f,
            aggressiveness = 0.1f
        ),
        Competitor(
            id = "qiangbuSchool",
            name = "强步培训",
            motto = "一步一步变更强",
            strategy = CompetitorStrategy.BUDGET,
            personality = CompetitorPersonality.HOSTILE,
            reputation = 600,
            cash = 120.0,
            studentCount = 45,
            courseCount = 3,
            teacherCount = 2,
            campusLevel = 1,
            starRating = 2.6f,
            baseGrowthRate = 1.05f,
            aggressiveness = 0.8f
        ),
        Competitor(
            id = "tuohuiClass",
            name = "拓慧课堂",
            motto = "拓展视野，启迪心慧",
            strategy = CompetitorStrategy.INNOVATION,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 500,
            cash = 100.0,
            studentCount = 25,
            courseCount = 2,
            teacherCount = 2,
            campusLevel = 1,
            starRating = 3.0f,
            baseGrowthRate = 1.15f,
            aggressiveness = 0.4f
        ),
        Competitor(
            id = "chuxinEdu",
            name = "初心小筑",
            motto = "不忘初心，方得始终",
            strategy = CompetitorStrategy.STEADY,
            personality = CompetitorPersonality.FRIENDLY,
            reputation = 300,
            cash = 80.0,
            studentCount = 15,
            courseCount = 1,
            teacherCount = 2,
            campusLevel = 1,
            starRating = 3.2f,
            baseGrowthRate = 0.8f,
            aggressiveness = 0.05f
        ),

        // ===== 研究型大学同侪池（仅研究型层次可见） =====
        Competitor(
            id = "yanjingGezhi",
            name = "燕京格致大学",
            motto = "格物致知，止于至善",
            strategy = CompetitorStrategy.INNOVATION,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 5800,
            cash = 880.0,
            studentCount = 240,
            courseCount = 10,
            teacherCount = 16,
            campusLevel = 5,
            starRating = 4.5f,
            baseGrowthRate = 1.22f,
            aggressiveness = 0.5f,
            pool = "RESEARCH"
        ),
        Competitor(
            id = "huadongTech",
            name = "华东理工联合大学",
            motto = "厚德博学，求是创新",
            strategy = CompetitorStrategy.QUALITY,
            personality = CompetitorPersonality.CUNNING,
            reputation = 5200,
            cash = 760.0,
            studentCount = 210,
            courseCount = 9,
            teacherCount = 14,
            campusLevel = 4,
            starRating = 4.2f,
            baseGrowthRate = 1.15f,
            aggressiveness = 0.45f,
            pool = "RESEARCH"
        ),
        Competitor(
            id = "jiangdongTech",
            name = "江东科技大学",
            motto = "科技报国，行胜于言",
            strategy = CompetitorStrategy.INNOVATION,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 3600,
            cash = 520.0,
            studentCount = 150,
            courseCount = 7,
            teacherCount = 10,
            campusLevel = 3,
            starRating = 4.0f,
            baseGrowthRate = 1.18f,
            aggressiveness = 0.4f,
            pool = "RESEARCH"
        ),
        Competitor(
            id = "canglanFinance",
            name = "沧澜财经大学",
            motto = "经世济民，商道致远",
            strategy = CompetitorStrategy.STEADY,
            personality = CompetitorPersonality.FRIENDLY,
            reputation = 3000,
            cash = 460.0,
            studentCount = 130,
            courseCount = 6,
            teacherCount = 9,
            campusLevel = 3,
            starRating = 3.9f,
            baseGrowthRate = 1.0f,
            aggressiveness = 0.2f,
            pool = "RESEARCH"
        ),
        Competitor(
            id = "qiyuanNormal",
            name = "启元师范大学",
            motto = "学高为师，身正为范",
            strategy = CompetitorStrategy.QUALITY,
            personality = CompetitorPersonality.FRIENDLY,
            reputation = 1100,
            cash = 190.0,
            studentCount = 55,
            courseCount = 3,
            teacherCount = 5,
            campusLevel = 2,
            starRating = 3.6f,
            baseGrowthRate = 0.95f,
            aggressiveness = 0.1f,
            pool = "RESEARCH"
        ),
        Competitor(
            id = "bailuAcademy",
            name = "白鹭研究院",
            motto = "心静如水，学海无涯",
            strategy = CompetitorStrategy.INNOVATION,
            personality = CompetitorPersonality.NEUTRAL,
            reputation = 700,
            cash = 130.0,
            studentCount = 30,
            courseCount = 2,
            teacherCount = 4,
            campusLevel = 1,
            starRating = 3.4f,
            baseGrowthRate = 1.2f,
            aggressiveness = 0.3f,
            pool = "RESEARCH"
        )
    )
}

/**
 * AI 竞争对手事件（由CompetitorEngine生成的影响玩家的事件）
 */
sealed class CompetitorEvent {
    abstract val competitorName: String

    data class PriceWar(
        override val competitorName: String,
        val reputationLoss: Long
    ) : CompetitorEvent()

    data class TalentPoaching(
        override val competitorName: String,
        val targetTeacher: String,
        val loyaltyDamage: Int = 15
    ) : CompetitorEvent()

    data class MarketExpansion(
        override val competitorName: String,
        val studentLoss: Int
    ) : CompetitorEvent()

    data class Partnership(
        override val competitorName: String,
        val reputationGain: Long
    ) : CompetitorEvent()

    data class CompetitorCollapse(
        override val competitorName: String,
        val studentGain: Int,
        val reputationGain: Long
    ) : CompetitorEvent()

    /**
     * 将竞争事件转换为 GameEvent 用于 UI 通知
     */
    fun toGameEvent(): com.arktools.xiaozhang.domain.model.GameEvent? {
        return when (this) {
            is PriceWar -> com.arktools.xiaozhang.domain.model.GameEvent.NegativeEvent(
                title = "价格战",
                message = "${competitorName}发起价格战，大幅降低学费抢夺生源！学校声誉-${reputationLoss}。",
                penaltyCash = 0.0,
                penaltyReputation = 0L
            )
            is TalentPoaching -> com.arktools.xiaozhang.domain.model.GameEvent.NegativeEvent(
                title = "人才挖角",
                message = "${competitorName}试图高薪挖走你的教师！",
                penaltyCash = 0.0
            )
            is MarketExpansion -> com.arktools.xiaozhang.domain.model.GameEvent.NegativeEvent(
                title = "市场扩张",
                message = "${competitorName}在你的学区大规模投放广告，招生竞争加剧。学校声誉-2。",
                penaltyCash = 0.0,
                penaltyReputation = 0L
            )
            is Partnership -> com.arktools.xiaozhang.domain.model.GameEvent.PositiveEvent(
                title = "合作邀请",
                message = "${competitorName}与你达成合作协议，互惠共赢！学校声誉+${reputationGain}。",
                bonusReputation = 0L
            )
            is CompetitorCollapse -> com.arktools.xiaozhang.domain.model.GameEvent.PositiveEvent(
                title = "对手倒闭",
                message = "${competitorName}经营不善宣布关闭！${studentGain}名学生转入你校，学校声誉+${reputationGain}。",
                bonusReputation = 0L
            )
        }
    }
}
