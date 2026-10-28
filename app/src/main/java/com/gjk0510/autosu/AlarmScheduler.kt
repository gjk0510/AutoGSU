package com.gjk0510.autosu

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

    /** 注册下一次闹钟（先取消已存在的，防止重复注册）。功能关闭时改为取消闹钟。 */
    fun scheduleNext(context: Context) {
        if (!Settings.isEnabled(context)) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context))
            prefs(context).edit().putLong(KEY_NEXT_TRIGGER_WALL, 0L).apply()
            return
        }

        val intervalMs = Settings.getIntervalMs(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        alarmManager.cancel(pi)

        val nextWall = System.currentTimeMillis() + intervalMs
        val elapsedTriggerAt = SystemClock.elapsedRealtime() + intervalMs
        val label = when (Settings.getAlarmMode(context)) {
            AlarmMode.EXACT -> {
                // 精确闹钟：Android 12+ 需要「闹钟和提醒」权限，无权限则退回非精确。
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
                ) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        elapsedTriggerAt,
                        pi
                    )
                    "精确"
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        elapsedTriggerAt,
                        pi
                    )
                    ExecutionLog.append(context, "未授予「闹钟和提醒」权限，精确闹钟退回非精确")
                    "非精确(回退)"
                }
            }
            AlarmMode.ALARM_CLOCK -> {
                val showIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(nextWall, showIntent),
                    pi
                )
                "闹钟"
            }
            else -> {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    elapsedTriggerAt,
                    pi
                )
                "非精确"
            }
        }

        prefs(context).edit().putLong(KEY_NEXT_TRIGGER_WALL, nextWall).apply()

        ExecutionLog.append(context, "已注册下一次${label}闹钟：${intervalMs / 1000} 秒后")
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
        if (!Settings.isEnabled(context)) return
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