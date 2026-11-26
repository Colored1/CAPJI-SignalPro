package com.capji.signalpro.ui.radar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capji.signalpro.ui.components.LtrStyle
import com.capji.signalpro.ui.theme.Capji
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Radial signal gauge with a sprung needle over a rotating sonar sweep. */
@Composable
fun SonarGauge(quality: Int?, rssi: Int?, caption: String) {
    val value by animateFloatAsState(
        targetValue = (quality ?: 0) / 100f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val transition = rememberInfiniteTransition(label = "sonar")
    val sweep = transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "sweep",
    )
    val color = quality?.let(Capji::quality) ?: Capji.Muted
    Box(
        Modifier.widthIn(max = 330.dp).fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val radius = size.minDimension / 2
            val inner = radius * 0.80f
            listOf(1f, 0.72f, 0.44f).forEach {
                drawCircle(Capji.Cyan.copy(alpha = 0.18f), inner * it, style = Stroke(1.dp.toPx()))
            }
            drawLine(Capji.Cyan.copy(alpha = 0.12f), Offset(center.x - inner, center.y), Offset(center.x + inner, center.y))
            drawLine(Capji.Cyan.copy(alpha = 0.12f), Offset(center.x, center.y - inner), Offset(center.x, center.y + inner))
            rotate(sweep.value) {
                drawArc(
                    Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.2f to color.copy(alpha = 0.40f),
                        0.2001f to Color.Transparent,
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 72f,
                    useCenter = true,
                    topLeft = Offset(center.x - inner, center.y - inner),
                    size = Size(inner * 2, inner * 2),
                )
            }
            val stroke = Stroke(13.dp.toPx(), cap = StrokeCap.Round)
            val arcRadius = radius - 8.dp.toPx()
            val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2, arcRadius * 2)
            drawArc(Color(0xFF1B2947), 150f, 240f, false, topLeft, arcSize, style = stroke)
            drawArc(
                color,
                startAngle = 150f,
                sweepAngle = 240f * value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            val angle = ((150f + 240f * value) * PI / 180).toFloat()
            val direction = Offset(cos(angle), sin(angle))
            drawLine(
                Color.White,
                start = center + direction * (arcRadius * 0.58f),
                end = center + direction * (arcRadius * 0.90f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                rssi?.toString() ?: "—",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                style = LtrStyle,
            )
            Text("dBm", color = Capji.Muted)
            Text(
                quality?.let { "$it%" } ?: "—",
                color = color,
                style = MaterialTheme.typography.headlineSmall.merge(LtrStyle),
                fontWeight = FontWeight.Bold,
            )
            Text(caption, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Scrolling trace of the newest samples (right edge). Each new sample glides in over one
 * second. Null samples leave a gap and, for pings, mark a lost probe.
 */
@Composable
fun Oscilloscope(samples: List<Float?>, minimum: Float, maximum: Float, color: Color, markLoss: Boolean) {
    val glide = remember { Animatable(1f) }
    LaunchedEffect(samples) {
        glide.snapTo(0f)
        glide.animateTo(1f, tween(1000, easing = LinearEasing))
    }
    Canvas(Modifier.fillMaxWidth().height(110.dp).clipToBounds()) {
        for (line in 0..4) {
            val y = size.height * line / 4
            drawLine(Capji.Cyan.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y))
        }
        for (column in 0..10) {
            val x = size.width * column / 10
            drawLine(Capji.Cyan.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, size.height))
        }
        if (samples.isEmpty()) return@Canvas
        val step = size.width / 58f
        val range = (maximum - minimum).coerceAtLeast(1f)
        fun point(index: Int, value: Float): Offset {
            val x = size.width - (samples.lastIndex - index) * step + step * (1 - glide.value)
            val y = size.height - ((value - minimum) / range).coerceIn(0f, 1f) * (size.height - 8f) - 4f
            return Offset(x, y)
        }
        val path = Path()
        var drawing = false
        samples.forEachIndexed { index, value ->
            if (value == null) {
                drawing = false
                if (markLoss) {
                    val x = point(index, minimum).x
                    drawLine(Capji.Red, Offset(x, size.height), Offset(x, size.height * 0.7f), 3.dp.toPx())
                }
            } else {
                val position = point(index, value)
                if (drawing) path.lineTo(position.x, position.y) else path.moveTo(position.x, position.y)
                drawing = true
                drawCircle(color, 1.8.dp.toPx(), position)
            }
        }
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
