package com.blackjackoracle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.blackjackoracle.ui.AppRoot
import com.blackjackoracle.ui.theme.BlackjackOracleTheme
import com.blackjackoracle.viewmodel.GameViewModel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.blackjackoracle.service.billing.EntitlementStore
import com.blackjackoracle.service.billing.PaywallController
import com.blackjackoracle.service.billing.PurchaseManager

val LocalEntitlements = staticCompositionLocalOf<EntitlementStore> { error("No EntitlementStore provided") }
val LocalPaywall = staticCompositionLocalOf<PaywallController> { error("No PaywallController provided") }
val LocalPurchases = staticCompositionLocalOf<PurchaseManager> { error("No PurchaseManager provided") }

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BlackjackApp
        setContent {
            CompositionLocalProvider(
                LocalEntitlements provides app.entitlements,
                LocalPaywall provides app.paywall,
                LocalPurchases provides app.purchases
            ) {
                BlackjackOracleTheme {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Cut Oliver off when the app leaves the foreground — MediaPlayer would
        // otherwise keep narrating into the background with no UI to control it.
        vm.stopSpeaking()
    }
}
