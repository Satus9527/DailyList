// CategoryDao.kt
// Category DAO（规格 §3 / §7.4）。预设项不可删由 Repository 层兜底。

package com.dailyplan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailyplan.app.data.local.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY name ASC")
    suspend fun all(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Query("DELETE FROM category WHERE id = :id AND is_preset = 0")
    suspend fun deleteIfNotPreset(id: String)

    @Query("UPDATE task SET category_id = :fallbackId WHERE category_id = :id")
    suspend fun reassignTasksTo(id: String, fallbackId: String)
}
