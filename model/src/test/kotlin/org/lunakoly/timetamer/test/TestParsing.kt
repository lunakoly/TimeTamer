package org.lunakoly.timetamer.test

import kotlinx.datetime.LocalTime
import org.lunakoly.timetamer.parsing.CompositeTimerFinder
import org.lunakoly.timetamer.parsing.TimeFinder
import org.lunakoly.timetamer.parsing.en.EnglishTimeFinder
import org.lunakoly.timetamer.parsing.formatTime
import org.lunakoly.timetamer.parsing.ru.RussianTimeFinder
import org.lunakoly.timetamer.parsing.zone
import kotlin.test.Test
import kotlin.test.assertEquals

class TestParsing {
    fun String.assertParsing(timeFinder: TimeFinder, vararg expected: LocalTime?) {
        val filtered = expected.filterNotNull()
        val results = timeFinder.findTime(this)
        assertEquals(filtered.size, results.size)

        filtered.zip(results).forEach { (expected, actual) ->
            assertEquals(expected.formatTime(zone), actual.time.formatTime(zone))
        }
    }

    fun String.assertEnglish(expected: LocalTime?) = assertParsing(EnglishTimeFinder, expected)
    fun String.assertRussian(expected: LocalTime?) = assertParsing(RussianTimeFinder, expected)

    @Test
    fun testEnglish() {
        "3 am".assertEnglish(LocalTime(3, 0))
        "5 pm".assertEnglish(LocalTime(17, 0))
        "at 7:20".assertEnglish(LocalTime(19, 20))
        "20".assertEnglish(null)
        "at 2:00 am".assertEnglish(LocalTime(2, 0))
        "at 2.30 p,".assertEnglish(LocalTime(14, 30))
        "before 2.30".assertEnglish(LocalTime(14, 30))
        "after 2.30".assertEnglish(LocalTime(14, 30))
    }

    @Test
    fun testRussian() {
        "3 утра".assertRussian(LocalTime(3, 0))
        "5 дня".assertRussian(LocalTime(17, 0))
        "6 вечера".assertRussian(LocalTime(18, 0))
        "2 ночи".assertRussian(LocalTime(2, 0))
        "в 7".assertRussian(LocalTime(19, 0))
        "в 4.30".assertRussian(LocalTime(16, 30))
        "4".assertRussian(null)
        "утром в 2".assertRussian(LocalTime(2, 0))
        "днем в 3".assertRussian(LocalTime(15, 0))
        "вечером в 3:15".assertRussian(LocalTime(15, 15))
        "ночью в 1:15".assertRussian(LocalTime(1, 15))
        "после 1:15".assertRussian(LocalTime(13, 15))
        "до 1:15".assertRussian(LocalTime(13, 15))
    }

    @Test
    fun testIntermixed() {
        "3 am, в 3 вечера, ночью в 1".assertParsing(
            CompositeTimerFinder.WITH_ALL_LANGUAGES,
            LocalTime(3, 0),
            LocalTime(15, 0),
            LocalTime(1, 0),
        )
    }
}
