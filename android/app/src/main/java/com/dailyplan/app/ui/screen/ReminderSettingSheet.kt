// ReminderSettingSheet.kt
// F3 提醒设置面板（M2-D，Task #36）：为单条待办设置/修改提醒时间、提前量、重复次数。
// 默认沿用 P0-2（leadMinutes=10 / repeatCount=3）；可分别关闭（L=0 / R=0，AC-11）。
// M5 F4 组织能力（本规格新增）：同 sheet 内扩展「分类 / 优先级 / 标签」三段，与提醒设置共存（规格 §4.1 / §4.3）。
// 确认后经 TodayViewModel.saveTaskAll 统一持久化（分类/优先级/标签 + 提醒）并调度。

package com.dailyplan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyplan.app.data.local.CategoryEntity
import com.dailyplan.app.data.local.TagEntity
import com.dailyplan.app.domain.model.Priority
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.displayName
import com.dailyplan.app.ui.viewmodel.TodayViewModel
import com.dailyplan.app.util.CategorySeed
import com.dailyplan.app.util.TagNormalizer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingSheet(
    task: Task,
    viewModel: TodayViewModel,
    onDismiss: () -> Unit
) {
    val baseEnabled = task.remindAt != null
    var enabled by remember { mutableStateOf(baseEnabled) }

    // —— M5 F4：分类 / 优先级 / 标签 状态 ——
    var selectedCategoryId by remember { mutableStateOf(task.categoryId ?: CategorySeed.OTHER_ID) }
    var selectedPriority by remember { mutableStateOf(task.priority) }
    var tagInput by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<TagEntity>() }
    var suggestions by remember { mutableStateOf<List<TagEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // 回显该任务已有关联标签
    LaunchedEffect(task.id) {
        selectedTags.clear()
        selectedTags.addAll(viewModel.tagsForTask(task.id))
    }

    // 分类列表（预设 + 自建），供选择器与筛选栏
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    // 标签去重（归一后同名视为重复）
    fun isDuplicateTag(raw: String): Boolean {
        val norm = TagNormalizer.normalize(raw)
        return norm.isBlank() || selectedTags.any { TagNormalizer.normalize(it.name) == norm }
    }
    // 确认一个标签词：归一 + 去重 + addOrReuse 写库
    fun commitTag(raw: String) {
        if (isDuplicateTag(raw)) {
            tagInput = ""
            suggestions = emptyList()
            return
        }
        scope.launch {
            viewModel.addTagFromInput(raw)?.let { tag -> selectedTags.add(tag) }
        }
        tagInput = ""
        suggestions = emptyList()
    }

    // 初始日期（毫秒）与时间（时/分）；未设提醒时默认今天 09:00（本地时区）
    val initialDate = task.remindAt?.time ?: run {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var dateMillis by remember { mutableStateOf(initialDate) }
    val initCal = remember { Calendar.getInstance().apply { timeInMillis = initialDate } }
    var hour by remember { mutableStateOf(initCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initCal.get(Calendar.MINUTE)) }

    var leadEnabled by remember { mutableStateOf(task.leadMinutes > 0) }
    var leadMinutes by remember { mutableStateOf(if (task.leadMinutes > 0) task.leadMinutes else 10) }
    var repeatEnabled by remember { mutableStateOf(task.repeatCount > 0) }
    var repeatCount by remember { mutableStateOf(if (task.repeatCount > 0) task.repeatCount else 3) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

    val leadOptions = listOf(5, 10, 15, 30)
    val repeatOptions = listOf(1, 2, 3, 5)

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // —— M5 F4 分类选择器（规格 §4.1）——
        Text("分类", style = MaterialTheme.typography.titleSmall)
        CategoryPicker(
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            onSelect = { selectedCategoryId = it },
            onCreate = { name ->
                scope.launch { viewModel.addCategory(name)?.let { selectedCategoryId = UUID.fromString(it.id) } }
            }
        )

        // —— M5 F4 优先级选择器（高/中/低，默认中，不强制展示标识，规格 §4.1 / AC-30②）——
        Text("优先级", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Priority.entries.forEach { p ->
                FilterChip(
                    selected = selectedPriority == p,
                    onClick = { selectedPriority = p },
                    label = { Text(p.displayName) }
                )
            }
        }

        // —— M5 F4 标签输入（联想 / 回车或逗号确认 / 去重 / chip 可删，规格 §4.1 / AC-12）——
        Text("标签（可跳过）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        TagInput(
            value = tagInput,
            suggestions = suggestions,
            selectedTags = selectedTags,
            onValueChange = { newText ->
                // 输入逗号/全角逗号即确认前一个词
                val sep = newText.indexOfFirst { it == ',' || it == '，' }
                if (sep >= 0) {
                    commitTag(newText.substring(0, sep))
                    tagInput = newText.substring(sep + 1)
                } else {
                    tagInput = newText
                    scope.launch { suggestions = viewModel.suggestTags(newText, 8) }
                }
            },
            onCommit = { commitTag(tagInput) },
            onPickSuggestion = { tag ->
                if (!isDuplicateTag(tag.name)) selectedTags.add(tag)
                tagInput = ""
                suggestions = emptyList()
            },
            onRemove = { selectedTags.remove(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // —— M4/F3 提醒设置（沿用原逻辑）——
        // 启用提醒
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用提醒", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        if (enabled) {
            // 日期 / 时间
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                TextButton(onClick = { showDatePicker = true }) {
                    Text("日期：${dateFmt.format(Date(dateMillis))}")
                }
                TextButton(onClick = { showTimePicker = true }) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                    }
                    Text("时间：${timeFmt.format(cal.time)}")
                }
            }

            // 提前提醒
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("提前提醒", modifier = Modifier.weight(1f))
                Switch(checked = leadEnabled, onCheckedChange = { leadEnabled = it })
            }
            if (leadEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    leadOptions.forEach { m ->
                        FilterChip(
                            selected = leadMinutes == m,
                            onClick = { leadMinutes = m },
                            label = { Text("${m}分") }
                        )
                    }
                }
            } else {
                Text(
                    "关闭：到点才提醒（不提前，L=0）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // 重复提醒
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("重复提醒", modifier = Modifier.weight(1f))
                Switch(checked = repeatEnabled, onCheckedChange = { repeatEnabled = it })
            }
            if (repeatEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeatOptions.forEach { n ->
                        FilterChip(
                            selected = repeatCount == n,
                            onClick = { repeatCount = n },
                            label = { Text("${n}次") }
                        )
                    }
                }
                Text(
                    "每次间隔 10 分钟（每次 +10min，P0-2）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Text(
                    "关闭：仅提醒一次（不重复，R=0，AC-11）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            Text(
                "未设置提醒：到点/提前/重复均不触发。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 取消 / 保存（统一保存 分类/优先级/标签 + 提醒）
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(onClick = {
                // 合并日期（毫秒）与时间（时/分）为绝对本地时刻
                val remindAt: Date? = if (enabled) {
                    Calendar.getInstance().apply {
                        timeInMillis = dateMillis
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                } else null
                val lm = if (leadEnabled) leadMinutes else 0   // 关闭提前 → L=0
                val rc = if (repeatEnabled) repeatCount else 0 // 关闭重复 → R=0（AC-11）
                // M5 统一保存：分类/优先级/标签 + 提醒（规格 §4.1）
                viewModel.saveTaskAll(
                    taskId = task.id,
                    categoryId = selectedCategoryId,
                    priority = selectedPriority,
                    tags = selectedTags.map { it.id },
                    remindAt = remindAt,
                    leadMinutes = lm,
                    repeatCount = rc
                )
                onDismiss()
            }) { Text("保存") }
        }
    }

    // 日期选择器
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 时间选择器
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

// MARK: - M5 F4 子组件

/** 分类选择器：预设 + 自建（规格 §4.1 / AC-13）。默认「其他」预设 id。 */
@Composable
private fun CategoryPicker(
    selectedCategoryId: UUID,
    categories: List<CategoryEntity>,
    onSelect: (UUID) -> Unit,
    onCreate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val current = categories.firstOrNull { it.id == selectedCategoryId.toString() }?.name ?: "其他"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        TextButton(onClick = { expanded = true }) {
            Text(current)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择分类")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onSelect(UUID.fromString(cat.id))
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            // 自建分类入口
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("新建分类") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val n = newName.trim()
                    if (n.isNotBlank()) {
                        onCreate(n)
                        newName = ""
                        expanded = false
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加分类")
                }
            }
        }
    }
}

/** 标签输入：文本框 + 联想下拉 + 已选 chip（可删，规格 §4.1）。 */
@Composable
private fun TagInput(
    value: String,
    suggestions: List<TagEntity>,
    selectedTags: List<TagEntity>,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onPickSuggestion: (TagEntity) -> Unit,
    onRemove: (TagEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        // 已选标签 chips
        if (selectedTags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                selectedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemove(tag) },
                        label = { Text(tag.name) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "移除 ${tag.name}") }
                    )
                }
            }
        }
        // 输入框（回车确认）
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("输入标签，回车或逗号确认") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onCommit) { Icon(Icons.Filled.Add, contentDescription = "添加标签") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        // 联想下拉
        if (suggestions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                suggestions.forEach { tag ->
                    DropdownMenuItem(
                        text = { Text(tag.name) },
                        onClick = { onPickSuggestion(tag) }
                    )
                }
            }
        }
    }
}
