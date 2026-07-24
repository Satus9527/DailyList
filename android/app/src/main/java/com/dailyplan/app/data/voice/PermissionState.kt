// PermissionState.kt
// 麦克风 / 语音识别授权状态（规格 M3 §2.2）。

package com.dailyplan.app.data.voice

/** 授权状态：Android 无独立「语音识别」授权，识别能力随 Google 服务；本枚举仅描述麦克风态 + 能力不可用 */
enum class PermissionState {
    NOT_DETERMINED,  // 未请求（理论值，Android 首次即弹窗，实际多为 DENIED/GRANTED）
    GRANTED,         // 已授权
    DENIED,          // 用户拒绝（麦克风 RECORD_AUDIO）
    RESTRICTED,      // 系统限制（如家长控制）
    UNAVAILABLE      // 设备不支持 / 语音识别能力缺失（SpeechRecognizer.isRecognitionAvailable=false）
}
