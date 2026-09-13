package com.example.exercisetracker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt

class HomeActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var weightInputLayout: TextInputLayout
    private lateinit var weightInput: TextInputEditText
    private lateinit var heightFeetLayout: View
    private lateinit var heightInchesLayout: View
    private lateinit var heightCmLayout: View
    private lateinit var heightFeetInput: TextInputEditText
    private lateinit var heightInchesInput: TextInputEditText
    private lateinit var heightCmInput: TextInputEditText
    private var isMetric = false

    companion object {
        private const val KG_PER_LB = 0.453592f
        private const val CM_PER_INCH = 2.54f
        private const val DEFAULT_WEIGHT_KG = 130 * KG_PER_LB
        private const val DEFAULT_HEIGHT_CM = 66 * CM_PER_INCH // 5'6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val usernameInput = findViewById<TextInputEditText>(R.id.usernameInput)
        val unitsToggle = findViewById<MaterialButtonToggleGroup>(R.id.unitsToggle)
        weightInputLayout = findViewById(R.id.weightInputLayout)
        weightInput = findViewById(R.id.weightInput)
        heightFeetLayout = findViewById(R.id.heightFeetLayout)
        heightInchesLayout = findViewById(R.id.heightInchesLayout)
        heightCmLayout = findViewById(R.id.heightCmLayout)
        heightFeetInput = findViewById(R.id.heightFeetInput)
        heightInchesInput = findViewById(R.id.heightInchesInput)
        heightCmInput = findViewById(R.id.heightCmInput)
        val ageInput = findViewById<TextInputEditText>(R.id.ageInput)
        val genderGroup = findViewById<RadioGroup>(R.id.genderGroup)
        val exerciseChips = findViewById<ChipGroup>(R.id.exerciseChips)
        val goalSetsInput = findViewById<TextInputEditText>(R.id.goalSetsInput)
        val goalTargetInput = findViewById<TextInputEditText>(R.id.goalTargetInput)
        val goalTargetLayout = findViewById<TextInputLayout>(R.id.goalTargetLayout)

        val nextButton = findViewById<Button>(R.id.nextButton)
        val signOutButton = findViewById<Button>(R.id.signOutButton)
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Load saved user info
        val sharedPrefs = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        auth.currentUser?.let { usernameInput.setText(Username.get(this, it.uid)) }
        isMetric = sharedPrefs.getString("UNITS", "imperial") == "metric"
        unitsToggle.check(if (isMetric) R.id.unitsMetric else R.id.unitsImperial)
        showBodyStats(loadWeightKg(sharedPrefs), loadHeightCm(sharedPrefs))
        ageInput.setText(sharedPrefs.getString("AGE", "18"))
        val savedGender = sharedPrefs.getString("GENDER", "male")
        if (savedGender == "female") {
            findViewById<RadioButton>(R.id.genderFemale).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.genderMale).isChecked = true
        }
        goalSetsInput.setText(sharedPrefs.getString("GOAL_SETS", ""))
        goalTargetInput.setText(sharedPrefs.getString("GOAL_TARGET", ""))

        unitsToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val metric = checkedId == R.id.unitsMetric
            if (!isChecked || metric == isMetric) return@addOnButtonCheckedListener
            // Convert whatever is currently typed, so switching units doesn't lose it
            val weightKg = readWeightKg() ?: DEFAULT_WEIGHT_KG
            val heightCm = readHeightCm() ?: DEFAULT_HEIGHT_CM
            isMetric = metric
            showBodyStats(weightKg, heightCm)
        }

        // One chip per exercise, with the last one used selected
        val savedExercise = Exercise.fromName(sharedPrefs.getString("EXERCISE", null))
        for (exercise in Exercise.entries) {
            val chip = layoutInflater.inflate(R.layout.item_exercise_chip, exerciseChips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = exercise.displayName
            chip.tag = exercise
            exerciseChips.addView(chip)
            if (exercise == savedExercise) exerciseChips.check(chip.id)
        }

        // Plank goals are in seconds, everything else in reps
        fun updateGoalHint() {
            goalTargetLayout.hint = if (selectedExercise(exerciseChips).isTimed) "Seconds per set" else "Reps per set"
        }
        updateGoalHint()
        exerciseChips.setOnCheckedStateChangeListener { _, _ -> updateGoalHint() }

        fetchQuickStats()

        findViewById<View>(R.id.guestBanner).visibility =
            if (auth.currentUser?.isAnonymous == true) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.createAccountButton).setOnClickListener { showCreateAccountDialog() }

        signOutButton.setOnClickListener {
            if (auth.currentUser?.isAnonymous == true) confirmGuestSignOut() else signOut()
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
            val weightKg = readWeightKg() ?: DEFAULT_WEIGHT_KG
            val heightCm = readHeightCm() ?: DEFAULT_HEIGHT_CM
            val ageStr = ageInput.text.toString()
            val age = ageStr.toIntOrNull() ?: 18

            val selectedGenderId = genderGroup.checkedRadioButtonId
            val gender = if (selectedGenderId == R.id.genderFemale) "female" else "male"

            val exercise = selectedExercise(exerciseChips)
            val goalSetsStr = goalSetsInput.text.toString()
            val goalTargetStr = goalTargetInput.text.toString()
            val goalSets = goalSetsStr.toIntOrNull()?.coerceIn(0, 20) ?: 0
            val goalTarget = goalTargetStr.toIntOrNull()?.coerceIn(0, 999) ?: 0

            // Save values for next time. Body stats are stored in metric no matter which units are
            // shown, so switching units back and forth doesn't round them off.
            sharedPrefs.edit().apply {
                putFloat("WEIGHT_KG", weightKg)
                putFloat("HEIGHT_CM", heightCm)
                remove("WEIGHT")
                remove("HEIGHT_FT")
                remove("HEIGHT_IN")
                remove("HEIGHT")
                putString("UNITS", if (isMetric) "metric" else "imperial")
                putString("AGE", ageStr)
                putString("GENDER", gender)
                putString("EXERCISE", exercise.name)
                putString("GOAL_SETS", goalSetsStr)
                putString("GOAL_TARGET", goalTargetStr)
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
                putExtra("WEIGHT_KG", weightKg)
                putExtra("HEIGHT_CM", heightCm)
                putExtra("AGE", age)
                putExtra("GENDER", gender)
                putExtra("EXERCISE", exercise.name)
                putExtra("GOAL_SETS", goalSets)
                putExtra("GOAL_TARGET", goalTarget)
            }
            startActivity(intent)
        }
    }

    private fun selectedExercise(chips: ChipGroup): Exercise =
        chips.findViewById<Chip>(chips.checkedChipId)?.tag as? Exercise ?: Exercise.SQUATS

    // --- Units ---

    // What showBodyStats last put in the fields. While a field still shows exactly that, the precise
    // value is used instead of re-reading the rounded text, so switching units back and forth can't
    // drift (130 lb -> 59 kg -> 130.1 lb).
    private var shownWeightKg = 0f
    private var shownWeightText = ""
    private var shownHeightCm = 0f
    private var shownHeightText = ""

    private fun showBodyStats(weightKg: Float, heightCm: Float) {
        weightInputLayout.hint = if (isMetric) "Weight (kg)" else "Weight (lb)"
        weightInput.setText(formatNumber(if (isMetric) weightKg else weightKg / KG_PER_LB))

        heightFeetLayout.visibility = if (isMetric) View.GONE else View.VISIBLE
        heightInchesLayout.visibility = if (isMetric) View.GONE else View.VISIBLE
        heightCmLayout.visibility = if (isMetric) View.VISIBLE else View.GONE
        if (isMetric) {
            heightCmInput.setText(heightCm.roundToInt().toString())
        } else {
            val totalInches = (heightCm / CM_PER_INCH).roundToInt()
            heightFeetInput.setText((totalInches / 12).toString())
            heightInchesInput.setText((totalInches % 12).toString())
        }

        shownWeightKg = weightKg
        shownWeightText = weightInput.text.toString()
        shownHeightCm = heightCm
        shownHeightText = heightFieldsText()
    }

    private fun heightFieldsText(): String =
        if (isMetric) heightCmInput.text.toString() else "${heightFeetInput.text}'${heightInchesInput.text}"

    // Reads the weight field in whichever units it's showing
    private fun readWeightKg(): Float? {
        val text = weightInput.text.toString()
        if (text == shownWeightText) return shownWeightKg
        val value = text.toFloatOrNull() ?: return null
        return if (isMetric) value else value * KG_PER_LB
    }

    private fun readHeightCm(): Float? {
        if (heightFieldsText() == shownHeightText) return shownHeightCm
        if (isMetric) return heightCmInput.text.toString().toFloatOrNull()
        val feet = heightFeetInput.text.toString().toIntOrNull() ?: return null
        val inches = (heightInchesInput.text.toString().toIntOrNull() ?: 0).coerceIn(0, 11)
        return (feet * 12 + inches) * CM_PER_INCH
    }

    // "130" rather than "130.0", but keeps one decimal when there is one
    private fun formatNumber(value: Float): String {
        val rounded = (value * 10).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    // Older builds stored weight as a pounds string and height as feet + inches (or, before that,
    // one decimal-feet value). Newer builds store both in metric.
    private fun loadWeightKg(prefs: SharedPreferences): Float {
        val kg = prefs.getFloat("WEIGHT_KG", -1f)
        if (kg > 0f) return kg
        val pounds = prefs.getString("WEIGHT", null)?.toFloatOrNull() ?: return DEFAULT_WEIGHT_KG
        return pounds * KG_PER_LB
    }

    private fun loadHeightCm(prefs: SharedPreferences): Float {
        val cm = prefs.getFloat("HEIGHT_CM", -1f)
        if (cm > 0f) return cm
        val feet = prefs.getString("HEIGHT_FT", null)?.toIntOrNull()
        val inches = prefs.getString("HEIGHT_IN", null)?.toIntOrNull()
        if (feet != null && inches != null) return (feet * 12 + inches) * CM_PER_INCH
        val legacyFeet = prefs.getString("HEIGHT", null)?.toFloatOrNull() ?: return DEFAULT_HEIGHT_CM
        return legacyFeet * 12 * CM_PER_INCH
    }

    // --- Accounts ---

    private fun signOut() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // A guest account can't be signed back into, so signing out would lose its workouts for good
    private fun confirmGuestSignOut() {
        AlertDialog.Builder(this)
            .setTitle("Sign out of guest account?")
            .setMessage("Guest accounts can't be signed back into, so your workouts would be lost. Create an account first to keep them.")
            .setPositiveButton("Create account") { _, _ -> showCreateAccountDialog() }
            .setNegativeButton("Sign out anyway") { _, _ -> signOut() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    // Attaches an email and password to the current guest account. The UID stays the same, so
    // workouts, the username and any leaderboard entry all carry over.
    private fun showCreateAccountDialog() {
        val user = auth.currentUser ?: return
        val view = layoutInflater.inflate(R.layout.dialog_create_account, null)
        val emailLayout = view.findViewById<TextInputLayout>(R.id.newEmailLayout)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.newPasswordLayout)
        val emailInput = view.findViewById<TextInputEditText>(R.id.newEmailInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.newPasswordInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create account")
            .setView(view)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Set the click listener after showing so a validation error keeps the dialog open
        dialog.setOnShowListener {
            val createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            createButton.setOnClickListener {
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                emailLayout.error = null
                passwordLayout.error = null

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailLayout.error = "Enter a valid email"
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    passwordLayout.error = "Use at least 6 characters"
                    return@setOnClickListener
                }

                createButton.isEnabled = false
                user.linkWithCredential(EmailAuthProvider.getCredential(email, password))
                    .addOnCompleteListener(this) { task ->
                        createButton.isEnabled = true
                        if (task.isSuccessful) {
                            dialog.dismiss()
                            findViewById<View>(R.id.guestBanner).visibility = View.GONE
                            Toast.makeText(this, "Account created. Your workouts are saved.", Toast.LENGTH_LONG).show()
                        } else {
                            when (task.exception) {
                                is FirebaseAuthUserCollisionException -> emailLayout.error = "That email already has an account"
                                is FirebaseAuthWeakPasswordException -> passwordLayout.error = "That password is too weak"
                                is FirebaseAuthInvalidCredentialsException -> emailLayout.error = "Enter a valid email"
                                else -> Toast.makeText(this, "Couldn't create account: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
            }
        }
        dialog.show()
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
