package com.capji.signalpro.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capji.signalpro.ui.components.CapjiSignalLogo
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val t = LocalLang.current
    val finish by rememberUpdatedState(onFinished)
    val appear = remember { Animatable(0f) }
    val transition = rememberInfiniteTransition(label = "splash")
    val wave = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "wave",
    )
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 120f))
    }
    LaunchedEffect(Unit) {
        delay(2300)
        finish()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Capji.Navy)
            .clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        // Expanding radar rings centred behind the emblem.
        Canvas(Modifier.fillMaxSize()) {
            val maximum = size.minDimension * 0.75f
            val start = 80.dp.toPx()
            repeat(4) { index ->
                val phase = (wave.value + index / 4f) % 1f
                val radius = start + (maximum - start) * phase
                val color = if (index % 2 == 0) Capji.Cyan else Capji.Emerald
                drawCircle(color.copy(alpha = (1 - phase) * 0.12f), radius)
                drawCircle(color.copy(alpha = (1 - phase) * 0.7f), radius, style = Stroke(2.dp.toPx()))
            }
        }
        CapjiSignalLogo(
            Modifier.graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
            },
            size = 168.dp,
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CAPJI", fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp, color = Capji.Text)
            Text("SIGNAL PRO", color = Capji.Cyan, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                t("مختبر الإشارة والتسريع", "Signal & boost laboratory"),
                color = Capji.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(72.dp))
        }
    }
}
