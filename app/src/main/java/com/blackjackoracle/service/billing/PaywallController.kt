package com.blackjackoracle.service.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/// Drives paywall presentation. `lastPlacement` records which entry point opened
/// it (for analytics / copy), mirroring the iOS controller.
class PaywallController {
    private val _isPresented = MutableStateFlow(false)
    val isPresented: StateFlow<Boolean> = _isPresented.asStateFlow()

    var lastPlacement: String? = null
        private set

    fun present(placement: String) {
        lastPlacement = placement
        _isPresented.value = true
    }

    fun dismiss() {
        _isPresented.value = false
    }
}
