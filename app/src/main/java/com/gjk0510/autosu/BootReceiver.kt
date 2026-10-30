package com.gjk0510.autosu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机完成广播：手机重启后重新注册定时任务。
 *
 * 注意：重启会清空基于 ELAPSED_REALTIME 的闹钟，此处必须无条件重新注册，
 * 不能用 ensureScheduled（其调度标记在重启后仍为“已调度”，会导致漏注册）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}