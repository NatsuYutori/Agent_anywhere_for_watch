package com.agentsanywhere.app.watch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import kotlin.math.max

/** The two "nothing on this screen yet" marks. */
internal enum class WatchEmptyGlyph {
    /** No conversation picked: an empty speech bubble. */
    Conversation,

    /** No conversation to act on: a panel of idle controls. */
    Controls,
}

/**
 * The middle-of-the-screen mark for a page that has nothing to show.
 *
 * It is drawn rather than taken from the icon set because both marks are round-panel shapes: a
 * bubble and a control strip read at a glance where a generic "blocked" symbol would not say
 * which page the user is on.
 *
 * The entry animation replays every time the page comes back on screen. That happens for free:
 * the pager disposes the page it leaves, so this composable is built again on the way in.
 */
@Composable
internal fun WatchEmptyState(
    glyph: WatchEmptyGlyph,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAAColors.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
    val breathe = rememberInfiniteTransition(label = "watchEmpty")
    val breath by breathe.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "watchEmptyBreath",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    alpha = enter.value * breath
                    val scale = 0.84f + 0.16f * enter.value
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            val stroke = Stroke(
                width = max(1.6f, size.minDimension * 0.055f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            when (glyph) {
                WatchEmptyGlyph.Conversation -> drawSpeechBubble(colors.muted, stroke)
                WatchEmptyGlyph.Controls -> drawControls(colors.muted, stroke)
            }
        }
        if (text.isNotBlank()) {
            Text(
                text = text,
                color = colors.muted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** A speech bubble outline with three dots in it: "there is no conversation here". */
private fun DrawScope.drawSpeechBubble(
    tint: Color,
    stroke: Stroke,
) {
    val w = size.width
    val h = size.height
    val body = Size(w * 0.80f, h * 0.58f)
    val bodyTopLeft = Offset(w * 0.10f, h * 0.12f)
    drawRoundRect(
        color = tint,
        topLeft = bodyTopLeft,
        size = body,
        cornerRadius = CornerRadius(w * 0.16f, h * 0.16f),
        style = stroke,
    )
    val tail = Path().apply {
        moveTo(w * 0.28f, bodyTopLeft.y + body.height)
        lineTo(w * 0.28f, h * 0.92f)
        lineTo(w * 0.46f, bodyTopLeft.y + body.height)
    }
    drawPath(tail, color = tint, style = stroke)
    val dotY = bodyTopLeft.y + body.height / 2f
    listOf(0.30f, 0.50f, 0.70f).forEach { x ->
        drawCircle(color = tint, radius = w * 0.035f, center = Offset(w * x, dotY))
    }
}

/** Three sliders: "there is no conversation for these controls to act on". */
private fun DrawScope.drawControls(
    tint: Color,
    stroke: Stroke,
) {
    val w = size.width
    val h = size.height
    val rows = listOf(0.24f to 0.34f, 0.50f to 0.66f, 0.76f to 0.44f)
    rows.forEach { (y, knob) ->
        drawLine(
            color = tint,
            start = Offset(w * 0.12f, h * y),
            end = Offset(w * 0.88f, h * y),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawCircle(color = tint, radius = w * 0.11f, center = Offset(w * knob, h * y))
    }
}

/**
 * The "the model is working" mark.
 *
 * A ring with one lit quarter, turning on the spot next to the word. A watch has room for exactly
 * one busy signal, and this is the one that reads as *working* rather than as *still loading* -
 * the three pulsing dots it replaces spent the same space looking like placeholder text.
 */
@Composable
internal fun WatchThinkingIndicator(modifier: Modifier = Modifier) {
    val colors = LocalAAColors.current
    val transition = rememberInfiniteTransition(label = "watchThinking")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
        ),
        label = "watchThinkingAngle",
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(11.dp)
                .graphicsLayer { rotationZ = angle },
        ) {
            val stroke = Stroke(width = size.minDimension * 0.2f, cap = StrokeCap.Round)
            drawArc(color = colors.ink, startAngle = 0f, sweepAngle = 92f, useCenter = false, style = stroke)
            drawArc(color = colors.subtle, startAngle = 112f, sweepAngle = 248f, useCenter = false, style = stroke)
        }
        Text(
            text = "思考中",
            color = colors.inkSoft,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Small green dot: the runtime behind this row is up and idle. */
@Composable
internal fun WatchOnlineDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(LocalAAColors.current.noticeSuccess),
    )
}
