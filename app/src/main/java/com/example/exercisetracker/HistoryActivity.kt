package com.example.exercisetracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit

class HistoryActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var workoutAdapter: WorkoutAdapter
    private val workoutList = mutableListOf<Workout>()
    private lateinit var achievementAdapter: AchievementAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerViews()
        setupNavigation()
        fetchLifetimeStats()
    }

    private fun setupRecyclerViews() {
        // Workouts RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.workoutRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        workoutAdapter = WorkoutAdapter(workoutList) { workout ->
            showDeleteConfirmation(workout)
        }
        recyclerView.adapter = workoutAdapter

        // Achievements RecyclerView (adding a horizontal one at the top)
        // I will dynamically add a RecyclerView to the XML or layout programmatically
    }

    private fun showDeleteConfirmation(workout: Workout) {
        AlertDialog.Builder(this)
            .setTitle("Delete Workout?")
            .setMessage("This will permanently remove this session and update your lifetime totals.")
            .setPositiveButton("Delete") { _, _ -> deleteWorkout(workout) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteWorkout(workout: Workout) {
        if (workout.id.isEmpty()) return
        db.collection("workouts").document(workout.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Workout deleted", Toast.LENGTH_SHORT).show()
                fetchLifetimeStats()
            }
    }

    private fun setupNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.selectedItemId = R.id.nav_history

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_leaderboard -> {
                    startActivity(Intent(this, LeaderboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> true
                else -> false
            }
        }
    }

    private fun fetchLifetimeStats() {
        val user = auth.currentUser ?: return

        db.collection("workouts")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                workoutList.clear()
                var totalCurls = 0
                var totalPushups = 0
                var totalSquats = 0
                var totalSitups = 0
                var totalOverhead = 0
                var totalJacks = 0
                var totalLunges = 0
                var totalPlank = 0
                var totalCalories = 0.0
                var totalXp = 0
                var scoreSum = 0
                var validScoreCount = 0
                val workoutDates = mutableSetOf<Long>()

                for (doc in documents) {
                    val workout = doc.toObject(Workout::class.java).copy(id = doc.id)
                    workoutList.add(workout)

                    totalCurls += workout.curls
                    totalPushups += workout.pushups
                    totalSquats += workout.squats
                    totalSitups += workout.situps
                    totalOverhead += workout.overhead
                    totalJacks += workout.jacks
                    totalLunges += workout.lunges
                    totalPlank += workout.plank

                    totalCalories += workout.calories

                    if (workout.overallScore != -1) {
                        scoreSum += workout.overallScore
                        validScoreCount++
                    }

                    val qualityFactor = if (workout.overallScore == -1) 0.5 else workout.overallScore / 100.0
                    // Plank counts as one rep per 10 seconds held
                    val sessionReps = workout.totalReps + workout.plank / 10
                    totalXp += (sessionReps * 10 * qualityFactor).toInt()

                    workout.timestamp?.let { 
                        val cal = Calendar.getInstance()
                        cal.time = it.toDate()
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        workoutDates.add(cal.timeInMillis)
                    }
                }

                workoutList.sortByDescending { it.timestamp }
                workoutAdapter.updateWorkouts(workoutList)

                val avgScore = if (validScoreCount > 0) scoreSum / validScoreCount else 0
                
                updateLevelUI(totalXp)
                val currentStreak = calculateStreak(workoutDates)
                updateStreakUI(currentStreak)
                
                // Update Hexagon
                val muscleChart = findViewById<MuscleStatsView>(R.id.muscleStatsChart)
                muscleChart.setStats(
                    arms = LevelingUtils.getMuscleProgress(totalCurls),
                    chest = LevelingUtils.getMuscleProgress(totalPushups),
                    legs = LevelingUtils.getMuscleProgress(totalSquats + totalLunges),
                    abs = LevelingUtils.getMuscleProgress(totalSitups + totalPlank / 10),
                    shoulders = LevelingUtils.getMuscleProgress(totalOverhead),
                    back = LevelingUtils.getMuscleProgress((totalCalories / 10).toInt())
                )

                findViewById<TextView>(R.id.totalCurls).text = "Total Curls: $totalCurls"
                findViewById<TextView>(R.id.totalPushups).text = "Total Push-ups: $totalPushups"
                findViewById<TextView>(R.id.totalSquats).text = "Total Squats: $totalSquats"
                findViewById<TextView>(R.id.totalSitups).text = "Total Sit-ups: $totalSitups"
                findViewById<TextView>(R.id.totalOverhead).text = "Total Overhead Press: $totalOverhead"
                findViewById<TextView>(R.id.totalJacks).text = "Total Jumping Jacks: $totalJacks"
                findViewById<TextView>(R.id.totalLunges).text = "Total Lunges: $totalLunges"
                findViewById<TextView>(R.id.totalPlank).text = "Total Plank: ${formatPlankTime(totalPlank)}"
                findViewById<TextView>(R.id.totalCalories).text = "Total Calories: %.1f kcal".format(totalCalories)
                findViewById<TextView>(R.id.avgQualityScore).text = "Avg Quality Score: $avgScore%"
            }
    }

    private fun updateLevelUI(totalXp: Int) {
        val level = LevelingUtils.getLevelFromXp(totalXp)
        val progressXp = LevelingUtils.getXpProgressInLevel(totalXp)
        val requiredXp = LevelingUtils.getRequiredXpForCurrentLevel(level)

        findViewById<TextView>(R.id.userLevelText).text = "Level $level"
        findViewById<ProgressBar>(R.id.xpProgressBar).apply {
            max = requiredXp
            progress = progressXp
        }
        findViewById<TextView>(R.id.xpRemainingText).text = "$progressXp / $requiredXp XP to next level"
    }

    private fun calculateStreak(dates: Set<Long>): Int {
        if (dates.isEmpty()) return 0
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var currentStreak = 0
        var checkDate = today
        if (!dates.contains(today)) checkDate -= TimeUnit.DAYS.toMillis(1)

        for (i in 0 until 1000) {
            if (dates.contains(checkDate)) {
                currentStreak++
                checkDate -= TimeUnit.DAYS.toMillis(1)
            } else break
        }
        return currentStreak
    }

    private fun updateStreakUI(streak: Int) {
        findViewById<TextView>(R.id.streakText).text = "🔥 $streak Day Streak"
    }
}
