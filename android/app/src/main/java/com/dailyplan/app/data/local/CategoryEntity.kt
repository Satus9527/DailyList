// CategoryEntity.kt
// Category 实体（规格 §7.2 / §3）。

package com.dailyplan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_preset", defaultValue = "0") val isPreset: Boolean = false
)
