package com.example.exercisetracker

data class RepResult(
    val exerciseName: String,
    val score: Int, // 0 to 100
    val feedback: List<String> = emptyList()
)

// Rejects "reps" that finish faster than anyone could really do them, which is usually landmark jitter
class RepTimer(private val minRepMs: Long = 400) {
    private var startMs = 0L

    fun start() {
        startMs = System.currentTimeMillis()
    }

    fun isTooFast(): Boolean = System.currentTimeMillis() - startMs < minRepMs
}

// Plank is tracked in seconds; show it as m:ss once it passes a minute
fun formatPlankTime(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "%d:%02d".format(seconds / 60, seconds % 60)

// Workout length as m:ss, or h:mm:ss once it passes an hour
fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}

class WorkoutSession {
    private val allReps = mutableListOf<RepResult>()

    fun addRep(rep: RepResult) {
        allReps.add(rep)
    }

    fun getAverageScore(): Int {
        if (allReps.isEmpty()) return 0
        return allReps.map { it.score }.average().toInt()
    }

    fun getRepsForExercise(name: String) = allReps.filter { it.exerciseName == name }
    
    fun getAllReps() = allReps

    fun calculateSessionXp(): Int {
        if (allReps.isEmpty()) return 0
        // Formula: 10 XP per rep * quality factor
        return allReps.sumOf { (10 * (it.score / 100.0)).toInt() }
    }
}
