package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 人脉关系网络管理器
 * 管理校长的社会关系，不同类型人脉可在不同场景使用
 */
@Singleton
class ConnectionManager @Inject constructor() {

    /**
     * 初始化人脉（新游戏开始时）
     */
    fun initializeConnections(principal: Principal) {
        // 新校长自带2-3个初始人脉
        principal.connections.addAll(listOf(
            Connection(
                type = ConnectionType.PARENT_REPRESENTATIVE,
                name = generateName(ConnectionType.PARENT_REPRESENTATIVE),
                relationLevel = 40
            ),
            Connection(
                type = ConnectionType.LOCAL_BUSINESSMAN,
                name = generateName(ConnectionType.LOCAL_BUSINESSMAN),
                relationLevel = 25
            )
        ))
    }

    /**
     * 尝试结交新人脉（通过事件或主动社交）
     */
    fun addConnection(principal: Principal, type: ConnectionType, initialRelation: Int = 30): Connection {
        val connection = Connection(
            type = type,
            name = generateName(type),
            relationLevel = initialRelation
        )
        principal.connections.add(connection)
        recalculateConnectionLevel(principal)
        return connection
    }

    /**
     * 使用人脉办事
     * @return 是否成功，以及效果描述
     */
    fun useConnection(
        principal: Principal,
        school: School,
        connectionType: ConnectionType,
        purpose: ConnectionPurpose
    ): ConnectionUseResult {
        val connection = principal.connections.firstOrNull { it.type == connectionType }
            ?: return ConnectionUseResult(false, "没有这类人脉关系")

        // 关系等级影响成功率
        val successChance = (connection.relationLevel / 100f * 0.7f + 0.2f).coerceIn(0.2f, 0.9f)

        // 使用后关系会消耗
        connection.usedCount++
        connection.relationLevel = (connection.relationLevel - Random.nextInt(5, 15)).coerceAtLeast(0)
        connection.lastUsedDay = calculateGameDay(school)

        if (Random.nextFloat() > successChance) {
            return ConnectionUseResult(false, "对方表示爱莫能助，这次帮不了忙")
        }

        // 根据人脉类型和用途计算效果
        val effect = calculateEffect(connection, purpose, school)
        recalculateConnectionLevel(principal)
        return effect
    }

    /**
     * 维护人脉关系（花钱请客等）
     */
    fun maintainConnection(principal: Principal, connection: Connection, cost: Double): Boolean {
        if (principal.personalFunds < cost) return false

        principal.personalFunds -= cost
        connection.relationLevel = (connection.relationLevel + Random.nextInt(10, 25)).coerceAtMost(100)
        recalculateConnectionLevel(principal)
        return true
    }

    /**
     * 每月自然关系衰减
     */
    fun monthlyDecay(principal: Principal) {
        principal.connections.forEach { connection ->
            // 长时间不联系会疏远
            connection.relationLevel = (connection.relationLevel - Random.nextInt(1, 4)).coerceAtLeast(0)
        }
        // 移除关系为0的人脉（彻底断联）
        principal.connections.removeAll { it.relationLevel <= 0 }
        recalculateConnectionLevel(principal)
    }

    /**
     * 获取可用的人脉操作列表
     */
    fun getAvailableActions(principal: Principal, school: School): List<ConnectionAction> {
        val actions = mutableListOf<ConnectionAction>()

        principal.connections.forEach { connection ->
            when (connection.type) {
                ConnectionType.EDUCATION_OFFICIAL -> {
                    actions.add(ConnectionAction(
                        connection = connection,
                        purpose = ConnectionPurpose.GET_POLICY_INFO,
                        description = "打听政策动向（提前知道检查时间）",
                        cost = 1.0
                    ))
                    if (connection.relationLevel >= 50) {
                        actions.add(ConnectionAction(
                            connection = connection,
                            purpose = ConnectionPurpose.REDUCE_INSPECTION_SEVERITY,
                            description = "请求检查时手下留情",
                            cost = 3.0
                        ))
                    }
                }
                ConnectionType.LOCAL_BUSINESSMAN -> {
                    actions.add(ConnectionAction(
                        connection = connection,
                        purpose = ConnectionPurpose.GET_DISCOUNT,
                        description = "找他谈设备/装修折扣",
                        cost = 0.5
                    ))
                    if (connection.relationLevel >= 60) {
                        actions.add(ConnectionAction(
                            connection = connection,
                            purpose = ConnectionPurpose.EMERGENCY_LOAN,
                            description = "紧急借款周转（无息）",
                            cost = 2.0
                        ))
                    }
                }
                ConnectionType.MEDIA_REPORTER -> {
                    actions.add(ConnectionAction(
                        connection = connection,
                        purpose = ConnectionPurpose.POSITIVE_COVERAGE,
                        description = "请他写正面报道",
                        cost = 2.0
                    ))
                    if (connection.relationLevel >= 40) {
                        actions.add(ConnectionAction(
                            connection = connection,
                            purpose = ConnectionPurpose.SUPPRESS_NEGATIVE,
                            description = "帮忙压下负面新闻",
                            cost = 5.0
                        ))
                    }
                }
                ConnectionType.PARENT_REPRESENTATIVE -> {
                    actions.add(ConnectionAction(
                        connection = connection,
                        purpose = ConnectionPurpose.RECRUIT_STUDENTS,
                        description = "请他帮忙介绍生源",
                        cost = 1.0
                    ))
                    actions.add(ConnectionAction(
                        connection = connection,
                        purpose = ConnectionPurpose.CALM_PARENTS,
                        description = "帮忙安抚其他不满家长",
                        cost = 0.5
                    ))
                }
                ConnectionType.FELLOW_PRINCIPAL -> {
                    actions.add(ConnectionAction(
                        connection = connection,
                        purpose = ConnectionPurpose.SHARE_INFO,
                        description = "交流办学经验（可能获得建议）",
                        cost = 1.0
                    ))
                    if (connection.relationLevel >= 50) {
                        actions.add(ConnectionAction(
                            connection = connection,
                            purpose = ConnectionPurpose.TEACHER_RECOMMEND,
                            description = "让他推荐好教师",
                            cost = 2.0
                        ))
                    }
                }
                ConnectionType.GOVERNMENT_INSPECTOR -> {
                    if (connection.relationLevel >= 60) {
                        actions.add(ConnectionAction(
                            connection = connection,
                            purpose = ConnectionPurpose.REDUCE_INSPECTION_SEVERITY,
                            description = "检查时给面子",
                            cost = 3.0
                        ))
                    }
                }
                ConnectionType.POLICE_CONTACT -> {
                    actions.add(ConnectionAction(
                        connection = connection,
                        purpose = ConnectionPurpose.HANDLE_INCIDENT,
                        description = "遇到纠纷时帮忙协调",
                        cost = 2.0
                    ))
                }
                ConnectionType.REAL_ESTATE -> {
                    if (school.campusLevel >= 4) {
                        actions.add(ConnectionAction(
                            connection = connection,
                            purpose = ConnectionPurpose.GET_DISCOUNT,
                            description = "拿到土地/建筑优惠价格",
                            cost = 3.0
                        ))
                    }
                }
            }
        }

        return actions
    }

    /**
     * 随机人脉事件（每月可能触发）
     */
    fun generateConnectionEvent(principal: Principal, school: School): GameEvent? {
        if (principal.connections.isEmpty()) return null
        if (Random.nextFloat() > 0.15f) return null  // 15%概率

        val connection = principal.connections.random()

        return when (connection.type) {
            ConnectionType.EDUCATION_OFFICIAL -> {
                if (connection.relationLevel >= 40) {
                    GameEvent.ChoiceEvent(
                        title = "官员请托",
                        message = "${connection.name}来电：他亲戚的孩子想转到你学校，成绩一般但家里有关系。",
                        choices = listOf(
                            EventChoice("收下这个学生",
                                EventConsequence(reputationChange = -100)),
                            EventChoice("婉拒，按规矩来",
                                EventConsequence(reputationChange = 50))
                        )
                    )
                } else null
            }
            ConnectionType.LOCAL_BUSINESSMAN -> {
                GameEvent.ChoiceEvent(
                    title = "商人宴请",
                    message = "${connection.name}邀请你参加商会聚餐，可以认识不少人。",
                    choices = listOf(
                        EventChoice("赴宴应酬",
                            EventConsequence(cashChange = -1.0)),
                        EventChoice("婉拒，忙于校务",
                            EventConsequence(reputationChange = -1))
                    )
                )
            }
            ConnectionType.MEDIA_REPORTER -> {
                if (school.reputation > 2000) {
                    GameEvent.ChoiceEvent(
                        title = "记者来访",
                        message = "${connection.name}想写一篇关于学校的深度报道，但可能会挖出一些不想公开的细节。",
                        choices = listOf(
                            EventChoice("配合采访",
                                EventConsequence(reputationChange = 800)),
                            EventChoice("礼貌推脱",
                                EventConsequence(reputationChange = -1))
                        )
                    )
                } else null
            }
            ConnectionType.PARENT_REPRESENTATIVE -> {
                GameEvent.ChoiceEvent(
                    title = "家长代表反馈",
                    message = "${connection.name}私下告诉你：最近有几位家长在群里议论学校的伙食质量。",
                    choices = listOf(
                        EventChoice("立即改善伙食",
                            EventConsequence(cashChange = -2.0, reputationChange = 300)),
                        EventChoice("暂时观望",
                            EventConsequence(reputationChange = -50))
                    )
                )
            }
            else -> null
        }
    }

    private fun calculateEffect(
        connection: Connection,
        purpose: ConnectionPurpose,
        school: School
    ): ConnectionUseResult {
        return when (purpose) {
            ConnectionPurpose.GET_POLICY_INFO -> ConnectionUseResult(
                true, "获悉下个月可能有突击检查，提前做好准备",
                reputationGain = 200
            )
            ConnectionPurpose.REDUCE_INSPECTION_SEVERITY -> ConnectionUseResult(
                true, "对方表示会'关照'一下",
                inspectionBonus = 15
            )
            ConnectionPurpose.GET_DISCOUNT -> ConnectionUseResult(
                true, "拿到了八折优惠，省下不少钱",
                cashGain = Random.nextDouble(2.0, 5.0)
            )
            ConnectionPurpose.EMERGENCY_LOAN -> ConnectionUseResult(
                true, "对方爽快借了20万，半年内还就行",
                cashGain = 20.0
            )
            ConnectionPurpose.POSITIVE_COVERAGE -> ConnectionUseResult(
                true, "一篇正面报道发布，学校知名度提升",
                reputationGain = 600
            )
            ConnectionPurpose.SUPPRESS_NEGATIVE -> ConnectionUseResult(
                true, "负面报道被撤下，风波暂时平息",
                reputationGain = 300
            )
            ConnectionPurpose.RECRUIT_STUDENTS -> ConnectionUseResult(
                true, "通过口碑介绍，多了几位优质生源",
                reputationGain = 200
            )
            ConnectionPurpose.CALM_PARENTS -> ConnectionUseResult(
                true, "家长代表帮忙做了工作，投诉声减少了",
                reputationGain = 150
            )
            ConnectionPurpose.SHARE_INFO -> ConnectionUseResult(
                true, "交流中获得了有价值的办学建议",
                reputationGain = 100
            )
            ConnectionPurpose.TEACHER_RECOMMEND -> ConnectionUseResult(
                true, "对方推荐了一位不错的老师，可以联系试试",
                reputationGain = 100
            )
            ConnectionPurpose.HANDLE_INCIDENT -> ConnectionUseResult(
                true, "纠纷得到妥善协调处理",
                reputationGain = 200
            )
        }
    }

    private fun recalculateConnectionLevel(principal: Principal) {
        val fromConnections = principal.connections.sumOf { it.relationLevel } / 10
        principal.connectionLevel = (fromConnections + principal.connectionBonus).coerceIn(0, 100)
    }

    private fun calculateGameDay(school: School): Int {
        return (school.currentYear - 1988) * 360 + (school.currentMonth - 1) * 30 + school.currentDay
    }

    private fun generateName(type: ConnectionType): String {
        val surnames = listOf("王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴",
            "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗")
        val maleNames = listOf("建国", "志强", "伟", "明", "军", "磊", "洋", "勇", "斌", "强",
            "平", "刚", "华", "飞", "鑫", "波", "宁", "辉", "峰", "超")
        val femaleNames = listOf("秀英", "丽", "敏", "静", "芳", "娟", "英", "华", "玲", "红",
            "燕", "萍", "慧", "琳", "雪", "洁", "霞", "莉", "蓉", "梅")

        val surname = surnames.random()
        val name = when (type) {
            ConnectionType.EDUCATION_OFFICIAL, ConnectionType.GOVERNMENT_INSPECTOR,
            ConnectionType.POLICE_CONTACT -> surname + maleNames.random()
            ConnectionType.PARENT_REPRESENTATIVE -> surname + if (Random.nextBoolean()) maleNames.random() else femaleNames.random()
            else -> surname + maleNames.random()
        }
        return name
    }
}

enum class ConnectionPurpose {
    GET_POLICY_INFO,
    REDUCE_INSPECTION_SEVERITY,
    GET_DISCOUNT,
    EMERGENCY_LOAN,
    POSITIVE_COVERAGE,
    SUPPRESS_NEGATIVE,
    RECRUIT_STUDENTS,
    CALM_PARENTS,
    SHARE_INFO,
    TEACHER_RECOMMEND,
    HANDLE_INCIDENT
}

data class ConnectionAction(
    val connection: Connection,
    val purpose: ConnectionPurpose,
    val description: String,
    val cost: Double
)

data class ConnectionUseResult(
    val success: Boolean,
    val message: String,
    val cashGain: Double = 0.0,
    val reputationGain: Long = 0,
    val inspectionBonus: Int = 0
)
