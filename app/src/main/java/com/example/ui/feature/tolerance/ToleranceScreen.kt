package com.example.ui.feature.tolerance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.engine.ToleranceAnalyzer
import com.example.domain.model.ToleranceSpec
import com.example.domain.model.ToleranceStatus
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.ToleranceStatusCard
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceRed
import com.example.ui.theme.ToleranceRedBg

@Composable
fun ToleranceScreen(
    onBack: () -> Unit
) {
    var nominalText by remember { mutableStateOf("12.00") }
    var upperTolText by remember { mutableStateOf("0.05") }
    var lowerTolText by remember { mutableStateOf("0.05") }
    var measuredText by remember { mutableStateOf("12.03") }

    val parsedNominal = nominalText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 && it <= 1000.0 }
    val parsedUpper = upperTolText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it <= 50.0 }
    val parsedLower = lowerTolText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it <= 50.0 }
    val parsedMeasured = measuredText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it <= 1000.0 }

    val validationError = when {
        parsedNominal == null -> "Ukuran nominal harus berupa angka > 0 mm (maks. 1000 mm)."
        parsedMeasured == null -> "Hasil pengukuran harus berupa angka ≥ 0 mm."
        parsedUpper == null -> "Toleransi atas harus berupa angka ≥ 0 mm (maks. 50 mm)."
        parsedLower == null -> "Toleransi bawah harus berupa angka ≥ 0 mm (maks. 50 mm)."
        else -> null
    }

    val nominalUm = parsedNominal?.let { MeasurementUnitConverter.mmToUm(it) }
    val upperUm = parsedUpper?.let { MeasurementUnitConverter.mmToUm(it) }
    val lowerUm = parsedLower?.let { MeasurementUnitConverter.mmToUm(it) }
    val measuredUm = parsedMeasured?.let { MeasurementUnitConverter.mmToUm(it) }

    val evaluation = if (nominalUm != null && upperUm != null && lowerUm != null && measuredUm != null) {
        ToleranceAnalyzer.evaluate(
            spec = ToleranceSpec(nominalUm, upperUm, lowerUm),
            measuredUm = measuredUm
        )
    } else {
        null
    }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Simulasi Toleransi Pemesinan",
                subtitle = "Batas Maks/Min, Deviasi & Status Kualitas",
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
                .testTag("tolerance_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick Presets from Mockups
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { nominalText = "12.00"; upperTolText = "0.05"; lowerTolText = "0.05"; measuredText = "12.02" },
                    label = { Text("Sesuai (Hijau)", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f).testTag("tol_preset_pass")
                )
                FilterChip(
                    selected = false,
                    onClick = { nominalText = "12.00"; upperTolText = "0.05"; lowerTolText = "0.05"; measuredText = "12.048" },
                    label = { Text("Batas Kritis", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f).testTag("tol_preset_border")
                )
                FilterChip(
                    selected = false,
                    onClick = { nominalText = "12.00"; upperTolText = "0.05"; lowerTolText = "0.05"; measuredText = "12.35" },
                    label = { Text("Tolak (Merah)", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f).testTag("tol_preset_fail")
                )
            }

            // Input Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Parameter Spesifikasi Gambar Kerja", style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nominalText,
                            onValueChange = { nominalText = it },
                            label = { Text("Nominal (mm)") },
                            isError = parsedNominal == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("tol_nominal_input")
                        )
                        OutlinedTextField(
                            value = measuredText,
                            onValueChange = { measuredText = it },
                            label = { Text("Hasil Ukur (mm)") },
                            isError = parsedMeasured == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("tol_measured_input")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = upperTolText,
                            onValueChange = { upperTolText = it },
                            label = { Text("Toleransi Atas (+mm)") },
                            isError = parsedUpper == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("tol_upper_input")
                        )
                        OutlinedTextField(
                            value = lowerTolText,
                            onValueChange = { lowerTolText = it },
                            label = { Text("Toleransi Bawah (−mm)") },
                            isError = parsedLower == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("tol_lower_input")
                        )
                    }
                }
            }

            if (validationError != null || evaluation == null || nominalUm == null || upperUm == null || lowerUm == null || measuredUm == null) {
                Surface(
                    color = ToleranceRedBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ToleranceRed),
                    modifier = Modifier.fillMaxWidth().testTag("tolerance_validation_error")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Input Spesifikasi Tidak Valid",
                            style = MaterialTheme.typography.titleSmall,
                            color = ToleranceRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = validationError ?: "Periksa kembali seluruh kolom angka spesifikasi toleransi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NavyPrimary
                        )
                    }
                }
            } else {
                // Visual Tolerance Band Gauge
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Visualisasi Zona Toleransi", style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                            val w = size.width
                            val h = size.height
                            val barY = h * 0.42f
                            val barH = 22f
                            val span = (upperUm + lowerUm).coerceAtLeast(20)
                            val minView = evaluation.spec.lowerLimitUm - span / 2
                            val maxView = evaluation.spec.upperLimitUm + span / 2
                            val totalSpan = (maxView - minView).coerceAtLeast(1)

                            fun umToX(um: Int): Float = ((um - minView).toFloat() / totalSpan.toFloat()).coerceIn(0.03f, 0.97f) * w

                            // Out-of-tolerance red background bar
                            drawRoundRect(ToleranceRed.copy(alpha = 0.22f), Offset(0f, barY), Size(w, barH), CornerRadius(10f, 10f))
                            // In-tolerance green zone
                            val lowX = umToX(evaluation.spec.lowerLimitUm)
                            val highX = umToX(evaluation.spec.upperLimitUm)
                            drawRoundRect(ToleranceGreen.copy(alpha = 0.72f), Offset(lowX, barY), Size((highX - lowX).coerceAtLeast(4f), barH), CornerRadius(6f, 6f))
                            // Nominal centerline
                            val nomX = umToX(nominalUm)
                            drawLine(NavyPrimary, Offset(nomX, barY - 10f), Offset(nomX, barY + barH + 10f), 3f)
                            // Measured pointer pin
                            val measX = umToX(measuredUm)
                            val pinColor = when (evaluation.status) {
                                ToleranceStatus.IN_TOLERANCE -> ToleranceGreen
                                ToleranceStatus.BORDERLINE_CHECK -> OrangeAccent
                                ToleranceStatus.OUT_OF_TOLERANCE -> ToleranceRed
                            }
                            drawLine(pinColor, Offset(measX, 6f), Offset(measX, barY + barH + 16f), 5f)
                            drawCircle(pinColor, 9f, Offset(measX, 10f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Min: ${MeasurementUnitConverter.formatUmAsMm(evaluation.spec.lowerLimitUm, 3)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Nom: ${MeasurementUnitConverter.formatUmAsMm(nominalUm, 3)}", style = MaterialTheme.typography.labelSmall, color = TealSecondary)
                            Text("Maks: ${MeasurementUnitConverter.formatUmAsMm(evaluation.spec.upperLimitUm, 3)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }

                // Full Tolerance Evaluation Status Card
                ToleranceStatusCard(evaluation = evaluation)
            }
        }
    }
}
