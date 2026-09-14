package com.example.exercisetracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// One block of a plan: reach `target` reps (seconds for plank) of an exercise
data class PlanStep(val exercise: Exercise, val target: Int) {
    // "Push-ups × 10" or "Plank 30s"
    fun describe(): String =
        if (exercise.isTimed) "${exercise.displayName} ${target}s" else "${exercise.displayName} × $target"

    fun spoken(): String = "${exercise.displayName}, $target ${if (exercise.isTimed) "seconds" else "reps"}"
}

// A workout plan. The steps are done in order, `rounds` times over, with a rest after each one.
// A single-exercise goal like "3 × 15 squats" is a plan with one step and isGoal set, so goals and
// routines share the same code in MainActivity.
data class Routine(
    val name: String,
    val steps: List<PlanStep>,
    val rounds: Int,
    val restSeconds: Int,
    val isGoal: Boolean = false
) {
    // Every block in the order it's done, so 3 rounds of 2 steps is 6 blocks
    val blocks: List<PlanStep> = List(rounds) { steps }.flatten()

    fun roundOf(blockIndex: Int): Int = blockIndex / steps.size + 1

    // "3 × 15 Squats" for a goal, "Push-ups × 10 → Plank 30s, 3 rounds" for a routine
    fun describe(): String {
        if (isGoal) {
            val step = steps.first()
            return "$rounds × ${step.exercise.formatTarget(step.target)} ${step.exercise.displayName}"
        }
        val roundsText = if (rounds == 1) "1 round" else "$rounds rounds"
        return steps.joinToString(" → ") { it.describe() } + ", $roundsText"
    }

    // Whether the phone can stay in one place for the whole routine
    fun placementNote(): String = when {
        steps.all { it.exercise.sideOn } -> "Phone to your side the whole time"
        steps.none { it.exercise.sideOn } -> "Face the camera the whole time"
        else -> "You'll need to move the phone between some exercises"
    }

    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("rounds", rounds)
        put("rest", restSeconds)
        put("goal", isGoal)
        put("steps", JSONArray(steps.map { JSONObject().put("exercise", it.exercise.name).put("target", it.target) }))
    }.toString()

    companion object {
        private const val GOAL_REST_SECONDS = 60

        fun goal(exercise: Exercise, sets: Int, target: Int) =
            Routine("", listOf(PlanStep(exercise, target)), sets, GOAL_REST_SECONDS, isGoal = true)

        fun fromJson(json: String?): Routine? {
            if (json.isNullOrEmpty()) return null
            return try {
                val obj = JSONObject(json)
                val stepsJson = obj.getJSONArray("steps")
                val steps = (0 until stepsJson.length()).map { i ->
                    val step = stepsJson.getJSONObject(i)
                    PlanStep(Exercise.fromName(step.getString("exercise")), step.getInt("target"))
                }
                val routine = Routine(
                    obj.optString("name"), steps, obj.getInt("rounds"), obj.optInt("rest", GOAL_REST_SECONDS), obj.optBoolean("goal")
                )
                if (routine.blocks.isEmpty() || steps.any { it.target <= 0 }) null else routine
            } catch (e: JSONException) {
                null
            }
        }
    }
}

// Built-in routines, plus the user's own, which are kept on the device
object Routines {
    private const val PREFS = "Routines"
    private const val KEY_CUSTOM = "custom"
    const val MAX_STEPS = 8

    // Each preset keeps the phone in one place, so nobody has to move it mid-routine
    val presets = listOf(
        Routine("Full body", listOf(PlanStep(Exercise.SQUATS, 15), PlanStep(Exercise.JACKS, 20), PlanStep(Exercise.OVERHEAD, 12)), rounds = 3, restSeconds = 30),
        Routine("Floor work", listOf(PlanStep(Exercise.PUSHUPS, 10), PlanStep(Exercise.SITUPS, 15), PlanStep(Exercise.PLANK, 30)), rounds = 3, restSeconds = 45),
        Routine("Arms", listOf(PlanStep(Exercise.CURLS, 12), PlanStep(Exercise.OVERHEAD, 10)), rounds = 3, restSeconds = 45),
        Routine("Core", listOf(PlanStep(Exercise.SITUPS, 15), PlanStep(Exercise.PLANK, 45)), rounds = 3, restSeconds = 45)
    )

    fun all(context: Context): List<Routine> = presets + custom(context)

    fun isPreset(name: String): Boolean = presets.any { it.name == name }

    fun nameTaken(context: Context, name: String): Boolean = all(context).any { it.name.equals(name, ignoreCase = true) }

    fun custom(context: Context): List<Routine> {
        val json = prefs(context).getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { Routine.fromJson(array.getString(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun saveCustom(context: Context, routine: Routine) {
        store(context, custom(context).filter { it.name != routine.name } + routine)
    }

    fun deleteCustom(context: Context, name: String) {
        store(context, custom(context).filter { it.name != name })
    }

    private fun store(context: Context, routines: List<Routine>) {
        prefs(context).edit().putString(KEY_CUSTOM, JSONArray(routines.map { it.toJson() }).toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
