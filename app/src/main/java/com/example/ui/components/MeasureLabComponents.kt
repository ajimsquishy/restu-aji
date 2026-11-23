package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.model.FaultConfig
import com.example.domain.model.LearningMode
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.MeasurementZone
import com.example.domain.model.ToleranceEvaluation
import com.example.domain.model.ToleranceStatus
import com.example.domain.model.ToolCompatibilityResult
import com.example.domain.model.Workpiece
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabOutline
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceGreenBg
import com.example.ui.theme.ToleranceOrangeBg
import com.example.ui.theme.ToleranceRed
import com.example.ui.theme.ToleranceRedBg
import kotlin.math.roundToInt

@Composable
fun MeasureLabTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        color = NavyPrimary,
        contentColor = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanHighlight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}

@Composable
fun MeasurementReadoutCard(
    readingText: String,
    secondaryUnitText: String? = null,
    formulaText: String? = null,
    isBlindMode: Boolean = false,
    isLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("measurement_readout_card"),
        shape = RoundedCornerShape(16.dp),
        color = NavyPrimary,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(NavyDark, NavyPrimary, Color(0xFF0A4D73))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBlindMode) "MODE BLIND (BACA SKALA LANGSUNG)" else "HASIL PENGUKURAN",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanHighlight
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Terkunci" else "Tidak Terkunci",
                        tint = if (isLocked) OrangeAccent else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isLocked) "LOCKED" else "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLocked) OrangeAccent else Color.White.copy(alpha = 0.65f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (isBlindMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Mode Blind",
                        tint = OrangeAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "??,?? mm",
                        style = MaterialTheme.typography.displayMedium,
                        color = OrangeAccent,
                        modifier = Modifier.testTag("readout_value_text")
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = readingText,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color(0xFFFFD166),
                        modifier = Modifier.testTag("readout_value_text")
                    )
                    if (!secondaryUnitText.isNullOrBlank()) {
                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = secondaryUnitText,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (!formulaText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formulaText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.testTag("readout_formula_text")
                    )
                }
            }
        }
    }
}

@Composable
fun ToleranceStatusCard(
    evaluation: ToleranceEvaluation,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (accentColor, bgColor, icon) = when (evaluation.status) {
        ToleranceStatus.IN_TOLERANCE -> Triple(ToleranceGreen, ToleranceGreenBg, Icons.Default.CheckCircle)
        ToleranceStatus.BORDERLINE_CHECK -> Triple(OrangeAccent, ToleranceOrangeBg, Icons.Default.Warning)
        ToleranceStatus.OUT_OF_TOLERANCE -> Triple(ToleranceRed, ToleranceRedBg, Icons.Default.Error)
    }

    Card(
        modifier = modifier.fillMaxWidth().testTag("tolerance_status_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analisis Toleransi",
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyPrimary
                )
                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = evaluation.status.label,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = evaluation.status.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("tolerance_status_label")
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricMiniCol("Nominal", "${MeasurementUnitConverter.formatUmAsMm(evaluation.spec.nominalUm, 2)} mm")
                MetricMiniCol("Batas Bawah", "${MeasurementUnitConverter.formatUmAsMm(evaluation.spec.lowerLimitUm, 2)} mm")
                MetricMiniCol("Batas Atas", "${MeasurementUnitConverter.formatUmAsMm(evaluation.spec.upperLimitUm, 2)} mm")
                MetricMiniCol(
                    "Selisih",
                    "${MeasurementUnitConverter.formatUmAsMm(evaluation.deviationUm, 3, showPlusSign = true)} mm",
                    valueColor = accentColor
                )
            }
            if (!compact) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = bgColor.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = evaluation.qualityAdvice,
                        style = MaterialTheme.typography.bodySmall,
                        color = NavyDark,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MeasurementModePillRow(
    kinds: List<MeasurementKind>,
    selectedKind: MeasurementKind,
    onSelect: (MeasurementKind) -> Unit,
    testTagPrefix: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        kinds.forEach { kind ->
            val selected = kind == selectedKind
            val weight = when (kind) {
                MeasurementKind.DEPTH -> 1.32f
                MeasurementKind.STEP -> 0.78f
                else -> 1f
            }
            Surface(
                color = if (selected) OrangeAccent else Color(0xFFF7FAFC),
                contentColor = if (selected) Color.White else NavyPrimary,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (selected) OrangeAccent else LabOutline),
                modifier = Modifier
                    .weight(weight)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(kind) }
                    .let { base ->
                        if (testTagPrefix != null) base.testTag("${testTagPrefix}_${kind.name.lowercase()}") else base
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = kind.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricMiniCol(label: String, value: String, valueColor: Color = NavyPrimary) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LearningModeSwitcher(
    selectedMode: LearningMode,
    onModeSelected: (LearningMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LearningMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            FilterChip(
                selected = selected,
                onClick = { onModeSelected(mode) },
                label = {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OrangeAccent,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = NavyPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = LabOutline,
                    selectedBorderColor = OrangeAccent
                ),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("learning_mode_${mode.name.lowercase()}")
            )
        }
    }
}

@Composable
fun StepGuideCard(
    title: String,
    steps: List<Pair<String, String>>,
    activeStepIndex: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LabOutline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyPrimary)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEachIndexed { idx, (heading, desc) ->
                    val isActive = idx == activeStepIndex
                    val isDone = idx < activeStepIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) OrangeContainer else Color.Transparent)
                            .padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isDone -> ToleranceGreen
                                        isActive -> OrangeAccent
                                        else -> LabSurfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDone || isActive) Color.White else NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = NavyPrimary
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaultSimulationBottomSheet(
    faultConfig: FaultConfig,
    onUpdate: (FaultConfig) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Fault Simulation (Simulasi Kesalahan)",
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Uji dampak kesalahan mekanis & prosedur terhadap hasil ukur",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                TextButton(
                    onClick = { onUpdate(FaultConfig()) },
                    modifier = Modifier.testTag("fault_reset_all_button")
                ) {
                    Text("Reset Fault")
                }
            }

            HorizontalDivider()

            // 1. Zero Error (+/-)
            Column {
                val sign = if (faultConfig.zeroErrorUm > 0) "+" else ""
                Text(
                    text = "1. Zero Error: $sign${MeasurementUnitConverter.formatUmAsMm(faultConfig.zeroErrorUm, 2)} mm",
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyPrimary
                )
                Slider(
                    value = faultConfig.zeroErrorUm.toFloat(),
                    onValueChange = {
                        val snapped = (it / 10f).roundToInt() * 10
                        onUpdate(faultConfig.copy(zeroErrorUm = snapped))
                    },
                    valueRange = -100f..100f,
                    colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = TealSecondary)
                )
            }

            // 2. Tekanan Berlebih
            FaultToggleRow(
                title = "2. Tekanan Berlebih (Over-Pressure)",
                subtitle = "Menekan rahang/thimble terlalu kuat menyebabkan kompresi elastis (-0,03 mm).",
                checked = faultConfig.overPressureEnabled,
                onCheckedChange = { onUpdate(faultConfig.copy(overPressureEnabled = it)) },
                tag = "fault_over_pressure_switch"
            )

            // 3. Posisi Miring
            Column {
                Text(
                    text = "3. Posisi Miring (Cosine Error): ${faultConfig.tiltAngleDeg}°",
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyPrimary
                )
                Slider(
                    value = faultConfig.tiltAngleDeg.toFloat(),
                    onValueChange = { onUpdate(faultConfig.copy(tiltAngleDeg = it.roundToInt())) },
                    valueRange = 0f..12f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = TealSecondary)
                )
            }

            // 4. Permukaan Kotor
            FaultToggleRow(
                title = "4. Permukaan Kotor (Gram / Debu +0,04 mm)",
                subtitle = "Serpihan geram menempel antara bidang ukur dan benda kerja.",
                checked = faultConfig.dirtySurfaceEnabled,
                onCheckedChange = { onUpdate(faultConfig.copy(dirtySurfaceEnabled = it)) },
                tag = "fault_dirty_surface_switch"
            )

            // 5. Kesalahan Penguncian
            FaultToggleRow(
                title = "5. Kesalahan Penguncian (Unlock Drift)",
                subtitle = "Menampilkan peringatan bila hasil dibaca/dilepas tanpa mengunci Lock.",
                checked = faultConfig.unlockDriftEnabled,
                onCheckedChange = { onUpdate(faultConfig.copy(unlockDriftEnabled = it)) },
                tag = "fault_unlock_drift_switch"
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Terapkan ke Simulator")
            }
        }
    }
}

@Composable
private fun FaultToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = OrangeAccent)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkpiecePickerBottomSheet(
    workpieces: List<Workpiece>,
    selectedWorkpieceId: String?,
    selectedZoneId: String?,
    isBlindMode: Boolean,
    onSelect: (Workpiece, MeasurementZone) -> Unit,
    onRemoveWorkpiece: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pilih Benda Kerja & Zona Ukur",
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Dimensi aktual terhubung langsung ke batas mekanis simulator",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = {
                        onRemoveWorkpiece()
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Lepas Benda")
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(workpieces, key = { it.id }) { wp ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (wp.id == selectedWorkpieceId) Color(0xFFEAF6FA) else Color(0xFFF8FBFD)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (wp.id == selectedWorkpieceId) TealSecondary else LabOutline
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = wp.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NavyPrimary
                                )
                                Surface(
                                    color = NavyPrimary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = wp.material,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            wp.zones.forEach { zone ->
                                val isCurrent = wp.id == selectedWorkpieceId && zone.zoneId == selectedZoneId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) OrangeContainer else Color.White)
                                        .border(
                                            1.dp,
                                            if (isCurrent) OrangeAccent else LabOutline,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            onSelect(wp, zone)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("pick_zone_${wp.id}_${zone.zoneId}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = zone.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = NavyPrimary
                                        )
                                        Text(
                                            text = zone.kind.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TealSecondary
                                        )
                                    }
                                    Text(
                                        text = if (isBlindMode) {
                                            "Nominal ${MeasurementUnitConverter.formatUmAsMm(zone.nominalUm, 2)} mm (Blind)"
                                        } else {
                                            "${MeasurementUnitConverter.formatUmAsMm(zone.actualUm, 2)} mm"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NavyDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolCompatibilityAlertDialog(
    incompatible: ToolCompatibilityResult.Incompatible,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alat Tidak Sesuai",
                tint = ToleranceRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = incompatible.title,
                style = MaterialTheme.typography.titleLarge,
                color = NavyPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = incompatible.reason, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = OrangeContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Rekomendasi: ${incompatible.recommendation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NavyDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Mengerti")
            }
        }
    )
}

@Composable
fun MetricEmptyState(
    icon: ImageVector = Icons.Default.Build,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LabOutline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(LabSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = TealSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = TealSecondary)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
