package com.gjk0510.autosu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * 接收闹钟事件：在后台线程执行用户在 App 内配置的命令，完成后注册下一次闹钟。
 */
class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 开局先记一条日志：只要收到广播就会留下记录，用于区分"广播没送达"和"送达但执行失败"。
        ExecutionLog.append(context, "收到闹钟广播，开始处理")

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
                    val result = CommandExecutor.execute(Settings.getCommand(context))
                    ExecutionLog.append(context, result.summary())
                    if (result.output.isNotBlank()) {
                        ExecutionLog.append(context, "输出：${result.output.trim()}")
                    }
                } finally {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            } catch (e: Exception) {
                ExecutionLog.append(context, "执行异常：${e.message}")
            } finally {
                // 无论成功失败都注册下一次闹钟，保证任务链不断。
                AlarmScheduler.scheduleNext(context)
                pendingResult.finish()
            }
        }.start()
    }
}