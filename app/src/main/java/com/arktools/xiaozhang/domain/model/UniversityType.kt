package com.arktools.xiaozhang.domain.model

/**
 * 办学性质：公办 / 民办（开局选择）。
 * 决定财政模型（拨款 vs 学费）、招生公信力与评估补贴。
 */
enum class SchoolOwnership(
    val key: String,
    val displayName: String,
    val detail: String,
    val tuitionMultiplier: Double,       // 学费定价系数（公办受管制偏低，民办自主偏高）
    val monthlyGrantPerStudent: Double,  // 生均月度财政拨款（万元/生/月，民办为 0）
    val enrollmentMultiplier: Double,    // 招生规模系数（公办公信力加成）
    val govSubsidyMultiplier: Float      // 年度评估补贴系数（民办评估补贴缩水）
) {
    PUBLIC(
        "PUBLIC", "公办",
        "财政按月拨付生均经费，学费受管制，招生公信力高，评估补贴足额",
        0.75, 0.12, 1.08, 1.2f
    ),
    PRIVATE(
        "PRIVATE", "民办",
        "学费自主上浮、无财政拨款、自负盈亏，评估补贴打折",
        1.15, 0.0, 1.0, 0.6f
    );

    companion object {
        fun fromKey(key: String?): SchoolOwnership = entries.firstOrNull { it.key == key } ?: PRIVATE
    }
}

/**
 * 办学层次：高职专科（3年制）/ 应用型本科（4年制，基准玩法）。
 * 决定学制、招生分数与规模、生源质量、学费、可设学院范围与长线目标（专科可升格本科）。
 */
enum class SchoolTier(
    val key: String,
    val displayName: String,
    val detail: String,
    val years: Int,                      // 学制（年）
    val graduationGrade: GradeLevel,     // 毕业年级
    val admissionScoreMin: Int,          // 招生分数区间下限
    val admissionScoreMax: Int,          // 招生分数区间上限
    val tuitionMultiplier: Double,       // 学费系数（专科便宜、本科标准）
    val enrollmentMultiplier: Double,    // 招生规模系数（专科量大）
    val studentQualityFactor: Float,     // 生源质量系数（作用于新生属性）
    val startCash: Double,               // 启动经费（万元）
    val graduateScoreFactor: Float,      // 毕业评估得分系数（专科培养出口更窄）
    val promotionTargetKey: String?,     // 升格目标层次（null = 不可升格）
    val allowedColleges: Set<String>     // 可成立学院（CollegeType.name），升格后按新层次开放
) {
    VOCATIONAL(
        "VOCATIONAL", "高职专科",
        "3年制 · 分数线低 · 生源量大但基础较弱 · 学费亲民 · 就业率是生命线 · 长线目标：申报升格职业本科",
        3, GradeLevel.GRADE_3, 320, 430,
        0.6, 1.35, 0.8f, 380.0, 0.85f, "VOCATIONAL_BACHELOR",
        setOf("LIBERAL_ARTS", "ENGINEERING", "BUSINESS")
    ),
    APPLIED(
        "APPLIED", "应用型本科",
        "4年制 · 标准玩法 · 六大学院全开放 · 长线目标：办成世界一流大学",
        4, GradeLevel.GRADE_4, 430, 520,
        1.0, 1.0, 1.0f, 500.0, 1.0f, null,
        setOf("LIBERAL_ARTS", "SCIENCE", "ENGINEERING", "MEDICINE", "BUSINESS", "ARTS")
    ),
    RESEARCH(
        "RESEARCH", "研究型大学",
        "4年制 · 高分严选 · 生源精而少 · 学费低 · 科研经费是收入大头 · 硕博点校园3级即可启动 · 目标：学术巅峰",
        4, GradeLevel.GRADE_4, 560, 650,
        0.7, 0.75, 1.25f, 650.0, 1.15f, null,
        setOf("LIBERAL_ARTS", "SCIENCE", "ENGINEERING", "MEDICINE", "BUSINESS", "ARTS")
    ),
    VOCATIONAL_BACHELOR(
        "VOCATIONAL_BACHELOR", "职业本科",
        "4年制 · 就业导向 · 校企合作：就业辅导费6折、就业声誉加成+50% · 理学医学不开放 · 目标：就业强校",
        4, GradeLevel.GRADE_4, 400, 480,
        0.85, 1.15, 0.9f, 420.0, 0.95f, null,
        setOf("LIBERAL_ARTS", "ENGINEERING", "BUSINESS", "ARTS")
    );

    /** 是否存在升格长线 */
    val canPromote: Boolean get() = promotionTargetKey != null

    /** 入学到毕业的完整学年跨度（大四毕业 = 3 年跨度） */
    val spanYears: Int get() = graduationGrade.order - 1

    fun allowsCollege(collegeName: String): Boolean = collegeName in allowedColleges

    companion object {
        fun fromKey(key: String?): SchoolTier = entries.firstOrNull { it.key == key } ?: APPLIED
    }
}

/** 从 School 读取当前办学层次（旧档缺省 = 应用型本科） */
fun School.schoolTier(): SchoolTier = SchoolTier.fromKey(tierKey)

/** 从 School 读取当前办学性质（旧档缺省 = 民办） */
fun School.schoolOwnership(): SchoolOwnership = SchoolOwnership.fromKey(ownershipKey)
