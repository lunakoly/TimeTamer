package org.lunakoly.timetamer.parsing.ru

import kotlinx.datetime.LocalTime
import org.lunakoly.timetamer.parsing.RegexBasedTimeFinder
import org.lunakoly.timetamer.parsing.TimeWithMessage
import org.lunakoly.timetamer.parsing.formatTime
import org.lunakoly.timetamer.parsing.zone

fun logError(message: String) = null.also { println("Error > $message") }

object RussianTimeFinder : RegexBasedTimeFinder {
    override val timePattern = """(?:^|[^а-яА-Я0-9])(?:(?:(утром|днем|вечером|ночью)\s+)?((?:в|до|после)\s*))?(\d{1,2})(?:[:.](\d{2}))?\s*(утра|дня|вечера|ночи)?(?:$|[^а-яА-Я0-9])"""
        .toRegex(RegexOption.IGNORE_CASE)

    override fun parseMatch(match: MatchResult): TimeWithMessage? {
        val prefix = match.groupValues[1].lowercase()
        val prefixIn = match.groupValues[2].lowercase()
        val hour = match.groupValues[3].toIntOrNull()
            ?: return logError("No hours in `${match.value}`, how did it even match?")
        val minute = match.groupValues[4].toIntOrNull()
        val suffix = match.groupValues[5].lowercase()

        if (prefix == "" && suffix == "" && prefixIn == "") {
            return null
        }

        val correction = when {
            hour > 12 || prefix == "утром" || prefix == "ночью" || suffix == "утра" || suffix == "ночи" -> 0
            else -> 12
        }
        val result = LocalTime(hour + correction, minute ?: 0)

        val message = when {
            prefix != "" && suffix != "" -> "Не понял, но пусть будет: " + result.formatTime(zone)
            else -> "По-православному: " + result.formatTime(zone)
        }

        return TimeWithMessage(result, message, match)
    }
}
