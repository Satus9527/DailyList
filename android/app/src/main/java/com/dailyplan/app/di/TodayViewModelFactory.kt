// TodayViewModelFactory.kt
// ViewModelProvider.Factory，供 MainActivity 用 by viewModels() 获取 TodayViewModel。

package com.dailyplan.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dailyplan.app.ui.viewmodel.TodayViewModel

class TodayViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
            return container.todayViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
