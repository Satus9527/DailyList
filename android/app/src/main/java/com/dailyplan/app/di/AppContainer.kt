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
import com.dailyplan.app.ui.viewmodel.TodayViewModel

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = DatabaseProvider.get(appContext)

    val taskRepository: TaskRepository = LocalTaskRepository(database)
    val categoryRepository: CategoryRepository = LocalCategoryRepository(database)
    val tagRepository: TagRepository = LocalTagRepository(database)

    /** 创建今日 ViewModel（每次读取当日库） */
    fun todayViewModel(): TodayViewModel = TodayViewModel(taskRepository)

    /** 首启种子：写入预设分类（规格 §3.2）；失败不崩溃（规格 §10.4） */
    fun seedPresetCategoriesIfNeeded() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { categoryRepository.seedPresetsIfNeeded() }
        }
    }
}
