package com.example.exercisetracker

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var titleText: TextView
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var submitButton: Button
    private lateinit var switchModeButton: Button
    private lateinit var forgotPasswordButton: Button
    private lateinit var guestButton: Button

    // Logging in never creates an account; "Sign up" switches the screen into create-account mode
    private var isSignUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Auto-login if already signed in
        if (auth.currentUser != null) {
            goToHome()
            return
        }

        setContentView(R.layout.activity_login)

        titleText = findViewById(R.id.loginTitle)
        emailLayout = findViewById(R.id.emailInputLayout)
        passwordLayout = findViewById(R.id.passwordInputLayout)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        submitButton = findViewById(R.id.loginButton)
        switchModeButton = findViewById(R.id.signUpButton)
        forgotPasswordButton = findViewById(R.id.forgotPasswordButton)
        guestButton = findViewById(R.id.skipButton)

        submitButton.setOnClickListener { submit() }
        forgotPasswordButton.setOnClickListener { sendPasswordReset() }
        switchModeButton.setOnClickListener {
            isSignUp = !isSignUp
            updateMode()
        }

        // Guests are signed in anonymously, so they still get a real UID and can save to the cloud
        guestButton.setOnClickListener {
            setBusy(true)
            auth.signInAnonymously().addOnCompleteListener(this) { task ->
                setBusy(false)
                if (task.isSuccessful) {
                    goToHome()
                } else {
                    Toast.makeText(this, "Couldn't continue as guest: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        updateMode()
    }

    private fun updateMode() {
        titleText.text = if (isSignUp) "Create your\naccount" else "Welcome to\nExercise Tracker"
        submitButton.text = if (isSignUp) "Create account" else "Log in"
        switchModeButton.text = if (isSignUp) "Already have an account? Log in" else "Don't have an account? Sign up"
        passwordLayout.helperText = if (isSignUp) PasswordRules.HINT else null
        forgotPasswordButton.visibility = if (isSignUp) View.GONE else View.VISIBLE
        emailLayout.error = null
        passwordLayout.error = null
    }

    private fun submit() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        emailLayout.error = null
        passwordLayout.error = null

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter a valid email"
            return
        }
        if (password.isEmpty()) {
            passwordLayout.error = "Enter your password"
            return
        }
        if (isSignUp) {
            PasswordRules.problemWith(password)?.let { problem ->
                passwordLayout.error = problem
                return
            }
        }

        setBusy(true)
        val task = if (isSignUp) auth.createUserWithEmailAndPassword(email, password)
                   else auth.signInWithEmailAndPassword(email, password)
        task.addOnCompleteListener(this) { result ->
            setBusy(false)
            if (!result.isSuccessful) {
                showAuthError(result.exception)
                return@addOnCompleteListener
            }
            if (isSignUp) {
                // Verifying proves the email is theirs, and lets the real owner reset the password
                result.result?.user?.sendEmailVerification()
                Toast.makeText(this, "Account created. Check your email for a verification link.", Toast.LENGTH_LONG).show()
            }
            goToHome()
        }
    }

    // The message is the same whether or not the email has an account, so this can't be used to
    // find out who's signed up
    private fun sendPasswordReset() {
        val email = emailInput.text.toString().trim()
        emailLayout.error = null
        passwordLayout.error = null
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter your email first"
            return
        }

        setBusy(true)
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this) { task ->
            setBusy(false)
            val message = if (task.isSuccessful || task.exception is FirebaseAuthInvalidUserException) {
                "If an account uses that email, a reset link is on its way."
            } else {
                "Couldn't send the reset link. Check your connection and try again."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showAuthError(error: Exception?) {
        when (error) {
            is FirebaseAuthUserCollisionException ->
                emailLayout.error = "That email already has an account. Log in instead."
            is FirebaseAuthWeakPasswordException ->
                passwordLayout.error = "That password is too weak"
            is FirebaseAuthInvalidUserException ->
                emailLayout.error = "No account uses that email. Tap Sign up to create one."
            // Newer Firebase projects report a wrong password and an unknown email the same way
            is FirebaseAuthInvalidCredentialsException ->
                if (isSignUp) emailLayout.error = "Enter a valid email"
                else passwordLayout.error = "Email or password is incorrect"
            is FirebaseTooManyRequestsException ->
                Toast.makeText(this, "Too many attempts. Try again in a few minutes.", Toast.LENGTH_LONG).show()
            is FirebaseNetworkException ->
                Toast.makeText(this, "Couldn't reach the server. Check your connection.", Toast.LENGTH_LONG).show()
            else ->
                Toast.makeText(this, "Something went wrong: ${error?.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setBusy(busy: Boolean) {
        submitButton.isEnabled = !busy
        switchModeButton.isEnabled = !busy
        forgotPasswordButton.isEnabled = !busy
        guestButton.isEnabled = !busy
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
