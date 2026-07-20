package com.blackjackoracle.tutorial

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.ui.LocalTutorial
import com.blackjackoracle.ui.theme.BjColors
import com.blackjackoracle.viewmodel.GameViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Anchors

/// Bounds (in root/window coordinates) of the views the tutorial spotlights.
/// Compose has no anchorPreference: targets publish their rects here via
/// `Modifier.tutorialAnchor`, and the overlay resolves cutouts from the map.
/// The bars and the buttons publish as separate keys — action steps union the
/// two rects so the Win Chance numbers stay lit while the user acts (the iOS
/// build lost the inner anchor entirely when they were nested).
class TutorialAnchors {
    val rects = mutableStateMapOf<String, Rect>()
}

object TutorialTarget {
    const val WIN_BAR = "winBar"
    const val ACTION_BAR = "actionBar"
    const val ADVISOR = "advisor"
}

/// Registers this composable as a tutorial spotlight target. All targets must
/// resolve in one full-bleed coordinate space (boundsInRoot), or the cutout
/// shifts by the system-inset amount.
fun Modifier.tutorialAnchor(anchors: TutorialAnchors, id: String): Modifier =
    onGloballyPositioned { anchors.rects[id] = it.boundsInRoot() }

// Attention swell

/// True when the user has animations disabled system-wide — the Android
/// equivalent of iOS Reduce Motion for this purpose.
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
}

/// Attention swell: scales up and settles back, then repeats every 5s while
/// the step stays targeted, so a control the coach names keeps drawing the
/// eye without a constant pulse. The cycle restarts on every step change, so
/// paired swells (instruction then the button it names) stay in lockstep.
/// Sits out under reduced motion.
@Composable
fun tutorialSwellModifier(
    isTarget: Boolean,
    step: TutorialStep,
    delayMs: Long,
    scale: Float = 1.10f,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    val reduceMotion = rememberReduceMotion()
    val anim = remember { Animatable(1f) }
    LaunchedEffect(step, isTarget, reduceMotion) {
        anim.snapTo(1f)
        if (!isTarget || reduceMotion) return@LaunchedEffect
        while (true) {
            delay(delayMs)
            // Ease up, hold briefly at peak, then ease back — the hold makes
            // the swell register at a small scale without a jarring bounce.
            anim.animateTo(scale, tween(280, easing = LinearOutSlowInEasing))
            delay(220)
            anim.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            delay((SWELL_PERIOD_MS - delayMs - 900).coerceAtLeast(0))
        }
    }
    return Modifier.graphicsLayer {
        scaleX = anim.value
        scaleY = anim.value
        transformOrigin = origin
    }
}

private const val SWELL_PERIOD_MS = 5000L

// Overlay

/// The full-screen coach layer for the guided first hand: a dim layer with a
/// cutout over the current step's target, Oliver's coach bubble, the closing
/// card, and an always-available Skip button. Rendered topmost in
/// GameTableScreen only while the tutorial runs, edge-to-edge with no insets
/// padding so the cutout and the anchors share one coordinate space.
@Composable
fun TutorialOverlay(vm: GameViewModel) {
    val tutorial = LocalTutorial.current
    val anchors = tutorial.anchors
    val step = tutorial.step
    val display = StepDisplay.forStep(step)

    Box(Modifier.fillMaxSize()) {
        if (display != null) {
            val cutout = display.targets
                .mapNotNull { anchors.rects[it] }
                .reduceOrNull { acc, r -> unionRect(acc, r) }

            Spotlight(
                cutout = cutout,
                blocking = !display.passThrough,
                onTap = { if (display.tapAdvances) tutorial.advanceFromTap() },
            )

            if (step == TutorialStep.ASK_OLIVER_SPLIT || step == TutorialStep.ASK_OLIVER_DOUBLE) {
                anchors.rects[TutorialTarget.ADVISOR]?.let { rect ->
                    AdvisorTapCatcher(rect) { tutorial.advisorTapped() }
                }
            }

            var bubbleRect by remember { mutableStateOf<Rect?>(null) }
            CoachBubble(display, step) { bubbleRect = it }

            // A dotted pointer from the coach bubble down to the Win Chance
            // bars. Only the Win Chance step gets it — the other steps read
            // fine from the spotlight ring alone.
            if (step == TutorialStep.WIN_CHANCE) {
                ConnectorLayer(
                    bubble = bubbleRect,
                    spot = anchors.rects[TutorialTarget.WIN_BAR],
                )
            }
        }

        if (step == TutorialStep.FINISHED) {
            TutorialDoneCard(vm)
        } else {
            SkipButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onSkip = {
                    tutorial.end()
                    vm.returnToSetup()
                },
            )
        }
    }
}

private fun unionRect(a: Rect, b: Rect): Rect = Rect(
    left = minOf(a.left, b.left),
    top = minOf(a.top, b.top),
    right = maxOf(a.right, b.right),
    bottom = maxOf(a.bottom, b.bottom),
)

// Spotlight

/// Dim layer with a punched-out cutout and a glowing accent ring. The cutout
/// uses BlendMode.Clear on an offscreen-composited layer — the Compose analog
/// of iOS `.blendMode(.destinationOut)` + `.compositingGroup()`.
@Composable
private fun Spotlight(
    cutout: Rect?,
    blocking: Boolean,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val pad = with(density) { 9.dp.toPx() }
    val corner = with(density) { 18.dp.toPx() }

    val touchModifier = if (blocking) {
        Modifier.pointerInput(onTap) { detectTapGestures { onTap() } }
    } else {
        // Action steps: taps must reach the spotlighted control underneath —
        // no pointer handling at all, so hits fall through the scrim.
        Modifier
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(touchModifier)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.55f))
                if (cutout != null) {
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(cutout.left - pad, cutout.top - pad),
                        size = Size(cutout.width + 2 * pad, cutout.height + 2 * pad),
                        cornerRadius = CornerRadius(corner),
                        blendMode = BlendMode.Clear,
                    )
                }
            },
    )
    if (cutout != null) {
        // Glow ring, drawn outside the offscreen layer so Clear can't eat it.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val topLeft = Offset(cutout.left - pad, cutout.top - pad)
                    val size = Size(cutout.width + 2 * pad, cutout.height + 2 * pad)
                    // Soft outer glow: widening, fading strokes.
                    for (i in 3 downTo 1) {
                        drawRoundRect(
                            color = BjColors.Accent.copy(alpha = 0.12f * i),
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = CornerRadius(corner),
                            style = Stroke(width = 2.dp.toPx() + i * 3.dp.toPx()),
                        )
                    }
                    drawRoundRect(
                        color = BjColors.Accent,
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = CornerRadius(corner),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                },
        )
    }
}

/// Invisible tap target over the Ask Oliver pill for the ask steps — the
/// blocking dim consumes every other touch, so the pill itself is the only
/// thing that responds, exactly as the coach line instructs.
@Composable
private fun AdvisorTapCatcher(rect: Rect, onTap: () -> Unit) {
    val density = LocalDensity.current
    val pad = with(density) { 9.dp.toPx() }
    val width = with(density) { (rect.width + 2 * pad).toDp() }
    val height = with(density) { (rect.height + 2 * pad).toDp() }
    Box(
        Modifier
            .offset { IntOffset((rect.left - pad).roundToInt(), (rect.top - pad).roundToInt()) }
            .size(width, height)
            .testTag("tutorial.askOliver")
            .semantics {
                contentDescription = "Ask Oliver"
                role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTap() },
    )
}

// Coach bubble

@Composable
private fun CoachBubble(
    display: StepDisplay,
    step: TutorialStep,
    onPositioned: (Rect) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // Every spotlight target sits in the bottom half of the table, so
            // the bubble always lives in the upper region, clear of the cutout.
            .padding(top = 64.dp)
            .padding(horizontal = 24.dp)
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF5141F29))
            .border(1.dp, BjColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.oliver),
                    contentDescription = null,
                    modifier = Modifier
                        .size(25.dp)
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "OLIVER",
                color = BjColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
        Spacer(Modifier.height(10.dp))

        display.speech?.let { speech ->
            Text(
                "“$speech”",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
        }

        display.coach?.let { coach ->
            Text(
                coach,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
        }

        display.instruction?.let { instruction ->
            // First beat of the handoff: the instruction swells, then the
            // named control does (see BottomRail).
            Text(
                instruction,
                color = BjColors.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.then(
                    tutorialSwellModifier(
                        isTarget = true,
                        step = step,
                        delayMs = 350,
                        scale = 1.07f,
                        origin = TransformOrigin(0f, 0.5f),
                    ),
                ),
            )
        }

        if (display.tapAdvances) {
            if (step == TutorialStep.WIN_CHANCE) {
                // First page: the user hasn't learned the tap-to-advance
                // gesture yet, so give it the same prominent, swelling
                // treatment as an action instruction. Later tap-advance steps
                // keep the subdued style below.
                Text(
                    "Tap anywhere to continue",
                    color = BjColors.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.then(
                        tutorialSwellModifier(
                            isTarget = true,
                            step = step,
                            delayMs = 350,
                            scale = 1.07f,
                            origin = TransformOrigin(0f, 0.5f),
                        ),
                    ),
                )
            } else {
                Text(
                    "Tap anywhere to continue",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// Connector

/// The dotted bubble→target curve with a small chevron at the target end,
/// routed down ~78% of the spotlight's width so it clears the dealer's cards,
/// the inscription, and the player's centered hand, landing over the
/// percentage end of the bars. Breathes gently so it guides the eye without
/// shouting.
@Composable
private fun ConnectorLayer(bubble: Rect?, spot: Rect?) {
    if (bubble == null || spot == null) return
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "connectorPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val x = spot.left + spot.width * 0.78f
                val from = Offset(x, bubble.bottom + 6.dp.toPx())
                // Stop just above the glow ring (rect is padded 9dp each side).
                val to = Offset(x, spot.top - 18.dp.toPx())
                if (to.y - from.y <= with(density) { 40.dp.toPx() }) return@drawBehind

                // Leave the bubble vertically, then swing toward the target.
                val control = Offset(from.x, from.y + (to.y - from.y) * 0.55f)
                val dash = PathEffect.dashPathEffect(
                    floatArrayOf(0.1.dp.toPx(), 8.dp.toPx()),
                )
                val stroke = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dash,
                )
                val path = Path().apply {
                    moveTo(from.x, from.y)
                    quadraticBezierTo(control.x, control.y, to.x, to.y)
                }
                drawPath(path, BjColors.Accent.copy(alpha = alpha), style = stroke)

                // Two short strokes forming an open arrowhead at the tip.
                val angle = atan2(to.y - control.y, to.x - control.x)
                val length = 8.dp.toPx()
                val spread = (Math.PI / 6).toFloat()
                val tipStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                for (side in floatArrayOf(angle - spread, angle + spread)) {
                    val tipPath = Path().apply {
                        moveTo(to.x, to.y)
                        lineTo(to.x - length * cos(side), to.y - length * sin(side))
                    }
                    drawPath(tipPath, BjColors.Accent.copy(alpha = alpha), style = tipStroke)
                }
            },
    )
}

// Skip

@Composable
private fun SkipButton(modifier: Modifier, onSkip: () -> Unit) {
    Text(
        "Skip Tutorial",
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 4.dp, end = 16.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable { onSkip() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// Closing card

/// Shown over the round-end state instead of RoundEndOverlay: the result, the
/// premium reveal, and the handoff to real play. Never presents the paywall.
@Composable
private fun TutorialDoneCard(vm: GameViewModel) {
    val tutorial = LocalTutorial.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Consume stray taps so nothing beneath the card can react.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF5141F29))
                .border(1.dp, BjColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.oliver),
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "That's Blackjack Oracle!",
                color = BjColors.Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            resultLine(vm)?.let { result ->
                Spacer(Modifier.height(16.dp))
                Text(
                    result,
                    color = BjColors.Success,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "“${TutorialScript.lineClosing.text}”",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Win Chance and Ask Oliver rode along free for this hand. They're part of Blackjack Oracle Premium — the game itself is always free.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(listOf(BjColors.Accent, BjColors.AccentSoft)),
                    )
                    .clickable {
                        tutorial.end()
                        vm.returnToSetup()
                    }
                    .padding(horizontal = 34.dp, vertical = 12.dp),
            ) {
                Text(
                    "Play for Real",
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun resultLine(vm: GameViewModel): String? {
    val results = vm.state.roundResults
    if (results.isEmpty()) return null
    val net = results.sumOf { it.net }
    if (net <= 0) return null
    val wins = results.count { it.net > 0 }
    return if (wins >= 2) "Both hands won — \$$net ahead." else "You finished \$$net ahead."
}

// Per-step content

/// What the overlay renders for a step: which targets get the cutout, what
/// Oliver says (quoted + spoken), the coach explainer, and how it advances.
/// The percentages quoted in coach text are the engine's own outputs for the
/// rigged cards, pinned by TutorialScriptedHandTest — if the engine changes,
/// that test fails before this copy can go stale silently.
internal data class StepDisplay(
    /// Targets to spotlight; multiple rects are unioned into one cutout.
    val targets: List<String> = emptyList(),
    val speech: String? = null,
    val coach: String? = null,
    val instruction: String? = null,
    val tapAdvances: Boolean = false,
    /// True for action steps, where taps must fall through to the real control.
    val passThrough: Boolean = false,
) {
    companion object {
        fun forStep(step: TutorialStep): StepDisplay? = when (step) {
            TutorialStep.WIN_CHANCE -> StepDisplay(
                targets = listOf(TutorialTarget.WIN_BAR),
                speech = TutorialScript.lineDeal.text,
                coach = "Every bar is a live win probability for that move, straight from the odds engine. Stand reads best right now — 61%. Watch these numbers move with every card this hand.",
                tapAdvances = true,
            )
            TutorialStep.ASK_OLIVER_SPLIT -> StepDisplay(
                targets = listOf(TutorialTarget.ADVISOR),
                coach = "Almost nobody breaks up an 18. But that dealer 6 is in real trouble — see what Oliver sees.",
                instruction = "Tap the Ask Oliver pill.",
            )
            TutorialStep.OLIVER_SAYS_SPLIT -> StepDisplay(
                targets = listOf(TutorialTarget.ADVISOR),
                speech = TutorialScript.lineSplit.text,
                coach = "One 18 wins 61% of one bet. Split, and each nine wins 59% — with a full bet riding on each. More money working while the dealer's weak.",
                tapAdvances = true,
            )
            TutorialStep.SPLIT_STEP -> StepDisplay(
                targets = listOf(TutorialTarget.WIN_BAR, TutorialTarget.ACTION_BAR),
                coach = "Your nines become two separate hands, each with its own bet.",
                instruction = "Tap the gold SPLIT button.",
                passThrough = true,
            )
            TutorialStep.STAND_STEP -> StepDisplay(
                targets = listOf(TutorialTarget.WIN_BAR, TutorialTarget.ACTION_BAR),
                speech = TutorialScript.lineStand.text,
                coach = "Stand just jumped to 73% — better than the 18 you broke up ever was.",
                instruction = "Tap STAND.",
                passThrough = true,
            )
            TutorialStep.ASK_OLIVER_DOUBLE -> StepDisplay(
                targets = listOf(TutorialTarget.ADVISOR),
                coach = "Your second hand landed 11 — one more card is coming either way. Ask Oliver how to play it.",
                instruction = "Tap the Ask Oliver pill.",
            )
            TutorialStep.OLIVER_SAYS_DOUBLE -> StepDisplay(
                targets = listOf(TutorialTarget.ADVISOR),
                speech = TutorialScript.lineDouble.text,
                coach = "Doubling puts a second bet on this hand and takes exactly one card. The Double bar reads 67% — that's where the money goes.",
                tapAdvances = true,
            )
            TutorialStep.DOUBLE_STEP -> StepDisplay(
                targets = listOf(TutorialTarget.WIN_BAR, TutorialTarget.ACTION_BAR),
                coach = "Watch the dealer try to survive a 16 after this.",
                instruction = "Tap the blue DOUBLE button.",
                passThrough = true,
            )
            TutorialStep.INACTIVE,
            TutorialStep.WAIT_DEAL,
            TutorialStep.WAIT_DEALER,
            TutorialStep.FINISHED,
            -> null
        }
    }
}
