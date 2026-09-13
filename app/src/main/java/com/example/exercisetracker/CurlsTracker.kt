package com.example.exercisetracker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs

// Tracks each arm separately, so two-arm, alternating and one-arm curls all count.
// curlCount is reps per arm: the higher of the two arms' counts.
class CurlsTracker(private val isMirrored: () -> Boolean = { true }) {

    private class Arm(val shoulder: Int, val elbow: Int, val wrist: Int, val hip: Int) {
        var count = 0
        var stage: String? = null
        var minElbowAngle = Double.MAX_VALUE
        var maxElbowAngle = 0.0
        var inMotion = false
        var angle: Double? = null
        var swinging = false
        var elbowForward = true
        val repTimer = RepTimer()
    }

    // MediaPipe's left/right are anatomical, so on a mirrored (front camera) frame they're swapped
    private val landmarkLeft = Arm(shoulder = 11, elbow = 13, wrist = 15, hip = 23)
    private val landmarkRight = Arm(shoulder = 12, elbow = 14, wrist = 16, hip = 24)

    val curlCount: Int get() = maxOf(landmarkLeft.count, landmarkRight.count)
    val inCurlMotion: Boolean get() = landmarkLeft.inMotion || landmarkRight.inMotion
    val feedback = mutableListOf<String>()

    // Scoring
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()

    // Thresholds
    private val contractedThreshold = 50.0
    private val extendedThreshold = 160.0
    private val minContractionRequired = 70.0
    private val maxExtensionRequired = 140.0
    private val maxShoulderSwing = 0.15f

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return curlCount

        var anyArmVisible = false
        for (arm in listOf(landmarkLeft, landmarkRight)) {
            val angle = if (isArmVisible(landmarks, arm)) {
                AngleUtils.getAngle(result, arm.shoulder, arm.elbow, arm.wrist)
            } else null
            arm.angle = angle
            if (angle == null) continue

            anyArmVisible = true
            arm.swinging = calculateShoulderOffset(landmarks, arm) > maxShoulderSwing
            arm.elbowForward = isElbowForward(landmarks, arm)
            updateArm(arm, angle)
        }

        if (!anyArmVisible) {
            feedback.clear()
            feedback.add("Arm not visible - face camera")
            return curlCount
        }

        updateLiveFeedback()
        return curlCount
    }

    private fun updateArm(arm: Arm, angle: Double) {
        when (arm.stage) {
            null, "down" -> {
                if (angle > extendedThreshold) {
                    arm.stage = "down"
                    arm.minElbowAngle = Double.MAX_VALUE
                    arm.maxElbowAngle = angle
                    arm.inMotion = false
                } else if (angle < extendedThreshold) {
                    arm.stage = "curling"
                    arm.minElbowAngle = angle
                    arm.inMotion = true
                    arm.repTimer.start()
                }
            }
            "curling" -> {
                arm.minElbowAngle = minOf(arm.minElbowAngle, angle)
                if (angle < contractedThreshold) arm.stage = "up"
            }
            "up" -> {
                if (angle > contractedThreshold) {
                    arm.stage = "extending"
                    arm.maxElbowAngle = angle
                }
            }
            "extending" -> {
                arm.maxElbowAngle = maxOf(arm.maxElbowAngle, angle)
                if (angle > extendedThreshold) {
                    validateRep(arm)
                }
            }
        }
    }

    private fun visibility(landmark: NormalizedLandmark): Float =
        if (landmark.visibility().isPresent) landmark.visibility().get() else 0f

    private fun isArmVisible(landmarks: List<NormalizedLandmark>, arm: Arm): Boolean =
        listOf(arm.shoulder, arm.elbow, arm.wrist).all { visibility(landmarks[it]) > 0.5f }

    // Horizontal shoulder-to-hip offset; a large one means the body is swinging to lift the weight
    private fun calculateShoulderOffset(landmarks: List<NormalizedLandmark>, arm: Arm): Float {
        val s = landmarks[arm.shoulder]
        val h = landmarks[arm.hip]
        return if (visibility(s) > 0.5f && visibility(h) > 0.5f) abs(s.x() - h.x()) else 0f
    }

    private fun isElbowForward(landmarks: List<NormalizedLandmark>, arm: Arm): Boolean {
        val s = landmarks[arm.shoulder]
        val e = landmarks[arm.elbow]
        if (visibility(s) < 0.5f || visibility(e) < 0.5f) return true

        // Use Z-depth: the elbow should be level with or slightly in front of the shoulder.
        // Allow it to sit a little behind, but not significantly.
        return s.z() - e.z() > -0.1f
    }

    private fun validateRep(arm: Arm) {
        arm.stage = "down"
        arm.inMotion = false
        if (arm.repTimer.isTooFast()) return

        var score = 100
        val repFeedback = mutableListOf<String>()

        if (arm.minElbowAngle > minContractionRequired) {
            score -= 30
            repFeedback.add("Curl higher!")
        }
        if (arm.maxElbowAngle < maxExtensionRequired) {
            score -= 30
            repFeedback.add("Extend fully!")
        }
        if (arm.swinging) {
            score -= 20
            repFeedback.add("Don't swing!")
        }
        if (!arm.elbowForward) {
            score -= 10
            repFeedback.add("Keep elbow forward")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Curls", score, repFeedback))
        arm.count++
    }

    private fun updateLiveFeedback() {
        val (userLeft, userRight) = if (isMirrored()) landmarkRight to landmarkLeft else landmarkLeft to landmarkRight

        feedback.clear()
        feedback.add("Curls: $curlCount (Last: $lastRepScore%)")
        feedback.add("Left ${userLeft.count} · Right ${userRight.count}")
        val angles = listOfNotNull(
            userLeft.angle?.let { "L ${it.toInt()}°" },
            userRight.angle?.let { "R ${it.toInt()}°" }
        )
        feedback.add("Angle: " + angles.joinToString(" · "))

        val moving = listOf(landmarkLeft, landmarkRight).filter { it.inMotion }
        if (moving.any { !it.elbowForward }) feedback.add("⚠ Bring elbow forward!")
        if (moving.any { it.swinging }) feedback.add("⚠ Don't swing!")
    }
}
