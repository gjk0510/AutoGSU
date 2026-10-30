package com.gjk0510.autosu

import android.content.Context

/** 闹钟模式：关闭 / 三种闹钟类型（非精确 / 精确 / 闹钟）。 */
enum class AlarmMode {
    OFF,
    INEXACT,
    EXACT,
    ALARM_CLOCK;

    companion object {
        fun fromName(name: String?): AlarmMode =
            values().firstOrNull { it.name == name } ?: INEXACT
    }
}

/**
 * 全局设置：仅保留常量与旧版单命令配置的读取（用于升级迁移）。
 */
object Settings {
    const val PREFS_NAME = "autosu_prefs"

    private const val KEY_COMMAND = "command"
    private const val KEY_INTERVAL_MS = "interval_ms"
    private const val KEY_ALARM_MODE = "alarm_mode"
    private const val KEY_ENABLED = "enabled" // 旧版开关，仅用于读取迁移

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

    fun getIntervalMs(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS)

    fun getAlarmMode(context: Context): AlarmMode {
        val p = prefs(context)
        val name = p.getString(KEY_ALARM_MODE, null)
        if (name != null) return AlarmMode.fromName(name)
        // 旧版 boolean 开关迁移：关闭 → OFF，否则默认非精确。
        return if (p.getBoolean(KEY_ENABLED, true)) AlarmMode.INEXACT else AlarmMode.OFF
    }

    /** 是否保存过旧版单命令配置（决定是否需要迁移）。 */
    fun hasLegacyConfig(context: Context): Boolean {
        val p = prefs(context)
        return p.contains(KEY_COMMAND) || p.contains(KEY_INTERVAL_MS) ||
            p.contains(KEY_ALARM_MODE) || p.contains(KEY_ENABLED)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}