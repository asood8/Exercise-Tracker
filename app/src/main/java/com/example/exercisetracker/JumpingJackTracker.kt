package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs

class JumpingJackTracker {
    var jackCount = 0
    private var stage: String? = null // "down", "up"
    var inMotion = false
    
    val feedback = mutableListOf<String>()
    
    // Scoring
    var lastRepScore = 0
    val sessionReps = mutableListOf<RepResult>()

    // Thresholds
    // "Down" position: Hands below hips, feet together
    // "Up" position: Hands above head, feet wide
    
    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return jackCount

        // Need wrists, shoulders, hips, and ankles
        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // Check visibility
        val required = listOf(15, 16, 11, 12, 23, 24, 27, 28)
        if (required.any { !landmarks[it].presence().isPresent || landmarks[it].presence().get() < 0.5f }) {
            feedback.clear()
            feedback.add("Full body not visible")
            return jackCount
        }

        // Logic for "Up"
        // Hands above shoulders
        val handsUp = leftWrist.y() < leftShoulder.y() && rightWrist.y() < rightShoulder.y()
        // Feet wider than hips
        val hipWidth = abs(leftHip.x() - rightHip.x())
        val ankleWidth = abs(leftAnkle.x() - rightAnkle.x())
        val feetWide = ankleWidth > hipWidth * 1.5f

        // Logic for "Down"
        // Hands below hips
        val handsDown = leftWrist.y() > leftHip.y() && rightWrist.y() > rightHip.y()
        // Feet closer together
        val feetTogether = ankleWidth < hipWidth * 1.2f

        when (stage) {
            null, "down" -> {
                if (handsUp && feetWide) {
                    stage = "up"
                    inMotion = true
                }
            }
            "up" -> {
                if (handsDown && feetTogether) {
                    validateRep()
                    stage = "down"
                    inMotion = false
                }
            }
        }

        updateLiveFeedback()
        return jackCount
    }

    private fun validateRep() {
        // Jumping jacks are binary for now, but we can score based on speed or arm height
        val score = 100 
        lastRepScore = score
        sessionReps.add(RepResult("Jumping Jacks", score))
        jackCount++
    }

    private fun updateLiveFeedback() {
        feedback.clear()
        feedback.add("Jumping Jacks: $jackCount (Last: $lastRepScore%)")
        if (inMotion) {
            feedback.add("Bring hands down!")
        } else {
            feedback.add("Jump up!")
        }
    }
}
