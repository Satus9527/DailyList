// DndPolicyHelper.kt
// 免打扰（DND）权限检测与引导（规格 §4.5）。
// setBypassDnd(true) 需要 ACCESS_NOTIFICATION_POLICY（Android 6.0+）；未授权时仅栏显、不响铃。

package com.dailyplan.app.data.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object DndPolicyHelper {
    private const val PREFS = "dnd_policy_prefs"
    private const val KEY_ASKED = "asked_bypass_dnd"

    /** 是否已获「绕过勿扰」授权。低版本系统无 DND 拦截，恒为 true。 */
    fun isBypassDndGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    /** 跳转系统「勿扰访问」设置页（需在 Activity 上下文调用）。 */
    fun openPolicySettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 首次尝试引导：若未授权且未曾提示过，记录并打开系统设置授予「绕过勿扰」。
     * 内部以 SharedPreferences 去重，避免反复弹窗。应在 Activity 上下文中调用。
     * 引导文案：未授权时提醒仅栏显、不响铃；未完成待办也会在 App 内「待完成」列表展示（规格 §4.5）。
     */
    fun maybeRequestDndPolicy(context: Context) {
        if (isBypassDndGranted(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        openPolicySettings(context)
    }
}
