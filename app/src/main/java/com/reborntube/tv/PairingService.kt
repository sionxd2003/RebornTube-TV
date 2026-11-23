package com.reborntube.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PairingService : Service() {
    private var receiver: PairingReceiver? = null
    @Volatile
    private var activeCode: String? = null
    private var lastNotificationText = ""
    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            receiver?.restart()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showNotification("Buscando celulares en la red local…")
        val code = PairingReceiver.getOrCreateCode(this)
        activeCode = code
        receiver = PairingReceiver(this, code, ::updateNotification).also { it.start() }
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedCode = intent?.getStringExtra(EXTRA_PAIRING_CODE)
            ?.takeIf { it.matches(Regex("\\d{6}")) }
        if (requestedCode != null && requestedCode != activeCode) {
            receiver?.stop()
            activeCode = requestedCode
            PairingReceiver.saveCode(this, requestedCode)
            receiver = PairingReceiver(this, requestedCode, ::updateNotification).also { it.start() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        connectivityManager = null
        receiver?.stop()
        receiver = null
        super.onDestroy()
    }

    private fun updateNotification(status: ReceiverStatus) {
        status.pairingCode?.let { activeCode = it }
        val text = status.video?.let { "Video listo: ${it.title}" } ?: status.message
        if (text != lastNotificationText) showNotification(text)
    }

    private fun showNotification(text: String) {
        lastNotificationText = text
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RebornTube TV")
            .setContentText(text)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conexión RebornTube",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene RebornTube TV disponible para conectarse con el celular."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_PAIRING_CODE = "com.reborntube.tv.extra.PAIRING_CODE"
        private const val CHANNEL_ID = "reborntube_tv_pairing"
        private const val NOTIFICATION_ID = 7482
    }
}
