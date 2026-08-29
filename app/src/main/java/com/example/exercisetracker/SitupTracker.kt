package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

class SitupTracker {
    var situpCount = 0
    private var stage: String? = null
    private var minAbAngle = Double.MAX_VALUE
    private var maxAbAngle = 0.0
    var inSitupMotion = false

    val feedback = mutableListOf<String>()

    // Scoring
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()

    // Thresholds
    private val downThreshold = 60.0   // Lying flat
    private val upThreshold = 120.0     // Sitting up
    private val minContraction = 80.0   // Must sit up to at least this
    private val maxExtension = 100.0    // Must lie down to at least this
    private val maxNeckStrain = 30.0    // Degrees of acceptable head tilt
    private val maxHipLift = 0.08f      // Hips shouldn't lift off ground

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return situpCount

        // Check form first
        if (!checkForm(landmarks)) {
            feedback.clear()
            feedback.add("Lie on back - show full body")
            return situpCount
        }

        // Calculate ab angle (torso to legs)
        val abAngle = calculateAbAngle(landmarks)

        if (abAngle == null) {
            feedback.clear()
            feedback.add("Position not clear")
            return situpCount
        }

        // Check if lying down (horizontal position)
        val isHorizontal = checkHorizontalPosition(landmarks)
        if (!isHorizontal && stage == null) {
            feedback.clear()
            feedback.add("Lie down to start")
            return situpCount
        }

        // Form metrics
        val neckAngle = calculateNeckAngle(landmarks)
        val hipLift = calculateHipLift(landmarks)

        // State machine
        when (stage) {
            null, "down" -> {
                if (abAngle < downThreshold) {
                    stage = "down"
                    minAbAngle = abAngle
                    maxAbAngle = 0.0
                    inSitupMotion = false
                } else if (abAngle > downThreshold) {
                    stage = "rising"
                    maxAbAngle = abAngle
                    inSitupMotion = true
                }
            }

            "rising" -> {
                maxAbAngle = maxOf(maxAbAngle, abAngle)
                if (abAngle > upThreshold) {
                    stage = "up"
                }
            }

            "up" -> {
                if (abAngle < upThreshold) {
                    stage = "lowering"
                    minAbAngle = abAngle
                }
            }

            "lowering" -> {
                minAbAngle = minOf(minAbAngle, abAngle)
                if (abAngle < downThreshold) {
                    validateRep(neckAngle, hipLift)
                }
            }
        }

        updateLiveFeedback(abAngle, neckAngle, hipLift)
        return situpCount
    }

    private fun checkForm(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        val required = listOf(0, 11, 12, 23, 24, 25, 26)  // Nose, shoulders, hips, knees

        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        return required.all { getPresence(it) > 0.5f }
    }

    private fun checkHorizontalPosition(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        // Check if shoulders and hips are at similar height (lying down)
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2f

        return abs(shoulderY - hipY) < 0.2f
    }

    private fun calculateAbAngle(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Double? {
        // Calculate angle between torso and upper legs
        val shoulderMidX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val shoulderMidY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipMidX = (landmarks[23].x() + landmarks[24].x()) / 2f
        val hipMidY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val kneeMidX = (landmarks[25].x() + landmarks[26].x()) / 2f
        val kneeMidY = (landmarks[25].y() + landmarks[26].y()) / 2f

        // Vector from hip to shoulder (torso)
        val torsoX = shoulderMidX - hipMidX
        val torsoY = shoulderMidY - hipMidY

        // Vector from hip to knee (upper legs)
        val legX = kneeMidX - hipMidX
        val legY = kneeMidY - hipMidY

        // Calculate angle
        val dot = torsoX * legX + torsoY * legY
        val magTorso = sqrt((torsoX * torsoX + torsoY * torsoY).toDouble())
        val magLeg = sqrt((legX * legX + legY * legY).toDouble())

        return if (magTorso > 0 && magLeg > 0) {
            val cosAngle = (dot / (magTorso * magLeg)).coerceIn(-1.0, 1.0)
            Math.toDegrees(acos(cosAngle))
        } else null
    }

    private fun calculateNeckAngle(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Double? {
        // Calculate angle between head and torso (detect neck strain)
        val nose = landmarks[0]
        val shoulderMidX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val shoulderMidY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipMidX = (landmarks[23].x() + landmarks[24].x()) / 2f
        val hipMidY = (landmarks[23].y() + landmarks[24].y()) / 2f

        // Vector from shoulder to nose (head)
        val headX = nose.x() - shoulderMidX
        val headY = nose.y() - shoulderMidY

        // Vector from hip to shoulder (torso)
        val torsoX = shoulderMidX - hipMidX
        val torsoY = shoulderMidY - hipMidY

        val dot = headX * torsoX + headY * torsoY
        val magHead = sqrt((headX * headX + headY * headY).toDouble())
        val magTorso = sqrt((torsoX * torsoX + torsoY * torsoY).toDouble())

        return if (magHead > 0 && magTorso > 0) {
            val cosAngle = (dot / (magHead * magTorso)).coerceIn(-1.0, 1.0)
            Math.toDegrees(acos(cosAngle))
        } else null
    }

    private fun calculateHipLift(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        // Detect if hips are lifting off ground (cheating)
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val kneeY = (landmarks[25].y() + landmarks[26].y()) / 2f

        // Expected hip position (should be on ground, lower than knees)
        // If hips lift, hipY becomes smaller (higher on screen)
        val expectedDiff = 0.1f  // Expected minimum difference
        val actualDiff = hipY - kneeY

        return expectedDiff - actualDiff  // Positive = hips are lifting
    }

    private fun validateRep(neckAngle: Double?, hipLift: Float) {
        var score = 100
        val repFeedback = mutableListOf<String>()

        // 1. Full sit-up (40 pts)
        when {
            maxAbAngle < minContraction -> {
                score -= 40
                repFeedback.add("Sit up higher!")
            }
            maxAbAngle < upThreshold - 10 -> {
                score -= 15
                repFeedback.add("More height needed")
            }
        }

        // 2. Full extension (30 pts)
        when {
            minAbAngle > maxExtension -> {
                score -= 30
                repFeedback.add("Lie down fully!")
            }
            minAbAngle > downThreshold + 10 -> {
                score -= 10
            }
        }

        // 3. Neck form (15 pts)
        neckAngle?.let { angle ->
            if (angle > maxNeckStrain) {
                score -= 15
                repFeedback.add("Don't pull neck!")
            }
        }

        // 4. Hip stability (15 pts)
        if (hipLift > maxHipLift) {
            score -= 15
            repFeedback.add("Keep hips down!")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Sit-ups", score, repFeedback))
        situpCount++

        stage = "down"
        inSitupMotion = false
    }

    private fun updateLiveFeedback(angle: Double, neckAngle: Double?, hipLift: Float) {
        feedback.clear()
        feedback.add("Sit-Ups: $situpCount (Last: $lastRepScore%)")
        feedback.add("Angle: ${angle.toInt()}°")

        if (inSitupMotion) {
            // Real-time cues
            when (stage) {
                "rising" -> feedback.add("Curl up!")
                "lowering" -> feedback.add("Control down")
            }

            // Form warnings
            neckAngle?.let { neck ->
                if (neck > maxNeckStrain) {
                    feedback.add("⚠ Don't pull neck!")
                }
            }

            if (hipLift > maxHipLift) {
                feedback.add("⚠ Keep hips down!")
            }
        }
    }
}
