@file:OptIn(ExperimentalMaterial3Api::class)

package com.capji.signalpro.ui.analyzer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.signalpro.core.Band
import com.capji.signalpro.core.ChannelScore
import com.capji.signalpro.core.Verdict
import com.capji.signalpro.core.WifiMath
import com.capji.signalpro.data.ProbeMethod
import com.capji.signalpro.ui.components.CardTitle
import com.capji.signalpro.ui.components.GlassCard
import com.capji.signalpro.ui.components.Hint
import com.capji.signalpro.ui.components.LocationCard
import com.capji.signalpro.ui.components.PrimaryButton
import com.capji.signalpro.ui.components.ScreenColumn
import com.capji.signalpro.ui.components.SecondaryButton
import com.capji.signalpro.ui.components.StatTile
import com.capji.signalpro.ui.i18n.Lang
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji
import java.util.Locale

@Composable
fun AnalyzerScreen(viewModel: AnalyzerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshEnvironment()
        if (!viewModel.state.value.scanned) viewModel.scan()
        onPauseOrDispose { }
    }
    ScreenColumn {
        PingLab(state, viewModel::startPingTest, viewModel::stopPingTest)
        LocationCard(state.hasPermission, state.locationEnabled, onChanged = viewModel::scan)
        ChannelCard(state, viewModel)
        if (state.scanned) RecommendationCard(state)
    }
}

@Composable
private fun ChannelCard(state: AnalyzerUiState, viewModel: AnalyzerViewModel) {
    val t = LocalLang.current
    val inBand = state.accessPoints.filter { it.band == state.band }
    GlassCard {
        CardTitle(t("تداخل وازدحام القنوات", "Channel overlap & interference"), Icons.Rounded.WifiFind)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Band.GHZ_2_4, Band.GHZ_5, Band.GHZ_6)
                .filter { band -> band != Band.GHZ_6 || state.accessPoints.any { it.band == band } }
                .forEach { band ->
                    FilterChip(
                        selected = state.band == band,
                        onClick = { viewModel.selectBand(band) },
                        label = { Text("${band.label} (${state.accessPoints.count { it.band == band }})") },
                    )
                }
        }
        ChannelGraph(state.band, state.accessPoints, state.ownBssid)
        if (state.scanned && inBand.isEmpty()) {
            Hint(t("لم تُرصد شبكات على هذا التردد.", "No networks detected on this band."))
        } else if (state.band != Band.GHZ_2_4) {
            Hint(t("اسحب الرسم أفقياً لرؤية كل القنوات. شبكتك باللون الأخضر.", "Swipe the graph to see all channels. Your network is green."))
        } else {
            Hint(t("شبكتك باللون الأخضر.", "Your network is shown in green."))
        }
        PrimaryButton(
            if (state.scanning) t("جاري الفحص…", "Scanning…") else t("فحص الشبكات المجاورة", "Scan nearby networks"),
            onClick = viewModel::scan,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Refresh,
            enabled = !state.scanning && state.hasPermission && state.locationEnabled,
        )
        if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.scanned && !state.fresh) {
            Hint(
                t(
                    "أندرويد يحدّ عدد مرات الفحص (4 كل دقيقتين)؛ هذه آخر نتائج محفوظة.",
                    "Android limits scan frequency (4 per 2 minutes); showing the last saved results.",
                ),
                Capji.Amber,
            )
        }
    }
}

@Composable
private fun RecommendationCard(state: AnalyzerUiState) {
    val t = LocalLang.current
    if (state.accessPoints.none { it.band == state.band }) return
    val ranked = WifiMath.recommend(state.band, state.accessPoints, state.ownBssid)
    val best = ranked.firstOrNull() ?: return
    val own = state.accessPoints.firstOrNull { it.bssid.equals(state.ownBssid, ignoreCase = true) }
    val neighbours = state.accessPoints.filterNot { it.bssid.equals(state.ownBssid, ignoreCase = true) }
    val ownChannel = own?.takeIf { it.band == state.band }?.channel
    GlassCard(accent = Capji.Emerald) {
        CardTitle(t("القناة الموصى بها", "Recommended channel"), Icons.Rounded.ThumbUp, Capji.Emerald)
        Text(
            t("القناة ${best.channel}", "Channel ${best.channel}"),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = Capji.Emerald,
        )
        if (ownChannel != null) {
            val current = WifiMath.score(state.band, ownChannel, neighbours)
            Hint(
                t(
                    "قناتك الحالية $ownChannel بازدحام ${current.congestion}%",
                    "Your current channel $ownChannel is ${current.congestion}% congested",
                ),
                Capji.Text,
            )
        }
        ranked.forEach { ScoreRow(it) }
        Hint(
            t(
                "غيّر القناة من صفحة إعدادات الراوتر. على 5 جيجاهرتز استُبعدت قنوات DFS، وقد تختلف القنوات المسموحة حسب بلدك.",
                "Change the channel in your router settings. DFS channels are excluded on 5 GHz; allowed channels vary by country.",
            ),
        )
    }
}

@Composable
private fun ScoreRow(score: ChannelScore) {
    val t = LocalLang.current
    val color = Capji.quality(100 - score.congestion)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("CH ${score.channel}", Modifier.width(64.dp), fontWeight = FontWeight.Bold)
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1B2947)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(score.congestion.coerceAtLeast(3) / 100f)
                    .height(10.dp)
                    .background(color),
            )
        }
        Spacer(Modifier.width(10.dp))
        Hint("${score.congestion}% · ${score.overlapping} " + t("شبكة", "AP"), color)
    }
}

private fun verdictText(t: Lang, verdict: Verdict): Pair<String, String> = when (verdict) {
    Verdict.HEALTHY -> t("الاتصال مستقر", "Connection is stable") to
        t("لا يظهر خلل في الواي فاي ولا لدى مزود الخدمة.", "No problem seen on Wi-Fi or at the provider.")
    Verdict.LOCAL_WIFI -> t("المشكلة في الواي فاي الداخلي", "The problem is your indoor Wi-Fi") to
        t("الاستجابة من الراوتر نفسه ضعيفة. اقترب منه أو غيّر القناة أو موضعه.", "Replies from the router itself are poor. Move closer, change channel or reposition it.")
    Verdict.ISP -> t("المشكلة بعد الراوتر (مزود الخدمة)", "The problem is beyond the router (provider)") to
        t("لا يظهر خلل بينك وبين الراوتر، لكن الإنترنت بطيء أو متقطع.", "The local link shows no fault, but the internet is slow or unstable.")
    Verdict.INTERNET_DOWN -> t("لا يوجد إنترنت", "No internet") to
        t("الراوتر يستجيب لكن الخوادم الخارجية لا تستجيب.", "The router answers but internet servers do not.")
    Verdict.NO_CONNECTION -> t("تعذّر الوصول إلى أي هدف", "Nothing answered") to
        t("تحقق من اتصال الواي فاي أو بيانات الهاتف.", "Check your Wi-Fi or mobile data connection.")
    Verdict.ROUTER_SILENT_INTERNET_BAD -> t("الإنترنت ضعيف", "Internet is poor") to
        t("الراوتر لا يستجيب للبينج، فلا يمكن تحديد مصدر المشكلة بدقة.", "The router ignores pings, so the source cannot be pinpointed.")
}

@Composable
private fun PingLab(state: AnalyzerUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val t = LocalLang.current
    GlassCard(Modifier.animateContentSize(), accent = Capji.Violet) {
        CardTitle(t("مختبر ثبات البينج", "Ping stability lab"), Icons.Rounded.NetworkCheck, Capji.Violet)
        Hint(
            t(
                "يقيس الراوتر والإنترنت معاً (30 محاولة): التأخير والتذبذب وفقدان الحزم.",
                "Tests router and internet together (30 probes): latency, jitter and packet loss.",
            ),
        )
        if (state.testing) {
            SecondaryButton(t("إيقاف", "Stop"), onStop, Modifier.fillMaxWidth(), Icons.Rounded.Stop)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(
                t("ابدأ الاختبار", "Start test"), onStart, Modifier.fillMaxWidth(),
                Icons.Rounded.NetworkCheck, color = Capji.Emerald,
            )
        }
        state.verdict?.let { verdict ->
            val (title, detail) = verdictText(t, verdict)
            val healthy = verdict == Verdict.HEALTHY
            val color = if (healthy) Capji.Emerald else Capji.Amber
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(12.dp),
            ) {
                CardTitle(title, if (healthy) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, color)
                Hint(detail, Capji.Text)
            }
        }
        if (state.targets.isNotEmpty() && state.targets.none { it.isRouter }) {
            Hint(t("لست على واي فاي، لذا يُقاس الإنترنت فقط.", "Not on Wi-Fi, so only the internet is measured."))
        }
        state.targets.forEach { target ->
            val stats = target.stats
            Text(
                (if (target.isRouter) t("الراوتر", "Router") else t("الإنترنت", "Internet")) +
                    " · ${target.host} · " +
                    if (target.method == ProbeMethod.ICMP) "ICMP" else "TCP ${target.tcpPort}",
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(t("التأخير ms", "Latency ms"), number(stats.averageMs), stats.averageMs?.let(Capji::latency) ?: Capji.Muted, Modifier.weight(1f))
                StatTile(t("التذبذب ms", "Jitter ms"), number(stats.jitterMs), Capji.Cyan, Modifier.weight(1f))
                StatTile(
                    t("الفقدان", "Loss"),
                    if (stats.sent == 0) "—" else "${stats.lossPercent.toInt()}%",
                    if (stats.lossPercent > 0) Capji.Red else Capji.Emerald,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

private fun number(value: Double?): String = value?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
