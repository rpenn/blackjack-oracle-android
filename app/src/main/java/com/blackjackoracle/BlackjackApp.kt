package com.blackjackoracle

import android.app.Application
import com.blackjackoracle.service.billing.EntitlementStore
import com.blackjackoracle.service.billing.PaywallController
import com.blackjackoracle.service.billing.PurchaseManager
import com.blackjackoracle.service.billing.TrialTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.LogLevel
/// Owns the billing singletons for the whole process (no DI framework). The VM
/// reads these via getApplication<BlackjackApp>(); Composables via CompositionLocals.
class BlackjackApp : Application() {

    lateinit var entitlements: EntitlementStore
        private set
    lateinit var purchases: PurchaseManager
        private set
    val paywall = PaywallController()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        entitlements = EntitlementStore(TrialTokenStore(this))
        purchases = PurchaseManager(applicationContext, entitlements)
        purchases.configure()
        appScope.launch { purchases.start() }
    }
}
