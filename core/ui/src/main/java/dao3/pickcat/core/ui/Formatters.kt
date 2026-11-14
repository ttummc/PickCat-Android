package dao3.pickcat.core.ui

import dao3.pickcat.core.network.Avatar
import dao3.pickcat.core.network.PICKCAT_ORIGIN
import dao3.pickcat.core.network.SessionUser
import dao3.pickcat.core.network.UserProfile
import dao3.pickcat.core.network.UserRef
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun absoluteUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("//")) return "https:$value"
    return PICKCAT_ORIGIN + if (value.startsWith("/")) value else "/$value"
}

fun avatarUrl(avatar: Avatar?): String? = absoluteUrl(avatar?.url)
fun avatarUrl(user: UserRef?): String? = absoluteUrl(user?.avatar?.url)
fun avatarUrl(user: UserProfile?): String? = absoluteUrl(user?.avatar?.url)
fun avatarUrl(user: SessionUser?): String? = absoluteUrl(user?.avatar?.url)

fun initials(name: String?): String {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return "?"
    val first = trimmed.first()
    return first.uppercaseChar().toString()
}

fun parseInstant(input: String?): Instant? {
    if (input.isNullOrBlank()) return null
    return runCatching { Instant.parse(input) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(input).toInstant() }.getOrNull()
}

fun relativeTime(input: String?): String {
    val instant = parseInstant(input) ?: return ""
    val now = Instant.now()
    val seconds = Duration.between(instant, now).seconds
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3_600} 小时前"
        seconds < 86_400 * 30 -> "${seconds / 86_400} 天前"
        else -> {
            val date = instant.atZone(ZoneId.systemDefault())
            val nowDate = now.atZone(ZoneId.systemDefault())
            if (date.year == nowDate.year) {
                "${date.monthValue} 月 ${date.dayOfMonth} 日"
            } else {
                "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日"
            }
        }
    }
}

fun formatDateTime(input: String?): String {
    val instant = parseInstant(input) ?: return ""
    return instant.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
}

fun compactNumber(value: Int?): String {
    val n = value ?: 0
    return when {
        n < 0 -> "0"
        n < 1_000 -> n.toString()
        n < 10_000 -> "${trimZero(n / 1000.0)}k"
        else -> "${trimZero(n / 10_000.0)}w"
    }
}

private fun trimZero(value: Double): String =
    String.format(Locale.US, "%.1f", value).removeSuffix(".0")

fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 6 -> "夜深了"
        hour < 12 -> "早上好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    }
}

fun errorText(error: Throwable?): String {
    if (error == null) return "加载失败"
    val message = error.message?.trim().orEmpty()
    return message.ifEmpty { "加载失败，请稍后重试" }
}

private val EmojiShortcode = Regex("(?i)\\[emoji:[^\\]]+]")

/** Removes raw emoji shortcode placeholders from server content before rendering it. */
fun cleanDisplayText(input: String?): String {
    if (input.isNullOrBlank()) return ""
    return input
        .replace(EmojiShortcode, "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
}
