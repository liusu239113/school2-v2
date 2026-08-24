package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.model.StockEventType
import com.arktools.xiaozhang.domain.model.StockMarketEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 股票市场事件生成器
 * 根据游戏进度、时代背景、学校状态生成影响股价的市场事件
 */
@Singleton
class StockEventGenerator @Inject constructor() {

    companion object {
        // 每日生成事件的概率（降低频率，配合冷却约30-60天一次）
        private const val EVENT_PROBABILITY = 0.05f
        // 最大同时活跃事件数
        private const val MAX_ACTIVE_EVENTS = 3
        // 最小事件间隔（天）- 防止高速模式下事件轰炸
        private const val MIN_EVENT_INTERVAL_DAYS = 20
    }

    private var lastEventDay: Int = -100

    /**
     * 尝试生成一个股票市场事件
     * @return 生成的事件，或 null（概率未触发时）
     */
    fun tryGenerateEvent(school: School, activeEventCount: Int): StockMarketEvent? {
        if (activeEventCount >= MAX_ACTIVE_EVENTS) return null
        val currentDay = school.currentYear * 360 + school.currentMonth * 30 + school.currentDay
        if (currentDay - lastEventDay < MIN_EVENT_INTERVAL_DAYS) return null
        if (Random.nextFloat() > EVENT_PROBABILITY) return null

        val eventPool = buildList {
            addAll(getPolicyEvents(school))
            addAll(getMarketCycleEvents(school))
            addAll(getSectorEvents(school))
            addAll(getCompanyEvents(school))
            addAll(getEraSpecificEvents(school))
        }

        if (eventPool.isEmpty()) return null
        val event = eventPool.random()
        lastEventDay = currentDay
        return event
    }

    /**
     * 政策类事件 - 影响整个教育板块
     */
    private fun getPolicyEvents(school: School): List<StockMarketEvent> {
        val year = school.currentYear
        val events = mutableListOf<StockMarketEvent>()

        // 通用政策事件
        events.add(StockMarketEvent(
            type = StockEventType.POLICY_POSITIVE,
            title = "教育经费增加",
            message = "行业基金会宣布增加教育投资，教育股全面上涨！",
            priceImpactPercent = Random.nextDouble(3.0, 8.0),
            durationDays = Random.nextInt(3, 7)
        ))
        events.add(StockMarketEvent(
            type = StockEventType.POLICY_NEGATIVE,
            title = "监管收紧",
            message = "行业协会加强培训机构审查，市场情绪低迷。",
            priceImpactPercent = -Random.nextDouble(3.0, 10.0),
            durationDays = Random.nextInt(3, 8)
        ))

        // 办学时间相关行业事件
        val schoolAge = school.currentYear - school.foundedYear
        if (schoolAge >= 20) {
            events.add(StockMarketEvent(
                type = StockEventType.POLICY_NEGATIVE,
                title = "行业整顿风暴",
                message = "行业协会出台新规，学科类培训公司股价暴跌！",
                priceImpactPercent = -Random.nextDouble(10.0, 20.0),
                durationDays = Random.nextInt(5, 14)
            ))
            events.add(StockMarketEvent(
                type = StockEventType.POLICY_POSITIVE,
                title = "素质教育热潮",
                message = "家长群体追捧素质教育，非学科类公司集体上涨。",
                affectedSector = "在线教育",
                priceImpactPercent = Random.nextDouble(5.0, 12.0),
                durationDays = Random.nextInt(5, 10)
            ))
        }

        if (schoolAge in 10..19) {
            events.add(StockMarketEvent(
                type = StockEventType.POLICY_POSITIVE,
                title = "教育信息化浪潮",
                message = "在线教育模式获得市场认可，相关概念大涨！",
                affectedSector = "在线教育",
                priceImpactPercent = Random.nextDouble(5.0, 10.0),
                durationDays = Random.nextInt(4, 8)
            ))
        }

        if (schoolAge < 10) {
            events.add(StockMarketEvent(
                type = StockEventType.POLICY_POSITIVE,
                title = "扩招利好",
                message = "多所高校宣布扩招计划，教育行业前景看好。",
                priceImpactPercent = Random.nextDouble(3.0, 7.0),
                durationDays = Random.nextInt(3, 6)
            ))
        }

        return events
    }

    /**
     * 市场周期事件 - 影响全部股票
     */
    private fun getMarketCycleEvents(school: School): List<StockMarketEvent> {
        return listOf(
            StockMarketEvent(
                type = StockEventType.MARKET_BOOM,
                title = "牛市行情",
                message = "A股大盘全面上涨，教育股跟随上攻！",
                priceImpactPercent = Random.nextDouble(5.0, 12.0),
                durationDays = Random.nextInt(5, 14)
            ),
            StockMarketEvent(
                type = StockEventType.MARKET_CRASH,
                title = "市场恐慌",
                message = "全球股市暴跌，投资者恐慌抛售教育股。",
                priceImpactPercent = -Random.nextDouble(8.0, 18.0),
                durationDays = Random.nextInt(3, 10)
            ),
            StockMarketEvent(
                type = StockEventType.MARKET_BOOM,
                title = "资金面宽松",
                message = "央行降息降准，热钱涌入股市推高估值。",
                priceImpactPercent = Random.nextDouble(3.0, 8.0),
                durationDays = Random.nextInt(4, 10)
            ),
            StockMarketEvent(
                type = StockEventType.MARKET_CRASH,
                title = "流动性收紧",
                message = "监管收紧融资渠道，市场资金面紧张。",
                priceImpactPercent = -Random.nextDouble(3.0, 8.0),
                durationDays = Random.nextInt(3, 7)
            )
        )
    }

    /**
     * 板块事件 - 影响特定板块
     */
    private fun getSectorEvents(school: School): List<StockMarketEvent> {
        return listOf(
            StockMarketEvent(
                type = StockEventType.SECTOR_BOOM,
                title = "在线教育热潮",
                message = "知名投行发布看好在线教育研报，板块集体上涨！",
                affectedSector = "在线教育",
                priceImpactPercent = Random.nextDouble(5.0, 15.0),
                durationDays = Random.nextInt(3, 8)
            ),
            StockMarketEvent(
                type = StockEventType.SECTOR_BUST,
                title = "在线教育退潮",
                message = "多家在线教育公司业绩不及预期，板块承压下跌。",
                affectedSector = "在线教育",
                priceImpactPercent = -Random.nextDouble(5.0, 12.0),
                durationDays = Random.nextInt(3, 7)
            ),
            StockMarketEvent(
                type = StockEventType.SECTOR_BOOM,
                title = "教育科技创新",
                message = "AI+教育概念火爆，教育科技板块大涨！",
                affectedSector = "教育科技",
                priceImpactPercent = Random.nextDouble(6.0, 14.0),
                durationDays = Random.nextInt(4, 9)
            ),
            StockMarketEvent(
                type = StockEventType.SECTOR_BUST,
                title = "教育科技泡沫",
                message = "教育科技估值泡沫破裂，多只龙头股跌停。",
                affectedSector = "教育科技",
                priceImpactPercent = -Random.nextDouble(8.0, 15.0),
                durationDays = Random.nextInt(3, 7)
            )
        )
    }

    /**
     * 个股事件 - 随机影响某一只股票
     */
    private fun getCompanyEvents(school: School): List<StockMarketEvent> {
        return listOf(
            StockMarketEvent(
                type = StockEventType.COMPANY_BREAKTHROUGH,
                title = "业绩超预期",
                message = "某教育公司季报营收大增80%，股价涨停！",
                affectedStockId = "__RANDOM__",  // 运行时随机选择
                priceImpactPercent = Random.nextDouble(8.0, 20.0),
                durationDays = Random.nextInt(2, 5)
            ),
            StockMarketEvent(
                type = StockEventType.COMPANY_SCANDAL,
                title = "财务造假",
                message = "某教育公司被曝财务数据造假，股价暴跌！",
                affectedStockId = "__RANDOM__",
                priceImpactPercent = -Random.nextDouble(10.0, 25.0),
                durationDays = Random.nextInt(3, 7)
            ),
            StockMarketEvent(
                type = StockEventType.COMPANY_BREAKTHROUGH,
                title = "战略合作",
                message = "某教育公司与科技巨头达成战略合作，前景看好！",
                affectedStockId = "__RANDOM__",
                priceImpactPercent = Random.nextDouble(5.0, 12.0),
                durationDays = Random.nextInt(2, 5)
            ),
            StockMarketEvent(
                type = StockEventType.COMPANY_SCANDAL,
                title = "高管离职",
                message = "某教育公司核心高管突然辞职，投资者信心动摇。",
                affectedStockId = "__RANDOM__",
                priceImpactPercent = -Random.nextDouble(4.0, 10.0),
                durationDays = Random.nextInt(2, 4)
            )
        )
    }

    /**
     * 时代特定的重大事件（低概率高冲击）
     */
    private fun getEraSpecificEvents(school: School): List<StockMarketEvent> {
        val year = school.currentYear
        val events = mutableListOf<StockMarketEvent>()

        if (year in 2003..2004) {
            events.add(StockMarketEvent(
                type = StockEventType.MARKET_CRASH,
                title = "非典疫情冲击",
                message = "SARS疫情蔓延，线下教育股暴跌，在线教育逆势上涨。",
                affectedSector = "教育科技",
                priceImpactPercent = -Random.nextDouble(10.0, 20.0),
                durationDays = Random.nextInt(10, 20)
            ))
        }

        if (year in 2008..2009) {
            events.add(StockMarketEvent(
                type = StockEventType.MARKET_CRASH,
                title = "金融危机",
                message = "全球金融海啸波及A股，教育股未能幸免。",
                priceImpactPercent = -Random.nextDouble(15.0, 30.0),
                durationDays = Random.nextInt(14, 30)
            ))
        }

        if (year in 2015..2015) {
            events.add(StockMarketEvent(
                type = StockEventType.MARKET_CRASH,
                title = "股灾来袭",
                message = "杠杆牛市崩盘，千股跌停！教育股无一幸免。",
                priceImpactPercent = -Random.nextDouble(20.0, 35.0),
                durationDays = Random.nextInt(10, 20)
            ))
        }

        if (year >= 2020) {
            events.add(StockMarketEvent(
                type = StockEventType.SECTOR_BOOM,
                title = "居家学习需求爆发",
                message = "疫情期间在线教育需求激增，相关公司业绩暴涨！",
                affectedSector = "在线教育",
                priceImpactPercent = Random.nextDouble(10.0, 25.0),
                durationDays = Random.nextInt(7, 14)
            ))
        }

        return events
    }
}
