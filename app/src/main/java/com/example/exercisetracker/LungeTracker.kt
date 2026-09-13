package com.example.exercisetracker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2

class LungeTracker {
    var lungeCount = 0
    private var stage: String? = null // "up", "descending", "down", "ascending"
    private var minKneeAngle = Double.MAX_VALUE
    var inMotion = false

    val feedback = mutableListOf<String>()
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()
    private val repTimer = RepTimer()

    // Thresholds
    private val downThreshold = 110.0 // Knee angle for deep lunge
    private val upThreshold = 160.0   // Standing up
    private val minDepthRequired = 120.0
    private val maxTorsoLean = 30.0   // Degrees from vertical
    private val maxShinAngle = 35.0   // Front shin from vertical; beyond this the knee is well past the toes

    // Form at the deepest point of the current rep
    private var torsoLeanAtBottom = 0.0
    private var shinAngleAtBottom = 0.0

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return lungeCount

        if (!checkForm(landmarks)) {
            feedback.clear()
            feedback.add("Legs not visible")
            return lungeCount
        }

        val leftKneeAngle = AngleUtils.getAngle(result, 23, 25, 27)
        val rightKneeAngle = AngleUtils.getAngle(result, 24, 26, 28)

        if (leftKneeAngle == null || rightKneeAngle == null) {
            feedback.clear()
            feedback.add("Legs not visible")
            return lungeCount
        }

        // We track the leg that is bending more (the back or front leg depending on view)
        val currentKneeAngle = minOf(leftKneeAngle, rightKneeAngle)
        val torsoLean = calculateTorsoLean(landmarks)
        // The more vertical shin belongs to the front leg
        val shinAngle = minOf(calculateShinAngle(landmarks, 25, 27), calculateShinAngle(landmarks, 26, 28))

        when (stage) {
            null, "up" -> {
                if (currentKneeAngle > upThreshold) {
                    stage = "up"
                    minKneeAngle = Double.MAX_VALUE
                    inMotion = false
                } else if (currentKneeAngle < upThreshold) {
                    stage = "descending"
                    inMotion = true
                    repTimer.start()
                    minKneeAngle = Double.MAX_VALUE
                    recordDepth(currentKneeAngle, torsoLean, shinAngle)
                }
            }
            "descending" -> {
                recordDepth(currentKneeAngle, torsoLean, shinAngle)
                if (currentKneeAngle < downThreshold) {
                    stage = "down"
                }
            }
            "down" -> {
                recordDepth(currentKneeAngle, torsoLean, shinAngle)
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

        updateLiveFeedback(currentKneeAngle, torsoLean, shinAngle)
        return lungeCount
    }

    // Keeps the form measurements from the deepest point of the rep
    private fun recordDepth(kneeAngle: Double, torsoLean: Double, shinAngle: Double) {
        if (kneeAngle < minKneeAngle) {
            minKneeAngle = kneeAngle
            torsoLeanAtBottom = torsoLean
            shinAngleAtBottom = shinAngle
        }
    }

    private fun checkForm(landmarks: List<NormalizedLandmark>): Boolean {
        val required = listOf(11, 12, 23, 24, 25, 26, 27, 28)

        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        return required.all { getPresence(it) > 0.5f }
    }

    // Degrees from vertical, 0 = upright. y grows downward, so use magnitudes.
    private fun calculateTorsoLean(landmarks: List<NormalizedLandmark>): Double {
        val dx = (landmarks[11].x() + landmarks[12].x()) / 2f - (landmarks[23].x() + landmarks[24].x()) / 2f
        val dy = (landmarks[11].y() + landmarks[12].y()) / 2f - (landmarks[23].y() + landmarks[24].y()) / 2f
        return Math.toDegrees(atan2(abs(dx).toDouble(), abs(dy).toDouble()))
    }

    // Degrees from vertical for the knee-to-ankle segment, 0 = shin straight up and down
    private fun calculateShinAngle(landmarks: List<NormalizedLandmark>, kneeIdx: Int, ankleIdx: Int): Double {
        val dx = landmarks[kneeIdx].x() - landmarks[ankleIdx].x()
        val dy = landmarks[kneeIdx].y() - landmarks[ankleIdx].y()
        return Math.toDegrees(atan2(abs(dx).toDouble(), abs(dy).toDouble()))
    }

    private fun validateRep() {
        if (repTimer.isTooFast()) return

        var score = 100
        val repFeedback = mutableListOf<String>()

        // 1. Depth (40 pts)
        if (minKneeAngle > minDepthRequired) {
            score -= 40
            repFeedback.add("Go lower!")
        }

        // 2. Upright torso (20 pts)
        if (torsoLeanAtBottom > maxTorsoLean) {
            score -= 20
            repFeedback.add("Keep torso upright!")
        }

        // 3. Front knee over the ankle (20 pts)
        if (shinAngleAtBottom > maxShinAngle) {
            score -= 20
            repFeedback.add("Keep front knee over ankle!")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Lunges", score, repFeedback))
        lungeCount++
    }

    private fun updateLiveFeedback(angle: Double, torsoLean: Double, shinAngle: Double) {
        feedback.clear()
        feedback.add("Lunges: $lungeCount (Last: $lastRepScore%)")
        feedback.add("Knee Angle: ${angle.toInt()}°")
        if (inMotion) {
            if (angle > minDepthRequired) feedback.add("⚠ Lower your hips!")
            if (torsoLean > maxTorsoLean) feedback.add("⚠ Keep torso upright!")
            if (shinAngle > maxShinAngle) feedback.add("⚠ Knee too far forward!")
        }
    }
}
