// TodayScreen.kt
// 首页「今日」(F1/F5/F6)：顶部 X/Y 进度、当日列表（可拖拽重排）、底部文字输入框 + 添加按钮。
// 数据从 Room 加载（F6 持久化）。

package com.dailyplan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyplan.app.ui.viewmodel.TodayViewModel

@Composable
fun TodayScreen(viewModel: TodayViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val input by viewModel.inputText.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 错误提示（截断/失败）短暂展示
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearError()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 进度 X / Y（R-U3 / AC-15）
        Text(
            text = "今日完成 ${viewModel.doneCount} / 共 ${viewModel.totalCount}",
            style = MaterialTheme.typography.titleMedium
        )

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }

        // 当日列表（可重排，AC-16）
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
                TaskItem(
                    task = task,
                    onToggleDone = viewModel::toggleDone,
                    onEditCommit = viewModel::editTitle,
                    onDelete = viewModel::delete,
                    onMoveUp = if (index > 0) {
                        { viewModel.reorder(index, index - 1) }
                    } else null,
                    onMoveDown = if (index < tasks.lastIndex) {
                        { viewModel.reorder(index, index + 1) }
                    } else null
                )
            }
        }

        // F1 文字输入（回车 / 点按钮新增）
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("添加今日待办…") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::addFromInput) {
                        Icon(Icons.Filled.Add, contentDescription = "添加")
                    }
                }
            )
        }
    }
}
