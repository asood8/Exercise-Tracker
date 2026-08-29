package com.example.exercisetracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        
        // Auto-login if already signed in
        if (auth.currentUser != null) {
            goToHome()
            return
        }

        setContentView(R.layout.activity_login)

        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Try logging in
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            goToHome()
                        } else {
                            // If user doesn't exist, try creating account automatically for easier setup
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener(this) { createSTask ->
                                    if (createSTask.isSuccessful) {
                                        goToHome()
                                    } else {
                                        Toast.makeText(this, "Auth Error: ${createSTask.exception?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        // "Skip" button signs the user in anonymously.
        // This gives them a real UID so they can still save data to the cloud!
        findViewById<Button>(R.id.skipButton).setOnClickListener {
            auth.signInAnonymously().addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    goToHome()
                } else {
                    Toast.makeText(this, "Skip failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        findViewById<Button>(R.id.signUpButton).setOnClickListener {
            findViewById<Button>(R.id.loginButton).performClick()
        }
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
