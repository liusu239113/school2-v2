package com.arktools.xiaozhang.domain.model

import kotlinx.serialization.Serializable

/**
 * 校长个人系统 - 玩家扮演的校长角色
 * 拥有独立于学校的个人属性、资金和关系网络
 */
@Serializable
data class Principal(
    // === StateFlow 刷新版本号（每次属性变更后自增以触发 UI 重组）===
    var version: Int = 0,

    // === 个人资金（与学校资金完全独立）===
    var personalFunds: Double = 0.0,        // 个人资金（万元）

    // === 核心属性 (0-100) ===
    var corruptionLevel: Int = 0,           // 腐败值：越高越容易被查
    var connectionLevel: Int = 10,          // 人脉值：越高能摆平的事越多
    var connectionBonus: Int = 0,            // 通过行贿/社交等直接获得的人脉加成（不受关系网衰减影响）
    var idealismLevel: Int = 80,            // 教育理想：影响部分事件选项和教师忠诚
    var personalReputation: Int = 50,       // 个人声望：影响升迁和被调查概率

    // === 状态 ===
    var isSuspended: Boolean = false,       // 是否被停职调查中
    var suspendedDaysLeft: Int = 0,         // 停职剩余天数
    var isArrested: Boolean = false,        // 是否已被逮捕（游戏结束级惩罚）
    var timesInvestigated: Int = 0,         // 被调查次数（越多越危险）
    var timesCaughtMinor: Int = 0,          // 小问题被逮住次数
    var timesCaughtMajor: Int = 0,          // 大问题被逮住次数

    // === 操作限制 ===
    var lastCorruptMonth: Int = 0,          // 上次贪污的月份（年*12+月）
    var corruptActsThisMonth: Int = 0,      // 本月已执行贪污次数

    // === 腐败操作记录 ===
    var totalEmbezzled: Double = 0.0,       // 历史总贪污额
    var antiCorruptionApplied: Boolean = false,  // 纪检清算是否已执行（一次性迁移标志）
    var recentCorruptActs: MutableList<CorruptAct> = mutableListOf(),  // 近期腐败行为（证据链）

    // === 人脉关系 ===
    var connections: MutableList<Connection> = mutableListOf(),

    // === 已购奢侈品记录 ===
    var purchasedLuxuryItems: MutableList<String> = mutableListOf(),

    // === 派系关系 ===
    var factionRelations: MutableMap<FactionType, Int> = mutableMapOf(
        FactionType.TEACHING to 50,
        FactionType.ADMINISTRATIVE to 50,
        FactionType.REFORM to 50,
        FactionType.CONSERVATIVE to 50
    )
)

/**
 * 腐败行为记录 - 作为"证据"存在，被查时会翻出来
 */
@Serializable
data class CorruptAct(
    val type: CorruptionType,
    val amount: Double,             // 涉及金额
    val gameDay: Int,               // 发生的游戏天数
    val description: String,
    var isDiscovered: Boolean = false,   // 是否已被发现
    val witnessCount: Int = 0       // 知情人数（越多越容易暴露）
)

enum class CorruptionType(val displayName: String, val baseRisk: Float, val severity: Int) {
    EMBEZZLE("贪污公款", 0.03f, 5),
    KICKBACK("收受回扣", 0.02f, 3),
    SELL_ADMISSION("卖学位", 0.04f, 4),
    FAKE_NUMBERS("虚报人数骗补贴", 0.03f, 4),
    NEPOTISM("安插关系户", 0.01f, 2),
    WAGE_SKIM("克扣工资吃差价", 0.02f, 3),
    GRADE_FRAUD("成绩造假", 0.03f, 3),
    COVER_UP("掩盖事故", 0.04f, 5),
    BRIBE_INSPECTOR("行贿检查人员", 0.05f, 5),
    MISUSE_RESEARCH_FUNDS("挪用研究经费", 0.02f, 3)
}

/**
 * 人脉关系
 */
@Serializable
data class Connection(
    val type: ConnectionType,
    val name: String,
    var relationLevel: Int = 30,    // 关系亲密度 0-100
    var usedCount: Int = 0,         // 使用次数（用多了关系会淡）
    var lastUsedDay: Int = 0
)

enum class ConnectionType(val displayName: String) {
    EDUCATION_OFFICIAL("教育局官员"),
    LOCAL_BUSINESSMAN("本地商人"),
    MEDIA_REPORTER("媒体记者"),
    PARENT_REPRESENTATIVE("家长代表"),
    FELLOW_PRINCIPAL("同行校长"),
    GOVERNMENT_INSPECTOR("督导员"),
    POLICE_CONTACT("公安关系"),
    REAL_ESTATE("地产商")
}

/**
 * 学校内部派系
 */
enum class FactionType(val displayName: String, val description: String) {
    TEACHING("教学派", "以资深教师为主，重视教学质量，反对过度商业化"),
    ADMINISTRATIVE("行政派", "行政管理人员，重视效率和规模扩张"),
    REFORM("改革派", "年轻教师为主，追求创新，接受风险"),
    CONSERVATIVE("保守派", "元老级教师，维稳第一，反对激进变动")
}

/**
 * 腐败被查处的结果等级
 */
enum class InvestigationResult(val displayName: String) {
    CLEARED("调查结束，未发现问题"),
    WARNING("警告处分"),
    FINE("罚款处分"),
    SUSPENSION("停职反省"),
    DEMOTION("降级处分，声誉大损"),
    ARRESTED("被纪检监察带走，移送司法机关")
}
