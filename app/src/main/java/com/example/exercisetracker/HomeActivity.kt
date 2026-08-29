package com.example.exercisetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val weightInput = findViewById<TextInputEditText>(R.id.weightInput)
        val heightInput = findViewById<TextInputEditText>(R.id.heightInput)
        val ageInput = findViewById<TextInputEditText>(R.id.ageInput)
        val genderGroup = findViewById<RadioGroup>(R.id.genderGroup)
        
        val nextButton = findViewById<Button>(R.id.nextButton)
        val signOutButton = findViewById<Button>(R.id.signOutButton)
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Load saved user info
        val sharedPrefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        weightInput.setText(sharedPrefs.getString("WEIGHT", "130"))
        heightInput.setText(sharedPrefs.getString("HEIGHT", "5.6"))
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
            val heightStr = heightInput.text.toString()
            val ageStr = ageInput.text.toString()
            
            val weight = weightStr.toFloatOrNull() ?: 130f
            val height = heightStr.toFloatOrNull() ?: 5.6f
            val age = ageStr.toIntOrNull() ?: 18
            
            val selectedGenderId = genderGroup.checkedRadioButtonId
            val gender = if (selectedGenderId == R.id.genderFemale) "female" else "male"

            // Save values for next time
            sharedPrefs.edit().apply {
                putString("WEIGHT", weightStr)
                putString("HEIGHT", heightStr)
                putString("AGE", ageStr)
                putString("GENDER", gender)
                apply()
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
