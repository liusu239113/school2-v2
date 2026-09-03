package com.arktools.xiao.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arktools.xiao.data.local.dao.CourseDao
import com.arktools.xiao.data.local.dao.SchoolDao
import com.arktools.xiao.data.local.dao.SchoolManagerStateDao
import com.arktools.xiao.data.local.dao.SchoolManagerStateChunkDao
import com.arktools.xiao.data.local.dao.StockDao
import com.arktools.xiao.data.local.dao.StudentDao
import com.arktools.xiao.data.local.dao.TeacherDao
import com.arktools.xiao.data.local.dao.TeachingMethodDao
import com.arktools.xiao.data.local.entity.CourseEntity
import com.arktools.xiao.data.local.entity.SchoolEntity
import com.arktools.xiao.data.local.entity.SchoolManagerStateEntity
import com.arktools.xiao.data.local.entity.SchoolManagerStateChunkEntity
import com.arktools.xiao.data.local.entity.StockEntity
import com.arktools.xiao.data.local.entity.StockHoldingEntity
import com.arktools.xiao.data.local.entity.StockPriceHistoryEntity
import com.arktools.xiao.data.local.entity.StudentEntity
import com.arktools.xiao.data.local.entity.TeacherEntity
import com.arktools.xiao.data.local.entity.TeachingMethodEntity

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
