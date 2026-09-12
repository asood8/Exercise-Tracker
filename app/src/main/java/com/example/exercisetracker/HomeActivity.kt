package com.example.exercisetracker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val usernameInput = findViewById<TextInputEditText>(R.id.usernameInput)
        val weightInput = findViewById<TextInputEditText>(R.id.weightInput)
        val heightFeetInput = findViewById<TextInputEditText>(R.id.heightFeetInput)
        val heightInchesInput = findViewById<TextInputEditText>(R.id.heightInchesInput)
        val ageInput = findViewById<TextInputEditText>(R.id.ageInput)
        val genderGroup = findViewById<RadioGroup>(R.id.genderGroup)

        val nextButton = findViewById<Button>(R.id.nextButton)
        val signOutButton = findViewById<Button>(R.id.signOutButton)
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Load saved user info
        val sharedPrefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        auth.currentUser?.let { usernameInput.setText(Username.get(this, it.uid)) }
        weightInput.setText(sharedPrefs.getString("WEIGHT", "130"))
        val (savedFeet, savedInches) = loadHeight(sharedPrefs)
        heightFeetInput.setText(savedFeet)
        heightInchesInput.setText(savedInches)
        ageInput.setText(sharedPrefs.getString("AGE", "18"))
        val savedGender = sharedPrefs.getString("GENDER", "male")
        if (savedGender == "female") {
            findViewById<RadioButton>(R.id.genderFemale).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.genderMale).isChecked = true
        }

        fetchQuickStats()

        signOutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        bottomNavigation.selectedItemId = R.id.nav_home
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_leaderboard -> {
                    startActivity(Intent(this, LeaderboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        nextButton.setOnClickListener {
            val weightStr = weightInput.text.toString()
            val feetStr = heightFeetInput.text.toString()
            val inchesStr = heightInchesInput.text.toString()
            val ageStr = ageInput.text.toString()

            val weight = weightStr.toFloatOrNull() ?: 130f
            val feet = feetStr.toIntOrNull() ?: 5
            val inches = (inchesStr.toIntOrNull() ?: 0).coerceIn(0, 11)
            // MainActivity takes height as decimal feet
            val height = feet + inches / 12f
            val age = ageStr.toIntOrNull() ?: 18

            val selectedGenderId = genderGroup.checkedRadioButtonId
            val gender = if (selectedGenderId == R.id.genderFemale) "female" else "male"

            // Save values for next time
            sharedPrefs.edit().apply {
                putString("WEIGHT", weightStr)
                putString("HEIGHT_FT", feetStr)
                putString("HEIGHT_IN", inchesStr)
                remove("HEIGHT")
                putString("AGE", ageStr)
                putString("GENDER", gender)
                apply()
            }

            auth.currentUser?.let { user ->
                val username = Username.clean(usernameInput.text.toString(), user.uid)
                usernameInput.setText(username)
                if (username != Username.get(this, user.uid)) {
                    Username.save(this, user.uid, username)
                    // Rename an existing leaderboard entry. update() fails harmlessly if the user
                    // never opted in, so this can't create an entry on its own.
                    db.collection("users").document(user.uid)
                        .update(mapOf("username" to username, "email" to FieldValue.delete()))
                }
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("WEIGHT", weight)
                putExtra("HEIGHT", height)
                putExtra("AGE", age)
                putExtra("GENDER", gender)
            }
            startActivity(intent)
        }
    }

    // Height used to be one decimal-feet field ("5.6"); older saves are converted to feet + inches
    private fun loadHeight(prefs: SharedPreferences): Pair<String, String> {
        val feet = prefs.getString("HEIGHT_FT", null)
        val inches = prefs.getString("HEIGHT_IN", null)
        if (feet != null && inches != null) return feet to inches

        val legacyFeet = prefs.getString("HEIGHT", null)?.toFloatOrNull() ?: return "5" to "6"
        val totalInches = (legacyFeet * 12).roundToInt()
        return (totalInches / 12).toString() to (totalInches % 12).toString()
    }

    private fun fetchQuickStats() {
        val user = auth.currentUser ?: return

        db.collection("workouts")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                var totalReps = 0
                var totalCalories = 0.0
                var scoreSum = 0
                var count = 0

                for (document in documents) {
                    totalReps += (document.getLong("curls") ?: 0).toInt()
                    totalReps += (document.getLong("pushups") ?: 0).toInt()
                    totalReps += (document.getLong("squats") ?: 0).toInt()
                    totalReps += (document.getLong("situps") ?: 0).toInt()
                    totalReps += (document.getLong("overhead") ?: 0).toInt()
                    totalReps += (document.getLong("jacks") ?: 0).toInt()
                    totalReps += (document.getLong("lunges") ?: 0).toInt()

                    totalCalories += document.getDouble("calories") ?: 0.0
                    scoreSum += (document.getLong("overallScore") ?: 0).toInt()
                    count++
                }

                val avgScore = if (count > 0) scoreSum / count else 0

                findViewById<TextView>(R.id.quickTotalReps).text = totalReps.toString()
                findViewById<TextView>(R.id.quickTotalCalories).text = totalCalories.toInt().toString()
                findViewById<TextView>(R.id.quickAvgScore).text = "$avgScore%"
            }
    }
}
