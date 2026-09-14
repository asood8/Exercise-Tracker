package com.example.exercisetracker

import com.google.firebase.firestore.DocumentSnapshot
import kotlin.math.roundToInt

// A saved workout, reduced to what goal suggestions need. counts are reps, or seconds for plank.
// scores are per-exercise form scores, which older workouts don't have.
data class PastSession(
    val timeMs: Long,
    val counts: Map<Exercise, Int>,
    val scores: Map<Exercise, Int>,
    val overallScore: Int,
    val goal: PastGoal?
)

data class PastGoal(val exercise: Exercise, val sets: Int, val target: Int, val setsDone: Int)

data class GoalSuggestion(val sets: Int, val target: Int, val reason: String)

// Suggests the next "sets × target" goal for an exercise from the last time it was done: a little
// more after a finished goal with good form, the same again when form slipped or it was close, and
// a little less when it was clearly too much.
object GoalSuggestions {
    private const val GOOD_FORM = 70
    private const val DEFAULT_SETS = 3
    private const val MAX_TARGET = 999

    // sessions must be newest first
    fun suggest(exercise: Exercise, sessions: List<PastSession>): GoalSuggestion {
        val last = sessions.firstOrNull { (it.counts[exercise] ?: 0) > 0 }
            ?: return GoalSuggestion(
                DEFAULT_SETS, exercise.starterTarget,
                "New to ${exercise.displayName.lowercase()}? This is a good place to start."
            )

        // Older workouts only have an overall score, which is only about this exercise if it was the only one done
        val score = last.scores[exercise]
            ?: last.overallScore.takeIf { it >= 0 && last.counts.count { (_, count) -> count > 0 } == 1 }
        val formText = score?.let { " at $it% form" } ?: ""

        val goal = last.goal?.takeIf { it.exercise == exercise }
        if (goal != null) {
            val lastGoal = "${goal.sets} × ${exercise.formatTarget(goal.target)}"
            return when {
                goal.setsDone >= goal.sets && score != null && score < GOOD_FORM -> GoalSuggestion(
                    goal.sets, goal.target, "You finished $lastGoal last time, but form was $score%. Repeat it with cleaner reps."
                )
                goal.setsDone >= goal.sets -> GoalSuggestion(
                    goal.sets, stepUp(exercise, goal.target), "You finished $lastGoal last time$formText, so go up a little."
                )
                goal.setsDone >= goal.sets - 1 -> GoalSuggestion(
                    goal.sets, goal.target, "You got ${goal.setsDone} of ${goal.sets} sets of $lastGoal last time. Have another go."
                )
                else -> GoalSuggestion(
                    goal.sets, stepDown(exercise, goal.target),
                    "$lastGoal was a stretch last time (${goal.setsDone} of ${goal.sets} sets), so try a little less."
                )
            }
        }

        // No goal last time: spread what was done over three sets, with a bit extra
        val total = last.counts.getValue(exercise)
        val perSet = roundTarget(exercise, total * 1.1 / DEFAULT_SETS).coerceAtLeast(minTarget(exercise))
        val amount = if (exercise.isTimed) formatPlankTime(total) else "$total"
        return GoalSuggestion(DEFAULT_SETS, perSet.coerceAtMost(MAX_TARGET), "Based on the $amount you did last time$formText.")
    }

    fun fromDocument(doc: DocumentSnapshot): PastSession {
        val counts = Exercise.entries.associateWith { (doc.getLong(it.fieldName) ?: 0L).toInt() }
        val scores = (doc.get("scores") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val exercise = Exercise.fromField(key as? String) ?: return@mapNotNull null
            val score = (value as? Number)?.toInt() ?: return@mapNotNull null
            exercise to score
        }.toMap()
        val goal = (doc.get("goal") as? Map<*, *>)?.let { map ->
            val exercise = Exercise.fromField(map["exercise"] as? String) ?: return@let null
            PastGoal(
                exercise,
                (map["sets"] as? Number)?.toInt() ?: return@let null,
                (map["target"] as? Number)?.toInt() ?: return@let null,
                (map["setsDone"] as? Number)?.toInt() ?: 0
            )
        }
        val time = doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return PastSession(
            time?.toDate()?.time ?: 0L, counts, scores, (doc.getLong("overallScore") ?: -1L).toInt(), goal
        )
    }

    private fun stepUp(exercise: Exercise, target: Int): Int {
        val step = if (exercise.isTimed) 5 else 1
        return maxOf(target + step, roundTarget(exercise, target * 1.1)).coerceAtMost(MAX_TARGET)
    }

    private fun stepDown(exercise: Exercise, target: Int): Int =
        minOf(target - 1, roundTarget(exercise, target * 0.85)).coerceAtLeast(minTarget(exercise))

    // Plank targets are rounded to 5 seconds
    private fun roundTarget(exercise: Exercise, value: Double): Int =
        if (exercise.isTimed) (value / 5).roundToInt() * 5 else value.roundToInt()

    private fun minTarget(exercise: Exercise): Int = if (exercise.isTimed) 15 else 5
}
