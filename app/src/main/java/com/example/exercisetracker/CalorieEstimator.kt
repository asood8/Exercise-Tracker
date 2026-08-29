package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt

class CalorieEstimator(
    private val weightKg: Float,
    private val heightM: Float,
    private val age: Int,
    private val gender: String
) {
    private var prevCom: AngleUtils.Point3D? = null
    private var prevTimestamp: Long? = null

    var totalCalories = 0.0
    private var activityCalories = 0.0
    private var verticalWork = 0.0
    private var totalDistance = 0.0
    private var frameCount = 0

    // BMR calculation (Harris-Benedict equation)
    private val bmr: Double = if (gender.lowercase() == "male") {
        88.362 + (13.397 * weightKg) + (4.799 * heightM * 100) - (5.677 * age)
    } else {
        447.593 + (9.247 * weightKg) + (3.098 * heightM * 100) - (4.330 * age)
    }

    private val bmrPerSecond = bmr / (24 * 3600)

    private val metHistory = mutableListOf<Double>()
    private val velocityHistory = mutableListOf<Double>()
    private val movementThreshold = 0.005f
    private val maxVelocity = 3.0  // m/s cap
    private val maxVerticalVelocity = 2.0  // m/s cap

    val feedback = mutableListOf<String>()

    // More detailed body segment weights (from biomechanics research)
    private val segmentWeights = mapOf(
        "head" to 0.081f,
        "trunk" to 0.497f,
        "upper_arms" to 0.028f * 2f,
        "forearms" to 0.016f * 2f,
        "hands" to 0.006f * 2f,
        "thighs" to 0.100f * 2f,
        "shanks" to 0.0465f * 2f,
        "feet" to 0.0145f * 2f
    )

    fun update(result: PoseLandmarkerResult, timestampMs: Long) {
        frameCount++

        val landmarks = result.landmarks().firstOrNull() ?: return
        val currentCom = estimateCom3D(landmarks) ?: return

        val lastCom = prevCom
        val lastTimestamp = prevTimestamp

        if (lastCom != null && lastTimestamp != null) {
            val dt = (timestampMs - lastTimestamp) / 1000.0

            // Sanity check on time delta
            if (dt <= 0 || dt > 1.0) {
                prevCom = currentCom
                prevTimestamp = timestampMs
                return
            }

            // Calculate 3D distance
            val dx = (currentCom.x - lastCom.x).toDouble()
            val dy = (currentCom.y - lastCom.y).toDouble()
            val dz = (currentCom.z - lastCom.z).toDouble()
            val distance = sqrt(dx * dx + dy * dy + dz * dz).toFloat()

            // Apply movement threshold to filter noise
            if (distance > movementThreshold) {
                val distanceM = distance * heightM
                val velocity = distanceM / dt

                // Cap unrealistic velocities
                if (velocity < maxVelocity) {
                    velocityHistory.add(velocity)
                    if (velocityHistory.size > 30) velocityHistory.removeAt(0)

                    totalDistance += velocity * dt

                    // Calculate vertical work (moving against gravity)
                    val verticalDisplacement = (lastCom.y - currentCom.y) * heightM
                    if (verticalDisplacement > 0) {  // Moving up
                        val work = weightKg * 9.81 * abs(verticalDisplacement)
                        verticalWork += work
                    }

                    // Calculate vertical velocity
                    val verticalDistanceM = abs(verticalDisplacement)
                    val verticalVelocity = verticalDistanceM / dt

                    // Cap vertical velocity
                    if (verticalVelocity < maxVerticalVelocity) {
                        // Estimate MET value based on movement
                        val met = estimateMetValue(velocity, verticalVelocity)
                        metHistory.add(met)
                        if (metHistory.size > 150) metHistory.removeAt(0)

                        // Calculate calories from MET
                        // Formula: Calories/minute = MET × weight(kg) × 3.5 / 200
                        val avgMet = if (metHistory.isNotEmpty()) metHistory.average() else 1.0
                        val caloriesPerMinute = avgMet * weightKg * 3.5 / 200.0
                        val dtMinutes = dt / 60.0
                        val frameCalories = caloriesPerMinute * dtMinutes

                        activityCalories += frameCalories
                        totalCalories += frameCalories
                    }
                }
            }
        }

        prevCom = currentCom
        prevTimestamp = timestampMs

        updateFeedback()
    }

    private fun estimateMetValue(velocity: Double, verticalVelocity: Double): Double {
        // More conservative MET values for camera-based exercise
        var met = when {
            velocity < 0.01 -> 1.2   // Minimal activity
            velocity < 0.05 -> 2.5   // Light activity
            velocity < 0.15 -> 4.0   // Moderate
            velocity < 0.30 -> 6.0   // Vigorous
            else -> 8.0              // Very vigorous
        }

        // Smaller boost for vertical movement
        if (verticalVelocity > 0.08) {
            met += 1.5
        } else if (verticalVelocity > 0.05) {
            met += 0.8
        }

        return met
    }

    private fun estimateCom3D(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): AngleUtils.Point3D? {
        var weightedX = 0f
        var weightedY = 0f
        var weightedZ = 0f
        var totalWeight = 0f

        // Helper function to get presence safely
        fun getPresence(idx: Int): Float {
            val p = landmarks[idx].presence()
            return if (p.isPresent) p.get() else 0f
        }

        // Helper function to check visibility
        fun isVisible(idx: Int): Boolean = getPresence(idx) > 0.5f

        // Helper function to get midpoint of two landmarks
        fun midpoint(idx1: Int, idx2: Int): Triple<Float, Float, Float>? {
            if (!isVisible(idx1) || !isVisible(idx2)) return null
            val l1 = landmarks[idx1]
            val l2 = landmarks[idx2]
            return Triple(
                (l1.x() + l2.x()) / 2f,
                (l1.y() + l2.y()) / 2f,
                (l1.z() + l2.z()) / 2f
            )
        }

        // 1. Head (nose)
        if (isVisible(0)) {
            val w = segmentWeights["head"]!!
            val nose = landmarks[0]
            weightedX += nose.x() * w
            weightedY += nose.y() * w
            weightedZ += nose.z() * w
            totalWeight += w
        }

        // 2. Trunk (average of shoulders and hips)
        val trunkIndices = listOf(11, 12, 23, 24)
        if (trunkIndices.all { isVisible(it) }) {
            val w = segmentWeights["trunk"]!!
            var tx = 0f
            var ty = 0f
            var tz = 0f
            for (idx in trunkIndices) {
                tx += landmarks[idx].x()
                ty += landmarks[idx].y()
                tz += landmarks[idx].z()
            }
            weightedX += (tx / 4f) * w
            weightedY += (ty / 4f) * w
            weightedZ += (tz / 4f) * w
            totalWeight += w
        }

        // 3. Upper arms (shoulder to elbow midpoints)
        val upperArms = listOf(
            Pair(11, 13),  // Left: shoulder to elbow
            Pair(12, 14)   // Right: shoulder to elbow
        )
        for ((s, e) in upperArms) {
            midpoint(s, e)?.let { (mx, my, mz) ->
                val w = segmentWeights["upper_arms"]!! / 2f
                weightedX += mx * w
                weightedY += my * w
                weightedZ += mz * w
                totalWeight += w
            }
        }

        // 4. Forearms (elbow to wrist midpoints)
        val forearms = listOf(
            Pair(13, 15),  // Left: elbow to wrist
            Pair(14, 16)   // Right: elbow to wrist
        )
        for ((e, w_idx) in forearms) {
            midpoint(e, w_idx)?.let { (mx, my, mz) ->
                val w = segmentWeights["forearms"]!! / 2f
                weightedX += mx * w
                weightedY += my * w
                weightedZ += mz * w
                totalWeight += w
            }
        }

        // 5. Hands (wrists)
        for (idx in listOf(15, 16)) {
            if (isVisible(idx)) {
                val w = segmentWeights["hands"]!! / 2f
                val hand = landmarks[idx]
                weightedX += hand.x() * w
                weightedY += hand.y() * w
                weightedZ += hand.z() * w
                totalWeight += w
            }
        }

        // 6. Thighs (hip to knee midpoints)
        val thighs = listOf(
            Pair(23, 25),  // Left: hip to knee
            Pair(24, 26)   // Right: hip to knee
        )
        for ((h, k) in thighs) {
            midpoint(h, k)?.let { (mx, my, mz) ->
                val w = segmentWeights["thighs"]!! / 2f
                weightedX += mx * w
                weightedY += my * w
                weightedZ += mz * w
                totalWeight += w
            }
        }

        // 7. Shanks (knee to ankle midpoints)
        val shanks = listOf(
            Pair(25, 27),  // Left: knee to ankle
            Pair(26, 28)   // Right: knee to ankle
        )
        for ((k, a) in shanks) {
            midpoint(k, a)?.let { (mx, my, mz) ->
                val w = segmentWeights["shanks"]!! / 2f
                weightedX += mx * w
                weightedY += my * w
                weightedZ += mz * w
                totalWeight += w
            }
        }

        // 8. Feet (ankles)
        for (idx in listOf(27, 28)) {
            if (isVisible(idx)) {
                val w = segmentWeights["feet"]!! / 2f
                val foot = landmarks[idx]
                weightedX += foot.x() * w
                weightedY += foot.y() * w
                weightedZ += foot.z() * w
                totalWeight += w
            }
        }

        return if (totalWeight > 0f) {
            AngleUtils.Point3D(
                weightedX / totalWeight,
                weightedY / totalWeight,
                weightedZ / totalWeight
            )
        } else null
    }

    fun getCalories(): Double {
        val metCalories = activityCalories
        val workCalories = (verticalWork / 4184) / 0.22
        return metCalories * 0.85 + workCalories * 0.15
    }

    fun getStats(): Map<String, Any> {
        val currentMet = if (metHistory.isNotEmpty()) metHistory.average() else 1.0
        val avgVelocity = if (velocityHistory.isNotEmpty()) velocityHistory.average() else 0.0

        val intensity = when {
            currentMet < 3 -> "Low"
            currentMet < 6 -> "Moderate"
            currentMet < 9 -> "High"
            else -> "Very High"
        }

        return mapOf(
            "activity_calories" to getCalories(),
            "total_calories" to totalCalories,
            "current_met" to currentMet,
            "avg_velocity" to avgVelocity,
            "total_distance" to totalDistance,
            "vertical_work_kcal" to (verticalWork / 4184),
            "intensity" to intensity
        )
    }

    private fun updateFeedback() {
        val stats = getStats()
        val currentMet = stats["current_met"] as Double
        val intensity = stats["intensity"] as String

        feedback.clear()
        feedback.add("Calories: %.1f kcal".format(totalCalories))
        feedback.add("Intensity: $intensity (MET: %.1f)".format(currentMet))

        if (currentMet > 3.0) {
            val avgVel = stats["avg_velocity"] as Double
            feedback.add("Velocity: %.2f m/s".format(avgVel))
        }
    }
}
