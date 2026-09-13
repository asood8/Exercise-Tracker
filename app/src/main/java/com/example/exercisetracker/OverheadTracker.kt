package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.acos

class OverheadTracker {
    var overheadCount = 0
    private var stage: String? = null
    private var minElbowAngle = Double.MAX_VALUE
    private var maxElbowAngle = 0.0
    var inPressMotion = false

    val feedback = mutableListOf<String>()

    // Scoring
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()
    private val repTimer = RepTimer()

    // Thresholds
    private val downThreshold = 60.0      // Hands at shoulders
    private val upThreshold = 160.0       // Arms fully extended
    private val minContractionRequired = 90.0
    private val maxExtensionRequired = 140.0
    private val maxArmAsymmetry = 20.0    // Max angle difference between arms
    private val maxElbowWidth = 0.4f      // Elbows shouldn't be too wide
    private val maxBackArch = 0.15f       // Max torso deviation from vertical

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return overheadCount

        // Check visibility first
        if (!checkForm(landmarks)) {
            feedback.clear()
            feedback.add("Face camera - show full body")
            return overheadCount
        }

        val leftElbowAngle = AngleUtils.getAngle(result, 11, 13, 15)
        val rightElbowAngle = AngleUtils.getAngle(result, 12, 14, 16)

        if (leftElbowAngle == null || rightElbowAngle == null) {
            feedback.clear()
            feedback.add("Arms not visible")
            return overheadCount
        }

        val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0

        // Form checks
        val armsUp = checkArmsAboveShoulders(landmarks)
        val armSymmetry = checkArmSymmetry(leftElbowAngle, rightElbowAngle)
        val elbowWidth = checkElbowWidth(landmarks)
        val backArch = checkBackArch(landmarks)
        val wristAlignment = checkWristAlignment(landmarks)

        // Must have arms up to start tracking
        if (!armsUp && stage != "lowering") {
            feedback.clear()
            feedback.add("Raise hands overhead to start")
            return overheadCount
        }

        // State Machine
        when (stage) {
            null, "down" -> {
                if (avgElbowAngle < downThreshold && armsUp) {
                    stage = "down"
                    minElbowAngle = avgElbowAngle
                    maxElbowAngle = 0.0
                    inPressMotion = false
                } else if (avgElbowAngle > downThreshold && armsUp) {
                    stage = "pressing"
                    maxElbowAngle = avgElbowAngle
                    inPressMotion = true
                    repTimer.start()
                }
            }

            "pressing" -> {
                maxElbowAngle = maxOf(maxElbowAngle, avgElbowAngle)
                if (avgElbowAngle > upThreshold) {
                    stage = "up"
                }
            }

            "up" -> {
                if (avgElbowAngle < upThreshold) {
                    stage = "lowering"
                    minElbowAngle = avgElbowAngle
                }
            }

            "lowering" -> {
                minElbowAngle = minOf(minElbowAngle, avgElbowAngle)
                if (avgElbowAngle < downThreshold) {
                    validateRep(armSymmetry, elbowWidth, backArch, wristAlignment)
                }
            }
        }

        updateLiveFeedback(avgElbowAngle, armSymmetry, elbowWidth, backArch, wristAlignment)
        return overheadCount
    }

    private fun checkForm(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        // Check if key landmarks are visible
        val keyIndices = listOf(11, 12, 13, 14, 15, 16, 23, 24)  // Shoulders, elbows, wrists, hips

        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        return keyIndices.all { getPresence(it) > 0.5f }
    }

    private fun checkArmsAboveShoulders(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        // Y decreases upwards in normalized coordinates
        val leftWristY = landmarks[15].y()
        val leftShoulderY = landmarks[11].y()
        val rightWristY = landmarks[16].y()
        val rightShoulderY = landmarks[12].y()

        return leftWristY < leftShoulderY && rightWristY < rightShoulderY
    }

    private fun checkArmSymmetry(leftAngle: Double, rightAngle: Double): Double {
        // Return the angle difference between left and right arms
        return abs(leftAngle - rightAngle)
    }

    private fun checkElbowWidth(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        // Check if elbows are too wide (should be in front, not flared out)
        val leftElbow = landmarks[13]
        val rightElbow = landmarks[14]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]

        // Calculate shoulder width
        val shoulderWidth = abs(leftShoulder.x() - rightShoulder.x())

        // Calculate elbow width
        val elbowWidth = abs(leftElbow.x() - rightElbow.x())

        // Ratio: elbows should be narrower than or equal to shoulders
        return if (shoulderWidth > 0) elbowWidth / shoulderWidth else 0f
    }

    private fun checkBackArch(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        // Check if torso is arching back excessively
        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]

        // Average shoulder and hip positions
        val shoulderX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val shoulderY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val hipX = (leftHip.x() + rightHip.x()) / 2f
        val hipY = (leftHip.y() + rightHip.y()) / 2f

        // Calculate torso angle from vertical
        // Ideally, shoulder should be directly above hip (minimal horizontal offset)
        val horizontalDeviation = abs(shoulderX - hipX)

        return horizontalDeviation
    }

    private fun checkWristAlignment(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        // Wrists should be roughly above elbows when pressing
        // Only check during pressing phase
        if (stage != "pressing" && stage != "up") return true

        val leftWrist = landmarks[15]
        val leftElbow = landmarks[13]
        val rightWrist = landmarks[16]
        val rightElbow = landmarks[14]

        // Wrists should have higher Y (lower on screen) than elbows when extended
        val leftAligned = abs(leftWrist.x() - leftElbow.x()) < 0.1f
        val rightAligned = abs(rightWrist.x() - rightElbow.x()) < 0.1f

        return leftAligned && rightAligned
    }

    private fun validateRep(
        armAsymmetry: Double,
        elbowWidth: Float,
        backArch: Float,
        wristAlignment: Boolean
    ) {
        if (repTimer.isTooFast()) {
            stage = "down"
            inPressMotion = false
            return
        }

        var score = 100
        val repFeedback = mutableListOf<String>()

        // 1. Extension (30 pts)
        when {
            maxElbowAngle < maxExtensionRequired -> {
                score -= 30
                repFeedback.add("Extend arms fully!")
            }
            maxElbowAngle < upThreshold - 10 -> {
                score -= 10
                repFeedback.add("More extension needed")
            }
        }

        // 2. Depth/Lowering (30 pts)
        when {
            minElbowAngle > minContractionRequired -> {
                score -= 30
                repFeedback.add("Lower to shoulders!")
            }
            minElbowAngle > downThreshold + 10 -> {
                score -= 10
                repFeedback.add("Lower more")
            }
        }

        // 3. Arm Symmetry (20 pts)
        if (armAsymmetry > maxArmAsymmetry) {
            score -= 20
            repFeedback.add("Press evenly!")
        } else if (armAsymmetry > maxArmAsymmetry / 2) {
            score -= 10
            repFeedback.add("More symmetry needed")
        }

        // 4. Elbow Position (10 pts)
        if (elbowWidth > maxElbowWidth) {
            score -= 10
            repFeedback.add("Elbows too wide!")
        }

        // 5. Torso Stability (10 pts)
        if (backArch > maxBackArch) {
            score -= 10
            repFeedback.add("Don't arch back!")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Overhead Press", score, repFeedback))
        overheadCount++

        stage = "down"
        inPressMotion = false
    }

    private fun updateLiveFeedback(
        angle: Double,
        armAsymmetry: Double,
        elbowWidth: Float,
        backArch: Float,
        wristAlignment: Boolean
    ) {
        feedback.clear()
        feedback.add("Overhead: $overheadCount (Last: $lastRepScore%)")
        feedback.add("Angle: ${angle.toInt()}°")

        if (inPressMotion) {
            // Real-time form cues during motion
            when (stage) {
                "pressing" -> feedback.add("Press straight up!")
                "lowering" -> feedback.add("Control descent")
            }

            // Form warnings
            if (armAsymmetry > maxArmAsymmetry) {
                feedback.add("⚠ Uneven arms!")
            }

            if (elbowWidth > maxElbowWidth) {
                feedback.add("⚠ Bring elbows in!")
            }

            if (backArch > maxBackArch) {
                feedback.add("⚠ Keep torso upright!")
            }

            if (!wristAlignment && (stage == "pressing" || stage == "up")) {
                feedback.add("⚠ Align wrists over elbows!")
            }
        }
    }
}