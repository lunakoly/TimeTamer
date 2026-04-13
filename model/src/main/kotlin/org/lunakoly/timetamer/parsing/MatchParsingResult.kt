package org.lunakoly.timetamer.parsing

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

val zone = TimeZone.of("Europe/Nicosia")

fun LocalTime.formatTime(zone: TimeZone): String {
    val local = LocalDateTime(Clock.System.now().toLocalDateTime(zone).date, this)

    val h = local.hour
    val m = local.minute

    val hour12 = when {
        h == 0 -> 12
        h <= 12 -> h
        else -> h - 12
    }

    val ampm = if (h < 12) "am" else "pm"

    return "$hour12:${m.toString().padStart(2, '0')} $ampm"
}
