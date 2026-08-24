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
class SchoolPolicyManager @Inject constructor() {

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
     * 获取所有政策的综合效果
     */
    fun getPolicyEffects(): PolicyEffects {
        val p = _policies.value
        return PolicyEffects(
            tuitionMultiplier = p.tuitionLevel.revenueMultiplier,
            enrollmentMultiplier = calculateEnrollmentMultiplier(p),
            qualityMultiplier = calculateQualityMultiplier(p),
            satisfactionModifier = calculateSatisfactionModifier(p),
            reputationModifier = calculateReputationModifier(p),
            expenseMultiplier = calculateExpenseMultiplier(p),
            dropoutRateModifier = calculateDropoutModifier(p),
            graduationQualityBonus = calculateGraduationBonus(p)
        )
    }

    private fun calculateEnrollmentMultiplier(p: SchoolPolicies): Float {
        var mult = 1f
        // 学费越高，报名意愿越低
        mult *= p.tuitionLevel.enrollmentMultiplier
        // 奖学金招生加成已在 ScholarshipManager.getEnrollmentBonus() 中应用
        // 宽松招生提高数量
        mult *= p.admissionPolicy.enrollmentMultiplier
        return mult
    }

    private fun calculateQualityMultiplier(p: SchoolPolicies): Float {
        var mult = 1f
        // 教师薪资影响教学质量
        mult *= p.teacherPayPolicy.qualityBonus
        // 考试难度影响教学严格程度
        mult *= p.examDifficulty.qualityMultiplier
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
        return mod
    }

    private fun calculateExpenseMultiplier(p: SchoolPolicies): Float {
        var mult = 1f
        // 教师薪资倍率已在 GameEngine.deductMonthlyExpenses 中单独应用于薪资部分
        // 课外活动开销
        mult *= p.extracurricularPolicy.expenseMultiplier
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
        return p.examDifficulty.graduationBonus
    }

    fun resetToDefaults() {
        _policies.value = SchoolPolicies()
    }

    fun toJson(): String {
        return try {
            val p = _policies.value
            val data = PolicyPersistData(
                tuitionLevel = p.tuitionLevel.name,
                examDifficulty = p.examDifficulty.name,
                teacherPayPolicy = p.teacherPayPolicy.name,
                extracurricularPolicy = p.extracurricularPolicy.name,
                admissionPolicy = p.admissionPolicy.name
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
                admissionPolicy = try { AdmissionPolicy.valueOf(data.admissionPolicy) } catch (_: Exception) { AdmissionPolicy.BALANCED }
            )
            _policies.value = restoredPolicies
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
    val admissionPolicy: AdmissionPolicy = AdmissionPolicy.BALANCED
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
    val graduationQualityBonus: Float = 0f
)

@Serializable
data class PolicyPersistData(
    val tuitionLevel: String = "STANDARD",
    val examDifficulty: String = "MODERATE",
    val teacherPayPolicy: String = "COMPETITIVE",
    val extracurricularPolicy: String = "STANDARD",
    val admissionPolicy: String = "BALANCED"
)
