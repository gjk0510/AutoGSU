package com.gjk0510.autosu

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * 闹钟调度：注册 / 取消 / 查询下一次定时任务。
 *
 * 使用 setAndAllowWhileIdle + ELAPSED_REALTIME_WAKEUP，属于非精确闹钟：
 * 在 Doze 下仍可触发（受系统频率限制），且不需要精确闹钟权限。
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 1001
    private const val KEY_NEXT_TRIGGER_WALL = "next_trigger_wall"

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CommandReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 注册下一次闹钟（先取消已存在的，防止重复注册）。 */
    fun scheduleNext(context: Context) {
        val intervalMs = Settings.getIntervalMs(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        alarmManager.cancel(pi)

        val triggerAt = SystemClock.elapsedRealtime() + intervalMs
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pi
        )

        val nextWall = System.currentTimeMillis() + intervalMs
        prefs(context).edit().putLong(KEY_NEXT_TRIGGER_WALL, nextWall).apply()

        ExecutionLog.append(context, "已注册下一次闹钟：${intervalMs / 1000} 秒后")
    }

    /** 取消已注册的闹钟。 */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
        prefs(context).edit().putLong(KEY_NEXT_TRIGGER_WALL, 0L).apply()
        ExecutionLog.append(context, "已取消闹钟")
    }

    /**
     * 若未注册或预定的执行时间已过，则重新注册（用于 App 启动 / 开机后的兜底恢复）。
     * 若下次执行时间仍在未来，则认为闹钟仍然有效，避免每次打开 App 都重置计时。
     */
    fun ensureScheduled(context: Context) {
        val nextWall = prefs(context).getLong(KEY_NEXT_TRIGGER_WALL, 0L)
        if (nextWall == 0L || nextWall <= System.currentTimeMillis()) {
            scheduleNext(context)
        }
    }

    fun getNextTriggerWall(context: Context): Long =
        prefs(context).getLong(KEY_NEXT_TRIGGER_WALL, 0L)

    private fun prefs(context: Context) =
        context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
}