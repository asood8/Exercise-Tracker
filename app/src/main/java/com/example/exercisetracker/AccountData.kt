package com.example.exercisetracker

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Everything the app stores about an account, for "Download my data" and "Delete account". In the
// cloud that's the user's workouts, their leaderboard doc (users/{uid}) and their username claims
// (usernames/{name}). Both read from the server rather than the local cache, so nothing is missed.
object AccountData {
    private const val BATCH_LIMIT = 450 // Firestore allows 500 writes per batch

    // Calls done with the export as pretty-printed JSON, or null if it couldn't be collected
    fun export(context: Context, db: FirebaseFirestore, user: FirebaseUser, done: (String?) -> Unit) {
        val uid = user.uid
        val workouts = db.collection("workouts").whereEqualTo("userId", uid).get(Source.SERVER)
        val entry = db.collection("users").document(uid).get(Source.SERVER)
        val names = usernameClaims(db, uid)

        Tasks.whenAllSuccess<Any>(workouts, entry, names)
            .addOnSuccessListener {
                val json = JSONObject()
                json.put("exportedAt", iso(Date()))
                json.put("account", JSONObject().apply {
                    put("uid", uid)
                    put("email", user.email ?: JSONObject.NULL)
                    put("guest", user.isAnonymous)
                    put("emailVerified", user.isEmailVerified)
                    user.metadata?.let { put("createdAt", iso(Date(it.creationTimestamp))) }
                })
                json.put("profile", localProfile(context, uid))
                json.put("leaderboardEntry", entry.result.data?.let { mapToJson(it) } ?: JSONObject.NULL)
                json.put("usernames", JSONArray(names.result.map { it.id }))
                val sorted = workouts.result.documents.sortedBy { it.getTimestamp("timestamp")?.seconds ?: 0L }
                json.put("workouts", JSONArray(sorted.map { doc -> mapToJson(doc.data.orEmpty()).put("id", doc.id) }))
                done(json.toString(2))
            }
            .addOnFailureListener { done(null) }
    }

    // Deletes the account's workouts, leaderboard entry and username claims. done gets null on
    // success. The Firebase Auth account itself is deleted separately, afterwards.
    fun deleteCloudData(db: FirebaseFirestore, uid: String, done: (Exception?) -> Unit) {
        val workouts = db.collection("workouts").whereEqualTo("userId", uid).get(Source.SERVER)
        val names = usernameClaims(db, uid)

        Tasks.whenAllSuccess<Any>(workouts, names)
            .onSuccessTask {
                val refs = workouts.result.documents.map { it.reference } +
                    names.result.map { it.reference } +
                    db.collection("users").document(uid)
                val commits = refs.chunked(BATCH_LIMIT).map { chunk ->
                    db.batch().apply { chunk.forEach { delete(it) } }.commit()
                }
                Tasks.whenAll(commits)
            }
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it) }
    }

    // Removes what this device remembers about the account. Body stats, units and custom routines
    // are device settings rather than account data, so they stay.
    fun clearLocalData(context: Context, uid: String) {
        Username.forget(context, uid)
        context.getSharedPreferences("LeaderboardPrefs", Context.MODE_PRIVATE).edit().remove("level_$uid").apply()
        context.getSharedPreferences("AchievementPrefs", Context.MODE_PRIVATE).edit().remove("seen_$uid").apply()
        context.getSharedPreferences("WorkoutPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        Streaks.clear(context)
        StreakReminders.disable(context)
    }

    // The account's username claims. If the rules don't let the app read the usernames collection
    // (a project set up before usernames were unique), it couldn't have claimed or deleted any
    // either, so that's treated as no claims rather than failing the whole export or deletion.
    private fun usernameClaims(db: FirebaseFirestore, uid: String): Task<List<DocumentSnapshot>> =
        db.collection("usernames").whereEqualTo("uid", uid).get(Source.SERVER)
            .continueWith { task -> if (task.isSuccessful) task.result.documents else emptyList() }

    private fun localProfile(context: Context, uid: String): JSONObject {
        val prefs = context.getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("username", Username.get(context, uid))
            put("units", prefs.getString("UNITS", "imperial"))
            prefs.getFloat("WEIGHT_KG", -1f).takeIf { it > 0f }?.let { put("weightKg", it.toDouble()) }
            prefs.getFloat("HEIGHT_CM", -1f).takeIf { it > 0f }?.let { put("heightCm", it.toDouble()) }
            put("age", prefs.getString("AGE", null) ?: JSONObject.NULL)
            put("gender", prefs.getString("GENDER", null) ?: JSONObject.NULL)
            put("streakRemindersOn", Streaks.remindersEnabled(context))
            put("customRoutines", JSONArray(Routines.custom(context).map { JSONObject(it.toJson()) }))
        }
    }

    private fun mapToJson(map: Map<*, *>): JSONObject =
        JSONObject().apply { map.forEach { (key, value) -> put(key.toString(), toJson(value)) } }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Timestamp -> iso(value.toDate())
        is Map<*, *> -> mapToJson(value)
        is List<*> -> JSONArray(value.map { toJson(it) })
        is Number, is String, is Boolean -> value
        else -> value.toString()
    }

    private fun iso(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}
