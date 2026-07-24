// TagRepository.kt
// Tag 基础 CRUD（M1 最简，规格 §4）。写入/查询前对 name 做归一（§4.1）。

package com.dailyplan.app.data.repository

import com.dailyplan.app.data.local.AppDatabase
import com.dailyplan.app.data.local.TagEntity
import com.dailyplan.app.util.TagNormalizer
import java.util.UUID

interface TagRepository {
    suspend fun all(): List<TagEntity>
    /** 归一后写入；已存在同名归一结果则复用（规格 §4.1 / AC-30） */
    suspend fun addOrReuse(rawName: String): TagEntity
}

class LocalTagRepository(private val db: AppDatabase) : TagRepository {
    override suspend fun all(): List<TagEntity> = db.tagDao().all()

    override suspend fun addOrReuse(rawName: String): TagEntity {
        val norm = TagNormalizer.normalize(rawName)
        val dao = db.tagDao()
        dao.byNormalizedName(norm)?.let { return it }
        val entity = TagEntity(id = UUID.randomUUID().toString(), name = norm)
        dao.insert(entity)
        return entity
    }
}
