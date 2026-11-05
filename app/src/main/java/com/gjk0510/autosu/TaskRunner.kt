package com.gjk0510.autosu

import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager

/**
 * 事件型触发（开机 / 关机 / 解锁 / 关屏）共用的执行入口：
 * 在后台线程顺序执行一批命令，统一处理 WakeLock 与日志写入。
 */
object TaskRunner {

    /**
     * 在广播接收器里执行 [tasks]；[pendingResult] 用于跨线程完成广播（goAsync）。
     * 在关屏等休眠场景下短暂持有 PARTIAL_WAKE_LOCK 提高命令执行成功率。
     */
    fun run(context: Context, tasks: List<Task>, pendingResult: BroadcastReceiver.PendingResult) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoSU:event"
        )
        Thread {
            try {
                // 事件型任务通常只有一条，设置硬上限避免长期持有 WakeLock。
                wakeLock.acquire(Settings.TIMEOUT_MS + 5_000L)
                tasks.forEach { task -> runOne(context, task) }
            } catch (e: Exception) {
                ExecutionLog.append(context, "事件执行异常：${e.message}")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }.start()
    }

    private fun runOne(context: Context, task: Task) {
        try {
            val result = CommandExecutor.execute(task.command)
            ExecutionLog.appendTask(context, task.id, result.summary())
            if (result.output.isNotBlank()) {
                ExecutionLog.appendTask(context, task.id, "输出：${result.output.trim()}")
            }
        } catch (e: Exception) {
            ExecutionLog.appendTask(context, task.id, "执行异常：${e.message}")
        }
    }
}