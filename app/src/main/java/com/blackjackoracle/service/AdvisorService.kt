package com.blackjackoracle.service

import com.blackjackoracle.BuildConfig
import com.blackjackoracle.engine.BasicStrategy
import com.blackjackoracle.engine.HandEvaluator
import com.blackjackoracle.engine.StrategyMove
import com.blackjackoracle.model.Card
import com.blackjackoracle.model.GamePhase
import com.blackjackoracle.model.GameState
import com.blackjackoracle.model.HandActionLog
import com.blackjackoracle.model.PlayerAction
import com.blackjackoracle.model.RoundResult
import com.blackjackoracle.model.WinChance
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/// One-shot snapshot of the live blackjack state the advisor reasons about, built
/// directly from the displayed [GameState]. The cards here are the same [Card]
/// values the UI renders — cards shown == cards sent. The prompt is assembled
/// server-side from the structured `state` payload this produces.
data class AdvisorContext(
    val phase: GamePhase,
    val playerCards: List<Card>,
    val dealerCards: List<Card>,
    val dealerHoleRevealed: Boolean,
    val handTotal: String,
    val bet: Int,
    val winChance: WinChance?,
    val available: Set<PlayerAction>,
    val canInsure: Boolean,
    val actionHistory: List<HandActionLog>,
    val roundResults: List<RoundResult>,
) {
    companion object {
        fun from(state: GameState, available: Set<PlayerAction>): AdvisorContext {
            val hand = state.human.activeHand
            val cards = hand?.cards.orEmpty()
            // handTotal is a client engine VALUE the user already sees on the UI.
            val total = if (cards.isEmpty()) "—" else HandEvaluator.evaluate(cards).displayString()
            return AdvisorContext(
                phase = state.phase,
                playerCards = cards,
                dealerCards = state.dealerCards,
                dealerHoleRevealed = state.dealerHoleRevealed,
                handTotal = total,
                bet = hand?.bet ?: state.human.pendingBet,
                winChance = state.winChance,
                available = available,
                canInsure = state.phase == GamePhase.INSURANCE,
                actionHistory = state.actionHistory,
                roundResults = state.roundResults,
            )
        }
    }
}

class AdvisorService(
    private val client: OkHttpClient = defaultClient,
    private val baseUrl: String = BuildConfig.ADVISOR_BASE_URL,
) {
    /**
     * Sends a blackjack advisor request. The structured snapshot is serialized to a
     * `state` payload; the server renders all prose and assembles the prompt.
     */
    fun advice(context: AdvisorContext, authToken: String? = null): String {
        val body = JSONObject().put("state", makeState(context))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url("$baseUrl/api/blackjack/android/advisor")
            .post(body)
        if (authToken != null) builder.header("Authorization", "Bearer $authToken")
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Advisor failed: ${response.code}")
            val source = (response.body ?: throw IOException("Empty advisor response")).source()
            source.request(MAX_RESPONSE_BYTES.toLong())
            val raw = source.readUtf8(minOf(source.buffer.size, MAX_RESPONSE_BYTES.toLong()))
            return JSONObject(raw).getString("text")
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 1_000_000
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        /** Cache key for replaying an identical advice/recap without a network call. */
        fun cacheKey(state: GameState): String = buildString {
            append(state.phase.name); append('|')
            state.human.activeHand?.cards?.joinToString(",") { it.id }?.let(::append); append('|')
            append(state.dealerCards.joinToString(",") { it.id }); append('|')
            if (state.phase == GamePhase.ROUND_END) {
                append(state.roundResults.joinToString(",") { "${it.playerName}:${it.outcomeLabel}:${it.net}" })
            }
        }

        /**
         * Serializes the snapshot into the structured `state` the server renders from.
         * Cards travel as `{rank, suit}` primitives. The engine OUTPUTS the player
         * already sees travel as VALUES: win chances, the basic-strategy move (computed
         * here, not on the server), the hand-total string, and — at round end — the
         * dealer's final total. The server never recomputes strategy, equity, or totals.
         * Internal (not private) so tests can verify fidelity.
         */
        fun makeState(c: AdvisorContext): JSONObject {
            val winChances = JSONObject()
            c.winChance?.let { wc ->
                winChances.put("ifHit", wc.ifHit)
                winChances.put("ifStand", wc.ifStand)
                winChances.put("ifDouble", wc.ifDouble)
                if (wc.ifSplitHand != null) winChances.put("ifSplit", wc.ifSplitHand)
            }

            val state = JSONObject()
                .put("phase", phaseWire(c.phase))
                .put("playerCards", cardsJson(c.playerCards))
                .put("dealerCards", cardsJson(c.dealerCards))
                .put("dealerHoleRevealed", c.dealerHoleRevealed)
                .put("handTotal", c.handTotal)
                .put("bet", c.bet)
                .put("canHit", PlayerAction.Hit in c.available)
                .put("canStand", PlayerAction.Stand in c.available)
                .put("canDouble", PlayerAction.Double in c.available)
                .put("canSplit", PlayerAction.Split in c.available)
                .put("canSurrender", PlayerAction.Surrender in c.available)
                .put("canInsure", c.canInsure)
                .put("winChances", winChances)
                .put("actionHistory", JSONArray().apply { c.actionHistory.forEach { put(actionLogJson(it)) } })
                .put("roundResults", JSONArray().apply {
                    c.roundResults.forEach {
                        put(JSONObject()
                            .put("playerName", it.playerName)
                            .put("handTotal", it.handTotal)
                            .put("outcomeLabel", it.outcomeLabel)
                            .put("net", it.net))
                    }
                })

            // Engine ground-truth move (a VALUE) for active decisions. Insurance has its
            // own server-side branch; round-end uses the per-decision recommendations.
            if (c.phase != GamePhase.INSURANCE && c.phase != GamePhase.ROUND_END &&
                c.playerCards.isNotEmpty() && c.dealerCards.isNotEmpty()) {
                val move = BasicStrategy.recommend(
                    playerCards = c.playerCards,
                    dealerUpCard = c.dealerCards.first(),
                    canDouble = PlayerAction.Double in c.available,
                    canSplit = PlayerAction.Split in c.available,
                    canSurrender = false,
                )
                state.put("basicStrategyMove", moveWire(move))
            }

            // Dealer's final total at round end — a client engine value, never re-derived.
            if (c.phase == GamePhase.ROUND_END && c.dealerCards.isNotEmpty()) {
                state.put("dealerTotal", HandEvaluator.evaluate(c.dealerCards).displayString())
            }

            return state
        }

        private fun phaseWire(phase: GamePhase): String = when (phase) {
            GamePhase.INSURANCE -> "insurance"
            GamePhase.ROUND_END -> "roundEnd"
            else -> "playerTurns"
        }

        private fun moveWire(m: StrategyMove): String = when (m) {
            StrategyMove.HIT -> "hit"
            StrategyMove.STAND -> "stand"
            StrategyMove.DOUBLE -> "double"
            StrategyMove.SPLIT -> "split"
            StrategyMove.SURRENDER -> "surrender"
        }

        private fun cardJson(card: Card): JSONObject =
            JSONObject().put("rank", card.rank).put("suit", card.suit.name.lowercase())

        private fun cardsJson(cards: List<Card>): JSONArray =
            JSONArray().apply { cards.forEach { put(cardJson(it)) } }

        private fun actionLogJson(e: HandActionLog): JSONObject {
            val entry = JSONObject()
                .put("playerName", e.playerName)
                .put("action", e.action)
                .put("handTotal", e.handTotal)
                .put("cardsBefore", cardsJson(e.cardsBefore))
                .put("totalBefore", e.totalBefore)
            e.dealerUp?.let { entry.put("dealerUp", cardJson(it)) }
            e.recommended?.let { entry.put("recommended", it) }
            return entry
        }
    }
}
