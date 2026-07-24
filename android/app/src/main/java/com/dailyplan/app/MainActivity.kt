// MainActivity.kt
// 主 Activity：承载今日 Compose 屏幕，提供 TodayViewModel。

package com.dailyplan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyplan.app.data.reminder.DndPolicyHelper
import com.dailyplan.app.di.TodayViewModelFactory
import com.dailyplan.app.ui.screen.TodayScreen
import com.dailyplan.app.ui.theme.DailyPlanTheme
import com.dailyplan.app.ui.viewmodel.TodayViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModelFactory: TodayViewModelFactory
    private lateinit var viewModel: TodayViewModel

    // 运行时申请 POST_NOTIFICATIONS（Android 13+ 必需，规格 §4.5 通知权限）
    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝后仅栏显，App 内列表兜底，不阻断记录流 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 通过 Application 的 AppContainer 构建 ViewModelFactory
        val app = application as DailyPlanApplication
        viewModelFactory = TodayViewModelFactory(app.container)
        // 取同一 ViewModel 实例（与 Compose viewModel() 共享 Activity 的 ViewModelStore）
        viewModel = ViewModelProvider(this, viewModelFactory)[TodayViewModel::class.java]

        // 申请通知权限（仅 API 33+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            DailyPlanTheme {
                val vm: TodayViewModel = viewModel(factory = viewModelFactory)
                TodayScreen(viewModel = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 进入前台补偿：重建未来 7 天提醒（规格 §4.4 / §5）
        viewModel.rescheduleReminders()
        // M4 D4：重算首页常驻提示（通知权限 / DND 风险，规格 §2 / AC-20）
        viewModel.refreshNotificationStatus(this)
        // 首次 DND 引导：若未获「绕过勿扰」授权，跳转系统设置（规格 §4.5，内部 SharedPreferences 去重）
        DndPolicyHelper.maybeRequestDndPolicy(this)
    }
}
