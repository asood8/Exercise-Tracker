package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2

class SquatTracker {
    var squatCount = 0
    private var stage: String? = null
    private var minKneeAngle = Double.MAX_VALUE
    private var hipStartHeight: Float? = null
    var inSquatMotion = false

    val feedback = mutableListOf<String>()

    // Scoring
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()

    // Thresholds
    private val downThreshold = 100.0
    private val upThreshold = 160.0
    private val minDepthRequired = 110.0
    private val maxTorsoLean = 45.0
    private val minHipDrop = 0.05f

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return squatCount

        // Check form first
        if (!checkForm(landmarks)) {
            feedback.clear()
            feedback.add("Position not visible - show full body")
            return squatCount
        }

        val leftKneeAngle = AngleUtils.getAngle(result, 23, 25, 27)
        val rightKneeAngle = AngleUtils.getAngle(result, 24, 26, 28)

        if (leftKneeAngle == null || rightKneeAngle == null) {
            feedback.clear()
            feedback.add("Legs not visible")
            return squatCount
        }

        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2.0

        // Form metrics
        val hipHeight = calculateHipHeight(landmarks)
        val torsoAngle = calculateTorsoAngle(landmarks)
        val kneeAlignment = checkKneeAlignment(landmarks)

        // Store initial hip height
        if (hipStartHeight == null && hipHeight != null && stage == "up") {
            hipStartHeight = hipHeight
        }

        // State machine
        when (stage) {
            null, "up" -> {
                if (avgKneeAngle > upThreshold) {
                    stage = "up"
                    minKneeAngle = Double.MAX_VALUE
                    inSquatMotion = false
                    if (hipHeight != null) hipStartHeight = hipHeight
                } else if (avgKneeAngle < upThreshold) {
                    stage = "descending"
                    minKneeAngle = avgKneeAngle
                    inSquatMotion = true
                }
            }

            "descending" -> {
                minKneeAngle = minOf(minKneeAngle, avgKneeAngle)
                if (avgKneeAngle < downThreshold) {
                    stage = "down"
                }
            }

            "down" -> {
                if (avgKneeAngle > downThreshold) {
                    stage = "ascending"
                }
            }

            "ascending" -> {
                if (avgKneeAngle > upThreshold) {
                    val hipDrop = calculateHipDrop(hipHeight)
                    validateRep(torsoAngle, hipDrop, kneeAlignment)
                }
            }
        }

        updateLiveFeedback(avgKneeAngle, torsoAngle, kneeAlignment, hipHeight)
        return squatCount
    }

    private fun checkForm(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        val required = listOf(23, 24, 25, 26, 27, 28, 11, 12)

        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        return required.all { getPresence(it) > 0.5f }
    }

    private fun calculateHipHeight(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float? {
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]

        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        return if (getPresence(23) > 0.5f && getPresence(24) > 0.5f) {
            (leftHip.y() + rightHip.y()) / 2f
        } else null
    }

    private fun calculateTorsoAngle(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Double? {
        val hipMidX = (landmarks[23].x() + landmarks[24].x()) / 2f
        val hipMidY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val shoulderMidX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val shoulderMidY = (landmarks[11].y() + landmarks[12].y()) / 2f

        val dx = shoulderMidX - hipMidX
        val dy = shoulderMidY - hipMidY

        // Angle from vertical (0 degrees = perfectly upright)
        return abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))
    }

    private fun checkKneeAlignment(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        // Check if knees track over toes (not caving in)
        val leftKneeX = landmarks[25].x()
        val rightKneeX = landmarks[26].x()
        val leftAnkleX = landmarks[27].x()
        val rightAnkleX = landmarks[28].x()

        // Knees should be roughly above ankles (allowing some forward travel)
        val leftAligned = abs(leftKneeX - leftAnkleX) < 0.15f
        val rightAligned = abs(rightKneeX - rightAnkleX) < 0.15f

        return leftAligned && rightAligned
    }

    private fun calculateHipDrop(currentHipHeight: Float?): Float {
        val start = hipStartHeight ?: return 0f
        val current = currentHipHeight ?: return 0f
        return start - current
    }

    private fun validateRep(torsoAngle: Double?, hipDrop: Float, kneeAlignment: Boolean) {
        var score = 100
        val repFeedback = mutableListOf<String>()

        // 1. Depth (40 pts)
        when {
            minKneeAngle > minDepthRequired -> {
                score -= 40
                repFeedback.add("Go deeper!")
            }
            minKneeAngle > downThreshold + 10 -> {
                score -= 15
                repFeedback.add("More depth needed")
            }
        }

        // 2. Hip drop validation (20 pts)
        if (hipDrop < minHipDrop) {
            score -= 20
            repFeedback.add("Drop hips lower!")
        }

        // 3. Torso angle (20 pts)
        torsoAngle?.let { angle ->
            when {
                angle > maxTorsoLean -> {
                    score -= 20
                    repFeedback.add("Keep chest up!")
                }
                angle > maxTorsoLean * 0.8 -> {
                    score -= 10
                }
            }
        }

        // 4. Knee alignment (20 pts)
        if (!kneeAlignment) {
            score -= 20
            repFeedback.add("Track knees over toes!")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Squats", score, repFeedback))
        squatCount++

        stage = "up"
        inSquatMotion = false
    }

    private fun updateLiveFeedback(angle: Double, torsoAngle: Double?, kneeAlignment: Boolean, hipHeight: Float?) {
        feedback.clear()
        feedback.add("Squats: $squatCount (Last: $lastRepScore%)")
        feedback.add("Angle: ${angle.toInt()}°")

        if (inSquatMotion) {
            // Real-time cues
            when (stage) {
                "descending" -> {
                    feedback.add("Sit back and down")

                    // Check depth in real-time
                    if (hipHeight != null && hipStartHeight != null) {
                        val currentDrop = hipStartHeight!! - hipHeight
                        if (currentDrop < minHipDrop) {
                            feedback.add("⚠ Go deeper!")
                        }
                    }
                }
                "ascending" -> feedback.add("Drive up!")
            }

            // Form warnings
            torsoAngle?.let { angle ->
                if (angle > maxTorsoLean) {
                    feedback.add("⚠ Keep chest up!")
                }
            }

            if (!kneeAlignment) {
                feedback.add("⚠ Knees over toes!")
            }
        }
    }
}