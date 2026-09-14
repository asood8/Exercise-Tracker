package com.example.exercisetracker

private val FULL_BODY = listOf(11, 12, 23, 24, 25, 26, 27, 28)
private val UPPER_BODY = listOf(11, 12, 13, 14, 15, 16, 23, 24)

// The exercise being tracked in a workout. The enum name is what gets passed to MainActivity and
// saved in prefs, so don't rename entries. requiredLandmarks is what the setup check waits to see
// before the countdown starts. sideOn means the phone goes to the user's side rather than in front.
// starterTarget is the per-set goal suggested to someone who has never done the exercise.
enum class Exercise(
    val displayName: String,
    val setupTip: String,
    val requiredLandmarks: List<Int>,
    val sideOn: Boolean,
    val starterTarget: Int,
    val isTimed: Boolean = false
) {
    PUSHUPS("Push-ups", "Put the phone to your side so your whole body is in view.", FULL_BODY, sideOn = true, starterTarget = 8),
    SQUATS("Squats", "Face the camera with your whole body in view.", FULL_BODY, sideOn = false, starterTarget = 12),
    SITUPS("Sit-ups", "Put the phone to your side and lie down with your whole body in view.", listOf(0, 11, 12, 23, 24, 25, 26), sideOn = true, starterTarget = 12),
    LUNGES("Lunges", "Stand side-on to the camera with your whole body in view.", FULL_BODY, sideOn = true, starterTarget = 10),
    CURLS("Curls", "Face the camera with both arms and your hips in view.", UPPER_BODY, sideOn = false, starterTarget = 12),
    OVERHEAD("Overhead Press", "Face the camera with everything from your hips up in view.", UPPER_BODY, sideOn = false, starterTarget = 10),
    JACKS("Jumping Jacks", "Face the camera with your whole body in view.", FULL_BODY, sideOn = false, starterTarget = 20),
    PLANK("Plank", "Put the phone to your side so your whole body is in view.", FULL_BODY, sideOn = true, starterTarget = 30, isTimed = true);

    // The Firestore field this exercise's count is saved under ("pushups", "plank", ...). Per-exercise
    // form scores and saved goals use the same names.
    val fieldName: String get() = name.lowercase()

    // "12" for reps, "30s" for plank
    fun formatTarget(target: Int): String = if (isTimed) "${target}s" else target.toString()

    companion object {
        fun fromName(name: String?): Exercise = entries.firstOrNull { it.name == name } ?: SQUATS

        fun fromField(field: String?): Exercise? = entries.firstOrNull { it.fieldName == field }
    }
}
