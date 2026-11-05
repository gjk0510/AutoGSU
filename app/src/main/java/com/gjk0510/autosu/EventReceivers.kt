package com.gjk0510.autosu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 关机执行：尽力而为。ACTION_SHUTDOWN 在新版 Android 上对第三方应用基本不再下发，
 * 只有少数定制 ROM 仍会收到，因此该功能在多数机型上会静默失效。
 */
class ShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SHUTDOWN) return
        EventTasks.dispatch(context, TriggerType.SHUTDOWN, goAsync())
    }
}

/**
 * 事件型任务共用分发入口：按触发方式筛选已启用任务并交给 TaskRunner。
 * 由前台服务的动态接收器（常驻通知模式）或 LspEventReceiver（LSP 模式）调用。
 */
object EventTasks {
    fun dispatch(context: Context, trigger: TriggerType, pendingResult: BroadcastReceiver.PendingResult) {
        val tasks = TaskStore.getAll(context).filter { it.trigger == trigger && it.enabled }
        if (tasks.isEmpty()) {
            pendingResult.finish()
            return
        }
        tasks.forEach { ExecutionLog.appendTask(context, it.id, "收到${triggerTypeLabel(trigger)}事件，开始执行") }
        TaskRunner.run(context, tasks, pendingResult)
    }
}