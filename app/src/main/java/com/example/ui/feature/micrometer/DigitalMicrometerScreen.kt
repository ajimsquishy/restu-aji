package com.example.ui.feature.micrometer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppContainer
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementUnit
import com.example.ui.components.DigitalMicrometerCanvas
import com.example.ui.components.FaultSimulationBottomSheet
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.MeasurementReadoutCard
import com.example.ui.components.ToleranceStatusCard
import com.example.ui.components.ToolCompatibilityAlertDialog
import com.example.ui.components.WorkpiecePickerBottomSheet
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceGreenBg
import com.example.ui.theme.ToleranceRed

@Composable
fun DigitalMicrometerScreen(
    container: AppContainer,
    workpieceId: String? = null,
    zoneId: String? = null,
    taskId: Long? = null,
    onBack: () -> Unit
) {
    val viewModel: DigitalMicrometerViewModel = viewModel(
        factory = DigitalMicrometerViewModel.factory(container, workpieceId, zoneId, taskId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val workpieces by viewModel.workpieces.collectAsStateWithLifecycle()

    var showWorkpiecePicker by remember { mutableStateOf(false) }
    var showFaultSheet by remember { mutableStateOf(false) }

    val displayedUm = uiState.engine.displayedUm
    val primaryReadout = if (displayedUm == null) "OFF"
    else MeasurementUnitConverter.formatReading(displayedUm, uiState.engine.unit, mmDecimals = 2)
    val altReadout = if (displayedUm == null) null else {
        val altUnit = if (uiState.engine.unit == MeasurementUnit.MILLIMETER) MeasurementUnit.INCH else MeasurementUnit.MILLIMETER
        MeasurementUnitConverter.formatReading(displayedUm, altUnit, mmDecimals = 2)
    }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Mikrometer Sekrup Digital",
                subtitle = "${uiState.selectedWorkpiece?.name ?: "Bebas"} • LCD 0,01 mm",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showFaultSheet = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("digital_micro_fault_button")
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Fault Simulation",
                            tint = if (uiState.engine.faultConfig.hasAnyFault) OrangeAccent else Color.White
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            val isLandscape = maxWidth >= 680.dp
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1.45f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DigitalMicrometerCanvas(
                            state = uiState.engine,
                            onRotateThimbleDeltaUm = viewModel::rotateDeltaUm,
                            onToggleLock = viewModel::toggleLock,
                            onRatchetClick = viewModel::engageRatchet,
                            onTogglePower = viewModel::togglePower,
                            onToggleUnit = viewModel::toggleUnit,
                            workpieceShape = uiState.selectedWorkpiece?.shapeType,
                            isXRayMode = uiState.isXRayMode,
                            modifier = Modifier.fillMaxWidth().height(255.dp)
                        )
                        DigitalMicrometerControls(uiState, viewModel) { showWorkpiecePicker = true }
                    }
                    Column(
                        modifier = Modifier.weight(0.95f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MeasurementReadoutCard(
                            readingText = primaryReadout,
                            secondaryUnitText = altReadout,
                            formulaText = "Spindle: ${MeasurementUnitConverter.formatUmAsMm(uiState.engine.spindlePositionUm, 2)} mm | Pitch 0,5 mm • 0,01 mm",
                            isLocked = uiState.engine.isLocked
                        )
                        uiState.toleranceEvaluation?.let { ToleranceStatusCard(it, compact = true) }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DigitalMicrometerCanvas(
                        state = uiState.engine,
                        onRotateThimbleDeltaUm = viewModel::rotateDeltaUm,
                        onToggleLock = viewModel::toggleLock,
                        onRatchetClick = viewModel::engageRatchet,
                        onTogglePower = viewModel::togglePower,
                        onToggleUnit = viewModel::toggleUnit,
                        workpieceShape = uiState.selectedWorkpiece?.shapeType,
                        isXRayMode = uiState.isXRayMode,
                        modifier = Modifier.fillMaxWidth().height(235.dp)
                    )
                    MeasurementReadoutCard(
                        readingText = primaryReadout,
                        secondaryUnitText = altReadout,
                        formulaText = "Posisi Spindle Aktual: ${MeasurementUnitConverter.formatUmAsMm(uiState.engine.spindlePositionUm, 2)} mm (0,01 mm)",
                        isLocked = uiState.engine.isLocked
                    )
                    DigitalMicrometerControls(uiState, viewModel) { showWorkpiecePicker = true }
                    uiState.toleranceEvaluation?.let { ToleranceStatusCard(it) }
                }
            }
        }
    }

    if (showWorkpiecePicker) {
        WorkpiecePickerBottomSheet(
            workpieces = workpieces,
            selectedWorkpieceId = uiState.selectedWorkpiece?.id,
            selectedZoneId = uiState.selectedZone?.zoneId,
            isBlindMode = false,
            onSelect = viewModel::selectWorkpiece,
            onRemoveWorkpiece = viewModel::removeWorkpiece,
            onDismiss = { showWorkpiecePicker = false }
        )
    }
    if (showFaultSheet) {
        FaultSimulationBottomSheet(
            faultConfig = uiState.engine.faultConfig,
            onUpdate = viewModel::updateFaultConfig,
            onDismiss = { showFaultSheet = false }
        )
    }
    uiState.incompatibleAlert?.let { ToolCompatibilityAlertDialog(it, viewModel::dismissIncompatibleAlert) }
}

@Composable
private fun DigitalMicrometerControls(
    uiState: DigitalMicrometerUiState,
    viewModel: DigitalMicrometerViewModel,
    onOpenWorkpiecePicker: () -> Unit
) {
    uiState.engine.procedureWarning?.let { msg ->
        Surface(color = OrangeContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(msg, style = MaterialTheme.typography.bodySmall, color = NavyPrimary, modifier = Modifier.padding(10.dp))
        }
    }
    uiState.savedMessage?.let { msg ->
        Surface(color = ToleranceGreenBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(msg, style = MaterialTheme.typography.bodySmall, color = ToleranceGreen, modifier = Modifier.padding(10.dp))
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LabOutline)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Digital buttons: ON/OFF, ZERO, mm/inch, HOLD
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = viewModel::togglePower,
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.engine.isPowerOn) ToleranceRed else ToleranceGreen),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_micro_power_button")
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "ON/OFF", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (uiState.engine.isPowerOn) "OFF" else "ON")
                }
                Button(
                    onClick = viewModel::pressZero,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_micro_zero_button")
                ) { Text("ZERO") }
                Button(
                    onClick = viewModel::toggleUnit,
                    colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_micro_unit_button")
                ) { Text("mm/in") }
                Button(
                    onClick = viewModel::toggleHold,
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.engine.isHoldActive) CyanHighlight else NavyPrimary),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_micro_hold_button")
                ) { Text("HOLD") }
            }

            // Allowed measurement kinds
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    MeasurementKind.OUTSIDE_DIAMETER,
                    MeasurementKind.THICKNESS,
                    MeasurementKind.DEPTH
                ).forEach { kind ->
                    FilterChip(
                        selected = uiState.engine.measurementKind == kind,
                        onClick = { viewModel.setMeasurementKind(kind) },
                        label = { Text(kind.shortLabel, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Precision spindle step buttons (0.01 mm & 0.5 mm = 1 full revolution)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { viewModel.rotateDeltaUm(-500, false) }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f).height(48.dp)) { Text("−0,5") }
                OutlinedButton(onClick = { viewModel.rotateDeltaUm(-10, false) }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f).height(48.dp).testTag("digital_micro_minus_10um")) { Text("−0,01") }
                OutlinedButton(onClick = { viewModel.rotateDeltaUm(10, false) }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f).height(48.dp).testTag("digital_micro_plus_10um")) { Text("+0,01") }
                OutlinedButton(onClick = { viewModel.rotateDeltaUm(500, false) }, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f).height(48.dp)) { Text("+0,5") }
            }

            // Ratchet, Lock, Reset, Benda
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = viewModel::engageRatchet,
                    colors = ButtonDefaults.buttonColors(containerColor = ToleranceGreen),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1.2f).height(48.dp).testTag("digital_micro_ratchet_button")
                ) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = "Ratchet", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Ratchet")
                }
                Button(
                    onClick = viewModel::toggleLock,
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.engine.isLocked) OrangeAccent else NavyPrimary),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_micro_lock_button")
                ) {
                    Icon(if (uiState.engine.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Lock")
                }
                OutlinedButton(
                    onClick = viewModel::reset,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(0.9f).height(48.dp).testTag("digital_micro_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                }
                Button(
                    onClick = onOpenWorkpiecePicker,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1.1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Category, contentDescription = "Benda", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Benda")
                }
            }

            Button(
                onClick = viewModel::saveMeasurement,
                colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("digital_micro_save_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = "Simpan", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Simpan Hasil ke Riwayat")
            }
        }
    }
}
