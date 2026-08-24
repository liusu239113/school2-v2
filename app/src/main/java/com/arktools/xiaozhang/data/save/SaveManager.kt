package com.arktools.xiaozhang.data.save

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.arktools.xiaozhang.data.local.APP_DATABASE_SCHEMA_VERSION
import com.arktools.xiaozhang.data.local.AppDatabase
import com.arktools.xiaozhang.domain.engine.GameEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class SaveManager(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsDataStore: com.arktools.xiaozhang.data.pref.SettingsDataStore,
    private val gameEngine: GameEngine
) {
    private val saveDir: File
        get() = File(context.filesDir, "saves").also { it.mkdirs() }

    private val metaFile: File
        get() = File(saveDir, "meta.json")

    companion object {
        const val AUTO_SAVE_SLOT = 0
        const val DB_FILE_NAME = "school_tycoon_db"
        private const val LEGACY_REPAIR_BASELINE_VERSION = 21
        private const val LOAD_PREFS = "save_load_prefs"
        private const val KEY_JUST_LOADED = "just_loaded_save"
    }

    private val loadPrefs = context.getSharedPreferences(LOAD_PREFS, Context.MODE_PRIVATE)

    /** 全局存档互斥锁：确保同一时间只有一个存档/读档操作在进行 */
    private val saveMutex = Mutex()

    /**
     * 读档替换 live DB 后，旧 Activity/ViewModel 会依次收到 onPause/onCleared。
     * 此标记在当前进程剩余生命周期内阻止它们反向自动保存。
     */
    @Volatile
    private var restoreInProgress = false

    fun isRestoreInProgress(): Boolean = restoreInProgress

    /**
     * 标记“刚完成读档”——进程重启后检查此标记，自动进入游戏并暂停
     */
    fun markJustLoaded() {
        loadPrefs.edit().putBoolean(KEY_JUST_LOADED, true).commit()
    }

    /**
     * 检查是否刚完成读档（进程重启后调用）
     */
    fun consumeJustLoaded(): Boolean {
        val loaded = loadPrefs.getBoolean(KEY_JUST_LOADED, false)
        if (loaded) {
            loadPrefs.edit().putBoolean(KEY_JUST_LOADED, false).commit()
        }
        return loaded
    }

    /**
     * 所有新版本进度固定写入唯一自动存档。旧 slot_1 至 slot_3 目录不会被读取、覆盖或删除，
     * 以便保留历史版本玩家已有的本地备份。
     */
    fun getAutoSaveTargetSlotId(): Int = AUTO_SAVE_SLOT

    suspend fun saveAutoSave(
        schoolName: String,
        currentYear: Int,
        currentMonth: Int,
        cash: Double,
        reputation: Long,
        schoolId: String = ""
    ): Boolean = saveGame(
        slotId = AUTO_SAVE_SLOT,
        schoolName = schoolName,
        currentYear = currentYear,
        currentMonth = currentMonth,
        cash = cash,
        reputation = reputation,
        schoolId = schoolId
    )

    private data class RecoveryCandidate(
        val slotId: Int,
        val file: File,
        val revision: Long
    )

    /** 返回所有可用的本地恢复候选，优先使用数据库内的保存修订时间。 */
    private fun recoveryCandidates(): List<RecoveryCandidate> = buildList {
        for (slotId in AUTO_SAVE_SLOT..3) {
            val dir = File(saveDir, "slot_$slotId")
            add(slotId to File(dir, DB_FILE_NAME))
            add(slotId to File(dir, "${DB_FILE_NAME}.previous"))
            dir.listFiles { file ->
                file.name.startsWith("${DB_FILE_NAME}.previous.pending")
            }?.forEach { add(slotId to it) }
        }

        // live DB 原子替换会把旧库保存在 databases/ 下；启动或读档中断后也必须可发现。
        val liveDb = context.getDatabasePath(DB_FILE_NAME)
        add(-1 to File(liveDb.parentFile, "${DB_FILE_NAME}.previous"))
        liveDb.parentFile?.listFiles { file ->
            file.name.startsWith("${DB_FILE_NAME}.previous.pending")
        }?.forEach { add(-1 to it) }
        add(-1 to File(liveDb.parentFile, "${DB_FILE_NAME}.loading"))
        add(-1 to File(liveDb.parentFile, "${DB_FILE_NAME}.startup-recovery"))
        add(-1 to File(context.filesDir, "${DB_FILE_NAME}.corrupted_backup"))
    }.mapNotNull { (slotId, file) ->
        inspectSnapshot(file)?.let { revision ->
            RecoveryCandidate(slotId, file, revision)
        }
    }.sortedWith(
        compareByDescending<RecoveryCandidate> { it.revision }
            .thenByDescending { it.file.lastModified() }
    )

    fun hasAnyBackupData(): Boolean = recoveryCandidates().isNotEmpty()

    /**
     * 自动恢复最新的有效本地备份。历史 slot 1-3 只读，不会被覆盖。
     * 非标准槽文件会先复制到隔离的临时恢复槽，再复用完整迁移和原子加载流程。
     */
    suspend fun loadLatestBackup(): Boolean {
        val candidate = recoveryCandidates().firstOrNull() ?: return false
        val slotId = candidate.slotId
        val source = candidate.file
        if (slotId >= 0 && source.name == DB_FILE_NAME) {
            return loadGame(slotId)
        }

        return saveMutex.withLock {
            withContext(Dispatchers.IO) {
                val recoverySlotId = 99
                val recoveryDir = File(saveDir, "slot_$recoverySlotId").also { it.mkdirs() }
                val recoveryDb = File(recoveryDir, DB_FILE_NAME)
                val staged = File(recoveryDir, "${DB_FILE_NAME}.writing")
                try {
                    source.copyTo(staged, overwrite = true)
                    if (!validateSnapshot(staged)) return@withContext false
                    replaceFileAtomically(staged, recoveryDb)
                    loadGameUnlocked(recoverySlotId)
                } finally {
                    staged.delete()
                    recoveryDb.delete()
                    recoveryDir.delete()
                }
            }
        }
    }

    fun hasAutoSaveData(): Boolean = hasAnyBackupData()

    /**
     * Checkpoint the WAL to ensure all pending writes are flushed to the main DB file.
     * This must be called before copying the DB file for save/load operations.
     */
    private fun checkpointDatabase() {
        try {
            val db = database.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        } catch (e: Exception) {
            android.util.Log.e("SaveManager", "Failed to checkpoint database", e)
            throw e
        }
    }

    /**
     * 校验 SQLite 物理完整性和最小业务完整性，并返回存档修订时间。
     * 空的 schools 表不是可恢复存档，不能用来覆盖玩家当前或历史进度。
     */
    private fun inspectSnapshot(snapshot: File): Long? {
        if (!snapshot.exists() || snapshot.length() == 0L) return null
        return try {
            SQLiteDatabase.openDatabase(
                snapshot.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
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
                    return@use snapshot.lastModified().coerceAtLeast(1L)
                }

                db.rawQuery(
                    "SELECT MAX(lastSaveTime) FROM schools",
                    null
                ).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.isNull(0)) {
                        snapshot.lastModified().coerceAtLeast(1L)
                    } else {
                        cursor.getLong(0)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "SaveManager",
                "Snapshot validation failed: ${snapshot.absolutePath}",
                e
            )
            null
        }
    }

    private fun validateSnapshot(snapshot: File): Boolean =
        inspectSnapshot(snapshot) != null

    /**
     * 原子替换文件，并永久保留替换前的一代为 .previous。
     * .previous.pending 只在替换窗口内保存更旧的一代，用于失败回滚。
     */
    private fun replaceFileAtomically(source: File, destination: File) {
        val backup = File(
            destination.parentFile,
            "${destination.name}.previous"
        )
        val pendingBackup = File(
            destination.parentFile,
            "${destination.name}.previous.pending.${System.currentTimeMillis()}"
        )

        var destinationPreserved = false
        var olderBackupPreserved = false
        try {
            if (destination.exists()) {
                if (backup.exists()) {
                    if (!backup.renameTo(pendingBackup)) {
                        throw IllegalStateException(
                            "Unable to preserve previous backup before replacement"
                        )
                    }
                    olderBackupPreserved = true
                }
                if (!destination.renameTo(backup)) {
                    throw IllegalStateException(
                        "Unable to preserve existing file before replacement"
                    )
                }
                destinationPreserved = true
            }

            if (!source.renameTo(destination)) {
                throw IllegalStateException(
                    "Unable to atomically install replacement file: ${destination.absolutePath}"
                )
            }

            // 新文件已就位；backup 保留为上一代，旧两代之外的 pending 才可删除。
            pendingBackup.delete()
        } catch (e: Exception) {
            // 只有当前文件已成功移到 backup 后，才允许删除不完整的新 destination 并回滚。
            // 若第一次 rename 就失败，原 destination 仍是唯一好文件，绝不能触碰。
            var destinationRestored = !destinationPreserved
            if (destinationPreserved) {
                destination.delete()
                destinationRestored = backup.exists() && backup.renameTo(destination)
                if (!destinationRestored) {
                    android.util.Log.e(
                        "SaveManager",
                        "Failed to restore destination after replacement error; " +
                            "backup files were left untouched: ${destination.absolutePath}"
                    )
                }
            }
            if (
                destinationRestored &&
                olderBackupPreserved &&
                pendingBackup.exists()
            ) {
                backup.delete()
                if (!pendingBackup.renameTo(backup)) {
                    android.util.Log.e(
                        "SaveManager",
                        "Failed to restore previous generation: ${backup.absolutePath}"
                    )
                }
            }
            throw e
        }
    }

    suspend fun saveGame(
        slotId: Int,
        schoolName: String,
        currentYear: Int,
        currentMonth: Int,
        cash: Double,
        reputation: Long,
        schoolId: String = ""
    ): Boolean = saveMutex.withLock {
        if (restoreInProgress) {
            android.util.Log.w(
                "SaveManager",
                "Skipped save while database restore is awaiting process restart"
            )
            return@withLock false
        }
        withContext(Dispatchers.IO) {
        try {
            // Checkpoint WAL before saving to ensure all data is in main DB file
            checkpointDatabase()

            val slotDir = File(saveDir, "slot_$slotId").also { it.mkdirs() }
            val targetDbFile = File(slotDir, DB_FILE_NAME)
            val snapshotFile = File(slotDir, "${DB_FILE_NAME}.writing")
            snapshotFile.delete()

            // Always write VACUUM INTO to a new file. SQLite refuses an existing target;
            // the former implementation therefore made every overwrite save fail silently.
            val db = database.openHelper.writableDatabase
            val escapedSnapshotPath = snapshotFile.absolutePath.replace("'", "''")
            db.execSQL("VACUUM INTO '$escapedSnapshotPath'")

            if (!validateSnapshot(snapshotFile)) {
                snapshotFile.delete()
                throw IllegalStateException("Generated save snapshot is invalid")
            }

            // VACUUM INTO may reset user_version on some Android SQLite versions. Preserve the
            // exact Room schema version before atomically replacing the previous snapshot.
            SQLiteDatabase.openDatabase(
                snapshotFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { snapshotDb ->
                snapshotDb.version = APP_DATABASE_SCHEMA_VERSION
            }

            replaceFileAtomically(snapshotFile, targetDbFile)
            updateMeta(slotId, schoolName, currentYear, currentMonth, cash, reputation, schoolId)
            android.util.Log.i("SaveManager", "Saved validated snapshot to slot $slotId (${targetDbFile.length()} bytes)")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        }  // withContext
    }  // saveMutex.withLock

    suspend fun loadGame(slotId: Int): Boolean = saveMutex.withLock {
        withContext(Dispatchers.IO) { loadGameUnlocked(slotId) }
    }

    private suspend fun loadGameUnlocked(slotId: Int): Boolean {
        return try {
                val slotDir = File(saveDir, "slot_$slotId")
                val slotDbFile = File(slotDir, DB_FILE_NAME)
                if (!validateSnapshot(slotDbFile)) {
                    android.util.Log.e("SaveManager", "Refusing to load invalid slot $slotId")
                    return false
                }

                val dbFile = context.getDatabasePath(DB_FILE_NAME)
                val stagedDbFile = File(dbFile.parentFile, "${DB_FILE_NAME}.loading")
                stagedDbFile.delete()
                slotDbFile.copyTo(stagedDbFile, overwrite = true)

                // Migrate and validate the copied slot before touching the player's active database.
                migrateLoadedDatabase(stagedDbFile)
                if (!validateSnapshot(stagedDbFile)) {
                    stagedDbFile.delete()
                    throw IllegalStateException("Migrated slot snapshot is invalid")
                }

                // 从这一刻开始阻止旧 Activity/ViewModel 的生命周期自动保存。
                // 即使它们已排队等待 saveMutex，进入 saveGame 后也会再次检查此闸门。
                restoreInProgress = true

                // No engine coroutine may keep a DAO/Room write alive while the database file changes.
                gameEngine.stopAndJoin()
                database.close()

                // Clear journal companions only after Room is closed. The staged DB has already
                // passed integrity validation, and replaceFileAtomically preserves the live DB
                // until the replacement succeeds.
                File(dbFile.parent, "${DB_FILE_NAME}-shm").delete()
                File(dbFile.parent, "${DB_FILE_NAME}-wal").delete()
                File(dbFile.parent, "${DB_FILE_NAME}-journal").delete()
                replaceFileAtomically(stagedDbFile, dbFile)

                // 从 meta 读取该存档的 schoolId 并立即同步到 DataStore。
                // 避免读档后 DataStore 仍保留旧 schoolId，导致学生/教师/课程等按 schoolId 过滤的查询全为空。
                val meta = readMeta()
                val slotData = meta.optJSONObject("slot_$slotId")
                val savedSchoolId = slotData?.optString("schoolId", "")?.takeIf { it.isNotBlank() }
                if (savedSchoolId != null) {
                    settingsDataStore.setSchoolId(savedSchoolId)
                    android.util.Log.i("SaveManager", "Restored schoolId for slot $slotId: $savedSchoolId")
                } else {
                    // 旧存档没有记录 schoolId，尝试从 DB 中的学校同步（GameEngine.start 也会做兜底同步）
                    try {
                        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                        val cursor = db.rawQuery("SELECT id FROM schools LIMIT 1", null)
                        val dbSchoolId = cursor.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                        db.close()
                        if (!dbSchoolId.isNullOrBlank()) {
                            settingsDataStore.setSchoolId(dbSchoolId)
                            android.util.Log.i("SaveManager", "Synced schoolId from loaded DB: $dbSchoolId")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SaveManager", "Failed to sync schoolId from DB", e)
                    }
                }

                // 读档接口仅为兼容旧版本保留；4.1 不再提供任何玩家可见的读档入口。
                markJustLoaded()
                android.util.Log.i("SaveManager", "Loaded validated slot $slotId")
                true
            } catch (e: Exception) {
                android.util.Log.e("SaveManager", "Failed to load slot $slotId", e)
                if (restoreInProgress) {
                    // Room 已关闭或 live DB 切换已经开始，旧进程不可继续使用。
                    // 即使替换失败，原库/.previous/启动恢复候选仍被保留；重启后统一恢复。
                    markJustLoaded()
                    true
                } else {
                    false
                }
            }
    }

    /**
     * 将早于 21 的异常旧存档补齐到可迁移基线。21–23 保留原始 user_version，
     * 由 Room 的正式 Migration 顺序升级并更新 identity hash，禁止把新库降级伪装成旧库。
     */
    private fun migrateLoadedDatabase(dbFile: File) {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val sourceVersion = db.version
            require(sourceVersion <= APP_DATABASE_SCHEMA_VERSION) {
                "Save schema $sourceVersion is newer than supported schema $APP_DATABASE_SCHEMA_VERSION"
            }
            if (sourceVersion >= LEGACY_REPAIR_BASELINE_VERSION) {
                return
            }

            // --- schools 表 ---
            ensureTableExists(db, "schools", """
                CREATE TABLE IF NOT EXISTS `schools` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `principalName` TEXT NOT NULL DEFAULT '张校长',
                    `cash` REAL NOT NULL,
                    `marketCap` REAL NOT NULL,
                    `reputation` INTEGER NOT NULL,
                    `starRating` REAL NOT NULL,
                    `foundedYear` INTEGER NOT NULL,
                    `currentYear` INTEGER NOT NULL,
                    `currentMonth` INTEGER NOT NULL,
                    `currentDay` INTEGER NOT NULL,
                    `campusLevel` INTEGER NOT NULL,
                    `levelUpYear` INTEGER NOT NULL DEFAULT 1988,
                    `maxTeachers` INTEGER NOT NULL,
                    `branchSchools` INTEGER NOT NULL,
                    `hasOwnTextbook` INTEGER NOT NULL,
                    `hasOwnTech` INTEGER NOT NULL,
                    `totalCoursesReleased` INTEGER NOT NULL,
                    `totalRevenue` REAL NOT NULL,
                    `wasNearBankrupt` INTEGER NOT NULL DEFAULT 0,
                    `facilitiesJson` TEXT NOT NULL DEFAULT '[]',
                    `studentLifeJson` TEXT NOT NULL DEFAULT '',
                    `marketingCampaignsJson` TEXT NOT NULL DEFAULT '[]',
                    `stockInvestmentsJson` TEXT NOT NULL DEFAULT '[]',
                    `reputationJson` TEXT NOT NULL DEFAULT '',
                    `achievementJson` TEXT NOT NULL DEFAULT '',
                    `milestoneJson` TEXT NOT NULL DEFAULT '',
                    `teacherDevJson` TEXT NOT NULL DEFAULT '',
                    `clubJson` TEXT NOT NULL DEFAULT '',
                    `scholarshipJson` TEXT NOT NULL DEFAULT '',
                    `expansionJson` TEXT NOT NULL DEFAULT '',
                    `governmentJson` TEXT NOT NULL DEFAULT '',
                    `parentJson` TEXT NOT NULL DEFAULT '',
                    `policyJson` TEXT NOT NULL DEFAULT '',
                    `seasonalJson` TEXT NOT NULL DEFAULT '',
                    `conferenceJson` TEXT NOT NULL DEFAULT '',
                    `clubActivityJson` TEXT NOT NULL DEFAULT '',
                    `timetableJson` TEXT NOT NULL DEFAULT '',
                    `examJson` TEXT NOT NULL DEFAULT '',
                    `teachingConfigJson` TEXT NOT NULL DEFAULT '',
                    `principalJson` TEXT NOT NULL DEFAULT '',
                    `suggestionBoxJson` TEXT NOT NULL DEFAULT '',
                    `lastYearEndProcessingYear` INTEGER NOT NULL DEFAULT 1988,
                    `lastMonthlySettlementYear` INTEGER NOT NULL DEFAULT 1988,
                    `lastMonthlySettlementMonth` INTEGER NOT NULL DEFAULT 8,
                    `lastSaveTime` INTEGER NOT NULL
                )
            """.trimIndent())

            ensureColumns(db, "schools", mapOf(
                "principalName" to "TEXT NOT NULL DEFAULT '张校长'",
                "levelUpYear" to "INTEGER NOT NULL DEFAULT 1988",
                "wasNearBankrupt" to "INTEGER NOT NULL DEFAULT 0",
                "facilitiesJson" to "TEXT NOT NULL DEFAULT '[]'",
                "studentLifeJson" to "TEXT NOT NULL DEFAULT ''",
                "marketingCampaignsJson" to "TEXT NOT NULL DEFAULT '[]'",
                "reputationJson" to "TEXT NOT NULL DEFAULT ''",
                "achievementJson" to "TEXT NOT NULL DEFAULT ''",
                "milestoneJson" to "TEXT NOT NULL DEFAULT ''",
                "teacherDevJson" to "TEXT NOT NULL DEFAULT ''",
                "clubJson" to "TEXT NOT NULL DEFAULT ''",
                "scholarshipJson" to "TEXT NOT NULL DEFAULT ''",
                "expansionJson" to "TEXT NOT NULL DEFAULT ''",
                "governmentJson" to "TEXT NOT NULL DEFAULT ''",
                "parentJson" to "TEXT NOT NULL DEFAULT ''",
                "policyJson" to "TEXT NOT NULL DEFAULT ''",
                "seasonalJson" to "TEXT NOT NULL DEFAULT ''",
                "conferenceJson" to "TEXT NOT NULL DEFAULT ''",
                "clubActivityJson" to "TEXT NOT NULL DEFAULT ''",
                "timetableJson" to "TEXT NOT NULL DEFAULT ''",
                "examJson" to "TEXT NOT NULL DEFAULT ''",
                "teachingConfigJson" to "TEXT NOT NULL DEFAULT ''",
                "stockInvestmentsJson" to "TEXT NOT NULL DEFAULT '[]'",
                "statisticsJson" to "TEXT NOT NULL DEFAULT ''",
                "financialReportJson" to "TEXT NOT NULL DEFAULT ''",
                "pressureJson" to "TEXT NOT NULL DEFAULT ''",
                "competitorJson" to "TEXT NOT NULL DEFAULT ''",
                "crisisJson" to "TEXT NOT NULL DEFAULT ''",
                "alumniJson" to "TEXT NOT NULL DEFAULT ''",
                "employmentJson" to "TEXT NOT NULL DEFAULT ''",
                "headTeacherMapJson" to "TEXT NOT NULL DEFAULT ''",
                "principalJson" to "TEXT NOT NULL DEFAULT ''",
                "suggestionBoxJson" to "TEXT NOT NULL DEFAULT ''",
                "classTierMapJson" to "TEXT NOT NULL DEFAULT ''",
                "lastYearEndProcessingYear" to
                    "INTEGER NOT NULL DEFAULT 1988",
                "lastMonthlySettlementYear" to
                    "INTEGER NOT NULL DEFAULT 1988",
                "lastMonthlySettlementMonth" to
                    "INTEGER NOT NULL DEFAULT 8"
            ))

            // --- teachers 表 ---
            ensureTableExists(db, "teachers", """
                CREATE TABLE IF NOT EXISTS `teachers` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `gender` TEXT NOT NULL DEFAULT 'MALE',
                    `level` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `teaching` INTEGER NOT NULL,
                    `research` INTEGER NOT NULL,
                    `management` INTEGER NOT NULL,
                    `psychology` INTEGER NOT NULL,
                    `salary` REAL NOT NULL,
                    `fatigue` INTEGER NOT NULL,
                    `loyalty` INTEGER NOT NULL,
                    `isWorking` INTEGER NOT NULL,
                    `isOnVacation` INTEGER NOT NULL,
                    `hireDate` INTEGER NOT NULL,
                    `schoolId` TEXT NOT NULL,
                    `traits` TEXT NOT NULL DEFAULT '',
                    `avatarIndex` INTEGER NOT NULL DEFAULT 1,
                    `pendingResignation` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            ensureColumns(db, "teachers", mapOf(
                "gender" to "TEXT NOT NULL DEFAULT 'MALE'",
                "traits" to "TEXT NOT NULL DEFAULT ''",
                "avatarIndex" to "INTEGER NOT NULL DEFAULT 1",
                "pendingResignation" to "INTEGER NOT NULL DEFAULT 0",
                "experiencePoints" to "INTEGER NOT NULL DEFAULT 0"
            ))

            // --- students 表 ---
            ensureTableExists(db, "students", """
                CREATE TABLE IF NOT EXISTS `students` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `courseId` TEXT NOT NULL,
                    `schoolId` TEXT NOT NULL,
                    `classId` TEXT,
                    `gradeLevel` TEXT NOT NULL DEFAULT 'GRADE_1',
                    `intelligence` REAL NOT NULL DEFAULT 50.0,
                    `physical` REAL NOT NULL DEFAULT 50.0,
                    `social` REAL NOT NULL DEFAULT 50.0,
                    `creativity` REAL NOT NULL DEFAULT 50.0,
                    `morality` REAL NOT NULL DEFAULT 50.0,
                    `backgroundTier` TEXT NOT NULL DEFAULT 'NORMAL',
                    `talent` REAL NOT NULL DEFAULT 0.8,
                    `motivation` REAL NOT NULL DEFAULT 0.85,
                    `traitsJson` TEXT NOT NULL DEFAULT '[]',
                    `status` TEXT NOT NULL DEFAULT 'ENROLLED',
                    `semesterMastery` REAL NOT NULL DEFAULT 0.0,
                    `satisfaction` REAL NOT NULL DEFAULT 70.0,
                    `academicScore` REAL NOT NULL DEFAULT 0.0,
                    `gaoKaoScore` REAL NOT NULL DEFAULT 0.0,
                    `admittedUniversity` TEXT,
                    `universityTier` TEXT,
                    `healthStatus` TEXT NOT NULL DEFAULT 'HEALTHY',
                    `mealQuality` REAL NOT NULL DEFAULT 50.0,
                    `dormSatisfaction` REAL NOT NULL DEFAULT 50.0,
                    `exerciseLevel` REAL NOT NULL DEFAULT 30.0,
                    `consecutiveSickDays` INTEGER NOT NULL DEFAULT 0,
                    `enrollYear` INTEGER NOT NULL DEFAULT 0,
                    `enrollMonth` INTEGER NOT NULL DEFAULT 0,
                    `lastPromotionYear` INTEGER NOT NULL DEFAULT 0,
                    `graduateYear` INTEGER,
                    `graduateMonth` INTEGER,
                    `graduationProjectionState` INTEGER NOT NULL DEFAULT 0,
                    `reviewRating` INTEGER,
                    `reviewComment` TEXT,
                    `reviewReputationImpact` INTEGER
                )
            """.trimIndent())

            ensureColumns(db, "students", mapOf(
                "classId" to "TEXT",
                "gradeLevel" to "TEXT NOT NULL DEFAULT 'GRADE_1'",
                "intelligence" to "REAL NOT NULL DEFAULT 50.0",
                "physical" to "REAL NOT NULL DEFAULT 50.0",
                "social" to "REAL NOT NULL DEFAULT 50.0",
                "creativity" to "REAL NOT NULL DEFAULT 50.0",
                "morality" to "REAL NOT NULL DEFAULT 50.0",
                "backgroundTier" to "TEXT NOT NULL DEFAULT 'NORMAL'",
                "talent" to "REAL NOT NULL DEFAULT 0.8",
                "motivation" to "REAL NOT NULL DEFAULT 0.85",
                "traitsJson" to "TEXT NOT NULL DEFAULT '[]'",
                "status" to "TEXT NOT NULL DEFAULT 'ENROLLED'",
                "semesterMastery" to "REAL NOT NULL DEFAULT 0.0",
                "satisfaction" to "REAL NOT NULL DEFAULT 70.0",
                "academicScore" to "REAL NOT NULL DEFAULT 0.0",
                "gaoKaoScore" to "REAL NOT NULL DEFAULT 0.0",
                "admittedUniversity" to "TEXT",
                "universityTier" to "TEXT",
                "healthStatus" to "TEXT NOT NULL DEFAULT 'HEALTHY'",
                "mealQuality" to "REAL NOT NULL DEFAULT 50.0",
                "dormSatisfaction" to "REAL NOT NULL DEFAULT 50.0",
                "exerciseLevel" to "REAL NOT NULL DEFAULT 30.0",
                "consecutiveSickDays" to "INTEGER NOT NULL DEFAULT 0",
                "enrollYear" to "INTEGER NOT NULL DEFAULT 0",
                "enrollMonth" to "INTEGER NOT NULL DEFAULT 0",
                "lastPromotionYear" to "INTEGER NOT NULL DEFAULT 0",
                "graduateYear" to "INTEGER",
                "graduateMonth" to "INTEGER",
                "graduationProjectionState" to
                    "INTEGER NOT NULL DEFAULT 0",
                "reviewRating" to "INTEGER",
                "reviewComment" to "TEXT",
                "reviewReputationImpact" to "INTEGER"
            ))

            // --- courses 表 ---
            ensureTableExists(db, "courses", """
                CREATE TABLE IF NOT EXISTS `courses` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `subject` TEXT NOT NULL,
                    `theme` TEXT NOT NULL,
                    `courseType` TEXT NOT NULL,
                    `targetDistrict` TEXT NOT NULL,
                    `scale` TEXT NOT NULL,
                    `preparationProgress` REAL NOT NULL,
                    `problemCount` INTEGER NOT NULL,
                    `qualityScore` REAL NOT NULL,
                    `designScore` REAL NOT NULL,
                    `status` TEXT NOT NULL,
                    `teamIdsJson` TEXT NOT NULL,
                    `methodIdsJson` TEXT NOT NULL,
                    `ipId` TEXT,
                    `enrollment` INTEGER NOT NULL,
                    `revenue` REAL NOT NULL,
                    `monthlyEnrollment` INTEGER NOT NULL,
                    `releaseDate` INTEGER,
                    `releaseYear` INTEGER,
                    `releaseMonth` INTEGER,
                    `heat` REAL NOT NULL,
                    `marketingSpend` REAL NOT NULL,
                    `schoolId` TEXT NOT NULL
                )
            """.trimIndent())

            // --- teaching_methods 表 ---
            ensureTableExists(db, "teaching_methods", """
                CREATE TABLE IF NOT EXISTS `teaching_methods` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `unlockYear` INTEGER NOT NULL,
                    `cost` REAL NOT NULL,
                    `researchDays` INTEGER NOT NULL,
                    `bonusType` TEXT NOT NULL,
                    `bonusValue` REAL NOT NULL,
                    `prerequisiteIdsJson` TEXT NOT NULL,
                    `isUnlocked` INTEGER NOT NULL,
                    `isResearching` INTEGER NOT NULL DEFAULT 0,
                    `remainingResearchDays` INTEGER NOT NULL DEFAULT 0,
                    `schoolId` TEXT NOT NULL
                )
            """.trimIndent())
            ensureColumns(db, "teaching_methods", mapOf(
                "isResearching" to "INTEGER NOT NULL DEFAULT 0",
                "remainingResearchDays" to "INTEGER NOT NULL DEFAULT 0"
            ))

            // --- stocks 表 ---
            ensureTableExists(db, "stocks", """
                CREATE TABLE IF NOT EXISTS `stocks` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `basePrice` REAL NOT NULL,
                    `currentPrice` REAL NOT NULL,
                    `volatility` REAL NOT NULL,
                    `trend` TEXT NOT NULL,
                    `sector` TEXT NOT NULL,
                    `schoolId` TEXT NOT NULL
                )
            """.trimIndent())

            // --- stock_holdings 表 ---
            ensureTableExists(db, "stock_holdings", """
                CREATE TABLE IF NOT EXISTS `stock_holdings` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `stockId` TEXT NOT NULL,
                    `shares` INTEGER NOT NULL,
                    `avgBuyPrice` REAL NOT NULL,
                    `schoolId` TEXT NOT NULL
                )
            """.trimIndent())

            // --- stock_price_history 表 ---
            ensureTableExists(db, "stock_price_history", """
                CREATE TABLE IF NOT EXISTS `stock_price_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `stockId` TEXT NOT NULL,
                    `schoolId` TEXT NOT NULL,
                    `gameDay` INTEGER NOT NULL,
                    `open` REAL NOT NULL,
                    `close` REAL NOT NULL,
                    `high` REAL NOT NULL,
                    `low` REAL NOT NULL
                )
            """.trimIndent())

            // 旧存档股票名称迁移（真实公司名 → 谐音虚构名）
            try {
                val stockNameMapping = mapOf(
                    "新东方教育" to "新东升教育",
                    "好未来" to "优未来集团",
                    "猿辅导" to "灵猿辅导",
                    "有道" to "易学有道",
                    "腾讯控股" to "腾达控股",
                    "阿里巴巴" to "阿里讯商",
                    "字节跳动" to "字符跃动",
                    "小米集团" to "米芯科技",
                    "贵州茅台" to "黔州酒业",
                    "海底捞" to "江湖捞火锅",
                    "李宁" to "力宁体育",
                    "恒瑞医药" to "恒锐医药",
                    "迈瑞医疗" to "迈睿医疗",
                    "招商银行" to "招财银行",
                    "中国平安" to "神州平安",
                    "宁德时代" to "安德时代",
                    "比亚迪" to "比亚达",
                    "万科" to "万嘉地产",
                    "米哈游" to "米哈森",
                    "B站" to "C站弹幕"
                )
                stockNameMapping.forEach { (oldName, newName) ->
                    db.execSQL("UPDATE stocks SET name = ? WHERE name = ?", arrayOf(newName, oldName))
                }
            } catch (e: Exception) {
                // 非致命，旧存档可能没有stocks表
                e.printStackTrace()
            }

            // 只声明为手工修补所覆盖的 21 基线；下一次 Room 打开时必须继续执行
            // 21→22 和 22→23 的正式迁移，以创建唯一索引并刷新 identity hash。
            db.version = LEGACY_REPAIR_BASELINE_VERSION
        } finally {
            db.close()
        }
    }

    /**
     * 确保表存在，不存在则创建
     */
    private fun ensureTableExists(db: SQLiteDatabase, tableName: String, createSql: String) {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        val exists = cursor.use { it.moveToFirst() }
        if (!exists) {
            db.execSQL(createSql)
        }
    }

    /**
     * 检查表中是否有缺失列，有则 ALTER TABLE ADD COLUMN
     */
    private fun ensureColumns(db: SQLiteDatabase, tableName: String, columns: Map<String, String>) {
        val existingColumns = mutableSetOf<String>()
        val cursor = db.rawQuery("PRAGMA table_info(`$tableName`)", null)
        cursor.use {
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                existingColumns.add(it.getString(nameIndex))
            }
        }
        for ((columnName, columnDef) in columns) {
            if (columnName !in existingColumns) {
                db.execSQL(
                    "ALTER TABLE `$tableName` " +
                        "ADD COLUMN `$columnName` $columnDef"
                )
            }
        }
    }

    private fun readMeta(): JSONObject {
        return try {
            if (metaFile.exists()) {
                JSONObject(metaFile.readText())
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun writeMeta(meta: JSONObject) {
        val writingFile = File(saveDir, "meta.json.writing")
        writingFile.writeText(meta.toString())
        replaceFileAtomically(writingFile, metaFile)
    }

    private fun updateMeta(
        slotId: Int,
        schoolName: String,
        currentYear: Int,
        currentMonth: Int,
        cash: Double,
        reputation: Long,
        schoolId: String = ""
    ) {
        val meta = readMeta()
        val slotData = JSONObject().apply {
            put("schoolName", schoolName)
            put("schoolId", schoolId)
            put("saveTime", System.currentTimeMillis())
            put("currentYear", currentYear)
            put("currentMonth", currentMonth)
            put("cash", cash)
            put("reputation", reputation)
        }
        meta.put("slot_$slotId", slotData)
        writeMeta(meta)
    }

    private fun removeMeta(slotId: Int) {
        val meta = readMeta()
        meta.remove("slot_$slotId")
        writeMeta(meta)
    }
}
