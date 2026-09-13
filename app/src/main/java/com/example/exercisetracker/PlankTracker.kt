package com.example.exercisetracker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.hypot

class PlankTracker {
    var totalSeconds: Int = 0
    var isPlanking = false

    val feedback = mutableListOf<String>()

    // Scoring: each hold becomes one RepResult, scored by the share of it spent in good form
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()

    // Thresholds
    private val maxVerticalDiff = 0.2f   // Shoulders vs hips vs ankles, to count as horizontal
    private val maxHipDeviation = 0.08f  // Hips off the shoulder-ankle line, as a fraction of body length
    private val minScoredHoldMs = 5000L  // Shorter holds add time but don't get a score
    private val breakGraceMs = 1000L     // Brief tracking drops don't end the hold

    private var holdStartMs = 0L
    private var lastPlankingMs = 0L
    private var completedMs = 0L         // Time from holds that have already ended
    private var totalFrames = 0
    private var goodFrames = 0
    private var sagFrames = 0
    private var pikeFrames = 0

    fun update(result: PoseLandmarkerResult): Int {
        val now = System.currentTimeMillis()
        val landmarks = result.landmarks().firstOrNull()

        if (landmarks == null || !checkForm(landmarks) || !isHorizontal(landmarks)) {
            if (isPlanking && now - lastPlankingMs > breakGraceMs) endHold()
            updateLiveFeedback(null)
            return totalSeconds
        }

        if (!isPlanking) startHold(now)
        lastPlankingMs = now

        val hipDeviation = calculateHipDeviation(landmarks)
        totalFrames++
        when {
            hipDeviation == null || abs(hipDeviation) <= maxHipDeviation -> goodFrames++
            hipDeviation > 0 -> sagFrames++
            else -> pikeFrames++
        }

        totalSeconds = ((completedMs + (now - holdStartMs)) / 1000).toInt()
        updateLiveFeedback(hipDeviation)
        return totalSeconds
    }

    // Ends a hold that's still going, e.g. when the workout ends mid-plank, so it gets scored
    fun finish() {
        if (isPlanking) endHold()
    }

    private fun startHold(now: Long) {
        isPlanking = true
        holdStartMs = now
        totalFrames = 0
        goodFrames = 0
        sagFrames = 0
        pikeFrames = 0
    }

    private fun endHold() {
        isPlanking = false
        val holdMs = lastPlankingMs - holdStartMs
        completedMs += holdMs
        totalSeconds = (completedMs / 1000).toInt()
        if (holdMs < minScoredHoldMs || totalFrames == 0) return

        val score = 100 * goodFrames / totalFrames
        val repFeedback = mutableListOf<String>()
        if (sagFrames > totalFrames / 4) repFeedback.add("Hips sagging!")
        if (pikeFrames > totalFrames / 4) repFeedback.add("Hips too high!")

        lastRepScore = score
        sessionReps.add(RepResult("Plank", score, repFeedback))
    }

    private fun checkForm(landmarks: List<NormalizedLandmark>): Boolean {
        val required = listOf(11, 12, 23, 24, 27, 28)

        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        return required.all { getPresence(it) > 0.5f }
    }

    private fun isHorizontal(landmarks: List<NormalizedLandmark>): Boolean {
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val ankleY = (landmarks[27].y() + landmarks[28].y()) / 2f
        return abs(shoulderY - hipY) < maxVerticalDiff && abs(hipY - ankleY) < maxVerticalDiff
    }

    // How far the hips sit off the straight shoulder-to-ankle line, relative to body length.
    // Positive = below the line (sagging), negative = above it (piking). Null when the camera
    // isn't side-on enough to judge.
    private fun calculateHipDeviation(landmarks: List<NormalizedLandmark>): Float? {
        val shoulderX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipX = (landmarks[23].x() + landmarks[24].x()) / 2f
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val ankleX = (landmarks[27].x() + landmarks[28].x()) / 2f
        val ankleY = (landmarks[27].y() + landmarks[28].y()) / 2f

        val bodyLength = hypot(ankleX - shoulderX, ankleY - shoulderY)
        if (bodyLength < 0.1f || abs(ankleX - shoulderX) < 0.05f) return null

        val t = (hipX - shoulderX) / (ankleX - shoulderX)
        val lineY = shoulderY + t * (ankleY - shoulderY)
        return (hipY - lineY) / bodyLength
    }

    private fun updateLiveFeedback(hipDeviation: Float?) {
        feedback.clear()
        feedback.add("Plank: ${formatPlankTime(totalSeconds)} (Last: $lastRepScore%)")
        when {
            !isPlanking -> feedback.add("Get in plank position")
            hipDeviation != null && hipDeviation > maxHipDeviation -> feedback.add("⚠ Lift your hips!")
            hipDeviation != null && hipDeviation < -maxHipDeviation -> feedback.add("⚠ Lower your hips!")
            else -> feedback.add("🔥 Holding strong!")
        }
    }
}
