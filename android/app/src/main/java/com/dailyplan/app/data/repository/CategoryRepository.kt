// CategoryRepository.kt
// Category 基础 CRUD（M1 最简，规格 §3）。预设项不可删/不可改名。

package com.dailyplan.app.data.repository

import com.dailyplan.app.data.local.AppDatabase
import com.dailyplan.app.data.local.CategoryEntity
import com.dailyplan.app.util.CategorySeed
import java.util.UUID

interface CategoryRepository {
    suspend fun all(): List<CategoryEntity>
    suspend fun add(name: String): CategoryEntity
    /** 删除自建分类；其下任务回退「其他」预设（规格 §3.2）。预设项不可删。 */
    suspend fun delete(id: UUID)
    /** 首启种子：写入 4 个预设分类（规格 §3.2） */
    suspend fun seedPresetsIfNeeded()
}

class LocalCategoryRepository(private val db: AppDatabase) : CategoryRepository {
    override suspend fun all(): List<CategoryEntity> = db.categoryDao().all()

    override suspend fun add(name: String): CategoryEntity {
        val entity = CategoryEntity(id = UUID.randomUUID().toString(), name = name, isPreset = false)
        db.categoryDao().insert(entity)
        return entity
    }

    override suspend fun delete(id: UUID) {
        val dao = db.categoryDao()
        // 先将其下任务回退「其他」预设（规格 §3.2）
        dao.reassignTasksTo(id.toString(), CategorySeed.OTHER_ID.toString())
        // 仅删自建（SQL 已限定 is_preset = 0）
        dao.deleteIfNotPreset(id.toString())
    }

    override suspend fun seedPresetsIfNeeded() {
        val dao = db.categoryDao()
        if (dao.all().isNotEmpty()) return   // 已种子过
        CategorySeed.PRESETS.forEach { (id, name, isPreset) ->
            dao.insert(CategoryEntity(id = id.toString(), name = name, isPreset = isPreset))
        }
    }
}
