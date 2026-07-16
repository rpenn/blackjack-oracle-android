package com.blackjackoracle.demo

import com.blackjackoracle.service.billing.EntitlementStore
import com.blackjackoracle.viewmodel.GameViewModel

/**
 * Release twin of the debug-only demo driver. The store-capture autopilot exists
 * solely in the debug source set; in release this is a no-op, guaranteeing the
 * stacked shoe, scripted actions, canned advisor text, and the `demo_mode`
 * behaviour are entirely absent from the shipped APK/AAB.
 */
object DemoEntry {
    fun start(vm: GameViewModel, entitlements: EntitlementStore, scene: String?) {
        // Intentionally empty — no demo behaviour in release builds.
    }
}
