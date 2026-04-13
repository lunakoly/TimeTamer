package org.lunakoly.timetamer.parsing

import kotlinx.datetime.LocalTime

data class TimeWithMessage(
    val time: LocalTime,
    val message: String,
    val match: MatchResult,
)

interface TimeFinder {
    fun findTime(input: String): List<TimeWithMessage>
}

interface RegexBasedTimeFinder : TimeFinder {
    val timePattern: Regex

    fun parseMatch(match: MatchResult): TimeWithMessage?

    override fun findTime(input: String): List<TimeWithMessage> =
        timePattern.findAll(input).mapNotNullTo(mutableListOf(), ::parseMatch)
}

class CompositeTimerFinder(val timeFinders: List<TimeFinder>) : TimeFinder {
    override fun findTime(input: String): List<TimeWithMessage> =
        timeFinders.flatMap { it.findTime(input) }
            .groupBy { it.time }
            .mapValues { it.value.first() }
            .values
            .toList()
            .sortedBy { it.match.range.first }

    companion object {
        val WITH_ALL_LANGUAGES = CompositeTimerFinder(
            timeFinders = listOf(
                org.lunakoly.timetamer.parsing.ru.RussianTimeFinder,
                org.lunakoly.timetamer.parsing.en.EnglishTimeFinder,
            )
        )
    }
}
