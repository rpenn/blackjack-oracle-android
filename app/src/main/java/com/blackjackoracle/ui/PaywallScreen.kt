package com.blackjackoracle.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.service.billing.PaywallProduct
import com.blackjackoracle.ui.components.GoldButton
import com.blackjackoracle.ui.theme.BjColors
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://www.angelfirelabs.com/privacy-policy"
private const val EULA_URL = "https://www.angelfirelabs.com/eula"

private enum class PaywallLoad { Loading, Ready, Failed }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/// Full-screen paywall overlay. Shown by AppRoot when PaywallController.isPresented.
@Composable
fun PaywallScreen(onPromoCode: () -> Unit) {
    val entitlements = LocalEntitlements.current
    val purchases = LocalPurchases.current
    val paywall = LocalPaywall.current
    val isPremium by entitlements.isPremium.collectAsState()
    val products by purchases.products.collectAsState()
    val activity = LocalContext.current.findActivity()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var selectedId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var loadState by remember { mutableStateOf(PaywallLoad.Loading) }
    var retryTick by remember { mutableIntStateOf(0) }

    // Default the selection to the first product (yearly) once they load.
    LaunchedEffect(products) {
        if (selectedId == null) selectedId = products.firstOrNull()?.id
    }
    // Belt-and-suspenders: dismiss the moment premium turns true (purchase,
    // restore, renewal, or a promo trial applied from the sheet).
    LaunchedEffect(isPremium) {
        if (isPremium) paywall.dismiss()
    }
    // Refresh offerings when the paywall opens and on each Retry. Tracks loading
    // vs failed so the user gets a retry affordance instead of a dead spinner
    // (e.g. transient Play outage, offline, or a sideloaded build Play won't
    // serve products to).
    LaunchedEffect(retryTick) {
        loadState = PaywallLoad.Loading
        val ok = purchases.refreshProducts()
        loadState = if (ok) PaywallLoad.Ready else PaywallLoad.Failed
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BjColors.BgTop, BjColors.BgBottom)))
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Leave room for the close button, which is drawn on top (declared
            // after this Column so the scroll container can't swallow its taps).
            Spacer(Modifier.height(48.dp))
            Text(
                "Blackjack Oracle Premium",
                color = BjColors.Accent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            ValueProp("Live Win Chance", "Exact hit / stand / double / split odds on every hand")
            Spacer(Modifier.height(10.dp))
            ValueProp("Ask Oliver", "Your AI advisor — spoken, in-game guidance and round recaps")
            Spacer(Modifier.height(26.dp))

            when {
                products.isNotEmpty() -> {
                    products.forEach { product ->
                        ProductRow(
                            product = product,
                            selected = product.id == selectedId,
                            onSelect = { selectedId = product.id },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                loadState == PaywallLoad.Loading -> {
                    CircularProgressIndicator(color = BjColors.Accent)
                }
                else -> {
                    Text(
                        "Couldn't load subscription options. Check your connection and try again.",
                        color = BjColors.Neutral.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    GoldButton("Try Again", Modifier.fillMaxWidth()) { retryTick++ }
                }
            }

            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = BjColors.Danger, fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            // Continue only makes sense with a product to buy; hidden while
            // loading / on failure (Restore + promo + legal stay available below).
            if (products.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                if (busy) {
                    CircularProgressIndicator(color = BjColors.Accent)
                } else {
                    GoldButton("Continue", Modifier.fillMaxWidth()) {
                        val product = products.firstOrNull { it.id == selectedId }
                        if (product == null || activity == null) {
                            status = "Purchase unavailable right now."
                            return@GoldButton
                        }
                        scope.launch {
                            busy = true
                            status = null
                            val ok = runCatching { purchases.purchase(activity, product) }.getOrDefault(false)
                            busy = false
                            if (ok) paywall.dismiss() else status = "Purchase didn't complete."
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Restore Purchases",
                color = BjColors.Neutral,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !busy) {
                    scope.launch {
                        busy = true
                        status = null
                        val ok = runCatching { purchases.restore() }.getOrDefault(false)
                        busy = false
                        if (ok) paywall.dismiss() else status = "No purchases to restore."
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Have a promo code?",
                color = BjColors.Neutral.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onPromoCode() },
            )

            Spacer(Modifier.height(22.dp))
            Text(
                "Subscriptions renew automatically until cancelled. Cancel anytime in " +
                    "Google Play. Payment is charged to your Play account.",
                color = BjColors.Neutral.copy(alpha = 0.55f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    "Privacy Policy",
                    color = BjColors.Neutral.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uriHandler.openUri(PRIVACY_URL) },
                )
                Text(
                    "Terms (EULA)",
                    color = BjColors.Neutral.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uriHandler.openUri(EULA_URL) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // Declared last so it sits above the scrollable Column and reliably
        // receives taps (the Column's verticalScroll otherwise intercepts them).
        IconButton(
            onClick = { paywall.dismiss() },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = BjColors.Neutral)
        }
    }
}

@Composable
private fun ValueProp(title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = BjColors.Success,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = BjColors.Neutral, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(body, color = BjColors.Neutral.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProductRow(
    product: PaywallProduct,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = if (selected) BjColors.Accent else BjColors.Neutral.copy(alpha = 0.25f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = if (selected) 0.35f else 0.18f))
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                product.title,
                color = BjColors.Neutral,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (product.isAnnual) {
                Text("Best value", color = BjColors.Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            product.priceFormatted,
            color = BjColors.Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
