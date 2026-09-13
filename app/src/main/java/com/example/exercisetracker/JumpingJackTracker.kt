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
    private val repTimer = RepTimer(minRepMs = 300)

    // Thresholds
    // "Down" position: Hands below hips, feet together (within 1.2x hip width)
    // "Up" position: Hands above shoulders, feet wide (over 1.5x hip width)
    private val fullSpreadRatio = 2.0f // Feet this far apart (vs hip width) counts as a full jump

    // Best positions reached during the current rep
    private var handsOverhead = false
    private var maxSpreadRatio = 0f

    fun update(result: PoseLandmarkerResult): Int {
        val landmarks = result.landmarks().firstOrNull() ?: return jackCount

        // Need nose, wrists, shoulders, hips, and ankles
        val nose = landmarks[0]
        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // Check visibility
        val required = listOf(0, 15, 16, 11, 12, 23, 24, 27, 28)
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

        // Form measures for scoring
        val wristsAboveHead = leftWrist.y() < nose.y() && rightWrist.y() < nose.y()
        val spreadRatio = if (hipWidth > 0f) ankleWidth / hipWidth else 0f

        when (stage) {
            null, "down" -> {
                if (handsUp && feetWide) {
                    stage = "up"
                    inMotion = true
                    repTimer.start()
                    handsOverhead = wristsAboveHead
                    maxSpreadRatio = spreadRatio
                }
            }
            "up" -> {
                if (wristsAboveHead) handsOverhead = true
                maxSpreadRatio = maxOf(maxSpreadRatio, spreadRatio)
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
        if (repTimer.isTooFast()) return

        var score = 100
        val repFeedback = mutableListOf<String>()

        // 1. Arms all the way overhead (30 pts)
        if (!handsOverhead) {
            score -= 30
            repFeedback.add("Raise arms overhead!")
        }

        // 2. Full leg spread (20 pts)
        if (maxSpreadRatio < fullSpreadRatio) {
            score -= 20
            repFeedback.add("Jump wider!")
        }

        lastRepScore = score
        sessionReps.add(RepResult("Jumping Jacks", score, repFeedback))
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
