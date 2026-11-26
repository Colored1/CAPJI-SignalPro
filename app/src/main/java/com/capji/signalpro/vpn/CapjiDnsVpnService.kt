package com.capji.signalpro.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.capji.signalpro.CapjiApp
import com.capji.signalpro.MainActivity
import com.capji.signalpro.R
import com.capji.signalpro.core.DnsProvider
import com.capji.signalpro.data.Prefs

/**
 * Local DNS-only tunnel. Only the single virtual resolver address is routed into the
 * interface, so all other traffic keeps using the normal network and never passes
 * through this app. There is no remote VPN server.
 */
class CapjiDnsVpnService : VpnService() {

    private var tunnel: DnsTunnel? = null
    private var wifiLock: WifiLatencyLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown(null)
            return START_NOT_STICKY
        }
        // Also reached by always-on VPN and by system restarts: settings come from storage.
        val prefs = Prefs(this)
        val provider = prefs.selectedProvider()
        // Must be posted before any early exit to satisfy startForegroundService().
        showNotification(provider, prefs.arabic)
        // The tunnel never starts unless the in-app disclosure was accepted.
        if (!prefs.disclosureAccepted) {
            shutdown(null)
            return START_NOT_STICKY
        }

        val current = tunnel
        if (current != null) {
            current.upstream = provider.servers
            publish(provider)
            return START_STICKY
        }

        val descriptor = runCatching {
            Builder()
                .setSession("CAPJI Signal Pro DNS")
                .addAddress(VIRTUAL_ADDRESS, 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
                // Without this, address families that have no route here would be blocked.
                .allowFamily(OsConstants.AF_INET)
                .allowFamily(OsConstants.AF_INET6)
                .allowBypass()
                .setMtu(DnsTunnel.MTU)
                .setBlocking(false)
                .setConfigureIntent(openAppIntent())
                .also { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.setMetered(false) }
                .establish()
        }.getOrNull()

        if (descriptor == null) {
            shutdown(VpnError.ESTABLISH_FAILED)
            return START_NOT_STICKY
        }

        wifiLock = WifiLatencyLock(this).also { it.acquire() }
        tunnel = DnsTunnel(
            service = this,
            tun = descriptor,
            upstream = provider.servers,
            onAnswered = { VpnState.update { it.copy(answeredQueries = it.answeredQueries + 1) } },
            onFailure = { ContextCompat.getMainExecutor(this).execute { shutdown(VpnError.ESTABLISH_FAILED) } },
        ).also { it.start() }
        publish(provider)
        return START_STICKY
    }

    private fun publish(provider: DnsProvider) {
        VpnState.update {
            VpnStatus(
                running = true,
                providerName = provider.name,
                server = provider.primary,
                answeredQueries = if (it.running) it.answeredQueries else 0,
                wifiLockHeld = wifiLock?.isHeld == true,
                startedAtElapsed = if (it.running) it.startedAtElapsed else SystemClock.elapsedRealtime(),
            )
        }
    }

    override fun onRevoke() {
        // Another VPN took over, or the user disconnected us from system settings.
        shutdown(VpnError.REVOKED)
    }

    override fun onDestroy() {
        release()
        VpnState.update { VpnStatus(error = it.error) }
        super.onDestroy()
    }

    private fun release() {
        tunnel?.stop()
        tunnel = null
        wifiLock?.release()
        wifiLock = null
    }

    private fun shutdown(error: VpnError?) {
        release()
        VpnState.update { VpnStatus(error = error) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun showNotification(provider: DnsProvider, arabic: Boolean) {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CapjiDnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CapjiApp.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_stat_capji)
            .setContentTitle(if (arabic) "تسريع كابجي مفعّل" else "CAPJI boost is on")
            .setContentText(
                if (arabic) "DNS فقط عبر ${provider.name} (${provider.primary})"
                else "DNS only via ${provider.name} (${provider.primary})",
            )
            .setContentIntent(openAppIntent())
            .addAction(0, if (arabic) "إيقاف" else "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else 0
        // If the system refuses foreground status the tunnel still works while bound.
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
    }

    companion object {
        const val VIRTUAL_ADDRESS = "10.215.173.1"
        const val VIRTUAL_DNS = "10.215.173.2"
        private const val ACTION_START = "com.capji.signalpro.vpn.START"
        private const val ACTION_STOP = "com.capji.signalpro.vpn.STOP"
        private const val NOTIFICATION_ID = 4101

        /** Starts the tunnel, or switches its resolver if it is already running. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, CapjiDnsVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CapjiDnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
