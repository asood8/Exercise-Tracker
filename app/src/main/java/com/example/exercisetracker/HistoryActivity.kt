package com.example.exercisetracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class HistoryActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var workoutAdapter: WorkoutAdapter
    private val workoutList = mutableListOf<Workout>()
    private lateinit var achievementAdapter: AchievementAdapter

    // Weekly chart data, oldest week first
    private var weekLabels = listOf<String>()
    private var weeklyReps = listOf<Float>()
    private var weeklyCalories = listOf<Float>()
    private var weeklyMinutes = listOf<Float>()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setReminders(true)
            } else {
                findViewById<SwitchMaterial>(R.id.streakReminderSwitch).isChecked = false
                Toast.makeText(this, "Notifications are off for this app, so reminders can't be shown", Toast.LENGTH_LONG).show()
            }
        }

    companion object {
        private const val WEEKS_SHOWN = 8
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupRecyclerViews()
        setupNavigation()
        setupChartToggle()
        setupReminderSwitch()
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

        // Achievements grid; everything starts locked until the stats load
        val achievementsView = findViewById<RecyclerView>(R.id.achievementsRecyclerView)
        achievementsView.layoutManager = GridLayoutManager(this, 3)
        achievementAdapter = AchievementAdapter(AchievementManager.allAchievements) { achievement, unlocked ->
            showAchievementDetails(achievement, unlocked)
        }
        achievementsView.adapter = achievementAdapter
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
                if (workout.sharedToLeaderboard) removeFromLeaderboard(workout)
                fetchLifetimeStats()
            }
    }

    // Takes a deleted workout's reps and calories back off the leaderboard totals it was added to
    private fun removeFromLeaderboard(workout: Workout) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            mapOf(
                "totalReps" to FieldValue.increment(-workout.totalReps.toLong()),
                "totalCalories" to FieldValue.increment(-workout.calories)
            )
        )
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

    private fun setupChartToggle() {
        findViewById<MaterialButtonToggleGroup>(R.id.chartToggle).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showChart(checkedId)
        }
    }

    private fun showChart(buttonId: Int) {
        val chart = findViewById<WeeklyChartView>(R.id.weeklyChart)
        when (buttonId) {
            R.id.chartCalories -> chart.setData(weeklyCalories, weekLabels) { compactNumber(it) }
            R.id.chartMinutes -> chart.setData(weeklyMinutes, weekLabels) { it.roundToInt().toString() }
            else -> chart.setData(weeklyReps, weekLabels) { compactNumber(it) }
        }
    }

    private fun compactNumber(value: Float): String =
        if (value >= 1000) "%.1fk".format(value / 1000) else value.roundToInt().toString()

    private fun setupReminderSwitch() {
        val reminderSwitch = findViewById<SwitchMaterial>(R.id.streakReminderSwitch)
        reminderSwitch.isChecked = Streaks.remindersEnabled(this)
        reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            when {
                !isChecked -> setReminders(false)
                needsNotificationPermission() -> requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> setReminders(true)
            }
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun setReminders(enabled: Boolean) {
        Streaks.setRemindersEnabled(this, enabled)
        if (enabled) StreakReminders.enable(this) else StreakReminders.disable(this)
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
                var totalSeconds = 0
                var totalCalories = 0.0
                var totalXp = 0
                var scoreSum = 0
                var validScoreCount = 0
                val workoutDays = mutableSetOf<Long>()

                for (doc in documents) {
                    // A workout saved offline has no server timestamp yet, so use the local estimate
                    // to keep it dated and counted in streaks until it uploads
                    val workout = doc.toObject(Workout::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .copy(id = doc.id)
                    workoutList.add(workout)

                    totalCurls += workout.curls
                    totalPushups += workout.pushups
                    totalSquats += workout.squats
                    totalSitups += workout.situps
                    totalOverhead += workout.overhead
                    totalJacks += workout.jacks
                    totalLunges += workout.lunges
                    totalPlank += workout.plank
                    totalSeconds += workout.durationSeconds

                    totalCalories += workout.calories

                    if (workout.overallScore != -1) {
                        scoreSum += workout.overallScore
                        validScoreCount++
                    }

                    totalXp += LevelingUtils.xpForWorkout(workout)

                    workout.timestamp?.let { workoutDays.add(Streaks.dayNumber(it.toDate().time)) }
                }

                workoutList.sortByDescending { it.timestamp }
                workoutAdapter.updateWorkouts(workoutList)

                val avgScore = if (validScoreCount > 0) scoreSum / validScoreCount else 0

                updateLevelUI(totalXp)
                syncLeaderboardLevel(user.uid, LevelingUtils.getLevelFromXp(totalXp))

                val currentStreak = Streaks.current(workoutDays)
                val longestStreak = Streaks.longest(workoutDays)
                updateStreakUI(currentStreak, longestStreak)
                // The reminder worker reads this, since it can't query Firestore itself
                workoutDays.maxOrNull()?.let { Streaks.saveState(this, it, currentStreak) }

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

                buildWeeklyChart()
                showChart(findViewById<MaterialButtonToggleGroup>(R.id.chartToggle).checkedButtonId)
                showPersonalRecords(longestStreak)

                val exerciseTotals = listOf(totalCurls, totalPushups, totalSquats, totalSitups, totalOverhead, totalJacks, totalLunges, totalPlank)
                showAchievements(
                    user.uid,
                    AchievementStats(
                        workoutCount = workoutList.size,
                        totalReps = workoutList.sumOf { it.totalReps },
                        totalCalories = totalCalories,
                        totalSeconds = totalSeconds,
                        longestStreak = longestStreak,
                        avgQuality = avgScore,
                        scoredWorkouts = validScoreCount,
                        bestScore = workoutList.maxOfOrNull { it.overallScore } ?: 0,
                        longestPlank = workoutList.maxOfOrNull { it.plank } ?: 0,
                        level = LevelingUtils.getLevelFromXp(totalXp),
                        exercisesTried = exerciseTotals.count { it > 0 }
                    )
                )

                findViewById<TextView>(R.id.totalCurls).text = "Total Curls: $totalCurls"
                findViewById<TextView>(R.id.totalPushups).text = "Total Push-ups: $totalPushups"
                findViewById<TextView>(R.id.totalSquats).text = "Total Squats: $totalSquats"
                findViewById<TextView>(R.id.totalSitups).text = "Total Sit-ups: $totalSitups"
                findViewById<TextView>(R.id.totalOverhead).text = "Total Overhead Press: $totalOverhead"
                findViewById<TextView>(R.id.totalJacks).text = "Total Jumping Jacks: $totalJacks"
                findViewById<TextView>(R.id.totalLunges).text = "Total Lunges: $totalLunges"
                findViewById<TextView>(R.id.totalPlank).text = "Total Plank: ${formatPlankTime(totalPlank)}"
                // Workouts saved before durations were recorded count as zero
                findViewById<TextView>(R.id.totalTime).text =
                    if (totalSeconds > 0) "Total Workout Time: ${formatDuration(totalSeconds)}" else "Total Workout Time: not recorded yet"
                findViewById<TextView>(R.id.totalCalories).text = "Total Calories: %.1f kcal".format(totalCalories)
                findViewById<TextView>(R.id.avgQualityScore).text = "Avg Quality Score: $avgScore%"
            }
    }

    // Keeps the leaderboard's level in step with this screen, e.g. after a workout that wasn't
    // shared, or a deleted one. update() fails harmlessly for users who never opted into the
    // leaderboard, so it can't create an entry. The last level sent is cached so this only writes
    // when the level actually changes.
    private fun syncLeaderboardLevel(uid: String, level: Int) {
        val prefs = getSharedPreferences("LeaderboardPrefs", Context.MODE_PRIVATE)
        val key = "level_$uid"
        if (prefs.getInt(key, -1) == level) return
        db.collection("users").document(uid).update("level", level)
            .addOnCompleteListener { prefs.edit().putInt(key, level).apply() }
    }

    private fun showAchievements(uid: String, stats: AchievementStats) {
        val unlocked = AchievementManager.getUnlockedAchievements(stats)
        val unlockedIds = unlocked.map { it.id }.toSet()
        achievementAdapter.update(unlockedIds)
        findViewById<TextView>(R.id.achievementsCountText).text =
            "${unlocked.size} of ${AchievementManager.allAchievements.size} unlocked"

        // Celebrate anything unlocked since this account last opened History. The first visit just
        // records what's already unlocked, so existing users don't get a pile of pop-ups, and each
        // achievement is only celebrated once even if deleting a workout re-locks it.
        val prefs = getSharedPreferences("AchievementPrefs", Context.MODE_PRIVATE)
        val key = "seen_$uid"
        val seen = prefs.getStringSet(key, null)
        prefs.edit().putStringSet(key, (seen ?: emptySet()) + unlockedIds).apply()
        if (seen == null) return

        val fresh = unlocked.filter { it.id !in seen }
        if (fresh.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(if (fresh.size == 1) "Achievement unlocked!" else "${fresh.size} achievements unlocked!")
            .setMessage(fresh.joinToString("\n") { "${it.icon}  ${it.title}: ${it.description}" })
            .setPositiveButton("Nice", null)
            .show()
    }

    private fun showAchievementDetails(achievement: Achievement, unlocked: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("${achievement.icon}  ${achievement.title}")
            .setMessage(achievement.description + if (unlocked) "\n\nUnlocked ✓" else "\n\nNot unlocked yet")
            .setPositiveButton("OK", null)
            .show()
    }

    // Buckets workouts into Monday-to-Sunday weeks, ending with the current week
    private fun buildWeeklyChart() {
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val thisWeek = weekStart(Streaks.today())
        val reps = FloatArray(WEEKS_SHOWN)
        val calories = FloatArray(WEEKS_SHOWN)
        val minutes = FloatArray(WEEKS_SHOWN)

        for (workout in workoutList) {
            val time = workout.timestamp?.toDate()?.time ?: continue
            val weeksAgo = ((thisWeek - weekStart(Streaks.dayNumber(time))) / 7).toInt()
            if (weeksAgo !in 0 until WEEKS_SHOWN) continue
            val slot = WEEKS_SHOWN - 1 - weeksAgo
            reps[slot] += workout.totalReps.toFloat()
            calories[slot] += workout.calories.toFloat()
            minutes[slot] += workout.durationSeconds / 60f
        }

        val labelFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "Md"), Locale.getDefault())
        weekLabels = (0 until WEEKS_SHOWN).map { slot ->
            val day = thisWeek - 7L * (WEEKS_SHOWN - 1 - slot)
            // Noon local time on that day, so the label can't slip a day across a timezone offset
            val approxMs = day * dayMs + TimeUnit.HOURS.toMillis(12)
            labelFormat.format(Date(approxMs - TimeZone.getDefault().getOffset(approxMs)))
        }
        weeklyReps = reps.toList()
        weeklyCalories = calories.toList()
        weeklyMinutes = minutes.toList()
    }

    // Day numbers count from 1970-01-01, which was a Thursday; shift so weeks start on Monday
    private fun weekStart(day: Long): Long = day - Math.floorMod(day + 3, 7L)

    private fun showPersonalRecords(longestStreak: Int) {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val records = mutableListOf<String>()

        fun addBest(label: String, value: (Workout) -> Int, format: (Int) -> String = { it.toString() }) {
            val best = workoutList.maxByOrNull(value) ?: return
            if (value(best) <= 0) return
            val date = best.timestamp?.toDate()?.let { " (${dateFormat.format(it)})" } ?: ""
            records.add("$label: ${format(value(best))}$date")
        }

        addBest("Most push-ups", { it.pushups })
        addBest("Most squats", { it.squats })
        addBest("Most sit-ups", { it.situps })
        addBest("Most lunges", { it.lunges })
        addBest("Most curls", { it.curls })
        addBest("Most overhead presses", { it.overhead })
        addBest("Most jumping jacks", { it.jacks })
        addBest("Longest plank", { it.plank }, ::formatPlankTime)
        addBest("Longest workout", { it.durationSeconds }, ::formatDuration)
        addBest("Best form score", { it.overallScore }, { "$it%" })
        if (longestStreak > 0) records.add("Longest streak: $longestStreak ${if (longestStreak == 1) "day" else "days"}")

        findViewById<TextView>(R.id.personalRecordsText).text =
            if (records.isEmpty()) "Save a workout to start setting records." else records.joinToString("\n")
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

    private fun updateStreakUI(streak: Int, longest: Int) {
        findViewById<TextView>(R.id.streakText).text = "🔥 $streak Day Streak"
        findViewById<TextView>(R.id.bestStreakText).text = "Best: $longest ${if (longest == 1) "day" else "days"}"
    }
}
