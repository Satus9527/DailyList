// TaskItem.kt
// 单条待办行：勾选完成（F5）、行内编辑 title、删除、拖拽手柄（F5）、提醒入口（F3）。
// M4 增强（R-4 / AC-5）：长按弹 DropdownMenu「合并到上一条 / 从此处拆分」；D3「错过的提醒」区可传 badgeText="提醒未达"。
// 已完成置底 + 删除线（R-S1）。

package com.dailyplan.app.ui.screen

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dailyplan.app.domain.model.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskItem(
    task: Task,
    onToggleDone: (Task) -> Unit,
    onEditCommit: (Task, String) -> Unit,
    onDelete: (Task) -> Unit,
    onSetReminder: (Task) -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    /** D3「错过的提醒」区标记文案（如「提醒未达」）；为 null 时不显示胶囊 */
    badgeText: String? = null,
    /** 合并到上一条（长按菜单）：为 null 时该项禁用（首条不可合并） */
    onMergeUp: (() -> Unit)? = null,
    /** 从此处拆分（长按菜单） */
    onRequestSplit: (Task) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(task.title) }
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    // 点按：未完成且非编辑态 → 进入行内编辑（与 M1 行为一致）
                    onClick = {
                        if (!task.isDone && !editing) {
                            editText = task.title
                            editing = true
                        }
                    },
                    // 长按：弹出合并/拆分菜单（规格 §4.2 / AC-5）
                    onLongClick = { menuExpanded = true }
                )
        ) {
            // 完成勾选
            Icon(
                imageVector = if (task.isDone) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = if (task.isDone) "已完成" else "未完成",
                tint = if (task.isDone) Color.Green else Color.Gray,
                modifier = Modifier
                    .clickable { onToggleDone(task) }
                    .padding(end = 8.dp)
            )

            // 标题：编辑态 / 展示态
            if (editing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    textStyle = TextStyle.Default,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    singleLine = true,
                    decorationBox = { inner -> inner() }
                )
            } else {
                Text(
                    text = task.title,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    color = if (task.isDone) Color.Gray else Color.Unspecified,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
            }

            // 编辑态提交按钮
            if (editing) {
                Text(
                    "完成",
                    modifier = Modifier
                        .clickable {
                            onEditCommit(task, editText)
                            editing = false
                        }
                        .padding(start = 4.dp)
                )
            } else {
                // F3 提醒入口（铃铛 + 下次提醒时间）
                IconButton(onClick = { onSetReminder(task) }) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "提醒设置",
                        tint = if (task.remindAt != null) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                if (task.remindAt != null) {
                    Text(
                        formatRemindTime(task.remindAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                // D3「提醒未达」胶囊（橙红，白字）
                if (badgeText != null) {
                    Surface(
                        color = Color(0xFFE53935),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 删除
                IconButton(onClick = { onDelete(task) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
                }
                // 上下移动（持久化排序，AC-16）
                IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                }
                // 更多（长按同等入口，便于无长按设备）
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
            }
        }

        // 长按 / 更多 菜单：合并到上一条、从此处拆分（规格 §4.2 / AC-5）
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("合并到上一条") },
                enabled = onMergeUp != null,
                onClick = {
                    menuExpanded = false
                    onMergeUp?.invoke()
                }
            )
            DropdownMenuItem(
                text = { Text("从此处拆分") },
                onClick = {
                    menuExpanded = false
                    onRequestSplit(task)
                }
            )
        }
    }
}

/** 行内展示用：压缩的提醒时间（MM/dd HH:mm，设备本地时区） */
private fun formatRemindTime(date: Date): String {
    val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
    fmt.timeZone = java.util.TimeZone.getDefault()   // 设备本地时区（规格 §1.1）
    return fmt.format(date)
}
