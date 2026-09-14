package com.example.exercisetracker

import android.content.Context
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// Streaks work on local calendar days. Days are numbered from the epoch in local time, which avoids
// the off-by-an-hour problems of subtracting 24 hours across daylight saving changes.
object Streaks {
    private const val PREFS = "StreakPrefs"
    private const val KEY_LAST_DAY = "lastWorkoutDay"
    private const val KEY_STREAK = "currentStreak"
    private const val KEY_REMINDERS = "remindersEnabled"

    fun dayNumber(timeMs: Long): Long {
        val offset = TimeZone.getDefault().getOffset(timeMs)
        return Math.floorDiv(timeMs + offset, TimeUnit.DAYS.toMillis(1))
    }

    fun today(): Long = dayNumber(System.currentTimeMillis())

    // Consecutive workout days ending today, or yesterday if there's no workout yet today
    fun current(days: Set<Long>): Int {
        var day = today()
        if (day !in days) day--
        var streak = 0
        while (day in days) {
            streak++
            day--
        }
        return streak
    }

    fun longest(days: Set<Long>): Int {
        var best = 0
        var run = 0
        var previous = Long.MIN_VALUE
        for (day in days.sorted()) {
            run = if (day == previous + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    // Saved locally so the reminder worker can check the streak without a network call
    fun saveState(context: Context, lastWorkoutDay: Long, currentStreak: Int) {
        prefs(context).edit()
            .putLong(KEY_LAST_DAY, lastWorkoutDay)
            .putInt(KEY_STREAK, currentStreak)
            .apply()
    }

    // Called right after a workout is saved, before History has recomputed anything
    fun recordWorkoutToday(context: Context) {
        val today = today()
        val last = lastWorkoutDay(context)
        val streak = prefs(context).getInt(KEY_STREAK, 0)
        val newStreak = when (last) {
            today -> maxOf(streak, 1)
            today - 1 -> streak + 1
            else -> 1
        }
        saveState(context, today, newStreak)
    }

    fun lastWorkoutDay(context: Context): Long = prefs(context).getLong(KEY_LAST_DAY, Long.MIN_VALUE)

    fun currentStreak(context: Context): Int = prefs(context).getInt(KEY_STREAK, 0)

    fun remindersEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_REMINDERS, false)

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDERS, enabled).apply()
    }

    // Used when an account is deleted
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
