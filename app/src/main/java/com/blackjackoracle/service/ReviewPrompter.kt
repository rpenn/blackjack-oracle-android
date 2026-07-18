package com.blackjackoracle.service

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.blackjackoracle.BuildConfig
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Decides when to ask for a Play Store rating. Google's in-app review flow is
 * quota-limited and reports nothing about its outcome — it may silently show
 * nothing at all — so every request must land on a high point: the player just
 * ended a session up chips, and it isn't their very first one. Port of iOS's
 * ReviewPrompter. Unlike iOS there is no write-review composer deep link, so
 * once the in-app flow has been spent for the current version, win moments
 * link to the Play listing instead.
 */
object ReviewPrompter {
    /// The release applicationId. BuildConfig.APPLICATION_ID carries the
    /// ".debug" suffix in debug builds and would point at an unpublished
    /// listing, so the id is hardcoded.
    private const val PLAY_APP_ID = "com.blackjackoracle.app"

    private const val PREFS = "blackjack_settings"
    private const val SESSIONS_COMPLETED_KEY = "reviewSessionsCompleted"
    private const val PROMPTED_VERSION_KEY = "reviewPromptedVersion"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordSessionCompleted(context: Context) {
        val p = prefs(context)
        p.edit().putInt(SESSIONS_COMPLETED_KEY, p.getInt(SESSIONS_COMPLETED_KEY, 0) + 1).apply()
    }

    /// In-app flow gate: second-or-later completed session, at most once per
    /// app version. Callers must also check that the player finished ahead.
    /// A milestone (bankroll crossed $1,000, or 4 wins in a row) is a strong
    /// enough signal to waive the session minimum — but never the version cap.
    fun shouldRequestReview(context: Context, isMilestone: Boolean = false): Boolean {
        val p = prefs(context)
        if (p.getString(PROMPTED_VERSION_KEY, null) == BuildConfig.VERSION_NAME) return false
        return isMilestone || p.getInt(SESSIONS_COMPLETED_KEY, 0) >= 2
    }

    fun markPrompted(context: Context) {
        prefs(context).edit().putString(PROMPTED_VERSION_KEY, BuildConfig.VERSION_NAME).apply()
    }

    /// Once the in-app flow has been spent for this version, later win moments
    /// offer the Play-listing link instead.
    fun shouldOfferWrittenReview(context: Context): Boolean =
        prefs(context).getString(PROMPTED_VERSION_KEY, null) == BuildConfig.VERSION_NAME

    /**
     * Launches Google's in-app review flow. Fire-and-forget: Play decides
     * whether a dialog actually appears (it usually won't outside a Play-track
     * install, and quota may suppress it even there) and the completion signal
     * fires regardless of what the user did — so nothing is branched, rewarded,
     * or confirmed on it.
     */
    suspend fun launchInAppReview(context: Context) {
        val activity = context.findActivity() ?: return
        try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReview()
            manager.launchReviewFlow(activity, reviewInfo)
        } catch (_: Exception) {
            // Quota'd, offline, or Play unavailable — degrade silently.
        }
    }

    /**
     * Opens the app's Play listing — the only route to a written review on
     * Android, and the only thing a "Rate" button may do (Play policy forbids
     * wiring buttons to the in-app flow). market:// targets the Play app;
     * the web URL covers devices without it.
     */
    fun openStoreListing(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_APP_ID"))
            )
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_APP_ID")
                    )
                )
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    /// launchReviewFlow needs the host Activity, but Compose's LocalContext may
    /// wrap it — unwind the ContextWrapper chain to find it.
    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
