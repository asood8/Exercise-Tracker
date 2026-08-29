package com.example.exercisetracker

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2

object AngleUtils {

    fun calculateAngle(
        a: Point,
        b: Point,
        c: Point
    ): Double {
        val radians = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) - 
                      atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
        var angle = abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) {
            angle = 360.0 - angle
        }
        return angle
    }

    data class Point(val x: Float, val y: Float)
    data class Point3D(val x: Float, val y: Float, val z: Float)

    fun getAngle(result: PoseLandmarkerResult, firstIdx: Int, midIdx: Int, lastIdx: Int): Double? {
        val landmarks = result.landmarks().firstOrNull() ?: return null
        if (landmarks.size <= lastIdx) return null

        val a = landmarks[firstIdx]
        val b = landmarks[midIdx]
        val c = landmarks[lastIdx]

        return calculateAngle(
            Point(a.x(), a.y()),
            Point(b.x(), b.y()),
            Point(c.x(), c.y())
        )
    }
}
