// NotificationStatusHelper.kt
// D4 首页常驻提示检测（规格 §2 / AC-20）：复用 M2 已就绪的权限检测回调，仅补检测与深链意图。
// - 通知权限被关：NotificationManagerCompat.areNotificationsEnabled() == false
// - DND 拦截（API≥23）：currentInterruptionFilter 为 NONE，或仅 ALARMS/PRIORITY 且本 App 未获「绕过勿扰」
// 仅当存在权限风险时首页才显示常驻横幅（规格 §2.1）。

package com.dailyplan.app.data.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat

/** 通知可达性状态（仅检测，不写持久化） */
data class NotificationStatus(
    /** 系统「通知」总开关是否开启（areNotificationsEnabled） */
    val notificationsEnabled: Boolean,
    /** 是否存在 DND/勿扰拦截风险（即便通知已开，仍可能被静音） */
    val dndBlocking: Boolean
)

object NotificationStatusHelper {

    /** 综合检测当前通知可达性风险。 */
    fun getStatus(context: Context): NotificationStatus {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        // 通知被关时，DND 风险无额外意义，横幅归因为「通知被关」
        val dndBlocking = if (!notificationsEnabled) false else isDndBlocking(context)
        return NotificationStatus(notificationsEnabled, dndBlocking)
    }

    /**
     * 是否处于 DND 拦截风险：
     * - API < 23：无 DND 概念，恒 false
     * - INTERRUPTION_FILTER_NONE：全部拦截
     * - INTERRUPTION_FILTER_ALARMS / PRIORITY：仅当本 App 未获「绕过勿扰」授权时视为风险
     */
    private fun isDndBlocking(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        // 等价 M2 DndPolicyHelper.isBypassDndGranted
        val bypass = nm.isNotificationPolicyAccessGranted
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> true
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> !bypass
            else -> false
        }
    }

    /** 深链：本 App 通知设置页（通知被关时引导） */
    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    /** 深链：系统勿扰策略访问设置页（DND 拦截风险时引导） */
    fun policyAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /** 深链：本 App 应用详情页（用于麦克风等权限重授权，规格 §4.1） */
    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
}
