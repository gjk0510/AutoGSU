package com.gjk0510.autosu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * 接收闹钟事件：在后台线程执行对应任务的命令，完成后注册该任务的下一次闹钟。
 */
class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID)
        if (taskId == null) {
            ExecutionLog.append(context, "收到无任务 ID 的广播，已忽略")
            return
        }
        val task = TaskStore.get(context, taskId)
        if (task == null) {
            ExecutionLog.append(context, "收到未知任务的广播（可能已删除），已忽略")
            return
        }

        // 开局先记一条日志：只要收到广播就会留下记录，用于区分"广播没送达"和"送达但执行失败"。
        ExecutionLog.appendTask(context, taskId, "收到闹钟广播，开始处理")

        // goAsync() 将广播处理移到后台线程，避免阻塞主线程导致 ANR。
        val pendingResult = goAsync()
        Thread {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AutoSU:exec"
                )
                try {
                    // 设置硬超时上限：命令结束即释放，不长期持有 WakeLock。
                    wakeLock.acquire(Settings.TIMEOUT_MS + 5_000L)
                    val result = CommandExecutor.execute(task.command)
                    ExecutionLog.appendTask(context, taskId, result.summary())
                    if (result.output.isNotBlank()) {
                        ExecutionLog.appendTask(context, taskId, "输出：${result.output.trim()}")
                    }
                } finally {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            } catch (e: Exception) {
                ExecutionLog.appendTask(context, taskId, "执行异常：${e.message}")
            } finally {
                // 无论成功失败都注册下一次闹钟，保证任务链不断。
                AlarmScheduler.schedule(context, task)
                pendingResult.finish()
            }
        }.start()
    }
}