package com.partyos.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.partyos.tv.R
import com.partyos.tv.partyRuntime
import kotlinx.coroutines.launch

/** Keeps the party server alive while PARTY OS is in the background, with Wi-Fi and CPU held awake. */
class PartyService : LifecycleService() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Party server", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("PARTY OS is hosting")
            .setContentText("Phones can join from the TV's QR code")
            .setSmallIcon(R.drawable.ic_party)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "partyos:wifi").apply { acquire() }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "partyos:server").apply { acquire() }
        lifecycleScope.launch {
            runCatching { partyRuntime.start() }.onFailure { android.util.Log.e("PartyService", "could not start the party server", it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        partyRuntime.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "party"
        fun start(context: Context) = context.startForegroundService(Intent(context, PartyService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, PartyService::class.java))
    }
}
