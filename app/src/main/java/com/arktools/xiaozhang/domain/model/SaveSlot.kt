package com.arktools.xiaozhang.domain.model

data class SaveSlot(
    val id: Int,
    val schoolName: String,
    val saveTime: Long,
    val currentYear: Int,
    val currentMonth: Int,
    val cash: Double,
    val reputation: Long,
    val isAutoSave: Boolean,
    val isEmpty: Boolean
) {
    val displayName: String
        get() = if (isEmpty) "空存档" else schoolName

    val displayDate: String
        get() = if (isEmpty) "" else "${currentYear}年${currentMonth}月"

    val displayTime: String
        get() = if (isEmpty) "" else {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(saveTime))
        }
}