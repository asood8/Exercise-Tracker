package com.example.exercisetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

class SummaryActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var exerciseRows: LinearLayout
    private lateinit var leaderboardSwitch: SwitchMaterial
    private lateinit var saveButton: Button

    // Counts as the trackers saw them, and as corrected on this screen. Plank is in seconds.
    private val detected = linkedMapOf<Exercise, Int>()
    private val counts = linkedMapOf<Exercise, Int>()

    // One entry per scored rep or plank hold, in the order MainActivity sent them
    private data class RepDetail(val exercise: Exercise, val score: Int, val seconds: Int, val issues: String)
    private var repDetails = listOf<RepDetail>()

    private var calories = 0.0
    private var overallScore = -1
    private var durationSeconds = 0
    private var saved = false

    companion object {
        private const val SAVE_COOLDOWN_MS = 180000L
        // How long to wait for the server before telling the user the save is waiting for a connection
        private const val OFFLINE_WAIT_MS = 5000L
        private const val MAX_REPS = 2000
        private const val MAX_PLANK_SECONDS = 3600
        private const val PLANK_STEP_SECONDS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // The count extras are named after the Exercise entries ("PUSHUPS", "PLANK", ...)
        for (exercise in Exercise.entries) {
            val count = intent.getIntExtra(exercise.name, 0)
            detected[exercise] = count
            counts[exercise] = count
        }
        calories = intent.getDoubleExtra("CALORIES", 0.0)
        overallScore = intent.getIntExtra("OVERALL_SCORE", -1)
        durationSeconds = intent.getIntExtra("DURATION", 0)
        val feedbackSummary = intent.getStringExtra("FEEDBACK_SUMMARY") ?: "No feedback available."
        val goalSummary = intent.getStringExtra("GOAL_SUMMARY")
        repDetails = readRepDetails()

        findViewById<TextView>(R.id.overallScore).text =
            if (overallScore == -1) "Overall Score: N/A" else "Overall Score: $overallScore%"

        // Session length and goal progress, when there is any
        val sessionInfo = mutableListOf<String>()
        if (durationSeconds > 0) {
            val minutes = durationSeconds / 60.0
            var timeLine = "Time: ${formatDuration(durationSeconds)}"
            if (minutes >= 1 && calories > 0) timeLine += " · %.1f kcal/min".format(calories / minutes)
            sessionInfo.add(timeLine)
        }
        goalSummary?.let { sessionInfo.add(it) }
        findViewById<TextView>(R.id.sessionInfoText).apply {
            text = sessionInfo.joinToString("\n")
            visibility = if (sessionInfo.isEmpty()) View.GONE else View.VISIBLE
        }

        leaderboardSwitch = findViewById(R.id.leaderboardSwitch)
        saveButton = findViewById(R.id.saveButton)
        exerciseRows = findViewById(R.id.exerciseRows)
        showCountRows()
        setupRepDetails()
        findViewById<TextView>(R.id.caloriesText).text = "Total Calories: %.1f kcal".format(calories)
        findViewById<TextView>(R.id.feedbackSummaryText).text = feedbackSummary

        saveButton.setOnClickListener {
            when {
                saved -> Unit
                isRateLimited() -> Toast.makeText(this, "Please wait a few minutes before saving again", Toast.LENGTH_LONG).show()
                counts.values.all { it == 0 } -> Toast.makeText(this, "Cannot save a workout with 0 reps", Toast.LENGTH_SHORT).show()
                else -> saveWorkout()
            }
        }

        findViewById<Button>(R.id.doneButton).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    private fun readRepDetails(): List<RepDetail> {
        val exercises = intent.getStringArrayListExtra("REP_EXERCISES") ?: return emptyList()
        val scores = intent.getIntArrayExtra("REP_SCORES") ?: return emptyList()
        val seconds = intent.getIntArrayExtra("REP_SECONDS") ?: IntArray(scores.size)
        val issues = intent.getStringArrayListExtra("REP_ISSUES").orEmpty()
        return exercises.indices.filter { it < scores.size }.map { i ->
            RepDetail(Exercise.fromName(exercises[i]), scores[i], seconds.getOrElse(i) { 0 }, issues.getOrElse(i) { "" })
        }
    }

    // --- Counts, with corrections ---

    // One row per exercise done this session, including ones that were tracked but counted nothing,
    // so a count the camera missed completely can still be fixed
    private fun showCountRows() {
        val used = intent.getStringArrayListExtra("EXERCISES_USED").orEmpty().map { Exercise.fromName(it) }.toSet()
        val shown = Exercise.entries.filter { (detected[it] ?: 0) > 0 || it in used }
        findViewById<TextView>(R.id.noRepsText).visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE

        for (exercise in shown) {
            val row = layoutInflater.inflate(R.layout.item_count_row, exerciseRows, false)
            row.tag = exercise
            val step = if (exercise.isTimed) PLANK_STEP_SECONDS else 1
            val max = if (exercise.isTimed) MAX_PLANK_SECONDS else MAX_REPS
            row.findViewById<Button>(R.id.countMinus).apply {
                contentDescription = "Lower the ${exercise.displayName} count"
                setOnClickListener { changeCount(exercise, -step, max) }
            }
            row.findViewById<Button>(R.id.countPlus).apply {
                contentDescription = "Raise the ${exercise.displayName} count"
                setOnClickListener { changeCount(exercise, step, max) }
            }
            exerciseRows.addView(row)
            updateRow(row, exercise)
        }
    }

    private fun changeCount(exercise: Exercise, delta: Int, max: Int) {
        counts[exercise] = ((counts[exercise] ?: 0) + delta).coerceIn(0, max)
        exerciseRows.findViewWithTag<View>(exercise)?.let { updateRow(it, exercise) }
        updateEditedState()
    }

    private fun updateRow(row: View, exercise: Exercise) {
        val count = counts[exercise] ?: 0
        val original = detected[exercise] ?: 0
        fun format(value: Int) = if (exercise.isTimed) formatPlankTime(value) else value.toString()
        row.findViewById<TextView>(R.id.countName).text =
            if (count == original) exercise.displayName else "${exercise.displayName} (counted ${format(original)})"
        row.findViewById<TextView>(R.id.countValue).text = format(count)
    }

    private fun isEdited() = counts != detected

    // Edited workouts are saved, but kept off the leaderboard so it can't be padded by hand
    private fun updateEditedState() {
        val edited = isEdited()
        findViewById<TextView>(R.id.editedNote).visibility = if (edited) View.VISIBLE else View.GONE
        leaderboardSwitch.isEnabled = !edited
        if (edited) leaderboardSwitch.isChecked = false
    }

    private fun setCountButtonsEnabled(enabled: Boolean) {
        for (i in 0 until exerciseRows.childCount) {
            val row = exerciseRows.getChildAt(i)
            row.findViewById<Button>(R.id.countMinus).isEnabled = enabled
            row.findViewById<Button>(R.id.countPlus).isEnabled = enabled
        }
    }

    // --- Rep-by-rep review ---

    private fun setupRepDetails() {
        val button = findViewById<Button>(R.id.repDetailsButton)
        val details = findViewById<TextView>(R.id.repDetailsText)
        if (repDetails.isEmpty()) {
            button.visibility = View.GONE
            return
        }
        details.text = buildRepDetailsText()
        button.setOnClickListener {
            val show = details.visibility != View.VISIBLE
            details.visibility = if (show) View.VISIBLE else View.GONE
            button.text = if (show) "Hide each rep" else "Show each rep"
        }
    }

    // Grouped by exercise, e.g. "Squats" followed by "1. 92%" and "2. 78% · Go lower!"
    private fun buildRepDetailsText(): String =
        repDetails.groupBy { it.exercise }.entries.joinToString("\n\n") { (exercise, reps) ->
            val lines = reps.mapIndexed { i, rep ->
                val label = if (exercise.isTimed) "Hold ${i + 1} (${formatPlankTime(rep.seconds)})" else "${i + 1}."
                val issues = if (rep.issues.isEmpty()) "" else " · ${rep.issues}"
                "   $label ${rep.score}%$issues"
            }
            exercise.displayName + "\n" + lines.joinToString("\n")
        }

    // --- Saving ---

    private fun isRateLimited(): Boolean {
        val lastSaveTime = getSharedPreferences("WorkoutPrefs", Context.MODE_PRIVATE).getLong("last_save_timestamp", 0L)
        return System.currentTimeMillis() - lastSaveTime < SAVE_COOLDOWN_MS
    }

    private fun saveWorkout() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Must be logged in to save", Toast.LENGTH_SHORT).show()
            return
        }
        saveButton.isEnabled = false
        saveButton.text = "Saving..."
        setCountButtonsEnabled(false)

        when {
            !leaderboardSwitch.isChecked || isEdited() -> writeWorkout(user.uid, username = null)
            // The username claim below needs the server, so an offline workout stays off the leaderboard
            !Connectivity.isOnline(this) -> writeWorkout(
                user.uid, username = null,
                pendingMessage = "Saved on this phone. It'll upload when you're back online, but it won't go on the leaderboard since you're offline."
            )
            else -> {
                // Usernames are unique, so the name has to be claimed before it can go on the leaderboard.
                // Claiming first means sharedToLeaderboard is only true when the workout really was shared.
                val username = Username.get(this, user.uid)
                Username.claim(db, user.uid, username) { result ->
                    when (result) {
                        Username.ClaimResult.CLAIMED -> writeWorkout(user.uid, username)
                        Username.ClaimResult.TAKEN -> writeWorkout(
                            user.uid, username = null,
                            savedMessage = "Workout saved, but someone else has your username. Change it on the Home screen to appear on the leaderboard."
                        )
                        Username.ClaimResult.FAILED -> writeWorkout(
                            user.uid, username = null, savedMessage = "Workout saved, but the leaderboard couldn't be updated."
                        )
                    }
                }
            }
        }
    }

    // Writes the workout, plus the leaderboard entry when a username is given. Without a connection,
    // Firestore keeps the write and uploads it later, even if the app is closed, but it only reports
    // success once the server has it. So after a few seconds with no answer, the save counts as done.
    private fun writeWorkout(
        uid: String,
        username: String?,
        savedMessage: String = "Workout saved!",
        pendingMessage: String = "Saved on this phone. It'll upload when you're back online."
    ) {
        val stillWaiting = Runnable { onSaved(pendingMessage) }
        db.collection("workouts").document().set(buildWorkout(uid, shared = username != null))
            .addOnSuccessListener {
                if (username != null) {
                    writeLeaderboardEntry(uid, username) { ok ->
                        onSaved(if (ok) savedMessage else "Workout saved, but the leaderboard couldn't be updated.")
                    }
                } else {
                    onSaved(savedMessage)
                }
            }
            .addOnFailureListener { e ->
                if (saved) {
                    Toast.makeText(applicationContext, "Your workout couldn't be uploaded: ${e.message}", Toast.LENGTH_LONG).show()
                } else {
                    handler.removeCallbacks(stillWaiting)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    saveButton.isEnabled = true
                    saveButton.text = "Save to Cloud"
                    setCountButtonsEnabled(true)
                }
            }
        handler.postDelayed(stillWaiting, OFFLINE_WAIT_MS)
    }

    private fun buildWorkout(uid: String, shared: Boolean): HashMap<String, Any> {
        val workout = hashMapOf<String, Any>(
            "userId" to uid,
            "timestamp" to FieldValue.serverTimestamp(),
            "calories" to calories,
            "overallScore" to overallScore,
            "durationSeconds" to durationSeconds,
            "sharedToLeaderboard" to shared
        )
        counts.forEach { (exercise, count) -> workout[exercise.fieldName] = count }
        if (isEdited()) workout["edited"] = true

        // Average form score per exercise, which goal suggestions use
        val scores = repDetails.groupBy { it.exercise }
            .map { (exercise, reps) -> exercise.fieldName to reps.map { it.score }.average().toInt() }
            .toMap()
        if (scores.isNotEmpty()) workout["scores"] = scores

        intent.getStringExtra("GOAL_EXERCISE")?.let { name ->
            workout["goal"] = mapOf(
                "exercise" to Exercise.fromName(name).fieldName,
                "sets" to intent.getIntExtra("GOAL_SETS", 0),
                "target" to intent.getIntExtra("GOAL_TARGET", 0),
                "setsDone" to intent.getIntExtra("GOAL_SETS_DONE", 0)
            )
        }
        intent.getStringExtra("ROUTINE_NAME")?.let { workout["routine"] = it }
        return workout
    }

    private fun writeLeaderboardEntry(uid: String, username: String, done: (Boolean) -> Unit) {
        val userRef = db.collection("users").document(uid)
        val sessionReps = counts.filterKeys { !it.isTimed }.values.sum()

        // The level comes from all of the user's workouts, calculated the same way as on History
        db.collection("workouts").whereEqualTo("userId", uid).get()
            .addOnCompleteListener { levelTask ->
                // Use increment to update global totals
                val updates = hashMapOf<String, Any>(
                    "username" to username,
                    // Older versions stored the account email here; remove it since leaderboard docs are public
                    "email" to FieldValue.delete(),
                    "totalReps" to FieldValue.increment(sessionReps.toLong()),
                    "totalCalories" to FieldValue.increment(calories),
                    "lastActive" to FieldValue.serverTimestamp()
                )
                if (levelTask.isSuccessful) {
                    val xp = levelTask.result.sumOf { LevelingUtils.xpForWorkout(it.toObject(Workout::class.java)) }
                    updates["level"] = LevelingUtils.getLevelFromXp(xp)
                }

                userRef.set(updates, SetOptions.merge()).addOnCompleteListener { done(it.isSuccessful) }
            }
    }

    private fun onSaved(message: String) {
        if (saved) return
        saved = true
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        saveButton.text = "Saved ✅"
        getSharedPreferences("WorkoutPrefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_save_timestamp", System.currentTimeMillis())
            .apply()
        // Keeps the streak reminder accurate before History has been reopened
        Streaks.recordWorkoutToday(this)
    }
}
