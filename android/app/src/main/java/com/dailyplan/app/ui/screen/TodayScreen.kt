// TodayScreen.kt
// 首页「今日」(F1/F5/F6)：顶部 X/Y 进度 + 设置入口、D4 常驻提示横幅、D3「错过的提醒」区、当日列表（可拖拽重排）、
// 底部文字输入框 + 添加按钮。
// F2 语音（M3）：麦克风开关 + 录音态视觉反馈 + 实时 partial + 落一条 / 停止并保存；降级态置灰麦克风并 Snackbar 引导。
// M4（D3/D4/S5/R-4）：错过提醒区、常驻提示、跨日重归类区块、长按合并/拆分、设置页入口。

package com.dailyplan.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat
import java.util.UUID
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyplan.app.data.local.CategoryEntity
import com.dailyplan.app.data.local.TagEntity
import com.dailyplan.app.domain.model.Priority
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.TaskFilter
import com.dailyplan.app.domain.model.displayName
import com.dailyplan.app.data.voice.VoiceState
import com.dailyplan.app.ui.viewmodel.TodayViewModel

@Composable
fun TodayScreen(viewModel: TodayViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val input by viewModel.inputText.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val partial by viewModel.partialText.collectAsStateWithLifecycle()
    val missed by viewModel.missedTasks.collectAsStateWithLifecycle()
    val inProgress by viewModel.inProgressTasks.collectAsStateWithLifecycle()
    val done by viewModel.doneTasks.collectAsStateWithLifecycle()
    val banner by viewModel.notificationBanner.collectAsStateWithLifecycle()
    // M5 F4 筛选栏状态（分类/优先级/标签，单维+组合 AND）
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // M3 运行时申请 RECORD_AUDIO（规格 §4.4）；拒绝 → 降级引导文字
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoice() else viewModel.onPermissionDenied() }

    // M3 降级提示宿主：关语音按钮 + 引导文字输入（规格 §6 / AC-6），用 Snackbar 而非 Toast
    val snackbarHostState = remember { SnackbarHostState() }
    // 录音计时（秒），仅在 Listening 时累加；状态切换即归零
    var elapsedSeconds by remember { mutableStateOf(0) }

    // 设置页 / 拆分对话框 / 提醒设置面板 状态
    var showSettings by remember { mutableStateOf(false) }
    var splitTask by remember { mutableStateOf<Task?>(null) }
    var reminderTask: Task? by remember { mutableStateOf(null) }

    // D4：进入前台检测一次通知可达性（从设置返回也会再次触发）
    LaunchedEffect(Unit) { viewModel.refreshNotificationStatus(context) }

    // 录音计时：进入 Listening 启动 1 秒心跳累加；退出或被降级时 key 变化自动取消
    LaunchedEffect(voiceState) {
        elapsedSeconds = 0
        if (voiceState is VoiceState.Listening) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                elapsedSeconds++
            }
        }
    }

    // 降级提示：关语音按钮 + 引导文字输入（规格 §6 / AC-6）
    LaunchedEffect(voiceState) {
        if (voiceState is VoiceState.Degraded) {
            snackbarHostState.showSnackbar("语音暂不可用，请改用文字输入")
        }
    }

    // 错误提示（截断/失败）短暂展示
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearError()
        }
    }

    // 平铺顺序（用于合并「上一条」定位）：错过的提醒 → 进行中 → 已完成
    val flat = remember(missed, inProgress, done) { missed + inProgress + done }
    val flatIndexById = remember(flat) { flat.mapIndexed { i, t -> t.id to i }.toMap() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            // 顶部：进度 + 设置入口（规格 §4.1 R-4）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日完成 ${viewModel.doneCount} / 共 ${viewModel.totalCount}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }

            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            // D4 首页常驻提示横幅（仅风险时显示，点击深链系统设置，规格 §2 / AC-20）
            banner?.let { info ->
                NotificationBannerBar(
                    reason = info.reason,
                    onClick = { viewModel.openNotificationSettings(context) }
                )
            }

            // M5 F4 筛选栏（置于 D4 横幅下、列表上；单维/组合 AND，清除=全部，规格 §4.2 / §4.3）
            FilterBar(
                filter = filter,
                categories = categories,
                allTags = allTags,
                onFilterChange = viewModel::applyFilter
            )

            // D3「错过的提醒」区（规格 §1.3，置于列表上方、空态不渲染）
            if (missed.isNotEmpty()) {
                MissedReminderSection(missedCount = missed.size)
            }

            // 当日列表（按展示日分区块：错过的提醒 / 进行中 / 已完成；可重排，AC-16）
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (missed.isNotEmpty()) {
                    itemsIndexed(missed, key = { _, t -> "missed_${t.id}" }) { _, task ->
                        val fi = flatIndexById[task.id] ?: 0
                        TaskItem(
                            task = task,
                            onToggleDone = viewModel::toggleDone,
                            onEditCommit = viewModel::editTitle,
                            onDelete = viewModel::delete,
                            onSetReminder = { reminderTask = it },
                            badgeText = "提醒未达",
                            onMergeUp = if (fi > 0) {
                                { flat.getOrNull(fi - 1)?.let { prev -> viewModel.mergeWithPrevious(task, prev) } }
                            } else null,
                            onRequestSplit = { splitTask = it },
                            onMoveUp = if (fi > 0) { { viewModel.reorder(fi, fi - 1) } } else null,
                            onMoveDown = if (fi < flat.lastIndex) { { viewModel.reorder(fi, fi + 1) } } else null
                        )
                    }
                }

                if (inProgress.isNotEmpty()) {
                    item { SectionHeader("进行中") }
                    itemsIndexed(inProgress, key = { _, t -> "ip_${t.id}" }) { _, task ->
                        val fi = flatIndexById[task.id] ?: 0
                        TaskItem(
                            task = task,
                            onToggleDone = viewModel::toggleDone,
                            onEditCommit = viewModel::editTitle,
                            onDelete = viewModel::delete,
                            onSetReminder = { reminderTask = it },
                            onMergeUp = if (fi > 0) {
                                { flat.getOrNull(fi - 1)?.let { prev -> viewModel.mergeWithPrevious(task, prev) } }
                            } else null,
                            onRequestSplit = { splitTask = it },
                            onMoveUp = if (fi > 0) { { viewModel.reorder(fi, fi - 1) } } else null,
                            onMoveDown = if (fi < flat.lastIndex) { { viewModel.reorder(fi, fi + 1) } } else null
                        )
                    }
                }

                if (done.isNotEmpty()) {
                    item { SectionHeader("已完成") }
                    itemsIndexed(done, key = { _, t -> "done_${t.id}" }) { _, task ->
                        val fi = flatIndexById[task.id] ?: 0
                        TaskItem(
                            task = task,
                            onToggleDone = viewModel::toggleDone,
                            onEditCommit = viewModel::editTitle,
                            onDelete = viewModel::delete,
                            onSetReminder = { reminderTask = it },
                            onMergeUp = if (fi > 0) {
                                { flat.getOrNull(fi - 1)?.let { prev -> viewModel.mergeWithPrevious(task, prev) } }
                            } else null,
                            onRequestSplit = { splitTask = it },
                            onMoveUp = if (fi > 0) { { viewModel.reorder(fi, fi - 1) } } else null,
                            onMoveDown = if (fi < flat.lastIndex) { { viewModel.reorder(fi, fi + 1) } } else null
                        )
                    }
                }
            }

            // M3 语音输入控制条（F2）：麦克风开关 + 录音态视觉反馈 + 听写中实时文本 + 落一条/停止并保存
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isListening = voiceState is VoiceState.Listening
                IconButton(
                    onClick = {
                        when {
                            isListening -> viewModel.stopVoice()
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED -> viewModel.startVoice()
                            else -> recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    // 降级 / 不可用态置灰（规格 §6，文字录入仍可用）
                    enabled = voiceState is VoiceState.Idle || voiceState is VoiceState.Listening
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = if (isListening) "停止语音" else "开始语音"
                    )
                }
                if (isListening) {
                    // 录音态视觉反馈：红点脉冲 + 计时 + 波形
                    VoiceRecordingIndicator(seconds = elapsedSeconds)
                    Text(
                        text = partial.ifBlank { "聆听中…" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = viewModel::commitManual) { Text("落一条") }
                    Button(onClick = viewModel::saveBufferedAsText) { Text("停止并保存") }
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

    // R-4 设置页（规格 §4.1 / AC-22）：ModalBottomSheet 承载全屏设置内容
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsScreen(
                viewModel = viewModel,
                onClose = {
                    showSettings = false
                    viewModel.refreshNotificationStatus(context)   // 从设置返回后重算横幅
                }
            )
        }
    }

    // R-4 拆分对话框（规格 §4.2 / AC-5）：将光标置于拆分位置后确认
    splitTask?.let { task ->
        SplitDialog(
            task = task,
            onConfirm = { index -> viewModel.splitTask(task, index); splitTask = null },
            onDismiss = { splitTask = null }
        )
    }

    // F3 提醒设置面板（M2-D，Task #36）：ModalBottomSheet 承载设置内容
    reminderTask?.let { task ->
        ModalBottomSheet(onDismissRequest = { reminderTask = null }) {
            ReminderSettingSheet(
                task = task,
                viewModel = viewModel,
                onDismiss = { reminderTask = null }
            )
        }
    }
}

// MARK: - D3 / 分区 小组件

/**
 * M5 F4 首页筛选栏（规格 §4.2 / §4.3）：分类 / 优先级 / 标签 三个入口，支持单维与组合（AND）。
 * 空条件 = 全部。筛选对各区块（错过的提醒/进行中/已完成）统一生效（由各区块派生时叠加）。
 */
@Composable
private fun FilterBar(
    filter: TaskFilter,
    categories: List<CategoryEntity>,
    allTags: List<TagEntity>,
    onFilterChange: (TaskFilter) -> Unit
) {
    var catExpanded by remember { mutableStateOf(false) }
    var tagExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 分类筛选
        Box {
            FilterChip(
                selected = filter.categoryId != null,
                onClick = { catExpanded = true },
                label = { Text("分类") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                DropdownMenuItem(text = { Text("全部") }, onClick = {
                    onFilterChange(filter.copy(categoryId = null)); catExpanded = false
                })
                categories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.name) },
                        onClick = {
                            onFilterChange(filter.copy(categoryId = UUID.fromString(cat.id)))
                            catExpanded = false
                        }
                    )
                }
            }
        }

        // 优先级筛选（单维切换，再次点击清除）
        Priority.entries.forEach { p ->
            FilterChip(
                selected = filter.priority == p,
                onClick = {
                    onFilterChange(filter.copy(priority = if (filter.priority == p) null else p))
                },
                label = { Text(p.displayName) }
            )
        }

        // 标签筛选（多选 AND）+ 仅无标签
        Box {
            FilterChip(
                selected = filter.tagIds.isNotEmpty() || filter.untaggedOnly,
                onClick = { tagExpanded = true },
                label = { Text("标签") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = tagExpanded, onDismissRequest = { tagExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("仅无标签") },
                    onClick = {
                        onFilterChange(filter.copy(untaggedOnly = !filter.untaggedOnly, tagIds = emptySet()))
                    }
                )
                HorizontalDivider()
                if (allTags.isEmpty()) {
                    DropdownMenuItem(text = { Text("暂无标签") }, onClick = { tagExpanded = false })
                }
                allTags.forEach { tag ->
                    val checked = tag.id.let { id -> filter.tagIds.any { it.toString() == id } }
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = checked,
                                    onCheckedChange = {}
                                )
                                Text(tag.name)
                            }
                        },
                        onClick = {
                            val id = UUID.fromString(tag.id)
                            val next = if (checked) filter.tagIds - id else filter.tagIds + id
                            onFilterChange(filter.copy(tagIds = next, untaggedOnly = false))
                        }
                    )
                }
            }
        }

        // 清除筛选（恢复全部，规格 §6 空条件=全部）
        if (!filter.isEmpty) {
            TextButton(onClick = { onFilterChange(TaskFilter()) }) { Text("清除") }
        }
    }
}

/** D3「错过的提醒」区标题（带计数，规格 §1.3） */
@Composable
private fun MissedReminderSection(missedCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = "错过的提醒 · $missedCount",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** 列表分区标题（进行中 / 已完成） */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

/**
 * D4 首页常驻提示横幅（规格 §2 / AC-20）：
 * 黄/橙警示色常驻条，点击深链系统设置；无风险时不渲染（零打扰）。
 */
@Composable
private fun NotificationBannerBar(
    reason: com.dailyplan.app.ui.viewmodel.NotificationBannerInfo.Reason,
    onClick: () -> Unit
) {
    val text = when (reason) {
        com.dailyplan.app.ui.viewmodel.NotificationBannerInfo.Reason.NOTIFICATIONS_DISABLED ->
            "提醒可能不送达，去开启通知"
        com.dailyplan.app.ui.viewmodel.NotificationBannerInfo.Reason.DND_ACTIVE ->
            "勿扰模式可能拦截提醒，去设置"
    }
    Surface(
        color = Color(0xFFFFF3CD),
        contentColor = Color(0xFF8A6D00),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔔", modifier = Modifier.padding(end = 8.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * R-4 拆分对话框（规格 §4.2 / AC-5）：用户在文本框中将光标置于断点位置，确认即按光标位置拆分。
 * 默认光标落在首个 splitPunctuation 之后（若有），否则置于中部，便于快速拆分。
 */
@Composable
private fun SplitDialog(
    task: Task,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 默认拆分位置：首个标点之后，否则长度一半
    val defaultIndex = task.title.indexOfFirst { it in "。！？；" }.let { if (it >= 0) it + 1 else task.title.length / 2 }
    var tfv by remember { mutableStateOf(TextFieldValue(task.title, selection = androidx.compose.ui.text.TextRange(defaultIndex))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(tfv.selection.start.coerceIn(0, task.title.length)) }) {
                Text("拆分")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("从此处拆分") },
        text = {
            Column {
                Text("将光标移动到拆分位置后点「拆分」：", style = MaterialTheme.typography.labelSmall)
                TextField(
                    value = tfv,
                    onValueChange = { tfv = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )
            }
        }
    )
}

// MARK: - F2 语音录音态视觉反馈（红点脉冲 / 计时 / 波形）

/** 录音态指示：红点脉冲 + mm:ss 计时 + 简易波形（仅视觉反馈，不阻塞文字输入）。 */
@Composable
private fun VoiceRecordingIndicator(seconds: Int) {
    val infinite = rememberInfiniteTransition(label = "voiceIndicator")
    val dotScale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dotScale"
    )
    val dotAlpha by infinite.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dotAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 红点脉冲：提示正在聆听
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(dotScale)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(Color.Red)
        )
        // 计时 mm:ss
        Text(formatVoiceTime(seconds), style = MaterialTheme.typography.labelMedium)
        // 简易波形
        VoiceWaveformBars()
    }
}

/** 简易波形：多根竖条循环起伏（非真实振幅，仅视觉反馈）。 */
@Composable
private fun VoiceWaveformBars() {
    val infinite = rememberInfiniteTransition(label = "voiceWave")
    val bars = listOf(8, 16, 10, 18, 8)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        bars.forEachIndexed { i, h ->
            val target = if (i % 2 == 0) h.toFloat() else (h / 2).toFloat()
            val anim by infinite.animateFloat(
                initialValue = h.toFloat(), targetValue = target,
                animationSpec = infiniteRepeatable(
                    tween(500 + i * 80), RepeatMode.Reverse
                ), label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(anim.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            )
        }
    }
}

/** 计时格式化 mm:ss。 */
private fun formatVoiceTime(s: Int): String {
    val m = s / 60
    val sec = s % 60
    return "%02d:%02d".format(m, sec)
}
