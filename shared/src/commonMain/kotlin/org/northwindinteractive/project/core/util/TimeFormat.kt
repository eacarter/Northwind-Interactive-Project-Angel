package org.northwindinteractive.project.core.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/** Local wall-clock time, e.g. "10:24 PM". */
fun Instant.formatTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = toLocalDateTime(timeZone)
    val hour12 = when (val h = local.hour % 12) { 0 -> 12; else -> h }
    val minute = local.minute.toString().padStart(2, '0')
    val suffix = if (local.hour < 12) "AM" else "PM"
    return "$hour12:$minute $suffix"
}

/** e.g. "27 Nov, 10:24 PM". */
fun Instant.formatDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = toLocalDateTime(timeZone)
    val month = MONTHS[local.month.ordinal]
    return "${local.day} $month, ${formatTime(timeZone)}"
}

/** Coarse age for observation labels, e.g. "just now", "3 min ago", "2 h ago". */
fun Duration.formatAge(): String = when {
    inWholeSeconds < 45 -> "just now"
    inWholeMinutes < 60 -> "${inWholeMinutes.coerceAtLeast(1)} min ago"
    else -> "${inWholeHours} h ago"
}

/** Remaining time, e.g. "4 h 12 min". */
fun Duration.formatRemaining(): String {
    val hours = inWholeHours
    val minutes = inWholeMinutes % 60
    return when {
        hours > 0 -> "$hours h ${minutes} min"
        inWholeMinutes > 0 -> "$minutes min"
        else -> "less than a minute"
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
