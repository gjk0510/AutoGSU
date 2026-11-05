package com.gjk0510.autosu

import android.content.Intent
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed 入口：hook 系统进程拿到解锁/灭屏时机，再以 system uid 发显式广播
 * 拉起 AutoSU 执行任务。App 侧无常驻进程、无常驻通知，空闲零耗电。
 *
 * LSPosed 作用域需勾选：Android 系统（system_server，灭屏）+ 系统界面
 * （SystemUI，解锁）。事件点找不到（ROM 改名）时自动退化到备用 hook 点。
 */
class LspHookEntry : IXposedHookLoadPackage {

    companion object {
        const val ACTION_EVENT = "com.gjk0510.autosu.LSP_EVENT"
        const val EXTRA_EVENT = "event"
        const val EVENT_UNLOCK = "UNLOCK"
        const val EVENT_SCREEN_OFF = "SCREEN_OFF"

        private const val TARGET_PACKAGE = "com.gjk0510.autosu"
        private const val TARGET_RECEIVER = "com.gjk0510.autosu.LspEventReceiver"
    }

    /** 同类事件去重（部分 ROM 一次灭屏会连续走到两个回调）。 */
    private val lastSendAt = mutableMapOf<String, Long>()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> hookScreenOff(lpparam.classLoader)
            "com.android.systemui" -> hookUnlock(lpparam.classLoader)
        }
    }

    /** 灭屏：优先 PhoneWindowManager.screenTurnedOff，退化 startedGoingToSleep。 */
    private fun hookScreenOff(classLoader: ClassLoader) {
        try {
            val pm = XposedHelpers.findClass(
                "com.android.server.policy.PhoneWindowManager", classLoader
            )
            if (XposedBridge.hookAllMethods(pm, "screenTurnedOff", eventHook(EVENT_SCREEN_OFF)).isNotEmpty()) {
                XposedBridge.log("AutoSU LSP: hook screenTurnedOff ok")
                return
            }
            XposedBridge.hookAllMethods(pm, "startedGoingToSleep", eventHook(EVENT_SCREEN_OFF))
            XposedBridge.log("AutoSU LSP: fallback hook startedGoingToSleep")
        } catch (t: Throwable) {
            XposedBridge.log("AutoSU LSP: hookScreenOff failed: $t")
        }
    }

    /** 解锁：优先 SystemUI KeyguardViewMediator.sendUserPresent，退化 handleKeyguardDone。 */
    private fun hookUnlock(classLoader: ClassLoader) {
        try {
            val mediator = XposedHelpers.findClass(
                "com.android.systemui.keyguard.KeyguardViewMediator", classLoader
            )
            if (XposedBridge.hookAllMethods(mediator, "sendUserPresent", eventHook(EVENT_UNLOCK)).isNotEmpty()) {
                XposedBridge.log("AutoSU LSP: hook sendUserPresent ok")
                return
            }
            XposedBridge.hookAllMethods(mediator, "handleKeyguardDone", eventHook(EVENT_UNLOCK))
            XposedBridge.log("AutoSU LSP: fallback hook handleKeyguardDone")
        } catch (t: Throwable) {
            XposedBridge.log("AutoSU LSP: hookUnlock failed: $t")
        }
    }

    private fun eventHook(event: String): XC_MethodHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val now = System.currentTimeMillis()
            if (now - lastSendAt.getOrDefault(event, 0L) < 300) return
            lastSendAt[event] = now
            try {
                // 两个 hook 点的宿主类都持有 mContext 字段，直接取来发广播。
                val context = param.thisObject?.let { host ->
                    runCatching {
                        XposedHelpers.getObjectField(host, "mContext") as android.content.Context
                    }.getOrNull()
                } ?: return
                val intent = Intent(ACTION_EVENT)
                    .setClassName(TARGET_PACKAGE, TARGET_RECEIVER)
                    .putExtra(EXTRA_EVENT, event)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                context.sendBroadcast(intent)
            } catch (t: Throwable) {
                XposedBridge.log("AutoSU LSP: send $event failed: $t")
            }
        }
    }
}
