package com.arktools.xiaozhang.domain.policy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学校政策管理系统
 * 管理可调节的学校运营参数，影响招生、教学质量、收入和声誉
 */
@Singleton
class SchoolPolicyManager @Inject constructor(
    val competitionManager: com.arktools.xiaozhang.domain.competition.UniversityCompetitionManager,
    val researchChainManager: com.arktools.xiaozhang.domain.research.ResearchChainManager,
    val teacherStoryManager: com.arktools.xiaozhang.domain.teacherdev.TeacherStoryManager,
    val graduateManager: com.arktools.xiaozhang.domain.graduate.GraduateSchoolManager,
    val internationalManager: com.arktools.xiaozhang.domain.international.InternationalProgramManager
) {

    private val _policies = MutableStateFlow(SchoolPolicies())
    val policies: StateFlow<SchoolPolicies> = _policies.asStateFlow()

    /**
     * 更新学费政策
     */
    fun setTuitionLevel(level: TuitionLevel) {
        _policies.value = _policies.value.copy(tuitionLevel = level)
    }



    /**
     * 更新考试难度政策
     */
    fun setExamDifficulty(difficulty: ExamDifficulty) {
        _policies.value = _policies.value.copy(examDifficulty = difficulty)
    }

    /**
     * 更新教师薪资政策
     */
    fun setTeacherPayPolicy(policy: TeacherPayPolicy) {
        _policies.value = _policies.value.copy(teacherPayPolicy = policy)
    }

    /**
     * 更新课外活动政策
     */
    fun setExtracurricularPolicy(policy: ExtracurricularPolicy) {
        _policies.value = _policies.value.copy(extracurricularPolicy = policy)
    }

    /**
     * 更新招生策略
     */
    fun setAdmissionPolicy(policy: AdmissionPolicy) {
        _policies.value = _policies.value.copy(admissionPolicy = policy)
    }

    /**
     * 更新年度招生定位
     */
    fun setEnrollmentPlan(plan: EnrollmentPlan) {
        _policies.value = _policies.value.copy(enrollmentPlan = plan)
    }

    fun setUniversityStrategy(strategy: UniversityStrategy) {
        _policies.value = _policies.value.copy(universityStrategy = strategy)
    }

    fun setBudgetAllocation(allocation: BudgetAllocation) {
        _policies.value = _policies.value.copy(budgetAllocation = allocation.normalized())
    }

    fun openCoreCourse(college: CollegeType): ManagedCollegeResult {
        val dev = _policies.value.collegeDevelopment
        if (!dev.founded.contains(college)) {
            return ManagedCollegeResult(false, "需先成立${college.displayName}")
        }
        val count = dev.coreCourses[college.name] ?: 0
        if (count >= 3) {
            return ManagedCollegeResult(false, "${college.displayName}核心课已开满3门")
        }
        _policies.value = _policies.value.copy(
            collegeDevelopment = dev.copy(
                coreCourses = dev.coreCourses + (college.name to (count + 1))
            )
        )
        return ManagedCollegeResult(
            true,
            "已开设${college.displayName}核心课（${count + 1}/3），该学院学生掌握度与毕业表现提升"
        )
    }

    fun setGraduateProgram(on: Boolean) {
        _policies.value = _policies.value.copy(
            collegeDevelopment = _policies.value.collegeDevelopment.copy(graduateProgram = on)
        )
    }

    /**
     * 应用办学风格：理工/人文免费赠送对应学院；综合返回 50 万启动经费加成。
     * 在新学校创建完成后调用一次。
     */
    fun applyFoundingStyle(key: String, allowedColleges: Set<String>? = null): Double {
        val dev = _policies.value.collegeDevelopment
        val gift: CollegeType? = when (key) {
            "TECH" -> CollegeType.SCIENCE
            "HUMAN" -> CollegeType.LIBERAL_ARTS
            else -> null
        }
        // 办学层次限制：赠送学院不在专科目录内时折算为等额开办经费
        val giftAllowed = gift == null || allowedColleges == null || gift.name in allowedColleges
        val newFounded = if (gift != null && giftAllowed && dev.founded.contains(gift).not()) {
            dev.founded + gift
        } else dev.founded
        _policies.value = _policies.value.copy(
            collegeDevelopment = dev.copy(foundingStyle = key, founded = newFounded)
        )
        return when {
            key == "BALANCED" -> 50.0
            gift != null && !giftAllowed -> gift.foundingCostWan
            else -> 0.0
        }
    }

    fun replaceCollegeDevelopment(dev: CollegeDevelopment) {
        _policies.value = _policies.value.copy(collegeDevelopment = dev)
    }

    fun markCampusTutorialDone() {
        _policies.value = _policies.value.copy(
            collegeDevelopment = _policies.value.collegeDevelopment.copy(tutorialDone = true)
        )
    }

    fun setAffiliatedHospital(built: Boolean) {
        _policies.value = _policies.value.copy(
            collegeDevelopment = _policies.value.collegeDevelopment.copy(affiliatedHospital = built)
        )
    }

    fun setAnnualGoal(goal: AnnualGoal) {
        _policies.value = _policies.value.copy(
            collegeDevelopment = _policies.value.collegeDevelopment.copy(annualGoal = goal)
        )
    }

    fun setAdmissionTrackPlan(plan: com.arktools.xiaozhang.domain.model.AdmissionTrackPlan) {
        _policies.value = _policies.value.copy(admissionTrackPlan = plan.normalized())
    }

    fun previewFoundCollege(type: CollegeType, campusLevel: Int, cash: Double): ManagedCollegeResult {
        val current = _policies.value.collegeDevelopment
        if (current.founded.contains(type)) {
            return ManagedCollegeResult(false, "${type.displayName}已经成立")
        }
        if (campusLevel < type.unlockLevel) {
            return ManagedCollegeResult(
                false,
                "${type.displayName}需要校园${type.unlockLevel}级（当前 ${campusLevel}级）"
            )
        }
        if (cash < type.foundingCostWan) {
            return ManagedCollegeResult(
                false,
                "资金不足，成立${type.displayName}需要 ${type.foundingCostWan.toInt()}万"
            )
        }
        return ManagedCollegeResult(
            true,
            "成立${type.displayName}将投入 ${type.foundingCostWan.toInt()}万，此后每月约 ${"%.1f".format(type.monthlyCostWan)}万"
        )
    }

    fun tryFoundCollege(type: CollegeType, campusLevel: Int, cash: Double): ManagedCollegeResult {
        val preview = previewFoundCollege(type, campusLevel, cash)
        if (!preview.success) return preview
        val current = _policies.value.collegeDevelopment
        _policies.value = _policies.value.copy(
            collegeDevelopment = current.copy(founded = current.founded + type)
        )
        return ManagedCollegeResult(
            true,
            "已成立${type.displayName}，投入 ${type.foundingCostWan.toInt()}万，此后每月约 ${"%.1f".format(type.monthlyCostWan)}万"
        )
    }

    fun evaluateAnnualGoal(
        year: Int,
        campusLevel: Int,
        students: Int,
        research: Int,
        reputation: Long,
        satisfaction: Float,
        employmentRate: Float,
        profit: Double
    ): AnnualGoalResult {
        val development = _policies.value.collegeDevelopment
        val goal = development.annualGoal
        val result = goal.evaluate(
            campusLevel = campusLevel,
            students = students,
            research = research,
            reputation = reputation,
            satisfaction = satisfaction,
            employmentRate = employmentRate,
            previousReputation = development.lastReviewReputation,
            previousResearch = development.lastReviewResearch,
            previousStudents = development.lastReviewStudents,
            previousSatisfaction = development.lastReviewSatisfaction
        )
        _policies.value = _policies.value.copy(
            collegeDevelopment = development.copy(
                lastReviewYear = year,
                lastReviewReputation = reputation,
                lastReviewResearch = research,
                lastReviewStudents = students,
                lastReviewSatisfaction = satisfaction
            )
        )
        val profitNote = if (profit >= 0) "学期财务平衡。" else "学期出现亏损，评估偏紧。"
        return result.copy(detail = result.detail + profitNote)
    }

    /**
     * 获取所有政策的综合效果
     */
    fun getPolicyEffects(): PolicyEffects {
        val p = _policies.value
        val chainQualityBonus = researchChainManager.qualityBonus()
        return PolicyEffects(
            tuitionMultiplier = p.tuitionLevel.revenueMultiplier,
            enrollmentMultiplier = calculateEnrollmentMultiplier(p),
            qualityMultiplier = calculateQualityMultiplier(p) * (1f + chainQualityBonus),
            satisfactionModifier = calculateSatisfactionModifier(p),
            reputationModifier = calculateReputationModifier(p),
            expenseMultiplier = calculateExpenseMultiplier(p),
            dropoutRateModifier = calculateDropoutModifier(p),
            graduationQualityBonus = calculateGraduationBonus(p),
            enrollmentQualityMultiplier = p.enrollmentPlan.qualityMultiplier,
            specialTalentMultiplier = p.enrollmentPlan.specialTalentMultiplier,
            welfareReputationBonus = p.enrollmentPlan.welfareReputationBonus,
            extraResearchDays = p.universityStrategy.extraResearchDays +
                p.budgetAllocation.extraResearchDays() +
                p.collegeDevelopment.extraResearchDays(),
            strategyName = p.universityStrategy.displayName,
            teachingFocus = p.budgetAllocation.teachingWeight,
            researchFocus = p.budgetAllocation.researchWeight,
            campusLifeFocus = p.budgetAllocation.campusLifeWeight,
            societyFocus = p.budgetAllocation.societyWeight,
            monthlySpecialBudgetCost = p.budgetAllocation.monthlyCostWan() +
                p.collegeDevelopment.monthlyCostWan(),
            collegeEnrollmentMultiplier = p.collegeDevelopment.enrollmentMultiplier(),
            collegeQualityMultiplier = p.collegeDevelopment.qualityMultiplier(),
            collegeSatisfactionModifier = p.collegeDevelopment.satisfactionModifier(),
            collegeReputationModifier = p.collegeDevelopment.reputationModifier(),
            collegeEmploymentBonus = p.collegeDevelopment.employmentBonus(),
            annualGoalName = p.collegeDevelopment.annualGoal.displayName,
            foundedCollegeNames = p.collegeDevelopment.founded.map { it.displayName }
        )
    }

    private fun calculateEnrollmentMultiplier(p: SchoolPolicies): Float {
        var mult = 1f
        // 学费越高，报名意愿越低
        mult *= p.tuitionLevel.enrollmentMultiplier
        // 奖学金招生加成已在 ScholarshipManager.getEnrollmentBonus() 中应用
        // 基础招生政策与年度招生定位叠加
        mult *= p.admissionPolicy.enrollmentMultiplier
        mult *= p.enrollmentPlan.enrollmentMultiplier
        mult *= p.universityStrategy.enrollmentMultiplier
        mult *= p.collegeDevelopment.enrollmentMultiplier()
        return mult
    }

    private fun calculateQualityMultiplier(p: SchoolPolicies): Float {
        var mult = 1f
        // 教师薪资影响教学质量
        mult *= p.teacherPayPolicy.qualityBonus
        // 考试难度影响教学严格程度
        mult *= p.examDifficulty.qualityMultiplier
        mult *= p.universityStrategy.qualityMultiplier
        mult *= p.budgetAllocation.qualityMultiplier()
        mult *= p.collegeDevelopment.qualityMultiplier()
        return mult
    }

    private fun calculateSatisfactionModifier(p: SchoolPolicies): Float {
        var mod = 0f
        // 学费高降低满意度
        mod += p.tuitionLevel.satisfactionModifier
        // 课外活动提升满意度
        mod += p.extracurricularPolicy.satisfactionBonus
        // 考试太难降低满意度
        mod += p.examDifficulty.satisfactionModifier
        // 教师薪资间接提高满意度（更好的教师）
        mod += p.teacherPayPolicy.satisfactionBonus
        mod += p.budgetAllocation.satisfactionModifier()
        mod += p.collegeDevelopment.satisfactionModifier()
        return mod
    }

    private fun calculateReputationModifier(p: SchoolPolicies): Long {
        var mod = 0L
        // 高学费 = 精英定位 = 声誉+
        mod += p.tuitionLevel.reputationModifier
        // 严格考试 = 学术声誉+
        mod += p.examDifficulty.reputationModifier
        // 精英招生 = 声誉+
        mod += p.admissionPolicy.reputationModifier
        // 丰富课外 = 声誉+
        mod += p.extracurricularPolicy.reputationBonus
        mod += p.universityStrategy.reputationModifier
        mod += p.budgetAllocation.reputationModifier()
        mod += p.collegeDevelopment.reputationModifier()
        return mod
    }

    private fun calculateExpenseMultiplier(p: SchoolPolicies): Float {
        var mult = 1f
        // 教师薪资倍率已在 GameEngine.deductMonthlyExpenses 中单独应用于薪资部分
        // 课外活动开销
        mult *= p.extracurricularPolicy.expenseMultiplier
        mult *= p.universityStrategy.expenseMultiplier
        return mult
    }

    private fun calculateDropoutModifier(p: SchoolPolicies): Float {
        var mod = 0f
        // 考试太难增加辍学
        mod += p.examDifficulty.dropoutModifier
        // 学费太高增加辍学
        mod += p.tuitionLevel.dropoutModifier
        // 课外活动减少辍学
        mod += p.extracurricularPolicy.dropoutReduction
        return mod
    }

    private fun calculateGraduationBonus(p: SchoolPolicies): Float {
        return p.examDifficulty.graduationBonus + p.universityStrategy.graduationQualityBonus
    }

    fun resetToDefaults() {
        _policies.value = SchoolPolicies()
        competitionManager.reset()
        researchChainManager.reset()
        teacherStoryManager.reset()
        graduateManager.reset()
        internationalManager.reset()
    }

    fun toJson(): String {
        return try {
            val p = _policies.value
            val data = PolicyPersistData(
                tuitionLevel = p.tuitionLevel.name,
                examDifficulty = p.examDifficulty.name,
                teacherPayPolicy = p.teacherPayPolicy.name,
                extracurricularPolicy = p.extracurricularPolicy.name,
                admissionPolicy = p.admissionPolicy.name,
                enrollmentPlan = p.enrollmentPlan.name,
                universityStrategy = p.universityStrategy.name,
                teachingWeight = p.budgetAllocation.teachingWeight,
                researchWeight = p.budgetAllocation.researchWeight,
                campusLifeWeight = p.budgetAllocation.campusLifeWeight,
                societyWeight = p.budgetAllocation.societyWeight,
                foundedColleges = p.collegeDevelopment.founded.map { it.name },
                annualGoal = p.collegeDevelopment.annualGoal.name,
                lastReviewYear = p.collegeDevelopment.lastReviewYear,
                lastReviewReputation = p.collegeDevelopment.lastReviewReputation,
                lastReviewResearch = p.collegeDevelopment.lastReviewResearch,
                lastReviewStudents = p.collegeDevelopment.lastReviewStudents,
                lastReviewSatisfaction = p.collegeDevelopment.lastReviewSatisfaction,
                liberalTrackWeight = p.admissionTrackPlan.liberalWeight,
                scienceTrackWeight = p.admissionTrackPlan.scienceWeight,
                engineeringTrackWeight = p.admissionTrackPlan.engineeringWeight,
                businessTrackWeight = p.admissionTrackPlan.businessWeight,
                artsTrackWeight = p.admissionTrackPlan.artsWeight,
                medicineTrackWeight = p.admissionTrackPlan.medicineWeight,
                competitionStateJson = competitionManager.toJson(),
                researchChainStateJson = researchChainManager.toJson(),
                storyStateJson = teacherStoryManager.toJson(),
                graduateStateJson = graduateManager.toJson(),
                internationalStateJson = internationalManager.toJson(),
                affiliatedHospital = p.collegeDevelopment.affiliatedHospital,
                coreCourses = p.collegeDevelopment.coreCourses,
                graduateProgram = p.collegeDevelopment.graduateProgram,
                foundingStyle = p.collegeDevelopment.foundingStyle,
                placedBuildings = p.collegeDevelopment.placedBuildings,
                terrainMap = p.collegeDevelopment.terrainMap,
                tutorialDone = p.collegeDevelopment.tutorialDone,
                disciplinesJson = p.collegeDevelopment.disciplinesJson
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<PolicyPersistData>(json)
            val restoredPolicies = SchoolPolicies(
                tuitionLevel = try { TuitionLevel.valueOf(data.tuitionLevel) } catch (_: Exception) { TuitionLevel.STANDARD },
                examDifficulty = try { ExamDifficulty.valueOf(data.examDifficulty) } catch (_: Exception) { ExamDifficulty.MODERATE },
                teacherPayPolicy = try { TeacherPayPolicy.valueOf(data.teacherPayPolicy) } catch (_: Exception) { TeacherPayPolicy.COMPETITIVE },
                extracurricularPolicy = try { ExtracurricularPolicy.valueOf(data.extracurricularPolicy) } catch (_: Exception) { ExtracurricularPolicy.STANDARD },
                admissionPolicy = try { AdmissionPolicy.valueOf(data.admissionPolicy) } catch (_: Exception) { AdmissionPolicy.BALANCED },
                enrollmentPlan = try { EnrollmentPlan.valueOf(data.enrollmentPlan) } catch (_: Exception) { EnrollmentPlan.BALANCED },
                universityStrategy = try { UniversityStrategy.valueOf(data.universityStrategy) } catch (_: Exception) { UniversityStrategy.BALANCED },
                budgetAllocation = BudgetAllocation(
                    teachingWeight = data.teachingWeight,
                    researchWeight = data.researchWeight,
                    campusLifeWeight = data.campusLifeWeight,
                    societyWeight = data.societyWeight
                ).normalized(),
                collegeDevelopment = CollegeDevelopment(
                    founded = data.foundedColleges.mapNotNull { name ->
                        try { CollegeType.valueOf(name) } catch (_: Exception) { null }
                    }.distinct(),
                    annualGoal = try {
                        AnnualGoal.valueOf(data.annualGoal)
                    } catch (_: Exception) {
                        AnnualGoal.BALANCED_GROWTH
                    },
                    lastReviewYear = data.lastReviewYear,
                    lastReviewReputation = data.lastReviewReputation,
                    lastReviewResearch = data.lastReviewResearch,
                    lastReviewStudents = data.lastReviewStudents,
                    lastReviewSatisfaction = data.lastReviewSatisfaction,
                    affiliatedHospital = data.affiliatedHospital,
                    coreCourses = data.coreCourses,
                    graduateProgram = data.graduateProgram,
                    foundingStyle = data.foundingStyle,
                    placedBuildings = data.placedBuildings,
                    terrainMap = data.terrainMap,
                    tutorialDone = data.tutorialDone,
                    disciplinesJson = data.disciplinesJson
                ),
                admissionTrackPlan = com.arktools.xiaozhang.domain.model.AdmissionTrackPlan(
                    liberalWeight = data.liberalTrackWeight,
                    scienceWeight = data.scienceTrackWeight,
                    engineeringWeight = data.engineeringTrackWeight,
                    businessWeight = data.businessTrackWeight,
                    artsWeight = data.artsTrackWeight,
                    medicineWeight = data.medicineTrackWeight
                ).normalized()
            )
            _policies.value = restoredPolicies
            competitionManager.restoreFromJson(data.competitionStateJson)
            researchChainManager.restoreFromJson(data.researchChainStateJson)
            teacherStoryManager.restoreFromJson(data.storyStateJson)
            graduateManager.restoreFromJson(data.graduateStateJson)
            internationalManager.restoreFromJson(data.internationalStateJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("SchoolPolicyManager.restoreFromJson failed", e)
        }
    }
}

/**
 * 所有学校政策集合
 */
data class SchoolPolicies(
    val tuitionLevel: TuitionLevel = TuitionLevel.STANDARD,
    val examDifficulty: ExamDifficulty = ExamDifficulty.MODERATE,
    val teacherPayPolicy: TeacherPayPolicy = TeacherPayPolicy.COMPETITIVE,
    val extracurricularPolicy: ExtracurricularPolicy = ExtracurricularPolicy.STANDARD,
    val admissionPolicy: AdmissionPolicy = AdmissionPolicy.BALANCED,
    val enrollmentPlan: EnrollmentPlan = EnrollmentPlan.BALANCED,
    val universityStrategy: UniversityStrategy = UniversityStrategy.BALANCED,
    val budgetAllocation: BudgetAllocation = BudgetAllocation(),
    val collegeDevelopment: CollegeDevelopment = CollegeDevelopment(),
    val admissionTrackPlan: com.arktools.xiaozhang.domain.model.AdmissionTrackPlan =
        com.arktools.xiaozhang.domain.model.AdmissionTrackPlan()
)

/**
 * 学费等级
 */
enum class TuitionLevel(
    val displayName: String,
    val icon: String,
    val description: String,
    val revenueMultiplier: Float,
    val enrollmentMultiplier: Float,
    val satisfactionModifier: Float,
    val reputationModifier: Long,
    val dropoutModifier: Float
) {
    SUBSIDIZED("公益低价", "💚", "极低学费，吸引大量学生，但收入微薄",
        0.4f, 1.6f, 15f, -5L, -0.03f),
    AFFORDABLE("经济实惠", "💙", "低于市场价，报名量大但利润薄",
        0.7f, 1.3f, 8f, -1L, -0.015f),
    STANDARD("标准定价", "⚪", "市场均价，平衡收入和报名",
        1.0f, 1.0f, 0f, 0L, 0f),
    PREMIUM("高端定价", "💛", "高于市场价，收入高但报名少",
        1.4f, 0.7f, -10f, 5L, 0.02f),
    ELITE("精英定价", "💎", "顶级定价，只吸引最优质家庭",
        2.0f, 0.4f, -20f, 12L, 0.05f)
}



/**
 * 考试难度
 */
enum class ExamDifficulty(
    val displayName: String,
    val icon: String,
    val description: String,
    val qualityMultiplier: Float,
    val satisfactionModifier: Float,
    val reputationModifier: Long,
    val dropoutModifier: Float,
    val graduationBonus: Float
) {
    EASY("轻松模式", "😊", "通过率高，学生压力小，但学术含金量低",
        0.8f, 12f, -5L, -0.03f, -0.15f),
    MODERATE("适中难度", "📝", "平衡学术要求和通过率",
        1.0f, 0f, 0L, 0f, 0f),
    CHALLENGING("高标准", "📚", "严格考核，培养真才实学",
        1.2f, -8f, 5L, 0.03f, 0.12f),
    RIGOROUS("极高要求", "🎯", "顶尖学术标准，只有优秀学生能毕业",
        1.4f, -18f, 10L, 0.06f, 0.25f)
}

/**
 * 教师薪资政策
 */
enum class TeacherPayPolicy(
    val displayName: String,
    val icon: String,
    val description: String,
    val qualityBonus: Float,
    val satisfactionBonus: Float,
    val expenseMultiplier: Float
) {
    MINIMUM("最低标准", "💸", "业内最低薪资，难以留住人才",
        0.7f, -8f, 0.7f),
    BELOW_AVERAGE("低于均值", "📉", "略低于市场价，流动率较高",
        0.85f, -3f, 0.85f),
    COMPETITIVE("市场水平", "⚖️", "与市场持平，正常招聘",
        1.0f, 0f, 1.0f),
    ABOVE_AVERAGE("高于均值", "📈", "高薪吸引优秀教师",
        1.2f, 5f, 1.2f),
    TOP_TIER("顶级薪酬", "👑", "业界顶薪，吸引最顶尖教育人才",
        1.45f, 10f, 1.5f)
}

/**
 * 课外活动政策
 */
enum class ExtracurricularPolicy(
    val displayName: String,
    val icon: String,
    val description: String,
    val satisfactionBonus: Float,
    val reputationBonus: Long,
    val expenseMultiplier: Float,
    val dropoutReduction: Float
) {
    NONE("无课外活动", "🚫", "纯学术导向，无课外活动",
        -12f, -3L, 1.0f, 0f),
    MINIMAL("基本活动", "🎈", "只有必要的体育课和图书馆",
        0f, 0L, 1.02f, -0.005f),
    STANDARD("标准配置", "⚽", "常规社团和体育队",
        8f, 2L, 1.08f, -0.015f),
    RICH("丰富多彩", "🎭", "大量社团、竞赛、艺术项目",
        18f, 5L, 1.18f, -0.03f),
    WORLD_CLASS("世界级", "🏆", "国际竞赛、交换项目、创业孵化",
        28f, 10L, 1.35f, -0.05f)
}

/**
 * 招生策略
 */
enum class AdmissionPolicy(
    val displayName: String,
    val icon: String,
    val description: String,
    val enrollmentMultiplier: Float,
    val reputationModifier: Long
) {
    OPEN("完全开放", "🚪", "零门槛，来者不拒",
        1.6f, -5L),
    RELAXED("宽松招生", "😊", "基本要求，大部分都能入学",
        1.25f, -2L),
    BALANCED("均衡招生", "⚖️", "合理筛选，平衡质量和数量",
        1.0f, 0L),
    SELECTIVE("严格筛选", "🔍", "较高门槛，只收优秀学生",
        0.65f, 5L),
    ELITE("精英入学", "🏛️", "顶级门槛，只收天才学生",
        0.35f, 12L)
}

/**
 * 年度招生定位：在招生数量、生源质量与社会责任之间取舍。
 */
enum class EnrollmentPlan(
    val displayName: String,
    val icon: String,
    val description: String,
    val enrollmentMultiplier: Float,
    val qualityMultiplier: Float,
    val specialTalentMultiplier: Float,
    val welfareReputationBonus: Long
) {
    BALANCED("均衡招生", "⚖️", "平衡规模、质量和收入，适合常规经营。", 1.00f, 1.00f, 1.00f, 0L),
    SCALE_FIRST("规模扩张", "📈", "优先扩大生源规模，但平均生源质量略低。", 1.15f, 0.92f, 1.00f, -2L),
    QUALITY_FIRST("质量优先", "🎓", "减少招生数量，提升高潜力生源比例和毕业质量。", 0.80f, 1.15f, 1.00f, 8L),
    SPECIAL_TALENT("特长生优先", "🏅", "重点吸引艺术、体育和竞赛特长生。", 0.90f, 1.03f, 1.60f, 5L),
    PUBLIC_WELFARE("公益招生", "🤝", "扩大困难家庭学生机会，换取社会声誉和公共评价。", 0.95f, 0.98f, 1.00f, 15L)
}

/**
 * 年度办学方针：教学、科研、就业、扩张之间的长期取舍。
 */
enum class UniversityStrategy(
    val displayName: String,
    val icon: String,
    val description: String,
    val qualityMultiplier: Float,
    val enrollmentMultiplier: Float,
    val expenseMultiplier: Float,
    val reputationModifier: Long,
    val graduationQualityBonus: Float,
    val extraResearchDays: Int
) {
    BALANCED("均衡办学", "⚖️", "教学、科研、就业和扩张同步推进。", 1.00f, 1.00f, 1.00f, 0L, 0f, 0),
    TEACHING_FIRST("教学优先", "📚", "强化课堂质量，科研推进稍慢。", 1.12f, 0.97f, 1.04f, 4L, 0.04f, 0),
    RESEARCH_FIRST("科研优先", "🔬", "加快研究进度，日常教学略受挤压。", 0.94f, 0.96f, 1.08f, 6L, 0.02f, 1),
    EMPLOYMENT_FIRST("就业优先", "💼", "重视毕业出口和社会评价，招生更稳。", 1.02f, 1.04f, 1.03f, 8L, 0.06f, 0),
    EXPANSION_FIRST("扩张优先", "🏗️", "扩大规模和容量，短期质量承压。", 0.92f, 1.10f, 1.10f, -2L, -0.02f, 0)
}

/**
 * 年度专项预算：把有限点数分到教学、科研、校园生活和社会合作。
 * 总点数固定为 10，投入越高，对应线越强，月度专项开支也越高。
 */
data class BudgetAllocation(
    val teachingWeight: Int = 3,
    val researchWeight: Int = 2,
    val campusLifeWeight: Int = 3,
    val societyWeight: Int = 2
) {
    fun totalPoints(): Int = teachingWeight + researchWeight + campusLifeWeight + societyWeight

    fun normalized(): BudgetAllocation {
        val total = totalPoints().coerceAtLeast(1)
        if (total == TOTAL_POINTS) {
            return copy(
                teachingWeight = teachingWeight.coerceIn(0, TOTAL_POINTS),
                researchWeight = researchWeight.coerceIn(0, TOTAL_POINTS),
                campusLifeWeight = campusLifeWeight.coerceIn(0, TOTAL_POINTS),
                societyWeight = societyWeight.coerceIn(0, TOTAL_POINTS)
            )
        }
        val teaching = ((teachingWeight.toFloat() / total) * TOTAL_POINTS).toInt().coerceIn(0, TOTAL_POINTS)
        val research = ((researchWeight.toFloat() / total) * TOTAL_POINTS).toInt().coerceIn(0, TOTAL_POINTS - teaching)
        val campusLife = ((campusLifeWeight.toFloat() / total) * TOTAL_POINTS).toInt()
            .coerceIn(0, TOTAL_POINTS - teaching - research)
        val society = (TOTAL_POINTS - teaching - research - campusLife).coerceIn(0, TOTAL_POINTS)
        return BudgetAllocation(teaching, research, campusLife, society)
    }

    fun adjust(line: BudgetLine, delta: Int): BudgetAllocation {
        val current = when (line) {
            BudgetLine.TEACHING -> teachingWeight
            BudgetLine.RESEARCH -> researchWeight
            BudgetLine.CAMPUS_LIFE -> campusLifeWeight
            BudgetLine.SOCIETY -> societyWeight
        }
        val next = (current + delta).coerceIn(0, TOTAL_POINTS)
        val spentWithout = totalPoints() - current
        if (spentWithout + next > TOTAL_POINTS) return this
        return when (line) {
            BudgetLine.TEACHING -> copy(teachingWeight = next)
            BudgetLine.RESEARCH -> copy(researchWeight = next)
            BudgetLine.CAMPUS_LIFE -> copy(campusLifeWeight = next)
            BudgetLine.SOCIETY -> copy(societyWeight = next)
        }
    }

    fun monthlyCostWan(): Double = (totalPoints() * COST_PER_POINT).coerceAtLeast(0.0)

    fun extraResearchDays(): Int = if (researchWeight >= 4) 1 else 0

    fun qualityMultiplier(): Float = 0.94f + teachingWeight * 0.02f

    fun satisfactionModifier(): Float = (campusLifeWeight - 2) * 2.5f

    fun reputationModifier(): Long = ((societyWeight - 2) * 2).toLong()

    companion object {
        const val TOTAL_POINTS = 10
        const val COST_PER_POINT = 0.8
    }
}

enum class BudgetLine(val displayName: String, val description: String) {
    TEACHING("教学投入", "提高课堂质量和毕业成果"),
    RESEARCH("科研投入", "加快研究进度"),
    CAMPUS_LIFE("校园生活", "改善宿舍食堂和学生满意度"),
    SOCIETY("社会合作", "换取声誉和外部支持")
}

/**
 * 政策效果汇总
 */
data class PolicyEffects(
    val tuitionMultiplier: Float = 1f,
    val enrollmentMultiplier: Float = 1f,
    val qualityMultiplier: Float = 1f,
    val satisfactionModifier: Float = 0f,
    val reputationModifier: Long = 0L,
    val expenseMultiplier: Float = 1f,
    val dropoutRateModifier: Float = 0f,
    val graduationQualityBonus: Float = 0f,
    val enrollmentQualityMultiplier: Float = 1f,
    val specialTalentMultiplier: Float = 1f,
    val welfareReputationBonus: Long = 0L,
    val extraResearchDays: Int = 0,
    val strategyName: String = "均衡办学",
    val teachingFocus: Int = 3,
    val researchFocus: Int = 2,
    val campusLifeFocus: Int = 3,
    val societyFocus: Int = 2,
    val monthlySpecialBudgetCost: Double = 8.0,
    val collegeEnrollmentMultiplier: Float = 1f,
    val collegeQualityMultiplier: Float = 1f,
    val collegeSatisfactionModifier: Float = 0f,
    val collegeReputationModifier: Long = 0L,
    val collegeEmploymentBonus: Float = 0f,
    val annualGoalName: String = "均衡发展",
    val foundedCollegeNames: List<String> = emptyList()
)

@Serializable
data class PolicyPersistData(
    val tuitionLevel: String = "STANDARD",
    val examDifficulty: String = "MODERATE",
    val teacherPayPolicy: String = "COMPETITIVE",
    val extracurricularPolicy: String = "STANDARD",
    val admissionPolicy: String = "BALANCED",
    val enrollmentPlan: String = "BALANCED",
    val universityStrategy: String = "BALANCED",
    val teachingWeight: Int = 3,
    val researchWeight: Int = 2,
    val campusLifeWeight: Int = 3,
    val societyWeight: Int = 2,
    val foundedColleges: List<String> = emptyList(),
    val annualGoal: String = "BALANCED_GROWTH",
    val lastReviewYear: Int = 0,
    val lastReviewReputation: Long = 0L,
    val lastReviewResearch: Int = 0,
    val lastReviewStudents: Int = 0,
    val lastReviewSatisfaction: Float = 0f,
    val liberalTrackWeight: Int = 3,
    val scienceTrackWeight: Int = 3,
    val engineeringTrackWeight: Int = 2,
    val businessTrackWeight: Int = 2,
    val artsTrackWeight: Int = 1,
    val medicineTrackWeight: Int = 1,
    val competitionStateJson: String = "",
    val researchChainStateJson: String = "",
    val storyStateJson: String = "",
    val graduateStateJson: String = "",
    val internationalStateJson: String = "",
    val affiliatedHospital: Boolean = false,
    val coreCourses: Map<String, Int> = emptyMap(),
    val graduateProgram: Boolean = false,
    val foundingStyle: String = "BALANCED",
    val placedBuildings: String = "",
    val terrainMap: String = "",
    val tutorialDone: Boolean = false,
    val disciplinesJson: String = ""
)

data class ManagedCollegeResult(
    val success: Boolean,
    val message: String
)

data class AnnualGoalResult(
    val success: Boolean,
    val title: String,
    val detail: String,
    val reputationDelta: Long,
    val cashDelta: Double
)

data class CollegeDevelopment(
    val founded: List<CollegeType> = emptyList(),
    val foundingStyle: String = "BALANCED",
    val affiliatedHospital: Boolean = false,
    val coreCourses: Map<String, Int> = emptyMap(),
    val graduateProgram: Boolean = false,
    val annualGoal: AnnualGoal = AnnualGoal.BALANCED_GROWTH,
    val placedBuildings: String = "",
    val terrainMap: String = "",
    val tutorialDone: Boolean = false,
    val disciplinesJson: String = "",
    val lastReviewYear: Int = 0,
    val lastReviewReputation: Long = 0L,
    val lastReviewResearch: Int = 0,
    val lastReviewStudents: Int = 0,
    val lastReviewSatisfaction: Float = 0f
) {
    fun monthlyCostWan(): Double = founded.sumOf { it.monthlyCostWan }

    fun extraResearchDays(): Int = if (founded.contains(CollegeType.SCIENCE)) 1 else 0

    fun enrollmentMultiplier(): Float = 1f + founded.sumOf { it.enrollmentBonus.toDouble() }.toFloat()

    fun qualityMultiplier(): Float = 1f + founded.sumOf { it.qualityBonus.toDouble() }.toFloat()

    fun satisfactionModifier(): Float = founded.sumOf { it.satisfactionBonus.toDouble() }.toFloat()

    fun reputationModifier(): Long = founded.sumOf { it.reputationBonus }

    fun employmentBonus(): Float = founded.sumOf { it.employmentBonus.toDouble() }.toFloat()
}

enum class CollegeType(
    val displayName: String,
    val icon: String,
    val description: String,
    val unlockLevel: Int,
    val foundingCostWan: Double,
    val monthlyCostWan: Double,
    val enrollmentBonus: Float,
    val qualityBonus: Float,
    val satisfactionBonus: Float,
    val reputationBonus: Long,
    val employmentBonus: Float
) {
    LIBERAL_ARTS(
        "人文学院", "📖",
        "稳住基础招生和校园氛围，月费较低。",
        1, 18.0, 1.2, 0.05f, 0.02f, 2.0f, 2L, 0.00f
    ),
    SCIENCE(
        "理学院", "🔬",
        "加快科研，提高培养质量，日常开支上升。",
        2, 36.0, 2.4, 0.04f, 0.06f, 0.0f, 4L, 0.01f
    ),
    ENGINEERING(
        "工学院", "🛠️",
        "扩大就业出口和招生规模，建设成本最高。",
        3, 58.0, 3.6, 0.08f, 0.03f, 1.0f, 3L, 0.06f
    ),
    BUSINESS(
        "商学院", "💼",
        "换取社会声誉和产业合作，对满意度帮助有限。",
        4, 72.0, 4.2, 0.05f, 0.02f, -1.0f, 8L, 0.04f
    ),
    ARTS(
        "艺术学院", "🎨",
        "校园氛围与满意度显著提升，就业偏传媒与教育。",
        3, 65.0, 3.4, 0.03f, 0.02f, 4.0f, 4L, 0.02f
    ),
    MEDICINE(
        "医学院", "🩺",
        "成本最高但就业质量极好，附属医院带来声誉。",
        4, 110.0, 6.0, 0.02f, 0.05f, 0.0f, 6L, 0.08f
    )
}

enum class AnnualGoal(
    val displayName: String,
    val icon: String,
    val description: String
) {
    BALANCED_GROWTH("均衡发展", "⚖️", "学生规模、科研和满意度都要稳住，不追求单项爆发。"),
    RESEARCH_BREAKTHROUGH("科研突破", "🔬", "本学年科研项目必须明显增加。"),
    EMPLOYMENT_QUALITY("就业质量", "💼", "毕业出口和就业率必须达标。"),
    CAMPUS_LIFE("校园体验", "🏠", "学生满意度必须明显高于去年。"),
    SOCIAL_INFLUENCE("社会影响", "📣", "声誉必须明显高于去年。");

    fun evaluate(
        campusLevel: Int,
        students: Int,
        research: Int,
        reputation: Long,
        satisfaction: Float,
        employmentRate: Float,
        previousReputation: Long,
        previousResearch: Int,
        previousStudents: Int,
        previousSatisfaction: Float
    ): AnnualGoalResult {
        val firstYear = previousReputation == 0L && previousResearch == 0 && previousStudents == 0
        val passed = when (this) {
            BALANCED_GROWTH -> students >= 40 + campusLevel * 20 &&
                research >= campusLevel &&
                satisfaction >= 62f
            RESEARCH_BREAKTHROUGH -> if (firstYear) {
                research >= campusLevel + 1
            } else {
                research >= previousResearch + 1
            }
            EMPLOYMENT_QUALITY -> employmentRate >= (0.42f + campusLevel * 0.04f)
            CAMPUS_LIFE -> if (firstYear) {
                satisfaction >= 70f
            } else {
                satisfaction >= previousSatisfaction + 4f
            }
            SOCIAL_INFLUENCE -> if (firstYear) {
                reputation >= 80L + campusLevel * 40L
            } else {
                reputation >= previousReputation + 40L + campusLevel * 10L
            }
        }
        val detail = when (this) {
            BALANCED_GROWTH -> "在校生${students}人，科研${research}项，满意度${satisfaction.toInt()}%。"
            RESEARCH_BREAKTHROUGH -> "科研项目从${previousResearch}项到${research}项。"
            EMPLOYMENT_QUALITY -> "当前就业/升学率为${(employmentRate * 100).toInt()}%。"
            CAMPUS_LIFE -> "学生满意度从${previousSatisfaction.toInt()}%到${satisfaction.toInt()}%。"
            SOCIAL_INFLUENCE -> "社会声誉从${previousReputation}到${reputation}。"
        }
        return if (passed) {
            AnnualGoalResult(
                success = true,
                title = "学年目标达成",
                detail = "目标「$displayName」完成。$detail",
                reputationDelta = 18L + campusLevel * 4L,
                cashDelta = 8.0 + campusLevel * 4.0
            )
        } else {
            AnnualGoalResult(
                success = false,
                title = "学年目标未完成",
                detail = "目标「$displayName」未达标。$detail",
                reputationDelta = -8L - campusLevel * 2L,
                cashDelta = 0.0
            )
        }
    }
}
