package com.arktools.xiao.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arktools.xiao.R

/**
 * Emoji-to-pixel-icon mapping.
 * Maps all emoji strings used in the game to their pixel art drawable equivalents.
 */
object EmojiIcons {
    private val emojiToDrawable: Map<String, Int> = mapOf(
        // Academic / Education
        "🎓" to R.drawable.ic_graduation,
        "📚" to R.drawable.ic_books,
        "📖" to R.drawable.ic_books,
        "📝" to R.drawable.ic_memo,
        "📋" to R.drawable.ic_clipboard,
        "🔬" to R.drawable.ic_research,
        "🧪" to R.drawable.ic_research,
        "💡" to R.drawable.ic_idea,
        "🧠" to R.drawable.ic_brain,

        // Financial / Money
        "💰" to R.drawable.ic_money,
        "💸" to R.drawable.ic_money,
        "💲" to R.drawable.ic_money,
        "🏦" to R.drawable.ic_bank,

        // Buildings / Campus
        "🏫" to R.drawable.ic_school,
        "🏠" to R.drawable.ic_house,
        "🏢" to R.drawable.ic_office,
        "🏛️" to R.drawable.ic_government,
        "🏛" to R.drawable.ic_government,
        "🏗️" to R.drawable.ic_construction,
        "🏗" to R.drawable.ic_construction,
        "🏟️" to R.drawable.ic_stadium,
        "🏟" to R.drawable.ic_stadium,
        "🏘️" to R.drawable.ic_house,
        "🏘" to R.drawable.ic_house,
        "🏚️" to R.drawable.ic_house,
        "🏚" to R.drawable.ic_house,

        // Awards / Achievement
        "🏆" to R.drawable.ic_trophy,
        "⭐" to R.drawable.ic_star,
        "🌟" to R.drawable.ic_star,
        "🥇" to R.drawable.ic_medal_gold,
        "🥈" to R.drawable.ic_medal_silver,
        "🥉" to R.drawable.ic_medal_bronze,
        "🏅" to R.drawable.ic_medal_gold,
        "🎖️" to R.drawable.ic_medal_gold,
        "🎖" to R.drawable.ic_medal_gold,
        "👑" to R.drawable.ic_crown,
        "💎" to R.drawable.ic_gem,

        // People / Social
        "👥" to R.drawable.ic_people,
        "👤" to R.drawable.ic_people,
        "👨‍🎓" to R.drawable.ic_graduation,
        "👩‍🏫" to R.drawable.ic_people,
        "👨‍🏫" to R.drawable.ic_people,
        "👨‍👩‍👧‍👦" to R.drawable.ic_people,
        "🤝" to R.drawable.ic_handshake,
        "👋" to R.drawable.ic_people,
        "👇" to R.drawable.ic_point_down,
        "😊" to R.drawable.ic_heart,

        // Trends / Charts
        "📈" to R.drawable.ic_trend_up,
        "📉" to R.drawable.ic_trend_down,
        "📊" to R.drawable.ic_chart,
        "⬆️" to R.drawable.ic_arrow_up,

        // Arts / Entertainment
        "🎨" to R.drawable.ic_art,
        "🎵" to R.drawable.ic_music,
        "🎤" to R.drawable.ic_music,
        "🎭" to R.drawable.ic_theater,
        "🎬" to R.drawable.ic_theater,
        "🎪" to R.drawable.ic_activity,

        // Sports
        "⚽" to R.drawable.ic_sports,
        "🤺" to R.drawable.ic_sports,

        // Notification / Status
        "⚠️" to R.drawable.ic_warning,
        "⚠" to R.drawable.ic_warning,
        "✅" to R.drawable.ic_check,
        "❌" to R.drawable.ic_cross,
        "🚫" to R.drawable.ic_cross,
        "🚨" to R.drawable.ic_alert,
        "🔥" to R.drawable.ic_fire,

        // Seasons
        "🌸" to R.drawable.ic_spring,
        "☀️" to R.drawable.ic_summer,
        "☀" to R.drawable.ic_summer,
        "🍂" to R.drawable.ic_autumn,
        "❄️" to R.drawable.ic_winter,
        "❄" to R.drawable.ic_winter,

        // Communication
        "📢" to R.drawable.ic_megaphone,
        "📣" to R.drawable.ic_megaphone,
        "📌" to R.drawable.ic_pin,
        "📩" to R.drawable.ic_megaphone,
        "📭" to R.drawable.ic_megaphone,
        "📰" to R.drawable.ic_newspaper,

        // Time
        "⏳" to R.drawable.ic_hourglass,
        "⌛" to R.drawable.ic_hourglass,

        // Misc
        "💻" to R.drawable.ic_computer,
        "🎯" to R.drawable.ic_target,
        "🔍" to R.drawable.ic_search,
        "💪" to R.drawable.ic_power,
        "🚪" to R.drawable.ic_door,
        "🎁" to R.drawable.ic_gift,
        "🎉" to R.drawable.ic_celebration,
        "🎈" to R.drawable.ic_balloon,
        "💼" to R.drawable.ic_briefcase,
        "📅" to R.drawable.ic_calendar,
        "📆" to R.drawable.ic_calendar,
        "🌳" to R.drawable.ic_tree,
        "🌍" to R.drawable.ic_globe,
        "💥" to R.drawable.ic_explosion,
        "🏥" to R.drawable.ic_hospital,
        "🍽️" to R.drawable.ic_food,
        "🍽" to R.drawable.ic_food,
        "⚖️" to R.drawable.ic_balance,
        "⚖" to R.drawable.ic_balance,
        "⚙️" to R.drawable.ic_construction,
        "⚙" to R.drawable.ic_construction,
        "💉" to R.drawable.ic_hospital,
        "💊" to R.drawable.ic_hospital,
        "🛒" to R.drawable.ic_briefcase,
        "♟️" to R.drawable.ic_brain,
        "♟" to R.drawable.ic_brain,
        "📛" to R.drawable.ic_alert,

        // Hearts / Colors as moods
        "💚" to R.drawable.ic_heart,
        "💙" to R.drawable.ic_heart,
        "💛" to R.drawable.ic_heart,
        "⚪" to R.drawable.ic_heart,

        // Navigation / UI
        "▶" to R.drawable.ic_arrow_up,
        "➡️" to R.drawable.ic_arrow_up,
    )

    /**
     * Get the drawable resource ID for an emoji string.
     * Returns null if no mapping exists.
     */
    fun getDrawableRes(emoji: String): Int? {
        return emojiToDrawable[emoji.trim()]
    }

    /**
     * Replace emoji in a string with empty string (for text-only contexts).
     * Use when emoji is a prefix like "🎓 课程开课" -> "课程开课"
     */
    fun stripEmoji(text: String): String {
        var result = text
        emojiToDrawable.keys.sortedByDescending { it.length }.forEach { emoji ->
            result = result.replace(emoji, "")
        }
        return result.trim()
    }

    /**
     * Extract leading emoji from a string.
     * Returns Pair(emoji, remainingText) or null if no leading emoji.
     */
    fun extractLeadingEmoji(text: String): Pair<String, String>? {
        val trimmed = text.trim()
        // Try longer sequences first (multi-codepoint emoji)
        emojiToDrawable.keys.sortedByDescending { it.length }.forEach { emoji ->
            if (trimmed.startsWith(emoji)) {
                return emoji to trimmed.removePrefix(emoji).trim()
            }
        }
        return null
    }
}

/**
 * A pixel art icon composable that renders the mapped drawable for a given emoji.
 * Falls back to rendering the emoji as text if no mapping exists.
 */
@Composable
fun PixelIcon(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    contentDescription: String? = null
) {
    val drawableRes = EmojiIcons.getDrawableRes(emoji)
    if (drawableRes != null) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription ?: emoji,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Displays text with a leading pixel icon (replacing emoji prefix).
 * Example: "🎓 课程开课" -> [icon] 课程开课
 */
@Composable
fun PixelIconText(
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    textContent: @Composable (String) -> Unit
) {
    val extracted = EmojiIcons.extractLeadingEmoji(text)
    if (extracted != null) {
        val (emoji, remaining) = extracted
        androidx.compose.foundation.layout.Row(
            modifier = modifier,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            PixelIcon(emoji = emoji, size = iconSize)
            textContent(remaining)
        }
    } else {
        textContent(text)
    }
}
