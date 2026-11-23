package com.example.ui.feature.caliper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.WorkpieceCatalog
import com.example.di.AppContainer
import com.example.domain.engine.DigitalCaliperEngine
import com.example.domain.engine.DigitalCaliperState
import com.example.domain.engine.ManualCaliperEngine
import com.example.domain.engine.ManualCaliperState
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.engine.ToleranceAnalyzer
import com.example.domain.engine.ToolCompatibilityGuard
import com.example.domain.model.CaliperResolution
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

data class ManualCaliperUiState(
    val engine: ManualCaliperState = ManualCaliperState(),
    val learningMode: LearningMode = LearningMode.LEARN,
    val selectedWorkpiece: Workpiece? = WorkpieceCatalog.defaultWorkpieces.first(),
    val selectedZone: MeasurementZone? = WorkpieceCatalog.defaultWorkpieces.first().zones.first(),
    val isBlindMode: Boolean = false,
    val explodeProgress: Float = 0f,
    val isXRayMode: Boolean = false,
    val showCallouts: Boolean = true,
    val activeStepIndex: Int = 2,
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

class ManualCaliperViewModel(
    private val container: AppContainer,
    initialWorkpieceId: String? = null,
    initialZoneId: String? = null,
    initialTaskId: Long? = null
) : ViewModel() {

    val workpieces: StateFlow<List<Workpiece>> = container.workpieceRepository.allWorkpieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkpieceCatalog.defaultWorkpieces)

    private val _uiState = MutableStateFlow(ManualCaliperUiState(activeTaskId = initialTaskId))
    val uiState: StateFlow<ManualCaliperUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = container.preferencesStore.preferencesFlow.first()
            val list = container.workpieceRepository.allWorkpieces.first()
            val wp = list.firstOrNull { it.id == initialWorkpieceId } ?: list.first()
            val zone = wp.zones.firstOrNull { it.zoneId == initialZoneId } ?: wp.zones.first()
            val task = initialTaskId?.let { id ->
                container.teacherTaskRepository.allTasks.first().firstOrNull { it.id == id }
            }
            val resolvedResolution = if (
                initialWorkpieceId != null &&
                zone.actualUm % prefs.defaultCaliperResolution.stepUm != 0 &&
                zone.actualUm % CaliperResolution.RES_0_02_MM.stepUm == 0
            ) {
                CaliperResolution.RES_0_02_MM
            } else {
                prefs.defaultCaliperResolution
            }
            _uiState.update { cur ->
                val engine = ManualCaliperState(
                    jawPositionUm = zone.actualUm,
                    resolution = resolvedResolution,
                    measurementKind = zone.kind,
                    workpieceContactUm = zone.actualUm,
                    isInContactWithWorkpiece = true
                )
                cur.copy(
                    engine = engine,
                    selectedWorkpiece = wp,
                    selectedZone = zone,
                    learningMode = if (task != null) LearningMode.CHALLENGE else prefs.learningMode,
                    isBlindMode = task?.isBlindMeasurement ?: prefs.blindMeasurementDefault,
                    showCallouts = prefs.learningMode == LearningMode.LEARN
                )
            }
        }
    }

    fun dragByUm(deltaUm: Int) {
        _uiState.update { s ->
            val next = ManualCaliperEngine.moveBy(s.engine, deltaUm)
            s.copy(
                engine = next,
                activeStepIndex = if (next.isInContactWithWorkpiece) 3 else 2,
                savedMessage = null
            )
        }
    }

    fun stepFine(steps: Int) {
        _uiState.update { s ->
            val next = ManualCaliperEngine.stepFine(s.engine, steps)
            s.copy(engine = next, savedMessage = null)
        }
    }

    fun toggleLock() {
        _uiState.update { s ->
            val next = ManualCaliperEngine.toggleLock(s.engine)
            s.copy(
                engine = next,
                activeStepIndex = if (next.isLocked) 4 else s.activeStepIndex
            )
        }
    }

    fun reset() {
        _uiState.update { s ->
            s.copy(
                engine = ManualCaliperEngine.reset(s.engine),
                activeStepIndex = 1,
                blindAnswerFeedback = null,
                savedMessage = null
            )
        }
    }

    fun closeToZero() {
        _uiState.update { s ->
            val next = ManualCaliperEngine.closeToZero(s.engine)
            if (s.engine.isLocked) {
                s.copy(engine = next)
            } else {
                s.copy(
                    engine = next,
                    selectedWorkpiece = null,
                    selectedZone = null,
                    activeStepIndex = 1,
                    savedMessage = null
                )
            }
        }
    }

    fun setResolution(resolution: CaliperResolution) {
        _uiState.update { s ->
            s.copy(engine = ManualCaliperEngine.setResolution(s.engine, resolution))
        }
        viewModelScope.launch { container.preferencesStore.updateCaliperResolution(resolution) }
    }

    fun setMeasurementKind(kind: MeasurementKind) {
        _uiState.update { s ->
            val currentWp = s.selectedWorkpiece
            val directZone = currentWp?.zones?.firstOrNull { it.kind == kind }
            val fallbackPair = if (directZone == null && currentWp != null) {
                if (kind == MeasurementKind.DEPTH) {
                    // Prefer a clear blind-hole depth zone (>= 8 mm) when switching from a workpiece without depth
                    workpieces.value.firstNotNullOfOrNull { wp ->
                        wp.zones.firstOrNull { it.kind == kind && it.actualUm >= 8_000 }?.let { wp to it }
                    } ?: workpieces.value.firstNotNullOfOrNull { wp ->
                        wp.zones.firstOrNull { it.kind == kind }?.let { wp to it }
                    }
                } else {
                    workpieces.value.firstNotNullOfOrNull { wp ->
                        wp.zones.firstOrNull { it.kind == kind }?.let { wp to it }
                    }
                }
            } else {
                null
            }
            val nextWp = fallbackPair?.first ?: currentWp
            val nextZone = directZone ?: fallbackPair?.second
            val nextPos = nextZone?.actualUm?.let {
                MeasurementUnitConverter.roundToStep(it, s.engine.resolution.stepUm)
                    .coerceIn(0, ManualCaliperEngine.MAX_RANGE_UM)
            } ?: s.engine.jawPositionUm
            val nextEngine = s.engine.copy(
                measurementKind = kind,
                workpieceContactUm = nextZone?.actualUm,
                jawPositionUm = nextPos,
                isLocked = false,
                isInContactWithWorkpiece = nextZone != null,
                procedureWarning = null
            )
            s.copy(
                engine = nextEngine,
                selectedWorkpiece = nextWp,
                selectedZone = nextZone ?: s.selectedZone,
                savedMessage = null
            )
        }
    }

    fun selectWorkpiece(workpiece: Workpiece, zone: MeasurementZone) {
        when (val check = ToolCompatibilityGuard.validate(InstrumentType.MANUAL_CALIPER, zone.kind, zone.actualUm)) {
            is ToolCompatibilityResult.Incompatible -> {
                _uiState.update { it.copy(incompatibleAlert = check) }
                return
            }
            ToolCompatibilityResult.Compatible -> Unit
        }
        _uiState.update { s ->
            val startPos = when (zone.kind) {
                MeasurementKind.INSIDE_DIAMETER -> {
                    val minClearance = (zone.actualUm / 3).coerceAtMost(3_000)
                    (zone.actualUm - 6_000).coerceAtLeast(minClearance)
                }
                MeasurementKind.DEPTH -> (zone.actualUm - 6_000).coerceAtLeast(0)
                else -> (zone.actualUm + 8_000).coerceAtMost(ManualCaliperEngine.MAX_RANGE_UM)
            }
            val snappedStart = MeasurementUnitConverter.roundToStep(startPos, s.engine.resolution.stepUm)
                .coerceIn(0, ManualCaliperEngine.MAX_RANGE_UM)
            s.copy(
                selectedWorkpiece = workpiece,
                selectedZone = zone,
                engine = s.engine.copy(
                    measurementKind = zone.kind,
                    workpieceContactUm = zone.actualUm,
                    jawPositionUm = snappedStart,
                    isLocked = false,
                    isInContactWithWorkpiece = false,
                    procedureWarning = "Benda kerja terpasang. Geser rahang hingga menyentuh bidang ukur."
                ),
                activeStepIndex = 2,
                blindAnswerFeedback = null,
                savedMessage = null
            )
        }
    }

    fun removeWorkpiece() {
        _uiState.update { s ->
            val drift = if (!s.engine.isLocked && s.engine.faultConfig.unlockDriftEnabled) 60 else 0
            s.copy(
                selectedWorkpiece = null,
                selectedZone = null,
                engine = s.engine.copy(
                    workpieceContactUm = null,
                    jawPositionUm = (s.engine.jawPositionUm + drift).coerceIn(0, ManualCaliperEngine.MAX_RANGE_UM),
                    isInContactWithWorkpiece = false,
                    procedureWarning = if (drift > 0) "Peringatan: Benda dilepas tanpa Lock — posisi rahang bergeser!" else null
                )
            )
        }
    }

    fun setLearningMode(mode: LearningMode) {
        _uiState.update {
            it.copy(
                learningMode = mode,
                showCallouts = mode == LearningMode.LEARN,
                isBlindMode = mode == LearningMode.CHALLENGE
            )
        }
    }

    fun toggleBlindMode() = _uiState.update { it.copy(isBlindMode = !it.isBlindMode, blindAnswerFeedback = null) }
    fun setExplodeProgress(value: Float) = _uiState.update { it.copy(explodeProgress = value.coerceIn(0f, 1f)) }
    fun toggleXRayMode() = _uiState.update { it.copy(isXRayMode = !it.isXRayMode) }
    fun toggleCallouts() = _uiState.update { it.copy(showCallouts = !it.showCallouts) }
    fun updateFaultConfig(config: FaultConfig) = _uiState.update { it.copy(engine = it.engine.copy(faultConfig = config)) }
    fun dismissIncompatibleAlert() = _uiState.update { it.copy(incompatibleAlert = null) }

    fun submitBlindReading(userInputMm: String) {
        val cleaned = userInputMm.trim().replace(',', '.')
        val parsed = cleaned.toDoubleOrNull()
        if (parsed == null) {
            _uiState.update { it.copy(blindAnswerFeedback = "Masukkan angka valid dalam mm (contoh: 25.50).") }
            return
        }
        val userUm = MeasurementUnitConverter.mmToUm(parsed)
        val actualUm = _uiState.value.engine.readingUm
        val diff = kotlin.math.abs(userUm - actualUm)
        val ok = diff <= _uiState.value.engine.resolution.stepUm
        val feedback = if (ok) {
            "Tepat! Pembacaan Anda (${MeasurementUnitConverter.formatUmAsMm(userUm, 2)} mm) sesuai dengan skala (${MeasurementUnitConverter.formatUmAsMm(actualUm, 2)} mm)."
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
                    instrumentType = InstrumentType.MANUAL_CALIPER,
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
                    savedMessage = "Hasil ${MeasurementUnitConverter.formatUmAsMm(s.engine.readingUm, s.engine.resolution.mmDecimals)} mm tersimpan ke Riwayat.",
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
                ManualCaliperViewModel(container, workpieceId, zoneId, taskId) as T
        }
    }
}

data class DigitalCaliperUiState(
    val engine: DigitalCaliperState = DigitalCaliperState(),
    val selectedWorkpiece: Workpiece? = WorkpieceCatalog.defaultWorkpieces[1],
    val selectedZone: MeasurementZone? = WorkpieceCatalog.defaultWorkpieces[1].zones.first(),
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

class DigitalCaliperViewModel(
    private val container: AppContainer,
    initialWorkpieceId: String? = null,
    initialZoneId: String? = null,
    initialTaskId: Long? = null
) : ViewModel() {

    val workpieces: StateFlow<List<Workpiece>> = container.workpieceRepository.allWorkpieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkpieceCatalog.defaultWorkpieces)

    private val _uiState = MutableStateFlow(DigitalCaliperUiState(activeTaskId = initialTaskId))
    val uiState: StateFlow<DigitalCaliperUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = container.workpieceRepository.allWorkpieces.first()
            val wp = list.firstOrNull { it.id == initialWorkpieceId } ?: list[1]
            val zone = wp.zones.firstOrNull { it.zoneId == initialZoneId } ?: wp.zones.first()
            _uiState.update { cur ->
                cur.copy(
                    selectedWorkpiece = wp,
                    selectedZone = zone,
                    engine = DigitalCaliperState(
                        jawPositionUm = zone.actualUm,
                        measurementKind = zone.kind,
                        workpieceContactUm = zone.actualUm,
                        isInContactWithWorkpiece = true
                    )
                )
            }
        }
    }

    fun dragByUm(deltaUm: Int) = _uiState.update { it.copy(engine = DigitalCaliperEngine.moveBy(it.engine, deltaUm), savedMessage = null) }
    fun stepFine(steps: Int) = _uiState.update { it.copy(engine = DigitalCaliperEngine.moveBy(it.engine, steps * DigitalCaliperEngine.RESOLUTION_UM), savedMessage = null) }
    fun togglePower() = _uiState.update { it.copy(engine = DigitalCaliperEngine.togglePower(it.engine)) }
    fun pressZero() = _uiState.update { it.copy(engine = DigitalCaliperEngine.pressZero(it.engine)) }
    fun toggleUnit() = _uiState.update { it.copy(engine = DigitalCaliperEngine.toggleUnit(it.engine)) }
    fun toggleHold() = _uiState.update { it.copy(engine = DigitalCaliperEngine.toggleHold(it.engine)) }
    fun toggleLock() = _uiState.update { it.copy(engine = DigitalCaliperEngine.toggleLock(it.engine)) }
    fun reset() = _uiState.update { it.copy(engine = DigitalCaliperEngine.reset(it.engine), savedMessage = null) }
    fun setExplodeProgress(v: Float) = _uiState.update { it.copy(explodeProgress = v.coerceIn(0f, 1f)) }
    fun toggleXRayMode() = _uiState.update { it.copy(isXRayMode = !it.isXRayMode) }
    fun toggleCallouts() = _uiState.update { it.copy(showCallouts = !it.showCallouts) }
    fun updateFaultConfig(config: FaultConfig) = _uiState.update { it.copy(engine = it.engine.copy(faultConfig = config)) }
    fun dismissIncompatibleAlert() = _uiState.update { it.copy(incompatibleAlert = null) }

    fun setMeasurementKind(kind: MeasurementKind) {
        _uiState.update { s ->
            val currentWp = s.selectedWorkpiece
            val directZone = currentWp?.zones?.firstOrNull { it.kind == kind }
            val fallbackPair = if (directZone == null) {
                if (kind == MeasurementKind.DEPTH) {
                    workpieces.value.firstNotNullOfOrNull { wp ->
                        wp.zones.firstOrNull { it.kind == kind && it.actualUm >= 8_000 }?.let { wp to it }
                    } ?: workpieces.value.firstNotNullOfOrNull { wp ->
                        wp.zones.firstOrNull { it.kind == kind }?.let { wp to it }
                    }
                } else {
                    workpieces.value.firstNotNullOfOrNull { wp ->
                        wp.zones.firstOrNull { it.kind == kind }?.let { wp to it }
                    }
                }
            } else {
                null
            }
            val nextWp = fallbackPair?.first ?: currentWp
            val nextZone = directZone ?: fallbackPair?.second
            val nextPos = nextZone?.actualUm?.let {
                MeasurementUnitConverter.roundToStep(it, DigitalCaliperEngine.RESOLUTION_UM)
                    .coerceIn(0, DigitalCaliperEngine.MAX_RANGE_UM)
            } ?: s.engine.jawPositionUm
            s.copy(
                selectedWorkpiece = nextWp,
                selectedZone = nextZone ?: s.selectedZone,
                engine = s.engine.copy(
                    measurementKind = kind,
                    workpieceContactUm = nextZone?.actualUm,
                    jawPositionUm = nextPos,
                    isInContactWithWorkpiece = nextZone != null,
                    isLocked = false,
                    isHoldActive = false,
                    heldValueUm = null,
                    procedureWarning = null
                ),
                savedMessage = null
            )
        }
    }

    fun selectWorkpiece(workpiece: Workpiece, zone: MeasurementZone) {
        when (val check = ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_CALIPER, zone.kind, zone.actualUm)) {
            is ToolCompatibilityResult.Incompatible -> {
                _uiState.update { it.copy(incompatibleAlert = check) }
                return
            }
            ToolCompatibilityResult.Compatible -> Unit
        }
        val startPos = when (zone.kind) {
            MeasurementKind.INSIDE_DIAMETER -> {
                val minClearance = (zone.actualUm / 3).coerceAtMost(3_000)
                (zone.actualUm - 6_000).coerceAtLeast(minClearance)
            }
            MeasurementKind.DEPTH -> (zone.actualUm - 6_000).coerceAtLeast(0)
            else -> (zone.actualUm + 8_000).coerceAtMost(DigitalCaliperEngine.MAX_RANGE_UM)
        }
        val snappedStart = MeasurementUnitConverter.roundToStep(startPos, DigitalCaliperEngine.RESOLUTION_UM)
            .coerceIn(0, DigitalCaliperEngine.MAX_RANGE_UM)
        _uiState.update { s ->
            s.copy(
                selectedWorkpiece = workpiece,
                selectedZone = zone,
                engine = s.engine.copy(
                    measurementKind = zone.kind,
                    workpieceContactUm = zone.actualUm,
                    jawPositionUm = snappedStart,
                    isLocked = false,
                    isHoldActive = false,
                    heldValueUm = null,
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
                engine = s.engine.copy(workpieceContactUm = null, isInContactWithWorkpiece = false)
            )
        }
    }

    fun saveMeasurement() {
        val s = _uiState.value
        val reading = s.engine.displayedUm ?: run {
            _uiState.update { it.copy(savedMessage = "Nyalakan alat (ON) sebelum menyimpan hasil ukur.") }
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
                    instrumentType = InstrumentType.DIGITAL_CALIPER,
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
                DigitalCaliperViewModel(container, workpieceId, zoneId, taskId) as T
        }
    }
}
