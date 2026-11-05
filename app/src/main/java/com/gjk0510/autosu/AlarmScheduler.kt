package com.gjk0510.autosu

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * 闹钟调度：为每个任务注册 / 取消 / 查询下一次定时触发。
 *
 * 使用 setAndAllowWhileIdle + ELAPSED_REALTIME_WAKEUP，属于非精确闹钟：
 * 在 Doze 下仍可触发（受系统频率限制），且不需要精确闹钟权限。
 */
object AlarmScheduler {

    const val EXTRA_TASK_ID = "task_id"

    private const val KEY_NEXT_WALL_PREFIX = "next_trigger_wall_"

    private fun requestCode(taskId: String): Int = taskId.hashCode()

    private fun pendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, CommandReceiver::class.java)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 为单个任务注册下一次闹钟；模式为 OFF 时改为取消。 */
    fun schedule(context: Context, task: Task) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, task.id)

        // 事件型任务（开机/关机/解锁/关屏）不参与闹钟调度。
        if (task.trigger != TriggerType.SCHEDULE) {
            alarmManager.cancel(pi)
            setNextTriggerWall(context, task.id, 0L)
            return
        }

        if (task.alarmMode == AlarmMode.OFF) {
            alarmManager.cancel(pi)
            setNextTriggerWall(context, task.id, 0L)
            return
        }

        alarmManager.cancel(pi)

        val intervalMs = if (task.intervalMs <= 0L) Settings.DEFAULT_INTERVAL_MS else task.intervalMs
        val nextWall = System.currentTimeMillis() + intervalMs
        val elapsedTriggerAt = SystemClock.elapsedRealtime() + intervalMs
        val label = when (task.alarmMode) {
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
                    ExecutionLog.appendTask(context, task.id, "未授予「闹钟和提醒」权限，精确闹钟退回非精确")
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

        setNextTriggerWall(context, task.id, nextWall)
        ExecutionLog.appendTask(context, task.id, "已注册下一次${label}闹钟：${intervalMs / 1000} 秒后")
    }

    /** 取消单个任务的闹钟。 */
    fun cancel(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, taskId))
        setNextTriggerWall(context, taskId, 0L)
        ExecutionLog.appendTask(context, taskId, "已取消闹钟")
    }

    /** 为所有定时任务重新注册闹钟（用于开机后恢复）。 */
    fun scheduleAll(context: Context) {
        TaskStore.getAll(context)
            .filter { it.trigger == TriggerType.SCHEDULE }
            .forEach { schedule(context, it) }
    }

    /**
     * 若任务未注册或预定的执行时间已过，则重新注册（用于 App 启动 / 开机后的兜底恢复）。
     * 若下次执行时间仍在未来，则认为闹钟仍然有效，避免每次打开 App 都重置计时。
     */
    fun ensureScheduled(context: Context) {
        TaskStore.getAll(context).forEach { task ->
            if (task.trigger != TriggerType.SCHEDULE) return@forEach
            if (task.alarmMode == AlarmMode.OFF) return@forEach
            val nextWall = getNextTriggerWall(context, task.id)
            if (nextWall == 0L || nextWall <= System.currentTimeMillis()) {
                schedule(context, task)
            }
        }
    }

    fun getNextTriggerWall(context: Context, taskId: String): Long =
        prefs(context).getLong(KEY_NEXT_WALL_PREFIX + taskId, 0L)

    private fun setNextTriggerWall(context: Context, taskId: String, value: Long) {
        prefs(context).edit().putLong(KEY_NEXT_WALL_PREFIX + taskId, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
}