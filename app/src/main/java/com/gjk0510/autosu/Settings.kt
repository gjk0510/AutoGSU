package com.gjk0510.autosu

import android.content.Context

/**
 * 应用设置：命令与调度间隔可持久化，供 App 内修改。
 */
object Settings {
    const val PREFS_NAME = "autosu_prefs"
    private const val KEY_COMMAND = "command"
    private const val KEY_INTERVAL_MS = "interval_ms"

    /** 默认命令（占位），用户可在 App 内自行填写。 */
    const val DEFAULT_COMMAND = "echo AutoSU_$(date +%s)"

    /** 默认调度间隔：1 小时。 */
    const val DEFAULT_INTERVAL_MS = 60 * 60 * 1000L

    /** 单次命令执行超时时间。 */
    const val TIMEOUT_MS = 30 * 1000L

    /** su 二进制名称（Magisk 通常已加入 PATH）。 */
    const val SU_BINARY = "su"

    fun getCommand(context: Context): String =
        prefs(context).getString(KEY_COMMAND, DEFAULT_COMMAND) ?: DEFAULT_COMMAND

    fun setCommand(context: Context, command: String) {
        prefs(context).edit().putString(KEY_COMMAND, command.trim()).apply()
    }

    fun getIntervalMs(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS)

    fun setIntervalMs(context: Context, intervalMs: Long) {
        prefs(context).edit().putLong(KEY_INTERVAL_MS, intervalMs).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}