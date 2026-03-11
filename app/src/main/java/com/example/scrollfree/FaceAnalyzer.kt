package com.example.scrollfree

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetection
import com.example.scrollfree.model.ScrollAction
import com.example.scrollfree.model.UserSettings
import com.example.scrollfree.model.blinkThreshold

class FaceAnalyzer(
    private val settingsProvider: () -> UserSettings,
    private val listener: Listener
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector
    private var eyesClosed = false
    private var blinkStartMs = 0L
    private var pendingBlinkAtMs: Long? = null
    private var lastActionAtMs: Long = 0L
    private var lastFaceSeenAtMs: Long = 0L
    private var noFaceReported = false

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()

        detector = FaceDetection.getClient(options)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        processPendingSingleBlink(now)

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                processFaces(faces, now)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                Log.e("ScrollFree", "Face detection failed: ${e.message}")
                listener.onDetectorError(e.message ?: "Face detector failure")
                imageProxy.close()
            }
    }

    private fun processFaces(faces: List<Face>, now: Long) {
        if (faces.isEmpty()) {
            if (!noFaceReported && now - lastFaceSeenAtMs > 1000L) {
                listener.onNoFace()
                noFaceReported = true
            }
            return
        }

        lastFaceSeenAtMs = now
        if (noFaceReported) {
            noFaceReported = false
        }
        listener.onFaceFound()

        val settings = settingsProvider()
        val threshold = settings.blinkThreshold()

        for (face in faces) {
            val leftEyeOpen = face.leftEyeOpenProbability ?: -1.0f
            val rightEyeOpen = face.rightEyeOpenProbability ?: -1.0f

            if (leftEyeOpen >= 0 && rightEyeOpen >= 0) {
                val bothEyesClosed = leftEyeOpen < threshold && rightEyeOpen < threshold
                if (bothEyesClosed && !eyesClosed) {
                    eyesClosed = true
                    blinkStartMs = now
                } else if (!bothEyesClosed && eyesClosed) {
                    eyesClosed = false
                    val closedDuration = now - blinkStartMs
                    if (closedDuration in 70L..450L) {
                        registerBlink(now, settings)
                    }
                }
            }
        }
    }

    private fun registerBlink(now: Long, settings: UserSettings) {
        val firstBlinkTs = pendingBlinkAtMs
        if (firstBlinkTs == null) {
            pendingBlinkAtMs = now
            return
        }

        val withinDoubleBlinkWindow = now - firstBlinkTs <= settings.doubleBlinkWindowMs
        if (withinDoubleBlinkWindow) {
            pendingBlinkAtMs = null
            emitAction(ScrollAction.UP, now, settings)
            return
        }

        pendingBlinkAtMs = now
    }

    private fun processPendingSingleBlink(now: Long) {
        val firstBlinkTs = pendingBlinkAtMs ?: return
        val settings = settingsProvider()
        if (now - firstBlinkTs >= settings.doubleBlinkWindowMs) {
            pendingBlinkAtMs = null
            emitAction(ScrollAction.DOWN, now, settings)
        }
    }

    private fun emitAction(action: ScrollAction, now: Long, settings: UserSettings) {
        if (now - lastActionAtMs < settings.cooldownMs) {
            return
        }
        lastActionAtMs = now
        listener.onAction(action)
    }

    interface Listener {
        fun onFaceFound()
        fun onNoFace()
        fun onAction(action: ScrollAction)
        fun onDetectorError(message: String)
    }
}
