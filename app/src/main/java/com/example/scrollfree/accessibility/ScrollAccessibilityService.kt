package com.example.scrollfree.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.scrollfree.model.ScrollAction

class ScrollAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollActionDispatcher.attach(this)
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event stream is unused for MVP. We only need gesture dispatch capabilities.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ScrollActionDispatcher.detach(this)
        return super.onUnbind(intent)
    }

    fun performScroll(action: ScrollAction): Boolean {
        return when (action) {
            ScrollAction.DOWN -> dispatchSwipe(
                startXRatio = 0.5f,
                startYRatio = 0.78f,
                endXRatio = 0.5f,
                endYRatio = 0.32f
            )

            ScrollAction.UP -> dispatchSwipe(
                startXRatio = 0.5f,
                startYRatio = 0.32f,
                endXRatio = 0.5f,
                endYRatio = 0.78f
            )
        }
    }

    private fun dispatchSwipe(
        startXRatio: Float,
        startYRatio: Float,
        endXRatio: Float,
        endYRatio: Float
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val path = Path().apply {
            moveTo(width * startXRatio, height * startYRatio)
            lineTo(width * endXRatio, height * endYRatio)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 220))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private const val TAG = "ScrollFree"
    }
}
