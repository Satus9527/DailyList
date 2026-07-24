// SettingsScreen.kt
// 设置页（规格 §4.1 / AC-22，R-4）：
// - 通知权限状态 + 深链系统设置
// - 麦克风权限状态 + 深链系统设置
// - 语音输入开关（持久化，关闭后首页语音按钮禁用）
// - P0-1 隐私说明文案
// - 默认提醒策略只读展示
// 不新增任何 Task 字段；与 M1/M2/M3 零返工。

package com.dailyplan.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dailyplan.app.data.reminder.NotificationStatusHelper
import com.dailyplan.app.ui.viewmodel.TodayViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// P0-1 隐私说明文案（规格 §8.4，双端同一文案）
private const val PRIVACY_TEXT =
    "本 App 纯本地存储、零登录；语音经系统 ASR 在设备端或联网转写，弱网/离线场景可能将音频上传至系统厂商" +
        "（Apple/Google）完成识别，非上传至本 App 账号/服务端；无个人数据随账号出端。"

@Composable
fun SettingsScreen(viewModel: TodayViewModel, onClose: () -> Unit) {
    val context = LocalContext.current

    // 权限状态（进入页面时取一次；更深刷新由调用方在返回首页时触发）
    val notifStatus = remember { NotificationStatusHelper.getStatus(context) }
    val micGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val voiceEnabled by viewModel.voiceInputEnabled.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // 顶部栏：标题 + 关闭
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Text("关闭") }
        }

        // 通知权限
        PermissionRow(
            label = "通知权限",
            granted = notifStatus.notificationsEnabled,
            onOpen = {
                val intent = NotificationStatusHelper.appNotificationSettingsIntent(context)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        )

        // 麦克风权限
        PermissionRow(
            label = "麦克风权限",
            granted = micGranted,
            onOpen = {
                val intent = NotificationStatusHelper.appDetailsSettingsIntent(context)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        )

        // 语音输入开关（持久化）
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("语音输入", style = MaterialTheme.typography.bodyLarge)
                Text("关闭后首页语音按钮禁用", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
            Switch(checked = voiceEnabled, onCheckedChange = viewModel::setVoiceInputEnabled)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // P0-1 隐私说明
        Text("隐私说明", style = MaterialTheme.typography.titleMedium)
        Text(
            PRIVACY_TEXT,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 默认提醒策略（只读展示，规格 §4.1）
        Text("默认提醒策略", style = MaterialTheme.typography.titleMedium)
        Text(
            "提前 10 分钟 + 到点 + 每 10 分钟重复最多 3 次；单条可在待办行铃铛处单独调整。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 权限状态行：绿点/灰点 + 去系统设置按钮 */
@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态点：已授权绿、未授权灰
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (granted) Color.Green else Color.Gray)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) "已授权" else "未授权",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        TextButton(onClick = onOpen) { Text("去系统设置") }
    }
}
