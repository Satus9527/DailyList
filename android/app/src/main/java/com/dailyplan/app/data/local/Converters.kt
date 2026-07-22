// Converters.kt
// 公共 TypeConverter（规格 §7.1）：Date ↔ Long 毫秒、UUID ↔ String。

package com.dailyplan.app.data.local

import androidx.room.TypeConverter
import java.util.Date
import java.util.UUID

class Converters {
    @TypeConverter fun dateToLong(d: Date?): Long? = d?.time

    @TypeConverter fun longToDate(v: Long?): Date? = v?.let { Date(it) }

    @TypeConverter fun uuidToStr(u: UUID?): String? = u?.toString()

    @TypeConverter fun strToUuid(s: String?): UUID? = s?.let { UUID.fromString(it) }
}
