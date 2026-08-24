package com.arktools.xiaozhang.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arktools.xiaozhang.data.local.dao.CourseDao
import com.arktools.xiaozhang.data.local.dao.SchoolDao
import com.arktools.xiaozhang.data.local.dao.SchoolManagerStateDao
import com.arktools.xiaozhang.data.local.dao.SchoolManagerStateChunkDao
import com.arktools.xiaozhang.data.local.dao.StockDao
import com.arktools.xiaozhang.data.local.dao.StudentDao
import com.arktools.xiaozhang.data.local.dao.TeacherDao
import com.arktools.xiaozhang.data.local.dao.TeachingMethodDao
import com.arktools.xiaozhang.data.local.entity.CourseEntity
import com.arktools.xiaozhang.data.local.entity.SchoolEntity
import com.arktools.xiaozhang.data.local.entity.SchoolManagerStateEntity
import com.arktools.xiaozhang.data.local.entity.SchoolManagerStateChunkEntity
import com.arktools.xiaozhang.data.local.entity.StockEntity
import com.arktools.xiaozhang.data.local.entity.StockHoldingEntity
import com.arktools.xiaozhang.data.local.entity.StockPriceHistoryEntity
import com.arktools.xiaozhang.data.local.entity.StudentEntity
import com.arktools.xiaozhang.data.local.entity.TeacherEntity
import com.arktools.xiaozhang.data.local.entity.TeachingMethodEntity

const val APP_DATABASE_SCHEMA_VERSION = 28

@Database(
    entities = [
        SchoolEntity::class,
        SchoolManagerStateEntity::class,
        SchoolManagerStateChunkEntity::class,
        TeacherEntity::class,
        CourseEntity::class,
        TeachingMethodEntity::class,
        StockEntity::class,
        StockHoldingEntity::class,
        StockPriceHistoryEntity::class,
        StudentEntity::class
    ],
    version = APP_DATABASE_SCHEMA_VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schoolDao(): SchoolDao
    abstract fun schoolManagerStateDao(): SchoolManagerStateDao
    abstract fun schoolManagerStateChunkDao(): SchoolManagerStateChunkDao
    abstract fun teacherDao(): TeacherDao
    abstract fun courseDao(): CourseDao
    abstract fun teachingMethodDao(): TeachingMethodDao
    abstract fun stockDao(): StockDao
    abstract fun studentDao(): StudentDao
}
