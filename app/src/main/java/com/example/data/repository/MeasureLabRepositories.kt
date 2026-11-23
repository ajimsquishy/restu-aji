package com.example.data.repository

import com.example.data.catalog.LearningAndQuizCatalog
import com.example.data.catalog.WorkpieceCatalog
import com.example.data.local.dao.CustomWorkpieceDao
import com.example.data.local.dao.EvaluationAttemptDao
import com.example.data.local.dao.MeasurementRecordDao
import com.example.data.local.dao.TeacherTaskDao
import com.example.data.local.entity.CustomWorkpieceEntity
import com.example.data.local.entity.EvaluationAttemptEntity
import com.example.data.local.entity.MeasurementRecordEntity
import com.example.data.local.entity.TeacherTaskEntity
import com.example.domain.model.DifficultyLevel
import com.example.domain.model.EvaluationAttempt
import com.example.domain.model.EvaluationCategory
import com.example.domain.model.InstrumentType
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementRecord
import com.example.domain.model.MeasurementZone
import com.example.domain.model.TeacherTask
import com.example.domain.model.ToleranceStatus
import com.example.domain.model.Workpiece
import com.example.domain.model.WorkpieceShapeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeasurementHistoryRepository(
    private val dao: MeasurementRecordDao
) {
    val allRecords: Flow<List<MeasurementRecord>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveRecord(record: MeasurementRecord): Long = dao.insert(record.toEntity())

    suspend fun deleteRecord(id: Long) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()

    private fun MeasurementRecordEntity.toDomain() = MeasurementRecord(
        id = id,
        participantCode = participantCode,
        instrumentType = runCatching { InstrumentType.valueOf(instrumentType) }.getOrDefault(InstrumentType.MANUAL_CALIPER),
        workpieceId = workpieceId,
        workpieceName = workpieceName,
        zoneLabel = zoneLabel,
        measurementKind = runCatching { MeasurementKind.valueOf(measurementKind) }.getOrDefault(MeasurementKind.OUTSIDE_DIAMETER),
        measuredUm = measuredUm,
        referenceUm = referenceUm,
        errorUm = errorUm,
        toleranceStatus = runCatching { ToleranceStatus.valueOf(toleranceStatus) }.getOrDefault(ToleranceStatus.IN_TOLERANCE),
        durationSeconds = durationSeconds,
        attemptCount = attemptCount,
        activeFaultsSummary = activeFaultsSummary,
        taskId = taskId,
        timestampMillis = timestampMillis
    )

    private fun MeasurementRecord.toEntity() = MeasurementRecordEntity(
        id = id,
        participantCode = participantCode,
        instrumentType = instrumentType.name,
        workpieceId = workpieceId,
        workpieceName = workpieceName,
        zoneLabel = zoneLabel,
        measurementKind = measurementKind.name,
        measuredUm = measuredUm,
        referenceUm = referenceUm,
        errorUm = errorUm,
        toleranceStatus = toleranceStatus.name,
        durationSeconds = durationSeconds,
        attemptCount = attemptCount,
        activeFaultsSummary = activeFaultsSummary,
        taskId = taskId,
        timestampMillis = timestampMillis
    )
}

class WorkpieceRepository(
    private val customDao: CustomWorkpieceDao
) {
    val allWorkpieces: Flow<List<Workpiece>> = customDao.observeAll().map { customs ->
        WorkpieceCatalog.defaultWorkpieces + customs.map { it.toDomain() }
    }

    suspend fun addCustomWorkpiece(
        name: String,
        shapeType: WorkpieceShapeType,
        material: String,
        primaryKind: MeasurementKind,
        actualUm: Int,
        nominalUm: Int,
        upperTolUm: Int,
        lowerTolUm: Int
    ): String {
        val newId = "custom_${System.currentTimeMillis()}"
        val entity = CustomWorkpieceEntity(
            id = newId,
            name = name.trim(),
            shapeType = shapeType.name,
            material = material.trim().ifEmpty { "Baja S45C" },
            description = "Benda kerja latihan kustom buatan pengguna.",
            primaryKind = primaryKind.name,
            actualUm = actualUm,
            nominalUm = nominalUm,
            upperTolUm = upperTolUm,
            lowerTolUm = lowerTolUm,
            secondaryKind = null,
            secondaryActualUm = null,
            createdAtMillis = System.currentTimeMillis()
        )
        customDao.insert(entity)
        return newId
    }

    suspend fun deleteCustomWorkpiece(id: String) = customDao.deleteById(id)

    private fun CustomWorkpieceEntity.toDomain(): Workpiece {
        val shape = runCatching { WorkpieceShapeType.valueOf(shapeType) }.getOrDefault(WorkpieceShapeType.CYLINDER_SHAFT)
        val kind = runCatching { MeasurementKind.valueOf(primaryKind) }.getOrDefault(MeasurementKind.OUTSIDE_DIAMETER)
        return Workpiece(
            id = id,
            name = name,
            shapeType = shape,
            material = material,
            description = description,
            difficulty = DifficultyLevel.INTERMEDIATE,
            isCustom = true,
            zones = listOf(
                MeasurementZone(
                    zoneId = "z_custom_1",
                    label = kind.label,
                    kind = kind,
                    actualUm = actualUm,
                    nominalUm = nominalUm,
                    upperTolUm = upperTolUm,
                    lowerTolUm = lowerTolUm
                )
            )
        )
    }
}

class TeacherTaskRepository(
    private val dao: TeacherTaskDao
) {
    val allTasks: Flow<List<TeacherTask>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    val activeTasks: Flow<List<TeacherTask>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun saveTask(task: TeacherTask): Long = dao.insert(task.toEntity())
    suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)
    suspend fun deleteTask(id: Long) = dao.deleteById(id)

    private fun TeacherTaskEntity.toDomain() = TeacherTask(
        id = id,
        title = title,
        instructions = instructions,
        allowedInstrument = runCatching { InstrumentType.valueOf(allowedInstrument) }.getOrDefault(InstrumentType.MANUAL_CALIPER),
        workpieceId = workpieceId,
        zoneId = zoneId,
        nominalUm = nominalUm,
        upperTolUm = upperTolUm,
        lowerTolUm = lowerTolUm,
        isBlindMeasurement = isBlindMeasurement,
        timeLimitSeconds = timeLimitSeconds,
        isActive = isActive,
        createdAtMillis = createdAtMillis
    )

    private fun TeacherTask.toEntity() = TeacherTaskEntity(
        id = id,
        title = title,
        instructions = instructions,
        allowedInstrument = allowedInstrument.name,
        workpieceId = workpieceId,
        zoneId = zoneId,
        nominalUm = nominalUm,
        upperTolUm = upperTolUm,
        lowerTolUm = lowerTolUm,
        isBlindMeasurement = isBlindMeasurement,
        timeLimitSeconds = timeLimitSeconds,
        isActive = isActive,
        createdAtMillis = createdAtMillis
    )
}

class EvaluationRepository(
    private val dao: EvaluationAttemptDao
) {
    val allAttempts: Flow<List<EvaluationAttempt>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun getTopics() = LearningAndQuizCatalog.lessonTopics
    fun getQuestions(category: EvaluationCategory) =
        LearningAndQuizCatalog.quizQuestions.filter { it.category == category }
    fun getMachiningChallenges() = LearningAndQuizCatalog.machiningChallenges

    suspend fun saveAttempt(attempt: EvaluationAttempt): Long = dao.insert(
        EvaluationAttemptEntity(
            id = attempt.id,
            participantCode = attempt.participantCode,
            category = attempt.category.name,
            scorePercent = attempt.scorePercent,
            correctCount = attempt.correctCount,
            totalCount = attempt.totalCount,
            procedureErrors = attempt.procedureErrors,
            durationSeconds = attempt.durationSeconds,
            timestampMillis = attempt.timestampMillis
        )
    )

    private fun EvaluationAttemptEntity.toDomain() = EvaluationAttempt(
        id = id,
        participantCode = participantCode,
        category = runCatching { EvaluationCategory.valueOf(category) }.getOrDefault(EvaluationCategory.SCALE_QUIZ),
        scorePercent = scorePercent,
        correctCount = correctCount,
        totalCount = totalCount,
        procedureErrors = procedureErrors,
        durationSeconds = durationSeconds,
        timestampMillis = timestampMillis
    )
}
