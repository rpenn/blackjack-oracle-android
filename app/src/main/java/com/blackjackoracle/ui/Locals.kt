package com.blackjackoracle.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.blackjackoracle.service.billing.EntitlementStore
import com.blackjackoracle.service.billing.PaywallController
import com.blackjackoracle.service.billing.PurchaseManager
import com.blackjackoracle.tutorial.TutorialController

/// Billing singletons exposed to Composables without prop-drilling. Provided in
/// MainActivity from the BlackjackApp instance.
val LocalEntitlements = staticCompositionLocalOf<EntitlementStore> {
    error("LocalEntitlements not provided")
}
val LocalPurchases = staticCompositionLocalOf<PurchaseManager> {
    error("LocalPurchases not provided")
}
val LocalPaywall = staticCompositionLocalOf<PaywallController> {
    error("LocalPaywall not provided")
}

/// The guided-first-hand controller, provided in AppRoot so the table screen,
/// bottom rail, and overlay all read one step machine.
val LocalTutorial = staticCompositionLocalOf<TutorialController> {
    error("LocalTutorial not provided")
}
