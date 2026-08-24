package com.arktools.xiaozhang.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arktools.xiaozhang.data.local.AppDatabase
import com.arktools.xiaozhang.data.local.dao.CourseDao
import com.arktools.xiaozhang.data.local.dao.SchoolDao
import com.arktools.xiaozhang.data.local.dao.StockDao
import com.arktools.xiaozhang.data.local.dao.StudentDao
import com.arktools.xiaozhang.data.local.dao.TeacherDao
import com.arktools.xiaozhang.data.local.dao.TeachingMethodDao
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import com.arktools.xiaozhang.data.save.SaveManager
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.data.repository.CourseRepositoryImpl
import com.arktools.xiaozhang.data.repository.SchoolRepositoryImpl
import com.arktools.xiaozhang.data.repository.StockRepositoryImpl
import com.arktools.xiaozhang.data.repository.StudentRepositoryImpl
import com.arktools.xiaozhang.data.repository.TeacherRepositoryImpl
import com.arktools.xiaozhang.data.repository.ResearchRepositoryImpl
import com.arktools.xiaozhang.domain.repository.CourseRepository
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StockRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.domain.repository.ResearchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private fun hasColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String
    ): Boolean {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    return true
                }
            }
        }
        return false
    }

    /** SQLite 不支持 ADD COLUMN IF NOT EXISTS，先精确检查列再补齐。 */
    private fun safeAddColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        type: String
    ) {
        if (!hasColumn(db, table, column)) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
        }
    }

    /**
     * Room Migration 14→15：补齐旧版数据库缺失的列，保留用户游戏数据。
     * 使用 ALTER TABLE ADD COLUMN + safeAddColumn 忽略已存在列。
     * 所有迁移路径均为幂等操作，确保从任何旧版本升级都不会丢失数据。
     */
    private fun installManagerStateTriggers(db: SupportSQLiteDatabase) {
        val fields = listOf(
            "facilitiesJson", "studentLifeJson", "marketingCampaignsJson", "stockInvestmentsJson",
            "reputationJson", "achievementJson", "milestoneJson", "teacherDevJson", "clubJson",
            "scholarshipJson", "expansionJson", "governmentJson", "parentJson", "policyJson",
            "seasonalJson", "conferenceJson", "clubActivityJson", "timetableJson", "examJson",
            "teachingConfigJson", "statisticsJson", "financialReportJson", "pressureJson",
            "competitorJson", "crisisJson", "alumniJson", "employmentJson", "headTeacherMapJson",
            "classTierMapJson", "suggestionBoxJson"
        )
        fields.forEach { field ->
            db.execSQL("DROP TRIGGER IF EXISTS `move_${field}_to_manager_state`")
            db.execSQL("DROP TRIGGER IF EXISTS `move_${field}_to_manager_chunks`")
            val chunkStatements = buildString {
                for (index in 0 until 128) {
                    val offset = index * 32_000 + 1
                    append(
                        "INSERT OR REPLACE INTO `school_manager_state_chunks` " +
                            "(`schoolId`, `stateKey`, `chunkIndex`, `payload`, `updatedAt`) " +
                            "SELECT NEW.`id`, '$field', $index, " +
                            "substr(NEW.`$field`, $offset, 32000), NEW.`lastSaveTime` " +
                            "WHERE length(NEW.`$field`) >= $offset; "
                    )
                }
            }
            db.execSQL(
                "CREATE TRIGGER `move_${field}_to_manager_chunks` " +
                    "AFTER UPDATE OF `$field` ON `schools` " +
                    "WHEN NEW.`$field` != '' BEGIN " +
                    "DELETE FROM `school_manager_state_chunks` " +
                    "WHERE `schoolId` = NEW.`id` AND `stateKey` = '$field'; " +
                    chunkStatements +
                    "UPDATE `schools` SET `$field` = '' WHERE `id` = NEW.`id`; " +
                    "END"
            )
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // --- schools 表: 补齐可能缺失的列 ---
            safeAddColumn(db, "schools", "stockInvestmentsJson", "TEXT NOT NULL DEFAULT '[]'")
            safeAddColumn(db, "schools", "principalName", "TEXT NOT NULL DEFAULT '张校长'")
            safeAddColumn(db, "schools", "levelUpYear", "INTEGER NOT NULL DEFAULT 1988")
            safeAddColumn(db, "schools", "wasNearBankrupt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "schools", "facilitiesJson", "TEXT NOT NULL DEFAULT '[]'")
            safeAddColumn(db, "schools", "studentLifeJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "marketingCampaignsJson", "TEXT NOT NULL DEFAULT '[]'")
            safeAddColumn(db, "schools", "reputationJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "achievementJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "milestoneJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "teacherDevJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "clubJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "scholarshipJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "expansionJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "governmentJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "parentJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "policyJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "seasonalJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "conferenceJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "clubActivityJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "timetableJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "examJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "teachingConfigJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "statisticsJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "financialReportJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "pressureJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "competitorJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "crisisJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "alumniJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "employmentJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "headTeacherMapJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "classTierMapJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "schools", "principalJson", "TEXT NOT NULL DEFAULT ''")

            // --- teachers 表 ---
            safeAddColumn(db, "teachers", "gender", "TEXT NOT NULL DEFAULT 'MALE'")
            safeAddColumn(db, "teachers", "traits", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "teachers", "avatarIndex", "INTEGER NOT NULL DEFAULT 1")
            safeAddColumn(db, "teachers", "pendingResignation", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "teachers", "experiencePoints", "INTEGER NOT NULL DEFAULT 0")

            // --- students 表 ---
            safeAddColumn(db, "students", "classId", "TEXT")
            safeAddColumn(db, "students", "gradeLevel", "TEXT NOT NULL DEFAULT 'GRADE_1'")
            safeAddColumn(db, "students", "intelligence", "REAL NOT NULL DEFAULT 50.0")
            safeAddColumn(db, "students", "physical", "REAL NOT NULL DEFAULT 50.0")
            safeAddColumn(db, "students", "social", "REAL NOT NULL DEFAULT 50.0")
            safeAddColumn(db, "students", "creativity", "REAL NOT NULL DEFAULT 50.0")
            safeAddColumn(db, "students", "morality", "REAL NOT NULL DEFAULT 50.0")
            safeAddColumn(db, "students", "backgroundTier", "TEXT NOT NULL DEFAULT 'NORMAL'")
            safeAddColumn(db, "students", "talent", "REAL NOT NULL DEFAULT 0.8")
            safeAddColumn(db, "students", "motivation", "REAL NOT NULL DEFAULT 0.85")
            safeAddColumn(db, "students", "traitsJson", "TEXT NOT NULL DEFAULT '[]'")
            safeAddColumn(db, "students", "status", "TEXT NOT NULL DEFAULT 'ENROLLED'")
            safeAddColumn(db, "students", "semesterMastery", "REAL NOT NULL DEFAULT 0.0")
            safeAddColumn(db, "students", "satisfaction", "REAL NOT NULL DEFAULT 70.0")
            safeAddColumn(db, "students", "academicScore", "REAL NOT NULL DEFAULT 0.0")
            safeAddColumn(db, "students", "gaoKaoScore", "REAL NOT NULL DEFAULT 0.0")
            safeAddColumn(db, "students", "admittedUniversity", "TEXT")
            safeAddColumn(db, "students", "universityTier", "TEXT")
            safeAddColumn(db, "students", "healthStatus", "TEXT NOT NULL DEFAULT 'HEALTHY'")
            safeAddColumn(db, "students", "mealQuality", "REAL NOT NULL DEFAULT 50.0")
            safeAddColumn(db, "students", "dormSatisfaction", "REAL NOT NULL DEFAULT 50.0")
            safeAddColumn(db, "students", "exerciseLevel", "REAL NOT NULL DEFAULT 30.0")
            safeAddColumn(db, "students", "consecutiveSickDays", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "students", "enrollYear", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "students", "enrollMonth", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "students", "graduateYear", "INTEGER")
            safeAddColumn(db, "students", "graduateMonth", "INTEGER")
            safeAddColumn(db, "students", "reviewRating", "INTEGER")
            safeAddColumn(db, "students", "reviewComment", "TEXT")
            safeAddColumn(db, "students", "reviewReputationImpact", "INTEGER")

            // --- 确保 stocks / stock_holdings / stock_price_history 表存在 ---
            db.execSQL("""
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
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `stock_holdings` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `stockId` TEXT NOT NULL,
                    `shares` INTEGER NOT NULL,
                    `avgBuyPrice` REAL NOT NULL,
                    `schoolId` TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
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
        }
    }

    /**
     * 通用安全迁移：将所有 ALTER TABLE ADD COLUMN 操作作为一次幂等迁移，
     * 注册到所有可能的历史版本升级路径上，彻底替代 fallbackToDestructiveMigration。
     * 这样从任何旧版本升级都不会丢失用户数据。
     */
    private fun createSafeMigration(start: Int, end: Int): Migration {
        return object : Migration(start, end) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_14_15.migrate(db)  // 复用已有的迁移逻辑（幂等）
            }
        }
    }

    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `stock_holdings_v22`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stock_holdings_v22` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `stockId` TEXT NOT NULL,
                    `shares` INTEGER NOT NULL,
                    `avgBuyPrice` REAL NOT NULL,
                    `schoolId` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `stock_holdings_v22`
                    (`id`, `stockId`, `shares`, `avgBuyPrice`, `schoolId`)
                SELECT
                    MIN(`id`),
                    `stockId`,
                    SUM(`shares`),
                    CASE
                        WHEN SUM(`shares`) > 0
                        THEN SUM(`avgBuyPrice` * `shares`) / SUM(`shares`)
                        ELSE 0.0
                    END,
                    `schoolId`
                FROM `stock_holdings`
                GROUP BY `schoolId`, `stockId`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `stock_holdings`")
            db.execSQL("ALTER TABLE `stock_holdings_v22` RENAME TO `stock_holdings`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_stock_holdings_schoolId_stockId` " +
                    "ON `stock_holdings` (`schoolId`, `stockId`)"
            )
        }
    }

    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM `stock_price_history`
                WHERE `id` NOT IN (
                    SELECT MIN(`id`)
                    FROM `stock_price_history`
                    GROUP BY `schoolId`, `stockId`, `gameDay`
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_stock_price_history_schoolId_stockId_gameDay` " +
                    "ON `stock_price_history` (`schoolId`, `stockId`, `gameDay`)"
            )
        }
    }

    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            safeAddColumn(
                db,
                "students",
                "lastPromotionYear",
                "INTEGER NOT NULL DEFAULT 0"
            )
            safeAddColumn(
                db,
                "students",
                "graduationProjectionState",
                "INTEGER NOT NULL DEFAULT 0"
            )
            safeAddColumn(
                db,
                "schools",
                "lastYearEndProcessingYear",
                "INTEGER NOT NULL DEFAULT 1988"
            )
            db.execSQL(
                """
                UPDATE `students`
                SET `lastPromotionYear` = CASE
                    WHEN COALESCE(
                        (
                            SELECT `currentMonth`
                            FROM `schools`
                            WHERE `schools`.`id` = `students`.`schoolId`
                            LIMIT 1
                        ),
                        1
                    ) >= 6
                        THEN COALESCE(
                            (
                                SELECT `currentYear`
                                FROM `schools`
                                WHERE `schools`.`id` = `students`.`schoolId`
                                LIMIT 1
                            ),
                            0
                        )
                    ELSE MAX(
                        0,
                        COALESCE(
                            (
                                SELECT `currentYear`
                                FROM `schools`
                                WHERE `schools`.`id` = `students`.`schoolId`
                                LIMIT 1
                            ),
                            1
                        ) - 1
                    )
                END
                WHERE `status` IN ('ENROLLED', 'STUDYING')
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE `schools`
                SET `lastYearEndProcessingYear` = CASE
                    WHEN `currentMonth` >= 6 THEN `currentYear`
                    ELSE MAX(1988, `currentYear` - 1)
                END
                """.trimIndent()
            )
            db.execSQL(
                "UPDATE `students` SET `graduationProjectionState` = 1 " +
                    "WHERE `status` = 'GRADUATED'"
            )
        }
    }

    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            safeAddColumn(
                db,
                "schools",
                "suggestionBoxJson",
                "TEXT NOT NULL DEFAULT ''"
            )
            safeAddColumn(
                db,
                "schools",
                "lastMonthlySettlementYear",
                "INTEGER NOT NULL DEFAULT 1988"
            )
            safeAddColumn(
                db,
                "schools",
                "lastMonthlySettlementMonth",
                "INTEGER NOT NULL DEFAULT 8"
            )
            db.execSQL(
                "UPDATE `schools` SET " +
                    "`lastMonthlySettlementYear` = `currentYear`, " +
                    "`lastMonthlySettlementMonth` = `currentMonth`"
            )
        }
    }

    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `school_manager_states` (" +
                    "`schoolId` TEXT NOT NULL, `stateKey` TEXT NOT NULL, " +
                    "`payload` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`schoolId`, `stateKey`))"
            )
            val fields = listOf(
                "facilitiesJson", "studentLifeJson", "marketingCampaignsJson", "stockInvestmentsJson",
                "reputationJson", "achievementJson", "milestoneJson", "teacherDevJson", "clubJson",
                "scholarshipJson", "expansionJson", "governmentJson", "parentJson", "policyJson",
                "seasonalJson", "conferenceJson", "clubActivityJson", "timetableJson", "examJson",
                "teachingConfigJson", "statisticsJson", "financialReportJson", "pressureJson",
                "competitorJson", "crisisJson", "alumniJson", "employmentJson", "headTeacherMapJson",
                "classTierMapJson", "suggestionBoxJson"
            )
            fields.forEach { field ->
                db.execSQL(
                    "INSERT OR REPLACE INTO `school_manager_states` " +
                        "(`schoolId`, `stateKey`, `payload`, `updatedAt`) " +
                        "SELECT `id`, '$field', `$field`, `lastSaveTime` FROM `schools` " +
                        "WHERE `$field` IS NOT NULL AND `$field` != ''"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `move_${field}_to_manager_state` " +
                        "AFTER UPDATE OF `$field` ON `schools` " +
                        "WHEN NEW.`$field` != '' BEGIN " +
                        "INSERT OR REPLACE INTO `school_manager_states` " +
                        "(`schoolId`, `stateKey`, `payload`, `updatedAt`) " +
                        "VALUES (NEW.`id`, '$field', NEW.`$field`, NEW.`lastSaveTime`); " +
                        "UPDATE `schools` SET `$field` = '' WHERE `id` = NEW.`id`; " +
                        "END"
                )
            }
            db.execSQL(
                "UPDATE `schools` SET " +
                    fields.joinToString(", ") { "`$it` = ''" }
            )
        }
    }

    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `school_manager_state_chunks` (" +
                    "`schoolId` TEXT NOT NULL, `stateKey` TEXT NOT NULL, " +
                    "`chunkIndex` INTEGER NOT NULL, `payload` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`schoolId`, `stateKey`, `chunkIndex`))"
            )
            // SQLite 内部分块，绝不经 CursorWindow 把 4.5 的大 payload 读入应用进程。
            db.execSQL(
                "WITH RECURSIVE chunks(schoolId, stateKey, payload, updatedAt, chunkIndex) AS (" +
                    "SELECT schoolId, stateKey, substr(payload, 1, 32000), updatedAt, 0 " +
                    "FROM school_manager_states WHERE payload != '' " +
                    "UNION ALL " +
                    "SELECT s.schoolId, s.stateKey, substr(s.payload, (c.chunkIndex + 1) * 32000 + 1, 32000), " +
                    "s.updatedAt, c.chunkIndex + 1 " +
                    "FROM school_manager_states s JOIN chunks c " +
                    "ON s.schoolId = c.schoolId AND s.stateKey = c.stateKey " +
                    "WHERE length(s.payload) > (c.chunkIndex + 1) * 32000" +
                    ") INSERT OR REPLACE INTO school_manager_state_chunks " +
                    "(schoolId, stateKey, chunkIndex, payload, updatedAt) " +
                    "SELECT schoolId, stateKey, chunkIndex, payload, updatedAt FROM chunks"
            )
            db.execSQL("DELETE FROM school_manager_states")
        }
    }

    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            safeAddColumn(
                db,
                "teaching_methods",
                "isResearching",
                "INTEGER NOT NULL DEFAULT 0"
            )
            safeAddColumn(
                db,
                "teaching_methods",
                "remainingResearchDays",
                "INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "school_tycoon_db"
        )
            .addMigrations(
                // 注册从版本1到20的全部迁移路径（幂等，不会重复添加列）
                *(1..20).map { createSafeMigration(it, it + 1) }.toTypedArray(),
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    installManagerStateTriggers(db)
                }
            })
            // 不在打开数据库时修改任何 JSON：进度异常必须保留以便恢复，不能静默删除。
            // 兜底迁移仅补列，不以销毁数据库作为恢复策略。
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    @Provides
    fun provideSchoolDao(database: AppDatabase): SchoolDao = database.schoolDao()

    @Provides
    fun provideTeacherDao(database: AppDatabase): TeacherDao = database.teacherDao()

    @Provides
    fun provideCourseDao(database: AppDatabase): CourseDao = database.courseDao()

    @Provides
    fun provideTeachingMethodDao(database: AppDatabase): TeachingMethodDao = database.teachingMethodDao()

    @Provides
    fun provideStockDao(database: AppDatabase): StockDao = database.stockDao()

    @Provides
    fun provideStudentDao(database: AppDatabase): StudentDao = database.studentDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideSchoolRepository(
        database: AppDatabase,
        schoolDao: SchoolDao,
        settingsDataStore: SettingsDataStore
    ): SchoolRepository {
        return SchoolRepositoryImpl(database, schoolDao, settingsDataStore)
    }

    @Provides
    @Singleton
    fun provideTeacherRepository(
        database: AppDatabase,
        teacherDao: TeacherDao,
        settingsDataStore: SettingsDataStore
    ): TeacherRepository {
        return TeacherRepositoryImpl(
            database,
            teacherDao,
            settingsDataStore
        )
    }

    @Provides
    @Singleton
    fun provideCourseRepository(
        courseDao: CourseDao,
        settingsDataStore: SettingsDataStore
    ): CourseRepository {
        return CourseRepositoryImpl(courseDao, settingsDataStore)
    }

    @Provides
    @Singleton
    fun provideResearchRepository(
        database: AppDatabase,
        teachingMethodDao: TeachingMethodDao,
        settingsDataStore: SettingsDataStore
    ): ResearchRepository {
        return ResearchRepositoryImpl(
            database,
            teachingMethodDao,
            settingsDataStore
        )
    }

    @Provides
    @Singleton
    fun provideStockRepository(
        database: AppDatabase,
        stockDao: StockDao,
        settingsDataStore: SettingsDataStore
    ): StockRepository {
        return StockRepositoryImpl(database, stockDao, settingsDataStore)
    }

    @Provides
    @Singleton
    fun provideStudentRepository(
        database: AppDatabase,
        studentDao: StudentDao,
        settingsDataStore: SettingsDataStore
    ): StudentRepository {
        return StudentRepositoryImpl(
            database,
            studentDao,
            settingsDataStore
        )
    }

    @Provides
    @Singleton
    fun provideSaveManager(
        @ApplicationContext context: Context,
        database: AppDatabase,
        settingsDataStore: SettingsDataStore,
        gameEngine: GameEngine
    ): SaveManager {
        return SaveManager(context, database, settingsDataStore, gameEngine)
    }
}
