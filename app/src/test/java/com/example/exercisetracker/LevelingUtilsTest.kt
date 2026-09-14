package com.example.exercisetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelingUtilsTest {

    @Test
    fun levelsStartAtOneAndGrowWithTheSquareRootOfXp() {
        assertEquals(1, LevelingUtils.getLevelFromXp(0))
        assertEquals(1, LevelingUtils.getLevelFromXp(99))
        assertEquals(2, LevelingUtils.getLevelFromXp(100))
        assertEquals(2, LevelingUtils.getLevelFromXp(399))
        assertEquals(3, LevelingUtils.getLevelFromXp(400))
        assertEquals(11, LevelingUtils.getLevelFromXp(10_000))
    }

    @Test
    fun progressIsMeasuredFromTheStartOfTheLevel() {
        // Level 2 runs from 100 to 400 XP
        assertEquals(50, LevelingUtils.getXpProgressInLevel(150))
        assertEquals(300, LevelingUtils.getRequiredXpForCurrentLevel(2))
        assertEquals(100, LevelingUtils.getRequiredXpForCurrentLevel(1))
    }

    @Test
    fun workoutXpIsTenPerRepScaledByForm() {
        assertEquals(80, LevelingUtils.xpForWorkout(Workout(squats = 10, overallScore = 80)))
    }

    @Test
    fun unscoredWorkoutsCountAtHalfValue() {
        assertEquals(50, LevelingUtils.xpForWorkout(Workout(squats = 10, overallScore = -1)))
    }

    @Test
    fun plankCountsOneRepPerTenSeconds() {
        assertEquals(30, LevelingUtils.xpForWorkout(Workout(plank = 30, overallScore = 100)))
    }

    @Test
    fun muscleProgressIsLogarithmicAndClamped() {
        assertEquals(0.05f, LevelingUtils.getMuscleProgress(0), 0.0001f)
        assertEquals(0.5f, LevelingUtils.getMuscleProgress(100), 0.0001f)
        assertEquals(1.0f, LevelingUtils.getMuscleProgress(10_000), 0.0001f)
        assertEquals(1.0f, LevelingUtils.getMuscleProgress(1_000_000), 0.0001f)
    }

    @Test
    fun totalRepsLeavesOutPlank() {
        assertEquals(15, Workout(pushups = 5, squats = 10, plank = 60).totalReps)
    }
}
