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

class BlackjackApp : Application() {
    lateinit var entitlements: EntitlementStore
        private set
    lateinit var purchases: PurchaseManager
        private set
    val paywall = PaywallController()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        entitlements = EntitlementStore(TrialTokenStore(this))
        PurchaseManager.configure(this)
        purchases = PurchaseManager(this, entitlements)
        
        applicationScope.launch {
            purchases.start()
        }
    }
}
