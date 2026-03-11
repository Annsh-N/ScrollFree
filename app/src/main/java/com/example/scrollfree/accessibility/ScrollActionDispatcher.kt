package com.example.scrollfree.accessibility

import java.lang.ref.WeakReference
import com.example.scrollfree.model.ScrollAction

object ScrollActionDispatcher {

    private var serviceRef: WeakReference<ScrollAccessibilityService>? = null

    fun attach(service: ScrollAccessibilityService) {
        serviceRef = WeakReference(service)
    }

    fun detach(service: ScrollAccessibilityService) {
        val current = serviceRef?.get() ?: return
        if (current == service) {
            serviceRef = null
        }
    }

    fun isConnected(): Boolean = serviceRef?.get() != null

    fun dispatch(action: ScrollAction): Boolean {
        val service = serviceRef?.get() ?: return false
        return service.performScroll(action)
    }
}
