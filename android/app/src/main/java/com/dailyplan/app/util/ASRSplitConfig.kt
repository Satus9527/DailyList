// ASRSplitConfig.kt
// 双端共享 ASR 拆分配置解析（规格 §8）。M1 仅定义结构、F2 才消费。
// 禁止在代码中硬编码拆分标点集或阈值，必须从 assets/asr_split_config.json 解析（P0-4）。

package com.dailyplan.app.util

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class ASRSplitConfig(
    val configVersion: Int,
    val splitPunctuation: List<String>,
    val splitPauseThresholdMs: Int,
    val includeEnumerationComma: Boolean,
    val includeNewline: Boolean,
    val note: String? = null
) {
    companion object {
        /** 从 assets/asr_split_config.json 加载（同源副本，来自 /workspace/shared）。 */
        fun load(context: Context): ASRSplitConfig? = runCatching {
            context.assets.open("asr_split_config.json").bufferedReader().use { reader ->
                Json.decodeFromString<ASRSplitConfig>(reader.readText())
            }
        }.getOrNull()
    }
}
