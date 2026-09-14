package com.example.exercisetracker

// Password requirements for new accounts (sign-up and guest upgrade). Existing accounts with older,
// shorter passwords can still log in.
object PasswordRules {
    const val HINT = "At least 8 characters, with a letter and a number"

    // Returns what's wrong with a password, or null if it's acceptable
    fun problemWith(password: String): String? = when {
        password.length < 8 -> "Use at least 8 characters"
        password.none { it.isLetter() } || password.none { it.isDigit() } -> "Include at least one letter and one number"
        else -> null
    }
}
