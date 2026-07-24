// AppContainer.kt
// 手动依赖注入容器（di 层）。Repository 实现依赖 AppDatabase，ViewModel 依赖 Repository。
// 领域层不依赖具体存储类（架构 §7.2）。

package com.dailyplan.app.di

import android.content.Context
import com.dailyplan.app.data.local.AppDatabase
import com.dailyplan.app.data.local.DatabaseProvider
import com.dailyplan.app.data.repository.CategoryRepository
import com.dailyplan.app.data.repository.LocalCategoryRepository
import com.dailyplan.app.data.repository.LocalTagRepository
import com.dailyplan.app.data.repository.LocalTaskRepository
import com.dailyplan.app.data.repository.TagRepository
import com.dailyplan.app.data.repository.TaskRepository
import com.dailyplan.app.data.voice.ASRController
import com.dailyplan.app.data.voice.NativeASRController
import com.dailyplan.app.data.reminder.ReminderNotificationHelper
import com.dailyplan.app.data.reminder.ReminderScheduler
import com.dailyplan.app.data.reminder.WorkManagerReminderScheduler
import com.dailyplan.app.ui.viewmodel.TodayViewModel
import androidx.work.WorkManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = DatabaseProvider.get(appContext)

    val taskRepository: TaskRepository = LocalTaskRepository(database)
    val categoryRepository: CategoryRepository = LocalCategoryRepository(database)
    val tagRepository: TagRepository = LocalTagRepository(database)

    /**
     * M3 语音层（F2）：解析 assets/asr_split_config.json 为单例；据此构造 NativeASRController。
     * config 为 null（assets 缺失）时语音入口将被判不可用，记录流不中断（规格 §6 / P0-4）。
     */
    val asrSplitConfig: com.dailyplan.app.util.ASRSplitConfig? =
        com.dailyplan.app.util.ASRSplitConfig.load(appContext)
    val asrController: ASRController = NativeASRController(appContext, asrSplitConfig)

    /** 通知层：渠道与展示 Helper（规格 §4.3） */
    val reminderNotificationHelper: ReminderNotificationHelper = ReminderNotificationHelper(appContext)

    /** 通知层：WorkManager 调度实现（规格 §4.1） */
    val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(
        appContext,
        WorkManager.getInstance(appContext),
        taskRepository
    )

    /** 创建今日 ViewModel（每次读取当日库），注入提醒调度器与语音层（M2 / M3 接线点） */
    fun todayViewModel(): TodayViewModel =
        TodayViewModel(taskRepository, reminderScheduler, asrController, asrSplitConfig)

    /** 首启种子：写入预设分类（规格 §3.2）；失败不崩溃（规格 §10.4） */
    fun seedPresetCategoriesIfNeeded() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { categoryRepository.seedPresetsIfNeeded() }
        }
    }
}
