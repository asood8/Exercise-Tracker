package com.example.exercisetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattingTest {

    @Test
    fun plankTimeSwitchesToMinutesAfterSixtySeconds() {
        assertEquals("45s", formatPlankTime(45))
        assertEquals("1:00", formatPlankTime(60))
        assertEquals("1:15", formatPlankTime(75))
    }

    @Test
    fun durationAddsHoursWhenNeeded() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("1:15", formatDuration(75))
        assertEquals("1:02:05", formatDuration(3725))
    }

    @Test
    fun exerciseLookupFallsBackSafely() {
        assertEquals(Exercise.PLANK, Exercise.fromName("PLANK"))
        assertEquals(Exercise.SQUATS, Exercise.fromName(null))
        assertEquals(Exercise.SQUATS, Exercise.fromName("NOT_AN_EXERCISE"))
        assertEquals(Exercise.PUSHUPS, Exercise.fromField("pushups"))
        assertNull(Exercise.fromField("burpees"))
    }

    @Test
    fun fieldNamesMatchTheFirestoreFields() {
        // Summary saves counts under these names and History reads them back through Workout
        assertEquals(
            listOf("pushups", "squats", "situps", "lunges", "curls", "overhead", "jacks", "plank"),
            Exercise.entries.map { it.fieldName }
        )
    }

    @Test
    fun targetsShowSecondsOnlyForTimedExercises() {
        assertEquals("12", Exercise.SQUATS.formatTarget(12))
        assertEquals("30s", Exercise.PLANK.formatTarget(30))
    }
}
