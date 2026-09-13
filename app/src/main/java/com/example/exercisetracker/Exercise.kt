package com.example.exercisetracker

private val FULL_BODY = listOf(11, 12, 23, 24, 25, 26, 27, 28)
private val UPPER_BODY = listOf(11, 12, 13, 14, 15, 16, 23, 24)

// The exercise being tracked in a workout. The enum name is what gets passed to MainActivity and
// saved in prefs, so don't rename entries. requiredLandmarks is what the setup check waits to see
// before the countdown starts.
enum class Exercise(
    val displayName: String,
    val setupTip: String,
    val requiredLandmarks: List<Int>,
    val isTimed: Boolean = false
) {
    PUSHUPS("Push-ups", "Put the phone to your side so your whole body is in view.", FULL_BODY),
    SQUATS("Squats", "Face the camera with your whole body in view.", FULL_BODY),
    SITUPS("Sit-ups", "Put the phone to your side and lie down with your whole body in view.", listOf(0, 11, 12, 23, 24, 25, 26)),
    LUNGES("Lunges", "Stand side-on to the camera with your whole body in view.", FULL_BODY),
    CURLS("Curls", "Face the camera with both arms and your hips in view.", UPPER_BODY),
    OVERHEAD("Overhead Press", "Face the camera with everything from your hips up in view.", UPPER_BODY),
    JACKS("Jumping Jacks", "Face the camera with your whole body in view.", FULL_BODY),
    PLANK("Plank", "Put the phone to your side so your whole body is in view.", FULL_BODY, isTimed = true);

    companion object {
        fun fromName(name: String?): Exercise = entries.firstOrNull { it.name == name } ?: SQUATS
    }
}

// A "3 × 15" style goal for the exercise picked on Home. target is reps per set, or seconds per
// set for plank.
data class WorkoutGoal(val exercise: Exercise, val sets: Int, val target: Int) {
    fun describe(): String {
        val unit = if (exercise.isTimed) "s" else ""
        return "$sets × $target$unit ${exercise.displayName}"
    }
}
