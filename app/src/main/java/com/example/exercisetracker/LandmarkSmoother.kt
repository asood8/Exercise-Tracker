package com.example.exercisetracker

import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.Optional

// Takes the frame-to-frame jitter out of pose landmarks with an exponential moving average
// before the trackers see them. Higher alpha follows movement more closely; lower is smoother.
class LandmarkSmoother(
    private val alpha: Float = 0.5f,
    private val resetAfterMs: Long = 500 // Start fresh after a gap, e.g. when the body left the frame
) {
    private var previous: List<NormalizedLandmark>? = null
    private var previousTimestampMs = 0L

    fun smooth(result: PoseLandmarkerResult): PoseLandmarkerResult {
        val current = result.landmarks().firstOrNull()
        if (current == null) {
            previous = null
            return result
        }

        val last = previous
        val smoothed = if (last == null || last.size != current.size ||
            result.timestampMs() - previousTimestampMs > resetAfterMs
        ) {
            current
        } else {
            current.mapIndexed { i, landmark ->
                val prev = last[i]
                NormalizedLandmark.create(
                    prev.x() + alpha * (landmark.x() - prev.x()),
                    prev.y() + alpha * (landmark.y() - prev.y()),
                    prev.z() + alpha * (landmark.z() - prev.z()),
                    landmark.visibility(),
                    landmark.presence()
                )
            }
        }

        previous = smoothed
        previousTimestampMs = result.timestampMs()
        return SmoothedResult(result, listOf(smoothed))
    }

    // PoseLandmarkerResult.create() isn't public, so wrap the original result and swap in the
    // smoothed landmarks
    private class SmoothedResult(
        private val source: PoseLandmarkerResult,
        private val smoothedLandmarks: List<List<NormalizedLandmark>>
    ) : PoseLandmarkerResult() {
        override fun timestampMs(): Long = source.timestampMs()
        override fun landmarks(): List<List<NormalizedLandmark>> = smoothedLandmarks
        override fun worldLandmarks(): List<List<Landmark>> = source.worldLandmarks()
        override fun segmentationMasks(): Optional<List<MPImage>> = source.segmentationMasks()
    }
}
