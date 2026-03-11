package com.example.scrollfree.core

import com.example.scrollfree.model.OverlayUiState
import com.example.scrollfree.model.ServiceStatus
import com.example.scrollfree.model.ScrollAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppRuntimeState {

    private val _serviceStatus = MutableStateFlow(ServiceStatus.INACTIVE)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _overlayState = MutableStateFlow(OverlayUiState())
    val overlayState: StateFlow<OverlayUiState> = _overlayState.asStateFlow()

    fun setServiceStatus(status: ServiceStatus) {
        _serviceStatus.value = status
        val message = when (status) {
            ServiceStatus.INACTIVE -> "Inactive"
            ServiceStatus.STARTING -> "Starting"
            ServiceStatus.ACTIVE -> "Active"
            ServiceStatus.NO_FACE -> "No face"
            ServiceStatus.ERROR -> "Error"
        }
        _overlayState.value = _overlayState.value.copy(
            active = status == ServiceStatus.ACTIVE,
            message = message
        )
    }

    fun showActionFeedback(action: ScrollAction) {
        _overlayState.value = _overlayState.value.copy(
            lastAction = action,
            feedbackVisible = true
        )
    }

    fun hideActionFeedbackIfVisible() {
        if (!_overlayState.value.feedbackVisible) return

        _overlayState.value = _overlayState.value.copy(
            feedbackVisible = false,
            lastAction = null
        )
    }
}
