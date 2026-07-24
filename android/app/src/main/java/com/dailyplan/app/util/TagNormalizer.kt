// TagNormalizer.kt
// 标签归一：忽略大小写、首尾空格、全角转半角（规格 §4.1 / AC-30③ / R-O1）。
// 集中放置于 util/ 便于单测；写入与查询均必经此归一，保证「同一视觉标签」在库中唯一。
// 与 iOS 口径完全一致（含 U+3000 全角空格，规格 §5.1 补全建议）。

package com.dailyplan.app.util

object TagNormalizer {
    fun normalize(raw: String): String {
        val trimmed = raw.trim()                 // ① 去首尾空格/换行
        val halfWidth = trimmed.fullWidthToHalfWidth()  // ② 全角 → 半角
        return halfWidth.lowercase()             // ③ 忽略大小写
    }

    private fun String.fullWidthToHalfWidth(): String =
        map { c ->
            when {
                // 全角空格 U+3000 → 半角空格（与 iOS .fullwidthToHalfwidth 完全等价）
                c == '　' -> ' '
                // 全角标点/数字/字母 U+FF01–U+FF5E → 半角（code - 0xFEE0）
                c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar()
                else -> c
            }
        }.joinToString("")
}
