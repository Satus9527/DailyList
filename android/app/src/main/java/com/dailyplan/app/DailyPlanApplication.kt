// DailyPlanApplication.kt
// Application：持有 AppContainer；首启种子预设分类（规格 §3.2）。
// 损坏兜底已内置在 DatabaseProvider（规格 §10），此处不抛异常。

package com.dailyplan.app

import android.app.Application
import com.dailyplan.app.di.AppContainer

class DailyPlanApplication : Application() {
    // 延迟初始化，首次访问时构建数据库（含损坏检测）
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.seedPresetCategoriesIfNeeded()
    }
}
