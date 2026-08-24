package com.arktools.xiaozhang.domain.model

/**
 * Subject differentiation: each subject has unique properties affecting gameplay.
 * - difficulty: affects preparation time and teacher skill requirements
 * - popularity: base demand multiplier
 * - costMultiplier: material and equipment costs
 * - synergies: subjects that benefit from being taught together
 */
object SubjectConfig {

    data class SubjectProfile(
        val difficulty: Float,        // 0.5 (easy) to 2.0 (hard)
        val popularity: Float,        // 0.5 (niche) to 2.0 (mainstream)
        val costMultiplier: Float,    // equipment/material cost factor
        val teacherDemand: Float,     // how hard to find good teachers (hiring cost)
        val synergies: List<Subject>  // subjects that boost each other
    )

    private val profiles = mapOf(
        Subject.CHINESE to SubjectProfile(
            difficulty = 1.0f,
            popularity = 1.8f,
            costMultiplier = 0.8f,
            teacherDemand = 1.0f,
            synergies = listOf(Subject.HISTORY, Subject.POLITICS)
        ),
        Subject.MATH to SubjectProfile(
            difficulty = 1.5f,
            popularity = 2.0f,
            costMultiplier = 0.7f,
            teacherDemand = 1.3f,
            synergies = listOf(Subject.PHYSICS, Subject.CHEMISTRY)
        ),
        Subject.ENGLISH to SubjectProfile(
            difficulty = 1.2f,
            popularity = 1.9f,
            costMultiplier = 1.0f,
            teacherDemand = 1.2f,
            synergies = listOf(Subject.HISTORY, Subject.GEOGRAPHY)
        ),
        Subject.PHYSICS to SubjectProfile(
            difficulty = 1.8f,
            popularity = 1.4f,
            costMultiplier = 1.3f,
            teacherDemand = 1.5f,
            synergies = listOf(Subject.MATH, Subject.CHEMISTRY)
        ),
        Subject.CHEMISTRY to SubjectProfile(
            difficulty = 1.6f,
            popularity = 1.3f,
            costMultiplier = 1.5f,
            teacherDemand = 1.4f,
            synergies = listOf(Subject.PHYSICS, Subject.BIOLOGY)
        ),
        Subject.BIOLOGY to SubjectProfile(
            difficulty = 1.3f,
            popularity = 1.2f,
            costMultiplier = 1.2f,
            teacherDemand = 1.1f,
            synergies = listOf(Subject.CHEMISTRY, Subject.PE)
        ),
        Subject.HISTORY to SubjectProfile(
            difficulty = 1.0f,
            popularity = 1.1f,
            costMultiplier = 0.6f,
            teacherDemand = 0.9f,
            synergies = listOf(Subject.CHINESE, Subject.POLITICS, Subject.GEOGRAPHY)
        ),
        Subject.GEOGRAPHY to SubjectProfile(
            difficulty = 1.1f,
            popularity = 1.0f,
            costMultiplier = 0.8f,
            teacherDemand = 0.9f,
            synergies = listOf(Subject.HISTORY, Subject.BIOLOGY)
        ),
        Subject.POLITICS to SubjectProfile(
            difficulty = 0.8f,
            popularity = 0.9f,
            costMultiplier = 0.5f,
            teacherDemand = 0.8f,
            synergies = listOf(Subject.HISTORY, Subject.CHINESE)
        ),
        Subject.ART to SubjectProfile(
            difficulty = 1.0f,
            popularity = 1.3f,
            costMultiplier = 1.4f,
            teacherDemand = 1.2f,
            synergies = listOf(Subject.MUSIC, Subject.PE)
        ),
        Subject.PE to SubjectProfile(
            difficulty = 0.7f,
            popularity = 1.5f,
            costMultiplier = 1.6f,
            teacherDemand = 0.8f,
            synergies = listOf(Subject.BIOLOGY, Subject.ART)
        ),
        Subject.MUSIC to SubjectProfile(
            difficulty = 0.9f,
            popularity = 1.2f,
            costMultiplier = 1.3f,
            teacherDemand = 1.1f,
            synergies = listOf(Subject.ART, Subject.ENGLISH)
        )
    )

    fun getProfile(subject: Subject): SubjectProfile {
        return profiles[subject] ?: SubjectProfile(1.0f, 1.0f, 1.0f, 1.0f, emptyList())
    }

    /**
     * Calculate synergy bonus when the school teaches multiple related subjects.
     * Returns a multiplier (1.0 = no bonus, up to 1.3 with full synergies).
     */
    fun calculateSynergyBonus(subject: Subject, allActiveSubjects: Set<Subject>): Float {
        val profile = getProfile(subject)
        val matchedSynergies = profile.synergies.count { it in allActiveSubjects }
        return 1.0f + matchedSynergies * 0.1f  // +10% per synergy match
    }

    /**
     * Calculate design score for a course based on theme-subject compatibility,
     * course type fit, and scale ambition.
     * Returns 1.0 to 10.0.
     */
    fun calculateDesignScore(
        subject: Subject,
        theme: CourseTheme,
        courseType: CourseType,
        scale: CourseScale,
        teacherAvgSkill: Float
    ): Float {
        // Theme-Subject compatibility (core design quality)
        val themeSubjectFit = getThemeSubjectFit(subject, theme)

        // Course type appropriateness (online for tech, offline for art, etc.)
        val typeFit = getCourseTypeFit(subject, courseType)

        // Scale ambition matches teacher capability
        val scaleFit = getScaleTeacherFit(scale, teacherAvgSkill)

        // Weighted combination
        val rawScore = themeSubjectFit * 0.4f + typeFit * 0.3f + scaleFit * 0.3f
        return (rawScore * 10f).coerceIn(1.0f, 10.0f)
    }

    private fun getThemeSubjectFit(subject: Subject, theme: CourseTheme): Float {
        // High compatibility pairs
        val goodCombos = mapOf(
            Subject.MATH to listOf(CourseTheme.EXAM_PREP, CourseTheme.COMPETITION, CourseTheme.STEM),
            Subject.PHYSICS to listOf(CourseTheme.COMPETITION, CourseTheme.STEM, CourseTheme.PRACTICAL),
            Subject.CHEMISTRY to listOf(CourseTheme.COMPETITION, CourseTheme.PRACTICAL, CourseTheme.STEM),
            Subject.BIOLOGY to listOf(CourseTheme.PRACTICAL, CourseTheme.STEM, CourseTheme.INTEREST),
            Subject.ENGLISH to listOf(CourseTheme.INTERNATIONAL, CourseTheme.EXAM_PREP, CourseTheme.INTEREST),
            Subject.CHINESE to listOf(CourseTheme.TRADITIONAL, CourseTheme.EXAM_PREP, CourseTheme.CREATIVE),
            Subject.HISTORY to listOf(CourseTheme.TRADITIONAL, CourseTheme.INTERNATIONAL, CourseTheme.INTEREST),
            Subject.GEOGRAPHY to listOf(CourseTheme.INTERNATIONAL, CourseTheme.PRACTICAL, CourseTheme.INTEREST),
            Subject.POLITICS to listOf(CourseTheme.EXAM_PREP, CourseTheme.TRADITIONAL),
            Subject.ART to listOf(CourseTheme.CREATIVE, CourseTheme.ARTISTIC, CourseTheme.INTEREST),
            Subject.PE to listOf(CourseTheme.SPORTS, CourseTheme.INTEREST, CourseTheme.PRACTICAL),
            Subject.MUSIC to listOf(CourseTheme.CREATIVE, CourseTheme.ARTISTIC, CourseTheme.INTEREST)
        )

        val bestThemes = goodCombos[subject] ?: emptyList()
        return when {
            theme in bestThemes -> 0.85f + (bestThemes.indexOf(theme) * -0.05f)  // 0.85, 0.80, 0.75
            else -> 0.55f  // neutral fit
        }
    }

    private fun getCourseTypeFit(subject: Subject, courseType: CourseType): Float {
        return when {
            // Art subjects (美术/音乐) need offline small class
            subject.category == SubjectCategory.ART && courseType == CourseType.OFFLINE_SMALL -> 0.9f
            // Sports need offline large
            subject == Subject.PE && courseType == CourseType.OFFLINE_LARGE -> 0.85f
            // One-on-one suits high difficulty
            courseType == CourseType.ONE_ON_ONE && getProfile(subject).difficulty >= 1.5f -> 0.85f
            // Large offline for popular subjects
            courseType == CourseType.OFFLINE_LARGE && getProfile(subject).popularity >= 1.5f -> 0.8f
            else -> 0.65f  // acceptable but not optimal
        }
    }

    private fun getScaleTeacherFit(scale: CourseScale, teacherAvgSkill: Float): Float {
        // Higher scale requires better teachers
        val requiredSkill = when (scale) {
            CourseScale.INTEREST -> 30f
            CourseScale.IMPROVEMENT -> 45f
            CourseScale.COMPETITION -> 60f
            CourseScale.FULL_TIME -> 70f
            CourseScale.INTERNATIONAL -> 80f
        }
        // If teachers exceed requirement → bonus; if below → penalty
        val ratio = teacherAvgSkill / requiredSkill
        return ratio.coerceIn(0.4f, 1.0f)
    }
}
