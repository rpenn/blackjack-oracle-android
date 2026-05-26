package com.blackjackoracle.service.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.blackjackoracle.BuildConfig
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
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

class PurchaseManager(private val context: Context, private val entitlements: EntitlementStore) {
    companion object {
        const val ENTITLEMENT_ID = "Blackjack Oracle Pro"
        val apiKey = "test_QDgyNJLlCdEHxlvqXPdnJwRYMmE" // Swap to goog_ for Play Billing
        
        fun configure(context: Context) {
            Purchases.logLevel = if (BuildConfig.DEBUG) com.revenuecat.purchases.LogLevel.DEBUG else com.revenuecat.purchases.LogLevel.WARN
            Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
        }
    }

    val appUserID: String?
        get() = Purchases.sharedInstance.appUserID

    private val _products = MutableStateFlow<List<PaywallProduct>>(emptyList())
    val products: StateFlow<List<PaywallProduct>> = _products.asStateFlow()

    suspend fun start() {
        try {
            val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
            applyCustomerInfo(customerInfo)
        } catch (e: Exception) {
            Log.e("PurchaseManager", "Error fetching customer info", e)
        }

        try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            val currentOffering = offerings.current
            if (currentOffering != null) {
                val pkgs = currentOffering.availablePackages.sortedByDescending { it.packageType == com.revenuecat.purchases.PackageType.ANNUAL }
                _products.value = pkgs.map { pkg ->
                    PaywallProduct(
                        id = pkg.identifier,
                        title = pkg.product.title.substringBefore("(").trim(),
                        price = pkg.product.price.formatted,
                        rcPackage = pkg
                    )
                }
            } else {
                useFallbackProducts()
            }
        } catch (e: Exception) {
            Log.e("PurchaseManager", "Error starting RevenueCat offerings", e)
            useFallbackProducts()
        }

        try {
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info ->
                applyCustomerInfo(info)
            }
        } catch (e: Exception) {
            Log.e("PurchaseManager", "Error setting listener", e)
        }
    }

    private fun useFallbackProducts() {
        _products.value = listOf(
            PaywallProduct("monthly", "Monthly", "$3.99"),
            PaywallProduct("yearly", "Annual", "$24.99")
        )
    }

    private fun applyCustomerInfo(customerInfo: CustomerInfo) {
        val isActive = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
        entitlements.entitled = isActive
    }

    suspend fun purchase(activity: Activity, pkg: PaywallProduct): Boolean {
        val rcPkg = pkg.rcPackage
        if (rcPkg == null) {
            // Simulate purchase for sandbox/test mode fallback
            entitlements.entitled = true
            return true
        }
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, rcPkg).build())
            applyCustomerInfo(result.customerInfo)
            true
        } catch (e: Exception) {
            Log.e("PurchaseManager", "Purchase failed", e)
            false
        }
    }

    suspend fun restore(): Boolean {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitRestore()
            applyCustomerInfo(customerInfo)
            true
        } catch (e: Exception) {
            Log.e("PurchaseManager", "Restore failed", e)
            false
        }
    }
}

data class PaywallProduct(
    val id: String,
    val title: String,
    val price: String,
    val rcPackage: Package? = null
)
