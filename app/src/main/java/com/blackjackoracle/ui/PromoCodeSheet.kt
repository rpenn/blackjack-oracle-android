package com.blackjackoracle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackjackoracle.service.billing.PromoCodeService
import com.blackjackoracle.service.billing.RedeemResult
import com.blackjackoracle.ui.components.GoldButton
import com.blackjackoracle.ui.theme.BjColors
import kotlinx.coroutines.launch

/// Promo-code redemption dialog, opened from the paywall's "Have a promo code?"
/// link. On success applies the trial JWT → isPremium flips → both this sheet and
/// the paywall dismiss.
@Composable
fun PromoCodeSheet(onDismiss: () -> Unit) {
    val entitlements = LocalEntitlements.current
    val purchases = LocalPurchases.current
    val paywall = LocalPaywall.current
    val scope = rememberCoroutineScope()
    val service = remember { PromoCodeService() }

    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101D28))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Redeem a Promo Code",
                color = BjColors.Accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                singleLine = true,
                enabled = !busy,
                label = { Text("Code") },
                modifier = Modifier.fillMaxWidth(),
            )
            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = BjColors.Danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            if (busy) {
                CircularProgressIndicator(color = BjColors.Accent)
            } else {
                GoldButton("Redeem", Modifier.fillMaxWidth()) {
                    val trimmed = code.trim()
                    if (trimmed.isEmpty()) {
                        status = "Please enter a code."
                        return@GoldButton
                    }
                    scope.launch {
                        busy = true
                        status = null
                        when (val result = service.redeem(trimmed, purchases.appUserID)) {
                            is RedeemResult.Success -> {
                                if (entitlements.applyTrial(result.token)) {
                                    onDismiss()
                                    paywall.dismiss()
                                } else {
                                    status = "Couldn't activate the trial."
                                }
                            }
                            is RedeemResult.Failure -> status = result.message
                        }
                        busy = false
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { if (!busy) onDismiss() }) {
                    Text("Close", color = BjColors.Neutral)
                }
            }
        }
    }
}
