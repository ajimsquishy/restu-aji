package com.example.ui.feature.erroranalysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.ErrorAnalyzer
import com.example.domain.engine.FaultSimulationEngine
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.model.FaultConfig
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceRed
import kotlin.math.roundToInt

@Composable
fun ErrorAnalysisScreen(
    onBack: () -> Unit
) {
    // Seed table matching Mockup 1 reference values (24.86 vs 25.00 -> -0.14, 0.56%; 15.02 vs 15.00; 10.00 vs 10.00)
    val rows = remember {
        mutableStateListOf(
            24_860 to 25_000,
            15_020 to 15_000,
            10_000 to 10_000
        )
    }

    var measuredInput by remember { mutableStateOf("25.04") }
    var referenceInput by remember { mutableStateOf("25.00") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var faultConfig by remember { mutableStateOf(FaultConfig(zeroErrorUm = 20, tiltAngleDeg = 4)) }

    val baseTargetUm = 25_000
    val faultBiasUm = FaultSimulationEngine.computeFaultBiasUm(
        basePositionUm = baseTargetUm,
        faultConfig = faultConfig,
        hasWorkpieceContact = true,
        unlockedAfterRemoval = faultConfig.unlockDriftEnabled
    )
    val faultedAnalysis = ErrorAnalyzer.analyze(baseTargetUm + faultBiasUm, baseTargetUm)

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Analisis Kesalahan Pengukuran",
                subtitle = "Galat Absolut, Galat Relatif & Lab 5 Fault",
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
                .testTag("error_analysis_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Error Analysis Table (Mockup 1 inspired)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tabel Perbandingan Hasil Ukur vs Acuan", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyPrimary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("No", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.45f))
                        Text("Hasil (mm)", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.1f))
                        Text("Acuan", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("Galat", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("Relatif", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.95f))
                    }

                    rows.forEachIndexed { idx, (measured, ref) ->
                        val res = ErrorAnalyzer.analyze(measured, ref)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.45f))
                            Text(MeasurementUnitConverter.formatUmAsMm(res.measuredUm, 2), style = MaterialTheme.typography.titleSmall, color = NavyPrimary, modifier = Modifier.weight(1.1f))
                            Text(MeasurementUnitConverter.formatUmAsMm(res.referenceUm, 2), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                MeasurementUnitConverter.formatUmAsMm(res.absoluteErrorUm, 2, showPlusSign = true),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (res.absoluteErrorUm == 0) ToleranceGreen else ToleranceRed,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (res.isRelativeErrorUndefined) "Tak terd." else MeasurementUnitConverter.formatPercentTimes100(res.relativeErrorPercentTimes100),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (res.isRelativeErrorUndefined) ToleranceRed else NavyDark,
                                modifier = Modifier.weight(0.95f)
                            )
                        }
                        if (idx < rows.lastIndex) HorizontalDivider(color = LabOutline.copy(alpha = 0.6f))
                    }

                    // Formula Box
                    Surface(color = OrangeContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Rumus Galat: Galat = Hasil Ukur − Nilai Acuan", style = MaterialTheme.typography.labelMedium, color = NavyDark, fontWeight = FontWeight.Bold)
                            Text("Galat Relatif = (|Galat| / Nilai Acuan) × 100%", style = MaterialTheme.typography.labelMedium, color = NavyDark)
                        }
                    }

                    // Add new measurement row to analyze (strict validation — never masks invalid input!)
                    val parsedMeasured = measuredInput.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                    val parsedReference = referenceInput.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = measuredInput,
                            onValueChange = { measuredInput = it; inputError = null },
                            label = { Text("Hasil (mm)") },
                            isError = inputError != null && parsedMeasured == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("error_measured_input")
                        )
                        OutlinedTextField(
                            value = referenceInput,
                            onValueChange = { referenceInput = it; inputError = null },
                            label = { Text("Acuan (mm)") },
                            isError = inputError != null && parsedReference == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("error_reference_input")
                        )
                        Button(
                            onClick = {
                                when {
                                    parsedMeasured == null ->
                                        inputError = "Input Hasil Ukur tidak valid. Masukkan angka ≥ 0 mm (contoh: 25,04)."
                                    parsedReference == null ->
                                        inputError = "Input Nilai Acuan tidak valid. Nilai acuan harus angka > 0 mm agar galat relatif dapat dihitung."
                                    parsedMeasured > 1000.0 || parsedReference > 1000.0 ->
                                        inputError = "Nilai melebihi batas wajar bengkel (≤ 1000 mm)."
                                    else -> {
                                        inputError = null
                                        rows.add(
                                            MeasurementUnitConverter.mmToUm(parsedMeasured) to
                                                MeasurementUnitConverter.mmToUm(parsedReference)
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                            modifier = Modifier.height(52.dp).testTag("error_add_row_button")
                        ) { Text("Hitung") }
                    }
                    inputError?.let { errMsg ->
                        Surface(
                            color = ToleranceRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ToleranceRed),
                            modifier = Modifier.fillMaxWidth().testTag("error_input_validation_msg")
                        ) {
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = ToleranceRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // 2. Interactive 5-Fault Impact Laboratory
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Simulator Dampak 5 Kesalahan (Acuan Ø25,000 mm)", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                    Surface(color = NavyPrimary, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Hasil Terbaca", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.75f))
                                Text("${MeasurementUnitConverter.formatUmAsMm(faultedAnalysis.measuredUm, 3)} mm", style = MaterialTheme.typography.titleLarge, color = OrangeAccent)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Galat / Relatif", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.75f))
                                Text(
                                    "${MeasurementUnitConverter.formatUmAsMm(faultedAnalysis.absoluteErrorUm, 3, showPlusSign = true)} mm (${MeasurementUnitConverter.formatPercentTimes100(faultedAnalysis.relativeErrorPercentTimes100)})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Text("Zero Error: ${MeasurementUnitConverter.formatUmAsMm(faultConfig.zeroErrorUm, 2, showPlusSign = true)} mm", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                    Slider(
                        value = faultConfig.zeroErrorUm.toFloat(),
                        onValueChange = { faultConfig = faultConfig.copy(zeroErrorUm = (it / 10f).roundToInt() * 10) },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = TealSecondary)
                    )

                    Text("Posisi Miring (Cosine Error): ${faultConfig.tiltAngleDeg}°", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                    Slider(
                        value = faultConfig.tiltAngleDeg.toFloat(),
                        onValueChange = { faultConfig = faultConfig.copy(tiltAngleDeg = it.roundToInt()) },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = TealSecondary)
                    )

                    FaultSwitchItem("Tekanan Berlebih (−0,03 mm)", faultConfig.overPressureEnabled) {
                        faultConfig = faultConfig.copy(overPressureEnabled = it)
                    }
                    FaultSwitchItem("Permukaan Kotor (+0,04 mm)", faultConfig.dirtySurfaceEnabled) {
                        faultConfig = faultConfig.copy(dirtySurfaceEnabled = it)
                    }
                    FaultSwitchItem("Kesalahan Penguncian (+0,06 mm drift)", faultConfig.unlockDriftEnabled) {
                        faultConfig = faultConfig.copy(unlockDriftEnabled = it)
                    }
                }
            }
        }
    }
}

@Composable
private fun FaultSwitchItem(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NavyDark)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = OrangeAccent)
        )
    }
}
