package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.acos

class CurlsTracker {
    var curlCount = 0
    private var stage: String? = null
    private var minElbowAngle = Double.MAX_VALUE
    private var maxElbowAngle = 0.0
    var inCurlMotion = false
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
    private val maxUpperArmAngle = 100.0

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return curlCount

        val leftElbowAngle = AngleUtils.getAngle(result, 11, 13, 15)
        val rightElbowAngle = AngleUtils.getAngle(result, 12, 14, 16)

        val (side, elbowAngle) = when {
            leftElbowAngle != null && checkForm(landmarks, 11, 13, 15) -> "left" to leftElbowAngle
            rightElbowAngle != null && checkForm(landmarks, 12, 14, 16) -> "right" to rightElbowAngle
            else -> {
                feedback.clear()
                feedback.add("Arm not visible - face camera")
                return curlCount
            }
        }

        val shoulderIdx = if (side == "left") 11 else 12
        val elbowIdx = if (side == "left") 13 else 14
        val hipIdx = if (side == "left") 23 else 24

        val shoulderOffset = calculateShoulderAlignment(landmarks, shoulderIdx, hipIdx)
        val upperArmAngle = calculateUpperArmAngle(landmarks, shoulderIdx, elbowIdx, hipIdx)
        val armForward = isArmForward(landmarks, side)

        when (stage) {
            null, "down" -> {
                if (elbowAngle > extendedThreshold) {
                    stage = "down"
                    minElbowAngle = Double.MAX_VALUE
                    maxElbowAngle = elbowAngle
                    inCurlMotion = false
                } else if (elbowAngle < extendedThreshold) {
                    stage = "curling"
                    minElbowAngle = elbowAngle
                    inCurlMotion = true
                }
            }
            "curling" -> {
                minElbowAngle = minOf(minElbowAngle, elbowAngle)
                if (elbowAngle < contractedThreshold) stage = "up"
            }
            "up" -> {
                if (elbowAngle > contractedThreshold) {
                    stage = "extending"
                    maxElbowAngle = elbowAngle
                }
            }
            "extending" -> {
                maxElbowAngle = maxOf(maxElbowAngle, elbowAngle)
                if (elbowAngle > extendedThreshold) {
                    validateRep(shoulderOffset, upperArmAngle, armForward)
                }
            }
        }

        updateLiveFeedback(elbowAngle, shoulderOffset, upperArmAngle, armForward)
        return curlCount
    }

    private fun checkForm(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, sIdx: Int, eIdx: Int, wIdx: Int): Boolean {
        val s = landmarks[sIdx]
        val e = landmarks[eIdx]
        val w = landmarks[wIdx]
        
        val sVis = if (s.visibility().isPresent) s.visibility().get() else 0f
        val eVis = if (e.visibility().isPresent) e.visibility().get() else 0f
        val wVis = if (w.visibility().isPresent) w.visibility().get() else 0f
        
        return sVis > 0.5f && eVis > 0.5f && wVis > 0.5f
    }

    private fun calculateShoulderAlignment(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, sIdx: Int, hIdx: Int): Float {
        val s = landmarks[sIdx]
        val h = landmarks[hIdx]
        val sVis = if (s.visibility().isPresent) s.visibility().get() else 0f
        val hVis = if (h.visibility().isPresent) h.visibility().get() else 0f
        
        return if (sVis > 0.5f && hVis > 0.5f) abs(s.x() - h.x()) else 0f
    }

    private fun calculateUpperArmAngle(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, sIdx: Int, eIdx: Int, hIdx: Int): Double? {
        val s = landmarks[sIdx]
        val e = landmarks[eIdx]
        val h = landmarks[hIdx]
        
        val sVis = if (s.visibility().isPresent) s.visibility().get() else 0f
        val eVis = if (e.visibility().isPresent) e.visibility().get() else 0f
        val hVis = if (h.visibility().isPresent) h.visibility().get() else 0f
        
        if (sVis < 0.5f || eVis < 0.5f || hVis < 0.5f) return null

        val torsoX = (s.x() - h.x()).toDouble()
        val torsoY = (s.y() - h.y()).toDouble()
        val armX = (e.x() - s.x()).toDouble()
        val armY = (e.y() - s.y()).toDouble()

        val dotProduct = torsoX * armX + torsoY * armY
        val magTorso = sqrt(torsoX * torsoX + torsoY * torsoY)
        val magArm = sqrt(armX * armX + armY * armY)

        return if (magTorso > 0 && magArm > 0) {
            val cosAngle = (dotProduct / (magTorso * magArm)).coerceIn(-1.0, 1.0)
            Math.toDegrees(acos(cosAngle))
        } else null
    }

    private fun isArmForward(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, side: String): Boolean {
        val sIdx = if (side == "left") 11 else 12
        val eIdx = if (side == "left") 13 else 14
        val s = landmarks[sIdx]
        val e = landmarks[eIdx]
        
        val sVis = if (s.visibility().isPresent) s.visibility().get() else 0f
        val eVis = if (e.visibility().isPresent) e.visibility().get() else 0f
        
        if (sVis < 0.5f || eVis < 0.5f) return true

        // IMPROVED LOGIC: Use Z-depth instead of X-axis
        // Elbow should be slightly closer to camera (smaller Z) or equal to shoulder
        val depthDiff = s.z() - e.z()
        
        // Tolerance: allow elbow to be slightly behind shoulder, but not significantly
        return depthDiff > -0.1f 
    }

    private fun validateRep(shoulderOffset: Float, upperArmAngle: Double?, armForward: Boolean) {
        var score = 100
        val repFeedback = mutableListOf<String>()

        if (minElbowAngle > minContractionRequired) {
            score -= 30
            repFeedback.add("Curl higher!")
        }
        if (maxElbowAngle < maxExtensionRequired) {
            score -= 30
            repFeedback.add("Extend fully!")
        }
        if (shoulderOffset > maxShoulderSwing) {
            score -= 20
            repFeedback.add("Don't swing!")
        }
        if (!armForward) {
            score -= 10
            repFeedback.add("Keep elbow forward")
        }

        score = score.coerceIn(0, 100)
        lastRepScore = score
        sessionReps.add(RepResult("Curls", score, repFeedback))
        curlCount++
        stage = "down"
        inCurlMotion = false
    }

    private fun updateLiveFeedback(angle: Double, shoulderOffset: Float, upperArmAngle: Double?, armForward: Boolean) {
        feedback.clear()
        feedback.add("Curls: $curlCount (Last: $lastRepScore%)")
        feedback.add("Angle: ${angle.toInt()}°")
        if (inCurlMotion) {
            // Only show "Bring elbow forward" if significantly out of position
            if (!armForward) feedback.add("⚠ Bring elbow forward!")
            if (shoulderOffset > maxShoulderSwing) feedback.add("⚠ Don't swing!")
        }
    }
}
