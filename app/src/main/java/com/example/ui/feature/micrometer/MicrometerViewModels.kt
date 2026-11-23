package com.example.ui.feature.micrometer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.WorkpieceCatalog
import com.example.di.AppContainer
import com.example.domain.engine.DigitalMicrometerEngine
import com.example.domain.engine.DigitalMicrometerState
import com.example.domain.engine.ManualMicrometerEngine
import com.example.domain.engine.ManualMicrometerState
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.engine.ToleranceAnalyzer
import com.example.domain.engine.ToolCompatibilityGuard
import com.example.domain.model.FaultConfig
import com.example.domain.model.InstrumentType
import com.example.domain.model.LearningMode
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementRecord
import com.example.domain.model.MeasurementZone
import com.example.domain.model.ToleranceEvaluation
import com.example.domain.model.ToleranceStatus
import com.example.domain.model.ToolCompatibilityResult
import com.example.domain.model.Workpiece
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManualMicrometerUiState(
    val engine: ManualMicrometerState = ManualMicrometerState(),
    val learningMode: LearningMode = LearningMode.LEARN,
    val selectedWorkpiece: Workpiece? = WorkpieceCatalog.defaultWorkpieces[3],
    val selectedZone: MeasurementZone? = WorkpieceCatalog.defaultWorkpieces[3].zones[2],
    val isBlindMode: Boolean = false,
    val explodeProgress: Float = 0f,
    val isXRayMode: Boolean = false,
    val showCallouts: Boolean = true,
    val activeStepIndex: Int = 1,
    val attemptCount: Int = 1,
    val sessionStartMillis: Long = System.currentTimeMillis(),
    val activeTaskId: Long? = null,
    val blindAnswerFeedback: String? = null,
    val savedMessage: String? = null,
    val incompatibleAlert: ToolCompatibilityResult.Incompatible? = null
) {
    val toleranceEvaluation: ToleranceEvaluation?
        get() = selectedZone?.let { zone ->
            ToleranceAnalyzer.evaluate(zone.toleranceSpec, engine.readingUm)
        }
}

class ManualMicrometerViewModel(
    private val container: AppContainer,
    initialWorkpieceId: String? = null,
    initialZoneId: String? = null,
    initialTaskId: Long? = null
) : ViewModel() {

    val workpieces: StateFlow<List<Workpiece>> = container.workpieceRepository.allWorkpieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkpieceCatalog.defaultWorkpieces)

    private val _uiState = MutableStateFlow(ManualMicrometerUiState(activeTaskId = initialTaskId))
    val uiState: StateFlow<ManualMicrometerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = container.preferencesStore.preferencesFlow.first()
            val list = container.workpieceRepository.allWorkpieces.first()
            val defaultWp = list.firstOrNull { it.id == "wp_bushing_ring" } ?: list.first()
            val wp = list.firstOrNull { it.id == initialWorkpieceId } ?: defaultWp
            val zone = wp.zones.firstOrNull {
                it.zoneId == initialZoneId &&
                    ToolCompatibilityGuard.validate(InstrumentType.MANUAL_MICROMETER, it.kind, it.actualUm) is ToolCompatibilityResult.Compatible
            } ?: wp.zones.firstOrNull {
                ToolCompatibilityGuard.validate(InstrumentType.MANUAL_MICROMETER, it.kind, it.actualUm) is ToolCompatibilityResult.Compatible
            } ?: WorkpieceCatalog.defaultWorkpieces[3].zones[2]

            val task = initialTaskId?.let { id ->
                container.teacherTaskRepository.allTasks.first().firstOrNull { it.id == id }
            }
            _uiState.update { cur ->
                cur.copy(
                    selectedWorkpiece = wp,
                    selectedZone = zone,
                    engine = ManualMicrometerState(
                        spindlePositionUm = zone.actualUm,
                        measurementKind = zone.kind,
                        workpieceContactUm = zone.actualUm,
                        isInContactWithWorkpiece = true
                    ),
                    learningMode = if (task != null) LearningMode.CHALLENGE else prefs.learningMode,
                    isBlindMode = task?.isBlindMeasurement ?: prefs.blindMeasurementDefault,
                    showCallouts = prefs.learningMode == LearningMode.LEARN
                )
            }
        }
    }

    fun rotateDeltaUm(deltaUm: Int, usingRatchet: Boolean) {
        _uiState.update { s ->
            val next = ManualMicrometerEngine.moveSpindleBy(s.engine, deltaUm, usingRatchet)
            s.copy(
                engine = next,
                activeStepIndex = if (next.ratchetClickCount >= 2) 2 else 1,
                savedMessage = null
            )
        }
    }

    fun stepThimble(steps: Int, usingRatchet: Boolean = false) {
        _uiState.update { s ->
            val next = ManualMicrometerEngine.stepThimble(s.engine, steps, usingRatchet)
            s.copy(engine = next, savedMessage = null)
        }
    }

    fun engageRatchetStop() {
        _uiState.update { s ->
            // Advances spindle smoothly toward contact with ratchet slip protection
            val next = ManualMicrometerEngine.moveSpindleBy(s.engine, -250, usingRatchet = true)
            s.copy(
                engine = next,
                activeStepIndex = if (next.ratchetClickCount >= 2) 2 else 1,
                savedMessage = null
            )
        }
    }

    fun toggleLock() {
        _uiState.update { s ->
            val next = ManualMicrometerEngine.toggleLock(s.engine)
            s.copy(engine = next, activeStepIndex = if (next.isLocked) 3 else s.activeStepIndex)
        }
    }

    fun calibrateZero() = _uiState.update { it.copy(engine = ManualMicrometerEngine.calibrateZero(it.engine), savedMessage = null) }
    fun clearZeroOffset() = _uiState.update { it.copy(engine = ManualMicrometerEngine.clearZeroOffset(it.engine), savedMessage = null) }

    fun closeToZero() {
        _uiState.update { s ->
            val next = ManualMicrometerEngine.closeToZero(s.engine)
            if (s.engine.isLocked) {
                s.copy(engine = next)
            } else {
                s.copy(
                    selectedWorkpiece = null,
                    selectedZone = null,
                    engine = next,
                    activeStepIndex = 0,
                    blindAnswerFeedback = null,
                    savedMessage = null
                )
            }
        }
    }

    fun reset() = _uiState.update { it.copy(engine = ManualMicrometerEngine.reset(it.engine), activeStepIndex = 0, blindAnswerFeedback = null, savedMessage = null) }
    fun setLearningMode(mode: LearningMode) = _uiState.update { it.copy(learningMode = mode, showCallouts = mode == LearningMode.LEARN, isBlindMode = mode == LearningMode.CHALLENGE) }
    fun toggleBlindMode() = _uiState.update { it.copy(isBlindMode = !it.isBlindMode, blindAnswerFeedback = null) }
    fun setExplodeProgress(v: Float) = _uiState.update { it.copy(explodeProgress = v.coerceIn(0f, 1f)) }
    fun toggleXRayMode() = _uiState.update { it.copy(isXRayMode = !it.isXRayMode) }
    fun updateFaultConfig(config: FaultConfig) = _uiState.update { it.copy(engine = it.engine.copy(faultConfig = config)) }
    fun dismissIncompatibleAlert() = _uiState.update { it.copy(incompatibleAlert = null) }

    fun setMeasurementKind(kind: MeasurementKind) {
        val currentDim = _uiState.value.selectedZone?.actualUm ?: 10_000
        when (val check = ToolCompatibilityGuard.validate(InstrumentType.MANUAL_MICROMETER, kind, currentDim)) {
            is ToolCompatibilityResult.Incompatible -> {
                _uiState.update { it.copy(incompatibleAlert = check) }
                return
            }
            ToolCompatibilityResult.Compatible -> Unit
        }
        val currentState = _uiState.value
        val curZone = currentState.selectedZone
        // Auto-select a compatible workpiece zone matching the newly requested measurement kind
        if (curZone != null && curZone.kind == kind) {
            _uiState.update { it.copy(engine = it.engine.copy(measurementKind = kind)) }
            return
        }
        val currentWp = currentState.selectedWorkpiece
        val currentWpMatch = currentWp?.zones?.firstOrNull { z ->
            z.kind == kind &&
                ToolCompatibilityGuard.validate(InstrumentType.MANUAL_MICROMETER, z.kind, z.actualUm) is ToolCompatibilityResult.Compatible
        }
        val catalogList = workpieces.value
        val matchedPair = if (currentWp != null && currentWpMatch != null) {
            currentWp to currentWpMatch
        } else {
            catalogList.firstNotNullOfOrNull { wp ->
                wp.zones.firstOrNull { z ->
                    z.kind == kind &&
                        ToolCompatibilityGuard.validate(InstrumentType.MANUAL_MICROMETER, z.kind, z.actualUm) is ToolCompatibilityResult.Compatible
                }?.let { wp to it }
            }
        }
        if (matchedPair != null) {
            val (wp, zone) = matchedPair
            _uiState.update { s ->
                s.copy(
                    selectedWorkpiece = wp,
                    selectedZone = zone,
                    engine = s.engine.copy(
                        measurementKind = kind,
                        workpieceContactUm = zone.actualUm,
                        spindlePositionUm = zone.actualUm,
                        isInContactWithWorkpiece = true,
                        isLocked = false,
                        ratchetClickCount = 0,
                        isOverPressureWarning = false,
                        procedureWarning = null
                    ),
                    blindAnswerFeedback = null,
                    savedMessage = null
                )
            }
        } else {
            _uiState.update { it.copy(engine = it.engine.copy(measurementKind = kind)) }
        }
    }

    fun selectWorkpiece(workpiece: Workpiece, zone: MeasurementZone) {
        when (val check = ToolCompatibilityGuard.validate(InstrumentType.MANUAL_MICROMETER, zone.kind, zone.actualUm)) {
            is ToolCompatibilityResult.Incompatible -> {
                _uiState.update { it.copy(incompatibleAlert = check) }
                return
            }
            ToolCompatibilityResult.Compatible -> Unit
        }
        val openPos = (zone.actualUm + 2_500).coerceAtMost(ManualMicrometerEngine.MAX_RANGE_UM)
        _uiState.update { s ->
            s.copy(
                selectedWorkpiece = workpiece,
                selectedZone = zone,
                engine = s.engine.copy(
                    measurementKind = zone.kind,
                    workpieceContactUm = zone.actualUm,
                    spindlePositionUm = openPos,
                    isLocked = false,
                    ratchetClickCount = 0,
                    isInContactWithWorkpiece = false,
                    procedureWarning = "Benda terpasang. Putar Thimble mendekat lalu gunakan Ratchet Stop (2–3 klik)."
                ),
                activeStepIndex = 1,
                blindAnswerFeedback = null,
                savedMessage = null
            )
        }
    }

    fun removeWorkpiece() {
        _uiState.update { s ->
            s.copy(
                selectedWorkpiece = null,
                selectedZone = null,
                engine = s.engine.copy(workpieceContactUm = null, isInContactWithWorkpiece = false, ratchetClickCount = 0)
            )
        }
    }

    fun submitBlindReading(userInputMm: String) {
        val parsed = userInputMm.trim().replace(',', '.').toDoubleOrNull()
        if (parsed == null) {
            _uiState.update { it.copy(blindAnswerFeedback = "Masukkan angka valid dalam mm (contoh: 10.28).") }
            return
        }
        val userUm = MeasurementUnitConverter.mmToUm(parsed)
        val actualUm = _uiState.value.engine.readingUm
        val diff = kotlin.math.abs(userUm - actualUm)
        val feedback = if (diff <= 10) {
            "Tepat! Bacaan Anda (${MeasurementUnitConverter.formatUmAsMm(userUm, 2)} mm) cocok dengan skala (${MeasurementUnitConverter.formatUmAsMm(actualUm, 2)} mm)."
        } else {
            "Belum tepat (selisih ${MeasurementUnitConverter.formatUmAsMm(diff, 2)} mm). Skala sebenarnya: ${MeasurementUnitConverter.formatUmAsMm(actualUm, 2)} mm."
        }
        _uiState.update { it.copy(blindAnswerFeedback = feedback, attemptCount = it.attemptCount + 1) }
    }

    fun saveMeasurement() {
        val s = _uiState.value
        val zone = s.selectedZone
        val refUm = zone?.actualUm ?: s.engine.readingUm
        val tolStatus = s.toleranceEvaluation?.status ?: ToleranceStatus.IN_TOLERANCE
        val duration = ((System.currentTimeMillis() - s.sessionStartMillis) / 1000L).toInt().coerceAtLeast(1)

        viewModelScope.launch {
            val prefs = container.preferencesStore.preferencesFlow.first()
            container.measurementHistoryRepository.saveRecord(
                MeasurementRecord(
                    participantCode = prefs.participantCode,
                    instrumentType = InstrumentType.MANUAL_MICROMETER,
                    workpieceId = s.selectedWorkpiece?.id ?: "free_measure",
                    workpieceName = s.selectedWorkpiece?.name ?: "Pengukuran Bebas",
                    zoneLabel = zone?.label ?: s.engine.measurementKind.label,
                    measurementKind = s.engine.measurementKind,
                    measuredUm = s.engine.readingUm,
                    referenceUm = refUm,
                    errorUm = s.engine.readingUm - refUm,
                    toleranceStatus = tolStatus,
                    durationSeconds = duration,
                    attemptCount = s.attemptCount,
                    activeFaultsSummary = s.engine.faultConfig.activeSummaryList().joinToString(", "),
                    taskId = s.activeTaskId
                )
            )
            _uiState.update {
                it.copy(
                    savedMessage = "Hasil ${MeasurementUnitConverter.formatUmAsMm(s.engine.readingUm, 2)} mm tersimpan ke Riwayat.",
                    attemptCount = it.attemptCount + 1
                )
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            workpieceId: String? = null,
            zoneId: String? = null,
            taskId: Long? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ManualMicrometerViewModel(container, workpieceId, zoneId, taskId) as T
        }
    }
}

data class DigitalMicrometerUiState(
    val engine: DigitalMicrometerState = DigitalMicrometerState(),
    val selectedWorkpiece: Workpiece? = WorkpieceCatalog.defaultWorkpieces[1],
    val selectedZone: MeasurementZone? = WorkpieceCatalog.defaultWorkpieces[1].zones[1],
    val explodeProgress: Float = 0f,
    val isXRayMode: Boolean = false,
    val showCallouts: Boolean = false,
    val attemptCount: Int = 1,
    val sessionStartMillis: Long = System.currentTimeMillis(),
    val activeTaskId: Long? = null,
    val savedMessage: String? = null,
    val incompatibleAlert: ToolCompatibilityResult.Incompatible? = null
) {
    val toleranceEvaluation: ToleranceEvaluation?
        get() = selectedZone?.let { zone ->
            val reading = engine.displayedUm ?: return null
            ToleranceAnalyzer.evaluate(zone.toleranceSpec, reading)
        }
}

class DigitalMicrometerViewModel(
    private val container: AppContainer,
    initialWorkpieceId: String? = null,
    initialZoneId: String? = null,
    initialTaskId: Long? = null
) : ViewModel() {

    val workpieces: StateFlow<List<Workpiece>> = container.workpieceRepository.allWorkpieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkpieceCatalog.defaultWorkpieces)

    private val _uiState = MutableStateFlow(DigitalMicrometerUiState(activeTaskId = initialTaskId))
    val uiState: StateFlow<DigitalMicrometerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = container.workpieceRepository.allWorkpieces.first()
            val wp = list.firstOrNull { it.id == initialWorkpieceId } ?: list[1]
            val zone = wp.zones.firstOrNull {
                it.zoneId == initialZoneId &&
                    ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_MICROMETER, it.kind, it.actualUm) is ToolCompatibilityResult.Compatible
            } ?: wp.zones.firstOrNull {
                ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_MICROMETER, it.kind, it.actualUm) is ToolCompatibilityResult.Compatible
            } ?: WorkpieceCatalog.defaultWorkpieces[1].zones[1]
            _uiState.update { cur ->
                cur.copy(
                    selectedWorkpiece = wp,
                    selectedZone = zone,
                    engine = DigitalMicrometerState(
                        spindlePositionUm = zone.actualUm,
                        measurementKind = zone.kind,
                        workpieceContactUm = zone.actualUm,
                        isInContactWithWorkpiece = true
                    )
                )
            }
        }
    }

    fun rotateDeltaUm(deltaUm: Int, usingRatchet: Boolean) = _uiState.update { it.copy(engine = DigitalMicrometerEngine.moveSpindleBy(it.engine, deltaUm, usingRatchet), savedMessage = null) }
    fun engageRatchet() = _uiState.update { it.copy(engine = DigitalMicrometerEngine.moveSpindleBy(it.engine, -250, usingRatchet = true), savedMessage = null) }
    fun togglePower() = _uiState.update { it.copy(engine = DigitalMicrometerEngine.togglePower(it.engine)) }
    fun pressZero() = _uiState.update { it.copy(engine = DigitalMicrometerEngine.pressZero(it.engine)) }
    fun toggleUnit() = _uiState.update { it.copy(engine = DigitalMicrometerEngine.toggleUnit(it.engine)) }
    fun toggleHold() = _uiState.update { it.copy(engine = DigitalMicrometerEngine.toggleHold(it.engine)) }
    fun toggleLock() = _uiState.update { it.copy(engine = DigitalMicrometerEngine.toggleLock(it.engine)) }
    fun reset() = _uiState.update { it.copy(engine = DigitalMicrometerEngine.reset(it.engine), savedMessage = null) }
    fun toggleXRayMode() = _uiState.update { it.copy(isXRayMode = !it.isXRayMode) }
    fun updateFaultConfig(config: FaultConfig) = _uiState.update { it.copy(engine = it.engine.copy(faultConfig = config)) }
    fun dismissIncompatibleAlert() = _uiState.update { it.copy(incompatibleAlert = null) }

    fun setMeasurementKind(kind: MeasurementKind) {
        val currentState = _uiState.value
        val dim = currentState.selectedZone?.actualUm ?: 12_000
        when (val check = ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_MICROMETER, kind, dim)) {
            is ToolCompatibilityResult.Incompatible -> {
                _uiState.update { it.copy(incompatibleAlert = check) }
                return
            }
            ToolCompatibilityResult.Compatible -> Unit
        }
        val curZone = currentState.selectedZone
        if (curZone != null && curZone.kind == kind) {
            _uiState.update { it.copy(engine = it.engine.copy(measurementKind = kind)) }
            return
        }
        val currentWp = currentState.selectedWorkpiece
        val currentWpMatch = currentWp?.zones?.firstOrNull { z ->
            z.kind == kind &&
                ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_MICROMETER, z.kind, z.actualUm) is ToolCompatibilityResult.Compatible
        }
        val matchedPair = if (currentWp != null && currentWpMatch != null) {
            currentWp to currentWpMatch
        } else {
            workpieces.value.firstNotNullOfOrNull { wp ->
                wp.zones.firstOrNull { z ->
                    z.kind == kind &&
                        ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_MICROMETER, z.kind, z.actualUm) is ToolCompatibilityResult.Compatible
                }?.let { wp to it }
            }
        }
        if (matchedPair != null) {
            val (wp, zone) = matchedPair
            _uiState.update { s ->
                s.copy(
                    selectedWorkpiece = wp,
                    selectedZone = zone,
                    engine = s.engine.copy(
                        measurementKind = kind,
                        workpieceContactUm = zone.actualUm,
                        spindlePositionUm = zone.actualUm,
                        isInContactWithWorkpiece = true,
                        isLocked = false,
                        isHoldActive = false,
                        heldValueUm = null,
                        ratchetClickCount = 0,
                        procedureWarning = null
                    ),
                    savedMessage = null
                )
            }
        } else {
            _uiState.update { it.copy(engine = it.engine.copy(measurementKind = kind)) }
        }
    }

    fun selectWorkpiece(workpiece: Workpiece, zone: MeasurementZone) {
        when (val check = ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_MICROMETER, zone.kind, zone.actualUm)) {
            is ToolCompatibilityResult.Incompatible -> {
                _uiState.update { it.copy(incompatibleAlert = check) }
                return
            }
            ToolCompatibilityResult.Compatible -> Unit
        }
        val openPos = (zone.actualUm + 2_500).coerceAtMost(DigitalMicrometerEngine.MAX_RANGE_UM)
        _uiState.update { s ->
            s.copy(
                selectedWorkpiece = workpiece,
                selectedZone = zone,
                engine = s.engine.copy(
                    measurementKind = zone.kind,
                    workpieceContactUm = zone.actualUm,
                    spindlePositionUm = openPos,
                    isLocked = false,
                    isHoldActive = false,
                    heldValueUm = null,
                    ratchetClickCount = 0,
                    isInContactWithWorkpiece = false
                ),
                savedMessage = null
            )
        }
    }

    fun removeWorkpiece() {
        _uiState.update { s ->
            s.copy(
                selectedWorkpiece = null,
                selectedZone = null,
                engine = s.engine.copy(workpieceContactUm = null, isInContactWithWorkpiece = false, ratchetClickCount = 0)
            )
        }
    }

    fun saveMeasurement() {
        val s = _uiState.value
        val reading = s.engine.displayedUm ?: run {
            _uiState.update { it.copy(savedMessage = "Nyalakan mikrometer (ON) sebelum menyimpan hasil.") }
            return
        }
        val zone = s.selectedZone
        val refUm = zone?.actualUm ?: reading
        val tolStatus = s.toleranceEvaluation?.status ?: ToleranceStatus.IN_TOLERANCE
        val duration = ((System.currentTimeMillis() - s.sessionStartMillis) / 1000L).toInt().coerceAtLeast(1)

        viewModelScope.launch {
            val prefs = container.preferencesStore.preferencesFlow.first()
            container.measurementHistoryRepository.saveRecord(
                MeasurementRecord(
                    participantCode = prefs.participantCode,
                    instrumentType = InstrumentType.DIGITAL_MICROMETER,
                    workpieceId = s.selectedWorkpiece?.id ?: "free_measure",
                    workpieceName = s.selectedWorkpiece?.name ?: "Pengukuran Bebas",
                    zoneLabel = zone?.label ?: s.engine.measurementKind.label,
                    measurementKind = s.engine.measurementKind,
                    measuredUm = reading,
                    referenceUm = refUm,
                    errorUm = reading - refUm,
                    toleranceStatus = tolStatus,
                    durationSeconds = duration,
                    attemptCount = s.attemptCount,
                    activeFaultsSummary = s.engine.faultConfig.activeSummaryList().joinToString(", "),
                    taskId = s.activeTaskId
                )
            )
            _uiState.update {
                it.copy(
                    savedMessage = "Hasil ${MeasurementUnitConverter.formatUmAsMm(reading, 2)} mm tersimpan ke Riwayat.",
                    attemptCount = it.attemptCount + 1
                )
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            workpieceId: String? = null,
            zoneId: String? = null,
            taskId: Long? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DigitalMicrometerViewModel(container, workpieceId, zoneId, taskId) as T
        }
    }
}
