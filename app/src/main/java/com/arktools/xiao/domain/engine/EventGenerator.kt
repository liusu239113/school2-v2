package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.EventChoice
import com.arktools.xiao.domain.model.EventConsequence
import com.arktools.xiao.domain.model.GameEvent
import com.arktools.xiao.domain.model.MilestoneType
import com.arktools.xiao.domain.model.School
import com.arktools.xiao.domain.model.Principal
import com.arktools.xiao.domain.model.FactionType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 事件生成器 - 100+条件触发事件
 * 所有事件基于游戏真实状态触发，非纯随机
 *
 * 事件分类：
 * 1. 季节性事件（月份触发）
 * 2. 规模事件（校园等级触发）
 * 3. 时代事件（年份触发）
 * 4. 状态事件（学校运营状况触发）
 * 5. 财务事件（资金状况触发）
 * 6. 声誉事件（声誉等级触发）
 * 7. 教师事件（师资状况触发）
 * 8. 校长个人事件（校长属性触发）
 * 9. 腐败相关事件（腐败行为后果触发）
 * 10. 人脉事件（关系网络触发）
 * 11. 派系事件（内部政治触发）
 * 12. 设施事件（设施状态触发）
 * 13. 社会事件（综合状态触发）
 */
@Singleton
class EventGenerator @Inject constructor() {

    // 月度事件机制：每月保证1-2个趣味事件
    private var currentEventMonth: Int = -1   // 当前追踪的月份
    private var monthlyEventCount: Int = 0    // 本月已触发事件数
    private var monthlyEventTarget: Int = 1   // 本月目标事件数（1或2）
    private val EVENT_DAY_FIRST = 8           // 第一个事件触发日（月初第8天左右）
    private val EVENT_DAY_SECOND = 22         // 第二个事件触发日（月中第22天左右）

    // 去重机制：记录最近触发的事件标题
    private val recentEventTitles: MutableList<String> = mutableListOf()
    private val MAX_RECENT_HISTORY = 25  // 记住最近25个事件

    fun generateEvent(school: School, principal: Principal? = null, studentCount: Int = 0): GameEvent? {
        // 新学校前3个月（90天）不触发随机事件
        val schoolAgeDays = (school.currentYear - school.foundedYear) * 360 +
                (school.currentMonth - 1) * 30 + school.currentDay
        if (schoolAgeDays < 90) return null

        // 月度重置：进入新月份时重新规划本月事件数
        val monthKey = school.currentYear * 12 + school.currentMonth
        if (monthKey != currentEventMonth) {
            currentEventMonth = monthKey
            monthlyEventCount = 0
            // 每月随机决定目标事件数：60%概率1个，40%概率2个
            monthlyEventTarget = if (Random.nextFloat() < 0.4f) 2 else 1
        }

        // 本月事件已满，不再触发
        if (monthlyEventCount >= monthlyEventTarget) return null

        // 根据目标数决定触发日
        val day = school.currentDay
        val shouldTrigger = when {
            monthlyEventTarget == 1 -> {
                // 只有1个事件：在第8-12天之间触发
                day in EVENT_DAY_FIRST..(EVENT_DAY_FIRST + 4) && monthlyEventCount == 0
            }
            monthlyEventTarget == 2 -> {
                // 有2个事件：第一个在8-12天，第二个在22-26天
                (day in EVENT_DAY_FIRST..(EVENT_DAY_FIRST + 4) && monthlyEventCount == 0) ||
                (day in EVENT_DAY_SECOND..(EVENT_DAY_SECOND + 4) && monthlyEventCount == 1)
            }
            else -> false
        }

        if (!shouldTrigger) return null

        // 在触发窗口内，每天50%概率触发（避免每月同一天出事件）
        if (Random.nextFloat() > 0.5f) return null

        // 构建完整事件池
        val eventPool = buildList {
            addAll(getSeasonalEvents(school))
            addAll(getScaleEvents(school))
            addAll(getEraEvents(school))
            addAll(getStateBasedEvents(school))
            addAll(getFinancialEvents(school))
            addAll(getReputationEvents(school))
            addAll(getTeacherEvents(school))
            addAll(getStudentEvents(school, studentCount))
            addAll(getFacilityEvents(school))
            addAll(getSocialEvents(school))
            // 新系统事件
            if (principal != null) {
                addAll(getPrincipalPersonalEvents(principal, school))
                addAll(getCorruptionRelatedEvents(principal, school))
                addAll(getConnectionEvents(principal, school))
                addAll(getFactionEvents(principal, school))
            }
        }

        if (eventPool.isEmpty()) return null

        // 去重
        val filteredPool = eventPool.filter { it.title !in recentEventTitles }
        if (filteredPool.isEmpty()) {
            recentEventTitles.clear()
            return null
        }

        val event = filteredPool.random()

        // 记录事件
        monthlyEventCount++
        recentEventTitles.add(event.title)
        if (recentEventTitles.size > MAX_RECENT_HISTORY) {
            recentEventTitles.removeAt(0)
        }

        return event
    }

    // ========== 1. 季节性事件 ==========

    private fun getSeasonalEvents(school: School): List<GameEvent> {
        val event = SemesterCalendar.getMonthlyEvent(school.currentMonth)
        return when (event) {
            SemesterCalendar.SchoolEvent.ENROLLMENT_SEASON -> {
                if (school.reputation > 500) listOf(
                    GameEvent.PositiveEvent(
                        title = "招生火爆",
                        message = "招生季来临，学校声誉在外，今年报名人数超出预期！",
                        bonusReputation = 300
                    )
                ) else if (school.campusLevel >= 2) listOf(
                    GameEvent.ChoiceEvent(
                        title = "招生策略",
                        message = "招生季到来，报名人数一般，是否加大宣传投入？",
                        choices = listOf(
                            EventChoice("加大投入（花费5万，声誉+800）",
                                EventConsequence(cashChange = -5.0, reputationChange = 800)),
                            EventChoice("保持现状", EventConsequence())
                        )
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.MIDTERM_EXAM -> {
                if (school.totalCoursesReleased > 0) listOf(
                    GameEvent.ChoiceEvent(
                        title = "期中考试",
                        message = "期中考试即将来临，教师们提议组织考前冲刺班。",
                        choices = listOf(
                            EventChoice("组织冲刺班（教师忠诚-5，声誉+400）",
                                EventConsequence(reputationChange = 400, teacherLoyaltyChange = -5)),
                            EventChoice("正常教学安排",
                                EventConsequence(reputationChange = 100))
                        )
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.FINAL_EXAM -> {
                if (school.reputation > 1000) listOf(
                    GameEvent.PositiveEvent(
                        title = "优异成绩",
                        message = "期末考试成绩出炉，多名学生进入年级前十，家长纷纷口碑传播！",
                        bonusReputation = 600
                    )
                ) else if (school.totalCoursesReleased > 0) listOf(
                    GameEvent.NegativeEvent(
                        title = "考试压力",
                        message = "期末考试来临，部分学生因压力出现厌学情绪，家长有所不满。",
                        penaltyReputation = 200
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.GRADUATION -> {
                if (school.campusLevel >= 3 && school.reputation > 2000) listOf(
                    GameEvent.ChoiceEvent(
                        title = "校友捐赠",
                        message = "一位事业有成的校友返校，提出捐赠30万，但希望以其名字命名新教学楼。",
                        choices = listOf(
                            EventChoice("接受捐赠（资金+30万）",
                                EventConsequence(cashChange = 30.0)),
                            EventChoice("婉言谢绝（保持独立，声誉+500）",
                                EventConsequence(reputationChange = 500))
                        )
                    )
                ) else if (school.totalCoursesReleased >= 3) listOf(
                    GameEvent.PositiveEvent(
                        title = "毕业典礼",
                        message = "毕业典礼顺利举行，优秀毕业生代表发言感谢母校培育！",
                        bonusReputation = 500, bonusCash = 2.0
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.SUMMER_CAMP -> {
                if (school.campusLevel >= 2 && school.facilities.isNotEmpty()) listOf(
                    GameEvent.ChoiceEvent(
                        title = "暑期夏令营",
                        message = "暑假来临，设施闲置，是否利用场地举办收费夏令营？",
                        choices = listOf(
                            EventChoice("举办夏令营（资金+8万，声誉+200）",
                                EventConsequence(cashChange = 8.0, reputationChange = 200)),
                            EventChoice("让师生休息（教师忠诚+10）",
                                EventConsequence(teacherLoyaltyChange = 10))
                        )
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.SPORTS_DAY -> {
                if (school.facilities.any { it.type == com.arktools.xiao.domain.model.FacilityType.SPORTS_FIELD }) listOf(
                    GameEvent.PositiveEvent(
                        title = "运动会佳绩",
                        message = "校运动会上学生打破校记录，全校氛围积极向上！",
                        bonusReputation = 400
                    )
                ) else if (school.campusLevel >= 2) listOf(
                    GameEvent.NegativeEvent(
                        title = "运动受伤",
                        message = "运动会上一名学生在简陋场地上受伤，家长要求赔偿。",
                        penaltyCash = 2.0, penaltyReputation = 150
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.SCIENCE_FAIR -> {
                if (school.reputation > 1500 && school.totalCoursesReleased >= 5) listOf(
                    GameEvent.PositiveEvent(
                        title = "科学展获奖",
                        message = "学生的创新项目在全市科学展上获得一等奖！",
                        bonusReputation = 800, bonusCash = 2.0
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.PARENT_MEETING -> {
                if (school.totalCoursesReleased > 0) listOf(
                    GameEvent.ChoiceEvent(
                        title = "家长会反馈",
                        message = "家长会上，多位家长对目前的课程安排提出改进建议。",
                        choices = listOf(
                            EventChoice("积极采纳（声誉+300）",
                                EventConsequence(reputationChange = 300)),
                            EventChoice("解释现有安排（声誉-100）",
                                EventConsequence(reputationChange = -100))
                        )
                    )
                ) else emptyList()
            }
            SemesterCalendar.SchoolEvent.NEW_YEAR -> {
                if (school.campusLevel >= 2) listOf(
                    GameEvent.PositiveEvent(
                        title = "新年祝福",
                        message = "新年到来，学校收到家长和社区的支持与祝福！",
                        bonusCash = 3.0, bonusReputation = 200
                    )
                ) else emptyList()
            }
            else -> emptyList()
        }
    }

    // ========== 2. 规模事件 ==========

    private fun getScaleEvents(school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (school.campusLevel <= 3 && school.reputation > 300) {
            events.add(GameEvent.ChoiceEvent(
                title = "社区开放日",
                message = "附近居民好奇学校情况，是否举办社区开放日展示教学成果？",
                choices = listOf(
                    EventChoice("举办开放日（花费1万，声誉+400）",
                        EventConsequence(cashChange = -1.0, reputationChange = 400)),
                    EventChoice("暂时低调发展", EventConsequence())
                )
            ))
        }

        if (school.campusLevel in 4..6) {
            events.add(GameEvent.ChoiceEvent(
                title = "行业协会巡检",
                message = "民办教育行业协会通知下周将来学校巡检教学质量和安全设施。",
                choices = listOf(
                    EventChoice("精心准备（花费3万，声誉+600）",
                        EventConsequence(cashChange = -3.0, reputationChange = 600)),
                    EventChoice("如实展示（声誉+200）",
                        EventConsequence(reputationChange = 200))
                )
            ))
        }

        if (school.campusLevel >= 7 && school.reputation > 5000) {
            events.add(GameEvent.ChoiceEvent(
                title = "国际交流合作",
                message = "一所海外知名学校提出交换生项目合作意向。",
                choices = listOf(
                    EventChoice("签约合作（花费20万，声誉+2000）",
                        EventConsequence(cashChange = -20.0, reputationChange = 2000)),
                    EventChoice("暂缓考虑", EventConsequence())
                )
            ))
        }

        if (school.campusLevel >= 8 && school.reputation > 10000) {
            events.add(GameEvent.PositiveEvent(
                title = "区域标杆",
                message = "学校已成为区域教育标杆，行业协会发来表彰通知！",
                bonusReputation = 2000, bonusCash = 10.0
            ))
        }

        // 新增规模事件
        if (school.campusLevel == 2 && school.totalCoursesReleased >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "周边学校打探",
                message = "邻近一所学校的教务主任假装家长来参观你的学校，被门卫认出。",
                choices = listOf(
                    EventChoice("大方接待，展示自信（声誉+200）",
                        EventConsequence(reputationChange = 200)),
                    EventChoice("婉拒参观，避免泄密", EventConsequence())
                )
            ))
        }

        if (school.campusLevel >= 5 && school.branchSchools == 0) {
            events.add(GameEvent.ChoiceEvent(
                title = "分校邀约",
                message = "隔壁区的教育投资商来信，希望你去他们那里开设分校，条件优厚。",
                choices = listOf(
                    EventChoice("表示兴趣（声誉+300）",
                        EventConsequence(reputationChange = 300)),
                    EventChoice("专注本校发展", EventConsequence())
                )
            ))
        }

        return events
    }

    // ========== 3. 发展阶段事件（按办学年数触发） ==========

    private fun getEraEvents(school: School): List<GameEvent> {
        val schoolAge = school.currentYear - school.foundedYear
        val events = mutableListOf<GameEvent>()

        when {
            // 创业期（前5年）：白手起家，筚路蓝缕
            schoolAge in 0..4 -> {
                if (school.cash > 10.0) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "招生动员",
                        message = "开学季到来，周边社区适龄学生众多，是否加大招生力度扩大规模？",
                        choices = listOf(
                            EventChoice("积极扩招（声誉+500，教师压力增大）",
                                EventConsequence(reputationChange = 500, teacherLoyaltyChange = -8)),
                            EventChoice("量力而行，稳步发展",
                                EventConsequence(reputationChange = 100))
                        )
                    ))
                }
                if (school.campusLevel >= 2) {
                    events.add(GameEvent.PositiveEvent(
                        title = "热心企业赞助",
                        message = "本地一家企业看好你的办学理念，主动赞助了一笔教育发展经费！",
                        bonusCash = 5.0
                    ))
                }
                if (schoolAge >= 3 && school.campusLevel >= 2) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "公益助学合作",
                        message = "一家教育公益基金会来访，提出资助困难家庭学生入读你校的合作意向。",
                        choices = listOf(
                            EventChoice("接受合作（声誉+600，需承担部分费用）",
                                EventConsequence(cashChange = -3.0, reputationChange = 600)),
                            EventChoice("暂时婉拒", EventConsequence())
                        )
                    ))
                }
            }
            // 成长期（5-12年）：站稳脚跟，开拓创新
            schoolAge in 5..11 -> {
                if (school.campusLevel >= 3 && school.cash > 15.0) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "信息化建设",
                        message = "教育信息化浪潮来临，是否投入资金建设智慧教室和多媒体设备？",
                        choices = listOf(
                            EventChoice("全面升级（花费8万，声誉+400）",
                                EventConsequence(cashChange = -8.0, reputationChange = 400)),
                            EventChoice("暂缓，继续用传统教学", EventConsequence())
                        )
                    ))
                }
                if (schoolAge >= 6 && school.totalCoursesReleased > 0) {
                    events.add(GameEvent.NegativeEvent(
                        title = "流感停课",
                        message = "流感季来袭，校内多名学生出现症状，为安全起见停课消毒一周。",
                        penaltyCash = 3.0, penaltyReputation = 200
                    ))
                }
                if (schoolAge >= 8 && school.campusLevel >= 4) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "社区救灾募捐",
                        message = "本地遭遇罕见暴雨，社区受灾严重。师生自发组织捐款，学校是否追加配捐？",
                        choices = listOf(
                            EventChoice("学校配捐10万（声誉+1000）",
                                EventConsequence(cashChange = -10.0, reputationChange = 1000)),
                            EventChoice("号召师生捐款但学校不追加（声誉+200）",
                                EventConsequence(reputationChange = 200))
                        )
                    ))
                }
                if (school.campusLevel >= 3 && school.cash > 10.0) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "在线教育浪潮",
                        message = "互联网教育平台强势崛起，是否也开设线上课程拓展生源？",
                        choices = listOf(
                            EventChoice("拥抱线上（花费5万，声誉+600）",
                                EventConsequence(cashChange = -5.0, reputationChange = 600)),
                            EventChoice("坚守线下特色",
                                EventConsequence(reputationChange = 100))
                        )
                    ))
                }
            }
            // 成熟期（12-20年）：稳中求变，特色发展
            schoolAge in 12..19 -> {
                if (school.reputation > 3000) {
                    events.add(GameEvent.PositiveEvent(
                        title = "特色课程走红",
                        message = "学校的特色课程获得家长圈广泛推荐，报名人数激增！",
                        bonusReputation = 500
                    ))
                }
                if (school.campusLevel >= 3) {
                    events.add(GameEvent.NegativeEvent(
                        title = "家长群风波",
                        message = "一位家长在班级群吐槽学校伙食，引发连锁反应，截图被广泛传播。",
                        penaltyReputation = 400
                    ))
                }
                if (school.campusLevel >= 4 && school.totalCoursesReleased >= 3) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "课程结构调整",
                        message = "家长对课业负担过重的呼声越来越高，是否调整课程结构，增加素质类课程比例？",
                        choices = listOf(
                            EventChoice("全面优化课程体系（花费10万，声誉+800）",
                                EventConsequence(cashChange = -10.0, reputationChange = 800)),
                            EventChoice("维持现有安排（声誉-200）",
                                EventConsequence(reputationChange = -200))
                        )
                    ))
                }
                if (school.campusLevel >= 4) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "心理健康关注",
                        message = "近期学生心理问题有上升趋势，是否配备专职心理咨询师？",
                        choices = listOf(
                            EventChoice("聘请心理咨询师（花费5万/年，声誉+500）",
                                EventConsequence(cashChange = -5.0, reputationChange = 500)),
                            EventChoice("由学业导师兼任（声誉+100）",
                                EventConsequence(reputationChange = 100))
                        )
                    ))
                }
            }
            // 巅峰期（20年+）：名校光环，新的挑战
            schoolAge >= 20 -> {
                if (school.campusLevel >= 5 && school.reputation > 3000) {
                    events.add(GameEvent.PositiveEvent(
                        title = "AI教育先锋",
                        message = "人工智能教育成为热点，学校凭借前瞻布局率先开设编程和AI课程！",
                        bonusReputation = 600, bonusCash = 5.0
                    ))
                }
                if (school.campusLevel >= 3) {
                    events.add(GameEvent.NegativeEvent(
                        title = "生源竞争加剧",
                        message = "周边新开了多所学校，适龄学生被分流，招生竞争越来越激烈。",
                        penaltyReputation = 300
                    ))
                }
                if (school.campusLevel >= 4 && school.cash > 20.0) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "教育集团化",
                        message = "有投资人提议将学校品牌做成教育集团，在多个城市开设连锁分校。",
                        choices = listOf(
                            EventChoice("尝试集团化（花费15万，声誉+1200）",
                                EventConsequence(cashChange = -15.0, reputationChange = 1200)),
                            EventChoice("专注当前学校品质",
                                EventConsequence(reputationChange = 200))
                        )
                    ))
                }
                if (school.reputation > 5000) {
                    events.add(GameEvent.ChoiceEvent(
                        title = "国际交流邀请",
                        message = "一所海外知名学校发来合作邀请，希望开展师生交换项目。",
                        choices = listOf(
                            EventChoice("建立合作关系（花费8万，声誉+900）",
                                EventConsequence(cashChange = -8.0, reputationChange = 900)),
                            EventChoice("暂不考虑",
                                EventConsequence())
                        )
                    ))
                }
            }
        }

        return events
    }

    // ========== 4. 状态驱动事件 ==========

    private fun getStateBasedEvents(school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val schoolAge = school.currentYear - school.foundedYear

        if (school.reputation > 5000 && school.campusLevel >= 4) {
            events.add(GameEvent.PositiveEvent(
                title = "口碑招生",
                message = "学校声誉远播，不少家长慕名而来主动咨询招生事宜！",
                bonusReputation = 400
            ))
            events.add(GameEvent.ChoiceEvent(
                title = "媒体采访",
                message = "本地电视台希望来拍摄一期关于学校办学理念的专题报道。",
                choices = listOf(
                    EventChoice("欣然接受采访（声誉+1000）",
                        EventConsequence(reputationChange = 1000)),
                    EventChoice("婉拒，低调办学", EventConsequence(reputationChange = 50))
                )
            ))
        }

        if (school.reputation < 200 && schoolAge > 1) {
            events.add(GameEvent.NegativeEvent(
                title = "家长流失",
                message = "学校口碑下滑，部分家长开始考虑转校。",
                penaltyReputation = 100
            ))
        }

        if (school.cash > 100.0 && school.campusLevel >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "教育地产机遇",
                message = "附近有一块地正在出售，适合扩建校区或建设新设施。",
                choices = listOf(
                    EventChoice("购入土地（花费50万，校园潜力大增）",
                        EventConsequence(cashChange = -50.0, reputationChange = 800)),
                    EventChoice("暂不考虑", EventConsequence())
                )
            ))
        }

        if (schoolAge >= 10 && school.reputation > 3000) {
            events.add(GameEvent.ChoiceEvent(
                title = "校庆筹备",
                message = "学校即将迎来${schoolAge}周年校庆，是否大办庆典邀请校友回校？",
                choices = listOf(
                    EventChoice("隆重举办（花费8万，声誉+1200）",
                        EventConsequence(cashChange = -8.0, reputationChange = 1200)),
                    EventChoice("简朴纪念", EventConsequence(reputationChange = 200))
                )
            ))
        }

        if (school.branchSchools > 0) {
            events.add(GameEvent.ChoiceEvent(
                title = "集团品牌建设",
                message = "多校区运营需要统一品牌形象，是否投入资金做品牌升级？",
                choices = listOf(
                    EventChoice("统一品牌升级（花费15万，声誉+1500）",
                        EventConsequence(cashChange = -15.0, reputationChange = 1500)),
                    EventChoice("各校区独立运营", EventConsequence())
                )
            ))
        }

        if (school.hasOwnTextbook) {
            events.add(GameEvent.PositiveEvent(
                title = "教材获奖",
                message = "学校自主研发的教材在全国教育创新评比中获得认可！",
                bonusReputation = 1000, bonusCash = 5.0
            ))
        }

        if (school.hasOwnTech && school.currentYear >= 2015) {
            events.add(GameEvent.PositiveEvent(
                title = "科技教育示范",
                message = "学校的技术实力被评为区域科技教育示范基地！",
                bonusReputation = 800, bonusCash = 3.0
            ))
        }

        if (school.totalCoursesReleased >= 8) {
            events.add(GameEvent.ChoiceEvent(
                title = "课程体系梳理",
                message = "课程越来越多，教务处建议花时间梳理课程体系，去重优化。",
                choices = listOf(
                    EventChoice("启动课程优化（声誉+500）",
                        EventConsequence(reputationChange = 500)),
                    EventChoice("维持现状", EventConsequence())
                )
            ))
        }

        if (school.marketingCampaigns.isNotEmpty()) {
            events.add(GameEvent.PositiveEvent(
                title = "营销见效",
                message = "近期的营销推广活动效果超出预期，咨询量明显上升！",
                bonusReputation = 600
            ))
        }

        // 新增状态事件
        if (school.wasNearBankrupt && school.cash > 50.0) {
            events.add(GameEvent.PositiveEvent(
                title = "浴火重生",
                message = "学校从濒临倒闭到如今蒸蒸日上，这段逆袭故事被本地报纸报道！",
                bonusReputation = 800
            ))
        }

        if (schoolAge >= 5 && school.campusLevel <= 2) {
            events.add(GameEvent.NegativeEvent(
                title = "发展停滞质疑",
                message = "有家长在网上发帖质疑学校五年了还在原地踏步，管理层是否有问题？",
                penaltyReputation = 300
            ))
        }

        if (school.totalRevenue > 1000.0) {
            events.add(GameEvent.ChoiceEvent(
                title = "慈善晚宴邀请",
                message = "市慈善总会邀请学校出席年度慈善晚宴并捐赠。",
                choices = listOf(
                    EventChoice("出席并捐赠10万（声誉+800）",
                        EventConsequence(cashChange = -10.0, reputationChange = 800)),
                    EventChoice("礼貌婉拒", EventConsequence())
                )
            ))
        }

        return events
    }

    // ========== 5. 财务事件 ==========

    private fun getFinancialEvents(school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val monthlyRent = GameBalanceConfig.getMonthlyRent(school.campusLevel)

        if (school.cash < monthlyRent * 3 && school.cash > 0) {
            events.add(GameEvent.ChoiceEvent(
                title = "资金周转困难",
                message = "学校账面资金已不足支撑三个月运营，是否向银行申请贷款？",
                choices = listOf(
                    EventChoice("申请贷款（资金+20万，未来还款压力增大）",
                        EventConsequence(cashChange = 20.0, reputationChange = -100)),
                    EventChoice("节流自救（声誉-50）",
                        EventConsequence(reputationChange = -50))
                )
            ))
        }

        if (school.cash > 200.0 && school.campusLevel >= 5) {
            events.add(GameEvent.ChoiceEvent(
                title = "公益基金",
                message = "资金充裕，是否设立教育公益基金资助贫困学生？",
                choices = listOf(
                    EventChoice("设立基金（花费20万，声誉+1500）",
                        EventConsequence(cashChange = -20.0, reputationChange = 1500)),
                    EventChoice("暂不考虑", EventConsequence())
                )
            ))
        }

        if (school.totalRevenue > 500.0 && school.campusLevel >= 4) {
            events.add(GameEvent.PositiveEvent(
                title = "行业表彰奖金",
                message = "学校被评为年度优秀民办教育机构，获得行业协会颁发的奖金！",
                bonusCash = 8.0, bonusReputation = 300
            ))
        }

        // 新增财务事件
        if (school.cash > 50.0 && school.campusLevel >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "理财产品推荐",
                message = "银行客户经理推荐了一款教育机构专属理财产品，年化6%。",
                choices = listOf(
                    EventChoice("投入20万理财（一年后收益约1.2万）",
                        EventConsequence(cashChange = -20.0)),
                    EventChoice("资金留着备用", EventConsequence())
                )
            ))
        }

        if (school.cash < 5.0 && school.campusLevel >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "供应商催款",
                message = "装修公司和设备供应商联合催讨欠款，威胁走法律途径。",
                choices = listOf(
                    EventChoice("想办法优先还款（声誉-100，避免诉讼）",
                        EventConsequence(reputationChange = -100)),
                    EventChoice("请求宽限一个月", EventConsequence(reputationChange = -200))
                )
            ))
        }

        if (school.totalRevenue > 200.0 && school.campusLevel >= 4) {
            events.add(GameEvent.ChoiceEvent(
                title = "审计抽查",
                message = "财政局对民办教育机构进行年度抽查审计，你校被抽中。",
                choices = listOf(
                    EventChoice("全面配合（如账目清白则声誉+300）",
                        EventConsequence(reputationChange = 300)),
                    EventChoice("尽力应付", EventConsequence())
                )
            ))
        }

        return events
    }

    // ========== 6. 声誉事件 ==========

    private fun getReputationEvents(school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (school.reputation in 1000..3000 && school.campusLevel >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "教育评选",
                message = "行业协会邀请学校参加年度优秀学校评选。",
                choices = listOf(
                    EventChoice("参与评选（花费2万准备材料，声誉+800）",
                        EventConsequence(cashChange = -2.0, reputationChange = 800)),
                    EventChoice("今年不参加", EventConsequence())
                )
            ))
        }

        if (school.reputation > 8000) {
            events.add(GameEvent.NegativeEvent(
                title = "同行竞争",
                message = "学校名气太大，引来竞争对手散布不实传闻，部分家长信以为真。",
                penaltyReputation = 500
            ))
        }

        if (school.reputation in 500..1500 && school.totalCoursesReleased >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "家长口碑活动",
                message = "有几位家长主动提出愿意在朋友圈帮忙宣传学校。",
                choices = listOf(
                    EventChoice("配合准备宣传素材（声誉+400）",
                        EventConsequence(reputationChange = 400)),
                    EventChoice("不必麻烦家长", EventConsequence())
                )
            ))
        }

        // 新增声誉事件
        if (school.reputation > 3000 && school.campusLevel >= 4) {
            events.add(GameEvent.ChoiceEvent(
                title = "教育大V探校",
                message = "一位拥有百万粉丝的教育博主想来探校直播。",
                choices = listOf(
                    EventChoice("欢迎来访（声誉+1500，但有翻车风险）",
                        EventConsequence(reputationChange = 1500)),
                    EventChoice("委婉谢绝", EventConsequence())
                )
            ))
        }

        if (school.reputation > 6000) {
            events.add(GameEvent.ChoiceEvent(
                title = "教育论坛发言",
                message = "受邀在全国教育论坛上做主题演讲，展示学校办学经验。",
                choices = listOf(
                    EventChoice("精心准备演讲（花费3万，声誉+2000）",
                        EventConsequence(cashChange = -3.0, reputationChange = 2000)),
                    EventChoice("派副校长代为出席（声誉+500）",
                        EventConsequence(reputationChange = 500))
                )
            ))
        }

        if (school.reputation < 500 && school.campusLevel >= 3) {
            events.add(GameEvent.NegativeEvent(
                title = "家长投诉",
                message = "有家长联名投诉学校管理混乱，校务办要求尽快书面回复并整改。",
                penaltyReputation = 200
            ))
        }

        if (school.reputation in 2000..5000) {
            events.add(GameEvent.PositiveEvent(
                title = "学区房效应",
                message = "学校周边房价因为学校口碑而上涨，中介纷纷把'学区'当卖点。",
                bonusReputation = 300
            ))
        }

        return events
    }

    // ========== 7. 教师事件 ==========

    private fun getTeacherEvents(school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (school.maxTeachers > 5 && school.campusLevel >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "教师进修",
                message = "一位骨干教师申请参加省级教学技能培训，需要脱产两周。",
                choices = listOf(
                    EventChoice("批准并承担费用（花费3万，教师忠诚+10）",
                        EventConsequence(cashChange = -3.0, teacherLoyaltyChange = 10)),
                    EventChoice("暂时不批准（忠诚-5）",
                        EventConsequence(teacherLoyaltyChange = -5))
                )
            ))
        }

        if (school.campusLevel >= 4 && school.maxTeachers >= 12) {
            events.add(GameEvent.ChoiceEvent(
                title = "教师跳槽",
                message = "一位优秀教师收到竞争对手开出的双倍薪资邀请，提出辞职。",
                choices = listOf(
                    EventChoice("加薪挽留（花费5万，忠诚+20）",
                        EventConsequence(cashChange = -5.0, teacherLoyaltyChange = 20)),
                    EventChoice("尊重其选择", EventConsequence(teacherLoyaltyChange = -5))
                )
            ))
        }

        if (school.campusLevel >= 5 && school.reputation > 3000) {
            events.add(GameEvent.PositiveEvent(
                title = "名师慕名",
                message = "学校声誉在外，一位资深名师主动来电询问入职事宜！",
                bonusReputation = 400, bonusTeacherSkill = 5
            ))
        }

        if (school.maxTeachers >= 8 && school.campusLevel >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "教师团建",
                message = "学期结束，行政部建议组织一次教师团建活动增强凝聚力。",
                choices = listOf(
                    EventChoice("组织团建（花费2万，忠诚+15）",
                        EventConsequence(cashChange = -2.0, teacherLoyaltyChange = 15)),
                    EventChoice("发放购物卡代替（花费1万，忠诚+5）",
                        EventConsequence(cashChange = -1.0, teacherLoyaltyChange = 5))
                )
            ))
        }

        // 新增教师事件
        if (school.maxTeachers >= 10) {
            events.add(GameEvent.ChoiceEvent(
                title = "教师恋情",
                message = "两位年轻教师在校内公开恋爱关系，部分家长认为影响教学风气。",
                choices = listOf(
                    EventChoice("不干涉私人生活（忠诚+10）",
                        EventConsequence(teacherLoyaltyChange = 10)),
                    EventChoice("建议低调处理（忠诚-5）",
                        EventConsequence(teacherLoyaltyChange = -5))
                )
            ))
        }

        if (school.maxTeachers >= 15 && school.campusLevel >= 5) {
            events.add(GameEvent.ChoiceEvent(
                title = "教师工会诉求",
                message = "教师集体提出加班补贴和带薪年假的诉求，递交了联名信。",
                choices = listOf(
                    EventChoice("满足诉求（花费8万/年，忠诚+25）",
                        EventConsequence(cashChange = -8.0, teacherLoyaltyChange = 25)),
                    EventChoice("部分满足（花费3万，忠诚+10）",
                        EventConsequence(cashChange = -3.0, teacherLoyaltyChange = 10)),
                    EventChoice("暂时搁置（忠诚-15）",
                        EventConsequence(teacherLoyaltyChange = -15))
                )
            ))
        }

        if (school.maxTeachers >= 6 && school.totalCoursesReleased >= 3) {
            events.add(GameEvent.NegativeEvent(
                title = "教师生病请长假",
                message = "一位主力教师突发疾病需要休养两个月，课程临时需要找人代课。",
                penaltyCash = 2.0
            ))
        }

        if (school.campusLevel >= 3 && school.maxTeachers >= 8) {
            events.add(GameEvent.ChoiceEvent(
                title = "教师论文发表",
                message = "一位教师的教学论文被核心期刊录用，提出希望学校资助出版费用。",
                choices = listOf(
                    EventChoice("全额资助（花费1万，声誉+300，忠诚+10）",
                        EventConsequence(cashChange = -1.0, reputationChange = 300, teacherLoyaltyChange = 10)),
                    EventChoice("资助一半（花费0.5万，忠诚+5）",
                        EventConsequence(cashChange = -0.5, teacherLoyaltyChange = 5))
                )
            ))
        }

        return events
    }

    // ========== 8. 校长个人事件 ==========

    private fun getPrincipalPersonalEvents(principal: Principal, school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        // 个人声望相关
        if (principal.personalReputation > 80 && school.reputation > 5000) {
            events.add(GameEvent.ChoiceEvent(
                title = "升迁机会",
                message = "教育集团总部看中了你的能力，邀请你出任集团副总裁，但需要离开学校。",
                choices = listOf(
                    EventChoice("婉拒，留在学校（声誉+500，个人声望+10）",
                        EventConsequence(reputationChange = 500)),
                    EventChoice("表示考虑中", EventConsequence())
                )
            ))
        }

        if (principal.personalReputation < 20) {
            events.add(GameEvent.NegativeEvent(
                title = "信任危机",
                message = "校内开始流传关于校长能力的质疑，部分教师私下议论。",
                penaltyReputation = 300
            ))
        }

        // 理想值相关
        if (principal.idealismLevel > 80 && school.reputation > 3000) {
            events.add(GameEvent.PositiveEvent(
                title = "教育理想被认可",
                message = "你的教育理念文章被教育杂志刊登，引起业界关注。",
                bonusReputation = 600
            ))
        }

        if (principal.idealismLevel < 20 && school.campusLevel >= 4) {
            events.add(GameEvent.ChoiceEvent(
                title = "教育初心拷问",
                message = "一位老教师退休前语重心长地问你：'校长，咱们办学的初心还在吗？'",
                choices = listOf(
                    EventChoice("深感触动，重拾教育理想（教师忠诚+15）",
                        EventConsequence(teacherLoyaltyChange = 15)),
                    EventChoice("感谢关心，继续做自己的事", EventConsequence())
                )
            ))
        }

        // 个人资金相关
        if (principal.personalFunds > 50.0) {
            events.add(GameEvent.ChoiceEvent(
                title = "个人投资机会",
                message = "老同学推荐了一个商铺投资机会，看起来回报不错。",
                choices = listOf(
                    EventChoice("投入20万（有50%概率获得5万收益）",
                        EventConsequence(cashChange = 0.0)),  // 实际效果在engine中处理
                    EventChoice("不参与", EventConsequence())
                )
            ))
        }

        if (principal.personalFunds > 100.0 && school.reputation > 3000) {
            events.add(GameEvent.ChoiceEvent(
                title = "匿名捐赠",
                message = "你个人资金充裕，是否匿名向学校捐一笔钱改善条件？",
                choices = listOf(
                    EventChoice("匿名捐赠10万（学校资金+10万）",
                        EventConsequence(cashChange = 10.0)),
                    EventChoice("不捐", EventConsequence())
                )
            ))
        }

        // 停职恢复后
        if (!principal.isSuspended && principal.timesInvestigated > 0 && principal.corruptionLevel < 30) {
            events.add(GameEvent.PositiveEvent(
                title = "重新获得信任",
                message = "经过一段时间清廉自律，校董事会对你重新建立了信任。",
                bonusReputation = 400
            ))
        }

        // 被调查多次后的心理压力
        if (principal.timesInvestigated >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "心理压力",
                message = "多次被调查让你倍感压力，失眠越来越严重。是否休个短假调整？",
                choices = listOf(
                    EventChoice("休假三天调整（什么都不变，清醒一下）", EventConsequence()),
                    EventChoice("咬牙坚持", EventConsequence(teacherLoyaltyChange = -3))
                )
            ))
        }

        // 校长家庭事件
        if (principal.personalFunds > 10.0 && school.campusLevel >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "家人住院",
                message = "你的家人突发疾病需要住院，医疗费用不菲。",
                choices = listOf(
                    EventChoice("用个人积蓄支付（个人资金-8万）",
                        EventConsequence()),
                    EventChoice("先借钱应急", EventConsequence())
                )
            ))
        }

        return events
    }

    // ========== 9. 腐败相关事件 ==========

    private fun getCorruptionRelatedEvents(principal: Principal, school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        // 腐败等级高时的事件
        if (principal.corruptionLevel > 50) {
            events.add(GameEvent.ChoiceEvent(
                title = "匿名举报信",
                message = "办公室收到一封匿名举报信，措辞含糊但暗示有人知道一些事。",
                choices = listOf(
                    EventChoice("加强保密意识，低调一段时间", EventConsequence()),
                    EventChoice("查出是谁写的", EventConsequence(teacherLoyaltyChange = -10))
                )
            ))
        }

        if (principal.corruptionLevel > 70) {
            events.add(GameEvent.NegativeEvent(
                title = "审计风声",
                message = "听闻董事会近期要对学校财务进行专项审计，你的心跳加快了。",
                penaltyReputation = 100
            ))
        }

        if (principal.corruptionLevel > 30 && principal.corruptionLevel <= 50) {
            events.add(GameEvent.ChoiceEvent(
                title = "知情人暗示",
                message = "一位了解内情的行政人员找你私聊：'校长，最近外面风声紧，你注意点。'",
                choices = listOf(
                    EventChoice("感谢提醒，收敛行为", EventConsequence()),
                    EventChoice("不以为然", EventConsequence())
                )
            ))
        }

        // 有过贪腐行为但很久没操作了
        if (principal.totalEmbezzled > 0 && principal.recentCorruptActs.isEmpty()) {
            events.add(GameEvent.PositiveEvent(
                title = "旧事淡忘",
                message = "很久没有新的违规行为，之前的事情似乎已经被人渐渐淡忘了。",
                bonusReputation = 100
            ))
        }

        // 贪腐金额很大
        if (principal.totalEmbezzled > 100.0) {
            events.add(GameEvent.ChoiceEvent(
                title = "资产转移念头",
                message = "个人资产已经很可观，要不要把钱转移到更安全的地方？",
                choices = listOf(
                    EventChoice("投资海外房产（资金固化但更安全）", EventConsequence()),
                    EventChoice("保持原样", EventConsequence())
                )
            ))
        }

        // 被停职后复职
        if (principal.timesCaughtMajor > 0 && !principal.isSuspended) {
            events.add(GameEvent.ChoiceEvent(
                title = "洗心革面？",
                message = "经历了停职处分后，你站在镜子前反思：以后还要继续走灰色地带吗？",
                choices = listOf(
                    EventChoice("痛定思痛，从此清廉（理想值+20）", EventConsequence()),
                    EventChoice("吃一堑长一智，以后更小心", EventConsequence())
                )
            ))
        }

        // 有其他校长同行被查
        if (principal.corruptionLevel > 20 && school.campusLevel >= 3) {
            events.add(GameEvent.NegativeEvent(
                title = "同行落马",
                message = "本市另一所学校的校长因经济问题被查，教育界震动，你心里有些不安。",
                penaltyReputation = 50
            ))
        }

        // 腐败带来的"好处"被发现
        if (principal.recentCorruptActs.size >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "会计疑问",
                message = "新来的会计翻看旧账时提出了一些疑问，问你几笔支出的去向。",
                choices = listOf(
                    EventChoice("编造理由搪塞", EventConsequence()),
                    EventChoice("安排老会计'带教'新会计", EventConsequence())
                )
            ))
        }

        // 商人递交信封
        if (school.campusLevel >= 4 && principal.corruptionLevel < 50) {
            events.add(GameEvent.ChoiceEvent(
                title = "工程商送礼",
                message = "学校装修工程的承包商临走时放下一个厚厚的信封在你办公桌上。",
                choices = listOf(
                    EventChoice("收下（个人资金+5万，腐败+1）",
                        EventConsequence()),
                    EventChoice("退回去，明确拒绝", EventConsequence(reputationChange = 100))
                )
            ))
        }

        return events
    }

    // ========== 10. 人脉事件 ==========

    private fun getConnectionEvents(principal: Principal, school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        // 人脉少时
        if (principal.connections.size < 2 && school.campusLevel >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "校长联谊会",
                message = "收到本区校长联谊会的邀请函，是个拓展人脉的好机会。",
                choices = listOf(
                    EventChoice("出席联谊会（花费个人资金1万）",
                        EventConsequence()),
                    EventChoice("太忙了不去", EventConsequence())
                )
            ))
        }

        // 人脉多时
        if (principal.connections.size >= 5) {
            events.add(GameEvent.ChoiceEvent(
                title = "人脉维护负担",
                message = "关系网越来越大，每月的应酬开销不小，是否精简社交圈？",
                choices = listOf(
                    EventChoice("维持现有社交圈（每月额外花费2万）",
                        EventConsequence()),
                    EventChoice("减少不必要的应酬", EventConsequence())
                )
            ))
        }

        // 有行业人脉时
        val hasOfficialConnection = principal.connections.any {
            it.type == com.arktools.xiao.domain.model.ConnectionType.EDUCATION_OFFICIAL && it.relationLevel > 50
        }
        if (hasOfficialConnection && school.campusLevel >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "行业消息通气",
                message = "教育圈的朋友私下透露：下个月可能有新的行业标准出台，影响民办学校的运营资质。",
                choices = listOf(
                    EventChoice("提前做好应对准备（声誉+200）",
                        EventConsequence(reputationChange = 200)),
                    EventChoice("等正式消息出来再说", EventConsequence())
                )
            ))
        }

        // 有媒体人脉时
        val hasMediaConnection = principal.connections.any {
            it.type == com.arktools.xiao.domain.model.ConnectionType.MEDIA_REPORTER && it.relationLevel > 30
        }
        if (hasMediaConnection && school.reputation > 2000) {
            events.add(GameEvent.ChoiceEvent(
                title = "软文合作",
                message = "认识的记者朋友提出可以写一篇学校的正面报道，但暗示需要'润笔费'。",
                choices = listOf(
                    EventChoice("给润笔费3万（声誉+800）",
                        EventConsequence(cashChange = -3.0, reputationChange = 800)),
                    EventChoice("算了，清清白白", EventConsequence())
                )
            ))
        }

        // 有公安人脉时
        val hasPoliceConnection = principal.connections.any {
            it.type == com.arktools.xiao.domain.model.ConnectionType.POLICE_CONTACT
        }
        if (hasPoliceConnection && school.campusLevel >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "校园安全协助",
                message = "学校附近最近治安不太好，是否请公安朋友帮忙加强巡逻？",
                choices = listOf(
                    EventChoice("请求协助（声誉+200，消耗人情）",
                        EventConsequence(reputationChange = 200)),
                    EventChoice("自己加强保安", EventConsequence())
                )
            ))
        }

        // 人脉等级高时的机遇
        if (principal.connectionLevel > 60) {
            events.add(GameEvent.ChoiceEvent(
                title = "饭局机遇",
                message = "朋友的朋友在饭局上提到有一个教育项目的招标信息，可以提前准备。",
                choices = listOf(
                    EventChoice("了解详情，积极准备（声誉+400）",
                        EventConsequence(reputationChange = 400)),
                    EventChoice("不感兴趣", EventConsequence())
                )
            ))
        }

        // 关系户子女问题
        if (principal.connectionLevel > 40 && school.reputation > 1000) {
            events.add(GameEvent.ChoiceEvent(
                title = "关系户插班",
                message = "一个关系户打电话来，想让他成绩很差的侄子插班进来。",
                choices = listOf(
                    EventChoice("安排插班（人脉+10，声誉-100）",
                        EventConsequence(reputationChange = -100)),
                    EventChoice("委婉拒绝", EventConsequence())
                )
            ))
        }

        return events
    }

    // ========== 11. 派系事件 ==========

    private fun getFactionEvents(principal: Principal, school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        val teachRel = principal.factionRelations[FactionType.TEACHING] ?: 50
        val adminRel = principal.factionRelations[FactionType.ADMINISTRATIVE] ?: 50
        val reformRel = principal.factionRelations[FactionType.REFORM] ?: 50
        val conservRel = principal.factionRelations[FactionType.CONSERVATIVE] ?: 50

        // 教学派强势时
        if (teachRel > 70 && adminRel < 40) {
            events.add(GameEvent.ChoiceEvent(
                title = "教学派推举年度教师",
                message = "教学派强烈推举他们的核心成员为年度优秀教师，行政派认为不公。",
                choices = listOf(
                    EventChoice("按教学派意见办（教师忠诚+10）",
                        EventConsequence(teacherLoyaltyChange = 10)),
                    EventChoice("设立公开评选制度（声誉+300）",
                        EventConsequence(reputationChange = 300)),
                    EventChoice("延期评选", EventConsequence())
                )
            ))
        }

        // 行政派强势时
        if (adminRel > 70 && teachRel < 40) {
            events.add(GameEvent.ChoiceEvent(
                title = "行政扩编报告",
                message = "行政派提交了增设副校长和多个行政岗位的报告，教学派认为肥了行政饿了教学。",
                choices = listOf(
                    EventChoice("批准增设（花费5万/年）",
                        EventConsequence(cashChange = -5.0)),
                    EventChoice("驳回报告（忠诚+5）",
                        EventConsequence(teacherLoyaltyChange = 5)),
                    EventChoice("缩减规模后批准", EventConsequence())
                )
            ))
        }

        // 改革派强势时
        if (reformRel > 70 && conservRel < 40) {
            events.add(GameEvent.ChoiceEvent(
                title = "激进课改方案",
                message = "改革派提出彻底废除传统考试，改用项目制评估。保守派坚决反对。",
                choices = listOf(
                    EventChoice("试点推行（声誉+500，有风险）",
                        EventConsequence(reputationChange = 500)),
                    EventChoice("暂缓讨论", EventConsequence()),
                    EventChoice("折中方案：部分科目试行",
                        EventConsequence(reputationChange = 200))
                )
            ))
        }

        // 保守派强势时
        if (conservRel > 70 && reformRel < 40) {
            events.add(GameEvent.ChoiceEvent(
                title = "元老集体发难",
                message = "保守派元老联名反对最近一系列改革措施，要求恢复'传统教学模式'。",
                choices = listOf(
                    EventChoice("接受元老意见", EventConsequence(teacherLoyaltyChange = 5)),
                    EventChoice("坚持改革方向（声誉+300，忠诚-10）",
                        EventConsequence(reputationChange = 300, teacherLoyaltyChange = -10)),
                    EventChoice("召开全体教师大会投票",
                        EventConsequence(reputationChange = 200))
                )
            ))
        }

        // 全派系低迷
        if (teachRel < 30 && adminRel < 30 && reformRel < 30 && conservRel < 30) {
            events.add(GameEvent.NegativeEvent(
                title = "人心涣散",
                message = "校内各派系都对校长失去信心，工作氛围极差，有人开始另谋出路。",
                penaltyReputation = 500
            ))
        }

        // 全派系高满意
        if (teachRel > 60 && adminRel > 60 && reformRel > 60 && conservRel > 60) {
            events.add(GameEvent.PositiveEvent(
                title = "上下一心",
                message = "各方力量难得地达成一致，学校凝聚力空前，效率大幅提升！",
                bonusReputation = 800
            ))
        }

        // 派系间的日常摩擦
        if (school.maxTeachers >= 10 && school.campusLevel >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "会议室之争",
                message = "教学组和行政组为了会议室使用权发生争执，双方都不让步。",
                choices = listOf(
                    EventChoice("优先保障教学使用", EventConsequence(teacherLoyaltyChange = 5)),
                    EventChoice("制定轮换制度（声誉+100）",
                        EventConsequence(reputationChange = 100)),
                    EventChoice("扩建新会议室（花费3万）",
                        EventConsequence(cashChange = -3.0))
                )
            ))
        }

        // 派系联合对抗校长
        if (teachRel < 25 && adminRel < 25) {
            events.add(GameEvent.NegativeEvent(
                title = "联合施压",
                message = "教学派和行政派罕见地联合起来，向校董事会递交了对校长管理的'建议书'。",
                penaltyReputation = 600
            ))
        }

        return events
    }

    // ========== 14. 学生事件（日常趣味）==========

    private fun getStudentEvents(school: School, studentCount: Int = 0): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        // 只要有学生就可以触发
        if (studentCount > 0) {
            // 趣味日常事件（无门槛，新学校也能触发）
            events.add(GameEvent.PositiveEvent(
                title = "学生才艺展示",
                message = "一位学生在课间展示了惊人的才艺，引来全班鼓掌！班级氛围更加融洽。",
                bonusCash = 0.0,
                bonusReputation = 2
            ))
            events.add(GameEvent.PositiveEvent(
                title = "学生好人好事",
                message = "有学生主动帮助跌倒的同学去医务室，这种互帮互助的校风令人欣慰。",
                bonusCash = 0.0,
                bonusReputation = 3
            ))
            events.add(GameEvent.NegativeEvent(
                title = "学生恶作剧",
                message = "有学生在走廊里放鞭炮，虽然没有人受伤，但被邻居投诉了。",
                penaltyCash = 0.0,
                penaltyReputation = 2
            ))
            events.add(GameEvent.PositiveEvent(
                title = "学习互助小组",
                message = "学生们自发组建了学习互助小组，帮助落后同学补课，年级整体成绩有所提升。",
                bonusCash = 0.0,
                bonusReputation = 3
            ))
            events.add(GameEvent.NegativeEvent(
                title = "学生丢失物品",
                message = "近期多位学生反映丢失文具和零花钱，学业导师正在调查中。",
                penaltyCash = 0.0,
                penaltyReputation = 2
            ))
            events.add(GameEvent.PositiveEvent(
                title = "黑板报获奖",
                message = "学校黑板报在区级评比中获得优秀奖，展示了学生们的创意和团队合作能力！",
                bonusCash = 0.0,
                bonusReputation = 4
            ))
        }

        // 学生多了才可能触发的事件
        if (studentCount >= 30) {
            events.add(GameEvent.ChoiceEvent(
                title = "学生请愿",
                message = "一群学生联名请愿希望学校延长图书馆开放时间，并增设自习室。你如何回应？",
                choices = listOf(
                    EventChoice("同意延长开放时间", EventConsequence(reputationChange = 5)),
                    EventChoice("暂时搁置，下学期再议", EventConsequence(reputationChange = -2))
                )
            ))
            events.add(GameEvent.PositiveEvent(
                title = "运动会佳绩",
                message = "学校田径队在区级运动会上斩获多枚奖牌，为校争光！",
                bonusCash = 0.0,
                bonusReputation = 6
            ))
            events.add(GameEvent.NegativeEvent(
                title = "课间打闹受伤",
                message = "两名学生课间追逐打闹时一人摔倒受伤，家长到校交涉。已妥善处理。",
                penaltyCash = 0.5,
                penaltyReputation = 3
            ))
            events.add(GameEvent.PositiveEvent(
                title = "学生发明创造",
                message = "一名学生的小发明在青少年科技创新大赛中获奖，学校名声传播！",
                bonusCash = 0.0,
                bonusReputation = 8
            ))
        }

        // 大型学校特有事件
        if (studentCount >= 100) {
            events.add(GameEvent.ChoiceEvent(
                title = "学生会竞选",
                message = "学生会换届选举即将举行，两位候选人竞争激烈。是否由校方介入引导？",
                choices = listOf(
                    EventChoice("让学生自主选举", EventConsequence(reputationChange = 5)),
                    EventChoice("校方指导推荐人选", EventConsequence(reputationChange = -3))
                )
            ))
            events.add(GameEvent.PositiveEvent(
                title = "校园文化节",
                message = "学生自发组织的文化节吸引了众多家长和社区居民参观，学校知名度大增！",
                bonusCash = 0.0,
                bonusReputation = 10
            ))
        }

        return events
    }

    // ========== 12. 设施事件 ==========

    private fun getFacilityEvents(school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        if (school.facilities.size >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "设施维护",
                message = "多项设施需要年度保养，是否安排集中维护？",
                choices = listOf(
                    EventChoice("集中维护（花费${school.facilities.size}万）",
                        EventConsequence(
                            cashChange = -school.facilities.size.toDouble(),
                            reputationChange = 200
                        )),
                    EventChoice("暂时延后（设施可能加速老化）",
                        EventConsequence(reputationChange = -50))
                )
            ))
        }

        // 设施老化
        if (school.facilities.any { it.condition < 30 }) {
            events.add(GameEvent.NegativeEvent(
                title = "设施故障",
                message = "老旧设施在使用中出现故障，幸好没有造成人员受伤，但需要紧急维修。",
                penaltyCash = 3.0, penaltyReputation = 150
            ))
        }

        // 设施全新时
        if (school.facilities.isNotEmpty() && school.facilities.all { it.condition > 80 }) {
            events.add(GameEvent.PositiveEvent(
                title = "设施评级优秀",
                message = "行业协会设施安全检查，学校所有设施评级优秀！",
                bonusReputation = 300
            ))
        }

        // 特定设施触发
        if (school.facilities.any { it.type == com.arktools.xiao.domain.model.FacilityType.LIBRARY }) {
            events.add(GameEvent.ChoiceEvent(
                title = "读书月活动",
                message = "图书馆管理员提议举办'校园读书月'活动，需要采购新书。",
                choices = listOf(
                    EventChoice("举办活动（花费2万，声誉+300）",
                        EventConsequence(cashChange = -2.0, reputationChange = 300)),
                    EventChoice("下次再说", EventConsequence())
                )
            ))
        }

        if (school.facilities.any { it.type == com.arktools.xiao.domain.model.FacilityType.COMPUTER_LAB }) {
            events.add(GameEvent.ChoiceEvent(
                title = "网络安全事件",
                message = "有学生在机房电脑上访问了不当网站，家长投诉学校管理疏忽。",
                choices = listOf(
                    EventChoice("加装过滤软件（花费1万，声誉+100）",
                        EventConsequence(cashChange = -1.0, reputationChange = 100)),
                    EventChoice("加强巡查", EventConsequence())
                )
            ))
        }

        if (school.facilities.any { it.type == com.arktools.xiao.domain.model.FacilityType.CANTEEN }) {
            events.add(GameEvent.ChoiceEvent(
                title = "食堂卫生抽查",
                message = "食品安全监督部门突击抽查学校食堂。",
                choices = listOf(
                    EventChoice("食堂一直管理规范（声誉+200）",
                        EventConsequence(reputationChange = 200)),
                    EventChoice("紧急整改迎检",
                        EventConsequence(cashChange = -1.0, reputationChange = 50))
                )
            ))
        }

        return events
    }

    // ========== 13. 社会事件 ==========

    private fun getSocialEvents(school: School): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        // 校外人员闯入
        if (school.campusLevel >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "校外人员闯入",
                message = "有陌生人试图翻墙进入校园，被保安拦住。家长群炸开了锅。",
                choices = listOf(
                    EventChoice("加强安保措施（花费3万，声誉+300）",
                        EventConsequence(cashChange = -3.0, reputationChange = 300)),
                    EventChoice("发公告安抚家长（声誉+100）",
                        EventConsequence(reputationChange = 100))
                )
            ))
        }

        // 校园欺凌
        if (school.campusLevel >= 3 && school.maxTeachers >= 8) {
            events.add(GameEvent.ChoiceEvent(
                title = "校园欺凌事件",
                message = "一位家长反映其孩子在学校遭到其他同学欺凌，要求学校处理。",
                choices = listOf(
                    EventChoice("严肃调查处理（花费1万，声誉+400）",
                        EventConsequence(cashChange = -1.0, reputationChange = 400)),
                    EventChoice("约谈双方家长和解",
                        EventConsequence(reputationChange = 100)),
                    EventChoice("淡化处理（声誉-200）",
                        EventConsequence(reputationChange = -200))
                )
            ))
        }

        // 社区投诉噪音
        if (school.facilities.any { it.type == com.arktools.xiao.domain.model.FacilityType.SPORTS_FIELD }) {
            events.add(GameEvent.ChoiceEvent(
                title = "居民投诉噪音",
                message = "周边居民投诉学校体育场活动噪音太大，物业来协调。",
                choices = listOf(
                    EventChoice("限制活动时间（声誉+100）",
                        EventConsequence(reputationChange = 100)),
                    EventChoice("加装隔音设施（花费5万）",
                        EventConsequence(cashChange = -5.0, reputationChange = 200)),
                    EventChoice("解释合规使用", EventConsequence(reputationChange = -50))
                )
            ))
        }

        // 自然灾害
        if (school.campusLevel >= 2) {
            events.add(GameEvent.ChoiceEvent(
                title = "暴雨积水",
                message = "连续暴雨导致校园低洼处严重积水，部分教室被淹。",
                choices = listOf(
                    EventChoice("紧急排水抢修（花费5万）",
                        EventConsequence(cashChange = -5.0, reputationChange = 100)),
                    EventChoice("临时停课等水退", EventConsequence(reputationChange = -100))
                )
            ))
        }

        // 学生获奖
        if (school.reputation > 2000 && school.totalCoursesReleased >= 4) {
            events.add(GameEvent.PositiveEvent(
                title = "学生竞赛获奖",
                message = "学生代表队在全市学科竞赛中获得团体一等奖！",
                bonusReputation = 700
            ))
        }

        // 学生意外
        if (school.campusLevel >= 3) {
            events.add(GameEvent.ChoiceEvent(
                title = "学生课间受伤",
                message = "一名学生课间追跑时摔倒骨折，家长要求学校承担全部医疗费。",
                choices = listOf(
                    EventChoice("主动承担费用（花费3万，声誉+200）",
                        EventConsequence(cashChange = -3.0, reputationChange = 200)),
                    EventChoice("走保险流程（声誉-100）",
                        EventConsequence(reputationChange = -100)),
                    EventChoice("协商各承担一半",
                        EventConsequence(cashChange = -1.5, reputationChange = 50))
                )
            ))
        }

        // 正面社会事件
        if (school.reputation > 4000 && school.campusLevel >= 5) {
            events.add(GameEvent.PositiveEvent(
                title = "爱心故事传播",
                message = "学校师生帮助一位困难学生的故事被传开，引发社会正能量传播！",
                bonusReputation = 1000
            ))
        }

        // 周边竞争
        if (school.campusLevel >= 4 && school.reputation > 2000) {
            events.add(GameEvent.NegativeEvent(
                title = "新校入驻竞争",
                message = "一所实力雄厚的新学校在附近开业，广告铺天盖地，部分家长被吸引。",
                penaltyReputation = 400
            ))
        }

        // 拆迁威胁
        if (school.campusLevel >= 3 && school.currentYear >= 2005) {
            events.add(GameEvent.ChoiceEvent(
                title = "规划调整",
                message = "市政规划调整，学校所在地块被划入商业开发区域，开发商来谈补偿。",
                choices = listOf(
                    EventChoice("接受高额补偿异地重建（资金+100万，声誉-500）",
                        EventConsequence(cashChange = 100.0, reputationChange = -500)),
                    EventChoice("坚决不搬，据理力争（声誉+300）",
                        EventConsequence(reputationChange = 300))
                )
            ))
        }

        // 公益合作
        if (school.campusLevel >= 3 && school.reputation > 1500) {
            events.add(GameEvent.ChoiceEvent(
                title = "企业冠名合作",
                message = "一家知名企业提出冠名赞助学校运动会，提供10万赞助费。",
                choices = listOf(
                    EventChoice("接受冠名赞助（资金+10万）",
                        EventConsequence(cashChange = 10.0)),
                    EventChoice("谢绝商业化（声誉+200）",
                        EventConsequence(reputationChange = 200))
                )
            ))
        }

        // 毕业生回馈
        if (school.currentYear - school.foundedYear >= 6 && school.reputation > 2000) {
            events.add(GameEvent.PositiveEvent(
                title = "优秀毕业生",
                message = "一位早年毕业的学生考上了顶尖大学，在采访中感谢母校培养！",
                bonusReputation = 600
            ))
        }

        // 教育基金评选
        if (school.campusLevel >= 4) {
            events.add(GameEvent.ChoiceEvent(
                title = "教育基金评选",
                message = "一家民办教育扶持基金会正在评选优秀学校，你的学校符合申报条件。",
                choices = listOf(
                    EventChoice("积极申报（花费1万准备材料，有望获得15万资助）",
                        EventConsequence(cashChange = -1.0, reputationChange = 100)),
                    EventChoice("嫌麻烦不申报", EventConsequence())
                )
            ))
        }

        return events
    }

    // ========== 里程碑事件 ==========

    fun generateMilestoneEvent(type: MilestoneType): GameEvent.MilestoneEvent {
        return when (type) {
            MilestoneType.FIRST_COURSE -> GameEvent.MilestoneEvent(
                title = "里程碑：首课开课",
                message = "恭喜！您的第一门课程正式开课！",
                milestoneType = type
            )
            MilestoneType.FIRST_PROFIT -> GameEvent.MilestoneEvent(
                title = "里程碑：首次盈利",
                message = "学校实现首次月度盈利，继续保持！",
                milestoneType = type
            )
            MilestoneType.CAMPUS_UPGRADE -> GameEvent.MilestoneEvent(
                title = "里程碑：校舍升级",
                message = "校舍升级完成，可以容纳更多师生！",
                milestoneType = type
            )
            MilestoneType.TEACHER_HIRED -> GameEvent.MilestoneEvent(
                title = "里程碑：名师加盟",
                message = "成功招聘到一位优秀教师！",
                milestoneType = type
            )
            MilestoneType.RESEARCH_UNLOCKED -> GameEvent.MilestoneEvent(
                title = "里程碑：教研突破",
                message = "解锁了新的教学方法，教学效率将大幅提升！",
                milestoneType = type
            )
            MilestoneType.BRANCH_SCHOOL -> GameEvent.MilestoneEvent(
                title = "里程碑：分校创立",
                message = "学校实力已足以开设分校，版图扩大！",
                milestoneType = type
            )
            MilestoneType.MARKET_CAP_MILESTONE -> GameEvent.MilestoneEvent(
                title = "里程碑：市值突破",
                message = "学校市值再创新高，投资者信心大增！",
                milestoneType = type
            )
        }
    }
}
