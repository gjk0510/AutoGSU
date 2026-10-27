package com.gjk0510.autosu

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 执行日志：同时写入 Logcat 与应用私有目录内的文件，方便调试。
 */
object ExecutionLog {
    private const val TAG = "AutoSU"
    private const val FILE_NAME = "autosu_exec.log"
    private const val MAX_FILE_SIZE = 256 * 1024L // 256 KB，超限时保留末尾一半
    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun append(context: Context, message: String) {
        val line = "${FORMATTER.format(LocalDateTime.now())} $message"
        Log.i(TAG, line)

        val file = File(context.filesDir, FILE_NAME)
        try {
            synchronized(this) {
                file.appendText(line + "\n")
                if (file.length() > MAX_FILE_SIZE) {
                    val bytes = file.readBytes()
                    file.writeBytes(bytes.copyOfRange(bytes.size / 2, bytes.size))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "写入日志文件失败: ${e.message}")
        }
    }

    fun readLast(context: Context, maxLines: Int = 200): String {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return "暂无执行日志"
        return try {
            val lines = file.readLines()
            if (lines.isEmpty()) "暂无执行日志" else lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}