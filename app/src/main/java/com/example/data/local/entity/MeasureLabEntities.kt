package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_records")
data class MeasurementRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantCode: String,
    val instrumentType: String,
    val workpieceId: String,
    val workpieceName: String,
    val zoneLabel: String,
    val measurementKind: String,
    val measuredUm: Int,
    val referenceUm: Int,
    val errorUm: Int,
    val toleranceStatus: String,
    val durationSeconds: Int,
    val attemptCount: Int,
    val activeFaultsSummary: String,
    val taskId: Long?,
    val timestampMillis: Long
)

@Entity(tableName = "custom_workpieces")
data class CustomWorkpieceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shapeType: String,
    val material: String,
    val description: String,
    val primaryKind: String,
    val actualUm: Int,
    val nominalUm: Int,
    val upperTolUm: Int,
    val lowerTolUm: Int,
    val secondaryKind: String?,
    val secondaryActualUm: Int?,
    val createdAtMillis: Long
)

@Entity(tableName = "teacher_tasks")
data class TeacherTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val instructions: String,
    val allowedInstrument: String,
    val workpieceId: String,
    val zoneId: String,
    val nominalUm: Int,
    val upperTolUm: Int,
    val lowerTolUm: Int,
    val isBlindMeasurement: Boolean,
    val timeLimitSeconds: Int,
    val isActive: Boolean,
    val createdAtMillis: Long
)

@Entity(tableName = "evaluation_attempts")
data class EvaluationAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantCode: String,
    val category: String,
    val scorePercent: Int,
    val correctCount: Int,
    val totalCount: Int,
    val procedureErrors: Int,
    val durationSeconds: Int,
    val timestampMillis: Long
)
