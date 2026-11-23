package com.example.ui.feature.caliper

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import com.example.ui.components.DigitalCaliperCanvas
import com.example.ui.components.FaultSimulationBottomSheet
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.MeasurementModePillRow
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
fun DigitalCaliperScreen(
    container: AppContainer,
    workpieceId: String? = null,
    zoneId: String? = null,
    taskId: Long? = null,
    onBack: () -> Unit
) {
    val viewModel: DigitalCaliperViewModel = viewModel(
        factory = DigitalCaliperViewModel.factory(container, workpieceId, zoneId, taskId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val workpieces by viewModel.workpieces.collectAsStateWithLifecycle()

    var showWorkpiecePicker by remember { mutableStateOf(false) }
    var showFaultSheet by remember { mutableStateOf(false) }

    val displayedUm = uiState.engine.displayedUm
    val primaryReadout = if (displayedUm == null) {
        "OFF"
    } else {
        MeasurementUnitConverter.formatReading(displayedUm, uiState.engine.unit, mmDecimals = 2)
    }
    val secondaryReadout = if (displayedUm == null) {
        null
    } else {
        val altUnit = if (uiState.engine.unit == MeasurementUnit.MILLIMETER) MeasurementUnit.INCH else MeasurementUnit.MILLIMETER
        MeasurementUnitConverter.formatReading(displayedUm, altUnit, mmDecimals = 2)
    }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Jangka Sorong Digital",
                subtitle = "${uiState.selectedWorkpiece?.name ?: "Pengukuran Bebas"} • LCD 0,01 mm",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showFaultSheet = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("digital_caliper_fault_button")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Simulasi Kesalahan",
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
                        DigitalCaliperCanvas(
                            state = uiState.engine,
                            onDragDeltaUm = viewModel::dragByUm,
                            workpieceShape = uiState.selectedWorkpiece?.shapeType,
                            showCallouts = uiState.showCallouts,
                            explodeProgress = uiState.explodeProgress,
                            isXRayMode = uiState.isXRayMode,
                            modifier = Modifier.fillMaxWidth().height(255.dp)
                        )
                        DigitalCaliperControls(uiState, viewModel) { showWorkpiecePicker = true }
                    }
                    Column(
                        modifier = Modifier.weight(0.95f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MeasurementReadoutCard(
                            readingText = primaryReadout,
                            secondaryUnitText = secondaryReadout,
                            formulaText = buildString {
                                append("Posisi Rahang: ${MeasurementUnitConverter.formatUmAsMm(uiState.engine.jawPositionUm, 2)} mm")
                                if (uiState.engine.zeroOffsetUm != 0) {
                                    append(" | ZERO Offset: ${MeasurementUnitConverter.formatUmAsMm(uiState.engine.zeroOffsetUm, 2)} mm")
                                }
                            },
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
                    DigitalCaliperCanvas(
                        state = uiState.engine,
                        onDragDeltaUm = viewModel::dragByUm,
                        workpieceShape = uiState.selectedWorkpiece?.shapeType,
                        showCallouts = uiState.showCallouts,
                        explodeProgress = uiState.explodeProgress,
                        isXRayMode = uiState.isXRayMode,
                        modifier = Modifier.fillMaxWidth().height(235.dp)
                    )
                    MeasurementReadoutCard(
                        readingText = primaryReadout,
                        secondaryUnitText = secondaryReadout,
                        formulaText = "Sensor Rahang: ${MeasurementUnitConverter.formatUmAsMm(uiState.engine.sensorUm, 2)} mm" +
                            if (uiState.engine.isHoldActive) " • HOLD AKTIF" else "",
                        isLocked = uiState.engine.isLocked
                    )
                    DigitalCaliperControls(uiState, viewModel) { showWorkpiecePicker = true }
                    uiState.toleranceEvaluation?.let { ToleranceStatusCard(it) }
                    Spacer(Modifier.height(16.dp))
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
private fun DigitalCaliperControls(
    uiState: DigitalCaliperUiState,
    viewModel: DigitalCaliperViewModel,
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
            // Digital instrument faceplate buttons: ON/OFF, ZERO, mm/inch, HOLD
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = viewModel::togglePower,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.engine.isPowerOn) ToleranceRed else ToleranceGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_power_button")
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "ON/OFF", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (uiState.engine.isPowerOn) "OFF" else "ON", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = viewModel::pressZero,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_zero_button")
                ) {
                    Text("ZERO", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = viewModel::toggleUnit,
                    colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_unit_button")
                ) {
                    Text("mm/in", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = viewModel::toggleHold,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.engine.isHoldActive) CyanHighlight else NavyPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_hold_button")
                ) {
                    Text("HOLD", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Measurement Kind Pills (single-line, balanced widths)
            MeasurementModePillRow(
                kinds = listOf(
                    MeasurementKind.OUTSIDE_DIAMETER,
                    MeasurementKind.INSIDE_DIAMETER,
                    MeasurementKind.DEPTH,
                    MeasurementKind.STEP
                ),
                selectedKind = uiState.engine.measurementKind,
                onSelect = viewModel::setMeasurementKind
            )

            // Fine Jaw Motion Controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.stepFine(-100) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("−1 mm", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = { viewModel.stepFine(-1) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_minus_step")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "-0.01 mm", modifier = Modifier.size(16.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.stepFine(1) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_plus_step")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+0.01 mm", modifier = Modifier.size(16.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.stepFine(100) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("+1 mm", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }

            // Lock, Reset, X-Ray, Workpiece, Save
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = viewModel::toggleLock,
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.engine.isLocked) OrangeAccent else NavyPrimary),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_lock_button")
                ) {
                    Icon(if (uiState.engine.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Lock", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = viewModel::reset,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("digital_caliper_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Reset", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = viewModel::toggleXRayMode,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(0.9f).height(48.dp)
                ) { Text("X-Ray", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) }
                Button(
                    onClick = onOpenWorkpiecePicker,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1.1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Category, contentDescription = "Benda", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Benda", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }

            Button(
                onClick = viewModel::saveMeasurement,
                colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("digital_caliper_save_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = "Simpan", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Simpan Hasil Pengukuran ke Riwayat")
            }
        }
    }
}
