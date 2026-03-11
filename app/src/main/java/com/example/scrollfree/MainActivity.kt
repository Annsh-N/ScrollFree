package com.example.scrollfree

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.scrollfree.accessibility.ScrollAccessibilityService
import com.example.scrollfree.core.AppRuntimeState
import com.example.scrollfree.core.AppSettingsRepository
import com.example.scrollfree.model.ServiceStatus
import com.example.scrollfree.model.UserSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: AppSettingsRepository

    private lateinit var runtimeStateText: TextView
    private lateinit var runtimeHintText: TextView
    private lateinit var cameraStatusText: TextView
    private lateinit var overlayStatusText: TextView
    private lateinit var accessibilityStatusText: TextView

    private lateinit var btnGrantCamera: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button

    private lateinit var switchDetection: Switch
    private lateinit var switchOverlay: Switch
    private lateinit var seekSensitivity: SeekBar
    private lateinit var sensitivityValueText: TextView

    private var renderingUi = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionStatusViews()
        if (it && settingsRepository.settings.value.detectionEnabled) {
            startDetectionServiceIfNeeded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsRepository = AppSettingsRepository.get(this)

        bindViews()
        bindListeners()
        observeAppState()
        refreshPermissionStatusViews()
    }

    override fun onResume() {
        super.onResume()
        settingsRepository.refresh()
        refreshPermissionStatusViews()

        if (settingsRepository.settings.value.detectionEnabled) {
            startDetectionServiceIfNeeded()
        }
    }

    private fun bindViews() {
        runtimeStateText = findViewById(R.id.runtimeStateText)
        runtimeHintText = findViewById(R.id.runtimeHintText)
        cameraStatusText = findViewById(R.id.cameraStatusText)
        overlayStatusText = findViewById(R.id.overlayStatusText)
        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)

        btnGrantCamera = findViewById(R.id.btnGrantCamera)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)

        switchDetection = findViewById(R.id.switchDetection)
        switchOverlay = findViewById(R.id.switchOverlay)
        seekSensitivity = findViewById(R.id.seekSensitivity)
        sensitivityValueText = findViewById(R.id.sensitivityValueText)
    }

    private fun bindListeners() {
        btnGrantCamera.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        switchOverlay.setOnCheckedChangeListener { _, enabled ->
            if (renderingUi) return@setOnCheckedChangeListener
            settingsRepository.updateOverlayEnabled(enabled)

            if (enabled && !hasOverlayPermission()) {
                Toast.makeText(this, "Grant overlay permission to show the widget", Toast.LENGTH_SHORT).show()
            }
            if (settingsRepository.settings.value.detectionEnabled) {
                startDetectionServiceIfNeeded()
            }
            refreshPermissionStatusViews()
        }

        switchDetection.setOnCheckedChangeListener { _, enabled ->
            if (renderingUi) return@setOnCheckedChangeListener
            if (enabled) {
                enableDetectionFromUi()
            } else {
                disableDetectionFromUi()
            }
        }

        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 1
                sensitivityValueText.text = "$value%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val newValue = (seekBar?.progress ?: 54) + 1
                settingsRepository.updateSensitivity(newValue)
            }
        })
    }

    private fun enableDetectionFromUi() {
        if (!hasCameraPermission()) {
            setDetectionToggleSilently(false)
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            setDetectionToggleSilently(false)
            Toast.makeText(this, "Enable ScrollFree accessibility service first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        settingsRepository.updateDetectionEnabled(true)
        startDetectionServiceIfNeeded()
    }

    private fun disableDetectionFromUi() {
        settingsRepository.updateDetectionEnabled(false)
        stopService(Intent(this, BlinkDetectionService::class.java))
        AppRuntimeState.setServiceStatus(ServiceStatus.INACTIVE)
    }

    private fun setDetectionToggleSilently(enabled: Boolean) {
        renderingUi = true
        switchDetection.isChecked = enabled
        renderingUi = false
    }

    private fun startDetectionServiceIfNeeded() {
        ContextCompat.startForegroundService(this, Intent(this, BlinkDetectionService::class.java))
    }

    private fun observeAppState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsRepository.settings.collect { renderSettings(it) }
                }
                launch {
                    AppRuntimeState.serviceStatus.collect { renderServiceStatus(it) }
                }
            }
        }
    }

    private fun renderSettings(settings: UserSettings) {
        renderingUi = true
        switchDetection.isChecked = settings.detectionEnabled
        switchOverlay.isChecked = settings.overlayEnabled
        seekSensitivity.progress = (settings.sensitivity - 1).coerceIn(0, 99)
        sensitivityValueText.text = "${settings.sensitivity}%"
        renderingUi = false
    }

    private fun renderServiceStatus(status: ServiceStatus) {
        runtimeStateText.text = when (status) {
            ServiceStatus.INACTIVE -> "Status: Inactive"
            ServiceStatus.STARTING -> "Status: Starting"
            ServiceStatus.ACTIVE -> "Status: Active"
            ServiceStatus.NO_FACE -> "Status: No face detected"
            ServiceStatus.ERROR -> "Status: Attention needed"
        }

        runtimeHintText.text = when (status) {
            ServiceStatus.INACTIVE -> "Gesture map: single blink = scroll down, double blink = scroll up"
            ServiceStatus.STARTING -> "Initializing camera and analyzer"
            ServiceStatus.ACTIVE -> "Blink to scroll. Keep your face centered for better accuracy."
            ServiceStatus.NO_FACE -> "No face in frame. Move into front camera view."
            ServiceStatus.ERROR -> "Check camera/accessibility permissions and service state."
        }
    }

    private fun refreshPermissionStatusViews() {
        val cameraGranted = hasCameraPermission()
        val overlayGranted = hasOverlayPermission()
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        cameraStatusText.text = "Camera: ${if (cameraGranted) "granted" else "missing"}"
        overlayStatusText.text = "Overlay: ${if (overlayGranted) "granted" else "missing"}"
        accessibilityStatusText.text = "Accessibility: ${if (accessibilityEnabled) "enabled" else "missing"}"

        btnGrantCamera.visibility = if (cameraGranted) View.GONE else View.VISIBLE
        btnGrantOverlay.visibility = if (overlayGranted) View.GONE else View.VISIBLE
        btnGrantAccessibility.visibility = if (accessibilityEnabled) View.GONE else View.VISIBLE
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, ScrollAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }
}
