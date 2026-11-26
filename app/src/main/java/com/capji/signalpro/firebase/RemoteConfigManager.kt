package com.capji.signalpro.firebase

import android.content.Context
import com.capji.signalpro.BuildConfig
import com.capji.signalpro.core.UpdateInfo
import com.capji.signalpro.core.UpdatePolicy
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteState(val update: UpdateInfo? = null, val announcement: String? = null)

/**
 * Optional remote control. Every call is a no-op when the app was built without
 * `google-services.json`.
 */
object RemoteConfigManager {
    const val TOPIC_ALL = "all_users"

    private val mutable = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = mutable.asStateFlow()

    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun initialize(context: Context) {
        if (!isAvailable(context)) return
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
            val config = FirebaseRemoteConfig.getInstance()
            config.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 300 else 3600)
                    .build(),
            )
            config.setDefaultsAsync(
                mapOf<String, Any>(
                    "latest_version_code" to BuildConfig.VERSION_CODE.toLong(),
                    "force_update" to false,
                    "update_message" to "",
                    "update_url" to UpdatePolicy.STORE_URL,
                    "announcement_banner_enabled" to false,
                    "announcement_text" to "",
                ),
            ).addOnCompleteListener {
                publish(config)
                config.fetchAndActivate().addOnCompleteListener { publish(config) }
            }
        }
    }

    private fun publish(config: FirebaseRemoteConfig) {
        val text = config.getString("announcement_text").trim()
        mutable.value = RemoteState(
            update = UpdatePolicy.evaluate(
                installedCode = BuildConfig.VERSION_CODE.toLong(),
                latestCode = config.getLong("latest_version_code"),
                force = config.getBoolean("force_update"),
                message = config.getString("update_message"),
                url = config.getString("update_url"),
            ),
            announcement = text.takeIf { config.getBoolean("announcement_banner_enabled") && it.isNotEmpty() },
        )
    }
}
