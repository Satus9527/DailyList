// DegradeReason.kt
// 语音降级触发原因（规格 M3 §6.1）。用于 UI 针对性提示与本地日志。

package com.dailyplan.app.data.voice

/** 降级原因：授权拒 / 无网无离线包 / 识别失败 / AudioFocus 永久丢失 */
enum class DegradeReason {
    PERMISSION_DENIED,   // 麦克风权限被拒（ERROR_INSUFFICIENT_PERMISSIONS）
    NETWORK,             // 无网且无离线包（ERROR_NETWORK 重试仍失败）
    ASR_ERROR,           // 识别失败（其他不可恢复错误 / 能力不可用）
    AUDIOFOCUS_LOST      // AudioFocus 永久丢失（来电/其他 App 永久占用麦克风）
}
