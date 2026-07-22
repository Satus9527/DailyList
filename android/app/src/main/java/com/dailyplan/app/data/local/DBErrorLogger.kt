// DBErrorLogger.kt
// 本地日志（规格 §10.2，仅本地、不联网）。写入应用私有目录 logs/db_error.log。

package com.dailyplan.app.data.local

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DBErrorLogger {
    /**
     * 记录一条 DB 错误（规格 §10.2 字段：timestamp / event / dbPath / errorType / message / action）。
     * 不联网、不含账号信息。
     */
    fun log(context: Context, event: String, error: Throwable, dbPath: String? = null) {
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())
        val dbPathStr = dbPath ?: context.getDatabasePath(AppDatabase.DB_NAME).absolutePath
        val entry = buildString {
            appendLine("[DB_ERROR] timestamp=$ts")
            appendLine("event=$event")
            appendLine("dbPath=$dbPathStr")
            appendLine("errorType=${error.javaClass.simpleName}")
            appendLine("message=${error.localizedMessage ?: error.message ?: "unknown"}")
            appendLine("action=rebuilt_empty_db")
            appendLine()
        }
        runCatching {
            val dir = File(context.filesDir, "logs")
            dir.mkdirs()
            val file = File(dir, "db_error.log")
            file.appendText(entry)
        }
        // 同时输出到系统日志便于排查
        android.util.Log.e("DB_ERROR", "$event @ $dbPathStr : ${error.message}")
    }
}
