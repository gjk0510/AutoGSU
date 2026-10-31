package com.gjk0510.autosu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 单个定时任务（主页上的一张卡片）。 */
data class Task(
    val id: String,
    val name: String,
    val command: String,
    val intervalMs: Long,
    val alarmMode: AlarmMode,
    val createdAt: Long
) {
    companion object {
        /** 新建一个未调度的任务卡片。 */
        fun new(): Task = Task(
            id = UUID.randomUUID().toString(),
            name = "新任务",
            command = Settings.DEFAULT_COMMAND,
            intervalMs = Settings.DEFAULT_INTERVAL_MS,
            alarmMode = AlarmMode.OFF,
            createdAt = System.currentTimeMillis()
        )

        fun fromJson(o: JSONObject): Task = Task(
            id = o.getString("id"),
            name = o.optString("name", "任务"),
            command = o.optString("command", Settings.DEFAULT_COMMAND),
            intervalMs = o.optLong("interval_ms", Settings.DEFAULT_INTERVAL_MS),
            alarmMode = AlarmMode.fromName(o.optString("alarm_mode")),
            createdAt = o.optLong("created_at", System.currentTimeMillis())
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("command", command)
        put("interval_ms", intervalMs)
        put("alarm_mode", alarmMode.name)
        put("created_at", createdAt)
    }
}

/** 任务的持久化存储（JSON 存入 SharedPreferences）。 */
object TaskStore {
    private const val KEY_TASKS = "tasks_v1"

    fun getAll(context: Context): List<Task> {
        val raw = prefs(context).getString(KEY_TASKS, null)
        if (raw != null) {
            return parse(raw)
        }
        // 首次升级：旧版只有一条命令，迁移为一个默认任务；全新安装则从空列表开始。
        val initial = if (Settings.hasLegacyConfig(context)) {
            listOf(
                Task(
                    id = UUID.randomUUID().toString(),
                    name = "默认任务",
                    command = Settings.getCommand(context),
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

    private fun parse(raw: String): List<Task> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { Task.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
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