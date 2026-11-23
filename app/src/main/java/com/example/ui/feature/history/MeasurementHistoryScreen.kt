package com.example.ui.feature.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.di.AppContainer
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.model.InstrumentType
import com.example.domain.model.ToleranceStatus
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.MetricEmptyState
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceGreenBg
import com.example.ui.theme.ToleranceRed
import com.example.ui.theme.ToleranceRedBg
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementHistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenManualCaliper: () -> Unit
) {
    val allRecords by container.measurementHistoryRepository.allRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var filterInstrument by remember { mutableStateOf<InstrumentType?>(null) }

    val filtered = remember(allRecords, filterInstrument) {
        if (filterInstrument == null) allRecords else allRecords.filter { it.instrumentType == filterInstrument }
    }
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Riwayat Pengukuran",
                subtitle = "${allRecords.size} Data Tersimpan Offline (Room DB)",
                onBack = onBack,
                actions = {
                    if (allRecords.isNotEmpty()) {
                        IconButton(
                            onClick = { scope.launch { container.measurementHistoryRepository.clearAll() } },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("history_clear_all_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus Semua", tint = Color.White)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("measurement_history_screen"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Filter by Instrument
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = filterInstrument == null,
                            onClick = { filterInstrument = null },
                            label = { Text("Semua (${allRecords.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NavyPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(InstrumentType.entries) { inst ->
                        FilterChip(
                            selected = filterInstrument == inst,
                            onClick = { filterInstrument = inst },
                            label = { Text(inst.shortName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealSecondary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    MetricEmptyState(
                        icon = Icons.Default.History,
                        title = "Belum Ada Riwayat Pengukuran",
                        subtitle = "Lakukan pengukuran pada salah satu simulator dan tekan 'Simpan Hasil' untuk mencatat data offline.",
                        actionLabel = "Buka Simulator Jangka Sorong",
                        onAction = onOpenManualCaliper
                    )
                }
            } else {
                items(filtered, key = { it.id }) { rec ->
                    val (badgeColor, badgeBg) = when (rec.toleranceStatus) {
                        ToleranceStatus.IN_TOLERANCE -> ToleranceGreen to ToleranceGreenBg
                        ToleranceStatus.BORDERLINE_CHECK -> OrangeAccent to OrangeContainer
                        ToleranceStatus.OUT_OF_TOLERANCE -> ToleranceRed to ToleranceRedBg
                    }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LabOutline)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.workpieceName, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                                    Text(
                                        "${rec.instrumentType.displayName} • ${rec.zoneLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TealSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { scope.launch { container.measurementHistoryRepository.deleteRecord(rec.id) } },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = TextSecondary, modifier = Modifier.size(20.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${MeasurementUnitConverter.formatUmAsMm(rec.measuredUm, 2)} mm",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(color = badgeBg, shape = RoundedCornerShape(12.dp)) {
                                    Text(
                                        text = rec.toleranceStatus.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Acuan: ${MeasurementUnitConverter.formatUmAsMm(rec.referenceUm, 2)} mm | Galat: ${MeasurementUnitConverter.formatUmAsMm(rec.errorUm, 2, showPlusSign = true)} mm | ${rec.durationSeconds} dtk • ${rec.attemptCount}× coba",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Peserta: ${rec.participantCode} • ${dateFmt.format(Date(rec.timestampMillis))}" +
                                    if (rec.activeFaultsSummary.isNotBlank()) " • Fault: ${rec.activeFaultsSummary}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
