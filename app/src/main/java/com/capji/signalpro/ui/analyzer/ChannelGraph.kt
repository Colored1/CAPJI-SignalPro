package com.capji.signalpro.ui.analyzer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capji.signalpro.core.AccessPoint
import com.capji.signalpro.core.Band
import com.capji.signalpro.core.WifiMath
import com.capji.signalpro.ui.theme.Capji

private class Axis(val segments: List<IntRange>, val ticks: List<Int>, val minimumWidth: Int) {
    private val total = segments.sumOf { it.last - it.first } + GAP * (segments.size - 1)

    /** Horizontal position 0..1 of a frequency; unused spectrum between segments is squeezed. */
    fun position(frequency: Float): Float {
        var offset = 0f
        segments.forEachIndexed { index, segment ->
            if (frequency <= segment.last || index == segments.lastIndex) {
                return (offset + (frequency - segment.first)) / total
            }
            offset += segment.last - segment.first + GAP
        }
        return 1f
    }

    private companion object {
        const val GAP = 20
    }
}

private fun axisFor(band: Band): Axis = when (band) {
    Band.GHZ_5 -> Axis(
        listOf(5160..5340, 5480..5895),
        listOf(36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157, 165),
        680,
    )
    Band.GHZ_6 -> Axis(listOf(5935..7125), (5..229 step 16).toList(), 900)
    else -> Axis(listOf(2399..2497), (1..14).toList(), 0)
}

private val palette = listOf(
    Capji.Cyan, Capji.Violet, Capji.Amber, Color(0xFFFF4081), Color(0xFF40C4FF),
    Color(0xFFB2FF59), Color(0xFFFF6E40), Color(0xFFEA80FC),
)

/** Channel overlap graph: one hump per access point, height follows signal strength. */
@Composable
fun ChannelGraph(band: Band, accessPoints: List<AccessPoint>, ownBssid: String?) {
    val axis = axisFor(band)
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(band, accessPoints) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    // Frequency axes read left to right in both languages.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxOf(maxWidth, axis.minimumWidth.dp)
            Canvas(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .width(width)
                    .height(240.dp),
            ) {
                val axisY = size.height - 22.dp.toPx()
                val top = 16.dp.toPx()
                val labelStyle = TextStyle(color = Capji.Muted, fontSize = 10.sp, textDirection = TextDirection.Ltr)
                fun x(frequency: Float) = axis.position(frequency) * size.width
                fun y(rssi: Int) =
                    axisY - ((rssi + 100) / 70f).coerceIn(0.04f, 1f) * (axisY - top) * grow.value

                listOf(-90, -70, -50).forEach { level ->
                    val lineY = axisY - ((level + 100) / 70f) * (axisY - top)
                    drawLine(Capji.Cyan.copy(alpha = 0.10f), Offset(0f, lineY), Offset(size.width, lineY))
                    drawText(measurer.measure("$level", labelStyle), topLeft = Offset(2f, lineY - 14.dp.toPx()))
                }
                drawLine(Capji.Muted, Offset(0f, axisY), Offset(size.width, axisY))
                axis.ticks.forEach { channel ->
                    val position = x(WifiMath.frequencyOf(band, channel).toFloat())
                    val label = measurer.measure("$channel", labelStyle)
                    drawLine(Capji.Muted, Offset(position, axisY), Offset(position, axisY + 4.dp.toPx()))
                    drawText(label, topLeft = Offset(position - label.size.width / 2f, axisY + 5.dp.toPx()))
                }

                accessPoints.filter { it.band == band }.sortedBy { it.rssi }.forEach { ap ->
                    val own = ap.bssid.equals(ownBssid, ignoreCase = true)
                    val color = if (own) Capji.Emerald
                    else palette[Math.floorMod(ap.bssid.hashCode(), palette.size)]
                    val half = WifiMath.occupiedWidth(ap) / 2f
                    val left = x(ap.centerFrequency - half)
                    val right = x(ap.centerFrequency + half)
                    val middle = (left + right) / 2
                    val peak = y(ap.rssi)
                    // A quadratic curve peaks halfway to its control point.
                    val path = Path().apply {
                        moveTo(left, axisY)
                        quadraticTo(middle, 2 * peak - axisY, right, axisY)
                    }
                    drawPath(path, color.copy(alpha = if (own) 0.30f else 0.12f))
                    drawPath(path, color, style = Stroke((if (own) 3f else 1.5f).dp.toPx()))
                    val name = ap.ssid.ifBlank { "(hidden)" }.take(14)
                    val label = measurer.measure(name, labelStyle.copy(color = color))
                    drawText(
                        label,
                        topLeft = Offset(middle - label.size.width / 2f, (peak - label.size.height).coerceAtLeast(0f)),
                    )
                }
            }
        }
    }
}
