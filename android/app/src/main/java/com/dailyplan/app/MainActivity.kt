// MainActivity.kt
// 主 Activity：承载今日 Compose 屏幕，提供 TodayViewModel。

package com.dailyplan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailyplan.app.di.TodayViewModelFactory
import com.dailyplan.app.ui.screen.TodayScreen
import com.dailyplan.app.ui.theme.DailyPlanTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModelFactory: TodayViewModelFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 通过 Application 的 AppContainer 构建 ViewModelFactory
        val app = application as DailyPlanApplication
        viewModelFactory = TodayViewModelFactory(app.container)

        setContent {
            DailyPlanTheme {
                val viewModel: com.dailyplan.app.ui.viewmodel.TodayViewModel =
                    viewModel(factory = viewModelFactory)
                TodayScreen(viewModel = viewModel)
            }
        }
    }
}
