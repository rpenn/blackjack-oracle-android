package com.blackjackoracle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.LocalEntitlements
import com.blackjackoracle.LocalPurchases
import com.blackjackoracle.service.billing.PromoCodeService
import com.blackjackoracle.service.billing.RedemptionResult
import com.blackjackoracle.ui.theme.BjColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromoCodeSheet(onDismiss: () -> Unit) {
    val entitlements = LocalEntitlements.current
    val purchases = LocalPurchases.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val service = remember { PromoCodeService() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BjColors.BgTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Redeem Promo Code", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BjColors.Accent)
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BjColors.Accent,
                    focusedLabelColor = BjColors.Accent
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    color = if (isError) BjColors.Danger else BjColors.Success,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (code.trim().isBlank()) {
                        isError = true
                        statusMessage = "Please enter a code"
                        return@Button
                    }
                    isLoading = true
                    statusMessage = null
                    scope.launch {
                        val result = service.redeem(code.trim(), purchases.appUserID)
                        isLoading = false
                        when (result) {
                            is RedemptionResult.Success -> {
                                entitlements.applyTrial(result.token)
                                isError = false
                                statusMessage = "Success! Premium unlocked."
                            }
                            is RedemptionResult.Error -> {
                                isError = true
                                statusMessage = result.message
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = BjColors.Accent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    Text("REDEEM", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
