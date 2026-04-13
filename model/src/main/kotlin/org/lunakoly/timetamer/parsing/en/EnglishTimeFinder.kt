package org.lunakoly.timetamer.parsing.en

import kotlinx.datetime.LocalTime
import org.lunakoly.timetamer.parsing.RegexBasedTimeFinder
import org.lunakoly.timetamer.parsing.TimeWithMessage
import org.lunakoly.timetamer.parsing.formatTime
import org.lunakoly.timetamer.parsing.zone

fun logError(message: String) = null.also { println("Error > $message") }

object EnglishTimeFinder : RegexBasedTimeFinder {
    override val timePattern: Regex = """\b(at\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b"""
        .toRegex(RegexOption.IGNORE_CASE)

    override fun parseMatch(match: MatchResult): TimeWithMessage? {
        val prefixAt = match.groupValues[1].lowercase()
        val hour = match.groupValues[2].toIntOrNull()
            ?: return logError("No hours in `${match.value}`, how did it even match?")
        val minute = match.groupValues[3].toIntOrNull()
        val suffix = match.groupValues[4].lowercase()

        val correction = when {
            prefixAt == "" && suffix == "" -> return null
            suffix != "am" -> 12
            else -> 0
        }
        val result = LocalTime(hour + correction, minute ?: 0)

        return TimeWithMessage(result, "That is: " + result.formatTime(zone), match)
    }
}
