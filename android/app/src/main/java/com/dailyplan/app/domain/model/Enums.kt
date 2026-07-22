// Enums.kt
// 双端一致的枚举原始值定义（规格 §2.2）。持久化一律以 String 落库，禁止用整数 ordinal。

package com.dailyplan.app.domain.model

/** 优先级：高 / 中（默认）/ 低 */
enum class Priority(val raw: String) {
    HIGH("high"), MEDIUM("medium"), LOW("low");   // 默认 MEDIUM
    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: MEDIUM
    }
}

/** 待办来源：语音 / 文字 / 模板 / 补生成 */
enum class TaskSource(val raw: String) {
    VOICE("voice"), TEXT("text"), TEMPLATE("template"), MAKEUP("makeup")
}

/** 同步状态预埋（v1.1 消费，M1 不消费） */
enum class SyncState(val raw: String) {
    LOCAL("local"), DIRTY("dirty"), SYNCED("synced")
}
