package com.example.exercisetracker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StreaksTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun useZoneWithDaylightSaving() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private val today get() = Streaks.today()

    @Test
    fun consecutiveDaysEndingTodayAreAStreak() {
        assertEquals(3, Streaks.current(setOf(today, today - 1, today - 2)))
    }

    @Test
    fun streakStillCountsBeforeTodaysWorkout() {
        assertEquals(2, Streaks.current(setOf(today - 1, today - 2)))
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        assertEquals(1, Streaks.current(setOf(today, today - 2, today - 3)))
    }

    @Test
    fun noRecentWorkoutsMeansNoStreak() {
        assertEquals(0, Streaks.current(setOf(today - 5)))
        assertEquals(0, Streaks.current(emptySet()))
    }

    @Test
    fun longestFindsTheBestRun() {
        assertEquals(3, Streaks.longest(setOf(1L, 2L, 3L, 7L, 8L, 10L)))
        assertEquals(1, Streaks.longest(setOf(5L)))
        assertEquals(0, Streaks.longest(emptySet()))
    }

    @Test
    fun dayNumbersSurviveDaylightSavingChanges() {
        fun at(month: Int, day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
            clear()
            set(2026, month, day, hour, minute)
        }.timeInMillis

        // Clocks go forward on March 8, 2026 in New York, so these are only 23 hours apart
        val saturdayNight = Streaks.dayNumber(at(Calendar.MARCH, 7, 23, 30))
        val sundayNight = Streaks.dayNumber(at(Calendar.MARCH, 8, 23, 30))
        assertEquals(1L, sundayNight - saturdayNight)

        // Both ends of the short day are still the same day
        assertEquals(Streaks.dayNumber(at(Calendar.MARCH, 8, 0, 30)), sundayNight)

        // And the 25-hour day when clocks go back on November 1
        val before = Streaks.dayNumber(at(Calendar.OCTOBER, 31, 0, 30))
        val after = Streaks.dayNumber(at(Calendar.NOVEMBER, 1, 23, 30))
        assertEquals(1L, after - before)
    }
}
