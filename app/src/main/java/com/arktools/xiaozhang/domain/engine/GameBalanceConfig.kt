package com.arktools.xiaozhang.domain.engine

object GameBalanceConfig {
    val INITIAL_CASH = 500.0  // 初始资金500万：用于组建学院、实验室和首批师资
    val INITIAL_REPUTATION = 35L  // 初创大学需要从社会声誉起步
    val INITIAL_CAMPUS_LEVEL = 1
    val INITIAL_MAX_TEACHERS = 6  // 初始学院可配置6位教师
    val STARTING_YEAR = 2026

    // ═══════════════════════════════════════════
    // 学校等级系统（6级制，极难升级）
    // ═══════════════════════════════════════════
    val MAX_SCHOOL_LEVEL = 6

    /**
     * 学校等级名称
     */
    fun getSchoolLevelName(level: Int): String {
        return when (level) {
            1 -> "新建综合大学"
            2 -> "区域应用型大学"
            3 -> "省级特色大学"
            4 -> "国家一流大学"
            5 -> "高水平研究型大学"
            6 -> "世界一流大学"
            else -> "新建综合大学"
        }
    }

    /**
     * 升级到下一级所需条件
     * 返回 SchoolUpgradeRequirement
     */
    fun getUpgradeRequirements(targetLevel: Int): SchoolUpgradeRequirement {
        return when (targetLevel) {
            2 -> SchoolUpgradeRequirement(
                cashCost = 80.0,         // 需要80万资金
                minReputation = 200L,     // 至少200声望
                minTeachers = 8,
                minClasses = 4,
                minStudents = 120,
                description = "完成首批学院建设，获得地方教育主管部门办学许可"
            )
            3 -> SchoolUpgradeRequirement(
                cashCost = 300.0,        // 需要300万
                minReputation = 800L,    // 800声望
                minTeachers = 18,
                minClasses = 8,
                minStudents = 400,       // v2.10: 500→400，避免"要学院才能扩招、要扩招才能建学院"死锁
                minYearsAtCurrentLevel = 1,
                description = "通过本科教学合格评估，形成稳定的专业与人才培养体系"
            )
            4 -> SchoolUpgradeRequirement(
                cashCost = 1000.0,       // 需要1000万
                minReputation = 2500L,   // 2500声望
                minTeachers = 40,
                minClasses = 16,
                minStudents = 1000,      // v2.10: 1200→1000，与Lv3同因
                minYearsAtCurrentLevel = 2,
                minAverageTeacherSkill = 70,
                description = "形成优势学科群，科研成果和毕业生就业质量进入省内前列"
            )
            5 -> SchoolUpgradeRequirement(
                cashCost = 15000.0,      // v2.9: 需要1.5亿（原5000万太便宜）
                minReputation = 8000L,   // 8000声望
                minTeachers = 80,
                minClasses = 28,
                minStudents = 3000,
                minYearsAtCurrentLevel = 3,
                minAverageTeacherSkill = 85,
                requiresResearch = true,
                description = "建设国家级科研平台，进入高水平大学建设序列"
            )
            6 -> SchoolUpgradeRequirement(
                cashCost = 80000.0,      // v2.9: 需要8亿（原2亿太便宜，后期资金过剩）
                minReputation = 30000L,  // 30000声望
                minTeachers = 160,
                minClasses = 48,
                minStudents = 8000,
                minYearsAtCurrentLevel = 5,
                minAverageTeacherSkill = 95,
                requiresResearch = true,
                requiresInternational = true,
                description = "拥有全球学术网络、顶尖科研成果与持续影响社会的能力"
            )
            else -> SchoolUpgradeRequirement(
                cashCost = Double.MAX_VALUE,
                minReputation = Long.MAX_VALUE,
                description = "已达最高等级"
            )
        }
    }

    val DAILY_PROGRESS_BASE = 0.35f  // 备课速度降低（课程准备更漫长）
    val DAILY_PROGRESS_SKILL_FACTOR = 0.003f  // 技能对备课速度加成降低
    val DAILY_PROGRESS_METHOD_BONUS_MULTIPLIER = 0.8f  // 教学方法加成降低

    val TEACHER_FATIGUE_DAILY_INCREASE = 1  // 疲劳积累（每日+1，适中节奏）
    val TEACHER_FATIGUE_LOOSE_THRESHOLD = 70  // 疲劳阈值（70以上才触发不满）
    val TEACHER_FATIGUE_LOYALTY_DECREASE = 1  // 疲劳导致忠诚度流失（每日-1）
    val TEACHER_LOW_SALARY_LOYALTY_DECREASE = 1  // 低薪忠诚度惩罚（每日-1，原为-2过于激进）
    val TEACHER_INSPIRATION_CHANCE = 0.003f  // 灵感触发概率降低
    val TEACHER_INSPIRATION_LOYALTY_THRESHOLD = 80  // 触发灵感需要更高忠诚度
    val TEACHER_INSPIRATION_SKILL_BONUS = 10  // 灵感提升量减半

    val COURSE_HEAT_DECAY_RATE = 0.94f  // 热度衰减大幅加速（课程更快过时）
    val COURSE_HEAT_MINIMUM = 1f  // 热度下限极低（过时课程几乎无人问津）
    val COURSE_SCORE_TEAM_WEIGHT = 0.35f
    val COURSE_SCORE_METHOD_WEIGHT = 0.20f
    val COURSE_SCORE_DESIGN_WEIGHT = 0.25f
    val COURSE_SCORE_DISTRICT_WEIGHT = 0.20f
    val COURSE_SCORE_BUG_PENALTY_PER = 0.05f  // 问题惩罚加重

    val MONTHLY_TUITION_PER_STUDENT = 0.45  // 大学基础月学费（万元），学费与科研、产业经费共同构成收入

    /**
     * 学费随学校等级增长系数
     * 设计逻辑：学校等级越高，品牌溢价越大，学费越贵
     * Lv.1: 0.6万/月（年7.2万，乡镇私立）
     * Lv.2: 0.78万/月（年9.36万，区级学校）
     * Lv.3: 1.02万/月（年12.24万，市重点）
     * Lv.4: 1.38万/月（年16.56万，省示范）
     * Lv.5: 1.8万/月（年21.6万，国家名校）
     * Lv.6: 2.4万/月（年28.8万，世界学府）
     */
    fun getTuitionMultiplier(campusLevel: Int): Double {
        return when (campusLevel) {
            1 -> 1.0
            2 -> 1.3
            3 -> 1.7
            4 -> 2.3
            5 -> 3.0
            6 -> 4.0
            else -> 1.0
        }
    }
    val ENROLLMENT_BASE_MULTIPLIER = 18  // 招生基础乘数（适度提高，让前期学生增长稍快）
    val ENROLLMENT_REPUTATION_DIVISOR = 120000.0  // 声望对招生影响分母（降低门槛，让声望更早见效）
    val ENROLLMENT_HEAT_DECAY_POWER = 0.70  // 热度衰减对招生影响

    val MONTHLY_EXPENSE_DAY = 1

    val EVENT_PROBABILITY = 0.01f

    val MARKET_SALARY_C = 0.5   // C级月薪0.5万（5000元，应届毕业生）
    val MARKET_SALARY_B = 1.2   // B级月薪1.2万（12000元，骨干教师）
    val MARKET_SALARY_A = 2.5   // A级月薪2.5万（25000元，优秀教师）
    val MARKET_SALARY_S = 5.0   // S级月薪5.0万（50000元，顶级名师）

    val BANKRUPTCY_THRESHOLD = -50.0  // 破产线收紧（负债50万即触发破产）

    // Difficulty curve: costs and competition scale with game year
    val DIFFICULTY_BASE_YEAR = 2026
    val DIFFICULTY_SALARY_INFLATION_PER_YEAR = 0.03   // 3% 年薪资涨幅（教师市场竞争）
    val DIFFICULTY_RENT_INFLATION_PER_YEAR = 0.03     // 3% 年租金涨幅（原6%过于激进，22年×3.6导致费用占比过高）
    val DIFFICULTY_COMPETITION_GROWTH_PER_YEAR = 0.03 // 3% 年竞争加剧

    /**
     * Calculate difficulty multiplier that increases over time.
     * This makes later years progressively harder, requiring better strategy.
     */
    fun getDifficultyMultiplier(currentYear: Int): Double {
        val yearsElapsed = (currentYear - DIFFICULTY_BASE_YEAR).coerceAtLeast(0)
        return 1.0 + yearsElapsed * DIFFICULTY_COMPETITION_GROWTH_PER_YEAR
    }

    /**
     * Salary inflation: teachers demand more pay over time.
     */
    fun getSalaryInflation(currentYear: Int): Double {
        val yearsElapsed = (currentYear - DIFFICULTY_BASE_YEAR).coerceAtLeast(0)
        // v2.9: 封顶5.0倍（约55年后达到上限），配合更高基础薪资让后期人力开支更大
        return Math.pow(1.0 + DIFFICULTY_SALARY_INFLATION_PER_YEAR, yearsElapsed.toDouble()).coerceAtMost(5.0)
    }

    /**
     * Rent inflation: property costs increase over time.
     */
    fun getRentInflation(currentYear: Int): Double {
        val yearsElapsed = (currentYear - DIFFICULTY_BASE_YEAR).coerceAtLeast(0)
        // v2.9: 封顶5.0倍，与薪资通胀保持一致
        return Math.pow(1.0 + DIFFICULTY_RENT_INFLATION_PER_YEAR, yearsElapsed.toDouble()).coerceAtMost(5.0)
    }

    fun getMonthlyRent(campusLevel: Int): Double {
        // 月租金/运营维护费（万元），6级制
        // v2.9: 大幅提高后期运营费，防止后期资金过度膨胀
        return when (campusLevel) {
            1 -> 8.0      // 新建大学，月运营8万（场地租赁+行政+基础维护）
            2 -> 20.0     // 区域应用型，月运营20万
            3 -> 60.0     // 省级特色，月运营60万
            4 -> 250.0    // 国家一流，月运营250万
            5 -> 1500.0   // 高水平研究型，月运营1500万（科研平台+行政开支）
            6 -> 5000.0   // 世界一流，月运营5000万（国际化运营成本极高）
            else -> 8.0
        }
    }

    /**
     * 生均月度办学成本（万元/人）。招生越多，水电、实验耗材、宿舍和教务开支越高，
     * 避免只靠学费堆现金、规模扩张没有流通代价。
     */
    fun getMonthlyStudentOperatingCost(campusLevel: Int): Double {
        return when (campusLevel) {
            1 -> 0.10
            2 -> 0.16
            3 -> 0.24
            4 -> 0.36
            5 -> 0.60
            6 -> 0.85
            else -> 0.10
        }
    }

    fun getMarketTrend(subject: com.arktools.xiaozhang.domain.model.Subject, theme: com.arktools.xiaozhang.domain.model.CourseTheme, year: Int): Float {
        var trend = 1.0f

        when {
            year < 2000 && theme == com.arktools.xiaozhang.domain.model.CourseTheme.EXAM_PREP -> trend *= 1.3f
            year < 2000 && theme == com.arktools.xiaozhang.domain.model.CourseTheme.INTEREST -> trend *= 0.7f
            year >= 2015 && theme == com.arktools.xiaozhang.domain.model.CourseTheme.STEM -> trend *= 1.2f
            year >= 2020 && subject == com.arktools.xiaozhang.domain.model.Subject.MUSIC -> trend *= 1.1f
            year >= 2020 && theme == com.arktools.xiaozhang.domain.model.CourseTheme.INTERNATIONAL -> trend *= 1.1f
        }

        return trend
    }

    fun getCampusUpgradeCost(currentLevel: Int): Double {
        // 升级到下一级的资金成本（万元），在 getUpgradeRequirements 中定义
        val targetLevel = currentLevel + 1
        return getUpgradeRequirements(targetLevel).cashCost
    }

    fun getMaxTeachersForLevel(campusLevel: Int): Int {
        // 大学师资规模随办学层级扩张
        return when (campusLevel) {
            1 -> 12
            2 -> 28
            3 -> 60
            4 -> 120
            5 -> 240
            6 -> 500
            else -> 12
        }
    }

    /**
     * 校长月薪（万元/月）—— 按学校等级递增
     * 这是校长唯一的正当收入来源，直接进入个人资金（personalFunds）
     * 设计参考：现实中校长月薪约0.8~3万，游戏中略高以支撑人脉/派系等花费
     */
    fun getPrincipalMonthlySalary(campusLevel: Int): Double {
        return when (campusLevel) {
            1 -> 1.2      // 新建大学校长：1.2万/月
            2 -> 2.0
            3 -> 3.5
            4 -> 5.0
            5 -> 8.0
            6 -> 15.0
            else -> 1.2
        }
    }

    /**
     * 每个班型的最大班级数量（根据学校等级递增）
     * 确保高等级学校能容纳足够学生达到升级条件
     */
    fun getMaxClassesPerTierForLevel(campusLevel: Int): Int {
        return when (campusLevel) {
            1 -> 3        // 乡镇学校：每类最多3个班
            2 -> 5        // 区级学校：每类最多5个班
            3 -> 10       // 市重点：每类最多10个班
            4 -> 15       // 省示范：每类最多15个班
            5 -> 40       // 国家名校：每类最多40个班（理论容量9200，5000学生可达）
            6 -> 60       // 世界学府：每类最多60个班
            else -> 3
        }
    }

    // ═══════════════════════════════════════════
    // 功能解锁等级要求
    // ═══════════════════════════════════════════

    /** 大学专业与课程主题从基础通识起步，随大学等级开放科研、产业和国际课程。 */
    fun getMaxSubjectsForLevel(level: Int): Int {
        return when (level) {
            1 -> 6
            2 -> 10
            3 -> 16
            4 -> 24
            5 -> 36
            else -> 99
        }
    }

    /** 课程主题解锁（名称匹配 CourseTheme 枚举） */
    fun getUnlockedThemesForLevel(level: Int): List<String> {
        val themes = mutableListOf("PRACTICAL") // 1级聚焦基础专业与应用型人才培养
        if (level >= 2) themes.add("INTEREST")
        if (level >= 3) themes.addAll(listOf("STEM", "COMPETITION"))
        if (level >= 4) themes.addAll(listOf("CREATIVE", "ARTISTIC"))
        if (level >= 5) themes.addAll(listOf("INTERNATIONAL", "TRADITIONAL"))
        if (level >= 6) themes.add("EXAM_PREP")
        return themes.distinct()
    }

    /** 设施解锁 */
    fun getMaxFacilitiesForLevel(level: Int): Int {
        return when (level) {
            1 -> 10
            2 -> 16
            3 -> 22
            4 -> 30
            5 -> 40
            6 -> 80
            else -> 10
        }
    }

    /** 社团上限 */
    fun getMaxClubsForLevel(level: Int): Int {
        return when (level) {
            1 -> 0      // 1级不能建社团
            2 -> 2
            3 -> 5
            4 -> 10
            5 -> 20
            6 -> 50
            else -> 0
        }
    }

    /** 课程规模解锁 */
    fun getMaxCourseSizeForLevel(level: Int): String {
        return when (level) {
            1 -> "SMALL"        // 只能小班
            2 -> "MEDIUM"       // 可中班
            3 -> "LARGE"        // 可大班
            4 -> "EXTRA_LARGE"  // 可超大班
            5 -> "LECTURE"      // 可讲堂
            6 -> "UNLIMITED"    // 无限制
            else -> "SMALL"
        }
    }

    fun isModuleUnlocked(module: GameModule, campusLevel: Int): Boolean {
        return campusLevel >= module.unlockLevel
    }

    fun getModuleLockReason(module: GameModule): String {
        return "${getSchoolLevelName(module.unlockLevel)}（校园${module.unlockLevel}级）解锁"
    }

    fun getNewlyUnlockedModules(previousLevel: Int, newLevel: Int): List<GameModule> {
        if (newLevel <= previousLevel) return emptyList()
        return GameModule.entries.filter { module ->
            module.unlockLevel > previousLevel && module.unlockLevel <= newLevel
        }
    }

    fun getNextStageUnlocks(campusLevel: Int): List<GameModule> {
        val nextLevel = (campusLevel + 1).coerceAtMost(MAX_SCHOOL_LEVEL)
        if (nextLevel <= campusLevel) return emptyList()
        return GameModule.entries.filter { it.unlockLevel == nextLevel }
    }

    fun moduleForTab(tab: Int): GameModule? {
        return when (tab) {
            5 -> GameModule.RANKING
            6 -> GameModule.STOCK
            12 -> GameModule.MARKETING
            15 -> GameModule.ALUMNI
            17 -> GameModule.CLUB
            18 -> GameModule.SEASONAL
            20 -> GameModule.REPUTATION
            21 -> GameModule.STUDENT_LIFE
            23 -> GameModule.CONFERENCE
            27 -> GameModule.PARENT
            28 -> GameModule.GOVERNMENT
            29 -> GameModule.SCHOLARSHIP
            31 -> GameModule.TIMETABLE
            33 -> GameModule.PRINCIPAL
            else -> null
        }
    }

    // ═══════════════════════════════════════════
    // 学区等级加成系统
    // ═══════════════════════════════════════════

    /**
     * 学校等级对学区曝光的加成倍率
     * 等级越高，学区曝光额外加成越大
     */
    fun getDistrictExposureBonus(schoolLevel: Int): Double {
        return when (schoolLevel) {
            1 -> 1.0    // 无加成
            2 -> 1.1    // +10%
            3 -> 1.25   // +25%
            4 -> 1.5    // +50%
            5 -> 2.0    // +100%
            6 -> 3.0    // +200%
            else -> 1.0
        }
    }

    /**
     * 学校等级对学区并发课程上限的加成
     */
    fun getDistrictCourseBonus(schoolLevel: Int): Int {
        return when (schoolLevel) {
            1 -> 0
            2 -> 1
            3 -> 2
            4 -> 3
            5 -> 5
            6 -> 8
            else -> 0
        }
    }

    /**
     * 学校等级对学区抽成的折扣（等级越高，平台给予更优惠的抽成）
     */
    fun getDistrictCommissionDiscount(schoolLevel: Int): Double {
        return when (schoolLevel) {
            1 -> 1.0    // 无折扣
            2 -> 0.95   // 95%
            3 -> 0.90   // 90%
            4 -> 0.85   // 85%
            5 -> 0.75   // 75%
            6 -> 0.60   // 60%（名校品牌效应，平台愿意让利）
            else -> 1.0
        }
    }

    /**
     * 判断学区是否已解锁（同时满足学校等级和声誉）
     */
    fun isDistrictUnlocked(district: com.arktools.xiaozhang.domain.model.DistrictType, schoolLevel: Int, reputation: Long): Boolean {
        return schoolLevel >= district.requiredSchoolLevel && reputation >= district.reputationThreshold
    }

    /**
     * 获取学区的等级名称描述
     */
    fun getDistrictLevelRequirementText(district: com.arktools.xiaozhang.domain.model.DistrictType): String {
        return "${getSchoolLevelName(district.requiredSchoolLevel)}(Lv.${district.requiredSchoolLevel})"
    }

    // ═══════════════════════════════════════════
    // 数值平衡：教师薪资和培训
    // ═══════════════════════════════════════════

    /**
     * 教师培训费用（万元），随技能等级递增——v2.9 大幅提高高技能培训费
     * 注意：currentSkill 为 averageSkill，范围 0-1000（四维属性平均值）
     */
    fun getTrainingCost(currentSkill: Int): Double {
        return when {
            currentSkill < 300 -> 5.0       // 初级培训5万
            currentSkill < 500 -> 15.0      // 中级培训15万
            currentSkill < 700 -> 40.0      // 高级培训40万
            currentSkill < 850 -> 120.0     // 精英培训120万（外聘专家）
            currentSkill < 950 -> 400.0     // 大师培训400万（海外研修）
            else -> 800.0                  // 顶级培训800万（全球顶尖导师一对一）
        }
    }

    /**
     * 培训成功率（技能越高越难提升）——降低成功率
     * 注意：currentSkill 为 averageSkill，范围 0-1000
     */
    fun getTrainingSuccessRate(currentSkill: Int): Double {
        return when {
            currentSkill < 300 -> 0.80  // 80%成功
            currentSkill < 500 -> 0.60  // 60%
            currentSkill < 700 -> 0.40  // 40%
            currentSkill < 850 -> 0.20  // 20%（高级人才培养极难）
            currentSkill < 950 -> 0.08  // 8%（几乎靠运气）
            else -> 0.03               // 3%（顶级突破需要天赋）
        }
    }

    /** 招聘费用（一次性猎头费，万元）：雇佣教师的额外前期成本 v2.9: 提高 */
    fun getHiringFee(level: com.arktools.xiaozhang.domain.model.TeacherLevel): Double {
        return when (level) {
            com.arktools.xiaozhang.domain.model.TeacherLevel.C -> 5.0    // C级猎头费5万
            com.arktools.xiaozhang.domain.model.TeacherLevel.B -> 20.0   // B级20万
            com.arktools.xiaozhang.domain.model.TeacherLevel.A -> 60.0   // A级60万
            com.arktools.xiaozhang.domain.model.TeacherLevel.S -> 200.0  // S级200万（顶级猎头费）
        }
    }
}

/**
 * 学校升级条件数据类
 */
data class SchoolUpgradeRequirement(
    val cashCost: Double = 0.0,
    val minReputation: Long = 0L,
    val minTeachers: Int = 0,
    val minClasses: Int = 0,
    val minStudents: Int = 0,
    val minYearsAtCurrentLevel: Int = 0,
    val minAverageTeacherSkill: Int = 0,
    val requiresResearch: Boolean = false,
    val requiresInternational: Boolean = false,
    val description: String = ""
)

/**
 * 大学功能模块按校园等级徐徐开放。
 * 前期只做招生、师资、教学和校园建设；中后期再打开社会、科研交流和资本玩法。
 */
enum class GameModule(
    val displayName: String,
    val unlockLevel: Int,
    val stageHint: String
) {
    FACILITY("校园设施", 1, "先把教室和基础校园建起来"),
    REPORT("数据报表", 1, "盯紧学费和开支"),
    EVENT("事件记录", 1, "处理日常校务"),
    POLICY("学校政策", 1, "定下本学年办学方针"),
    EXAM("教学评估", 1, "跟踪培养质量"),
    CLUB("社团活动", 2, "学生社团开始申请"),
    SEASONAL("季节活动", 2, "校园活动日历开放"),
    STUDENT_LIFE("学生生活", 2, "宿舍食堂开始占用预算"),
    TIMETABLE("专业课表", 2, "学院课表需要统筹"),
    MARKETING("营销推广", 2, "招生传播可以花钱换生源"),
    REPUTATION("声誉详情", 3, "社会口碑开始影响生源质量"),
    SCHOLARSHIP("奖助学金", 3, "奖学金会分流学费、提升留存"),
    PARENT("校友与家委会", 3, "家庭与社会支持网络成型"),
    RANKING("排行榜", 3, "开始进入大学竞争榜"),
    STOCK("股市投资", 3, "闲钱可以进入资本市场"),
    ALUMNI("校友就业", 4, "毕业生开始反哺母校"),
    CONFERENCE("学术会议", 4, "学术交流带来声誉和就业"),
    GOVERNMENT("产业与社会合作", 4, "政府评估与产业补贴开放"),
    PRINCIPAL("校长办公室", 5, "个人事务与灰色地带出现");
}