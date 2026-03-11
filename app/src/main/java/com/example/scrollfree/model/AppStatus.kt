package com.example.scrollfree.model

enum class ServiceStatus {
    INACTIVE,
    STARTING,
    ACTIVE,
    NO_FACE,
    ERROR
}

enum class ScrollAction {
    UP,
    DOWN
}

data class OverlayUiState(
    val active: Boolean = false,
    val message: String = "Inactive",
    val lastAction: ScrollAction? = null,
    val feedbackVisible: Boolean = false
)
