package com.example.ui.feature.calibration

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.DigitalCaliperEngine
import com.example.domain.engine.DigitalCaliperState
import com.example.domain.engine.DigitalMicrometerEngine
import com.example.domain.engine.DigitalMicrometerState
import com.example.domain.engine.ManualCaliperEngine
import com.example.domain.engine.ManualCaliperState
import com.example.domain.engine.ManualMicrometerEngine
import com.example.domain.engine.ManualMicrometerState
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.model.CaliperResolution
import com.example.domain.model.FaultConfig
import com.example.domain.model.InstrumentType
import com.example.ui.components.DigitalCaliperCanvas
import com.example.ui.components.DigitalMicrometerCanvas
import com.example.ui.components.ManualCaliperCanvas
import com.example.ui.components.ManualMicrometerCanvas
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceGreenBg
import kotlin.math.roundToInt

@Composable
fun CalibrationScreen(
    onBack: () -> Unit
) {
    var selectedInstrument by remember { mutableStateOf(InstrumentType.DIGITAL_CALIPER) }
    var simulatedZeroErrorUm by remember { mutableIntStateOf(40) } // +0.04 mm initial drift to calibrate
    var isCleaned by remember { mutableStateOf(false) }
    var isClosed by remember { mutableStateOf(false) }
    var isZeroed by remember { mutableStateOf(false) }

    val currentPosUm = if (isClosed) 0 else 15_000
    val zeroStepUm = if (selectedInstrument == InstrumentType.MANUAL_CALIPER) 20 else 10
    val effectiveZeroErrUm = MeasurementUnitConverter.roundToStep(simulatedZeroErrorUm, zeroStepUm)

    // Single Source of Truth: build actual engine state for the selected instrument
    val manualCaliperState = remember(currentPosUm, effectiveZeroErrUm, isZeroed) {
        val base = ManualCaliperState(
            jawPositionUm = currentPosUm,
            resolution = CaliperResolution.RES_0_02_MM,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = effectiveZeroErrUm)
        )
        if (isZeroed) ManualCaliperEngine.calibrateZero(base) else base
    }
    val digitalCaliperState = remember(currentPosUm, effectiveZeroErrUm, isZeroed) {
        val base = DigitalCaliperState(
            jawPositionUm = currentPosUm,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = effectiveZeroErrUm)
        )
        if (isZeroed) DigitalCaliperEngine.pressZero(base) else base
    }
    val manualMicrometerState = remember(currentPosUm, effectiveZeroErrUm, isZeroed) {
        val base = ManualMicrometerState(
            spindlePositionUm = currentPosUm,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = effectiveZeroErrUm)
        )
        if (isZeroed) ManualMicrometerEngine.calibrateZero(base) else base
    }
    val digitalMicrometerState = remember(currentPosUm, effectiveZeroErrUm, isZeroed) {
        val base = DigitalMicrometerState(
            spindlePositionUm = currentPosUm,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = effectiveZeroErrUm)
        )
        if (isZeroed) DigitalMicrometerEngine.pressZero(base) else base
    }

    // Live reading comes strictly from the active simulation engine (never detached arithmetic!)
    val engineReadingUm = when (selectedInstrument) {
        InstrumentType.MANUAL_CALIPER -> manualCaliperState.readingUm
        InstrumentType.DIGITAL_CALIPER -> digitalCaliperState.displayedUm ?: 0
        InstrumentType.MANUAL_MICROMETER -> manualMicrometerState.readingUm
        InstrumentType.DIGITAL_MICROMETER -> digitalMicrometerState.displayedUm ?: 0
    }
    val isCalibrationVerified = isCleaned && isClosed && isZeroed && engineReadingUm == 0

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Kalibrasi (Zero Setting)",
                subtitle = "Prosedur Pengecekan Nol & Koreksi Zero Error",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
                .testTag("calibration_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Instrument Selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InstrumentType.entries.forEach { inst ->
                    FilterChip(
                        selected = selectedInstrument == inst,
                        onClick = {
                            selectedInstrument = inst
                            isCleaned = false
                            isClosed = false
                            isZeroed = false
                        },
                        label = { Text(inst.shortName, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("cal_inst_${inst.name.lowercase()}")
                    )
                }
            }

            // Live Instrument Canvas reflecting exact calibration position & zero error
            when (selectedInstrument) {
                InstrumentType.MANUAL_CALIPER -> {
                    ManualCaliperCanvas(
                        state = manualCaliperState,
                        onDragDeltaUm = {},
                        showCallouts = false,
                        modifier = Modifier.fillMaxWidth().height(210.dp)
                    )
                }
                InstrumentType.DIGITAL_CALIPER -> {
                    DigitalCaliperCanvas(
                        state = digitalCaliperState,
                        onDragDeltaUm = {},
                        modifier = Modifier.fillMaxWidth().height(210.dp)
                    )
                }
                InstrumentType.MANUAL_MICROMETER -> {
                    ManualMicrometerCanvas(
                        state = manualMicrometerState,
                        onRotateThimbleDeltaUm = { _, _ -> },
                        showCallouts = false,
                        modifier = Modifier.fillMaxWidth().height(210.dp)
                    )
                }
                InstrumentType.DIGITAL_MICROMETER -> {
                    DigitalMicrometerCanvas(
                        state = digitalMicrometerState,
                        onRotateThimbleDeltaUm = { _, _ -> },
                        modifier = Modifier.fillMaxWidth().height(210.dp)
                    )
                }
            }

            // Zero Error Simulator Slider & Presets (+/-)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Simulasi Zero Error Awal: ${MeasurementUnitConverter.formatUmAsMm(effectiveZeroErrUm, 2, showPlusSign = true)} mm",
                        style = MaterialTheme.typography.titleSmall,
                        color = NavyPrimary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = effectiveZeroErrUm == -40,
                            onClick = { simulatedZeroErrorUm = -40; isZeroed = false },
                            label = { Text("−0,04 mm", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f).testTag("cal_preset_neg")
                        )
                        FilterChip(
                            selected = effectiveZeroErrUm == 0,
                            onClick = { simulatedZeroErrorUm = 0; isZeroed = false },
                            label = { Text("0,00 mm", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f).testTag("cal_preset_zero")
                        )
                        FilterChip(
                            selected = effectiveZeroErrUm == 40,
                            onClick = { simulatedZeroErrorUm = 40; isZeroed = false },
                            label = { Text("+0,04 mm", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f).testTag("cal_preset_pos")
                        )
                    }
                    Slider(
                        value = effectiveZeroErrUm.toFloat(),
                        onValueChange = {
                            simulatedZeroErrorUm = MeasurementUnitConverter.roundToStep(it.roundToInt(), zeroStepUm)
                            isZeroed = false
                        },
                        valueRange = -80f..80f,
                        colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = TealSecondary)
                    )
                    Text(
                        text = "Pembacaan Saat Ini: ${MeasurementUnitConverter.formatUmAsMm(engineReadingUm, 2)} mm",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCalibrationVerified) ToleranceGreen else NavyPrimary,
                        modifier = Modifier.testTag("calibration_current_reading")
                    )
                }
            }

            // Interactive 3-Step Calibration Checklist
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Langkah Kalibrasi Interaktif", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)

                    CalibrationStepButton(
                        stepNumber = 1,
                        title = "Bersihkan Permukaan Ukur",
                        subtitle = "Hilangkan debu/oli pada rahang atau anvil-spindle.",
                        done = isCleaned,
                        enabled = true,
                        icon = Icons.Default.CleaningServices,
                        onClick = { isCleaned = true },
                        tag = "cal_step_1_clean"
                    )
                    CalibrationStepButton(
                        stepNumber = 2,
                        title = if (selectedInstrument.name.contains("CALIPER")) "Tutup Rahang Sepenuhnya" else "Rapatkan Spindle dengan Ratchet",
                        subtitle = "Pastikan bidang ukur menyentuh tanpa tekanan berlebih.",
                        done = isClosed,
                        enabled = isCleaned,
                        icon = Icons.Default.Compress,
                        onClick = { isClosed = true },
                        tag = "cal_step_2_close"
                    )
                    CalibrationStepButton(
                        stepNumber = 3,
                        title = if (selectedInstrument.name.contains("DIGITAL")) "Tekan Tombol ZERO (0,00 mm)" else "Setel / Koreksi Zero Error ke 0,00 mm",
                        subtitle = "Menyamakan titik referensi pembacaan tepat pada nol.",
                        done = isZeroed,
                        enabled = isCleaned && isClosed,
                        icon = Icons.Default.Verified,
                        onClick = { isZeroed = true },
                        tag = "cal_step_3_zero"
                    )

                    if (isCalibrationVerified) {
                        Surface(
                            color = ToleranceGreenBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ToleranceGreen),
                            modifier = Modifier.fillMaxWidth().testTag("calibration_success_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Berhasil", tint = ToleranceGreen)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Kalibrasi Berhasil! Pembacaan 0,00 mm", style = MaterialTheme.typography.titleSmall, color = ToleranceGreen, fontWeight = FontWeight.Bold)
                                    Text("Alat ukur ${selectedInstrument.displayName} siap digunakan untuk pengukuran presisi.", style = MaterialTheme.typography.bodySmall, color = NavyPrimary)
                                }
                            }
                        }
                    } else if (!isCleaned) {
                        Surface(color = OrangeContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Mulai dari Langkah 1 agar tidak terjadi kesalahan permukaan kotor saat kalibrasi.", style = MaterialTheme.typography.bodySmall, color = NavyPrimary, modifier = Modifier.padding(10.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isCleaned = false
                            isClosed = false
                            isZeroed = false
                            simulatedZeroErrorUm = 40
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Ulangi")
                        Spacer(Modifier.width(6.dp))
                        Text("Ulangi Simulasi Kalibrasi")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationStepButton(
    stepNumber: Int,
    title: String,
    subtitle: String,
    done: Boolean,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    Button(
        onClick = onClick,
        enabled = enabled && !done,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (done) ToleranceGreen else TealSecondary,
            disabledContainerColor = if (done) ToleranceGreen else Color(0xFFD5E1E8),
            disabledContentColor = if (done) Color.White else TextSecondary
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (done) Icons.Default.CheckCircle else icon, contentDescription = title, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("$stepNumber. $title", style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = if (enabled || done) 0.9f else 0.7f))
            }
        }
    }
}
