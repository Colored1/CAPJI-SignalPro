package com.capji.signalpro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.capji.signalpro.ui.theme.Capji

/** The animated CAPJI emblem: a "C" ring and lightning bolt inside a live radar. */
@Composable
fun CapjiSignalLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val transition = rememberInfiniteTransition(label = "logo")
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "sweep",
    )
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow",
    )
    Canvas(modifier.size(size).semantics { contentDescription = "CAPJI" }) {
        val radius = this.size.minDimension / 2
        drawCircle(
            Brush.radialGradient(
                listOf(Capji.Cyan.copy(alpha = 0.28f * glow), Color.Transparent),
                center, radius,
            ),
            radius,
        )
        drawCircle(Capji.Violet.copy(alpha = 0.55f), radius * 0.94f, style = Stroke(radius * 0.025f))
        rotate(sweep.value) {
            drawArc(
                Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.25f to Capji.Cyan.copy(alpha = 0.45f),
                    0.2501f to Color.Transparent,
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                size = Size(radius * 1.84f, radius * 1.84f),
            )
        }
        drawEmblem(radius, glow)
    }
}

private fun DrawScope.drawEmblem(radius: Float, glow: Float) {
    val ring = radius * 0.58f
    drawArc(
        Capji.Cyan,
        startAngle = 45f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(center.x - ring, center.y - ring),
        size = Size(ring * 2, ring * 2),
        style = Stroke(radius * 0.15f, cap = StrokeCap.Round),
    )
    val outer = radius * 0.78f
    listOf(-38f, 142f).forEach { start ->
        drawArc(
            Capji.Violet.copy(alpha = glow),
            startAngle = start,
            sweepAngle = 76f,
            useCenter = false,
            topLeft = Offset(center.x - outer, center.y - outer),
            size = Size(outer * 2, outer * 2),
            style = Stroke(radius * 0.06f, cap = StrokeCap.Round),
        )
    }
    val s = radius / 28f
    val bolt = Path().apply {
        moveTo(center.x + 3 * s, center.y - 14 * s)
        lineTo(center.x - 8 * s, center.y + 2 * s)
        lineTo(center.x - 1 * s, center.y + 2 * s)
        lineTo(center.x - 4 * s, center.y + 14 * s)
        lineTo(center.x + 8 * s, center.y - 3 * s)
        lineTo(center.x + 1 * s, center.y - 3 * s)
        close()
    }
    drawPath(bolt, Capji.Emerald)
}
