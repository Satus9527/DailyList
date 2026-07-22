// CategorySeed.kt
// 预设分类固定 UUID（规格 §3.2，禁止随机）。首次启动写入种子数据。

package com.dailyplan.app.util

import java.util.UUID

object CategorySeed {
    val WORK_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val LIFE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val STUDY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    /** 默认归类「其他」（规格 §3.2） */
    val OTHER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")

    /** [id, name, isPreset] */
    val PRESETS: List<Triple<UUID, String, Boolean>> = listOf(
        Triple(WORK_ID, "工作", true),
        Triple(LIFE_ID, "生活", true),
        Triple(STUDY_ID, "学习", true),
        Triple(OTHER_ID, "其他", true)
    )
}
