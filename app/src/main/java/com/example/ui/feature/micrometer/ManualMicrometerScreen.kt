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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.domain.model.LearningMode
import com.example.domain.model.MeasurementKind
import com.example.ui.components.FaultSimulationBottomSheet
import com.example.ui.components.LearningModeSwitcher
import com.example.ui.components.ManualMicrometerCanvas
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.MeasurementReadoutCard
import com.example.ui.components.MicrometerSleeveLoupe
import com.example.ui.components.StepGuideCard
import com.example.ui.components.ToleranceStatusCard
import com.example.ui.components.ToolCompatibilityAlertDialog
import com.example.ui.components.WorkpiecePickerBottomSheet
import com.example.ui.feature.caliper.BlindAnswerBox
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceGreenBg

@Composable
fun ManualMicrometerScreen(
    container: AppContainer,
    workpieceId: String? = null,
    zoneId: String? = null,
    taskId: Long? = null,
    onBack: () -> Unit,
    onOpenLearning: () -> Unit
) {
    val viewModel: ManualMicrometerViewModel = viewModel(
        factory = ManualMicrometerViewModel.factory(container, workpieceId, zoneId, taskId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val workpieces by viewModel.workpieces.collectAsStateWithLifecycle()

    var showWorkpiecePicker by remember { mutableStateOf(false) }
    var showFaultSheet by remember { mutableStateOf(false) }
    var showExplodeBar by remember { mutableStateOf(false) }
    var blindInput by remember { mutableStateOf("") }

    val microSteps = remember {
        listOf(
            "Letakkan Benda Kerja" to "Posisikan benda kerja tegak lurus di antara anvil dan spindle.",
            "Putar Thimble & Ratchet" to "Dekatkan spindle lalu gunakan Ratchet Stop (2–3 klik) agar gaya konstan.",
            "Kunci dengan Lock Lever" to "Aktifkan pengunci agar posisi spindle tidak bergeser.",
            "Baca Sleeve & Thimble" to "Jumlahkan skala sleeve (termasuk garis 0,5 mm) dengan skala thimble × 0,01 mm."
        )
    }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Mikrometer Sekrup Manual",
                subtitle = "${uiState.selectedWorkpiece?.name ?: "Bebas"} • 0–25 mm (0,01 mm)",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showFaultSheet = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("micrometer_fault_button")
                    ) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Fault Simulation",
                            tint = if (uiState.engine.faultConfig.hasAnyFault) OrangeAccent else Color.White
                        )
                    }
                    IconButton(onClick = onOpenLearning, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Materi", tint = Color.White)
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
                        ManualMicrometerCanvas(
                            state = uiState.engine,
                            onRotateThimbleDeltaUm = viewModel::rotateDeltaUm,
                            onToggleLock = viewModel::toggleLock,
                            onRatchetClick = viewModel::engageRatchetStop,
                            workpieceShape = uiState.selectedWorkpiece?.shapeType,
                            showCallouts = uiState.showCallouts,
                            explodeProgress = uiState.explodeProgress,
                            isXRayMode = uiState.isXRayMode,
                            modifier = Modifier.fillMaxWidth().height(255.dp)
                        )
                        if (!uiState.isBlindMode) {
                            MicrometerTelemetryHeader(uiState = uiState)
                        }
                        MicrometerSleeveLoupe(state = uiState.engine)
                        ManualMicrometerControls(
                            uiState = uiState,
                            viewModel = viewModel,
                            showExplodeBar = showExplodeBar,
                            onToggleExplodeBar = { showExplodeBar = !showExplodeBar },
                            onOpenWorkpiecePicker = { showWorkpiecePicker = true }
                        )
                    }
                    Column(
                        modifier = Modifier.weight(0.95f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LearningModeSwitcher(uiState.learningMode, viewModel::setLearningMode)
                        MeasurementReadoutCard(
                            readingText = "${MeasurementUnitConverter.formatUmAsMm(uiState.engine.readingUm, 2, commaDecimal = true)} mm",
                            secondaryUnitText = "${MeasurementUnitConverter.formatUmAsInch(uiState.engine.readingUm)} in",
                            formulaText = if (uiState.learningMode != LearningMode.CHALLENGE) uiState.engine.formulaBreakdown else null,
                            isBlindMode = uiState.isBlindMode,
                            isLocked = uiState.engine.isLocked
                        )
                        if (uiState.isBlindMode) {
                            BlindAnswerBox(blindInput, { blindInput = it }, { viewModel.submitBlindReading(blindInput) }, uiState.blindAnswerFeedback)
                        }
                        uiState.toleranceEvaluation?.let { if (!uiState.isBlindMode) ToleranceStatusCard(it, compact = true) }
                        if (uiState.learningMode == LearningMode.LEARN) {
                            StepGuideCard("Petunjuk Penggunaan", microSteps, uiState.activeStepIndex)
                        }
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
                    LearningModeSwitcher(uiState.learningMode, viewModel::setLearningMode)
                    ManualMicrometerCanvas(
                        state = uiState.engine,
                        onRotateThimbleDeltaUm = viewModel::rotateDeltaUm,
                        onToggleLock = viewModel::toggleLock,
                        onRatchetClick = viewModel::engageRatchetStop,
                        workpieceShape = uiState.selectedWorkpiece?.shapeType,
                        showCallouts = uiState.showCallouts,
                        explodeProgress = uiState.explodeProgress,
                        isXRayMode = uiState.isXRayMode,
                        modifier = Modifier.fillMaxWidth().height(238.dp)
                    )
                    if (!uiState.isBlindMode) {
                        MicrometerTelemetryHeader(uiState = uiState)
                    } else {
                        Text(
                            text = "Kaca Pembesar Sleeve (Atas 1 mm / Bawah 0,5 mm) & Thimble (0,01 mm)",
                            style = MaterialTheme.typography.labelMedium,
                            color = NavyPrimary
                        )
                    }
                    MicrometerSleeveLoupe(state = uiState.engine)
                    MeasurementReadoutCard(
                        readingText = "${MeasurementUnitConverter.formatUmAsMm(uiState.engine.readingUm, 2, commaDecimal = true)} mm",
                        secondaryUnitText = "${MeasurementUnitConverter.formatUmAsInch(uiState.engine.readingUm)} in",
                        formulaText = if (uiState.learningMode != LearningMode.CHALLENGE) uiState.engine.formulaBreakdown else null,
                        isBlindMode = uiState.isBlindMode,
                        isLocked = uiState.engine.isLocked
                    )
                    if (uiState.isBlindMode) {
                        BlindAnswerBox(blindInput, { blindInput = it }, { viewModel.submitBlindReading(blindInput) }, uiState.blindAnswerFeedback)
                    }
                    ManualMicrometerControls(
                        uiState = uiState,
                        viewModel = viewModel,
                        showExplodeBar = showExplodeBar,
                        onToggleExplodeBar = { showExplodeBar = !showExplodeBar },
                        onOpenWorkpiecePicker = { showWorkpiecePicker = true }
                    )
                    uiState.toleranceEvaluation?.let { if (!uiState.isBlindMode) ToleranceStatusCard(it) }
                    if (uiState.learningMode == LearningMode.LEARN) {
                        StepGuideCard("Petunjuk Penggunaan", microSteps, uiState.activeStepIndex)
                    }
                }
            }
        }
    }

    if (showWorkpiecePicker) {
        WorkpiecePickerBottomSheet(
            workpieces = workpieces,
            selectedWorkpieceId = uiState.selectedWorkpiece?.id,
            selectedZoneId = uiState.selectedZone?.zoneId,
            isBlindMode = uiState.isBlindMode,
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
private fun MicrometerTelemetryHeader(uiState: ManualMicrometerUiState) {
    val eng = uiState.engine
    val sleeveText = MeasurementUnitConverter.formatUmAsMm(eng.sleeveScaleUm, decimals = 1, commaDecimal = true)
    val thimbleText = MeasurementUnitConverter.formatUmAsMm(eng.thimbleDivision * 10, decimals = 2, commaDecimal = true)
    val revFormatted = String.format(java.util.Locale.US, "%.2f", eng.totalRevolutions).replace('.', ',')
    Surface(
        color = NavyPrimary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LabOutline),
        modifier = Modifier.fillMaxWidth().testTag("micro_telemetry_hud")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Sleeve: $sleeveText mm • Thimble: ${eng.thimbleDivision} ($thimbleText mm) • $revFormatted putaran (pitch 0,5 mm)",
                style = MaterialTheme.typography.labelSmall,
                color = NavyPrimary
            )
            val statusParts = buildList {
                if (eng.hasZeroOffset) {
                    val off = MeasurementUnitConverter.formatUmAsMm(eng.zeroCalibrationOffsetUm, 2, commaDecimal = true, showPlusSign = true)
                    add("Zero Offset: $off mm")
                }
                if (eng.isAtUpperLimit) add("Batas Maks 25,00 mm")
                else if (eng.isAtLowerLimit && eng.workpieceContactUm != null) add("Kontak Benda Kerja")
                else if (eng.isAtLowerLimit) add("Batas Min 0,00 mm (Anvil)")
            }
            if (statusParts.isNotEmpty()) {
                Text(
                    text = statusParts.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (eng.isOverPressureWarning) Color(0xFFDC2626) else TealSecondary
                )
            }
        }
    }
}

@Composable
private fun ManualMicrometerControls(
    uiState: ManualMicrometerUiState,
    viewModel: ManualMicrometerViewModel,
    showExplodeBar: Boolean,
    onToggleExplodeBar: () -> Unit,
    onOpenWorkpiecePicker: () -> Unit
) {
    uiState.engine.procedureWarning?.let { msg ->
        Surface(
            color = if (uiState.engine.ratchetClickCount >= 2 && !uiState.engine.isOverPressureWarning) ToleranceGreenBg else OrangeContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("micro_procedure_warning")
        ) {
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
            // Allowed measurement kinds (Outside & Thickness; Inside shows educational compatibility guard)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    MeasurementKind.OUTSIDE_DIAMETER,
                    MeasurementKind.THICKNESS,
                    MeasurementKind.INSIDE_DIAMETER
                ).forEach { kind ->
                    FilterChip(
                        selected = uiState.engine.measurementKind == kind,
                        onClick = { viewModel.setMeasurementKind(kind) },
                        label = { Text(kind.shortLabel, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("micro_kind_${kind.name.lowercase()}")
                    )
                }
            }

            // Putar Thimble (+/-0.01 mm = 1 division; +/-0.5 mm = 1 full 360° pitch revolution)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.stepThimble(-50) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("micro_step_minus_pitch")
                ) { Text("−0,5") }
                OutlinedButton(
                    onClick = { viewModel.stepThimble(-1) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("micro_step_minus_1")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "-0,01 mm", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("0,01", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { viewModel.stepThimble(1) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("micro_step_plus_1")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+0,01 mm", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("0,01", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { viewModel.stepThimble(50) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("micro_step_plus_pitch")
                ) { Text("+0,5") }
            }

            // Ratchet Stop + Lock Lever + Close 0,00 + Reset
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = viewModel::engageRatchetStop,
                    colors = ButtonDefaults.buttonColors(containerColor = ToleranceGreen),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1.35f).height(48.dp).testTag("micro_ratchet_button")
                ) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = "Ratchet Stop", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Ratchet (${uiState.engine.ratchetClickCount}×)", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = viewModel::toggleLock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.engine.isLocked) OrangeAccent else NavyPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(0.95f).height(48.dp).testTag("micro_lock_button")
                ) {
                    Icon(if (uiState.engine.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock Lever", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Lock", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::closeToZero,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(0.75f).height(48.dp).testTag("micro_close_zero_button")
                ) {
                    Text("0,00", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::reset,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(0.85f).height(48.dp).testTag("micro_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Explode, X-Ray, Blind, Benda Kerja
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onToggleExplodeBar,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Explode", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Explode", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::toggleXRayMode,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (uiState.isXRayMode) CyanHighlight.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    modifier = Modifier.weight(0.85f).height(48.dp)
                ) { Text("X-Ray", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    onClick = viewModel::toggleBlindMode,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(0.85f).height(48.dp)
                ) {
                    Icon(if (uiState.isBlindMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Blind", modifier = Modifier.size(15.dp))
                }
                Button(
                    onClick = onOpenWorkpiecePicker,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1.2f).height(48.dp).testTag("micro_workpiece_button")
                ) {
                    Icon(Icons.Default.Category, contentDescription = "Benda", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Benda")
                }
            }

            // Zero Setting + Simpan Hasil
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::calibrateZero,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("micro_zero_button")
                ) { Text("Zero Setting") }
                Button(
                    onClick = viewModel::saveMeasurement,
                    colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                    modifier = Modifier.weight(1.2f).height(48.dp).testTag("micro_save_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Simpan", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Hasil")
                }
            }

            if (showExplodeBar) {
                Column {
                    Text("Exploded View: ${(uiState.explodeProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                    Slider(
                        value = uiState.explodeProgress,
                        onValueChange = viewModel::setExplodeProgress,
                        colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = CyanHighlight)
                    )
                }
            }
        }
    }
}
