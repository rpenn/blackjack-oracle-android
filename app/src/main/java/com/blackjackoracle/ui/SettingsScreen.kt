package com.blackjackoracle.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackjackoracle.BuildConfig
import com.blackjackoracle.data.CaptionPreferences
import com.blackjackoracle.data.OnboardingPreferences
import com.blackjackoracle.service.ReviewPrompter
import com.blackjackoracle.ui.theme.BjColors
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = "support@angelfirelabs.com"

/**
 * Settings / About — the home for account, subscription, accessibility, legal,
 * and support, reachable from the Setup screen's gear. Ports iOS SettingsView.
 * Play Store policy (like App Review) expects Restore + the legal links
 * reachable from normal navigation, not only the paywall.
 *
 * The Accessibility switches write the same DataStore keys as the in-game CC
 * chip (via [CaptionPreferences]), so the two toggle points stay in sync.
 */
@Composable
fun SettingsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entitlements = LocalEntitlements.current
    val purchases = LocalPurchases.current
    val paywall = LocalPaywall.current
    val isPremium by entitlements.isPremium.collectAsState()

    var showPrivacy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }

    // Caption prefs read straight from DataStore (the process-wide singleton the
    // in-game CC chip also writes), so both toggle points stay in lockstep.
    val captionPrefs = remember { CaptionPreferences(context) }
    val onboardingPrefs = remember { OnboardingPreferences(context) }
    val showCaptions by captionPrefs.showCaptions.collectAsState(initial = false)
    val captionOnly by captionPrefs.captionOnly.collectAsState(initial = false)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackgroundGradientBox {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                ) {
                    Text(
                        "Settings",
                        color = BjColors.Accent,
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black),
                    )
                    Box(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = BjColors.Neutral.copy(alpha = 0.6f),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Premium
                    SettingsCard(
                        footer = "Premium unlocks live Win Chance and Ask Oliver.",
                    ) {
                        if (isPremium) {
                            SettingsRow(
                                icon = Icons.Filled.WorkspacePremium,
                                title = if (entitlements.entitled) "Premium Active" else "Trial Active",
                                onClick = null,
                                trailing = {
                                    Icon(
                                        Icons.Filled.Verified,
                                        contentDescription = null,
                                        tint = BjColors.Success,
                                    )
                                },
                            )
                        } else {
                            SettingsRow(
                                icon = Icons.Filled.WorkspacePremium,
                                title = "Go Premium",
                                tint = BjColors.Accent,
                                onClick = {
                                    onDismiss()
                                    paywall.present("settings")
                                },
                            )
                        }
                    }

                    // Accessibility
                    SettingsCard(
                        header = "Accessibility",
                        footer = "Captions show Oliver's advice as text while he speaks. " +
                            "Caption-Only Mode keeps the text and mutes his voice. Captions " +
                            "turn on automatically when captions are enabled in your device's " +
                            "accessibility settings.",
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.ClosedCaption,
                            title = "Oliver's Captions",
                            checked = showCaptions,
                            onCheckedChange = { scope.launch { captionPrefs.setShowCaptions(it) } },
                        )
                        SettingsSwitchRow(
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            title = "Caption-Only Mode",
                            checked = captionOnly,
                            // Caption-Only is meaningless without captions on.
                            enabled = showCaptions,
                            onCheckedChange = { scope.launch { captionPrefs.setCaptionOnly(it) } },
                        )
                    }

                    // Subscription
                    SettingsCard(header = "Subscription") {
                        SettingsRow(
                            icon = Icons.Filled.Refresh,
                            title = "Restore Purchases",
                            enabled = !isRestoring,
                            trailing = if (isRestoring) {
                                {
                                    CircularProgressIndicator(
                                        color = BjColors.Accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                if (isRestoring) return@SettingsRow
                                isRestoring = true
                                scope.launch {
                                    val message = try {
                                        if (purchases.restore()) {
                                            "Your subscription has been restored."
                                        } else {
                                            "No active subscription found to restore."
                                        }
                                    } catch (e: Exception) {
                                        "Couldn't restore purchases. Please try again."
                                    }
                                    isRestoring = false
                                    restoreMessage = message
                                }
                            },
                        )
                        if (entitlements.entitled) {
                            SettingsRow(
                                icon = Icons.Filled.CreditCard,
                                title = "Manage Subscription",
                                // Google Play manages subscriptions, not the App Store;
                                // deep-links to the exact active plan when known.
                                onClick = { openUrl(context, purchases.manageSubscriptionUrl()) },
                            )
                        }
                    }

                    // Legal
                    SettingsCard(header = "Legal") {
                        SettingsRow(
                            icon = Icons.Filled.Shield,
                            title = "Privacy Policy",
                            onClick = { showPrivacy = true },
                        )
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.Article,
                            title = "Terms (EULA)",
                            onClick = { showTerms = true },
                        )
                    }

                    // Support
                    SettingsCard(header = "Support") {
                        // Clears the onboarding flag and closes Settings —
                        // AppRoot reacts by showing the welcome screen again,
                        // where the guided hand can be redealt. Mirrors iOS
                        // SettingsView.replayTutorial(). Dismiss only after
                        // the write lands: onDismiss removes this composition
                        // and cancels `scope`, so a write launched alongside
                        // it would race its own cancellation.
                        SettingsRow(
                            icon = Icons.Filled.School,
                            title = "Replay Tutorial",
                            onClick = {
                                scope.launch {
                                    onboardingPrefs.setCompleted(false)
                                    onDismiss()
                                }
                            },
                        )
                        SettingsRow(
                            icon = Icons.Filled.MailOutline,
                            title = "Contact Support",
                            onClick = { contactSupport(context) },
                        )
                        // Play policy: buttons must deep-link to the listing,
                        // never trigger the in-app review flow.
                        SettingsRow(
                            icon = Icons.Filled.Star,
                            title = "Rate Blackjack Oracle",
                            onClick = { ReviewPrompter.openStoreListing(context) },
                        )
                    }

                    // About
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Version",
                                color = Color.White.copy(alpha = 0.7f),
                                style = TextStyle(fontSize = 14.sp),
                            )
                            Text(
                                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                color = Color.White.copy(alpha = 0.5f),
                                style = TextStyle(fontSize = 14.sp),
                            )
                        }
                        Text(
                            "Angelfire Labs",
                            color = Color.White.copy(alpha = 0.4f),
                            style = TextStyle(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showPrivacy) {
        LegalDocumentDialog(
            title = "Privacy Policy",
            assetFile = "privacy-policy.html",
            onDismiss = { showPrivacy = false },
        )
    }
    if (showTerms) {
        LegalDocumentDialog(
            title = "Terms (EULA)",
            assetFile = "eula.html",
            onDismiss = { showTerms = false },
        )
    }
    restoreMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreMessage = null },
            confirmButton = {
                TextButton(onClick = { restoreMessage = null }) {
                    Text("OK", color = BjColors.Accent)
                }
            },
            title = { Text("Restore Purchases") },
            text = { Text(message) },
            containerColor = Color(0xFF101D28),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
        )
    }
}

/** A glass card with an optional accent header and dim footer note. */
@Composable
private fun SettingsCard(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                color = BjColors.Accent,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(vertical = 6.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
        if (footer != null) {
            Text(
                footer,
                color = Color.White.copy(alpha = 0.45f),
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            title,
            color = tint,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** A settings row whose trailing control is a Material 3 Switch. */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            title,
            color = Color.White,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = BjColors.Accent,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

/**
 * Renders a bundled HTML legal document (Privacy Policy / EULA) in a WebView, so
 * the documents are available offline and don't depend on a live website. The
 * HTML ships in app/src/main/assets. Android analog of iOS's WKWebView view.
 */
@Composable
private fun LegalDocumentDialog(title: String, assetFile: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackgroundGradientBox {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        title,
                        color = BjColors.Accent,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = BjColors.Neutral.copy(alpha = 0.6f),
                        )
                    }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            loadUrl("file:///android_asset/$assetFile")
                        }
                    },
                )
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun contactSupport(context: Context) {
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
    val subject = "Blackjack Oracle Support"
    val body = "\n\n\n———\nApp: Blackjack Oracle $version\nDevice: $device"
    val uri = Uri.parse(
        "mailto:$SUPPORT_EMAIL" +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body),
    )
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    } catch (_: ActivityNotFoundException) {
    }
}
