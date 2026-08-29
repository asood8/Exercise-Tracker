package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs

class LungeTracker {
    var lungeCount = 0
    private var stage: String? = null // "up", "down"
    private var minKneeAngle = Double.MAX_VALUE
    var inMotion = false
    
    val feedback = mutableListOf<String>()
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()

    // Thresholds
    private val downThreshold = 110.0 // Knee angle for deep lunge
    private val upThreshold = 160.0   // Standing up
    private val minDepthRequired = 120.0

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return lungeCount

        val leftKneeAngle = AngleUtils.getAngle(result, 23, 25, 27)
        val rightKneeAngle = AngleUtils.getAngle(result, 24, 26, 28)

        if (leftKneeAngle == null || rightKneeAngle == null) {
            feedback.clear()
            feedback.add("Legs not visible")
            return lungeCount
        }

        // We track the leg that is bending more (the back or front leg depending on view)
        val currentKneeAngle = minOf(leftKneeAngle, rightKneeAngle)

        when (stage) {
            null, "up" -> {
                if (currentKneeAngle > upThreshold) {
                    stage = "up"
                    minKneeAngle = Double.MAX_VALUE
                    inMotion = false
                } else if (currentKneeAngle < upThreshold) {
                    stage = "descending"
                    minKneeAngle = currentKneeAngle
                    inMotion = true
                }
            }
            "descending" -> {
                minKneeAngle = minOf(minKneeAngle, currentKneeAngle)
                if (currentKneeAngle < downThreshold) {
                    stage = "down"
                }
            }
            "down" -> {
                if (currentKneeAngle > downThreshold) {
                    stage = "ascending"
                }
            }
            "ascending" -> {
                if (currentKneeAngle > upThreshold) {
                    validateRep()
                    stage = "up"
                    inMotion = false
                }
            }
        }

        updateLiveFeedback(currentKneeAngle)
        return lungeCount
    }

    private fun validateRep() {
        var score = 100
        val repFeedback = mutableListOf<String>()

        if (minKneeAngle > minDepthRequired) {
            score -= 40
            repFeedback.add("Go lower!")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Lunges", score, repFeedback))
        lungeCount++
    }

    private fun updateLiveFeedback(angle: Double) {
        feedback.clear()
        feedback.add("Lunges: $lungeCount (Last: $lastRepScore%)")
        feedback.add("Knee Angle: ${angle.toInt()}°")
        if (inMotion && angle > minDepthRequired) {
            feedback.add("⚠ Lower your hips!")
        }
    }
}
