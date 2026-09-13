package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

class PushUpTracker {
    var pushUpCount = 0
    private var stage: String? = null
    private var minElbowAngle = Double.MAX_VALUE
    private var maxElbowAngle = 0.0
    var inPushUpMotion = false
    private var initialBodyAngle: Double? = null

    val feedback = mutableListOf<String>()

    // Scoring
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()
    private val repTimer = RepTimer()

    // Thresholds
    private val downThreshold = 90.0
    private val upThreshold = 160.0
    private val minDepthRequired = 100.0
    private val maxExtensionRequired = 150.0
    private val bodyAngleTolerance = 20.0
    private val maxHipSag = 0.08f
    private val minHandRatio = 0.8f
    private val maxHandRatio = 1.5f

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return pushUpCount

        // Check form first
        if (!checkForm(landmarks)) {
            feedback.clear()
            feedback.add("Position not visible - show full body")
            return pushUpCount
        }

        val leftElbowAngle = AngleUtils.getAngle(result, 11, 13, 15)
        val rightElbowAngle = AngleUtils.getAngle(result, 12, 14, 16)

        if (leftElbowAngle == null || rightElbowAngle == null) {
            feedback.clear()
            feedback.add("Arms not visible")
            return pushUpCount
        }

        val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0

        // Check if in horizontal position
        val isHorizontal = checkHorizontalPosition(landmarks)
        if (!isHorizontal) {
            feedback.clear()
            feedback.add("Get in push-up position")
            return pushUpCount
        }

        // Form metrics
        val bodyAngle = calculateBodyAngle(landmarks)
        val hipSag = calculateHipSag(landmarks)
        val handRatio = calculateShoulderWidthRatio(landmarks)

        // Store initial body angle
        if (initialBodyAngle == null && bodyAngle != null) {
            initialBodyAngle = bodyAngle
        }

        // State machine
        when (stage) {
            null, "up" -> {
                if (avgElbowAngle > upThreshold) {
                    stage = "up"
                    minElbowAngle = Double.MAX_VALUE
                    maxElbowAngle = avgElbowAngle
                    inPushUpMotion = false
                } else if (avgElbowAngle < upThreshold) {
                    stage = "descending"
                    minElbowAngle = avgElbowAngle
                    inPushUpMotion = true
                    repTimer.start()
                }
            }

            "descending" -> {
                minElbowAngle = minOf(minElbowAngle, avgElbowAngle)
                if (avgElbowAngle < downThreshold) {
                    stage = "down"
                }
            }

            "down" -> {
                if (avgElbowAngle > downThreshold) {
                    stage = "ascending"
                    maxElbowAngle = avgElbowAngle
                }
            }

            "ascending" -> {
                maxElbowAngle = maxOf(maxElbowAngle, avgElbowAngle)
                if (avgElbowAngle > upThreshold) {
                    validateRep(bodyAngle, hipSag, handRatio)
                }
            }
        }

        updateLiveFeedback(avgElbowAngle, bodyAngle, hipSag, handRatio)
        return pushUpCount
    }

    private fun checkForm(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        val required = listOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)

        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        return required.all { getPresence(it) > 0.5f }
    }

    private fun checkHorizontalPosition(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        val shoulderAvgY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipAvgY = (landmarks[23].y() + landmarks[24].y()) / 2f
        return abs(shoulderAvgY - hipAvgY) < 0.25f
    }

    private fun calculateBodyAngle(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Double? {
        val shoulderMid = AngleUtils.Point(
            (landmarks[11].x() + landmarks[12].x()) / 2f,
            (landmarks[11].y() + landmarks[12].y()) / 2f
        )
        val hipMid = AngleUtils.Point(
            (landmarks[23].x() + landmarks[24].x()) / 2f,
            (landmarks[23].y() + landmarks[24].y()) / 2f
        )
        val ankleMid = AngleUtils.Point(
            (landmarks[27].x() + landmarks[28].x()) / 2f,
            (landmarks[27].y() + landmarks[28].y()) / 2f
        )

        val vec1 = floatArrayOf(shoulderMid.x - hipMid.x, shoulderMid.y - hipMid.y)
        val vec2 = floatArrayOf(ankleMid.x - hipMid.x, ankleMid.y - hipMid.y)

        val dot = vec1[0] * vec2[0] + vec1[1] * vec2[1]
        val mag1 = sqrt((vec1[0] * vec1[0] + vec1[1] * vec1[1]).toDouble())
        val mag2 = sqrt((vec2[0] * vec2[0] + vec2[1] * vec2[1]).toDouble())

        return if (mag1 > 0 && mag2 > 0) {
            val cosAngle = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
            Math.toDegrees(acos(cosAngle))
        } else null
    }

    private fun calculateHipSag(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        val shoulderMidY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipMidY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val kneeMidY = (landmarks[25].y() + landmarks[26].y()) / 2f
        val expectedHipY = (shoulderMidY + kneeMidY) / 2f
        return hipMidY - expectedHipY
    }

    private fun calculateShoulderWidthRatio(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        val shoulderWidth = abs(landmarks[12].x() - landmarks[11].x())
        val handWidth = abs(landmarks[16].x() - landmarks[15].x())
        return if (shoulderWidth > 0) handWidth / shoulderWidth else 1f
    }

    private fun validateRep(bodyAngle: Double?, hipSag: Float, handRatio: Float) {
        if (repTimer.isTooFast()) {
            stage = "up"
            inPushUpMotion = false
            return
        }

        var score = 100
        val repFeedback = mutableListOf<String>()

        // 1. Depth (30 pts)
        when {
            minElbowAngle > minDepthRequired -> {
                score -= 30
                repFeedback.add("Go lower!")
            }
            minElbowAngle > downThreshold + 10 -> {
                score -= 10
            }
        }

        // 2. Extension (20 pts)
        if (maxElbowAngle < maxExtensionRequired) {
            score -= 20
            repFeedback.add("Extend fully!")
        }

        // 3. Body alignment (25 pts)
        initialBodyAngle?.let { initial ->
            bodyAngle?.let { current ->
                if (abs(current - initial) > bodyAngleTolerance) {
                    score -= 25
                    repFeedback.add("Keep body straight!")
                }
            }
        }

        // 4. Hip stability (15 pts)
        if (abs(hipSag) > maxHipSag) {
            score -= 15
            if (hipSag > 0) repFeedback.add("Hips sagging!")
            else repFeedback.add("Don't pike hips!")
        }

        // 5. Hand position (10 pts)
        if (handRatio < minHandRatio || handRatio > maxHandRatio) {
            score -= 10
            repFeedback.add("Adjust hand width!")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Push-ups", score, repFeedback))
        pushUpCount++

        stage = "up"
        inPushUpMotion = false
    }

    private fun updateLiveFeedback(angle: Double, bodyAngle: Double?, hipSag: Float, handRatio: Float) {
        feedback.clear()
        feedback.add("Push-Ups: $pushUpCount (Last: $lastRepScore%)")
        feedback.add("Angle: ${angle.toInt()}°")

        if (inPushUpMotion) {
            // Real-time cues
            when (stage) {
                "descending" -> feedback.add("Lower chest to ground")
                "ascending" -> feedback.add("Push up!")
            }

            // Form warnings
            initialBodyAngle?.let { initial ->
                bodyAngle?.let { current ->
                    if (abs(current - initial) > bodyAngleTolerance) {
                        feedback.add("⚠ Straighten body!")
                    }
                }
            }

            if (abs(hipSag) > maxHipSag) {
                if (hipSag > 0) feedback.add("⚠ Engage core!")
                else feedback.add("⚠ Lower hips!")
            }

            if (handRatio < minHandRatio || handRatio > maxHandRatio) {
                feedback.add("⚠ Adjust hand width!")
            }
        }
    }
}