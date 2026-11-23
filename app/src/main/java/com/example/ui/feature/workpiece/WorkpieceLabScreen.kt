package com.example.ui.feature.workpiece

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppContainer
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.engine.ToolCompatibilityGuard
import com.example.domain.model.InstrumentType
import com.example.domain.model.MeasurementKind
import com.example.domain.model.ToolCompatibilityResult
import com.example.domain.model.WorkpieceShapeType
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.ToolCompatibilityAlertDialog
import com.example.ui.components.WorkpieceCameraMode
import com.example.ui.components.WorkpieceViewportCanvas
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceRed

@Composable
fun WorkpieceLabScreen(
    container: AppContainer,
    onBack: (() -> Unit)? = null,
    onLaunchSimulator: (InstrumentType, String, String) -> Unit
) {
    val viewModel: WorkpieceLabViewModel = viewModel(factory = WorkpieceLabViewModel.factory(container))
    val workpieces by viewModel.workpieces.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val selectedWorkpiece = remember(workpieces, uiState.selectedWorkpieceId) {
        uiState.resolveWorkpiece(workpieces)
    }
    val selectedZone = remember(selectedWorkpiece, uiState.selectedZoneId) {
        uiState.resolveZone(selectedWorkpiece)
    }
    val geometryProfile = remember(selectedWorkpiece) {
        uiState.buildProfile(selectedWorkpiece)
    }

    var showCustomDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Workpiece Laboratory",
                subtitle = "${workpieces.size} Benda Kerja Parametrik • Data Dimensi Aktual",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = viewModel::toggleBlindMode,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("workpiece_blind_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isBlindMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Blind Measurement",
                            tint = if (uiState.isBlindMode) OrangeAccent else Color.White
                        )
                    }
                    IconButton(
                        onClick = { showCustomDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("workpiece_add_custom_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Buat Benda Kustom", tint = Color.White)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("workpiece_lab_screen"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Horizontal object selector covering all 11+ machining workpieces
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("wp_selector_row")
                ) {
                    items(workpieces, key = { it.id }) { wp ->
                        val isSelected = wp.id == selectedWorkpiece.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectWorkpiece(wp) },
                            label = {
                                Text(
                                    "${wp.shapeType.label}: ${wp.name}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeAccent,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = NavyPrimary
                            ),
                            modifier = Modifier.testTag("wp_chip_${wp.id}")
                        )
                    }
                }
            }

            // 2. Interactive Parametric 360° Workpiece Viewport Canvas
            item {
                WorkpieceViewportCanvas(
                    workpiece = selectedWorkpiece,
                    selectedZone = selectedZone,
                    rotationDeg = uiState.rotationDeg,
                    tiltDeg = uiState.tiltDeg,
                    zoomScale = uiState.zoomScale,
                    panOffset = uiState.panOffset,
                    cameraMode = uiState.cameraMode,
                    isBlindMode = uiState.isBlindMode,
                    onTransform = viewModel::applyViewportTransform,
                    onSelectZone = viewModel::selectZone,
                    modifier = Modifier.fillMaxWidth().height(242.dp)
                )
            }

            // 3. Complete Camera Controls (Rotate/Pan Mode, Rotate +45°, Zoom +/-, Pan Nudge, Reset Camera)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val normRot = ((uiState.rotationDeg.toInt() % 360) + 360) % 360
                        Text(
                            text = "Kamera: Rot ${normRot}° • Zoom ${(uiState.zoomScale * 100).toInt()}% • Pan (${uiState.panOffset.x.toInt()}, ${uiState.panOffset.y.toInt()}) • Ketuk titik cyan pada benda untuk pilih zona",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealSecondary,
                            modifier = Modifier.testTag("wp_camera_hud_summary")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = viewModel::toggleCameraMode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.cameraMode == WorkpieceCameraMode.PAN) OrangeAccent else NavyPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.weight(1.25f).height(48.dp).testTag("wp_camera_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.cameraMode == WorkpieceCameraMode.PAN) Icons.Default.OpenWith else Icons.Default.Autorenew,
                                    contentDescription = "Mode Kamera",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (uiState.cameraMode == WorkpieceCameraMode.PAN) "Pan" else "Putar",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.rotateBy(45f) },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(0.95f).height(48.dp).testTag("wp_rotate_button")
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Putar 45 derajat", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("45°", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.zoomBy(0.25f) },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(0.75f).height(48.dp).testTag("wp_zoom_in_button")
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar", modifier = Modifier.size(18.dp))
                            }
                            OutlinedButton(
                                onClick = { viewModel.zoomBy(-0.25f) },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(0.75f).height(48.dp).testTag("wp_zoom_out_button")
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil", modifier = Modifier.size(18.dp))
                            }
                            OutlinedButton(
                                onClick = viewModel::resetCamera,
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1.0f).height(48.dp).testTag("wp_reset_camera_button")
                            ) {
                                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Kamera", modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Reset", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (uiState.cameraMode == WorkpieceCameraMode.PAN) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.panBy(-28f, 0f) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("wp_pan_left_button")
                                ) { Text("← Kiri", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = { viewModel.panBy(0f, -22f) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("wp_pan_up_button")
                                ) { Text("↑ Atas", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = { viewModel.panBy(0f, 22f) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("wp_pan_down_button")
                                ) { Text("↓ Bawah", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = { viewModel.panBy(28f, 0f) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("wp_pan_right_button")
                                ) { Text("Kanan →", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }

            // 4. Parametric Dimension Data & Measurement Area Selector
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(selectedWorkpiece.name, style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
                                Text(
                                    "${selectedWorkpiece.shapeType.label} • ${selectedWorkpiece.material} • ${selectedWorkpiece.difficulty.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealSecondary
                                )
                            }
                            if (selectedWorkpiece.isCustom) {
                                IconButton(
                                    onClick = { viewModel.deleteCustomWorkpiece(selectedWorkpiece.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Benda Kustom", tint = ToleranceRed)
                                }
                            }
                        }
                        Text(selectedWorkpiece.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                        // Parametric Geometry Proof Pill (derived strictly from stored integer µm dimensions)
                        if (!uiState.isBlindMode) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NavyPrimary.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, CyanHighlight.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().testTag("wp_geometry_data_pill")
                            ) {
                                val dimSummary = buildString {
                                    append("Geometri Parametrik: ")
                                    append("Batas ${String.format(java.util.Locale.US, "%.2f", geometryProfile.boundingWidthMm).replace('.', ',')} × ")
                                    append("${String.format(java.util.Locale.US, "%.2f", geometryProfile.boundingHeightMm).replace('.', ',')} mm")
                                    if (geometryProfile.wallThicknessMm > 0f && selectedWorkpiece.shapeType in setOf(
                                            WorkpieceShapeType.RING,
                                            WorkpieceShapeType.BUSHING_RING,
                                            WorkpieceShapeType.PIPE
                                        )
                                    ) {
                                        append(" • Dinding ${String.format(java.util.Locale.US, "%.2f", geometryProfile.wallThicknessMm).replace('.', ',')} mm")
                                    }
                                }
                                Text(
                                    text = dimSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }

                        Text("Pilih Area / Zona Pengukuran:", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                    }
                }
            }

            items(selectedWorkpiece.zones, key = { "${selectedWorkpiece.id}_${it.zoneId}" }) { zone ->
                val isSelected = zone.zoneId == selectedZone.zoneId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) OrangeContainer else Color.White,
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) OrangeAccent else LabOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectZone(zone.zoneId) }
                        .testTag("wp_zone_${zone.zoneId}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(zone.label, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                            Text(
                                "${zone.kind.label} • Nominal ${MeasurementUnitConverter.formatUmAsMm(zone.nominalUm, 2, commaDecimal = true)} (+${MeasurementUnitConverter.formatUmAsMm(zone.upperTolUm, 2, commaDecimal = true)}/−${MeasurementUnitConverter.formatUmAsMm(zone.lowerTolUm, 2, commaDecimal = true)}) mm",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = if (uiState.isBlindMode) "BLIND" else "${MeasurementUnitConverter.formatUmAsMm(zone.actualUm, 2, commaDecimal = true)} mm",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (uiState.isBlindMode) OrangeAccent else NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 5. Direct Connection to All 4 Simulation Engines (with live ToolCompatibilityGuard status)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hubungkan ke Simulation Engine", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                        Text(
                            "Zona aktif (${selectedZone.label} • ${selectedZone.kind.label}) divalidasi otomatis terhadap batas fisik alat ukur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        val manualCalOk = ToolCompatibilityGuard.validate(InstrumentType.MANUAL_CALIPER, selectedZone.kind, selectedZone.actualUm) is ToolCompatibilityResult.Compatible
                        val digitalCalOk = ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_CALIPER, selectedZone.kind, selectedZone.actualUm) is ToolCompatibilityResult.Compatible
                        val manualMicOk = ToolCompatibilityGuard.validate(InstrumentType.MANUAL_MICROMETER, selectedZone.kind, selectedZone.actualUm) is ToolCompatibilityResult.Compatible
                        val digitalMicOk = ToolCompatibilityGuard.validate(InstrumentType.DIGITAL_MICROMETER, selectedZone.kind, selectedZone.actualUm) is ToolCompatibilityResult.Compatible

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SimulatorLaunchButton(
                                label = "J. Sorong Manual",
                                isCompatible = manualCalOk,
                                activeColor = TealSecondary,
                                onClick = { viewModel.validateAndLaunch(InstrumentType.MANUAL_CALIPER, onLaunchSimulator) },
                                modifier = Modifier.weight(1f).testTag("wp_open_manual_caliper")
                            )
                            SimulatorLaunchButton(
                                label = "J. Sorong Digital",
                                isCompatible = digitalCalOk,
                                activeColor = NavyPrimary,
                                onClick = { viewModel.validateAndLaunch(InstrumentType.DIGITAL_CALIPER, onLaunchSimulator) },
                                modifier = Modifier.weight(1f).testTag("wp_open_digital_caliper")
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SimulatorLaunchButton(
                                label = "Mikrometer Manual",
                                isCompatible = manualMicOk,
                                activeColor = OrangeAccent,
                                onClick = { viewModel.validateAndLaunch(InstrumentType.MANUAL_MICROMETER, onLaunchSimulator) },
                                modifier = Modifier.weight(1f).testTag("wp_open_manual_micrometer")
                            )
                            SimulatorLaunchButton(
                                label = "Mikrometer Digital",
                                isCompatible = digitalMicOk,
                                activeColor = Color(0xFF0A6780),
                                onClick = { viewModel.validateAndLaunch(InstrumentType.DIGITAL_MICROMETER, onLaunchSimulator) },
                                modifier = Modifier.weight(1f).testTag("wp_open_digital_micrometer")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CreateCustomWorkpieceDialog(
            onDismiss = { showCustomDialog = false },
            onSave = { name, shape, kind, actualUm, nominalUm, tolUm ->
                viewModel.addCustomWorkpiece(name, shape, kind, actualUm, nominalUm, tolUm)
                showCustomDialog = false
            }
        )
    }

    uiState.incompatibleAlert?.let { alert ->
        ToolCompatibilityAlertDialog(alert, viewModel::dismissIncompatibleAlert)
    }
}

@Composable
private fun SimulatorLaunchButton(
    label: String,
    isCompatible: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCompatible) activeColor else Color(0xFF64748B)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.height(50.dp)
    ) {
        Icon(
            imageVector = if (isCompatible) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (isCompatible) "Alat kompatibel" else "Cek kompatibilitas",
            tint = if (isCompatible) Color(0xFFD6F5E3) else Color(0xFFFDE68A),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun CreateCustomWorkpieceDialog(
    onDismiss: () -> Unit,
    onSave: (String, WorkpieceShapeType, MeasurementKind, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("Poros Latihan Kustom") }
    var shape by remember { mutableStateOf(WorkpieceShapeType.CYLINDER_SHAFT) }
    var kind by remember { mutableStateOf(MeasurementKind.OUTSIDE_DIAMETER) }
    var actualMmText by remember { mutableStateOf("18.45") }
    var nominalMmText by remember { mutableStateOf("18.50") }
    var tolMmText by remember { mutableStateOf("0.05") }

    val actualVal = actualMmText.trim().replace(',', '.').toDoubleOrNull()
    val nominalVal = nominalMmText.trim().replace(',', '.').toDoubleOrNull()
    val tolVal = tolMmText.trim().replace(',', '.').toDoubleOrNull()

    val isNameValid = name.trim().isNotEmpty()
    val isActualValid = actualVal != null && actualVal in 0.5..145.0
    val isNominalValid = nominalVal != null && nominalVal in 0.5..145.0
    val isTolValid = tolVal != null && tolVal in 0.01..5.0

    val validationError = when {
        !isNameValid -> "Nama benda kerja tidak boleh kosong."
        !isActualValid -> "Dimensi aktual harus berupa angka valid (0,50 – 145,00 mm)."
        !isNominalValid -> "Dimensi nominal harus berupa angka valid (0,50 – 145,00 mm)."
        !isTolValid -> "Toleransi ± harus berupa angka valid (0,01 – 5,00 mm)."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Benda Kerja Parametrik", color = NavyPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Benda Kerja") },
                    isError = !isNameValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_wp_name_input")
                )
                Text("Bentuk Geometri:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WorkpieceShapeType.entries) { st ->
                        FilterChip(
                            selected = shape == st,
                            onClick = { shape = st },
                            label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Jenis Dimensi:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MeasurementKind.entries) { mk ->
                        FilterChip(
                            selected = kind == mk,
                            onClick = { kind = mk },
                            label = { Text(mk.shortLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = actualMmText,
                        onValueChange = { actualMmText = it },
                        label = { Text("Aktual (mm)") },
                        isError = !isActualValid,
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("custom_wp_actual_input")
                    )
                    OutlinedTextField(
                        value = nominalMmText,
                        onValueChange = { nominalMmText = it },
                        label = { Text("Nominal (mm)") },
                        isError = !isNominalValid,
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("custom_wp_nominal_input")
                    )
                }
                OutlinedTextField(
                    value = tolMmText,
                    onValueChange = { tolMmText = it },
                    label = { Text("Toleransi ± (mm)") },
                    isError = !isTolValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_wp_tol_input")
                )
                if (validationError != null) {
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ToleranceRed,
                        modifier = Modifier.testTag("custom_wp_validation_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validationError == null && actualVal != null && nominalVal != null && tolVal != null) {
                        val actualUm = MeasurementUnitConverter.mmToUm(actualVal)
                        val nomUm = MeasurementUnitConverter.mmToUm(nominalVal)
                        val tolUm = MeasurementUnitConverter.mmToUm(tolVal)
                        onSave(name.trim(), shape, kind, actualUm, nomUm, tolUm)
                    }
                },
                enabled = validationError == null,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                modifier = Modifier.testTag("custom_wp_save_button")
            ) { Text("Simpan Benda") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
