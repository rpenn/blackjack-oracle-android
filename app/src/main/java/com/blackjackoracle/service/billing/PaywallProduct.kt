package com.blackjackoracle.service.billing

/// UI-facing product model. Decouples the paywall from the RevenueCat SDK — the
/// actual `Package` lives inside PurchaseManager, keyed by [id].
data class PaywallProduct(
    val id: String,
    val title: String,
    val priceFormatted: String,
    val isAnnual: Boolean,
)
