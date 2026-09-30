package com.jev.probe.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.jev.probe.MainActivity
import com.jev.probe.core.Prefs

/**
 * A minimal foreground service whose only job is to keep the app process at
 * foreground importance so MIUI/HyperOS "Greezer" does not freeze the
 * accessibility service (which otherwise dies within seconds — see P1 report).
 * Not a full fix on its own: the user must also grant autostart / no battery
 * restriction, but this holds the process while the app is set up and running.
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        val channelId = "jev_keepalive"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "聊天军师悬浮入口", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle("聊天军师悬浮入口已开启")
            .setContentText("点击悬浮球才识别，确认后分析；发送由你决定")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).putExtra("recoverOverlay", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_rotate, "恢复悬浮入口",
                PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).putExtra("recoverOverlay", true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
            .build()
        startForeground(1, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs(this).enabled) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
