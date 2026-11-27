@file:OptIn(ExperimentalMaterial3Api::class)

package com.capji.signalpro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.signalpro.Contact
import com.capji.signalpro.core.UpdateInfo
import com.capji.signalpro.ui.analyzer.AnalyzerScreen
import com.capji.signalpro.ui.boost.BoostScreen
import com.capji.signalpro.ui.components.CapjiSignalLogo
import com.capji.signalpro.ui.components.GlassCard
import com.capji.signalpro.ui.i18n.Lang
import com.capji.signalpro.ui.i18n.LocalLang
import com.capji.signalpro.ui.radar.RadarScreen
import com.capji.signalpro.ui.splash.SplashScreen
import com.capji.signalpro.ui.theme.Capji
import com.capji.signalpro.ui.theme.CapjiTheme
import com.capji.signalpro.ui.tools.ToolsScreen

@Composable
fun CapjiRoot(viewModel: MainViewModel = viewModel()) {
    val arabic by viewModel.arabic.collectAsStateWithLifecycle()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(
        LocalLang provides Lang(arabic),
        LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        CapjiTheme {
            Crossfade(splashDone, animationSpec = tween(500), label = "root") { done ->
                if (done) MainScreen(viewModel) else SplashScreen { splashDone = true }
            }
        }
    }
}

private enum class Tab(val ar: String, val en: String) {
    BOOST("التسريع", "Boost"),
    RADAR("الرادار", "Radar"),
    ANALYZER("المحلل", "Analyzer"),
    TOOLS("الأدوات", "Tools"),
}

@Composable
private fun MainScreen(viewModel: MainViewModel) {
    val t = LocalLang.current
    val context = LocalContext.current
    val remote by viewModel.remote.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var dismissedAnnouncement by rememberSaveable { mutableStateOf("") }
    var dismissedUpdate by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selected != 0) { selected = 0 }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1328), Capji.Navy, Capji.Navy))),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Capji.Text,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CapjiSignalLogo(size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("CAPJI Signal Pro", fontWeight = FontWeight.Black)
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::toggleLanguage) {
                            Icon(Icons.Rounded.Language, contentDescription = t("تغيير اللغة", "Change language"))
                            Spacer(Modifier.width(6.dp))
                            Text(t("EN", "عربي"), fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xF20C1324)) {
                    Tab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = {
                                Icon(
                                    when (tab) {
                                        Tab.BOOST -> Icons.Rounded.Bolt
                                        Tab.RADAR -> Icons.Rounded.Radar
                                        Tab.ANALYZER -> Icons.Rounded.Insights
                                        Tab.TOOLS -> Icons.Rounded.QrCode2
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(t(tab.ar, tab.en)) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val announcement = remote.announcement
                if (announcement != null && announcement != dismissedAnnouncement) {
                    GlassCard(Modifier.padding(horizontal = 16.dp), accent = Capji.Violet) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Campaign, contentDescription = null, tint = Capji.Violet)
                            Text(announcement, Modifier.weight(1f).padding(horizontal = 12.dp))
                            IconButton(onClick = { dismissedAnnouncement = announcement }) {
                                Icon(Icons.Rounded.Close, contentDescription = t("إغلاق", "Dismiss"))
                            }
                        }
                    }
                }
                AnimatedContent(
                    targetState = Tab.entries[selected],
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith
                            fadeOut(tween(140))
                    },
                    label = "tabs",
                    modifier = Modifier.weight(1f),
                ) { tab ->
                    when (tab) {
                        Tab.BOOST -> BoostScreen()
                        Tab.RADAR -> RadarScreen()
                        Tab.ANALYZER -> AnalyzerScreen()
                        Tab.TOOLS -> ToolsScreen()
                    }
                }
            }
        }
    }

    remote.update?.let { update ->
        if (update.forced || !dismissedUpdate) {
            UpdateDialog(
                update,
                onUpdate = { Contact.open(context, update.url) },
                onLater = { dismissedUpdate = true },
            )
        }
    }
}

@Composable
private fun UpdateDialog(update: UpdateInfo, onUpdate: () -> Unit, onLater: () -> Unit) {
    val t = LocalLang.current
    AlertDialog(
        onDismissRequest = { if (!update.forced) onLater() },
        title = { Text(t("تحديث جديد متوفر", "Update available")) },
        text = {
            Text(
                update.message.ifEmpty {
                    t("حدّث التطبيق للحصول على أحدث التحسينات.", "Update to get the latest improvements.")
                },
            )
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text(t("تحديث الآن", "Update now")) } },
        dismissButton = if (update.forced) null else {
            { TextButton(onClick = onLater) { Text(t("لاحقاً", "Later")) } }
        },
    )
}
