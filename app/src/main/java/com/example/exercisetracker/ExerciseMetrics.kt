package com.example.exercisetracker

data class RepResult(
    val exerciseName: String,
    val score: Int, // 0 to 100
    val feedback: List<String> = emptyList()
)

// Plank is tracked in seconds; show it as m:ss once it passes a minute
fun formatPlankTime(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "%d:%02d".format(seconds / 60, seconds % 60)

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
