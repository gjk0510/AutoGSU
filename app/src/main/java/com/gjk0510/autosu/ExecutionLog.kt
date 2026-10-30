package com.gjk0510.autosu

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 执行日志：同时写入 Logcat 与应用私有目录内的文件，方便调试。
 *
 * 全局日志用于系统级消息；每个任务各自写入独立文件，便于在卡片与详情页查看。
 */
object ExecutionLog {
    private const val TAG = "AutoSU"
    private const val GLOBAL_FILE = "autosu_exec.log"
    private const val MAX_FILE_SIZE = 256 * 1024L // 256 KB，超限时保留末尾一半
    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun append(context: Context, message: String) = write(context, GLOBAL_FILE, message)

    fun readLast(context: Context, maxLines: Int = 200): String =
        read(context, GLOBAL_FILE, maxLines)

    fun appendTask(context: Context, taskId: String, message: String) =
        write(context, taskFile(taskId), message)

    fun readLastTask(context: Context, taskId: String, maxLines: Int = 200): String =
        read(context, taskFile(taskId), maxLines)

    fun clear(context: Context) {
        File(context.filesDir, GLOBAL_FILE).delete()
    }

    fun clearTask(context: Context, taskId: String) {
        File(context.filesDir, taskFile(taskId)).delete()
    }

    private fun taskFile(taskId: String): String = "task_${taskId}.log"

    private fun write(context: Context, fileName: String, message: String) {
        val line = "${FORMATTER.format(LocalDateTime.now())} $message"
        Log.i(TAG, line)

        val file = File(context.filesDir, fileName)
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

    private fun read(context: Context, fileName: String, maxLines: Int): String {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return "暂无执行日志"
        return try {
            val lines = file.readLines()
            if (lines.isEmpty()) "暂无执行日志" else lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }
}