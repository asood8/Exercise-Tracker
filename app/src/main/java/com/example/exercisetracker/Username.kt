package com.example.exercisetracker

import android.content.Context

// Public display name shown on the leaderboard instead of the account email.
// Stored per account on the device and copied to the user's leaderboard doc when they share a workout.
object Username {
    const val MAX_LENGTH = 20

    private const val PREFS = "UserProfile"

    // Used until someone picks a name; stable per account and doesn't reveal anything about them
    fun defaultFor(uid: String): String = "Athlete ${uid.takeLast(4).uppercase()}"

    fun get(context: Context, uid: String): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(uid), null)
        return if (saved.isNullOrBlank()) defaultFor(uid) else saved
    }

    fun save(context: Context, uid: String, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(uid), username)
            .apply()
    }

    // Trims, collapses runs of whitespace and caps the length; an empty name falls back to the default
    fun clean(input: String, uid: String): String {
        val cleaned = input.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH)
        return cleaned.ifEmpty { defaultFor(uid) }
    }

    private fun key(uid: String) = "USERNAME_$uid"
}
