package com.example.exercisetracker

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

// Public display name shown on the leaderboard instead of the account email.
// Stored per account on the device and copied to the user's leaderboard doc when they share a workout.
// Names are unique ignoring case: each one is claimed in the shared `usernames` collection, whose doc
// id is the lowercase name and whose `uid` field is the owner. The Firestore rules only accept a
// leaderboard username that the writer has claimed.
object Username {
    const val MAX_LENGTH = 20
    private const val MIN_LENGTH = 3

    private const val PREFS = "UserProfile"
    private val ALLOWED = Regex("^[A-Za-z0-9 ._-]+$")
    // Names that could pass for the app or its staff
    private val RESERVED = listOf("admin", "moderator", "support", "official", "staff", "exercisetracker")

    enum class ClaimResult { CLAIMED, TAKEN, FAILED }

    // Used until someone picks a name; stable per account and doesn't reveal anything about them
    fun defaultFor(uid: String): String = "Athlete ${uid.takeLast(4).uppercase()}"

    fun get(context: Context, uid: String): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(prefKey(uid), null)
        return if (saved.isNullOrBlank()) defaultFor(uid) else saved
    }

    fun save(context: Context, uid: String, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(prefKey(uid), username)
            .apply()
    }

    // Used when an account is deleted
    fun forget(context: Context, uid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(prefKey(uid)).apply()
    }

    // Trims, collapses runs of whitespace and caps the length; an empty name falls back to the default
    fun clean(input: String, uid: String): String {
        val cleaned = input.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH)
        return cleaned.ifEmpty { defaultFor(uid) }
    }

    // Returns what's wrong with a name, or null if it can be used. Limiting the characters also
    // blocks look-alike Unicode tricks for impersonating someone.
    fun problemWith(name: String): String? {
        val squashed = name.lowercase().filter { it.isLetterOrDigit() }
        return when {
            name.length < MIN_LENGTH -> "Use at least $MIN_LENGTH characters"
            !ALLOWED.matches(name) -> "Use only letters, numbers, spaces, dots, dashes and underscores"
            RESERVED.any { squashed.startsWith(it) } -> "That name is reserved"
            else -> null
        }
    }

    // Claims a name for this account. Claiming a name you already own succeeds without writing.
    fun claim(db: FirebaseFirestore, uid: String, name: String, done: (ClaimResult) -> Unit) {
        val ref = db.collection("usernames").document(name.lowercase())
        db.runTransaction<Unit> { transaction ->
            val existing = transaction.get(ref)
            if (existing.exists() && existing.getString("uid") != uid) {
                throw FirebaseFirestoreException("Username taken", FirebaseFirestoreException.Code.ALREADY_EXISTS)
            }
            if (!existing.exists()) transaction.set(ref, mapOf("uid" to uid))
        }.addOnSuccessListener { done(ClaimResult.CLAIMED) }
            .addOnFailureListener { e ->
                val taken = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS
                done(if (taken) ClaimResult.TAKEN else ClaimResult.FAILED)
            }
    }

    // Frees a name this account no longer uses. The rules only allow deleting your own claim.
    fun release(db: FirebaseFirestore, name: String) {
        db.collection("usernames").document(name.lowercase()).delete()
    }

    private fun prefKey(uid: String) = "USERNAME_$uid"
}
