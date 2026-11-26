package com.capji.signalpro.ui.tools

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.capji.signalpro.BuildConfig
import com.capji.signalpro.Contact
import com.capji.signalpro.ui.components.CapjiSignalLogo
import com.capji.signalpro.ui.components.GlassCard
import com.capji.signalpro.ui.components.Hint
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.theme.Capji

@Composable
fun AboutCard() {
    val t = LocalLang.current
    GlassCard(accent = Capji.Violet) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CapjiSignalLogo(size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                t("حول كابجي وتواصل مع المطوّر", "About CAPJI & contact the developer"),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Hint("CAPJI Signal Pro ${BuildConfig.VERSION_NAME}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandButton(t("إنستغرام", "Instagram"), Icons.Rounded.PhotoCamera, Color(0xFFE1306C), Contact.INSTAGRAM, Modifier.weight(1f))
            BrandButton(t("تيك توك", "TikTok"), Icons.Rounded.MusicNote, Color(0xFF25F4EE), Contact.TIKTOK, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandButton(t("واتساب", "WhatsApp"), Icons.Rounded.Forum, Color(0xFF25D366), Contact.WHATSAPP, Modifier.weight(1f))
            BrandButton(t("اتصال مباشر", "Call"), Icons.Rounded.Call, Capji.Cyan, Contact.CALL, Modifier.weight(1f))
        }
        Text(
            t(Contact.COPYRIGHT_AR, Contact.COPYRIGHT_EN),
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Capji.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BrandButton(label: String, icon: ImageVector, color: Color, link: String, modifier: Modifier) {
    val t = LocalLang.current
    val context = LocalContext.current
    Button(
        onClick = {
            if (!Contact.open(context, link)) {
                Toast.makeText(context, t("لا يوجد تطبيق لفتح الرابط", "No app can open this link"), Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Capji.Navy),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
