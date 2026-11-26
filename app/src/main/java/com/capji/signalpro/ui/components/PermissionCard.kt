package com.capji.signalpro.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji

fun openSystemScreen(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun openAppSettings(context: Context) = openSystemScreen(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

/** Explains and requests what Android needs before it reveals Wi-Fi names and scans. */
@Composable
fun LocationCard(hasPermission: Boolean, locationEnabled: Boolean, onChanged: () -> Unit) {
    if (hasPermission && locationEnabled) return
    val t = LocalLang.current
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        denied = it[Manifest.permission.ACCESS_FINE_LOCATION] != true
        onChanged()
    }
    GlassCard(accent = Capji.Amber) {
        if (!hasPermission) {
            CardTitle(t("إذن الموقع الدقيق مطلوب", "Precise location needed"), Icons.Rounded.LocationOn, Capji.Amber)
            Hint(
                t(
                    "يشترط أندرويد هذا الإذن لإظهار اسم الشبكة وفحص الشبكات المجاورة. لا نقرأ موقعك ولا نخزنه ولا نرسله.",
                    "Android requires it to show the network name and scan nearby networks. Your location is never read, stored or sent.",
                ),
            )
            PrimaryButton(
                t("منح الإذن", "Grant permission"),
                onClick = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                color = Capji.Amber,
            )
            if (denied) {
                Hint(t("إن لم تظهر نافذة الإذن، فعّل «الموقع الدقيق» من إعدادات التطبيق.", "If no prompt appears, enable precise location in app settings."))
                SecondaryButton(
                    t("إعدادات التطبيق", "App settings"),
                    onClick = { openAppSettings(context) },
                    icon = Icons.Rounded.Settings,
                )
            }
        } else {
            CardTitle(t("خدمة الموقع متوقفة", "Location is turned off"), Icons.Rounded.LocationOn, Capji.Amber)
            Hint(t("شغّل الموقع (GPS) من الهاتف حتى يعرض أندرويد بيانات الشبكات.", "Turn on device location so Android can report Wi-Fi data."))
            PrimaryButton(
                t("فتح إعدادات الموقع", "Open location settings"),
                onClick = { openSystemScreen(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                color = Capji.Amber,
            )
        }
    }
}
