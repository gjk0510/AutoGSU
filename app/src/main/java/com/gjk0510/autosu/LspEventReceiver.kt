package com.gjk0510.autosu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * LSP 模式事件入口：由 system_server / SystemUI（uid 1000）在灭屏/解锁时
 * 通过显式广播拉起本应用。进程被拉起 → 执行任务 → 自然退出，无常驻。
 * 仅在监听模式为 LSP 时生效。
 */
class LspEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LspHookEntry.ACTION_EVENT) return
        if (Settings.getEventMode(context) != EventMode.LSP) return
        val trigger = when (intent.getStringExtra(LspHookEntry.EXTRA_EVENT)) {
            LspHookEntry.EVENT_UNLOCK -> TriggerType.UNLOCK
            LspHookEntry.EVENT_SCREEN_OFF -> TriggerType.SCREEN_OFF
            else -> return
        }
        EventTasks.dispatch(context, trigger, goAsync())
    }
}
