package com.blackjackoracle.tutorial

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.Suit

/// One canned Oliver line: the display text plus the bundled MP3 rendered
/// from it in the production TTS voice (en-US-Neural2-D — the same voice the
/// advisor uses live). Regenerate with scripts/gen-tutorial-audio.js whenever
/// a line's text changes; the audio must stay in sync with the text.
data class TutorialLine(
    val text: String,
    /// res/raw resource name of the pre-rendered MP3 (no extension). Android
    /// resource names cannot contain hyphens, so these use underscores where
    /// the iOS bundle names use dashes.
    val audioResource: String,
)

/// The fixed content of the guided first hand: a rigged shoe and Oliver's
/// canned narration.
///
/// This is a product tour, not a rules lesson: one deliberately
/// counterintuitive hand (9-9 against a dealer 6) where Oliver breaks up a
/// made 18 — the play recreational players never make — and the engine's own
/// numbers prove him right at every step. Measured against
/// WinChanceCalculator: standing on 18 v 6 is 61% equity; each split hand is
/// 59% with a bet on each (EV +0.375 bets versus +0.223 standing); the first
/// split hand lands 19 (73%), the second lands 11, the premier double (67%),
/// and the dealer's 16 busts. TutorialScriptedHandTest pins all of this.
///
/// The lines live locally (not fetched from the advisor API) so onboarding
/// works offline and costs nothing per install. Their audio is pre-rendered
/// through the real TTS voice and bundled, because the backend TTS endpoint
/// requires a premium token the new user doesn't have yet.
object TutorialScript {

    /// Fixed wager for the practice hand: enough chips remain from the $100
    /// stack to cover the split and the double ($30 total in play).
    const val BET = 10

    /// Cards in the exact order the engine consumes them from `Shoe.deal()`:
    /// the initial deal alternates player, dealer-up, player, dealer-hole
    /// (`beginHand` + the ViewModel's deal loop); a split immediately draws
    /// hand 1's second card then hand 2's (`handleAction(Split)`); the double
    /// takes one card; then the dealer draws. Force-fed via `Shoe.forceNext`.
    val riggedCards: List<Card> = listOf(
        Card(Suit.CLUBS, 9),       // you: 9♣
        Card(Suit.HEARTS, 6),      // dealer up: 6♥
        Card(Suit.DIAMONDS, 9),    // you: 9♦ → 18
        Card(Suit.SPADES, 10),     // dealer hole: 10♠ → 16
        Card(Suit.HEARTS, 10),     // split, hand 1: 10♥ → 19
        Card(Suit.SPADES, 2),      // split, hand 2: 2♠ → 11
        Card(Suit.HEARTS, 13),     // double card: K♥ → 21
        Card(Suit.SPADES, 9),      // dealer draws: 9♠ → 25, bust
    )

    // Oliver's lines

    val lineDeal = TutorialLine(
        text = "Eighteen against a six. Feels safe. It isn't. Ask me, Oliver, your AI Coach!",
        audioResource = "tutorial_deal",
    )
    val lineSplit = TutorialLine(
        text = "Split them. A six is one of the weakest cards a dealer can show, and two hands built on a nine beat one static eighteen.",
        audioResource = "tutorial_split",
    )
    val lineStand = TutorialLine(
        text = "Nineteen. The split already paid for itself — this hand alone beats the eighteen you started with. Stand.",
        audioResource = "tutorial_stand",
    )
    val lineDouble = TutorialLine(
        text = "Eleven against a six — one of the best doubles in the game. This is exactly the spot splitting those nines bought you.",
        audioResource = "tutorial_double",
    )
    val lineClosing = TutorialLine(
        text = "Most players never break an eighteen. That's why they never find hands like this one.",
        audioResource = "tutorial_closing",
    )

    val allLines: List<TutorialLine>
        get() = listOf(lineDeal, lineSplit, lineStand, lineDouble, lineClosing)
}
