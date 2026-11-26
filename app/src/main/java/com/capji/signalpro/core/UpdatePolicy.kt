package com.capji.signalpro.core

import java.net.URI

data class UpdateInfo(val forced: Boolean, val message: String, val url: String)

object UpdatePolicy {
    const val STORE_URL = "https://play.google.com/store/apps/details?id=com.capji.signalpro"

    /**
     * Only Google Play links are followed: Play policy forbids apps from updating
     * themselves from any other source.
     */
    fun safeUrl(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val allowed = uri != null &&
            ((uri.scheme == "https" && uri.host == "play.google.com") || uri.scheme == "market")
        return if (allowed) url.trim() else STORE_URL
    }

    fun evaluate(
        installedCode: Long,
        latestCode: Long,
        force: Boolean,
        message: String,
        url: String,
    ): UpdateInfo? =
        if (latestCode <= installedCode) null else UpdateInfo(force, message.trim(), safeUrl(url))
}
