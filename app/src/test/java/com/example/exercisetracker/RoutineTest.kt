package com.example.exercisetracker

import com.example.exercisetracker.Exercise.CURLS
import com.example.exercisetracker.Exercise.PLANK
import com.example.exercisetracker.Exercise.PUSHUPS
import com.example.exercisetracker.Exercise.SQUATS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineTest {

    private val routine = Routine(
        name = "Test",
        steps = listOf(PlanStep(PUSHUPS, 10), PlanStep(PLANK, 30)),
        rounds = 3,
        restSeconds = 45
    )

    @Test
    fun blocksRepeatTheStepsForEachRound() {
        assertEquals(6, routine.blocks.size)
        assertEquals(listOf(PUSHUPS, PLANK, PUSHUPS, PLANK, PUSHUPS, PLANK), routine.blocks.map { it.exercise })
    }

    @Test
    fun roundOfCountsFromOne() {
        assertEquals(1, routine.roundOf(0))
        assertEquals(1, routine.roundOf(1))
        assertEquals(2, routine.roundOf(2))
        assertEquals(3, routine.roundOf(5))
    }

    @Test
    fun goalsDescribeThemselvesAsSetsTimesTarget() {
        assertEquals("3 × 15 Squats", Routine.goal(SQUATS, 3, 15).describe())
        assertEquals("2 × 45s Plank", Routine.goal(PLANK, 2, 45).describe())
    }

    @Test
    fun routinesDescribeTheirSteps() {
        assertEquals("Push-ups × 10 → Plank 30s, 3 rounds", routine.describe())
    }

    @Test
    fun placementNoteSaysWhetherThePhoneMoves() {
        assertEquals("Phone to your side the whole time", routine.placementNote())
        val facing = Routine("Facing", listOf(PlanStep(SQUATS, 10), PlanStep(CURLS, 10)), 2, 30)
        assertEquals("Face the camera the whole time", facing.placementNote())
        val mixed = Routine("Mixed", listOf(PlanStep(SQUATS, 10), PlanStep(PUSHUPS, 10)), 2, 30)
        assertEquals("You'll need to move the phone between some exercises", mixed.placementNote())
    }

    @Test
    fun presetsKeepThePhoneInOnePlace() {
        for (preset in Routines.presets) {
            assertFalse(preset.name, preset.placementNote().contains("move"))
        }
    }

    @Test
    fun jsonRoundTripKeepsEverything() {
        assertEquals(routine, Routine.fromJson(routine.toJson()))
        val goal = Routine.goal(SQUATS, 3, 15)
        assertEquals(goal, Routine.fromJson(goal.toJson()))
    }

    @Test
    fun badJsonGivesNull() {
        assertNull(Routine.fromJson(null))
        assertNull(Routine.fromJson(""))
        assertNull(Routine.fromJson("not json"))
        assertNull(Routine.fromJson("""{"name":"x","rounds":3,"steps":[]}"""))
        assertNull(Routine.fromJson("""{"name":"x","rounds":3,"steps":[{"exercise":"SQUATS","target":0}]}"""))
        assertNull(Routine.fromJson("""{"name":"x","rounds":0,"steps":[{"exercise":"SQUATS","target":10}]}"""))
    }
}
