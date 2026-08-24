package com.arktools.xiaozhang.ui.utils

import java.text.DecimalFormat

/**
 * Centralized formatting utilities for consistent number display across the UI.
 */
object FormatUtils {

    private val cashFormat = DecimalFormat("#,##0.0")
    private val largeCashFormat = DecimalFormat("#,##0")
    private val percentFormat = DecimalFormat("0.0")
    private val intFormat = DecimalFormat("#,##0")

    /**
     * Format cash value with appropriate unit.
     * - < 1: "0.X万"
     * - 1-9999: "X.X万"
     * - 10000+: "X.X亿"
     */
    fun formatCash(value: Double): String {
        return when {
            value < 0 && value > -1 -> "-${cashFormat.format(-value)}万"
            value < 0 && value <= -10000 -> "-${cashFormat.format(-value / 10000)}亿"
            value < 0 -> "-${cashFormat.format(-value)}万"
            value >= 10000 -> "${cashFormat.format(value / 10000)}亿"
            value >= 1 -> "${cashFormat.format(value)}万"
            value > 0 -> "${cashFormat.format(value)}万"
            else -> "0万"
        }
    }

    /**
     * Format cash with color hint.
     * Returns pair of (formatted string, isPositive flag).
     */
    fun formatCashWithSign(value: Double): Pair<String, Boolean> {
        val prefix = if (value > 0) "+" else ""
        return "$prefix${formatCash(value)}" to (value >= 0)
    }

    /**
     * Format reputation value.
     * - < 10000: exact number "1,234"
     * - 10000+: "1.2万"
     * - 100000000+: "1.0亿"
     */
    fun formatReputation(value: Long): String {
        return when {
            value >= 100_000_000 -> "${cashFormat.format(value / 100_000_000.0)}亿"
            value >= 10_000 -> "${cashFormat.format(value / 10_000.0)}万"
            else -> intFormat.format(value)
        }
    }

    /**
     * Format enrollment/student count.
     */
    fun formatStudentCount(value: Long): String {
        return when {
            value >= 10_000 -> "${cashFormat.format(value / 10_000.0)}万人"
            else -> "${intFormat.format(value)}人"
        }
    }

    /**
     * Format percentage (0-100 scale).
     */
    fun formatPercent(value: Float): String {
        return "${percentFormat.format(value)}%"
    }

    /**
     * Format game date.
     */
    fun formatGameDate(year: Int, month: Int, day: Int): String {
        return "${year}年${month}月${day}日"
    }

    /**
     * Format time duration in game days.
     * - < 30: "X天"
     * - 30-360: "X个月"
     * - 360+: "X年X个月"
     */
    fun formatDuration(days: Int): String {
        return when {
            days < 30 -> "${days}天"
            days < 360 -> "${days / 30}个月${if (days % 30 > 0) "${days % 30}天" else ""}"
            else -> {
                val years = days / 360
                val months = (days % 360) / 30
                if (months > 0) "${years}年${months}个月" else "${years}年"
            }
        }
    }

    /**
     * Format a skill value with rating.
     */
    fun formatSkill(value: Int): String {
        return when {
            value >= 90 -> "$value (卓越)"
            value >= 70 -> "$value (优秀)"
            value >= 50 -> "$value (良好)"
            value >= 30 -> "$value (一般)"
            else -> "$value (较弱)"
        }
    }

    /**
     * Format course quality score.
     */
    fun formatQuality(score: Float): String {
        return when {
            score >= 9.0f -> "${percentFormat.format(score)}/10 优"
            score >= 7.0f -> "${percentFormat.format(score)}/10 良"
            score >= 5.0f -> "${percentFormat.format(score)}/10 中"
            else -> "${percentFormat.format(score)}/10"
        }
    }

    /**
     * Format teacher level with Chinese description.
     */
    fun formatTeacherLevel(level: com.arktools.xiaozhang.domain.model.TeacherLevel): String {
        return when (level) {
            com.arktools.xiaozhang.domain.model.TeacherLevel.C -> "C级 (初级)"
            com.arktools.xiaozhang.domain.model.TeacherLevel.B -> "B级 (中级)"
            com.arktools.xiaozhang.domain.model.TeacherLevel.A -> "A级 (高级)"
            com.arktools.xiaozhang.domain.model.TeacherLevel.S -> "S级 (顶尖)"
        }
    }
}
