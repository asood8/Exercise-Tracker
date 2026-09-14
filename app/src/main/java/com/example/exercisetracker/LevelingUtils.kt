package com.example.exercisetracker

import kotlin.math.floor
import kotlin.math.sqrt

object LevelingUtils {
    fun getLevelFromXp(totalXp: Int): Int {
        return floor(sqrt(totalXp.toDouble() / 100.0)).toInt() + 1
    }

    fun getXpForNextLevel(currentLevel: Int): Int {
        return (currentLevel * currentLevel) * 100
    }

    fun getXpProgressInLevel(totalXp: Int): Int {
        val currentLevel = getLevelFromXp(totalXp)
        val xpAtStartOfLevel = if (currentLevel == 1) 0 else getXpForNextLevel(currentLevel - 1)
        return totalXp - xpAtStartOfLevel
    }

    fun getRequiredXpForCurrentLevel(currentLevel: Int): Int {
        val xpAtStartOfLevel = if (currentLevel == 1) 0 else getXpForNextLevel(currentLevel - 1)
        val xpAtNextLevel = getXpForNextLevel(currentLevel)
        return xpAtNextLevel - xpAtStartOfLevel
    }

    // XP for one saved workout: 10 per rep (plank counts one rep per 10 seconds), scaled by the form
    // score. Workouts without a score count at half value.
    fun xpForWorkout(workout: Workout): Int {
        val quality = if (workout.overallScore == -1) 0.5 else workout.overallScore / 100.0
        return ((workout.totalReps + workout.plank / 10) * 10 * quality).toInt()
    }

    // Hexagon Stat Formulas (Muscle Group Progression)
    // 0: Arms, 1: Chest, 2: Legs, 3: Abs, 4: Shoulders, 5: Back
    fun getMuscleProgress(reps: Int): Float {
        // Logarithmic scale: easy to start, very hard to max
        // maxes out around 10,000 reps for a value of 1.0
        if (reps <= 0) return 0.05f // small visible starting point
        val progress = (Math.log10(reps.toDouble()) / 4.0).toFloat()
        return progress.coerceIn(0.05f, 1.0f)
    }
}
