// TaskFilter.kt
// 组合筛选条件值类型 + 内存版匹配谓词（规格 §3.1 / §3.4）。
// 与 M4 首页三区块一致：ViewModel 推荐对「已加载的展示日集合」做内存过滤（零额外查询）。

package com.dailyplan.app.domain.model

import com.dailyplan.app.util.CategorySeed
import java.util.UUID

/**
 * 筛选条件。任意维度传空即「不限」；全部为空 = 不过滤（§6 空条件=全部）。
 * @param categoryId null=不限；传「其他」预设 id 时逻辑上包含 categoryId==null 的任务（§6）
 * @param priority   null=不限
 * @param tagIds     空=不限；非空=任务须同时拥有全部这些标签（AND 语义）
 * @param untaggedOnly true=仅返回「无任何标签」的任务（与 tagIds 互斥，优先于 tagIds）
 */
data class TaskFilter(
    val categoryId: UUID? = null,
    val priority: Priority? = null,
    val tagIds: Set<UUID> = emptySet(),
    val untaggedOnly: Boolean = false
) {
    val isEmpty: Boolean
        get() = categoryId == null && priority == null && tagIds.isEmpty() && !untaggedOnly
}

/**
 * 内存匹配：对某任务应用筛选条件。
 * @param taskTagIds 该任务已关联标签 id 集合（由 Repository 经 TaskTagCrossRef 读取，ViewModel 缓存）
 */
fun TaskFilter.matches(task: Task, taskTagIds: Set<UUID>): Boolean {
    // 分类：未归类视为「其他」（§6）；筛「其他」含 nil
    if (categoryId != null) {
        val cat = task.categoryId ?: CategorySeed.OTHER_ID
        if (cat != categoryId) return false
    }
    // 优先级
    if (priority != null && task.priority != priority) return false
    // 标签：untaggedOnly 优先；否则要求拥有全部指定标签（AND）
    if (untaggedOnly) {
        if (taskTagIds.isNotEmpty()) return false
    } else if (tagIds.isNotEmpty()) {
        if (!tagIds.all { it in taskTagIds }) return false
    }
    return true
}
