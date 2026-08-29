package com.example.exercisetracker

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String, // Emoji or resource name
    val requirement: (totalReps: Int, maxStreak: Int, avgQuality: Int) -> Boolean
)

object AchievementManager {
    val allAchievements = listOf(
        Achievement("first_step", "First Step", "Complete your first workout", "👟") { reps, _, _ -> reps > 0 },
        Achievement("streak_3", "Consistency", "Reach a 3-day streak", "🔥") { _, streak, _ -> streak >= 3 },
        Achievement("rep_100", "Centurion", "Perform 100 total reps", "💯") { reps, _, _ -> reps >= 100 },
        Achievement("perfect_form", "Perfectionist", "Average quality over 95%", "⭐") { _, _, quality -> quality >= 95 },
        Achievement("calorie_king", "Burner", "Burn 1000 total calories", "🔋") { _, _, _ -> false } // Placeholder for custom logic
    )

    fun getUnlockedAchievements(totalReps: Int, maxStreak: Int, avgQuality: Int): List<Achievement> {
        return allAchievements.filter { it.requirement(totalReps, maxStreak, avgQuality) }
    }
}
