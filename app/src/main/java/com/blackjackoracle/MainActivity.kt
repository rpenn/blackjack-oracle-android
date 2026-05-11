package com.blackjackoracle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.blackjackoracle.ui.AppRoot
import com.blackjackoracle.ui.theme.BlackjackOracleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlackjackOracleTheme {
                AppRoot()
            }
        }
    }
}
