package com.example.exercisetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
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

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var alignmentStatus: TextView
    private lateinit var liveStatsText: TextView
    private lateinit var soundButton: ImageButton

    private var poseLandmarker: PoseLandmarker? = null
    private lateinit var cameraExecutor: ExecutorService

    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    
    private val curlsTracker = CurlsTracker()
    private val pushUpTracker = PushUpTracker()
    private val squatTracker = SquatTracker()
    private val situpTracker = SitupTracker()
    private val overheadTracker = OverheadTracker()
    private val jumpingJackTracker = JumpingJackTracker()
    private val lungeTracker = LungeTracker()
    private val plankTracker = PlankTracker()
    private lateinit var calorieEstimator: CalorieEstimator

    private var showCurls = false
    private var showPushups = false
    private var showSquats = false
    private var showSitups = false
    private var showOverhead = false
    private var showJumpingJacks = false
    private var showLunges = false
    private var showPlanks = false
    private var showCalories = true

    private var tts: TextToSpeech? = null
    private var lastSpokenTime = 0L
    private val TTS_COOLDOWN_MS = 3000L
    private var isSoundEnabled = true

    private val isUiUpdatePending = AtomicBoolean(false)
    private var lastUiUpdateTime = 0L
    private val UI_UPDATE_INTERVAL_MS = 33L
    private var isFrontCamera = true

    private var lastActiveExercise: String? = null
    private var lastActiveTime = 0L
    private val HIGHLIGHT_DURATION_MS = 5000L

    companion object {
        private const val TAG = "LimbDetector"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

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

        val weightLb = intent.getFloatExtra("WEIGHT", 130f)
        val heightFt = intent.getFloatExtra("HEIGHT", 5.6f)
        val age = intent.getIntExtra("AGE", 18)
        val gender = intent.getStringExtra("GENDER") ?: "male"
        
        val weightKg = weightLb * 0.453592f
        val heightM = heightFt * 0.3048f
        calorieEstimator = CalorieEstimator(weightKg, heightM, age, gender)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)
        alignmentStatus = findViewById(R.id.alignmentStatus)
        liveStatsText = findViewById(R.id.liveStatsText)
        soundButton = findViewById(R.id.soundButton)
        
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.endTaskButton).setOnClickListener { endWorkout() }
        findViewById<ImageButton>(R.id.filterButton).setOnClickListener { showFilterDialog() }
        
        soundButton.setOnClickListener { toggleSound() }

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

    private fun speakFeedback(text: String) {
        if (!isSoundEnabled) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpokenTime > TTS_COOLDOWN_MS) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpokenTime = currentTime
        }
    }

    private fun showFilterDialog() {
        val exercises = arrayOf("Curls", "Push-ups", "Squats", "Sit-ups", "Overhead Press", "Jumping Jacks", "Lunges", "Planks", "Calories")
        val checkedItems = booleanArrayOf(showCurls, showPushups, showSquats, showSitups, showOverhead, showJumpingJacks, showLunges, showPlanks, showCalories)

        AlertDialog.Builder(this)
            .setTitle("Pin to Sidebar")
            .setMultiChoiceItems(exercises, checkedItems) { _, index, isChecked ->
                when (index) {
                    0 -> showCurls = isChecked
                    1 -> showPushups = isChecked
                    2 -> showSquats = isChecked
                    3 -> showSitups = isChecked
                    4 -> showOverhead = isChecked
                    5 -> showJumpingJacks = isChecked
                    6 -> showLunges = isChecked
                    7 -> showPlanks = isChecked
                    8 -> showCalories = isChecked
                }
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startInitialization() {
        runOnUiThread { alignmentStatus.text = "Initializing AI..." }
        cameraExecutor.execute {
            setupPoseLandmarker()
            runOnUiThread { startCamera() }
        }
    }

    private fun endWorkout() {
        val allReps = mutableListOf<RepResult>()
        allReps.addAll(curlsTracker.sessionReps)
        allReps.addAll(pushUpTracker.sessionReps)
        allReps.addAll(squatTracker.sessionReps)
        allReps.addAll(situpTracker.sessionReps)
        allReps.addAll(overheadTracker.sessionReps)
        allReps.addAll(jumpingJackTracker.sessionReps)
        allReps.addAll(lungeTracker.sessionReps)
        
        val totalRepsCount = curlsTracker.curlCount + pushUpTracker.pushUpCount + 
                             squatTracker.squatCount + situpTracker.situpCount + 
                             overheadTracker.overheadCount + jumpingJackTracker.jackCount + 
                             lungeTracker.lungeCount + (plankTracker.totalSeconds / 10) // Plank counts as "reps" for score
        
        val overallScore = if (totalRepsCount == 0) -1 else allReps.map { it.score }.average().toInt()

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

        val intent = Intent(this, SummaryActivity::class.java).apply {
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
        }
        startActivity(intent)
        finish()
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

    private fun processLandmarkerResult(result: PoseLandmarkerResult, image: MPImage) {
        curlsTracker.update(result)
        pushUpTracker.update(result)
        squatTracker.update(result)
        situpTracker.update(result)
        overheadTracker.update(result)
        jumpingJackTracker.update(result)
        lungeTracker.update(result)
        plankTracker.update(result)
        calorieEstimator.update(result, System.currentTimeMillis())
        
        val currentTime = System.currentTimeMillis()
        
        when {
            curlsTracker.inCurlMotion -> { lastActiveExercise = "Curls"; lastActiveTime = currentTime }
            pushUpTracker.inPushUpMotion -> { lastActiveExercise = "Push-ups"; lastActiveTime = currentTime }
            squatTracker.inSquatMotion -> { lastActiveExercise = "Squats"; lastActiveTime = currentTime }
            situpTracker.inSitupMotion -> { lastActiveExercise = "Sit-ups"; lastActiveTime = currentTime }
            overheadTracker.inPressMotion -> { lastActiveExercise = "Overhead"; lastActiveTime = currentTime }
            jumpingJackTracker.inMotion -> { lastActiveExercise = "Jacks"; lastActiveTime = currentTime }
            lungeTracker.inMotion -> { lastActiveExercise = "Lunges"; lastActiveTime = currentTime }
            plankTracker.isPlanking -> { lastActiveExercise = "Plank"; lastActiveTime = currentTime }
        }

        if (currentTime - lastUiUpdateTime >= UI_UPDATE_INTERVAL_MS && isUiUpdatePending.compareAndSet(false, true)) {
            lastUiUpdateTime = currentTime
            val statsBuilder = SpannableStringBuilder()
            
            fun appendExerciseStats(name: String, feedback: List<String>, isPinned: Boolean) {
                val isActive = name == lastActiveExercise && (currentTime - lastActiveTime < HIGHLIGHT_DURATION_MS)
                if (!isPinned && !isActive) return
                
                val start = statsBuilder.length
                val filteredFeedback = feedback.filter { !it.contains("visible", ignoreCase = true) && !it.contains("body", ignoreCase = true) && it.isNotBlank() }
                
                if (filteredFeedback.isNotEmpty()) {
                    statsBuilder.append(filteredFeedback.joinToString("\n")).append("\n\n")
                } else if (isPinned && feedback.isNotEmpty()) {
                    statsBuilder.append(feedback[0]).append("\n\n")
                }
                
                if (isActive && statsBuilder.length > start) {
                    statsBuilder.setSpan(StyleSpan(Typeface.BOLD), start, statsBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    statsBuilder.setSpan(RelativeSizeSpan(1.3f), start, statsBuilder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val lastLine = if (filteredFeedback.isNotEmpty()) filteredFeedback.last() else ""
                    if (lastLine.contains("!")) speakFeedback(lastLine)
                }
            }

            appendExerciseStats("Curls", curlsTracker.feedback, showCurls)
            appendExerciseStats("Push-ups", pushUpTracker.feedback, showPushups)
            appendExerciseStats("Squats", squatTracker.feedback, showSquats)
            appendExerciseStats("Sit-ups", situpTracker.feedback, showSitups)
            appendExerciseStats("Overhead", overheadTracker.feedback, showOverhead)
            appendExerciseStats("Jacks", jumpingJackTracker.feedback, showJumpingJacks)
            appendExerciseStats("Lunges", lungeTracker.feedback, showLunges)
            appendExerciseStats("Plank", plankTracker.feedback, showPlanks)

            if (showCalories) statsBuilder.append(calorieEstimator.feedback.joinToString("\n")).append("\n\n")

            val isBodyFound = result.landmarks().isNotEmpty()
            runOnUiThread {
                try {
                    if (isBodyFound) {
                        alignmentStatus.text = "Properly Aligned"
                        alignmentStatus.setTextColor(Color.GREEN)
                        alignmentStatus.setBackgroundColor(Color.parseColor("#3300FF00"))
                        liveStatsText.text = statsBuilder
                        overlayView.setResults(result, image.height, image.width, emptyList())
                    } else {
                        alignmentStatus.text = "Searching for body..."
                        alignmentStatus.setTextColor(Color.RED)
                        alignmentStatus.setBackgroundColor(Color.parseColor("#33FF0000"))
                        liveStatsText.text = if (statsBuilder.isNotEmpty()) statsBuilder else ""
                        overlayView.clear()
                    }
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
        tts?.stop()
        tts?.shutdown()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
    }
}
