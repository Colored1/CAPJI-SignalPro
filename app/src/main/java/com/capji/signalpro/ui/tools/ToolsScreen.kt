@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.capji.signalpro.ui.tools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.signalpro.core.PasswordGenerator
import com.capji.signalpro.core.Strength
import com.capji.signalpro.core.WifiCredentials
import com.capji.signalpro.core.WifiSecurity
import com.capji.signalpro.qr.ConnectAction
import com.capji.signalpro.qr.QrCard
import com.capji.signalpro.qr.WifiConnector
import com.capji.signalpro.ui.boost.VpnDisclosureDialog
import com.capji.signalpro.ui.components.CardTitle
import com.capji.signalpro.ui.components.GlassCard
import com.capji.signalpro.ui.components.Hint
import com.capji.signalpro.ui.components.InfoRow
import com.capji.signalpro.ui.components.LtrStyle
import com.capji.signalpro.ui.components.PrimaryButton
import com.capji.signalpro.ui.components.ScreenColumn
import com.capji.signalpro.ui.components.SecondaryButton
import com.capji.signalpro.ui.components.openAppSettings
import com.capji.signalpro.ui.components.openSystemScreen
import com.capji.signalpro.ui.i18n.Lang
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ToolsScreen(viewModel: ToolsViewModel = viewModel()) {
    val t = LocalLang.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.prefillSsid() }

    ScreenColumn {
        QrGeneratorCard(state, viewModel)
        ScannerCard(state, viewModel)
        PasswordCard(state, viewModel)
        GlassCard(accent = Capji.Emerald) {
            CardTitle(t("الخصوصية", "Privacy"), Icons.Rounded.Shield, Capji.Emerald)
            Hint(t("كلمات المرور تبقى في الذاكرة فقط ولا تُحفظ أو تُرسل.", "Passwords stay in memory only; they are never saved or sent."))
            SecondaryButton(t("إفصاح VPN المحلي", "Local VPN disclosure"), { showDisclosure = true }, Modifier.fillMaxWidth())
        }
        AboutCard()
    }
    if (showDisclosure) VpnDisclosureDialog(onAccept = null, onDecline = { showDisclosure = false })
}

private fun toast(context: android.content.Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()

private fun securityLabel(t: Lang, security: WifiSecurity) = when (security) {
    WifiSecurity.WPA -> "WPA/WPA2"
    WifiSecurity.WPA3 -> "WPA3"
    WifiSecurity.WEP -> "WEP"
    WifiSecurity.OPEN -> t("مفتوحة", "Open")
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) {
    val t = LocalLang.current
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange,
        label = { Text(t("كلمة المرور", "Password")) },
        singleLine = true,
        textStyle = LtrStyle,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = t("إظهار أو إخفاء", "Show or hide"),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QrGeneratorCard(state: ToolsUiState, viewModel: ToolsViewModel) {
    val t = LocalLang.current
    val context = LocalContext.current
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.saveCard() else viewModel.storageDenied()
    }
    GlassCard {
        CardTitle(t("بطاقة QR للواي فاي", "Wi-Fi QR card"), Icons.Rounded.QrCode2)
        Hint(t("يمسحها الضيف فيتصل دون أن تخبره بكلمة المرور.", "Guests scan it to connect without being told the password."))
        OutlinedTextField(
            state.ssid, viewModel::setSsid,
            label = { Text(t("اسم الشبكة (SSID)", "Network name (SSID)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WifiSecurity.entries.forEach {
                FilterChip(
                    selected = state.security == it,
                    onClick = { viewModel.setSecurity(it) },
                    label = { Text(securityLabel(t, it)) },
                )
            }
        }
        if (state.security != WifiSecurity.OPEN) PasswordField(state.password, viewModel::setPassword)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("شبكة مخفية", "Hidden network"), Modifier.weight(1f))
            Switch(state.hidden, viewModel::setHidden)
        }
        PrimaryButton(
            t("إنشاء البطاقة", "Create card"),
            onClick = { viewModel.createCard(t.isArabic) },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.QrCode2,
        )
        state.message?.let { message ->
            val good = message == ToolMessage.SAVED
            Hint(
                when (message) {
                    ToolMessage.SAVED -> t("حُفظت البطاقة في الصور/CAPJI", "Saved to Pictures/CAPJI")
                    ToolMessage.SAVE_FAILED -> t("تعذّر حفظ الصورة", "Could not save the image")
                    ToolMessage.SHARE_FAILED -> t("تعذّرت المشاركة", "Could not share")
                    ToolMessage.STORAGE_DENIED -> t("يلزم إذن التخزين للحفظ", "Storage permission is needed to save")
                    ToolMessage.SSID_REQUIRED -> t("أدخل اسم الشبكة", "Enter the network name")
                    ToolMessage.BAD_PASSWORD -> t(
                        "طول كلمة المرور غير صالح (WPA: من 8 إلى 63؛ WEP: 5 أو 10 أو 13 أو 26)",
                        "Invalid password length (WPA: 8–63; WEP: 5, 10, 13 or 26)",
                    )
                },
                if (good) Capji.Emerald else Capji.Red,
            )
        }
        state.card?.let { card ->
            Image(
                card.asImageBitmap(),
                contentDescription = t("بطاقة QR", "QR card"),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    t("حفظ", "Save"),
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < 29 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        else viewModel.saveCard()
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Download,
                    color = Capji.Emerald,
                )
                SecondaryButton(
                    t("مشاركة", "Share"),
                    onClick = { if (!QrCard.share(context, card)) viewModel.shareFailed() },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Share,
                )
            }
        }
    }
}

@Composable
private fun ScannerCard(state: ToolsUiState, viewModel: ToolsViewModel) {
    val t = LocalLang.current
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        denied = !it
        open = it
    }
    GlassCard(accent = Capji.Violet) {
        CardTitle(t("ماسح رموز الواي فاي", "Wi-Fi QR scanner"), Icons.Rounded.QrCodeScanner, Capji.Violet)
        Hint(t("تُحلَّل صورة الكاميرا داخل الهاتف ولا تُحفظ.", "Camera frames are analysed on the phone and not saved."))
        PrimaryButton(
            t("فتح الكاميرا", "Open camera"),
            onClick = {
                viewModel.clearScan()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) open = true else camera.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.QrCodeScanner,
        )
        if (denied) {
            Hint(t("يلزم إذن الكاميرا للمسح.", "Camera permission is needed to scan."), Capji.Red)
            SecondaryButton(t("إعدادات التطبيق", "App settings"), { openAppSettings(context) })
        }
        if (state.scannedOther) {
            Hint(t("هذا الرمز ليس رمز شبكة واي فاي.", "This code is not a Wi-Fi code."), Capji.Amber)
        }
        state.scanned?.let { ScannedNetwork(it) }
    }
    if (open) {
        QrScannerDialog(
            onResult = {
                open = false
                viewModel.onScanned(it)
            },
            onClose = { open = false },
        )
    }
}

@Composable
private fun ScannedNetwork(network: WifiCredentials) {
    val t = LocalLang.current
    val context = LocalContext.current
    var reveal by remember(network) { mutableStateOf(false) }
    val saveDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        toast(
            context,
            if (it.resultCode == android.app.Activity.RESULT_OK) t("حُفظت الشبكة", "Network saved")
            else t("لم تُحفظ الشبكة", "Network not saved"),
        )
    }
    fun openWifiSettings() {
        if (network.password.isNotEmpty()) WifiConnector.copySecret(context, network.password)
        toast(context, t("نُسخت كلمة المرور، اختر الشبكة والصقها", "Password copied: pick the network and paste it"))
        openSystemScreen(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    }
    InfoRow(t("الشبكة", "Network"), network.ssid)
    InfoRow(t("الحماية", "Security"), securityLabel(t, network.security))
    if (network.password.isNotEmpty()) {
        InfoRow(t("كلمة المرور", "Password"), if (reveal) network.password else "••••••••")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                if (reveal) t("إخفاء", "Hide") else t("إظهار", "Show"),
                onClick = { reveal = !reveal },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                t("نسخ", "Copy"),
                onClick = {
                    WifiConnector.copySecret(context, network.password)
                    toast(context, t("تم النسخ", "Copied"))
                },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ContentCopy,
            )
        }
    }
    PrimaryButton(
        t("اتصال بالشبكة", "Connect"),
        onClick = {
            when (val action = WifiConnector.connect(context, network)) {
                is ConnectAction.SystemDialog ->
                    runCatching { saveDialog.launch(action.intent) }.onFailure { openWifiSettings() }
                ConnectAction.Requested -> toast(
                    context,
                    t("أُرسل الطلب لأندرويد؛ وافق على الإشعار إن ظهر", "Request sent to Android; approve the notification if shown"),
                )
                ConnectAction.Manual -> openWifiSettings()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Rounded.Wifi,
        color = Capji.Emerald,
    )
}

private fun crackTime(t: Lang, seconds: Double): String {
    val year = 31_557_600.0
    fun n(value: Double) = String.format(Locale.US, "%,d", value.toLong())
    return when {
        seconds < 60 -> t("أقل من دقيقة", "under a minute")
        seconds < 3600 -> t("${n(seconds / 60)} دقيقة", "${n(seconds / 60)} minutes")
        seconds < 86_400 -> t("${n(seconds / 3600)} ساعة", "${n(seconds / 3600)} hours")
        seconds < year -> t("${n(seconds / 86_400)} يوم", "${n(seconds / 86_400)} days")
        seconds < year * 1e6 -> t("${n(seconds / year)} سنة", "${n(seconds / year)} years")
        else -> t("أكثر من مليون سنة", "over a million years")
    }
}

@Composable
private fun PasswordCard(state: ToolsUiState, viewModel: ToolsViewModel) {
    val t = LocalLang.current
    val context = LocalContext.current
    val options = state.options
    val pool = PasswordGenerator.characterSets(options).sumOf { it.length }
    val bits = PasswordGenerator.entropyBits(state.generated.length, pool)
    val strength = PasswordGenerator.strength(bits)
    val color by animateColorAsState(
        when (strength) {
            Strength.WEAK -> Capji.Red
            Strength.FAIR -> Capji.Amber
            Strength.STRONG -> Capji.Cyan
            Strength.EXCELLENT -> Capji.Emerald
        },
        label = "strengthColor",
    )
    val meter by animateFloatAsState((bits / 128).toFloat().coerceIn(0.03f, 1f), tween(500), label = "strength")
    GlassCard(accent = Capji.Emerald) {
        CardTitle(t("مولّد كلمات مرور قوية", "Strong password generator"), Icons.Rounded.Key, Capji.Emerald)
        SelectionContainer {
            Text(
                state.generated,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge.merge(LtrStyle),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { meter },
            color = color,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            when (strength) {
                Strength.WEAK -> t("ضعيفة", "Weak")
                Strength.FAIR -> t("متوسطة", "Fair")
                Strength.STRONG -> t("قوية", "Strong")
                Strength.EXCELLENT -> t("ممتازة", "Excellent")
            } + " · ${bits.roundToInt()} bit",
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Hint(
            t("وقت الكسر التقديري: ", "Estimated time to crack: ") +
                crackTime(t, PasswordGenerator.crackSeconds(bits)) +
                t(" (بافتراض مليار محاولة في الثانية)", " (assuming 1 billion guesses per second)"),
        )
        Text(t("الطول: ${options.length}", "Length: ${options.length}"))
        Slider(
            value = options.length.toFloat(),
            onValueChange = {
                val length = it.roundToInt()
                if (length != options.length) viewModel.setOptions(options.copy(length = length))
            },
            valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..32f,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("a-z", options.lowercase) { viewModel.setOptions(options.copy(lowercase = it)) }
            Chip("A-Z", options.uppercase) { viewModel.setOptions(options.copy(uppercase = it)) }
            Chip("0-9", options.digits) { viewModel.setOptions(options.copy(digits = it)) }
            Chip("!@#", options.symbols) { viewModel.setOptions(options.copy(symbols = it)) }
            Chip(t("بدون المتشابهة", "No look-alikes"), options.avoidAmbiguous) {
                viewModel.setOptions(options.copy(avoidAmbiguous = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                t("توليد", "Generate"), viewModel::generatePassword, Modifier.weight(1f),
                Icons.Rounded.Refresh, color = Capji.Emerald,
            )
            SecondaryButton(
                t("نسخ", "Copy"),
                onClick = {
                    WifiConnector.copySecret(context, state.generated)
                    toast(context, t("تم النسخ", "Copied"))
                },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ContentCopy,
            )
        }
        SecondaryButton(
            t("استخدمها في بطاقة QR", "Use in the QR card"),
            onClick = {
                viewModel.useGenerated()
                toast(context, t("أضيفت إلى نموذج البطاقة", "Added to the card form"))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(state.password == state.generated) {
            Hint(t("تذكّر تغيير كلمة المرور في الراوتر أيضاً.", "Remember to set this password on your router too."), Capji.Amber)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(selected = selected, onClick = { onToggle(!selected) }, label = { Text(label) })
}
