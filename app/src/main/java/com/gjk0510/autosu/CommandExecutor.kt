package com.gjk0510.autosu

import android.os.SystemClock
import java.util.concurrent.TimeUnit

/** 单次命令执行结果。 */
data class ExecResult(
    val success: Boolean,
    val exitCode: Int,
    val timedOut: Boolean,
    val durationMs: Long,
    val output: String
) {
    fun summary(): String = when {
        timedOut -> "执行超时：耗时 ${durationMs}ms"
        success -> "执行成功：exit=$exitCode，耗时 ${durationMs}ms"
        else -> "执行失败：exit=$exitCode，耗时 ${durationMs}ms"
    }
}

/**
 * 通过 `su -c` 执行命令，带超时与输出收集。
 */
object CommandExecutor {

    fun execute(
        command: String,
        timeoutMs: Long = Settings.TIMEOUT_MS
    ): ExecResult {
        val start = SystemClock.elapsedRealtime()
        val process = try {
            ProcessBuilder(Settings.SU_BINARY, "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            return ExecResult(
                success = false,
                exitCode = -1,
                timedOut = false,
                durationMs = SystemClock.elapsedRealtime() - start,
                output = "无法启动 ${Settings.SU_BINARY}: ${e.message}"
            )
        }

        // 在独立线程读取输出，避免进程输出缓冲区写满导致阻塞。
        val output = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(output) { output.appendLine(line) }
                }
            } catch (_: Exception) {
            }
        }
        reader.start()

        // waitFor(long, TimeUnit) 需要 API 26+，本项目 minSdk 26，可直接使用。
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        val duration = SystemClock.elapsedRealtime() - start

        if (!finished) {
            process.destroyForcibly()
            reader.join(2000)
            return ExecResult(
                success = false,
                exitCode = -1,
                timedOut = true,
                durationMs = duration,
                output = "执行超时（${timeoutMs}ms），已强制结束。\n${output}"
            )
        }

        reader.join(2000)
        val exitCode = process.exitValue()
        return ExecResult(
            success = exitCode == 0,
            exitCode = exitCode,
            timedOut = false,
            durationMs = duration,
            output = output.toString()
        )
    }
}