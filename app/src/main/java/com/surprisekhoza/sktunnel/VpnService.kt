package com.surprisekhoza.sktunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService as AndroidVpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import moe.matsuri.lite.Libcore

/**
 * Android VPN Service that creates a TUN interface (no root) and
 * passes it to sing-box via libcore.
 */
class VpnService : AndroidVpnService() {

    private var vpnFd: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var libcoreInstance: Libcore? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val configJson = intent.getStringExtra("config") ?: return START_NOT_STICKY

        // Start foreground with a persistent notification
        startForeground(VPN_NOTIFICATION_ID, createNotification())

        // Launch the VPN setup on a background thread
        scope.launch {
            try {
                // Build the VPN interface via VpnService.Builder
                val builder = Builder()
                    .setSession("SK Tunnel")
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 30)   // tun address
                    .addRoute("0.0.0.0", 0)       // route all traffic
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .setBlocking(true)

                // For apps to bypass VPN (optional, you can leave empty)
                // builder.addDisallowedApplication("com.example.app")

                vpnFd = builder.establish()
                    ?: throw Exception("Failed to establish VPN interface")

                // Pass the file descriptor to sing-box
                val fd = vpnFd!!.fd
                libcoreInstance = Libcore()
                libcoreInstance!!.start(fd, configJson)

            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        // Stop sing-box
        scope.launch {
            try {
                libcoreInstance?.stop()
            } catch (_: Exception) {}
            libcoreInstance = null
            // Close the VPN file descriptor
            try {
                vpnFd?.close()
            } catch (_: Exception) {}
            vpnFd = null
        }
        super.onDestroy()
    }

    /**
     * Create a notification so the service runs in the foreground.
     */
    private fun createNotification(): Notification {
        val channelId = "sk_vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SK Tunnel VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SK Tunnel")
            .setContentText("VPN is running")
            .setSmallIcon(android.R.drawable.ic_menu_share) // placeholder icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val VPN_NOTIFICATION_ID = 1001
    }
}