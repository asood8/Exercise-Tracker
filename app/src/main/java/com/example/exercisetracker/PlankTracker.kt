package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs

class PlankTracker {
    private var startTime: Long = 0
    var totalSeconds: Int = 0
    var isPlanking = false
    
    val feedback = mutableListOf<String>()
    
    // Thresholds
    private val maxHipSag = 0.1f
    private val maxVerticalDiff = 0.2f // Shoulders vs Hips

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return totalSeconds

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // Check if horizontal
        val shoulderY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val hipY = (leftHip.y() + rightHip.y()) / 2f
        val ankleY = (leftAnkle.y() + rightAnkle.y()) / 2f
        
        val isHorizontal = abs(shoulderY - hipY) < maxVerticalDiff && abs(hipY - ankleY) < maxVerticalDiff

        if (isHorizontal) {
            if (!isPlanking) {
                startTime = System.currentTimeMillis()
                isPlanking = true
            } else {
                totalSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            }
            updateLiveFeedback(true)
        } else {
            isPlanking = false
            updateLiveFeedback(false)
        }

        return totalSeconds
    }

    private fun updateLiveFeedback(active: Boolean) {
        feedback.clear()
        feedback.add("Plank: $totalSeconds seconds")
        if (active) {
            feedback.add("🔥 Holding strong!")
        } else {
            feedback.add("Get in plank position")
        }
    }
}
