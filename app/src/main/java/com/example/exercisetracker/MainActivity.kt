package com.example.exercisetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // SETUP waits until the body is in view, COUNTDOWN is the 3-2-1, TRACKING counts reps, REST is
    // the break between goal sets. Trackers and calories only update while TRACKING.
    private enum class Phase { SETUP, COUNTDOWN, TRACKING, REST, PAUSED }

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var alignmentStatus: TextView
    private lateinit var liveStatsText: TextView
    private lateinit var soundButton: ImageButton
    private lateinit var pauseButton: ImageButton
    private lateinit var goalPanel: View
    private lateinit var goalRing: CircularProgressIndicator
    private lateinit var goalRingText: TextView
    private lateinit var goalSetText: TextView

    private var poseLandmarker: PoseLandmarker? = null
    private lateinit var cameraExecutor: ExecutorService

    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Curl left/right labels depend on whether the frame was mirrored for the front camera
    private val curlsTracker = CurlsTracker { isFrontCamera }
    private val pushUpTracker = PushUpTracker()
    private val squatTracker = SquatTracker()
    private val situpTracker = SitupTracker()
    private val overheadTracker = OverheadTracker()
    private val jumpingJackTracker = JumpingJackTracker()
    private val lungeTracker = LungeTracker()
    private val plankTracker = PlankTracker()
    private val landmarkSmoother = LandmarkSmoother()
    private lateinit var calorieEstimator: CalorieEstimator

    // Only this exercise's tracker runs, so one movement can't count as two exercises
    @Volatile private var activeExercise = Exercise.SQUATS

    // Phase changes happen on the UI thread; the result thread reads it
    @Volatile private var phase = Phase.SETUP
    private val handler = Handler(Looper.getMainLooper())
    private var countdownValue = 0
    private var restRemaining = 0
    private var setupVisibleSinceMs = 0L

    // Optional "3 × 15" goal from Home. Progress is measured from setBaseline, the exercise's count
    // (or plank seconds) when the current set started.
    private var goal: WorkoutGoal? = null
    @Volatile private var setsCompleted = 0
    @Volatile private var setBaseline = 0
    @Volatile private var goalComplete = false

    // Duration runs from the first "Go" and leaves out paused time
    private var workoutStartMs = 0L
    private var pausedAtMs = 0L
    private var pausedTotalMs = 0L

    // Last rep count read out, so each new rep is spoken once
    private var spokenExercise: Exercise? = null
    private var spokenValue = 0

    // Latest stats built on the result thread, shown while tracking
    private var lastStats: CharSequence = ""

    private var tts: TextToSpeech? = null
    private var lastSpokenTime = 0L
    private val TTS_COOLDOWN_MS = 3000L
    private var isSoundEnabled = true

    private val isUiUpdatePending = AtomicBoolean(false)
    private var lastUiUpdateTime = 0L
    private val UI_UPDATE_INTERVAL_MS = 33L
    private var isFrontCamera = true

    private var lastActiveTime = 0L
    private val HIGHLIGHT_DURATION_MS = 5000L

    // Trackers are updated on MediaPipe's result thread but read by endWorkout() on the UI thread
    private val trackerLock = Any()
    @Volatile private var workoutEnded = false

    companion object {
        private const val TAG = "LimbDetector"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val SETUP_HOLD_MS = 1000L // How long the body must stay in view before the countdown
        private const val REST_SECONDS = 60

        init {
            try {
                System.loadLibrary("mediapipe_tasks_vision_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        val weightKg = intent.getFloatExtra("WEIGHT_KG", 59f)
        val heightCm = intent.getFloatExtra("HEIGHT_CM", 168f)
        val age = intent.getIntExtra("AGE", 18)
        val gender = intent.getStringExtra("GENDER") ?: "male"
        activeExercise = Exercise.fromName(intent.getStringExtra("EXERCISE"))
        calorieEstimator = CalorieEstimator(weightKg, heightCm / 100f, age, gender)

        val goalSets = intent.getIntExtra("GOAL_SETS", 0)
        val goalTarget = intent.getIntExtra("GOAL_TARGET", 0)
        if (goalSets > 0 && goalTarget > 0) goal = WorkoutGoal(activeExercise, goalSets, goalTarget)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)
        alignmentStatus = findViewById(R.id.alignmentStatus)
        liveStatsText = findViewById(R.id.liveStatsText)
        soundButton = findViewById(R.id.soundButton)
        pauseButton = findViewById(R.id.pauseButton)
        goalPanel = findViewById(R.id.goalPanel)
        goalRing = findViewById(R.id.goalRing)
        goalRingText = findViewById(R.id.goalRingText)
        goalSetText = findViewById(R.id.goalSetText)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.endTaskButton).setOnClickListener { endWorkout() }
        findViewById<ImageButton>(R.id.filterButton).setOnClickListener { showExerciseDialog() }
        soundButton.setOnClickListener { toggleSound() }
        pauseButton.setOnClickListener { onPauseButton() }

        // During setup, tapping the instructions skips the wait, e.g. if detection is being fussy
        liveStatsText.setOnClickListener { if (phase == Phase.SETUP) startCountdown() }

        render()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startInitialization()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun toggleSound() {
        isSoundEnabled = !isSoundEnabled
        if (isSoundEnabled) {
            soundButton.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            soundButton.setColorFilter(Color.WHITE)
            Toast.makeText(this, "Coaching On", Toast.LENGTH_SHORT).show()
        } else {
            soundButton.setImageResource(android.R.drawable.ic_lock_silent_mode)
            soundButton.setColorFilter(Color.RED)
            tts?.stop()
            Toast.makeText(this, "Coaching Off", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    // Form tips, rate-limited so they don't talk over each other
    private fun speakFeedback(text: String) {
        if (!isSoundEnabled) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpokenTime > TTS_COOLDOWN_MS) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpokenTime = currentTime
        }
    }

    // Rep counts and workout cues. These skip the cooldown, and form tips wait until after them.
    private fun speak(text: String, interrupt: Boolean) {
        if (!isSoundEnabled) return
        tts?.speak(text, if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, null)
        lastSpokenTime = System.currentTimeMillis()
    }

    private fun showExerciseDialog() {
        val exercises = Exercise.entries
        AlertDialog.Builder(this)
            .setTitle("Switch exercise")
            .setSingleChoiceItems(exercises.map { it.displayName }.toTypedArray(), exercises.indexOf(activeExercise)) { dialog, index ->
                switchExercise(exercises[index])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Everything counted so far stays in the session; the new exercise starts with its own setup check
    private fun switchExercise(exercise: Exercise) {
        if (exercise == activeExercise) return
        handler.removeCallbacksAndMessages(null)
        if (phase == Phase.PAUSED) addPausedTime()
        synchronized(trackerLock) {
            if (activeExercise == Exercise.PLANK) plankTracker.finish()
            activeExercise = exercise
            lastActiveTime = 0L
            phase = Phase.SETUP
        }
        setupVisibleSinceMs = 0L
        render()
    }

    // --- Workout flow: setup -> countdown -> tracking, with pause and rest ---

    private fun enterSetup() {
        handler.removeCallbacksAndMessages(null)
        setupVisibleSinceMs = 0L
        phase = Phase.SETUP
        render()
    }

    private fun startCountdown() {
        handler.removeCallbacksAndMessages(null)
        phase = Phase.COUNTDOWN
        countdownValue = 3
        tickCountdown()
    }

    private fun tickCountdown() {
        if (phase != Phase.COUNTDOWN) return
        if (countdownValue == 0) {
            startTracking()
            return
        }
        speak(countdownValue.toString(), interrupt = true)
        render()
        countdownValue--
        handler.postDelayed({ tickCountdown() }, 1000)
    }

    private fun startTracking() {
        if (workoutStartMs == 0L) workoutStartMs = System.currentTimeMillis()
        synchronized(trackerLock) { phase = Phase.TRACKING }
        speak("Go!", interrupt = true)
        render()
    }

    private fun onPauseButton() {
        when (phase) {
            Phase.PAUSED -> resume()
            Phase.REST -> startCountdown() // Skip the rest of the break
            else -> pause()
        }
    }

    private fun pause() {
        handler.removeCallbacksAndMessages(null)
        synchronized(trackerLock) {
            // A plank hold shouldn't keep counting through the pause
            plankTracker.finish()
            phase = Phase.PAUSED
        }
        pausedAtMs = System.currentTimeMillis()
        tts?.stop()
        render()
    }

    private fun resume() {
        addPausedTime()
        // Not started yet: go back to waiting for the body. Otherwise a quick countdown to get back in position.
        if (workoutStartMs == 0L) enterSetup() else startCountdown()
    }

    private fun addPausedTime() {
        if (workoutStartMs != 0L && pausedAtMs != 0L) pausedTotalMs += System.currentTimeMillis() - pausedAtMs
        pausedAtMs = 0L
    }

    private fun startRest() {
        if (phase != Phase.TRACKING) return
        handler.removeCallbacksAndMessages(null)
        synchronized(trackerLock) {
            plankTracker.finish()
            phase = Phase.REST
        }
        restRemaining = REST_SECONDS
        speak("Set $setsCompleted done. Take a break.", interrupt = false)
        tickRest()
    }

    private fun tickRest() {
        if (phase != Phase.REST) return
        if (restRemaining <= 0) {
            startCountdown()
            return
        }
        if (restRemaining == 10) speak("10 seconds", interrupt = true)
        render()
        restRemaining--
        handler.postDelayed({ tickRest() }, 1000)
    }

    // Result thread: start the countdown once the body has been in view for a moment
    private fun checkSetup(pose: PoseLandmarkerResult, exercise: Exercise) {
        val landmarks = pose.landmarks().firstOrNull()
        val inView = landmarks != null && exercise.requiredLandmarks.all { idx ->
            val presence = landmarks[idx].presence()
            presence.isPresent && presence.get() > 0.5f
        }
        if (!inView) {
            setupVisibleSinceMs = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (setupVisibleSinceMs == 0L) {
            setupVisibleSinceMs = now
        } else if (now - setupVisibleSinceMs >= SETUP_HOLD_MS) {
            setupVisibleSinceMs = 0L
            runOnUiThread { if (phase == Phase.SETUP) startCountdown() }
        }
    }

    // Result thread: read out each new rep, or every 10 seconds of plank
    private fun announceProgress(exercise: Exercise) {
        val value = progressValue(exercise)
        if (exercise == spokenExercise && value > spokenValue) {
            if (!exercise.isTimed) {
                speak(value.toString(), interrupt = true)
            } else if (value / 10 > spokenValue / 10) {
                speak("${value / 10 * 10} seconds", interrupt = true)
            }
        }
        spokenExercise = exercise
        spokenValue = value
    }

    // Result thread: finish a set when its target is reached, then rest or wrap up the goal
    private fun checkGoal(exercise: Exercise) {
        val g = goal ?: return
        if (goalComplete || exercise != g.exercise) return
        if (progressValue(exercise) - setBaseline < g.target) return

        setsCompleted++
        setBaseline = progressValue(exercise)
        if (setsCompleted >= g.sets) {
            goalComplete = true
            speak("Goal complete!", interrupt = false)
        } else {
            runOnUiThread { startRest() }
        }
    }

    // --- Rendering (UI thread) ---

    private fun render() {
        liveStatsText.text = when (phase) {
            Phase.SETUP -> "Get ready: ${activeExercise.displayName}\n\n${activeExercise.setupTip}\n\n" +
                "Starts when you're in view, or tap here to start now."
            Phase.COUNTDOWN -> bigText(countdownValue.toString())
            Phase.REST -> "Rest ${formatDuration(restRemaining)}\n\nNext: set ${setsCompleted + 1} of ${goal?.sets}\n" +
                "Tap ⏭ to skip the rest"
            Phase.PAUSED -> "Paused\n\nTap ▶ to resume"
            Phase.TRACKING -> lastStats.ifBlank { activeExercise.setupTip }
        }

        val (icon, description) = when (phase) {
            Phase.PAUSED -> android.R.drawable.ic_media_play to "Resume workout"
            Phase.REST -> android.R.drawable.ic_media_next to "Skip rest"
            else -> android.R.drawable.ic_media_pause to "Pause workout"
        }
        pauseButton.setImageResource(icon)
        pauseButton.contentDescription = description

        renderGoal()
    }

    private fun renderGoal() {
        val g = goal
        if (g == null || activeExercise != g.exercise) {
            goalPanel.visibility = View.GONE
            return
        }
        goalPanel.visibility = View.VISIBLE
        val done = if (goalComplete) g.target else (progressValue(g.exercise) - setBaseline).coerceIn(0, g.target)
        goalRing.setProgressCompat(done * 100 / g.target, true)
        goalRingText.text = if (g.exercise.isTimed) "$done/${g.target}s" else "$done/${g.target}"
        goalSetText.text = if (goalComplete) "Goal complete ✓" else "Set ${setsCompleted + 1} of ${g.sets}"
    }

    private fun bigText(text: String): CharSequence = SpannableString(text).apply {
        setSpan(RelativeSizeSpan(3f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun startInitialization() {
        runOnUiThread { alignmentStatus.text = "Initializing AI..." }
        cameraExecutor.execute {
            setupPoseLandmarker()
            runOnUiThread { startCamera() }
        }
    }

    private fun endWorkout() {
        if (workoutEnded) return
        handler.removeCallbacksAndMessages(null)
        if (phase == Phase.PAUSED) addPausedTime()
        val durationSeconds = if (workoutStartMs == 0L) 0
            else ((System.currentTimeMillis() - workoutStartMs - pausedTotalMs) / 1000).toInt()

        // Stop processing frames, then read tracker state under the same lock the result thread uses
        workoutEnded = true
        imageAnalyzer?.clearAnalyzer()
        val intent = synchronized(trackerLock) { buildSummaryIntent(durationSeconds) }
        startActivity(intent)
        finish()
    }

    private fun buildSummaryIntent(durationSeconds: Int): Intent {
        val allReps = mutableListOf<RepResult>()
        allReps.addAll(curlsTracker.sessionReps)
        allReps.addAll(pushUpTracker.sessionReps)
        allReps.addAll(squatTracker.sessionReps)
        allReps.addAll(situpTracker.sessionReps)
        allReps.addAll(overheadTracker.sessionReps)
        allReps.addAll(jumpingJackTracker.sessionReps)
        allReps.addAll(lungeTracker.sessionReps)
        // Close out a plank that's still being held so it gets scored too
        plankTracker.finish()
        allReps.addAll(plankTracker.sessionReps)

        // Nothing scored this session (e.g. only a very short plank) is N/A rather than 0%
        val overallScore = if (allReps.isEmpty()) -1 else allReps.map { it.score }.average().toInt()

        val feedbackCountMap = mutableMapOf<String, Int>()
        allReps.forEach { rep ->
            rep.feedback.forEach { msg ->
                feedbackCountMap[msg] = (feedbackCountMap[msg] ?: 0) + 1
            }
        }

        val topFeedback = feedbackCountMap.toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        val feedbackSummary = if (topFeedback.isEmpty()) "Form: Excellent! Keep it up."
                              else "Top issues: " + topFeedback.joinToString(", ")

        val goalSummary = goal?.let { g ->
            "Goal ${g.describe()}: " + if (goalComplete) "complete" else "$setsCompleted of ${g.sets} sets"
        }

        return Intent(this, SummaryActivity::class.java).apply {
            putExtra("CURLS", curlsTracker.curlCount)
            putExtra("PUSHUPS", pushUpTracker.pushUpCount)
            putExtra("SQUATS", squatTracker.squatCount)
            putExtra("SITUPS", situpTracker.situpCount)
            putExtra("OVERHEAD", overheadTracker.overheadCount)
            putExtra("JACKS", jumpingJackTracker.jackCount)
            putExtra("LUNGES", lungeTracker.lungeCount)
            putExtra("PLANK", plankTracker.totalSeconds)
            putExtra("CALORIES", calorieEstimator.totalCalories)
            putExtra("OVERALL_SCORE", overallScore)
            putExtra("FEEDBACK_SUMMARY", feedbackSummary)
            putExtra("DURATION", durationSeconds)
            putExtra("GOAL_SUMMARY", goalSummary)
        }
    }

    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, image -> processLandmarkerResult(result, image) }
                .setErrorListener { error -> Log.e(TAG, "PoseLandmarker error: ${error.message}") }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up PoseLandmarker", e)
        }
    }

    private fun updateTracker(exercise: Exercise, pose: PoseLandmarkerResult) {
        when (exercise) {
            Exercise.CURLS -> curlsTracker.update(pose)
            Exercise.PUSHUPS -> pushUpTracker.update(pose)
            Exercise.SQUATS -> squatTracker.update(pose)
            Exercise.SITUPS -> situpTracker.update(pose)
            Exercise.OVERHEAD -> overheadTracker.update(pose)
            Exercise.JACKS -> jumpingJackTracker.update(pose)
            Exercise.LUNGES -> lungeTracker.update(pose)
            Exercise.PLANK -> plankTracker.update(pose)
        }
    }

    private fun feedbackFor(exercise: Exercise): List<String> = when (exercise) {
        Exercise.CURLS -> curlsTracker.feedback
        Exercise.PUSHUPS -> pushUpTracker.feedback
        Exercise.SQUATS -> squatTracker.feedback
        Exercise.SITUPS -> situpTracker.feedback
        Exercise.OVERHEAD -> overheadTracker.feedback
        Exercise.JACKS -> jumpingJackTracker.feedback
        Exercise.LUNGES -> lungeTracker.feedback
        Exercise.PLANK -> plankTracker.feedback
    }

    private fun isInMotion(exercise: Exercise): Boolean = when (exercise) {
        Exercise.CURLS -> curlsTracker.inCurlMotion
        Exercise.PUSHUPS -> pushUpTracker.inPushUpMotion
        Exercise.SQUATS -> squatTracker.inSquatMotion
        Exercise.SITUPS -> situpTracker.inSitupMotion
        Exercise.OVERHEAD -> overheadTracker.inPressMotion
        Exercise.JACKS -> jumpingJackTracker.inMotion
        Exercise.LUNGES -> lungeTracker.inMotion
        Exercise.PLANK -> plankTracker.isPlanking
    }

    // Reps so far this session, or seconds for plank
    private fun progressValue(exercise: Exercise): Int = when (exercise) {
        Exercise.CURLS -> curlsTracker.curlCount
        Exercise.PUSHUPS -> pushUpTracker.pushUpCount
        Exercise.SQUATS -> squatTracker.squatCount
        Exercise.SITUPS -> situpTracker.situpCount
        Exercise.OVERHEAD -> overheadTracker.overheadCount
        Exercise.JACKS -> jumpingJackTracker.jackCount
        Exercise.LUNGES -> lungeTracker.lungeCount
        Exercise.PLANK -> plankTracker.totalSeconds
    }

    // Short progress label ("12" or "0:45") for an exercise this session, or null if it wasn't done
    private fun progressLabel(exercise: Exercise): String? {
        val value = progressValue(exercise)
        if (value <= 0) return null
        return if (exercise.isTimed) formatPlankTime(value) else value.toString()
    }

    private fun processLandmarkerResult(result: PoseLandmarkerResult, image: MPImage) {
        if (workoutEnded) return

        // Trackers and the skeleton overlay use smoothed landmarks. The calorie model keeps the raw
        // ones, since its own noise filtering was tuned on them.
        val pose = landmarkSmoother.smooth(result)

        // Reps and calories only count while tracking; setup, countdown, rest and pause are ignored
        val (exercise, tracking) = synchronized(trackerLock) {
            val current = activeExercise
            val isTracking = phase == Phase.TRACKING
            if (isTracking) {
                updateTracker(current, pose)
                calorieEstimator.update(result, System.currentTimeMillis())
            }
            current to isTracking
        }

        if (phase == Phase.SETUP) checkSetup(pose, exercise)
        if (tracking) {
            announceProgress(exercise)
            checkGoal(exercise)
        }

        val currentTime = System.currentTimeMillis()
        if (tracking && isInMotion(exercise)) lastActiveTime = currentTime

        if (currentTime - lastUiUpdateTime >= UI_UPDATE_INTERVAL_MS && isUiUpdatePending.compareAndSet(false, true)) {
            lastUiUpdateTime = currentTime
            val statsBuilder = SpannableStringBuilder()

            val feedback = feedbackFor(exercise)
            val filteredFeedback = feedback.filter { !it.contains("visible", ignoreCase = true) && !it.contains("body", ignoreCase = true) && it.isNotBlank() }
            // With nothing else to show, keep the tracker's first line, e.g. "show full body"
            val shownFeedback = filteredFeedback.ifEmpty { feedback.take(1) }

            if (shownFeedback.isNotEmpty()) {
                statsBuilder.append(shownFeedback.joinToString("\n")).append("\n\n")

                // Highlight the exercise while it's being done, and speak its latest cue
                if (tracking && currentTime - lastActiveTime < HIGHLIGHT_DURATION_MS) {
                    statsBuilder.setSpan(StyleSpan(Typeface.BOLD), 0, statsBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    statsBuilder.setSpan(RelativeSizeSpan(1.3f), 0, statsBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val lastLine = filteredFeedback.lastOrNull() ?: ""
                    if (lastLine.contains("!")) speakFeedback(lastLine)
                }
            }

            // Exercises done earlier in the session, before switching
            val earlier = Exercise.entries.filter { it != exercise }
                .mapNotNull { other -> progressLabel(other)?.let { "${other.displayName} $it" } }
            if (earlier.isNotEmpty()) {
                statsBuilder.append("This session: ").append(earlier.joinToString(" · ")).append("\n\n")
            }

            statsBuilder.append(calorieEstimator.feedback.joinToString("\n")).append("\n\n")

            val isBodyFound = result.landmarks().isNotEmpty()
            val stats: CharSequence = if (statsBuilder.isBlank()) "" else statsBuilder
            runOnUiThread {
                try {
                    lastStats = stats
                    if (isBodyFound) {
                        alignmentStatus.text = "Properly Aligned"
                        alignmentStatus.setTextColor(Color.GREEN)
                        alignmentStatus.setBackgroundColor(Color.parseColor("#3300FF00"))
                        overlayView.setResults(pose, image.height, image.width, emptyList())
                    } else {
                        alignmentStatus.text = "Searching for body..."
                        alignmentStatus.setTextColor(Color.RED)
                        alignmentStatus.setBackgroundColor(Color.parseColor("#33FF0000"))
                        overlayView.clear()
                    }
                    render()
                } finally {
                    isUiUpdatePending.set(false)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(320, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { imageProxy -> detectPose(imageProxy) } }

            val cameraSelector = if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true) {
                isFrontCamera = true
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                isFrontCamera = false
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if (poseLandmarker == null) {
            imageProxy.close()
            return
        }
        val bitmap = imageProxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            postScale(0.5f, 0.5f)
            if (isFrontCamera) postScale(-1f, 1f)
        }
        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        bitmap.recycle()
        val mpImage = BitmapImageBuilder(finalBitmap).build()
        poseLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
        imageProxy.close()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) startInitialization() else finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }
}
