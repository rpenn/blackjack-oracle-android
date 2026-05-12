package com.blackjackoracle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.blackjackoracle.ui.AppRoot
import com.blackjackoracle.ui.theme.BlackjackOracleTheme
import com.blackjackoracle.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackjackOracleTheme {
                AppRoot(vm)
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
