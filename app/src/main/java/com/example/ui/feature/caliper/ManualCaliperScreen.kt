package com.example.ui.feature.caliper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppContainer
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.model.CaliperResolution
import com.example.domain.model.LearningMode
import com.example.domain.model.MeasurementKind
import com.example.ui.components.FaultSimulationBottomSheet
import com.example.ui.components.LearningModeSwitcher
import com.example.ui.components.ManualCaliperCanvas
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.MeasurementReadoutCard
import com.example.ui.components.StepGuideCard
import com.example.ui.components.ToleranceStatusCard
import com.example.ui.components.ToolCompatibilityAlertDialog
import com.example.ui.components.VernierMagnifierLoupe
import com.example.ui.components.WorkpiecePickerBottomSheet
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceGreenBg

@Composable
fun ManualCaliperScreen(
    container: AppContainer,
    workpieceId: String? = null,
    zoneId: String? = null,
    taskId: Long? = null,
    onBack: () -> Unit,
    onOpenLearning: () -> Unit
) {
    val viewModel: ManualCaliperViewModel = viewModel(
        factory = ManualCaliperViewModel.factory(container, workpieceId, zoneId, taskId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val workpieces by viewModel.workpieces.collectAsStateWithLifecycle()

    var showWorkpiecePicker by remember { mutableStateOf(false) }
    var showFaultSheet by remember { mutableStateOf(false) }
    var showExplodeBar by remember { mutableStateOf(false) }
    var blindInput by remember { mutableStateOf("") }

    val caliperSteps = remember {
        listOf(
            "Kenali Bagian Alat" to "Amati rahang tetap, rahang geser, nonius, lock screw, dan depth rod.",
            "Persiapan & Nol" to "Pastikan rahang bersih dan nol sejajar (gunakan Reset / Nolkan).",
            "Lakukan Pengukuran" to "Geser rahang atau pakai tombol halus hingga menyentuh benda kerja.",
            "Kunci & Baca Skala" to "Aktifkan Lock, perhatikan kaca pembesar nonius, lalu jumlahkan.",
            "Evaluasi & Simpan" to "Bandingkan terhadap toleransi lalu simpan ke Riwayat Pengukuran."
        )
    }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Jangka Sorong Manual",
                subtitle = "${uiState.selectedWorkpiece?.name ?: "Pengukuran Bebas"} • ${uiState.engine.resolution.label}",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showFaultSheet = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("caliper_fault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Fault Simulation",
                            tint = if (uiState.engine.faultConfig.hasAnyFault) OrangeAccent else Color.White
                        )
                    }
                    IconButton(
                        onClick = onOpenLearning,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Materi", tint = Color.White)
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
            val isLandscapeWide = maxWidth >= 680.dp

            if (isLandscapeWide) {
                // Landscape Industrial Bench Layout (Mockup 3 style)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.45f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ManualCaliperCanvas(
                            state = uiState.engine,
                            onDragDeltaUm = viewModel::dragByUm,
                            onToggleLock = viewModel::toggleLock,
                            workpieceShape = uiState.selectedWorkpiece?.shapeType,
                            showCallouts = uiState.showCallouts,
                            explodeProgress = uiState.explodeProgress,
                            isXRayMode = uiState.isXRayMode,
                            modifier = Modifier.fillMaxWidth().height(268.dp)
                        )
                        VernierMagnifierLoupe(state = uiState.engine)
                        CaliperControlCluster(
                            uiState = uiState,
                            viewModel = viewModel,
                            showExplodeBar = showExplodeBar,
                            onToggleExplodeBar = { showExplodeBar = !showExplodeBar },
                            onOpenWorkpiecePicker = { showWorkpiecePicker = true }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LearningModeSwitcher(
                            selectedMode = uiState.learningMode,
                            onModeSelected = viewModel::setLearningMode
                        )
                        MeasurementReadoutCard(
                            readingText = "${MeasurementUnitConverter.formatUmAsMm(uiState.engine.readingUm, uiState.engine.resolution.mmDecimals, commaDecimal = true)} mm",
                            secondaryUnitText = "${MeasurementUnitConverter.formatUmAsInch(uiState.engine.readingUm)} in",
                            formulaText = if (uiState.learningMode == LearningMode.LEARN) uiState.engine.formulaBreakdown else null,
                            isBlindMode = uiState.isBlindMode,
                            isLocked = uiState.engine.isLocked
                        )
                        if (uiState.isBlindMode) {
                            BlindAnswerBox(
                                input = blindInput,
                                onInputChange = { blindInput = it },
                                onSubmit = { viewModel.submitBlindReading(blindInput) },
                                feedback = uiState.blindAnswerFeedback
                            )
                        }
                        uiState.toleranceEvaluation?.let { eval ->
                            if (!uiState.isBlindMode) ToleranceStatusCard(eval, compact = true)
                        }
                        if (uiState.learningMode == LearningMode.LEARN) {
                            StepGuideCard("Langkah Pembelajaran", caliperSteps, uiState.activeStepIndex)
                        }
                    }
                }
            } else {
                // Portrait Ergonomic Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LearningModeSwitcher(
                        selectedMode = uiState.learningMode,
                        onModeSelected = viewModel::setLearningMode
                    )
                    ManualCaliperCanvas(
                        state = uiState.engine,
                        onDragDeltaUm = viewModel::dragByUm,
                        onToggleLock = viewModel::toggleLock,
                        workpieceShape = uiState.selectedWorkpiece?.shapeType,
                        showCallouts = uiState.showCallouts,
                        explodeProgress = uiState.explodeProgress,
                        isXRayMode = uiState.isXRayMode,
                        modifier = Modifier.fillMaxWidth().height(248.dp)
                    )
                    Text(
                        text = if (uiState.isBlindMode) {
                            "Kaca Pembesar Skala Utama & Nonius (Mode Blind)"
                        } else {
                            "Loupe 3,5× • SU: ${uiState.engine.mainScaleMm} mm | Garis Nonius #${uiState.engine.vernierCoincidentIndex} berimpit"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = NavyPrimary
                    )
                    VernierMagnifierLoupe(state = uiState.engine)
                    MeasurementReadoutCard(
                        readingText = "${MeasurementUnitConverter.formatUmAsMm(uiState.engine.readingUm, uiState.engine.resolution.mmDecimals, commaDecimal = true)} mm",
                        secondaryUnitText = "${MeasurementUnitConverter.formatUmAsInch(uiState.engine.readingUm)} in",
                        formulaText = if (uiState.learningMode != LearningMode.CHALLENGE) uiState.engine.formulaBreakdown else null,
                        isBlindMode = uiState.isBlindMode,
                        isLocked = uiState.engine.isLocked
                    )
                    if (uiState.isBlindMode) {
                        BlindAnswerBox(
                            input = blindInput,
                            onInputChange = { blindInput = it },
                            onSubmit = { viewModel.submitBlindReading(blindInput) },
                            feedback = uiState.blindAnswerFeedback
                        )
                    }
                    CaliperControlCluster(
                        uiState = uiState,
                        viewModel = viewModel,
                        showExplodeBar = showExplodeBar,
                        onToggleExplodeBar = { showExplodeBar = !showExplodeBar },
                        onOpenWorkpiecePicker = { showWorkpiecePicker = true }
                    )
                    uiState.toleranceEvaluation?.let { eval ->
                        if (!uiState.isBlindMode) ToleranceStatusCard(eval)
                    }
                    if (uiState.learningMode == LearningMode.LEARN) {
                        StepGuideCard("Langkah Pembelajaran", caliperSteps, uiState.activeStepIndex)
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
    uiState.incompatibleAlert?.let { alert ->
        ToolCompatibilityAlertDialog(alert, onDismiss = viewModel::dismissIncompatibleAlert)
    }
}

@Composable
private fun CaliperControlCluster(
    uiState: ManualCaliperUiState,
    viewModel: ManualCaliperViewModel,
    showExplodeBar: Boolean,
    onToggleExplodeBar: () -> Unit,
    onOpenWorkpiecePicker: () -> Unit
) {
    // Warning or Save confirmation banner
    uiState.engine.procedureWarning?.let { msg ->
        Surface(color = OrangeContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(text = msg, style = MaterialTheme.typography.bodySmall, color = NavyPrimary, modifier = Modifier.padding(10.dp))
        }
    }
    uiState.savedMessage?.let { msg ->
        Surface(color = ToleranceGreenBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(text = msg, style = MaterialTheme.typography.bodySmall, color = ToleranceGreen, modifier = Modifier.padding(10.dp))
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LabOutline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Ketelitian selector (0,05 / 0,02 mm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ketelitian:", style = MaterialTheme.typography.labelLarge, color = NavyPrimary)
                CaliperResolution.entries.forEach { res ->
                    FilterChip(
                        selected = uiState.engine.resolution == res,
                        onClick = { viewModel.setResolution(res) },
                        label = { Text(res.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealSecondary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("caliper_res_${res.stepUm}")
                    )
                }
            }

            // 2. Jenis Pengukuran (Outside, Inside, Depth, Step)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    MeasurementKind.OUTSIDE_DIAMETER,
                    MeasurementKind.INSIDE_DIAMETER,
                    MeasurementKind.DEPTH,
                    MeasurementKind.STEP
                ).forEach { kind ->
                    val selected = uiState.engine.measurementKind == kind
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setMeasurementKind(kind) },
                        label = { Text(kind.shortLabel, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("caliper_kind_${kind.name.lowercase()}")
                    )
                }
            }

            // 3. Exact Thumb Wheel Controls (-1 mm, -1 step, +1 step, +1 mm)
            val fineStepLabel = uiState.engine.resolution.label.removeSuffix(" mm")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.dragByUm(-1_000) },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_step_minus_1mm")
                ) { Text("−1 mm", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    onClick = { viewModel.stepFine(-1) },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_step_minus_1")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Kurangi $fineStepLabel mm", modifier = Modifier.size(14.dp))
                    Text(fineStepLabel, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { viewModel.stepFine(1) },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_step_plus_1")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah $fineStepLabel mm", modifier = Modifier.size(14.dp))
                    Text(fineStepLabel, style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { viewModel.dragByUm(1_000) },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_step_plus_1mm")
                ) { Text("+1 mm", style = MaterialTheme.typography.labelSmall) }
            }

            // 4. Primary Instrument Actions (Lock, Reset, Nol 0.00, Simpan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = viewModel::toggleLock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.engine.isLocked) OrangeAccent else NavyPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1.1f).height(48.dp).testTag("caliper_lock_button")
                ) {
                    Icon(
                        imageVector = if (uiState.engine.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(if (uiState.engine.isLocked) "Kunci" else "Lock", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = viewModel::reset,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = viewModel::closeToZero,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(0.85f).height(48.dp).testTag("caliper_zero_close_button")
                ) {
                    Text("0,00", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = viewModel::saveMeasurement,
                    colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1.05f).height(48.dp).testTag("caliper_save_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Simpan", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Simpan", style = MaterialTheme.typography.labelMedium)
                }
            }

            // 5. Innovative Views + Workpiece Selector (Exploded, X-Ray, Blind, Ganti Benda)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleExplodeBar,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_exploded_toggle")
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Exploded", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Explode", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::toggleXRayMode,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (uiState.isXRayMode) CyanHighlight.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_xray_toggle")
                ) {
                    Text("X-Ray", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = viewModel::toggleBlindMode,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("caliper_blind_toggle")
                ) {
                    Icon(
                        imageVector = if (uiState.isBlindMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Blind Mode",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("Blind", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onOpenWorkpiecePicker,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1.3f).height(48.dp).testTag("caliper_workpiece_button")
                ) {
                    Icon(Icons.Default.Category, contentDescription = "Benda", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Benda", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (showExplodeBar) {
                Column {
                    Text(
                        text = "Exploded View: ${(uiState.explodeProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = NavyPrimary
                    )
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

@Composable
internal fun BlindAnswerBox(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    feedback: String?
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, OrangeAccent)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Uji Pembacaan Mandiri (Blind Measurement)",
                style = MaterialTheme.typography.titleSmall,
                color = NavyPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("Hasil baca (mm)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("blind_answer_input")
                )
                Button(
                    onClick = onSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    modifier = Modifier.height(52.dp).testTag("blind_answer_submit")
                ) {
                    Text("Periksa")
                }
            }
            if (!feedback.isNullOrBlank()) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = NavyPrimary
                )
            }
        }
    }
}
