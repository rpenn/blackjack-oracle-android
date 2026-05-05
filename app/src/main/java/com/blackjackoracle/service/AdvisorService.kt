package com.blackjackoracle.service

import com.blackjackoracle.model.Card
import com.blackjackoracle.model.HandActionLog
import com.blackjackoracle.model.RoundResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Snapshot of the game state used to generate an Oliver hint or hand recap.
 *
 * For active hands: include hole cards, current hand total, dealer up-card,
 * win-chance numbers, available actions.
 * For round-end recap: include final dealer hand and per-hand outcomes.
 */
data class AdvisorContext(
    val playerCards: List<Card>?,         // current active hand for the human
    val dealerCards: List<Card>,          // up-card (and hole card after reveal)
    val dealerHoleRevealed: Boolean,
    val handTotal: String,
    val ifHitPct: Double?,
    val ifStandPct: Double?,
    val bet: Int,
    val phase: String,
    val canHit: Boolean,
    val canStand: Boolean,
    val canDouble: Boolean,
    val canSplit: Boolean,
    val canSurrender: Boolean,
    val canInsure: Boolean,
    val actionHistory: List<HandActionLog>,
    val roundResults: List<RoundResult>
)

object AdvisorService {
    private val JSON = "application/json".toMediaType()
    private const val MAX_RESPONSE_BYTES = 1_000_000L

    /**
     * Sends a blackjack advisor request to the remote LLM. The body includes a
     * `game: "blackjack"` discriminator so the same Vercel function that
     * services Poker Oracle can branch its prompt template by game.
     *
     * Both the discriminator and the `prompt` text are sent — until the
     * server is updated, it will use the legacy `prompt` field (which we
     * write in blackjack-style).
     */
    suspend fun getAdvice(ctx: AdvisorContext): String = withContext(Dispatchers.IO) {
        val prompt = if (ctx.phase == "roundEnd") buildSummaryPrompt(ctx) else buildAdvicePrompt(ctx)
        val body = JSONObject()
            .put("game", "blackjack")
            .put("prompt", prompt)
            .toString()
        val req = Request.Builder()
            .url("${HttpClient.BASE_URL}/api/advisor")
            .header("Content-Type", "application/json")
            .header("Cache-Control", "no-cache")
            .post(body.toRequestBody(JSON))
            .build()

        HttpClient.client.newCall(req).awaitResponse().use { resp ->
            if (!resp.isSuccessful) error("Server error ${resp.code}")
            val rspBody = resp.body ?: error("Empty body")
            val cl = rspBody.contentLength()
            if (cl > MAX_RESPONSE_BYTES) error("Response too large ($cl)")
            val source = rspBody.source()
            source.request(MAX_RESPONSE_BYTES + 1)
            if (source.buffer.size > MAX_RESPONSE_BYTES) error("Response too large")
            val text = JSONObject(source.readUtf8()).optString("text")
            if (text.isBlank()) error("Missing text in response")
            text
        }
    }

    private fun buildAdvicePrompt(c: AdvisorContext): String {
        val hand = c.playerCards?.joinToString(", ") { it.spokenString } ?: "none"
        val dealerUp = c.dealerCards.firstOrNull()?.spokenString ?: "unknown"
        val ifHit = c.ifHitPct?.let { "${it.roundToInt()}%" } ?: "unknown"
        val ifStand = c.ifStandPct?.let { "${it.roundToInt()}%" } ?: "unknown"
        val actions = buildList {
            if (c.canHit) add("hit")
            if (c.canStand) add("stand")
            if (c.canDouble) add("double down")
            if (c.canSplit) add("split")
            if (c.canSurrender) add("surrender")
            if (c.canInsure) add("take insurance")
        }.joinToString(", ")

        val phaseGuide = when (c.phase) {
            "insurance" -> "The dealer is showing an Ace. Decide whether to take insurance — generally a poor bet against the house unless you are counting cards, which the player is not."
            else -> "It is the player's turn to act. Use Vegas basic strategy as your guide and explain why your recommendation is the right play."
        }

        return buildString {
            append("You are a blackjack advisor speaking directly to the player. Your response will be read aloud by a text-to-speech service, so write exactly as you would speak — plain sentences, no bullet points, no numbered lists, no asterisks, no markdown, no special characters, no dollar signs (say \"dollars\" instead), no percent signs (say \"percent\" instead). Just natural, flowing speech.\n\n")
            append("The player holds exactly these cards: $hand. Refer to them by these exact ranks and suits — do not substitute or invent others.\n\n")
            append("Current situation:\n")
            append("Phase: ${c.phase}\n")
            append("Player hand: $hand (total: ${c.handTotal})\n")
            append("Dealer shows: $dealerUp\n")
            append("Bet on this hand: ${c.bet} dollars\n")
            append("Win chance if you hit: $ifHit\n")
            append("Win chance if you stand: $ifStand\n")
            append("Available actions: $actions\n")
            if (c.actionHistory.isNotEmpty()) {
                append("Recent actions: ")
                append(c.actionHistory.takeLast(6).joinToString(", ") { "${it.playerName} ${it.action}" })
                append("\n")
            }
            append("\n$phaseGuide\n\n")
            append("In 2 to 3 spoken sentences, recommend a single action and briefly explain the reasoning. Be direct and conversational, as if talking to a friend at the table.")
        }
    }

    private fun buildSummaryPrompt(c: AdvisorContext): String {
        val finalHand = c.playerCards?.joinToString(", ") { it.spokenString } ?: "unknown"
        val dealerHand = c.dealerCards.joinToString(", ") { it.spokenString }
        val resultLine = if (c.roundResults.isEmpty()) "No outcome recorded."
        else c.roundResults.joinToString("; ") { r ->
            val sign = if (r.net >= 0) "won" else "lost"
            "${r.playerName}'s hand of ${r.handTotal} ${r.outcomeLabel}, $sign ${kotlin.math.abs(r.net)} dollars"
        }
        val historyLine = c.actionHistory.joinToString(", ") { "${it.playerName} ${it.action}" }

        return buildString {
            append("You are a blackjack advisor speaking directly to the player. Your response will be read aloud by a text-to-speech service, so write exactly as you would speak — plain sentences, no bullet points, no numbered lists, no asterisks, no markdown, no special characters, no dollar signs (say \"dollars\" instead), no percent signs (say \"percent\" instead). Just natural, flowing speech.\n\n")
            append("The hand just finished. Here is what happened:\n")
            append("Player's final hand: $finalHand (total: ${c.handTotal})\n")
            append("Dealer's hand: $dealerHand\n")
            append("Outcome: $resultLine\n")
            if (historyLine.isNotEmpty()) append("Player actions: $historyLine\n")
            append("\nIn 2 to 3 spoken sentences, recap the hand. If the player made a notable mistake or a smart play, mention it.")
        }
    }
}
