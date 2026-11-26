package com.capji.signalpro

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Developer attribution and contact links. */
object Contact {
    const val COPYRIGHT_AR = "جميع الحقوق محفوظة لـ CAPJI © 2026"
    const val COPYRIGHT_EN = "All rights reserved © 2026 CAPJI"

    const val PHONE = "+967772678571"
    const val INSTAGRAM = "https://instagram.com/axh.d"
    const val TIKTOK = "https://tiktok.com/@c1fq_7"
    const val WHATSAPP = "https://wa.me/967772678571"
    const val CALL = "tel:$PHONE"

    /** Opens a link or the dialer. ACTION_DIAL needs no call permission. */
    fun open(context: Context, link: String): Boolean {
        val action = if (link.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW
        return try {
            context.startActivity(Intent(action, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
