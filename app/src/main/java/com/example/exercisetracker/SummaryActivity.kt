package com.example.exercisetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val curls = intent.getIntExtra("CURLS", 0)
        val pushups = intent.getIntExtra("PUSHUPS", 0)
        val squats = intent.getIntExtra("SQUATS", 0)
        val situps = intent.getIntExtra("SITUPS", 0)
        val overhead = intent.getIntExtra("OVERHEAD", 0)
        val jacks = intent.getIntExtra("JACKS", 0)
        val lunges = intent.getIntExtra("LUNGES", 0)
        val plank = intent.getIntExtra("PLANK", 0)
        val calories = intent.getDoubleExtra("CALORIES", 0.0)
        val overallScore = intent.getIntExtra("OVERALL_SCORE", -1)
        val feedbackSummary = intent.getStringExtra("FEEDBACK_SUMMARY") ?: "No feedback available."

        val scoreTextView = findViewById<TextView>(R.id.overallScore)
        if (overallScore == -1) {
            scoreTextView.text = "Overall Score: N/A"
        } else {
            scoreTextView.text = "Overall Score: $overallScore%"
        }

        // Only list what was actually done this session
        val breakdown = listOf(
            "Push-ups" to pushups, "Squats" to squats, "Sit-ups" to situps, "Lunges" to lunges,
            "Curls" to curls, "Overhead Press" to overhead, "Jumping Jacks" to jacks
        ).filter { it.second > 0 }.map { (name, count) -> "$name: $count" }.toMutableList()
        if (plank > 0) breakdown.add("Plank: ${formatPlankTime(plank)}")
        findViewById<TextView>(R.id.exerciseBreakdownText).text =
            if (breakdown.isEmpty()) "No reps recorded" else breakdown.joinToString("\n")
        findViewById<TextView>(R.id.caloriesText).text = "Total Calories: %.1f kcal".format(calories)
        findViewById<TextView>(R.id.feedbackSummaryText).text = feedbackSummary

        val leaderboardSwitch = findViewById<SwitchMaterial>(R.id.leaderboardSwitch)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            if (isRateLimited()) {
                Toast.makeText(this, "Please wait a few minutes before saving again", Toast.LENGTH_LONG).show()
            } else if (curls == 0 && pushups == 0 && squats == 0 && situps == 0 && overhead == 0 && jacks == 0 && lunges == 0 && plank == 0) {
                Toast.makeText(this, "Cannot save a workout with 0 reps", Toast.LENGTH_SHORT).show()
            } else {
                saveWorkoutToCloud(
                    curls, pushups, squats, situps, overhead, jacks, lunges, plank,
                    calories, overallScore, leaderboardSwitch.isChecked
                )
            }
        }

        findViewById<Button>(R.id.doneButton).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    private fun isRateLimited(): Boolean {
        val prefs = getSharedPreferences("WorkoutPrefs", Context.MODE_PRIVATE)
        val lastSaveTime = prefs.getLong("last_save_timestamp", 0L)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastSaveTime) < 180000L
    }

    private fun saveWorkoutToCloud(
        curls: Int, pushups: Int, squats: Int, situps: Int, 
        overhead: Int, jacks: Int, lunges: Int, plank: Int,
        calories: Double, score: Int, shouldShare: Boolean
    ) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Must be logged in to save", Toast.LENGTH_SHORT).show()
            return
        }

        val workout = hashMapOf(
            "userId" to user.uid,
            "timestamp" to FieldValue.serverTimestamp(),
            "curls" to curls,
            "pushups" to pushups,
            "squats" to squats,
            "situps" to situps,
            "overhead" to overhead,
            "jacks" to jacks,
            "lunges" to lunges,
            "plank" to plank,
            "calories" to calories,
            "overallScore" to score,
            "sharedToLeaderboard" to shouldShare
        )

        findViewById<Button>(R.id.saveButton).isEnabled = false
        findViewById<Button>(R.id.saveButton).text = "Saving..."

        db.collection("workouts")
            .add(workout)
            .addOnSuccessListener {
                if (shouldShare) {
                    updateLeaderboardStats(user.uid, Username.get(this, user.uid), curls + pushups + squats + situps + overhead + jacks + lunges, calories, score)
                } else {
                    onSaveComplete()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                findViewById<Button>(R.id.saveButton).isEnabled = true
                findViewById<Button>(R.id.saveButton).text = "Save to Cloud"
            }
    }

    private fun updateLeaderboardStats(uid: String, username: String, sessionReps: Int, sessionCalories: Double, sessionScore: Int) {
        val userRef = db.collection("users").document(uid)

        // Use increment to update global totals
        val updates = hashMapOf(
            "username" to username,
            // Older versions stored the account email here; remove it since leaderboard docs are public
            "email" to FieldValue.delete(),
            "totalReps" to FieldValue.increment(sessionReps.toLong()),
            "totalCalories" to FieldValue.increment(sessionCalories),
            "lastActive" to FieldValue.serverTimestamp()
        )

        userRef.set(updates, SetOptions.merge())
            .addOnSuccessListener { onSaveComplete() }
            .addOnFailureListener { onSaveComplete() } // Still mark as saved even if leaderboard update fails
    }

    private fun onSaveComplete() {
        Toast.makeText(this, "Workout saved!", Toast.LENGTH_SHORT).show()
        findViewById<Button>(R.id.saveButton).text = "Saved ✅"
        getSharedPreferences("WorkoutPrefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_save_timestamp", System.currentTimeMillis())
            .apply()
    }
}
