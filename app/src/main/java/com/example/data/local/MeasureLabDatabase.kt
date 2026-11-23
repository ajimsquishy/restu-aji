package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.CustomWorkpieceDao
import com.example.data.local.dao.EvaluationAttemptDao
import com.example.data.local.dao.MeasurementRecordDao
import com.example.data.local.dao.TeacherTaskDao
import com.example.data.local.entity.CustomWorkpieceEntity
import com.example.data.local.entity.EvaluationAttemptEntity
import com.example.data.local.entity.MeasurementRecordEntity
import com.example.data.local.entity.TeacherTaskEntity

@Database(
    entities = [
        MeasurementRecordEntity::class,
        CustomWorkpieceEntity::class,
        TeacherTaskEntity::class,
        EvaluationAttemptEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MeasureLabDatabase : RoomDatabase() {
    abstract fun measurementRecordDao(): MeasurementRecordDao
    abstract fun customWorkpieceDao(): CustomWorkpieceDao
    abstract fun teacherTaskDao(): TeacherTaskDao
    abstract fun evaluationAttemptDao(): EvaluationAttemptDao

    companion object {
        @Volatile
        private var INSTANCE: MeasureLabDatabase? = null

        fun getInstance(context: Context): MeasureLabDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeasureLabDatabase::class.java,
                    "measure_lab_offline.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
