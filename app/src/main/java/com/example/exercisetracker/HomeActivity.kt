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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException
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

    private lateinit var exerciseChips: ChipGroup
    private lateinit var routineChips: ChipGroup
    private lateinit var goalSetsInput: TextInputEditText
    private lateinit var goalTargetInput: TextInputEditText

    // Past workouts, newest first, for goal suggestions. No suggestion is shown until they've loaded.
    private var pastSessions = listOf<PastSession>()
    private var suggestionsReady = false

    // "Download my data": the export is built first, then saved wherever the user picks
    private var pendingExport: String? = null
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri == null || json == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(this, "Your data was saved", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Couldn't save the file: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Couldn't save the file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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
        val usernameInputLayout = findViewById<TextInputLayout>(R.id.usernameInputLayout)
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
        exerciseChips = findViewById(R.id.exerciseChips)
        routineChips = findViewById(R.id.routineChips)
        goalSetsInput = findViewById(R.id.goalSetsInput)
        goalTargetInput = findViewById(R.id.goalTargetInput)
        val goalTargetLayout = findViewById<TextInputLayout>(R.id.goalTargetLayout)
        val workoutTypeToggle = findViewById<MaterialButtonToggleGroup>(R.id.workoutTypeToggle)

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
            goalTargetLayout.hint = if (selectedExercise().isTimed) "Seconds per set" else "Reps per set"
        }
        updateGoalHint()
        exerciseChips.setOnCheckedStateChangeListener { _, _ ->
            updateGoalHint()
            showGoalSuggestion()
        }
        findViewById<Button>(R.id.useSuggestionButton).setOnClickListener { useGoalSuggestion() }

        // One exercise (with an optional goal), or a routine
        showRoutineChips(sharedPrefs.getString("ROUTINE", null))
        val routineMode = sharedPrefs.getString("WORKOUT_MODE", "single") == "routine"
        workoutTypeToggle.check(if (routineMode) R.id.workoutTypeRoutine else R.id.workoutTypeSingle)
        showWorkoutType(routineMode)
        workoutTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) showWorkoutType(checkedId == R.id.workoutTypeRoutine)
        }
        routineChips.setOnCheckedStateChangeListener { _, _ -> showRoutineDetails() }
        findViewById<Button>(R.id.newRoutineButton).setOnClickListener {
            RoutineBuilder.show(this) { routine ->
                Routines.saveCustom(this, routine)
                showRoutineChips(routine.name)
            }
        }
        findViewById<Button>(R.id.deleteRoutineButton).setOnClickListener { confirmDeleteRoutine() }

        fetchQuickStats()

        findViewById<View>(R.id.guestBanner).visibility =
            if (auth.currentUser?.isAnonymous == true) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.createAccountButton).setOnClickListener { showCreateAccountDialog() }

        findViewById<Button>(R.id.resendVerificationButton).setOnClickListener { resendVerificationEmail() }
        findViewById<Button>(R.id.checkVerificationButton).setOnClickListener { refreshVerificationBanner(showResult = true) }
        refreshVerificationBanner(showResult = false)

        signOutButton.setOnClickListener {
            if (auth.currentUser?.isAnonymous == true) confirmGuestSignOut() else signOut()
        }
        findViewById<Button>(R.id.exportDataButton).setOnClickListener { exportData() }
        findViewById<Button>(R.id.deleteAccountButton).setOnClickListener { confirmDeleteAccount() }

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
            // Check a changed username first, so a bad one gets fixed before the workout starts
            val user = auth.currentUser
            var renameTo: String? = null
            if (user != null) {
                val username = Username.clean(usernameInput.text.toString(), user.uid)
                if (username != Username.get(this, user.uid)) {
                    Username.problemWith(username)?.let { problem ->
                        usernameInputLayout.error = problem
                        return@setOnClickListener
                    }
                    renameTo = username
                }
                usernameInputLayout.error = null
                usernameInput.setText(username)
            }

            val weightKg = readWeightKg() ?: DEFAULT_WEIGHT_KG
            val heightCm = readHeightCm() ?: DEFAULT_HEIGHT_CM
            val ageStr = ageInput.text.toString()
            val age = ageStr.toIntOrNull() ?: 18

            val selectedGenderId = genderGroup.checkedRadioButtonId
            val gender = if (selectedGenderId == R.id.genderFemale) "female" else "male"

            val isRoutine = workoutTypeToggle.checkedButtonId == R.id.workoutTypeRoutine
            val routine = selectedRoutine()
            val goalSetsStr = goalSetsInput.text.toString()
            val goalTargetStr = goalTargetInput.text.toString()
            val goalSets = goalSetsStr.toIntOrNull()?.coerceIn(0, 20) ?: 0
            val goalTarget = goalTargetStr.toIntOrNull()?.coerceIn(0, 999) ?: 0
            // A routine, a "3 × 15" goal (which is a one-step routine), or no plan at all
            val plan = when {
                isRoutine -> routine
                goalSets > 0 && goalTarget > 0 -> Routine.goal(selectedExercise(), goalSets, goalTarget)
                else -> null
            }
            val exercise = plan?.steps?.first()?.exercise ?: selectedExercise()

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
                putString("EXERCISE", selectedExercise().name)
                putString("WORKOUT_MODE", if (isRoutine) "routine" else "single")
                putString("ROUTINE", routine.name)
                putString("GOAL_SETS", goalSetsStr)
                putString("GOAL_TARGET", goalTargetStr)
                apply()
            }

            if (user != null && renameTo != null) renameUsername(user.uid, Username.get(this, user.uid), renameTo)

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("WEIGHT_KG", weightKg)
                putExtra("HEIGHT_CM", heightCm)
                putExtra("AGE", age)
                putExtra("GENDER", gender)
                putExtra("EXERCISE", exercise.name)
                plan?.let { putExtra("ROUTINE", it.toJson()) }
            }
            startActivity(intent)
        }
    }

    private fun selectedExercise(): Exercise =
        exerciseChips.findViewById<Chip>(exerciseChips.checkedChipId)?.tag as? Exercise ?: Exercise.SQUATS

    // --- Routines and goals ---

    private fun showWorkoutType(isRoutine: Boolean) {
        findViewById<View>(R.id.singleSection).visibility = if (isRoutine) View.GONE else View.VISIBLE
        findViewById<View>(R.id.routineSection).visibility = if (isRoutine) View.VISIBLE else View.GONE
    }

    // One chip per routine, built-in ones first, with `select` (or the first) selected
    private fun showRoutineChips(select: String?) {
        routineChips.removeAllViews()
        val routines = Routines.all(this)
        val selected = routines.firstOrNull { it.name == select } ?: routines.first()
        for (routine in routines) {
            val chip = layoutInflater.inflate(R.layout.item_exercise_chip, routineChips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = routine.name
            chip.tag = routine
            routineChips.addView(chip)
            if (routine == selected) routineChips.check(chip.id)
        }
        showRoutineDetails()
    }

    private fun selectedRoutine(): Routine =
        routineChips.findViewById<Chip>(routineChips.checkedChipId)?.tag as? Routine ?: Routines.presets.first()

    private fun showRoutineDetails() {
        val routine = selectedRoutine()
        val rest = if (routine.restSeconds > 0) "${routine.restSeconds}s rest between sets" else "No rest between sets"
        findViewById<TextView>(R.id.routineDescription).text = "${routine.describe()}\n$rest · ${routine.placementNote()}"
        findViewById<View>(R.id.deleteRoutineButton).visibility =
            if (Routines.isPreset(routine.name)) View.GONE else View.VISIBLE
    }

    private fun confirmDeleteRoutine() {
        val routine = selectedRoutine()
        AlertDialog.Builder(this)
            .setTitle("Delete \"${routine.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                Routines.deleteCustom(this, routine.name)
                showRoutineChips(null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // A goal for the selected exercise, based on the last time it was done
    private fun showGoalSuggestion() {
        if (!suggestionsReady) return
        val exercise = selectedExercise()
        val suggestion = GoalSuggestions.suggest(exercise, pastSessions)
        findViewById<View>(R.id.suggestionRow).visibility = View.VISIBLE
        findViewById<TextView>(R.id.suggestionText).text =
            "Try ${suggestion.sets} × ${exercise.formatTarget(suggestion.target)}. ${suggestion.reason}"
    }

    private fun useGoalSuggestion() {
        val suggestion = GoalSuggestions.suggest(selectedExercise(), pastSessions)
        goalSetsInput.setText(suggestion.sets.toString())
        goalTargetInput.setText(suggestion.target.toString())
    }

    // Usernames are unique, so a new one is claimed before it's saved. This runs in the background
    // so starting a workout never waits on the network. If the claim fails, the old name is kept.
    private fun renameUsername(uid: String, oldName: String, newName: String) {
        val appContext = applicationContext
        Username.claim(db, uid, newName) { result ->
            when (result) {
                Username.ClaimResult.CLAIMED -> {
                    Username.save(appContext, uid, newName)
                    val sameName = oldName.lowercase() == newName.lowercase()
                    // Only an existing leaderboard entry is renamed, so this can't create one for someone
                    // who never opted in. It checks first because the rules refuse update() on a missing
                    // doc (PERMISSION_DENIED) rather than letting it fail with NOT_FOUND. The old name is
                    // only released once the entry no longer uses it, since the rules require the
                    // entry's name to be claimed.
                    val entryRef = db.collection("users").document(uid)
                    entryRef.get().addOnSuccessListener { entry ->
                        if (!entry.exists()) {
                            if (!sameName) Username.release(db, oldName)
                            return@addOnSuccessListener
                        }
                        entryRef.update(mapOf("username" to newName, "email" to FieldValue.delete()))
                            .addOnSuccessListener { if (!sameName) Username.release(db, oldName) }
                    }
                }
                Username.ClaimResult.TAKEN ->
                    Toast.makeText(appContext, "\"$newName\" is already taken, so your username wasn't changed", Toast.LENGTH_LONG).show()
                Username.ClaimResult.FAILED ->
                    Toast.makeText(appContext, "Couldn't save your new username, so it wasn't changed", Toast.LENGTH_LONG).show()
            }
        }
    }

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

    // Email accounts see a reminder until they've clicked the link in the verification email.
    // Reloading the user picks up a verification done in the email app.
    private fun refreshVerificationBanner(showResult: Boolean) {
        val user = auth.currentUser ?: return
        val banner = findViewById<View>(R.id.verifyBanner)
        if (user.isAnonymous || user.email.isNullOrEmpty()) {
            banner.visibility = View.GONE
            return
        }
        user.reload().addOnCompleteListener(this) {
            val verified = auth.currentUser?.isEmailVerified == true
            banner.visibility = if (verified) View.GONE else View.VISIBLE
            findViewById<TextView>(R.id.verifyBannerText).text =
                "We sent a link to ${user.email}. Verifying lets you reset your password if you ever forget it."
            if (showResult) {
                val message = if (verified) "Email verified" else "Not verified yet. Check your inbox, and your spam folder."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.addOnCompleteListener(this) { task ->
            val message = if (task.isSuccessful) "Verification link sent" else "Couldn't send the link. Try again in a few minutes."
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
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
                PasswordRules.problemWith(password)?.let { problem ->
                    passwordLayout.error = problem
                    return@setOnClickListener
                }

                createButton.isEnabled = false
                user.linkWithCredential(EmailAuthProvider.getCredential(email, password))
                    .addOnCompleteListener(this) { task ->
                        createButton.isEnabled = true
                        if (task.isSuccessful) {
                            dialog.dismiss()
                            findViewById<View>(R.id.guestBanner).visibility = View.GONE
                            auth.currentUser?.sendEmailVerification()
                            refreshVerificationBanner(showResult = false)
                            Toast.makeText(this, "Account created. Check your email for a verification link.", Toast.LENGTH_LONG).show()
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

                // An empty result from the offline cache says nothing about past workouts, so it
                // shouldn't produce a "new to this exercise" suggestion
                if (!(documents.isEmpty && documents.metadata.isFromCache)) {
                    pastSessions = documents.map { GoalSuggestions.fromDocument(it) }.sortedByDescending { it.timeMs }
                    suggestionsReady = true
                    showGoalSuggestion()
                }
            }
    }

    // --- Your data ---

    private fun exportData() {
        val user = auth.currentUser ?: return
        val button = findViewById<Button>(R.id.exportDataButton)
        button.isEnabled = false
        Toast.makeText(this, "Collecting your data…", Toast.LENGTH_SHORT).show()
        AccountData.export(this, db, user) { json ->
            if (isFinishing || isDestroyed) return@export
            button.isEnabled = true
            if (json == null) {
                Toast.makeText(this, "Couldn't collect your data. Check your connection and try again.", Toast.LENGTH_LONG).show()
                return@export
            }
            pendingExport = json
            exportLauncher.launch("exercise-tracker-data.json")
        }
    }

    private fun confirmDeleteAccount() {
        val user = auth.currentUser ?: return
        if (!Connectivity.isOnline(this)) {
            Toast.makeText(this, "You need to be online to delete your account", Toast.LENGTH_LONG).show()
            return
        }
        val warning = "This permanently deletes your account, all your workouts, your leaderboard entry and your username. It can't be undone."
        val email = user.email
        if (user.isAnonymous || email.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Delete account?")
                .setMessage(warning)
                .setPositiveButton("Delete") { _, _ -> deleteAccount() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        // Firebase only deletes an account after a recent sign-in, so the password is checked first
        val view = layoutInflater.inflate(R.layout.dialog_confirm_password, null)
        view.findViewById<TextView>(R.id.confirmPasswordMessage).text = "$warning\n\nEnter your password to confirm."
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.confirmPasswordLayout)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.confirmPasswordInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete account?")
            .setView(view)
            .setPositiveButton("Delete", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Set the click listener after showing so a wrong password keeps the dialog open
        dialog.setOnShowListener {
            val deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            deleteButton.setOnClickListener {
                val password = passwordInput.text.toString()
                passwordLayout.error = null
                if (password.isEmpty()) {
                    passwordLayout.error = "Enter your password"
                    return@setOnClickListener
                }
                deleteButton.isEnabled = false
                user.reauthenticate(EmailAuthProvider.getCredential(email, password))
                    .addOnCompleteListener(this) { task ->
                        deleteButton.isEnabled = true
                        when {
                            task.isSuccessful -> {
                                dialog.dismiss()
                                deleteAccount()
                            }
                            task.exception is FirebaseAuthInvalidCredentialsException -> passwordLayout.error = "Incorrect password"
                            task.exception is FirebaseTooManyRequestsException -> passwordLayout.error = "Too many attempts. Try again later."
                            else -> Toast.makeText(this, "Couldn't check your password: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
        dialog.show()
    }

    // Cloud data goes first while the account can still reach it, then the login itself
    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val progress = AlertDialog.Builder(this)
            .setMessage("Deleting your account…")
            .setCancelable(false)
            .show()

        AccountData.deleteCloudData(db, user.uid) { error ->
            if (error != null) {
                progress.dismiss()
                Toast.makeText(this, "Couldn't finish deleting your data. Check your connection and try again.", Toast.LENGTH_LONG).show()
                return@deleteCloudData
            }
            user.delete().addOnCompleteListener { task ->
                progress.dismiss()
                AccountData.clearLocalData(this, user.uid)
                auth.signOut()
                // A guest login that couldn't be removed has no data left and can't be signed back into
                val message = if (task.isSuccessful || user.isAnonymous) {
                    "Your account and data have been deleted"
                } else {
                    "Your data was deleted, but the login couldn't be removed. Log in and delete the account again to finish."
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }
    }
}
