// VoiceState.kt
// UI 层观察的语音交互状态（M3 接线）。纯展示态，不含业务逻辑。

package com.dailyplan.app.data.voice

import com.dailyplan.app.data.voice.DegradeReason

/** 语音状态机：空闲 / 听写中 / 能力不可用 / 已降级（含原因） */
sealed interface VoiceState {
    /** 空闲（未开启语音） */
    data object Idle : VoiceState

    /** 持续听写中 */
    data object Listening : VoiceState

    /** 设备不支持 / 配置缺失，入口置灰 */
    data object Unavailable : VoiceState

    /** 已降级：关闭语音按钮 + 引导文字输入（规格 §6） */
    data class Degraded(val reason: DegradeReason) : VoiceState
}
