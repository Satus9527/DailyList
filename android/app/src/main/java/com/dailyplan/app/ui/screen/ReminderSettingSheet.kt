// ReminderSettingSheet.kt
// F3 提醒设置面板（M2-D，Task #36）：为单条待办设置/修改提醒时间、提前量、重复次数。
// 默认沿用 P0-2（leadMinutes=10 / repeatCount=3）；可分别关闭（L=0 / R=0，AC-11）。
// 确认后经 TodayViewModel.saveReminder 持久化并调度（ReminderScheduler.schedule 内部先 cancel 再登记，幂等）。

package com.dailyplan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.ui.viewmodel.TodayViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingSheet(
    task: Task,
    viewModel: TodayViewModel,
    onDismiss: () -> Unit
) {
    val baseEnabled = task.remindAt != null
    var enabled by remember { mutableStateOf(baseEnabled) }

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

        // 取消 / 保存
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
                viewModel.saveReminder(task.id, remindAt, lm, rc)
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
