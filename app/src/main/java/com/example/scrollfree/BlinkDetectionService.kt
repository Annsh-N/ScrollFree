package com.example.scrollfree

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.scrollfree.accessibility.ScrollActionDispatcher
import com.example.scrollfree.core.AppRuntimeState
import com.example.scrollfree.core.AppSettingsRepository
import com.example.scrollfree.model.OverlayUiState
import com.example.scrollfree.model.ScrollAction
import com.example.scrollfree.model.ServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BlinkDetectionService : Service(), FaceAnalyzer.Listener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settingsRepository: AppSettingsRepository

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraRunning = false

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayMessageText: TextView? = null
    private var overlayFeedbackText: TextView? = null
    private var overlayStatusDot: View? = null
    private var feedbackWasVisible = false

    private var settingsJob: Job? = null
    private var overlayJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = AppSettingsRepository.get(this)

        AppRuntimeState.setServiceStatus(ServiceStatus.STARTING)
        observeOverlayState()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        return START_STICKY
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                if (settings.overlayEnabled) {
                    showOverlayIfAllowed()
                } else {
                    hideOverlay()
                }

                if (settings.detectionEnabled) {
                    startCameraIfPossible()
                } else {
                    stopCamera()
                    AppRuntimeState.setServiceStatus(ServiceStatus.INACTIVE)
                }
            }
        }
    }

    private fun observeOverlayState() {
        overlayJob?.cancel()
        overlayJob = serviceScope.launch {
            AppRuntimeState.overlayState.collectLatest { state ->
                renderOverlayState(state)
            }
        }
    }

    private fun renderOverlayState(state: OverlayUiState) {
        overlayMessageText?.text = state.message
        overlayView?.alpha = if (state.active) 1.0f else 0.78f
        overlayStatusDot?.setBackgroundResource(
            if (state.active) R.drawable.overlay_dot_active else R.drawable.overlay_dot_inactive
        )

        if (state.feedbackVisible && state.lastAction != null) {
            overlayFeedbackText?.text = if (state.lastAction == ScrollAction.DOWN) {
                "Scroll ↓"
            } else {
                "Scroll ↑"
            }
            overlayFeedbackText?.visibility = View.VISIBLE
            if (!feedbackWasVisible) {
                overlayFeedbackText?.alpha = 0f
                overlayFeedbackText?.animate()?.alpha(1f)?.setDuration(160)?.start()
            }
            feedbackWasVisible = true
        } else {
            if (feedbackWasVisible) {
                overlayFeedbackText?.animate()
                    ?.alpha(0f)
                    ?.setDuration(120)
                    ?.withEndAction {
                        overlayFeedbackText?.visibility = View.GONE
                        overlayFeedbackText?.alpha = 1f
                    }
                    ?.start()
            } else {
                overlayFeedbackText?.visibility = View.GONE
            }
            feedbackWasVisible = false
        }
    }

    private fun startCameraIfPossible() {
        if (cameraRunning) {
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            AppRuntimeState.setServiceStatus(ServiceStatus.ERROR)
            Log.w(TAG, "Camera permission missing; cannot start blink detection")
            return
        }

        AppRuntimeState.setServiceStatus(ServiceStatus.STARTING)

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                val analyzer = FaceAnalyzer(
                    settingsProvider = { settingsRepository.settings.value },
                    listener = this
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(this), analyzer)
                    }

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(ProcessLifecycleOwner.get(), selector, imageAnalysis)
                cameraRunning = true
            } catch (t: Throwable) {
                cameraRunning = false
                AppRuntimeState.setServiceStatus(ServiceStatus.ERROR)
                Log.e(TAG, "Failed to bind camera", t)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to unbind camera cleanly", t)
        } finally {
            cameraRunning = false
            cameraProvider = null
        }
    }

    private fun createNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Blink Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service for ScrollFree blink detection"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        val notification = notificationBuilder
            .setContentTitle("ScrollFree Running")
            .setContentText("Blink detection active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showOverlayIfAllowed() {
        if (overlayView != null) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; widget will stay hidden")
            return
        }

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_widget, null)
            overlayMessageText = overlayView?.findViewById(R.id.blinkStatusText)
            overlayFeedbackText = overlayView?.findViewById(R.id.feedbackText)
            overlayStatusDot = overlayView?.findViewById(R.id.statusDot)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 20
                y = 140
            }

            windowManager?.addView(overlayView, params)
            renderOverlayState(AppRuntimeState.overlayState.value)
        } catch (t: Throwable) {
            overlayView = null
            overlayMessageText = null
            Log.e(TAG, "Failed to show overlay", t)
        }
    }

    private fun hideOverlay() {
        try {
            val view = overlayView
            if (view != null) {
                windowManager?.removeView(view)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to remove overlay view", t)
        } finally {
            overlayView = null
            overlayMessageText = null
            overlayFeedbackText = null
            overlayStatusDot = null
            feedbackWasVisible = false
        }
    }

    override fun onFaceFound() {
        AppRuntimeState.setServiceStatus(ServiceStatus.ACTIVE)
    }

    override fun onNoFace() {
        AppRuntimeState.setServiceStatus(ServiceStatus.NO_FACE)
    }

    override fun onAction(action: ScrollAction) {
        val dispatched = ScrollActionDispatcher.dispatch(action)
        if (!dispatched) {
            AppRuntimeState.setServiceStatus(ServiceStatus.ERROR)
            Log.w(TAG, "Scroll action ignored because accessibility service is not connected")
            return
        }

        AppRuntimeState.showActionFeedback(action)
        serviceScope.launch {
            delay(900)
            AppRuntimeState.hideActionFeedbackIfVisible()
        }
    }

    override fun onDetectorError(message: String) {
        AppRuntimeState.setServiceStatus(ServiceStatus.ERROR)
        Log.e(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        hideOverlay()
        settingsJob?.cancel()
        overlayJob?.cancel()
        serviceScope.cancel()
        AppRuntimeState.setServiceStatus(ServiceStatus.INACTIVE)
    }

    companion object {
        private const val TAG = "ScrollFree"
        private const val CHANNEL_ID = "scrollfree_blink_service"
        private const val NOTIFICATION_ID = 1
    }
}
