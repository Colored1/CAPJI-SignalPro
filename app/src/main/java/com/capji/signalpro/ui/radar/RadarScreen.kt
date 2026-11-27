@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.capji.signalpro.ui.radar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.signalpro.data.RoomReading
import com.capji.signalpro.ui.components.CardTitle
import com.capji.signalpro.ui.components.GlassCard
import com.capji.signalpro.ui.components.Hint
import com.capji.signalpro.ui.components.InfoRow
import com.capji.signalpro.ui.components.LocationCard
import com.capji.signalpro.ui.components.PrimaryButton
import com.capji.signalpro.ui.components.ScreenColumn
import com.capji.signalpro.ui.components.ltr
import com.capji.signalpro.ui.i18n.Lang
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji
import kotlin.math.roundToInt

private fun rating(t: Lang, quality: Int?): String = when {
    quality == null -> t("لا توجد إشارة", "No signal")
    quality >= 75 -> t("إشارة ممتازة", "Excellent")
    quality >= 55 -> t("إشارة جيدة", "Good")
    quality >= 35 -> t("إشارة متوسطة", "Fair")
    else -> t("إشارة ضعيفة", "Weak")
}

@Composable
fun RadarScreen(viewModel: RadarViewModel = viewModel()) {
    val t = LocalLang.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.monitor() }
    }
    // Keep the screen awake while walking around with the sonar.
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(state.sonarOn) {
        view.keepScreenOn = state.sonarOn
        onDispose { view.keepScreenOn = false }
    }
    val telemetry = state.telemetry

    ScreenColumn {
        LocationCard(state.hasPermission, state.locationEnabled, onChanged = {})
        if (!telemetry.connected) {
            GlassCard(accent = Capji.Amber) {
                CardTitle(
                    if (telemetry.wifiEnabled) t("غير متصل بشبكة واي فاي", "Not connected to Wi-Fi")
                    else t("الواي فاي متوقف", "Wi-Fi is off"),
                    Icons.Rounded.WifiOff, Capji.Amber,
                )
                Hint(t("اتصل بشبكة لعرض القياسات الحية.", "Connect to a network to see live readings."))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SonarGauge(telemetry.quality, telemetry.rssi, rating(t, telemetry.quality))
        }

        GlassCard(accent = Capji.Emerald) {
            CardTitle(t("سونار تحديد موضع الراوتر", "Router placement sonar"), Icons.Rounded.GraphicEq, Capji.Emerald)
            Hint(
                t(
                    "تجوّل في المكان: كلما قويت الإشارة ارتفعت النغمة وتسارعت.",
                    "Walk around: the stronger the signal, the higher and faster the beep.",
                ),
            )
            ToggleRow(t("تشغيل السونار", "Sonar"), state.sonarOn, viewModel::setSonar)
            ToggleRow(t("الصوت", "Sound"), state.sound, viewModel::setSound)
            ToggleRow(t("الاهتزاز", "Vibration"), state.haptics, viewModel::setHaptics)
        }

        GlassCard {
            CardTitle(t("المرسام الحي", "Live oscilloscope"), Icons.Rounded.ShowChart)
            Hint("RSSI · " + (telemetry.rssi?.let { ltr("$it dBm") } ?: "—"))
            Oscilloscope(state.rssiHistory, -95f, -30f, Capji.Cyan, markLoss = false)
            val last = state.pingHistory.lastOrNull()
            Hint(
                t("بينج الراوتر", "Router ping") + " · " +
                    (last?.let { ltr("${it.roundToInt()} ms") } ?: t("لا استجابة", "no reply")),
            )
            val peak = state.pingHistory.filterNotNull().maxOrNull() ?: 0f
            Oscilloscope(state.pingHistory, 0f, maxOf(50f, peak * 1.2f), Capji.Emerald, markLoss = true)
        }

        GlassCard(accent = Capji.Violet) {
            CardTitle(t("تفاصيل الشبكة الحية", "Live network details"), Icons.Rounded.Wifi, Capji.Violet)
            InfoRow(t("اسم الشبكة", "SSID"), telemetry.ssid)
            InfoRow("BSSID", telemetry.bssid)
            InfoRow(t("قوة الإشارة", "RSSI"), telemetry.rssi?.let { "$it dBm" })
            InfoRow(t("الجودة", "Quality"), telemetry.quality?.let { "$it%" })
            InfoRow(t("سرعة الربط", "Link speed"), telemetry.linkSpeedMbps?.let { "$it Mbps" })
            InfoRow(t("التردد", "Band"), telemetry.frequency?.let { "${telemetry.band.label} · $it MHz" })
            InfoRow(t("القناة", "Channel"), telemetry.channel?.toString())
            InfoRow(t("عنوان IP المحلي", "Local IP"), telemetry.ipAddress)
            InfoRow(t("البوابة", "Gateway"), telemetry.gateway)
            InfoRow("DNS", telemetry.dns.joinToString("\n").ifEmpty { null })
        }

        RoomLogger(state, viewModel)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RoomLogger(state: RadarUiState, viewModel: RadarViewModel) {
    val t = LocalLang.current
    var name by rememberSaveable { mutableStateOf("") }
    GlassCard {
        CardTitle(t("مسجّل إشارة الغرف", "Room signal logger"), Icons.Rounded.MeetingRoom)
        Hint(t("قف في الغرفة، اختر اسمها ثم اضغط «قِس هنا».", "Stand in the room, name it, then tap “Measure here”."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                t("الصالة", "Living room"),
                t("المجلس", "Majlis"),
                t("الغرفة", "Bedroom"),
                t("المحل", "Shop"),
            ).forEach { suggestion ->
                FilterChip(
                    selected = name == suggestion,
                    onClick = { name = suggestion },
                    label = { Text(suggestion) },
                )
            }
        }
        OutlinedTextField(
            name, { name = it.take(30) },
            label = { Text(t("اسم الغرفة", "Room name")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            t("قِس هنا", "Measure here"),
            onClick = { viewModel.measureRoom(name) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && state.measuring == null && state.telemetry.rssi != null,
        )
        state.measuring?.let { progress ->
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        if (state.measureFailed) {
            Hint(t("تعذّر القياس: انقطع الاتصال بالواي فاي.", "Measurement failed: Wi-Fi disconnected."), Capji.Red)
        }
        if (state.rooms.isEmpty()) {
            Hint(t("لا توجد قياسات محفوظة بعد.", "No saved readings yet."))
        } else {
            val sorted = state.rooms.sortedByDescending { it.rssi }
            sorted.forEachIndexed { index, room ->
                key(room.name) { RoomBar(room, best = index == 0 && sorted.size > 1, viewModel::deleteRoom) }
            }
        }
    }
}

/** One bar of the room comparison chart. */
@Composable
private fun RoomBar(room: RoomReading, best: Boolean, onDelete: (String) -> Unit) {
    val t = LocalLang.current
    val fraction by animateFloatAsState(room.quality.coerceAtLeast(3) / 100f, tween(800), label = "room")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(room.name + if (best) "  ★" else "", fontWeight = FontWeight.Bold)
                Hint(
                    ltr(
                        "${room.rssi} dBm · ${room.quality}% · ${room.band}" +
                            (room.linkSpeedMbps?.let { " · $it Mbps" } ?: "") +
                            (room.ssid?.let { " · $it" } ?: ""),
                    ),
                )
            }
            IconButton(onClick = { onDelete(room.name) }) {
                Icon(Icons.Rounded.Delete, contentDescription = t("حذف", "Delete"), tint = Capji.Muted)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1B2947)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Capji.quality(room.quality)),
            )
        }
    }
}
