package com.blackjackoracle.ui.components

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackjackoracle.R
import com.blackjackoracle.caption.CaptionEngine
import com.blackjackoracle.ui.theme.BjColors

/// The captions surface that replaces the Oliver pill / Hoot button while
/// Oliver is speaking (or reading, in caption-only mode). A pure renderer of
/// [CaptionEngine] state; every control calls back into the ViewModel. Port of
/// the iOS `CaptionCardView`.
///
/// Header: Oliver avatar + OLIVER + pause/replay + CC chip + close.
/// Body:   full advice as a scrollable transcript, current sentence highlighted,
///         auto-scrolling to follow speech (the user can scroll freely).
/// Footer: a 2dp progress hairline.
@Composable
fun CaptionCard(
    engine: CaptionEngine,
    captionsEnabled: Boolean,
    isPaused: Boolean,
    isRoundEnd: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onReplay: () -> Unit,
    onToggleCaptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    // Reduce Motion: honor the OS "remove animations" switch (animator scale 0).
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // Follow the spoken sentence. The user can still scroll freely between ticks;
    // the next index change re-centers on the current line.
    LaunchedEffect(engine.currentIndex, engine.sentences.size) {
        if (engine.sentences.isEmpty()) return@LaunchedEffect
        val target = engine.currentIndex.coerceIn(0, engine.sentences.size - 1)
        if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BjColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painterResource(R.drawable.oliver),
                contentDescription = "Oliver",
                modifier = Modifier.size(if (isRoundEnd) 32.dp else 26.dp).clip(CircleShape),
            )
            Text(
                "OLIVER",
                color = BjColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            // Finished → replay; otherwise pause/resume.
            if (engine.isFinished) {
                IconButton(onClick = onReplay, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Replay Oliver",
                        tint = BjColors.Accent,
                    )
                }
            } else {
                IconButton(onClick = onTogglePause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = BjColors.Neutral.copy(alpha = 0.9f),
                    )
                }
            }
            CCChip(enabled = captionsEnabled, onClick = onToggleCaptions)
            IconButton(onClick = onStop, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close captions",
                    tint = BjColors.Neutral.copy(alpha = 0.7f),
                )
            }
        }

        // Transcript — whole advice at once, capped + scrollable so nothing
        // truncates and the controls/cards below are never pushed off-screen.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (isRoundEnd) 150.dp else 84.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(engine.sentences) { index, sentence ->
                val (alpha, weight) = when {
                    index == engine.currentIndex -> 0.98f to FontWeight.SemiBold
                    index < engine.currentIndex -> 0.60f to FontWeight.Normal
                    else -> 0.32f to FontWeight.Normal
                }
                Text(
                    sentence,
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = weight,
                )
            }
        }

        // Progress hairline
        LinearProgressIndicator(
            progress = { engine.progress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp)),
            color = BjColors.Accent,
            trackColor = BjColors.Neutral.copy(alpha = 0.18f),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/// The "CC" toggle. Shared by the Oliver pill / Hoot button (to turn captions
/// on) and the caption-card header (to turn them off). Enabled = captions on.
/// 44dp touch target around a compact 5dp-rounded pill.
@Composable
fun CCChip(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textColor = if (enabled) BjColors.Accent else Color.White.copy(alpha = 0.45f)
    val fill = if (enabled) BjColors.Accent.copy(alpha = 0.18f) else Color.Transparent
    val borderColor = if (enabled) BjColors.Accent.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.30f)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (enabled) "Captions on" else "Captions off" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(fill)
                .border(1.dp, borderColor, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "CC",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
