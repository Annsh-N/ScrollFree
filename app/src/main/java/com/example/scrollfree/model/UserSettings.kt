package com.example.scrollfree.model

data class UserSettings(
    val detectionEnabled: Boolean = false,
    val overlayEnabled: Boolean = true,
    val sensitivity: Int = 55,
    val cooldownMs: Long = 1200L,
    val doubleBlinkWindowMs: Long = 650L
)

fun UserSettings.blinkThreshold(): Float {
    // Higher sensitivity makes blink recognition easier to trigger.
    val normalized = sensitivity.coerceIn(1, 100) / 100f
    return 0.25f + (normalized * 0.35f)
}
