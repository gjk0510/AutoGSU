package com.gjk0510.autosu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

/**
 * 常驻通知模式：前台服务内动态注册解锁/关屏广播。
 *
 * USER_PRESENT / SCREEN_OFF 属于受保护广播，Manifest 静态声明在 MIUI 上会被
 * 「Background execution not allowed」拦下（bugreport 已证实），必须运行时
 * registerReceiver() 才能收到。前台服务保证进程存活，空闲时无 CPU 消耗，
 * 代价仅为一条常驻通知（系统强制要求，无法去掉）。
 */
class EventForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "autosu_event_listen"
        private const val NOTIFICATION_ID = 1001

        /** 按当前监听模式拉起/停止服务。 */
        fun ensure(context: Context) {
            if (Settings.getEventMode(context) == EventMode.NOTIFICATION) {
                start(context)
            } else {
                stop(context)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, EventForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EventForegroundService::class.java))
        }
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            EventTasks.dispatch(context, TriggerType.UNLOCK, goAsync())
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            EventTasks.dispatch(context, TriggerType.SCREEN_OFF, goAsync())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), receiverFlags())
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), receiverFlags())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    private fun receiverFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Context.RECEIVER_NOT_EXPORTED else 0

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "解锁/关屏监听",
            NotificationManager.IMPORTANCE_MIN
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("AutoGSU")
            .setContentText("解锁/关屏监听运行中")
            .setOngoing(true)
            .build()
    }
}
