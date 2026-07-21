package com.blackjackoracle

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blackjackoracle.data.CaptionPreferences
import com.blackjackoracle.data.OnboardingPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/// Plays the guided first hand end-to-end: welcome screen → every coach step →
/// scripted dealer bust → closing card → back on the setup screen. This is the
/// only automated coverage of the tutorial's step machine against real game
/// timing (deal animation, dealer draw delays, settlement pauses), so it
/// exercises the whole rigged shoe. Android port of iOS TutorialFlowUITests.
///
/// Onboarding/caption state is reset through the SAME DataStore the app reads
/// (not a launch-argument override, which on iOS masked the persisted value
/// and made the final routing assertion untestable in-process) — so the
/// closing "lands on setup" assertion exercises the real persisted routing.
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TutorialFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun guidedTutorialHandCompletes() {
        val context = ApplicationProvider.getApplicationContext<BlackjackApp>()
        // Fresh-install onboarding state, with narration muted (caption-only)
        // so the test isn't at the mercy of emulator audio.
        runBlocking {
            OnboardingPreferences(context).setCompleted(false)
            CaptionPreferences(context).setShowCaptions(true)
            CaptionPreferences(context).setCaptionOnly(true)
        }
        ActivityScenario.launch(MainActivity::class.java)

        // Welcome screen → deal the practice hand
        compose.waitUntilAtLeastOneExists(hasText("Deal Me a Practice Hand"), 10_000)
        compose.onNodeWithText("Deal Me a Practice Hand").performClick()

        // Step: Win Chance bars at 18 v 6 (deal animation must finish first)
        waitForTextPrefix("Every bar is a live win probability", 15_000)
        tapCenter()

        // Step: ask Oliver about the 18
        compose.waitUntilAtLeastOneExists(hasTestTag("tutorial.askOliver"), 5_000)
        compose.onNodeWithTag("tutorial.askOliver").performClick()

        // Step: Oliver's split call is showing — tap through
        waitForTextPrefix("One 18 wins 61%", 5_000)
        tapCenter()

        // Step: split the nines
        tapWhenEnabled("SPLIT")

        // Step: stand the 19
        waitForTextPrefix("Stand just jumped to 73%", 5_000)
        tapWhenEnabled("STAND")

        // Step: ask Oliver about the 11
        compose.waitUntilAtLeastOneExists(hasTestTag("tutorial.askOliver"), 5_000)
        compose.onNodeWithTag("tutorial.askOliver").performClick()

        // Step: Oliver's double call — tap through
        waitForTextPrefix("Doubling puts a second bet", 5_000)
        tapCenter()

        // Step: double the 11
        tapWhenEnabled("DOUBLE")

        // Closing card after the dealer busts
        compose.waitUntilAtLeastOneExists(hasText("Play for Real"), 20_000)
        compose.onNodeWithText("That's Blackjack Oracle!").assertExists()
        compose.onNodeWithText("Both hands won — $30 ahead.").assertExists()
        compose.onNodeWithText("Play for Real").performClick()

        // Ending the tutorial persists onboarding-complete, so the SETUP phase
        // now routes to the real setup screen, not the welcome fork.
        compose.waitUntilAtLeastOneExists(hasText("TAKE A SEAT"), 10_000)
    }

    @Test
    fun skipFromWelcomeLandsOnSetup() {
        val context = ApplicationProvider.getApplicationContext<BlackjackApp>()
        runBlocking { OnboardingPreferences(context).setCompleted(false) }
        ActivityScenario.launch(MainActivity::class.java)

        compose.waitUntilAtLeastOneExists(hasText("I know Blackjack — skip"), 10_000)
        compose.onNodeWithText("I know Blackjack — skip").performClick()

        // The skip writes the persisted flag; AppRoot re-routes reactively.
        compose.waitUntilAtLeastOneExists(hasText("TAKE A SEAT"), 10_000)
    }

    // Helpers

    private fun waitForTextPrefix(prefix: String, timeoutMs: Long) {
        compose.waitUntilAtLeastOneExists(
            hasText(prefix, substring = true), timeoutMs,
        )
    }

    /// Taps the middle of the screen — the blocking coach overlays advance on
    /// a tap anywhere.
    private fun tapCenter() {
        compose.onRoot().performTouchInput { click(center) }
    }

    private fun tapWhenEnabled(label: String, timeoutMs: Long = 5_000) {
        compose.waitUntilAtLeastOneExists(
            SemanticsMatcher("$label enabled") { node ->
                hasText(label).matches(node) && isEnabled().matches(node)
            },
            timeoutMs,
        )
        compose.onNodeWithText(label).performClick()
    }
}
