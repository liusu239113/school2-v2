package com.arktools.xiaozhang

import android.app.Application
import android.util.Log
import com.arktools.adsdk.AdSdkConfig


import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SchoolTycoonApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 在 Room 初始化前执行非破坏性恢复：只从已验证的快照恢复，绝不删除唯一的 live 数据库。
        // SQLite 会自行处理 journal；手工删除 journal 或因一次 quick_check 失败移走主库，
        // 会把普通卡死/强退升级成真实清档。
        recoverDatabaseNonDestructively()

        // 安装全局未捕获异常处理器：所有异常记录日志后交由系统处理
        // 不再吞掉任何异常 —— 吞掉异常导致真实崩溃数据不可见，玩家闪退开发者不知情
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SchoolTycoon", "FATAL: Uncaught exception on thread ${thread.name}", throwable)
            // 追加写入崩溃日志文件，方便用户反馈时附带
            try {
                val crashLog = java.io.File(filesDir, "crash_log.txt")
                crashLog.appendText(
                    "\n=== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} ===\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n" +
                    throwable.stackTraceToString() + "\n"
                )
            } catch (_: Exception) { /* 写入失败不影响主流程 */ }
            // 全部交由系统默认处理器处理（正常崩溃+上报）
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // TapTap SDK 延迟初始化：必须在用户同意隐私政策后才能初始化
        // 否则 SDK 会在同意前读取 AndroidID，不符合隐私合规要求
        // 实际初始化在 MainScreen 的 onAccepted 回调中执行

        // 配置广告 SDK
        AdSdkConfig.configure(
            appId = 2062434858991079425L,
            rewardVideoId = "2062435124066897922",
            privacyPolicyUrl = "http://yanyususu.online:5555/xiaozhang.html",
            isDebug = BuildConfig.DEBUG
        )
    }

    /**
     * 仅在 live 数据库不存在或为 0 字节时，从本地已验证快照恢复。
     * 对一个存在但暂时打不开的数据库保持原样：文件锁、I/O 抖动和未完成 journal
     * 都不能成为删除玩家数据的理由。
     */
    private fun recoverDatabaseNonDestructively() {
        val dbFile = getDatabasePath("school_tycoon_db")
        if (dbFile.exists() && dbFile.length() > 0L) {
            Log.i("SchoolTycoon", "Live database present; destructive startup repair is disabled")
            return
        }

        val saveRoot = java.io.File(filesDir, "saves")
        val candidates = buildList {
            for (slot in 0..3) {
                val slotDir = java.io.File(saveRoot, "slot_$slot")
                add(java.io.File(slotDir, "school_tycoon_db"))
                add(java.io.File(slotDir, "school_tycoon_db.previous"))
                slotDir.listFiles { file ->
                    file.name.startsWith("school_tycoon_db.previous.pending")
                }?.forEach(::add)
            }
            add(java.io.File(dbFile.parentFile, "school_tycoon_db.previous"))
            dbFile.parentFile?.listFiles { file ->
                file.name.startsWith("school_tycoon_db.previous.pending")
            }?.forEach(::add)
            add(java.io.File(dbFile.parentFile, "school_tycoon_db.loading"))
            add(java.io.File(dbFile.parentFile, "school_tycoon_db.startup-recovery"))
            add(java.io.File(filesDir, "school_tycoon_db.corrupted_backup"))
        }.mapNotNull { file ->
            inspectDatabaseSnapshot(file)?.let { revision ->
                file to revision
            }
        }.sortedWith(
            compareByDescending<Pair<java.io.File, Long>> { it.second }
                .thenByDescending { it.first.lastModified() }
        )

        val source = candidates.firstOrNull()?.first ?: return
        val staged = java.io.File(
            dbFile.parentFile,
            "${dbFile.name}.startup-recovery"
        )
        try {
            dbFile.parentFile?.mkdirs()
            if (source.absolutePath != staged.absolutePath) {
                source.copyTo(staged, overwrite = true)
            }
            if (inspectDatabaseSnapshot(staged) == null) {
                if (source.absolutePath != staged.absolutePath) {
                    staged.delete()
                }
                return
            }
            if (dbFile.exists() && dbFile.length() == 0L) dbFile.delete()
            if (!staged.renameTo(dbFile)) {
                throw IllegalStateException(
                    "Unable to atomically install startup recovery database"
                )
            }
            Log.w("SchoolTycoon", "Recovered missing live database from ${source.absolutePath}")
        } catch (e: Exception) {
            Log.e("SchoolTycoon", "Non-destructive startup recovery failed; source preserved", e)
        }
    }

    /** 返回有效存档中的最大修订时间；空学校库或结构不完整时返回 null。 */
    private fun inspectDatabaseSnapshot(file: java.io.File): Long? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val physicallyValid = db.rawQuery(
                    "PRAGMA quick_check(1)",
                    null
                ).use { cursor ->
                    cursor.moveToFirst() &&
                        cursor.getString(0).equals("ok", ignoreCase = true)
                }
                if (!physicallyValid) return@use null

                val hasSchoolsTable = db.rawQuery(
                    "SELECT 1 FROM sqlite_master " +
                        "WHERE type = 'table' AND name = 'schools' LIMIT 1",
                    null
                ).use { it.moveToFirst() }
                if (!hasSchoolsTable) return@use null

                val hasSchool = db.rawQuery(
                    "SELECT 1 FROM schools LIMIT 1",
                    null
                ).use { it.moveToFirst() }
                if (!hasSchool) return@use null

                val hasRevisionColumn = db.rawQuery(
                    "PRAGMA table_info(`schools`)",
                    null
                ).use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "lastSaveTime") {
                            found = true
                            break
                        }
                    }
                    found
                }
                if (!hasRevisionColumn) {
                    return@use file.lastModified().coerceAtLeast(1L)
                }

                db.rawQuery(
                    "SELECT MAX(lastSaveTime) FROM schools",
                    null
                ).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.isNull(0)) {
                        file.lastModified().coerceAtLeast(1L)
                    } else {
                        cursor.getLong(0)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
