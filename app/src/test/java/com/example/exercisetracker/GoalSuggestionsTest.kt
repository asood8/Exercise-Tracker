package com.example.exercisetracker

import com.example.exercisetracker.Exercise.PLANK
import com.example.exercisetracker.Exercise.PUSHUPS
import com.example.exercisetracker.Exercise.SQUATS
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalSuggestionsTest {

    private fun session(
        counts: Map<Exercise, Int>,
        scores: Map<Exercise, Int> = emptyMap(),
        overallScore: Int = -1,
        goal: PastGoal? = null
    ) = PastSession(timeMs = 0L, counts = counts, scores = scores, overallScore = overallScore, goal = goal)

    private fun assertSuggestion(sets: Int, target: Int, suggestion: GoalSuggestion) {
        assertEquals("sets", sets, suggestion.sets)
        assertEquals("target", target, suggestion.target)
    }

    @Test
    fun noHistorySuggestsTheStarterGoal() {
        assertSuggestion(3, PUSHUPS.starterTarget, GoalSuggestions.suggest(PUSHUPS, emptyList()))
    }

    @Test
    fun finishedGoalWithGoodFormStepsUp() {
        val last = session(mapOf(SQUATS to 36), mapOf(SQUATS to 91), goal = PastGoal(SQUATS, 3, 12, setsDone = 3))
        assertSuggestion(3, 13, GoalSuggestions.suggest(SQUATS, listOf(last)))
    }

    @Test
    fun largerTargetsStepUpByAboutTenPercent() {
        val last = session(mapOf(SQUATS to 90), mapOf(SQUATS to 85), goal = PastGoal(SQUATS, 3, 30, setsDone = 3))
        assertSuggestion(3, 33, GoalSuggestions.suggest(SQUATS, listOf(last)))
    }

    @Test
    fun finishedGoalWithPoorFormIsRepeated() {
        val last = session(mapOf(SQUATS to 36), mapOf(SQUATS to 60), goal = PastGoal(SQUATS, 3, 12, setsDone = 3))
        assertSuggestion(3, 12, GoalSuggestions.suggest(SQUATS, listOf(last)))
    }

    @Test
    fun oneSetShortIsRepeated() {
        val last = session(mapOf(SQUATS to 24), mapOf(SQUATS to 90), goal = PastGoal(SQUATS, 3, 12, setsDone = 2))
        assertSuggestion(3, 12, GoalSuggestions.suggest(SQUATS, listOf(last)))
    }

    @Test
    fun wellShortStepsDown() {
        val last = session(mapOf(PUSHUPS to 20), goal = PastGoal(PUSHUPS, 3, 20, setsDone = 1))
        assertSuggestion(3, 17, GoalSuggestions.suggest(PUSHUPS, listOf(last)))
    }

    @Test
    fun steppingDownNeverGoesBelowTheMinimum() {
        val last = session(mapOf(PUSHUPS to 2), goal = PastGoal(PUSHUPS, 3, 5, setsDone = 0))
        assertSuggestion(3, 5, GoalSuggestions.suggest(PUSHUPS, listOf(last)))
    }

    @Test
    fun plankTargetsMoveInFiveSecondSteps() {
        val last = session(mapOf(PLANK to 90), mapOf(PLANK to 90), goal = PastGoal(PLANK, 3, 30, setsDone = 3))
        assertSuggestion(3, 35, GoalSuggestions.suggest(PLANK, listOf(last)))
    }

    @Test
    fun withoutAGoalLastSessionIsSplitIntoThreeSets() {
        val last = session(mapOf(SQUATS to 30))
        assertSuggestion(3, 11, GoalSuggestions.suggest(SQUATS, listOf(last)))
    }

    @Test
    fun usesTheMostRecentSessionWithThatExercise() {
        val newest = session(mapOf(PUSHUPS to 10))
        val older = session(mapOf(SQUATS to 30), mapOf(SQUATS to 80), goal = PastGoal(SQUATS, 3, 10, setsDone = 3))
        assertSuggestion(3, 11, GoalSuggestions.suggest(SQUATS, listOf(newest, older)))
    }

    @Test
    fun aGoalForAnotherExerciseIsIgnored() {
        val last = session(mapOf(SQUATS to 30, PUSHUPS to 30), goal = PastGoal(PUSHUPS, 3, 10, setsDone = 3))
        assertSuggestion(3, 11, GoalSuggestions.suggest(SQUATS, listOf(last)))
    }

    @Test
    fun overallScoreOnlyCountsWhenItWasTheOnlyExercise() {
        val goal = PastGoal(SQUATS, 3, 12, setsDone = 3)
        // A low overall score from a mixed workout says nothing about squat form, so it steps up
        val mixed = session(mapOf(SQUATS to 36, PUSHUPS to 10), overallScore = 50, goal = goal)
        assertSuggestion(3, 13, GoalSuggestions.suggest(SQUATS, listOf(mixed)))
        // From a squat-only workout it does, so the goal is repeated
        val squatsOnly = session(mapOf(SQUATS to 36), overallScore = 50, goal = goal)
        assertSuggestion(3, 12, GoalSuggestions.suggest(SQUATS, listOf(squatsOnly)))
    }
}
