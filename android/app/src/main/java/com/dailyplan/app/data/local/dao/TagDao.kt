// TagDao.kt
// Tag DAO（规格 §4.1）。唯一约束由实体索引保证，归一由 Repository 层做。

package com.dailyplan.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailyplan.app.data.local.TagEntity

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY name ASC")
    suspend fun all(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity)

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun byNormalizedName(name: String): TagEntity?
}
