// TodayScreen.kt
// 首页「今日」(F1/F5/F6)：顶部 X/Y 进度、当日列表（可拖拽重排）、底部文字输入框 + 添加按钮。
// 数据从 Room 加载（F6 持久化）。
// F2 语音（M3）：麦克风开关 + 录音态视觉反馈（红点脉冲 / 计时 / 波形）+ 实时 partial + 落一条 / 停止并保存；
// 降级态置灰麦克风并以 Snackbar 引导文字输入（规格 §6 / AC-6）。

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.data.voice.VoiceState
import com.dailyplan.app.ui.viewmodel.TodayViewModel

@Composable
fun TodayScreen(viewModel: TodayViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val input by viewModel.inputText.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val partial by viewModel.partialText.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // M3 运行时申请 RECORD_AUDIO（规格 §4.4）；拒绝 → 降级引导文字
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoice() else viewModel.onPermissionDenied() }

    // M3 降级提示宿主：关语音按钮 + 引导文字输入（规格 §6 / AC-6），用 Snackbar 而非 Toast
    val snackbarHostState = remember { SnackbarHostState() }
    // 录音计时（秒），仅在 Listening 时累加；状态切换即归零
    var elapsedSeconds by remember { mutableStateOf(0) }

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

    // F3 提醒设置面板所针对的待办（null 表示未打开，M2-D）
    var reminderTask: Task? by remember { mutableStateOf(null) }

    // 错误提示（截断/失败）短暂展示
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
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
                        onSetReminder = { reminderTask = it },
                        onMoveUp = if (index > 0) {
                            { viewModel.reorder(index, index - 1) }
                        } else null,
                        onMoveDown = if (index < tasks.lastIndex) {
                            { viewModel.reorder(index, index + 1) }
                        } else null
                    )
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
