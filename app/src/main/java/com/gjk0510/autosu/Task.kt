package com.gjk0510.autosu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 触发方式：决定一张卡片在什么事件下执行命令。 */
enum class TriggerType {
    /** 定时：按间隔重复执行。 */
    SCHEDULE,
    /** 开机：每次开机后执行一次。 */
    BOOT,
    /** 关机：尽力而为（多数机型收不到 ACTION_SHUTDOWN）。 */
    SHUTDOWN,
    /** 解锁：解锁进入桌面后执行一次（ACTION_USER_PRESENT）。 */
    UNLOCK,
    /** 关屏：屏幕关闭时执行一次（ACTION_SCREEN_OFF）。 */
    SCREEN_OFF;

    companion object {
        fun fromName(name: String?): TriggerType =
            values().firstOrNull { it.name == name } ?: SCHEDULE
    }
}

/** 单个任务（主页上的一张卡片）。 */
data class Task(
    val id: String,
    val name: String,
    val command: String,
    val trigger: TriggerType,
    /** 统一开关：事件型任务以此控制是否执行；定时任务仍由 alarmMode 控制。 */
    val enabled: Boolean,
    val intervalMs: Long,
    val alarmMode: AlarmMode,
    val createdAt: Long
) {
    companion object {
        /** 新建一个未调度的任务卡片；触发方式决定默认名称与初始开关。 */
        fun new(trigger: TriggerType = TriggerType.SCHEDULE): Task = Task(
            id = UUID.randomUUID().toString(),
            name = defaultName(trigger),
            command = Settings.DEFAULT_COMMAND,
            trigger = trigger,
            enabled = true,
            intervalMs = Settings.DEFAULT_INTERVAL_MS,
            alarmMode = if (trigger == TriggerType.SCHEDULE) AlarmMode.OFF else AlarmMode.INEXACT,
            createdAt = System.currentTimeMillis()
        )

        private fun defaultName(trigger: TriggerType): String = when (trigger) {
            TriggerType.SCHEDULE -> "定时任务"
            TriggerType.BOOT -> "开机任务"
            TriggerType.SHUTDOWN -> "关机任务"
            TriggerType.UNLOCK -> "解锁任务"
            TriggerType.SCREEN_OFF -> "关屏任务"
        }

        fun fromJson(o: JSONObject): Task = Task(
            id = o.getString("id"),
            name = o.optString("name", "任务"),
            command = o.optString("command", Settings.DEFAULT_COMMAND),
            trigger = TriggerType.fromName(o.optString("trigger")),
            enabled = o.optBoolean("enabled", true),
            intervalMs = o.optLong("interval_ms", Settings.DEFAULT_INTERVAL_MS),
            alarmMode = AlarmMode.fromName(o.optString("alarm_mode")),
            createdAt = o.optLong("created_at", System.currentTimeMillis())
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("command", command)
        put("trigger", trigger.name)
        put("enabled", enabled)
        put("interval_ms", intervalMs)
        put("alarm_mode", alarmMode.name)
        put("created_at", createdAt)
    }
}

/** 任务的持久化存储（JSON 存入 SharedPreferences）。 */
object TaskStore {
    private const val KEY_TASKS = "tasks_v1"
    private const val KEY_CORRUPT_BACKUP = "tasks_v1_corrupt_backup"

    fun getAll(context: Context): List<Task> {
        val raw = prefs(context).getString(KEY_TASKS, null)
        if (raw != null) {
            return parse(raw, context)
        }
        // 首次升级：旧版只有一条命令，迁移为一个默认任务；全新安装则从空列表开始。
        val initial = if (Settings.hasLegacyConfig(context)) {
            listOf(
                Task(
                    id = UUID.randomUUID().toString(),
                    name = "默认任务",
                    command = Settings.getCommand(context),
                    trigger = TriggerType.SCHEDULE,
                    enabled = true,
                    intervalMs = Settings.getIntervalMs(context),
                    alarmMode = Settings.getAlarmMode(context),
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            emptyList()
        }
        write(context, initial)
        return initial
    }

    fun get(context: Context, id: String): Task? =
        getAll(context).firstOrNull { it.id == id }

    fun save(context: Context, task: Task) {
        val list = getAll(context).toMutableList()
        val index = list.indexOfFirst { it.id == task.id }
        if (index >= 0) list[index] = task else list.add(task)
        write(context, list)
    }

    fun delete(context: Context, id: String) {
        write(context, getAll(context).filter { it.id != id })
    }

    private fun parse(raw: String, context: Context? = null): List<Task> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { Task.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        // 解析失败时不静默丢弃：原始串留底，便于排查“卡片莫名消失”
        if (context != null) {
            prefs(context).edit().putString(KEY_CORRUPT_BACKUP, raw).apply()
        }
        emptyList()
    }

    private fun write(context: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
}