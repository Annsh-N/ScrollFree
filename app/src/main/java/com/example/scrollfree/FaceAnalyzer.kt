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

class FaceAnalyzer : ImageAnalysis.Analyzer {

    private val detector: FaceDetector

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
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                processFaces(faces)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                Log.e("ScrollFree", "Face detection failed: ${e.message}")
                imageProxy.close()
            }
    }

    private fun processFaces(faces: List<Face>) {
        for (face in faces) {
            val leftEyeOpen = face.leftEyeOpenProbability ?: -1.0f
            val rightEyeOpen = face.rightEyeOpenProbability ?: -1.0f

            if (leftEyeOpen >= 0 && rightEyeOpen >= 0) {
                val bothEyesClosed = leftEyeOpen < 0.4 && rightEyeOpen < 0.4
                if (bothEyesClosed) {
                    Log.d("ScrollFree", "Blink detected")
                    // Later: Trigger ScrollUp or ScrollDown
                }
            }
        }
    }
}
