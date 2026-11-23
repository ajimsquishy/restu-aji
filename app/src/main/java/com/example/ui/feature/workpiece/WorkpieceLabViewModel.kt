package com.example.ui.feature.workpiece

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.WorkpieceCatalog
import com.example.di.AppContainer
import com.example.domain.engine.ToolCompatibilityGuard
import com.example.domain.engine.WorkpieceGeometryEngine
import com.example.domain.engine.WorkpieceGeometryProfile
import com.example.domain.model.InstrumentType
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementZone
import com.example.domain.model.ToolCompatibilityResult
import com.example.domain.model.Workpiece
import com.example.domain.model.WorkpieceShapeType
import com.example.ui.components.WorkpieceCameraMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkpieceLabUiState(
    val selectedWorkpieceId: String = WorkpieceCatalog.defaultWorkpieces.first().id,
    val selectedZoneId: String = WorkpieceCatalog.defaultWorkpieces.first().zones.first().zoneId,
    val rotationDeg: Float = 24f,
    val tiltDeg: Float = 14f,
    val zoomScale: Float = 1.0f,
    val panOffset: Offset = Offset.Zero,
    val cameraMode: WorkpieceCameraMode = WorkpieceCameraMode.ROTATE,
    val isBlindMode: Boolean = false,
    val incompatibleAlert: ToolCompatibilityResult.Incompatible? = null
) {
    fun resolveWorkpiece(catalog: List<Workpiece>): Workpiece =
        catalog.firstOrNull { it.id == selectedWorkpieceId } ?: catalog.first()

    fun resolveZone(workpiece: Workpiece): MeasurementZone =
        workpiece.zones.firstOrNull { it.zoneId == selectedZoneId } ?: workpiece.zones.first()

    fun buildProfile(workpiece: Workpiece): WorkpieceGeometryProfile =
        WorkpieceGeometryEngine.buildProfile(workpiece)
}

class WorkpieceLabViewModel(
    private val container: AppContainer
) : ViewModel() {

    val workpieces: StateFlow<List<Workpiece>> = container.workpieceRepository.allWorkpieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkpieceCatalog.defaultWorkpieces)

    private val _uiState = MutableStateFlow(WorkpieceLabUiState())
    val uiState: StateFlow<WorkpieceLabUiState> = _uiState.asStateFlow()

    fun selectWorkpiece(workpiece: Workpiece) {
        _uiState.update {
            it.copy(
                selectedWorkpieceId = workpiece.id,
                selectedZoneId = workpiece.zones.first().zoneId,
                rotationDeg = 24f,
                tiltDeg = 14f,
                zoomScale = 1.0f,
                panOffset = Offset.Zero
            )
        }
    }

    fun selectZone(zoneId: String) {
        _uiState.update { it.copy(selectedZoneId = zoneId) }
    }

    fun toggleCameraMode() {
        _uiState.update {
            val next = if (it.cameraMode == WorkpieceCameraMode.ROTATE) {
                WorkpieceCameraMode.PAN
            } else {
                WorkpieceCameraMode.ROTATE
            }
            it.copy(cameraMode = next)
        }
    }

    fun applyViewportTransform(
        deltaRotationDeg: Float,
        deltaTiltDeg: Float,
        zoomFactor: Float,
        panDelta: Offset
    ) {
        _uiState.update { s ->
            val nextRot = (s.rotationDeg + deltaRotationDeg) % 360f
            val nextTilt = (s.tiltDeg + deltaTiltDeg).coerceIn(-35f, 35f)
            val nextZoom = (s.zoomScale * zoomFactor).coerceIn(0.6f, 2.5f)
            val nextPan = Offset(
                x = (s.panOffset.x + panDelta.x).coerceIn(-180f, 180f),
                y = (s.panOffset.y + panDelta.y).coerceIn(-140f, 140f)
            )
            s.copy(
                rotationDeg = nextRot,
                tiltDeg = nextTilt,
                zoomScale = nextZoom,
                panOffset = nextPan
            )
        }
    }

    fun rotateBy(deltaDeg: Float) {
        _uiState.update { it.copy(rotationDeg = (it.rotationDeg + deltaDeg) % 360f) }
    }

    fun zoomBy(deltaZoom: Float) {
        _uiState.update { it.copy(zoomScale = (it.zoomScale + deltaZoom).coerceIn(0.6f, 2.5f)) }
    }

    fun panBy(dx: Float, dy: Float) {
        _uiState.update { s ->
            s.copy(
                panOffset = Offset(
                    x = (s.panOffset.x + dx).coerceIn(-180f, 180f),
                    y = (s.panOffset.y + dy).coerceIn(-140f, 140f)
                )
            )
        }
    }

    fun resetCamera() {
        _uiState.update {
            it.copy(
                rotationDeg = 24f,
                tiltDeg = 14f,
                zoomScale = 1.0f,
                panOffset = Offset.Zero
            )
        }
    }

    fun toggleBlindMode() {
        _uiState.update { it.copy(isBlindMode = !it.isBlindMode) }
    }

    fun dismissIncompatibleAlert() {
        _uiState.update { it.copy(incompatibleAlert = null) }
    }

    fun validateAndLaunch(
        instrument: InstrumentType,
        onCompatible: (InstrumentType, String, String) -> Unit
    ) {
        val state = _uiState.value
        val wp = state.resolveWorkpiece(workpieces.value)
        val zone = state.resolveZone(wp)
        when (val result = ToolCompatibilityGuard.validate(instrument, zone.kind, zone.actualUm)) {
            is ToolCompatibilityResult.Compatible -> onCompatible(instrument, wp.id, zone.zoneId)
            is ToolCompatibilityResult.Incompatible -> _uiState.update { it.copy(incompatibleAlert = result) }
        }
    }

    fun addCustomWorkpiece(
        name: String,
        shape: WorkpieceShapeType,
        kind: MeasurementKind,
        actualUm: Int,
        nominalUm: Int,
        tolUm: Int
    ) {
        viewModelScope.launch {
            val newId = container.workpieceRepository.addCustomWorkpiece(
                name = name,
                shapeType = shape,
                material = "Baja Kustom SMK",
                primaryKind = kind,
                actualUm = actualUm,
                nominalUm = nominalUm,
                upperTolUm = tolUm,
                lowerTolUm = tolUm
            )
            _uiState.update {
                it.copy(
                    selectedWorkpieceId = newId,
                    selectedZoneId = "z_custom_1",
                    rotationDeg = 24f,
                    tiltDeg = 14f,
                    zoomScale = 1.0f,
                    panOffset = Offset.Zero
                )
            }
        }
    }

    fun deleteCustomWorkpiece(id: String) {
        val fallback = WorkpieceCatalog.defaultWorkpieces.first()
        _uiState.update {
            it.copy(
                selectedWorkpieceId = fallback.id,
                selectedZoneId = fallback.zones.first().zoneId
            )
        }
        viewModelScope.launch {
            container.workpieceRepository.deleteCustomWorkpiece(id)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkpieceLabViewModel(container) as T
        }
    }
}
