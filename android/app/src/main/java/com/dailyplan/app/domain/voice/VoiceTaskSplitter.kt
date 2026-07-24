// VoiceTaskSplitter.kt
// 领域层自动拆分器（规格 M3 §5）。双端同源算法在 Android 的落地。
//
// 依赖：
// - ASRSplitConfig（纯数据，来自 shared/asr_split_config.json，assets 下游副本）—— 唯一拆分常量来源
// - TaskRepository（M1 协议）—— 仅经 add() 落库，不新增任何 Task 字段
//
// 不 import Speech / SpeechRecognizer（平台无关，架构 §7.2 边界）。
// 拆分常量（splitPunctuation / splitPauseThresholdMs / includeEnumerationComma / includeNewline）
// 全部来自 config，禁止硬编码（P0-4 防漂移）。

package com.dailyplan.app.domain.voice

import com.dailyplan.app.data.repository.TaskRepository
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.TaskSource
import com.dailyplan.app.util.ASRSplitConfig

class VoiceTaskSplitter(
    private val config: ASRSplitConfig,
    private val repository: TaskRepository
) {
    // splitPunctuation 转查找集合（仅取自配置，禁止硬编码标点字面量）
    private val punctuationSet: Set<String> = config.splitPunctuation.toSet()

    /**
     * 平台层在「句末标点 / 长停顿边界 / onFinal / 用户手动落一条」时调用。
     * 按 config 切分，每段非空 → repository.add(source=VOICE)。
     *
     * @param text       本次 final 边界文本（已确定句段）
     * @param startOrder 本条 batch 起始 sortOrder（保证同一批内顺序；默认 0）
     */
    suspend fun commitFinalSegment(text: String, startOrder: Int = 0) {
        splitIntoSegments(text)
            .filter { it.isNotBlank() }                 // 空段跳过（R-X4 / AC-4）
            .forEachIndexed { index, seg ->
                // title ≤ 500 字（M1 §1.5 / AC-29），超出截断并提示由上层 Toast 负责
                val title = if (seg.length > 500) seg.take(500) else seg
                val task = Task.makeNew(
                    title = title,
                    source = TaskSource.VOICE,          // 语音来源（raw "voice"）
                    sortOrder = startOrder + index
                )
                repository.add(task)                    // M1 接口落库；Flow 自动刷新 UI（§9）
            }
    }

    /**
     * 按 config 切分：
     * 1) 仅 splitPunctuation 触发切分，标点本身丢弃（不进 title）；
     * 2) 「、」：includeEnumerationComma=false → 保留在 title 内，不切（避免一条清单被误拆）；
     * 3) 换行：includeNewline=false → 保留在 title 内，不切（同一条内换行李内容）。
     * 长停顿边界由平台层（config.splitPauseThresholdMs）触发 commitFinalSegment，本方法不直接计时。
     */
    private fun splitIntoSegments(text: String): List<String> {
        val segs = mutableListOf<String>()
        val buf = StringBuilder()
        for (ch in text) {
            val s = ch.toString()
            when {
                punctuationSet.contains(s) -> {
                    val t = buf.toString().trim()
                    if (t.isNotEmpty()) segs.add(t)
                    buf.clear()
                }
                s == "、" && !config.includeEnumerationComma -> buf.append(s)   // 顿号并入，不切
                (s == "\n" || s == "\r") && !config.includeNewline -> buf.append(s)  // 换行并入，不切
                else -> buf.append(ch)
            }
        }
        val tail = buf.toString().trim()
        if (tail.isNotEmpty()) segs.add(tail)            // 尾段（无句末标点）也成条
        return segs
    }
}
