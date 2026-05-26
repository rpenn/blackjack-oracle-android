package com.blackjackoracle.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.LocalEntitlements
import com.blackjackoracle.LocalPaywall
import com.blackjackoracle.LocalPurchases
import com.blackjackoracle.ui.theme.BjColors
import com.revenuecat.purchases.Package
import com.blackjackoracle.service.billing.PaywallProduct
import kotlinx.coroutines.launch

@Composable
fun PaywallScreen() {
    val context = LocalContext.current
    val entitlements = LocalEntitlements.current
    val paywall = LocalPaywall.current
    val purchases = LocalPurchases.current
    val scope = rememberCoroutineScope()
    
    val products by purchases.products.collectAsState()
    val isPremium by entitlements.isPremium.collectAsState()
    var selectedProduct by remember { mutableStateOf<PaywallProduct?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showPromoSheet by remember { mutableStateOf(false) }

    LaunchedEffect(isPremium) {
        if (isPremium) {
            paywall.dismiss()
        }
    }
    
    LaunchedEffect(products) {
        if (selectedProduct == null && products.isNotEmpty()) {
            selectedProduct = products.first()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .systemBarsPadding()
    ) {
        IconButton(
            onClick = { paywall.dismiss() },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Blackjack Oracle Premium",
                color = BjColors.Accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            ValueProp("Live Win Chance", "See exact odds for Hit, Stand, Split, and Double.")
            Spacer(modifier = Modifier.height(16.dp))
            ValueProp("Ask Oliver", "Get real-time AI advice on every hand.")
            
            Spacer(modifier = Modifier.weight(1f))

            if (products.isEmpty()) {
                CircularProgressIndicator(color = BjColors.Accent)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(products) { pkg ->
                        ProductItem(
                            pkg = pkg,
                            isSelected = selectedProduct == pkg,
                            onClick = { selectedProduct = pkg }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    selectedProduct?.let { pkg ->
                        isLoading = true
                        scope.launch {
                            val success = purchases.purchase(context as Activity, pkg)
                            isLoading = false
                            if (success) paywall.dismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BjColors.Accent),
                enabled = !isLoading && selectedProduct != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    Text("CONTINUE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = {
                isLoading = true
                scope.launch {
                    val success = purchases.restore()
                    isLoading = false
                    if (success) paywall.dismiss()
                }
            }) {
                Text("Restore Purchases", color = BjColors.Neutral)
            }

            Text(
                "Have a promo code?",
                color = BjColors.Neutral,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { showPromoSheet = true }.padding(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Subscriptions auto-renew unless canceled. See terms for details.",
                color = BjColors.Neutral.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Privacy Policy",
                    color = BjColors.Neutral.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.angelfirelabs.com/privacy-policy"))
                        context.startActivity(intent)
                    }.padding(8.dp)
                )
                Text(
                    "EULA",
                    color = BjColors.Neutral.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.angelfirelabs.com/eula"))
                        context.startActivity(intent)
                    }.padding(8.dp)
                )
            }
        }
    }

    if (showPromoSheet) {
        PromoCodeSheet(onDismiss = { showPromoSheet = false })
    }
}

@Composable
fun ValueProp(title: String, desc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = BjColors.Neutral, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProductItem(pkg: PaywallProduct, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) BjColors.Accent else Color.Transparent
    val bgColor = if (isSelected) BjColors.Accent.copy(alpha = 0.1f) else Color.DarkGray
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            pkg.title,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            pkg.price,
            color = BjColors.Accent,
            fontWeight = FontWeight.Bold
        )
    }
}
