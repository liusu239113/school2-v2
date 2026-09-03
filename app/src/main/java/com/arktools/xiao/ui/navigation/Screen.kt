package com.arktools.xiao.ui.navigation

/**
 * 页面导航常量：替代散落在代码中的魔法数字。
 * 底部导航栏使用 0-4，子页面使用 5+。
 * 使用 object 常量而非 enum 以保持与现有 Int-based 导航的兼容性，
 * 后续可逐步迁移到 Navigation Compose。
 */
object Screen {
    // === 底部主导航 ===
    const val OVERVIEW = 0
    const val GOVERNANCE = 1
    const val TEACHER = 2
    const val RESEARCH = 3
    const val DISTRICT = 4

    // === 子页面 ===
    const val RANKING = 5
    const val STOCK = 6
    const val FACILITY = 7
    const val STUDENT = 8
    // 9 reserved
    const val ACHIEVEMENT = 10
    const val REPORT = 11
    const val MARKETING = 12
    const val EVENT = 13
    const val NOTIFICATION = 14
    const val ALUMNI = 15
    const val POLICY = 16
    const val CLUB = 17
    const val SEASONAL = 18
    // 19 reserved
    const val REPUTATION = 20
    const val STUDENT_LIFE = 21
    // 22 reserved
    const val CONFERENCE = 23
    // 24-26 reserved
    const val PARENT = 27
    const val GOVERNMENT = 28
    const val SCHOLARSHIP = 29
    // 30 reserved
    const val TIMETABLE = 31
    const val EXAM = 32
    const val PRINCIPAL_OFFICE = 33

    // === 扩展子页面（ CampusView/治院入口使用） ===
    const val SEASONAL_ACTIVITIES = 18
    const val CLUB_MANAGE = 17
    const val TEACHING_CONFIG = 40
    const val RESEARCH_LAB = 41
    const val DISCIPLINE = 45
    const val GRADUATE_SCHOOL = 46
    const val INTERNATIONAL = 47
    const val TEACHER_LIST = 48

    /** 底部导航最大索引，用于判断是否为子页面 */
    const val BOTTOM_NAV_MAX = 4

    fun isSubPage(tab: Int): Boolean = tab > BOTTOM_NAV_MAX
}
