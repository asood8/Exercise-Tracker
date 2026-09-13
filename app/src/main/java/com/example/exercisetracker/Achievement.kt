package com.example.exercisetracker

// Lifetime numbers the achievements are checked against. HistoryActivity builds this from the
// user's workouts.
data class AchievementStats(
    val workoutCount: Int,
    val totalReps: Int,
    val totalCalories: Double,
    val totalSeconds: Int,
    val longestStreak: Int,
    val avgQuality: Int,
    val scoredWorkouts: Int,
    val bestScore: Int,
    val longestPlank: Int, // seconds
    val level: Int,
    val exercisesTried: Int // How many of the eight exercises have been done at least once
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String, // Emoji
    val requirement: (AchievementStats) -> Boolean
)

object AchievementManager {
    // The ids are saved to remember which unlocks have already been shown, so don't change them
    val allAchievements = listOf(
        Achievement("first_step", "First Step", "Complete your first workout", "👟") { it.workoutCount >= 1 },
        Achievement("rep_100", "Centurion", "Do 100 reps in total", "💯") { it.totalReps >= 100 },
        Achievement("rep_1000", "Thousand Club", "Do 1,000 reps in total", "🏋️") { it.totalReps >= 1000 },
        Achievement("streak_3", "Consistency", "Work out 3 days in a row", "🔥") { it.longestStreak >= 3 },
        Achievement("streak_7", "Week Warrior", "Work out 7 days in a row", "📅") { it.longestStreak >= 7 },
        Achievement("workouts_10", "Regular", "Complete 10 workouts", "🎯") { it.workoutCount >= 10 },
        Achievement("workouts_50", "Dedicated", "Complete 50 workouts", "🏅") { it.workoutCount >= 50 },
        Achievement("calorie_king", "Burner", "Burn 1,000 calories in total", "🔋") { it.totalCalories >= 1000 },
        Achievement("perfect_form", "Perfectionist", "Average a 95% form score over at least 5 workouts", "⭐") {
            it.scoredWorkouts >= 5 && it.avgQuality >= 95
        },
        Achievement("flawless", "Flawless", "Score 100% on a workout", "💎") { it.bestScore >= 100 },
        Achievement("plank_120", "Rock Solid", "Hold a plank for 2 minutes", "🧱") { it.longestPlank >= 120 },
        Achievement("level_10", "Double Digits", "Reach level 10", "🚀") { it.level >= 10 },
        Achievement("all_rounder", "All-Rounder", "Try all 8 exercises", "🌈") { it.exercisesTried >= 8 },
        Achievement("hour_total", "Hour of Power", "Log 60 minutes of workouts", "⏱️") { it.totalSeconds >= 3600 }
    )

    fun getUnlockedAchievements(stats: AchievementStats): List<Achievement> =
        allAchievements.filter { it.requirement(stats) }
}
