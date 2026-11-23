package com.example.di

import android.content.Context
import com.example.data.firebase.TeacherActivationRepository
import com.example.data.local.MeasureLabDatabase
import com.example.data.local.preferences.UserPreferencesStore
import com.example.data.repository.EvaluationRepository
import com.example.data.repository.MeasurementHistoryRepository
import com.example.data.repository.TeacherTaskRepository
import com.example.data.repository.WorkpieceRepository
import com.example.domain.model.InstrumentType
import com.example.domain.model.TeacherTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lightweight Constructor-Injection container for offline repositories, preferences,
 * and Cloud Firestore teacher/admin activation repository.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database: MeasureLabDatabase = MeasureLabDatabase.getInstance(appContext)

    val preferencesStore = UserPreferencesStore(appContext)
    val measurementHistoryRepository = MeasurementHistoryRepository(database.measurementRecordDao())
    val workpieceRepository = WorkpieceRepository(database.customWorkpieceDao())
    val teacherTaskRepository = TeacherTaskRepository(database.teacherTaskDao())
    val evaluationRepository = EvaluationRepository(database.evaluationAttemptDao())
    val teacherActivationRepository: TeacherActivationRepository by lazy {
        TeacherActivationRepository(appContext)
    }

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        seedInitialTeacherTasksIfEmpty()
    }

    private fun seedInitialTeacherTasksIfEmpty() {
        initScope.launch {
            val existing = teacherTaskRepository.allTasks.first()
            if (existing.isEmpty()) {
                teacherTaskRepository.saveTask(
                    TeacherTask(
                        title = "Praktikum 1: Ukur Poros Silinder Ø25,50",
                        instructions = "Gunakan Jangka Sorong Manual (0,05 mm) pada rahang luar, kunci rahang sebelum membaca, lalu simpan hasil.",
                        allowedInstrument = InstrumentType.MANUAL_CALIPER,
                        workpieceId = "wp_cyl_shaft",
                        zoneId = "z_od",
                        nominalUm = 25_500,
                        upperTolUm = 50,
                        lowerTolUm = 50,
                        isBlindMeasurement = false,
                        timeLimitSeconds = 180
                    )
                )
                teacherTaskRepository.saveTask(
                    TeacherTask(
                        title = "Evaluasi Blind: Tebal Plat Baja",
                        instructions = "Gunakan Mikrometer Sekrup Manual dengan Ratchet Stop (2–3 klik), tentukan dimensi aktual tanpa bantuan readout.",
                        allowedInstrument = InstrumentType.MANUAL_MICROMETER,
                        workpieceId = "wp_flat_plate",
                        zoneId = "z_thick",
                        nominalUm = 6_300,
                        upperTolUm = 50,
                        lowerTolUm = 50,
                        isBlindMeasurement = true,
                        timeLimitSeconds = 240
                    )
                )
            }
        }
    }
}
