package com.example.exercisetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun stats(
        workoutCount: Int = 0,
        totalReps: Int = 0,
        avgQuality: Int = 0,
        scoredWorkouts: Int = 0,
        longestPlank: Int = 0
    ) = AchievementStats(
        workoutCount = workoutCount,
        totalReps = totalReps,
        totalCalories = 0.0,
        totalSeconds = 0,
        longestStreak = 0,
        avgQuality = avgQuality,
        scoredWorkouts = scoredWorkouts,
        bestScore = 0,
        longestPlank = longestPlank,
        level = 1,
        exercisesTried = 0
    )

    private fun unlockedIds(stats: AchievementStats) =
        AchievementManager.getUnlockedAchievements(stats).map { it.id }

    @Test
    fun nothingIsUnlockedAtTheStart() {
        assertTrue(unlockedIds(stats()).isEmpty())
    }

    @Test
    fun firstWorkoutUnlocksFirstStep() {
        assertEquals(listOf("first_step"), unlockedIds(stats(workoutCount = 1, totalReps = 50)))
    }

    @Test
    fun perfectFormNeedsFiveScoredWorkouts() {
        assertTrue("perfect_form" !in unlockedIds(stats(workoutCount = 4, avgQuality = 97, scoredWorkouts = 4)))
        assertTrue("perfect_form" in unlockedIds(stats(workoutCount = 5, avgQuality = 97, scoredWorkouts = 5)))
    }

    @Test
    fun twoMinutePlankUnlocksRockSolid() {
        assertTrue("plank_120" !in unlockedIds(stats(longestPlank = 119)))
        assertTrue("plank_120" in unlockedIds(stats(longestPlank = 120)))
    }

    @Test
    fun idsAreUnique() {
        // Seen achievements are stored by id, so a duplicate would hide one of them
        val ids = AchievementManager.allAchievements.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
