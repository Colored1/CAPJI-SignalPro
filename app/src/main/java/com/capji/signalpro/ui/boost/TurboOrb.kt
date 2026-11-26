package com.capji.signalpro.ui.boost

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.capji.signalpro.ui.theme.Capji
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The central boost button: glowing core, spinning energy rings and an activation burst. */
@Composable
fun TurboOrb(
    phase: BoostPhase,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val busy = phase == BoostPhase.BENCHMARKING || phase == BoostPhase.CONNECTING
    val active = phase == BoostPhase.ACTIVE
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    val core by animateColorAsState(
        when {
            active -> Capji.Emerald
            busy -> Capji.Violet
            else -> Capji.Cyan
        },
        tween(600),
        label = "core",
    )
    val transition = rememberInfiniteTransition(label = "orb")
    // One full turn per cycle; busy mode speeds up by spinning several turns.
    val spin = transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin",
    )
    val pulse = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val burst = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        }
    }
    val haptics = LocalHapticFeedback.current

    Box(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    listOf(core.copy(alpha = 0.35f + 0.2f * pulse.value), Color.Transparent),
                    center, radius,
                ),
                radius,
            )
            val speed = if (busy) 4f else 1f
            listOf(
                Triple(0.90f, Capji.Cyan, 1f),
                Triple(0.80f, Capji.Violet, -2f),
                Triple(0.70f, Capji.Emerald, 3f),
            ).forEach { (fraction, color, direction) ->
                val r = radius * fraction
                val start = spin.value * direction * speed
                repeat(2) { half ->
                    drawArc(
                        color.copy(alpha = 0.85f),
                        startAngle = start + half * 180f,
                        sweepAngle = 70f + 30f * pulse.value,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            drawCircle(
                Brush.radialGradient(
                    listOf(core.copy(alpha = 0.55f), Color(0xFF0D1830), Color(0xFF080E1C)),
                    center, radius * 0.62f,
                ),
                radius * 0.60f,
            )
            drawCircle(core, radius * 0.60f, style = Stroke(2.dp.toPx()))
            if (burst.value < 1f) {
                val progress = burst.value
                drawCircle(
                    Capji.Emerald.copy(alpha = 1 - progress),
                    radius * (0.6f + 0.4f * progress),
                    style = Stroke(6.dp.toPx() * (1 - progress) + 1f),
                )
                repeat(28) { index ->
                    val angle = index * 2 * PI.toFloat() / 28
                    val distance = radius * (0.55f + progress * if (index % 2 == 0) 0.45f else 0.3f)
                    drawCircle(
                        (if (index % 3 == 0) Capji.Cyan else Capji.Emerald).copy(alpha = 1 - progress),
                        radius = 4.dp.toPx() * (1 - progress * 0.5f),
                        center = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize(0.6f)
                .clip(CircleShape)
                .clickable(interaction, indication = null, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
