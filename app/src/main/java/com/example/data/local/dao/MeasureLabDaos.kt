package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CustomWorkpieceEntity
import com.example.data.local.entity.EvaluationAttemptEntity
import com.example.data.local.entity.MeasurementRecordEntity
import com.example.data.local.entity.TeacherTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementRecordDao {
    @Query("SELECT * FROM measurement_records ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<MeasurementRecordEntity>>

    @Query("SELECT * FROM measurement_records WHERE instrumentType = :instrumentType ORDER BY timestampMillis DESC")
    fun observeByInstrument(instrumentType: String): Flow<List<MeasurementRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MeasurementRecordEntity): Long

    @Query("DELETE FROM measurement_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM measurement_records")
    suspend fun clearAll()
}

@Dao
interface CustomWorkpieceDao {
    @Query("SELECT * FROM custom_workpieces ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<CustomWorkpieceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workpiece: CustomWorkpieceEntity)

    @Query("DELETE FROM custom_workpieces WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TeacherTaskDao {
    @Query("SELECT * FROM teacher_tasks ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<TeacherTaskEntity>>

    @Query("SELECT * FROM teacher_tasks WHERE isActive = 1 ORDER BY createdAtMillis DESC")
    fun observeActive(): Flow<List<TeacherTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TeacherTaskEntity): Long

    @Query("UPDATE teacher_tasks SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("DELETE FROM teacher_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface EvaluationAttemptDao {
    @Query("SELECT * FROM evaluation_attempts ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<EvaluationAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: EvaluationAttemptEntity): Long
}
