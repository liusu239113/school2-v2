package com.arktools.xiaozhang.domain.engine

/**
 * 大学学年节奏：招生、迎新、科研、就业和毕业共同驱动经营循环。
 * 每月仍然推进一次模拟日历，保留一代的季节活动与结算框架。
 */
object SemesterCalendar {

    enum class Season(val displayName: String) {
        SPRING_SEMESTER("春季学期"),
        SUMMER_BREAK("暑期学期"),
        FALL_SEMESTER("秋季学期"),
        WINTER_BREAK("寒假")
    }

    enum class SchoolEvent(val displayName: String) {
        ENROLLMENT_SEASON("招生与迎新"),
        MIDTERM_EXAM("期中教学检查"),
        FINAL_EXAM("期末教学评估"),
        GRADUATION("毕业季"),
        SUMMER_CAMP("暑期项目"),
        PARENT_MEETING("校友与家委会"),
        SPORTS_DAY("校园联赛"),
        SCIENCE_FAIR("科研成果展"),
        NEW_YEAR("年度规划会"),
        NORMAL("日常办学")
    }

    fun getSeason(month: Int): Season {
        return when (month) {
            in 2..6 -> Season.SPRING_SEMESTER
            in 7..8 -> Season.SUMMER_BREAK
            in 9..12 -> Season.FALL_SEMESTER
            1 -> Season.WINTER_BREAK
            else -> Season.SPRING_SEMESTER
        }
    }

    fun getMonthlyEvent(month: Int): SchoolEvent {
        return when (month) {
            1 -> SchoolEvent.NEW_YEAR
            2 -> SchoolEvent.ENROLLMENT_SEASON
            3 -> SchoolEvent.NORMAL
            4 -> SchoolEvent.MIDTERM_EXAM
            5 -> SchoolEvent.PARENT_MEETING
            6 -> SchoolEvent.FINAL_EXAM
            7 -> SchoolEvent.GRADUATION
            8 -> SchoolEvent.SUMMER_CAMP
            9 -> SchoolEvent.ENROLLMENT_SEASON
            10 -> SchoolEvent.SPORTS_DAY
            11 -> SchoolEvent.MIDTERM_EXAM
            12 -> SchoolEvent.SCIENCE_FAIR
            else -> SchoolEvent.NORMAL
        }
    }

    fun getEnrollmentMultiplier(month: Int): Float {
        return when (getMonthlyEvent(month)) {
            SchoolEvent.ENROLLMENT_SEASON -> 2.0f
            SchoolEvent.SUMMER_CAMP -> 0.7f
            SchoolEvent.NEW_YEAR -> 0.3f
            SchoolEvent.GRADUATION -> 0.8f
            SchoolEvent.FINAL_EXAM, SchoolEvent.MIDTERM_EXAM -> 0.7f
            else -> 1.0f
        }
    }

    fun getReputationBonus(month: Int): Long {
        return when (getMonthlyEvent(month)) {
            SchoolEvent.GRADUATION -> 280
            SchoolEvent.SCIENCE_FAIR -> 180
            SchoolEvent.SPORTS_DAY -> 100
            SchoolEvent.FINAL_EXAM -> 70
            SchoolEvent.MIDTERM_EXAM -> 40
            else -> 0
        }
    }

    fun isOnBreak(month: Int): Boolean {
        val season = getSeason(month)
        return season == Season.SUMMER_BREAK || season == Season.WINTER_BREAK
    }
}
