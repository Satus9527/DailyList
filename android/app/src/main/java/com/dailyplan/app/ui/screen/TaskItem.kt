// TaskItem.kt
// 单条待办行：勾选完成（F5）、行内编辑 title、删除、拖拽手柄（F5）。
// 已完成置底 + 删除线（R-S1）。

package com.dailyplan.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(task.title) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (!task.isDone) {
                                editText = task.title
                                editing = true
                            }
                        })
                    }
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

            // 删除
            IconButton(onClick = { onDelete(task) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
            }
            // 上下移动（持久化排序，AC-16；完整手指拖拽可在 F4 阶段增强）
            IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
            }
        }
    }
}

/** 行内展示用：压缩的提醒时间（MM/dd HH:mm，设备本地时区） */
private fun formatRemindTime(date: Date): String {
    val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
    fmt.timeZone = java.util.TimeZone.getDefault()   // 设备本地时区（规格 §1.1）
    return fmt.format(date)
}
