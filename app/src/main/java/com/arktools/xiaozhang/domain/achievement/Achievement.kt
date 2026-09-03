package com.arktools.xiaozhang.domain.achievement

import com.arktools.xiaozhang.domain.model.School

/**
 * Achievement definitions for the game.
 * Achievements are checked after each tick and unlock once their condition is met.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    var unlocked: Boolean = false,
    var unlockTime: Long = 0,
    val condition: (School) -> Boolean
)

enum class AchievementCategory(val displayName: String) {
    MILESTONE("里程碑"),
    FINANCIAL("财务"),
    ACADEMIC("学术"),
    GROWTH("成长"),
    TEACHER("师资"),
    FACILITY("设施"),
    CHALLENGE("挑战"),
    SECRET("隐藏")
}

object AchievementRegistry {

    fun getAllAchievements(): List<Achievement> = listOf(
        // 里程碑——当前版本采用学年和学校发展节点，不再展示已移除的“开设课程”成就
        // ═══════════════════════════════════════════
        Achievement("first_year", "开学典礼", "完成第一学年", AchievementCategory.MILESTONE) { it.currentYear >= it.foundedYear + 1 },
        Achievement("five_years", "五年坚守", "持续经营5年", AchievementCategory.MILESTONE) { (it.currentYear - it.foundedYear) >= 5 },
        Achievement("ten_years", "十年树木", "持续经营10年", AchievementCategory.MILESTONE) { (it.currentYear - it.foundedYear) >= 10 },
        Achievement("twenty_years", "二十年如一日", "持续经营20年", AchievementCategory.MILESTONE) { (it.currentYear - it.foundedYear) >= 20 },
        Achievement("thirty_years", "三十而立", "持续经营30年", AchievementCategory.MILESTONE) { (it.currentYear - it.foundedYear) >= 30 },
        Achievement("fifty_years", "半世纪传奇", "持续经营50年", AchievementCategory.MILESTONE) { (it.currentYear - it.foundedYear) >= 50 },
        Achievement("year_2030", "AI新纪元", "经营到2030年", AchievementCategory.MILESTONE) { it.currentYear >= 2030 },

        // ═══════════════════════════════════════════
        // 财务（14个）——经济成就（档位按开局500万重新标定，避免开局秒解）
        // ═══════════════════════════════════════════
        Achievement("cash_1000", "首个千万", "学校现金达到1000万", AchievementCategory.FINANCIAL) { it.cash >= 1000.0 },
        Achievement("cash_2000", "腰缠万贯", "学校现金达到2000万", AchievementCategory.FINANCIAL) { it.cash >= 2000.0 },
        Achievement("cash_10000", "富可敌国", "学校现金达到1亿", AchievementCategory.FINANCIAL) { it.cash >= 10000.0 },
        Achievement("revenue_100", "初见回报", "累计收入100万", AchievementCategory.FINANCIAL) { it.totalRevenue >= 100.0 },
        Achievement("revenue_500", "盈利可观", "累计收入500万", AchievementCategory.FINANCIAL) { it.totalRevenue >= 500.0 },
        Achievement("revenue_2000", "教育富豪", "累计收入2000万", AchievementCategory.FINANCIAL) { it.totalRevenue >= 2000.0 },
        Achievement("revenue_10000", "教育帝国", "累计收入1亿", AchievementCategory.FINANCIAL) { it.totalRevenue >= 10000.0 },
        Achievement("revenue_50000", "教育财阀", "累计收入5亿", AchievementCategory.FINANCIAL) { it.totalRevenue >= 50000.0 },
        Achievement("revenue_200000", "富甲一方", "累计收入20亿", AchievementCategory.FINANCIAL) { it.totalRevenue >= 200000.0 },
        Achievement("marketcap_2000", "市值新星", "学校市值达到2000万", AchievementCategory.FINANCIAL) { it.marketCap >= 2000.0 },
        Achievement("marketcap_5000", "市值飙升", "学校市值达到5000万", AchievementCategory.FINANCIAL) { it.marketCap >= 5000.0 },
        Achievement("marketcap_50000", "教育巨头", "学校市值达到5亿", AchievementCategory.FINANCIAL) { it.marketCap >= 50000.0 },
        Achievement("frugal_start", "精打细算", "创校第一年结束时现金不低于600万", AchievementCategory.FINANCIAL) { it.currentYear == it.foundedYear + 1 && it.cash > 600.0 },

        // ═══════════════════════════════════════════
        // 学术（12个）——声誉和评分
        // ═══════════════════════════════════════════
        Achievement("rep_100", "崭露头角", "声誉达到100", AchievementCategory.ACADEMIC) { it.reputation >= 100 },
        Achievement("rep_500", "小有名气", "声誉达到500", AchievementCategory.ACADEMIC) { it.reputation >= 500 },
        Achievement("rep_1000", "口碑相传", "声誉达到1000", AchievementCategory.ACADEMIC) { it.reputation >= 1000 },
        Achievement("rep_5000", "远近闻名", "声誉达到5000", AchievementCategory.ACADEMIC) { it.reputation >= 5000 },
        Achievement("rep_10000", "声名远播", "声誉达到10000", AchievementCategory.ACADEMIC) { it.reputation >= 10000 },
        Achievement("rep_50000", "金字招牌", "声誉达到50000", AchievementCategory.ACADEMIC) { it.reputation >= 50000 },
        Achievement("rep_100000", "教育圣殿", "声誉达到100000", AchievementCategory.ACADEMIC) { it.reputation >= 100000 },
        Achievement("star_2", "两星学校", "获得2星评分", AchievementCategory.ACADEMIC) { it.starRating >= 2.0f },
        Achievement("star_3", "三星学校", "获得3星评分", AchievementCategory.ACADEMIC) { it.starRating >= 3.0f },
        Achievement("star_4", "四星学校", "获得4星评分", AchievementCategory.ACADEMIC) { it.starRating >= 4.0f },
        Achievement("star_5", "五星学校", "获得5星评分", AchievementCategory.ACADEMIC) { it.starRating >= 5.0f },
        Achievement("textbook", "教材建设", "学校等级达到3级且拥有10个以上设施", AchievementCategory.ACADEMIC) { it.campusLevel >= 3 && it.facilities.size >= 10 },

        // ═══════════════════════════════════════════
        // 成长（12个）——学校等级和规模
        // ═══════════════════════════════════════════
        Achievement("level_2", "区级规范", "学校升到2级", AchievementCategory.GROWTH) { it.campusLevel >= 2 },
        Achievement("level_3", "市级重点", "学校升到3级", AchievementCategory.GROWTH) { it.campusLevel >= 3 },
        Achievement("level_4", "省级示范", "学校升到4级", AchievementCategory.GROWTH) { it.campusLevel >= 4 },
        Achievement("level_5", "国家名校", "学校升到5级", AchievementCategory.GROWTH) { it.campusLevel >= 5 },
        Achievement("level_6", "世界一流", "学校升到最高6级", AchievementCategory.GROWTH) { it.campusLevel >= 6 },
        Achievement("full_teachers", "师资班底", "校园达到2级并具备教师容量", AchievementCategory.GROWTH) { it.facilities.size > 0 && it.campusLevel >= 2 },
        Achievement("ten_facilities", "设施齐全", "拥有10个以上设施", AchievementCategory.GROWTH) { it.facilities.size >= 10 },
        Achievement("all_facility_types", "应有尽有", "拥有全部12种设施类型", AchievementCategory.GROWTH) { it.facilities.map { f -> f.type }.toSet().size >= 12 },

        // ═══════════════════════════════════════════
        // 师资（12个）——教师相关
        // ═══════════════════════════════════════════
        Achievement("teacher_5", "五人团队", "拥有5名教师", AchievementCategory.TEACHER) { it.maxTeachers >= 5 && it.campusLevel >= 2 },
        Achievement("teacher_10", "十人大所", "教师可容纳10人以上", AchievementCategory.TEACHER) { it.maxTeachers >= 10 },
        Achievement("teacher_25", "师资雄厚", "教师可容纳25人以上", AchievementCategory.TEACHER) { it.maxTeachers >= 25 },
        Achievement("teacher_50", "名师荟萃", "教师可容纳50人以上", AchievementCategory.TEACHER) { it.maxTeachers >= 50 },
        Achievement("teacher_100", "百人天团", "教师可容纳100人以上", AchievementCategory.TEACHER) { it.maxTeachers >= 100 },
        Achievement("teacher_200", "师资帝国", "教师可容纳200人", AchievementCategory.TEACHER) { it.maxTeachers >= 200 },
        Achievement("high_salary_era", "高薪时代", "学校等级达到4级（高薪教师市场）", AchievementCategory.TEACHER) { it.campusLevel >= 4 },
        Achievement("level2_teachers", "培养达人", "学校等级>=3（解锁高级培训）", AchievementCategory.TEACHER) { it.campusLevel >= 3 },
        Achievement("recruit_s", "伯乐识马", "学校等级>=5（可招募S级教师）", AchievementCategory.TEACHER) { it.campusLevel >= 5 },
        Achievement("veteran_school", "桃李满天", "经营超过15年", AchievementCategory.TEACHER) { (it.currentYear - it.foundedYear) >= 15 },
        Achievement("teacher_loyalty", "人心所向", "学校声誉>5000且等级>=3", AchievementCategory.TEACHER) { it.reputation >= 5000 && it.campusLevel >= 3 },
        Achievement("master_trainer", "名师摇篮", "学校等级达到4级且教师容量达到25人", AchievementCategory.TEACHER) { it.campusLevel >= 4 && it.maxTeachers >= 25 },

        // ═══════════════════════════════════════════
        // 设施（11个）——建设相关
        // ═══════════════════════════════════════════
        Achievement("first_facility", "破土动工", "建设第一个设施", AchievementCategory.FACILITY) { it.facilities.size >= 1 },
        Achievement("three_facilities", "基础完善", "拥有3个设施", AchievementCategory.FACILITY) { it.facilities.size >= 3 },
        Achievement("five_facilities", "校园升级", "拥有5个设施", AchievementCategory.FACILITY) { it.facilities.size >= 5 },
        Achievement("has_library", "书香校园", "建设图书馆", AchievementCategory.FACILITY) { it.facilities.any { f -> f.type.name == "LIBRARY" } },
        Achievement("has_lab", "科学殿堂", "建设实验室", AchievementCategory.FACILITY) { it.facilities.any { f -> f.type.name == "LABORATORY" } },
        Achievement("has_sports", "强身健体", "建设运动场", AchievementCategory.FACILITY) { it.facilities.any { f -> f.type.name == "SPORTS_FIELD" } },
        Achievement("has_auditorium", "文化地标", "建设大礼堂", AchievementCategory.FACILITY) { it.facilities.any { f -> f.type.name == "AUDITORIUM" } },
        Achievement("has_dormitory", "寄宿名校", "建设宿舍楼", AchievementCategory.FACILITY) { it.facilities.any { f -> f.type.name == "DORMITORY" } },
        Achievement("upgrade_facility", "精益求精", "任一设施升到2级", AchievementCategory.FACILITY) { it.facilities.any { f -> f.level >= 2 } },
        Achievement("max_facility", "登峰造极", "任一设施升到满级", AchievementCategory.FACILITY) { it.facilities.any { f -> f.level >= f.type.maxLevel } },
        Achievement("all_max", "完美校园", "3个以上设施达到满级", AchievementCategory.FACILITY) { it.facilities.count { f -> f.level >= f.type.maxLevel } >= 3 },

        // ═══════════════════════════════════════════
        // 挑战（12个）——高难度/特殊条件
        // ═══════════════════════════════════════════
        Achievement("near_bankruptcy", "绝处逢生", "濒临破产后恢复到正数", AchievementCategory.CHALLENGE) { it.cash > 0 && it.wasNearBankrupt },
        Achievement("speed_level2", "闪电升级", "3年内升到2级", AchievementCategory.CHALLENGE) { it.campusLevel >= 2 && (it.currentYear - it.foundedYear) <= 3 },
        Achievement("speed_level3", "精英速度", "8年内升到3级", AchievementCategory.CHALLENGE) { it.campusLevel >= 3 && (it.currentYear - it.foundedYear) <= 8 },
        Achievement("speed_level4", "传奇速通", "15年内升到4级", AchievementCategory.CHALLENGE) { it.campusLevel >= 4 && (it.currentYear - it.foundedYear) <= 15 },
        Achievement("speed_level5", "速通大师", "25年内升到5级", AchievementCategory.CHALLENGE) { it.campusLevel >= 5 && (it.currentYear - it.foundedYear) <= 25 },
        Achievement("no_debt_year", "量入为出", "连续运营5年以上且从未负债", AchievementCategory.CHALLENGE) { (it.currentYear - it.foundedYear) >= 5 && !it.wasNearBankrupt && it.cash > 0 },
        Achievement("big_earner", "日进斗金", "单校现金>1000万且等级仅为3", AchievementCategory.CHALLENGE) { it.cash >= 1000.0 && it.campusLevel == 3 },
        Achievement("early_reputation", "一鸣惊人", "5年内声誉达到1000", AchievementCategory.CHALLENGE) { it.reputation >= 1000 && (it.currentYear - it.foundedYear) <= 5 },
        Achievement("marathon", "马拉松", "经营超过40年且保持正现金流", AchievementCategory.CHALLENGE) { (it.currentYear - it.foundedYear) >= 40 && it.cash > 0 },
        Achievement("minimalist", "极简主义", "仅用2个设施达到3级学校", AchievementCategory.CHALLENGE) { it.campusLevel >= 3 && it.facilities.size <= 2 },
        Achievement("late_bloomer", "大器晚成", "20年后才升到3级", AchievementCategory.CHALLENGE) { it.campusLevel >= 3 && (it.currentYear - it.foundedYear) >= 20 },
        Achievement("perfectionist", "完美主义者", "5星评分+5级学校+现金>5000万", AchievementCategory.CHALLENGE) { it.starRating >= 5.0f && it.campusLevel >= 5 && it.cash >= 5000.0 },

        // ═══════════════════════════════════════════
        // 大学体系（学院/课题链/附属医院/竞赛）
        // ═══════════════════════════════════════════
        Achievement("college_first", "开院立学", "成立第一所学院", AchievementCategory.ACADEMIC) { collegeCount(it) >= 1 },
        Achievement("college_three", "三院并立", "成立3所学院", AchievementCategory.ACADEMIC) { collegeCount(it) >= 3 },
        Achievement("college_all", "六大全院", "成立全部6所学院", AchievementCategory.ACADEMIC) { collegeCount(it) >= 6 },
        Achievement("chain_all", "科研三链", "教学、应用、产学研三条课题链全部结题", AchievementCategory.ACADEMIC) { chainFinished(it) >= 3 },
        Achievement("hospital", "悬壶济世", "建成附属医院", AchievementCategory.FACILITY) { it.policyJson.contains("\"affiliatedHospital\":true") },
        Achievement("core_courses", "课程体系", "任一学院开设满3门专业核心课", AchievementCategory.ACADEMIC) {
            Regex("\"coreCourses\":\\{[^}]*\"[A-Z_]+\":3").containsMatchIn(it.policyJson)
        },
        Achievement("graduate", "硕博点", "启动研究生培养体系", AchievementCategory.ACADEMIC) { it.policyJson.contains("\"graduateProgram\":true") },
        Achievement("competition_win", "竞赛首冠", "校际学科竞赛首次夺冠", AchievementCategory.CHALLENGE) {
            Regex("\\\\\"totalWins\\\\\":[1-9]").containsMatchIn(it.policyJson)
        }
    )

    private fun collegeCount(school: School): Int =
        Regex("\"foundedColleges\":\\[([^\\]]*)\\]").find(school.policyJson)
            ?.groupValues?.get(1)
            ?.split(',')?.count { it.isNotBlank() } ?: 0

    private fun chainFinished(school: School): Int =
        Regex("\"completedChains\":\\[([^\\]]*)\\]").find(school.policyJson)
            ?.groupValues?.get(1)
            ?.split(',')?.count { it.isNotBlank() } ?: 0
}
