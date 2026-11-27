package com.capji.signalpro.ui.boost

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.signalpro.core.DnsProvider
import com.capji.signalpro.core.DnsServers
import com.capji.signalpro.data.BenchResult
import com.capji.signalpro.ui.components.CardTitle
import com.capji.signalpro.ui.components.GlassCard
import com.capji.signalpro.ui.components.Hint
import com.capji.signalpro.ui.components.LtrStyle
import com.capji.signalpro.ui.components.PrimaryButton
import com.capji.signalpro.ui.components.ScreenColumn
import com.capji.signalpro.ui.components.SecondaryButton
import com.capji.signalpro.ui.components.StatTile
import com.capji.signalpro.ui.components.ltr
import com.capji.signalpro.ui.components.openSystemScreen
import com.capji.signalpro.ui.i18n.Lang
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji
import com.capji.signalpro.vpn.VpnError
import com.capji.signalpro.vpn.VpnStatus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun ms(value: Double?): String = value?.let { "${it.roundToInt()} ms" } ?: "—"

@Composable
fun BoostScreen(viewModel: BoostViewModel = viewModel()) {
    val t = LocalLang.current
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vpn by viewModel.vpn.collectAsStateWithLifecycle()
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshEnvironment()
        onPauseOrDispose { }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.boost() else viewModel.permissionDenied()
    }
    fun requestVpn() {
        val intent = runCatching { VpnService.prepare(context) }.getOrNull()
        if (intent != null) consent.launch(intent) else viewModel.boost()
    }
    // The notification permission is optional: the boost continues either way.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestVpn()
    }
    fun begin() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else requestVpn()
    }
    fun onOrb() {
        when {
            state.phase != BoostPhase.IDLE -> viewModel.stop()
            !viewModel.disclosureAccepted -> showDisclosure = true
            else -> begin()
        }
    }

    if (showDisclosure) {
        VpnDisclosureDialog(
            onAccept = {
                showDisclosure = false
                viewModel.acceptDisclosure()
                begin()
            },
            onDecline = { showDisclosure = false },
        )
    }

    ScreenColumn {
        state.privateDns?.let { PrivateDnsWarning(it) }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TurboOrb(state.phase, ::onOrb) { OrbContent(state) }
        }
        Text(
            when (state.phase) {
                BoostPhase.IDLE -> t("تفعيل التسريع وتقليل البينج", "Boost Now")
                BoostPhase.BENCHMARKING -> t("جاري اختبار خوادم DNS…", "Testing DNS servers…")
                BoostPhase.CONNECTING -> t("جاري تشغيل النفق المحلي…", "Starting the local tunnel…")
                BoostPhase.ACTIVE -> t("التسريع مفعّل — اضغط للإيقاف", "Boost is on — tap to stop")
            },
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            color = if (state.phase == BoostPhase.ACTIVE) Capji.Emerald else Capji.Text,
        )
        Hint(
            t(
                "يسرّع ترجمة أسماء المواقع (DNS) ويقلّل تقطّع الواي فاي. لا يغيّر مسار اتصال الألعاب بخوادمها.",
                "Speeds up DNS lookups and limits Wi-Fi power-save spikes. It does not change the route to game servers.",
            ),
        )

        val error = errorText(t, state.message, vpn)
        AnimatedVisibility(error != null) {
            GlassCard(accent = Capji.Red) { Text(error.orEmpty(), color = Capji.Red) }
        }

        ImprovementCard(state)
        if (vpn.running) ActiveCard(vpn)
        DnsListCard(state, viewModel)
    }
}

private fun errorText(t: Lang, message: BoostMessage?, vpn: VpnStatus): String? = when {
    message == BoostMessage.OFFLINE -> t("لا يوجد اتصال بالشبكة.", "No network connection.")
    message == BoostMessage.NO_DNS_ANSWERED -> t(
        "لم يستجب الخادم المختار. جرّب خادماً آخر أو تحقق من الإنترنت.",
        "The selected server did not answer. Try another one or check your internet.",
    )
    message == BoostMessage.START_FAILED -> t("تعذّر تشغيل النفق المحلي.", "Could not start the local tunnel.")
    message == BoostMessage.INVALID_CUSTOM_DNS -> t("عنوان DNS غير صالح (مثال: 1.1.1.1).", "Invalid DNS address (e.g. 1.1.1.1).")
    message == BoostMessage.PERMISSION_DENIED -> t(
        "لم تتم الموافقة على اتصال VPN المحلي، لذلك لم يبدأ التسريع.",
        "The local VPN request was not approved, so the boost did not start.",
    )
    vpn.error == VpnError.REVOKED -> t(
        "أوقف النظام التسريع (ربما بدأ تطبيق VPN آخر).",
        "The system stopped the boost (another VPN may have started).",
    )
    vpn.error == VpnError.ESTABLISH_FAILED -> t("انقطع النفق المحلي.", "The local tunnel failed.")
    else -> null
}

@Composable
private fun OrbContent(state: BoostUiState) {
    val t = LocalLang.current
    // Counts from the ISP time down (or up) to the time measured through the tunnel.
    val counter = remember { Animatable(0f) }
    LaunchedEffect(state.afterMs) {
        val after = state.afterMs ?: return@LaunchedEffect
        counter.snapTo((state.beforeMs ?: after).toFloat())
        counter.animateTo(after.toFloat(), tween(1600))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.phase == BoostPhase.ACTIVE && state.afterMs != null) {
            Text(
                counter.value.roundToInt().toString(),
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(t("زمن DNS (ms)", "ms DNS time"), color = Capji.Emerald, fontWeight = FontWeight.Bold)
        } else {
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = t("تفعيل التسريع وتقليل البينج", "Boost Now"),
                tint = Color.White,
                modifier = Modifier.height(64.dp).width(64.dp),
            )
            Text(
                when (state.phase) {
                    BoostPhase.IDLE -> t("تسريع", "BOOST")
                    BoostPhase.ACTIVE -> t("مفعّل", "ON")
                    else -> t("انتظر…", "WAIT…")
                },
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PrivateDnsWarning(host: String) {
    val t = LocalLang.current
    val context = LocalContext.current
    GlassCard(accent = Capji.Amber) {
        CardTitle(t("«DNS الخاص» مفعّل في هاتفك", "Private DNS is enabled"), Icons.Rounded.Warning, Capji.Amber)
        Hint(
            t(
                "الهاتف يستخدم $host ويتجاهل خادم التطبيق. اضبط «DNS الخاص» على «تلقائي» أو «إيقاف».",
                "The phone uses $host and ignores this app's server. Set Private DNS to Automatic or Off.",
            ),
        )
        SecondaryButton(
            t("فتح إعدادات الشبكة", "Open network settings"),
            onClick = { openSystemScreen(context, Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
        )
    }
}

@Composable
private fun ImprovementCard(state: BoostUiState) {
    val t = LocalLang.current
    if (state.beforeMs == null && state.afterMs == null && state.providerResult == null) return
    val before = state.beforeMs
    val best = state.results.firstOrNull { it.provider.id == state.selectedId }?.latencyMs
    val after = state.afterMs ?: best
    GlassCard(accent = Capji.Emerald) {
        CardTitle(t("قبل وبعد (زمن استجابة DNS)", "Before vs. after (DNS response time)"), Icons.Rounded.Speed, Capji.Emerald)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(t("قبل: مزود الخدمة", "Before: ISP"), ms(before), Capji.Amber, Modifier.weight(1f))
            StatTile(
                if (state.afterMs != null) t("بعد: عبر التسريع", "After: boosted")
                else t("المتوقع: الخادم المختار", "Expected: selected"),
                ms(after), Capji.Emerald, Modifier.weight(1f),
            )
        }
        if (before != null && after != null) {
            // Matches the two rounded figures shown above.
            val difference = before.roundToInt() - after.roundToInt()
            Text(
                when {
                    difference > 0 -> t("تحسّن ${ltr("$difference ms")} في كل طلب DNS", "$difference ms faster per DNS lookup")
                    difference < 0 -> t(
                        "خادم مزودك أسرع بمقدار ${ltr("${abs(difference)} ms")} على هذه الشبكة",
                        "Your ISP resolver is ${abs(difference)} ms faster on this network",
                    )
                    else -> t("لا فرق يُذكر على هذه الشبكة", "No measurable difference on this network")
                },
                color = if (difference > 0) Capji.Emerald else Capji.Amber,
                fontWeight = FontWeight.Bold,
            )
        } else if (before == null) {
            Hint(t("تعذّر قياس خادم مزود الخدمة على هذه الشبكة.", "The ISP resolver could not be measured on this network."))
        }
    }
}

@Composable
private fun ActiveCard(vpn: VpnStatus) {
    val t = LocalLang.current
    GlassCard(accent = Capji.Violet) {
        CardTitle(t("النفق المحلي يعمل", "Local tunnel running"), Icons.Rounded.Shield, Capji.Violet)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(vpn.server, vpn.providerName, Capji.Cyan, Modifier.weight(1f))
            StatTile(
                t("طلبات DNS مُجابة", "DNS answers"),
                String.format(Locale.US, "%d", vpn.answeredQueries), Capji.Emerald, Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = Capji.Cyan)
            Spacer(Modifier.width(8.dp))
            Hint(
                when {
                    !vpn.wifiLockHeld -> t("قفل الواي فاي غير متاح على هذا الجهاز.", "Wi-Fi lock is not available on this device.")
                    Build.VERSION.SDK_INT >= 34 -> t(
                        "وضع الواي فاي منخفض التأخير: أندرويد 14+ يطبّقه فقط والتطبيق ظاهر والشاشة مضاءة.",
                        "Low-latency Wi-Fi: Android 14+ applies it only while this app is on screen.",
                    )
                    else -> t("قفل الواي فاي مفعّل لمنع توفير الطاقة.", "Wi-Fi lock is holding off power save.")
                },
            )
        }
    }
}

@Composable
private fun DnsListCard(state: BoostUiState, viewModel: BoostViewModel) {
    val t = LocalLang.current
    GlassCard {
        CardTitle(t("اختبار سرعة DNS الذكي", "Smart DNS speed test"), Icons.Rounded.Dns)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("اختيار الأسرع تلقائياً", "Auto-select the fastest"), Modifier.weight(1f))
            Switch(state.autoSelect, onCheckedChange = viewModel::setAutoSelect)
        }
        PrimaryButton(
            if (state.testing) t("جاري القياس…", "Measuring…") else t("اختبر الآن", "Test now"),
            onClick = viewModel::benchmark,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Speed,
            enabled = !state.testing && state.phase != BoostPhase.CONNECTING,
        )
        if (state.testing) LinearProgressIndicator(Modifier.fillMaxWidth())

        val measured = state.results.map { it.provider.id }
        val slowest = state.results.mapNotNull { it.latencyMs }.maxOrNull() ?: 1.0
        state.results.forEachIndexed { index, result ->
            DnsRow(index + 1, result.provider, result, slowest, state.selectedId, viewModel::select)
        }
        state.providers.filterNot { it.id in measured }.forEach {
            DnsRow(null, it, null, slowest, state.selectedId, viewModel::select)
        }
        state.providerResult?.let {
            Hint(
                t("خادم مزود الخدمة", "ISP resolver") + " ${ltr("${it.provider.primary}: ${ms(it.latencyMs)}")} · " +
                    t("فقدان ${it.lossPercent}%", "${it.lossPercent}% loss"),
            )
        }
        CustomDns(state, viewModel::saveCustom)
    }
}

@Composable
private fun DnsRow(
    rank: Int?,
    provider: DnsProvider,
    result: BenchResult?,
    slowest: Double,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val t = LocalLang.current
    val selected = provider.id == selectedId
    val latency = result?.latencyMs
    val fraction by animateFloatAsState(
        targetValue = latency?.let { (it / slowest).toFloat().coerceIn(0.04f, 1f) } ?: 0f,
        animationSpec = tween(700),
        label = "bar",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x3300E5FF) else Color(0x33101A30))
            .clickable { onSelect(provider.id) }
            .padding(end = 12.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick = { onSelect(provider.id) })
            Column(Modifier.weight(1f)) {
                val name = if (provider.id == DnsServers.CUSTOM_ID) t("مخصص", "Custom") else provider.name
                Text(ltr((rank?.let { "$it. " } ?: "") + name), fontWeight = FontWeight.Bold)
                Text(
                    provider.servers.joinToString(" / "),
                    color = Capji.Muted,
                    style = MaterialTheme.typography.bodySmall.merge(LtrStyle),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (result != null && latency == null) t("لا استجابة", "No reply") else ltr(ms(latency)),
                    color = latency?.let(Capji::latency) ?: Capji.Muted,
                    fontWeight = FontWeight.Black,
                )
                if (result != null && result.lossPercent in 1..99) {
                    Hint(t("فقدان ${result.lossPercent}%", "${result.lossPercent}% loss"), Capji.Amber)
                }
            }
        }
        if (latency != null) {
            Box(
                Modifier
                    .padding(start = 48.dp)
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Capji.latency(latency)),
            )
        }
    }
}

@Composable
private fun CustomDns(state: BoostUiState, onSave: (String, String) -> Unit) {
    val t = LocalLang.current
    var open by rememberSaveable { mutableStateOf(false) }
    var primary by rememberSaveable { mutableStateOf(state.customPrimary) }
    var secondary by rememberSaveable { mutableStateOf(state.customSecondary) }
    TextButton(onClick = { open = !open }) { Text(t("إضافة DNS مخصص", "Add a custom DNS")) }
    AnimatedVisibility(open) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            OutlinedTextField(
                primary, { primary = it.take(15) },
                label = { Text(t("العنوان الأساسي (IPv4)", "Primary (IPv4)")) },
                singleLine = true, keyboardOptions = keyboard, textStyle = LtrStyle,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                secondary, { secondary = it.take(15) },
                label = { Text(t("العنوان الاحتياطي (اختياري)", "Secondary (optional)")) },
                singleLine = true, keyboardOptions = keyboard, textStyle = LtrStyle,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(t("حفظ واستخدام", "Save and use"), onClick = { onSave(primary, secondary) })
        }
    }
}

/** Prominent disclosure that must be accepted before the VPN permission is requested. */
@Composable
fun VpnDisclosureDialog(onAccept: (() -> Unit)?, onDecline: () -> Unit) {
    val t = LocalLang.current
    AlertDialog(
        onDismissRequest = onDecline,
        icon = { Icon(Icons.Rounded.Shield, contentDescription = null, tint = Capji.Emerald) },
        title = { Text(t("كيف يعمل التسريع؟ (إفصاح VPN)", "How the boost works (VPN disclosure)")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    t(
                        "يستخدم CAPJI Signal Pro خدمة VpnService في أندرويد لإنشاء نفق محلي داخل هاتفك، لغرض واحد: توجيه طلبات DNS إلى الخادم الذي تختاره لتقليل زمن الاستجابة.",
                        "CAPJI Signal Pro uses Android's VpnService to create a local tunnel on your phone for one purpose: sending DNS requests to the server you choose, to lower response time.",
                    ),
                )
                Text(t("• طلبات DNS فقط تمر عبر التطبيق؛ بقية بياناتك لا تلمسه.", "• Only DNS requests pass through the app; your other traffic does not."))
                Text(t("• لا يوجد خادم VPN بعيد، ولا يتغيّر عنوان IP الخاص بك.", "• There is no remote VPN server and your IP address does not change."))
                Text(t("• لا نجمع ولا نسجّل ولا نبيع أي بيانات من حركة الشبكة.", "• No traffic data is collected, logged or sold."))
                Text(
                    t(
                        "• أسماء المواقع المطلوبة تُرسل إلى مزود DNS المختار (مثل Cloudflare أو Google) عبر DNS العادي غير المشفّر، وتخضع لسياسة خصوصيته.",
                        "• Requested domain names are sent to your chosen DNS provider (e.g. Cloudflare or Google) over standard unencrypted DNS, under its privacy policy.",
                    ),
                )
            }
        },
        confirmButton = {
            if (onAccept != null) {
                TextButton(onClick = onAccept) { Text(t("أوافق وأتابع", "Agree and continue")) }
            } else {
                TextButton(onClick = onDecline) { Text(t("إغلاق", "Close")) }
            }
        },
        dismissButton = if (onAccept == null) null else {
            { TextButton(onClick = onDecline) { Text(t("لا أوافق", "Decline")) } }
        },
    )
}
