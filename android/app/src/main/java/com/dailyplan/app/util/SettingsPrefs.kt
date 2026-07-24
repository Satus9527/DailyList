// SettingsPrefs.kt
// 本地设置持久化（规格 §4.1 语音输入开关）：纯 SharedPreferences，等价于 iOS UserDefaults/DataStore。
// 仅存 App 内开关，不触碰任何账号/网络。

package com.dailyplan.app.util

import android.content.Context
import android.content.SharedPreferences

class SettingsPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 语音输入开关（默认开）。关闭后首页语音按钮禁用（规格 §4.1） */
    var voiceInputEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_INPUT, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_INPUT, value).apply()

    companion object {
        private const val PREFS_NAME = "dailyplan_settings"
        private const val KEY_VOICE_INPUT = "voice_input_enabled"
    }
}
