package com.blackjackoracle.service.billing

import android.app.Activity
import android.content.Context
import com.blackjackoracle.BuildConfig
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/// Owns the entire RevenueCat lifecycle. No other file imports the SDK — the
/// rest of the app sees only [PaywallProduct] and the entitlement flow.
class PurchaseManager(
    private val context: Context,
    private val entitlements: EntitlementStore,
) {
    private val _products = MutableStateFlow<List<PaywallProduct>>(emptyList())
    val products: StateFlow<List<PaywallProduct>> = _products.asStateFlow()

    /// SDK packages kept here, keyed by identifier; the UI references them by id.
    private val packagesById = mutableMapOf<String, Package>()

    private var configured = false

    val appUserID: String?
        get() = if (configured) Purchases.sharedInstance.appUserID else null

    fun configure() {
        if (configured) return
        // logLevel is set by BlackjackApp.onCreate before this runs.
        Purchases.configure(PurchasesConfiguration.Builder(context, API_KEY).build())
        // Pushes renewals / cross-device restores into the entitlement store.
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info -> applyEntitlement(info) }
        configured = true
    }

    /// Fetch current entitlement + offerings. Failures are swallowed (offline
    /// launch): the user simply stays non-premium until a later refresh.
    suspend fun start() {
        runCatching { applyEntitlement(Purchases.sharedInstance.awaitCustomerInfo()) }
        runCatching { loadOfferings() }
    }

    /// Re-fetch offerings on demand (paywall Retry). Returns true if at least one
    /// product loaded. Distinguishes a genuine failure (threw) from "loaded but
    /// the offering is empty" — both leave `products` empty, but the paywall
    /// shows different copy for each.
    suspend fun refreshProducts(): Boolean =
        runCatching { loadOfferings() }.getOrDefault(false)

    /// Returns true if the fetched offering contained at least one product.
    private suspend fun loadOfferings(): Boolean {
        val offerings = Purchases.sharedInstance.awaitOfferings()
        val packages = offerings.current?.availablePackages.orEmpty()
        packagesById.clear()
        _products.value = packages
            .onEach { packagesById[it.identifier] = it }
            .map { pkg ->
                PaywallProduct(
                    id = pkg.identifier,
                    title = pkg.product.title,
                    priceFormatted = pkg.product.price.formatted,
                    isAnnual = pkg.packageType == PackageType.ANNUAL,
                )
            }
            // Yearly first (mirrors iOS ordering).
            .sortedByDescending { it.isAnnual }
        return _products.value.isNotEmpty()
    }

    /// Android requires the current Activity to launch the billing flow.
    suspend fun purchase(activity: Activity, product: PaywallProduct): Boolean {
        val pkg = packagesById[product.id] ?: return false
        val result = Purchases.sharedInstance.awaitPurchase(
            PurchaseParams.Builder(activity, pkg).build(),
        )
        applyEntitlement(result.customerInfo)
        return entitlements.entitled
    }

    suspend fun restore(): Boolean {
        applyEntitlement(Purchases.sharedInstance.awaitRestore())
        return entitlements.entitled
    }

    private fun applyEntitlement(info: CustomerInfo) {
        entitlements.entitled = info.entitlements[ENTITLEMENT_ID]?.isActive == true
    }

    companion object {
        const val ENTITLEMENT_ID = "Blackjack Oracle Pro"

        // RevenueCat public SDK key.
        // - Debug builds (local development): the project's Test Store key —
        //   purchases are simulated by the SDK; no Google Play Billing involved.
        // - Release builds (signed AAB → internal/closed/production): the Google
        //   Play public key — hits real Play Billing. License testers on the
        //   internal-testing track are not charged.
        // Public SDK keys are intended to be embedded in clients; safe to commit.
        val API_KEY: String =
            if (BuildConfig.DEBUG) "test_QDgyNJLlCdEHxlvqXPdnJwRYMmE"
            else "goog_uGzxHkbIgZCvkIcEghauPhtcaaA"
    }
}
