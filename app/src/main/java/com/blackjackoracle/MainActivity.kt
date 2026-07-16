package com.blackjackoracle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blackjackoracle.demo.DemoEntry
import com.blackjackoracle.ui.AppRoot
import com.blackjackoracle.ui.LocalEntitlements
import com.blackjackoracle.ui.LocalPaywall
import com.blackjackoracle.ui.LocalPurchases
import com.blackjackoracle.ui.theme.BlackjackOracleTheme
import com.blackjackoracle.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BlackjackApp
        // Store-capture demo: opt-in via intent extras, debug builds only. The
        // BuildConfig.DEBUG gate lets R8 strip this whole branch (including the
        // extra names) from release, where DemoEntry is a no-op twin anyway.
        val demoRequested = BuildConfig.DEBUG &&
            intent?.getBooleanExtra("demo_mode", false) == true
        val demoScene = if (demoRequested) intent?.getStringExtra("demo_scene") else null
        if (demoRequested) {
            // Clean capture: hide the navigation bar / large-screen taskbar (keep
            // the status bar) so nav icons never appear in store screenshots.
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            BlackjackOracleTheme {
                CompositionLocalProvider(
                    LocalEntitlements provides app.entitlements,
                    LocalPurchases provides app.purchases,
                    LocalPaywall provides app.paywall,
                ) {
                    LaunchedEffect(demoRequested) {
                        if (demoRequested) {
                            DemoEntry.start(vm, app.entitlements, demoScene)
                        }
                    }
                    AppRoot(vm, demoActive = demoRequested)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Drop a trial that expired while backgrounded so isPremium recomputes.
        (application as BlackjackApp).entitlements.pruneExpiredTrial()
    }

    override fun onStop() {
        super.onStop()
        // Cut Oliver off when the app leaves the foreground — MediaPlayer would
        // otherwise keep narrating into the background with no UI to control it.
        vm.stopSpeaking()
    }
}
