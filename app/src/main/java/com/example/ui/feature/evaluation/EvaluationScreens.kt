package com.example.ui.feature.evaluation

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.catalog.LearningAndQuizCatalog
import com.example.di.AppContainer
import com.example.domain.engine.DigitalCaliperState
import com.example.domain.engine.DigitalMicrometerState
import com.example.domain.engine.ManualCaliperState
import com.example.domain.engine.ManualMicrometerState
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.model.EvaluationAttempt
import com.example.domain.model.EvaluationCategory
import com.example.domain.model.InstrumentType
import com.example.ui.components.DigitalCaliperCanvas
import com.example.ui.components.DigitalMicrometerCanvas
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.components.MicrometerSleeveLoupe
import com.example.ui.components.VernierMagnifierLoupe
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceGreenBg
import com.example.ui.theme.ToleranceRed
import com.example.ui.theme.ToleranceRedBg
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun EvaluationHubScreen(
    container: AppContainer,
    onBack: (() -> Unit)? = null,
    onStartQuiz: (EvaluationCategory) -> Unit,
    onOpenMachiningChallenge: () -> Unit
) {
    val attempts by container.evaluationRepository.allAttempts.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Latihan & Evaluasi Kompetensi",
                subtitle = "Baca Skala Dinamis, Machining Challenge, Pre/Post-Test",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("evaluation_hub_screen"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                EvaluationModeCard(
                    title = "Latihan Baca Skala (Precision Training)",
                    subtitle = "5 soal visual langsung dari engine skala nonius, sleeve/thimble, dan LCD digital.",
                    icon = Icons.Default.Straighten,
                    accent = TealSecondary,
                    onClick = { onStartQuiz(EvaluationCategory.SCALE_QUIZ) },
                    tag = "eval_start_scale_quiz"
                )
            }
            item {
                EvaluationModeCard(
                    title = "Machining Challenge (Gambar Kerja & Toleransi)",
                    subtitle = "Inspeksi benda kerja nyata berdasarkan gambar kerja & putuskan PASS atau REJECT.",
                    icon = Icons.Default.EmojiEvents,
                    accent = OrangeAccent,
                    onClick = onOpenMachiningChallenge,
                    tag = "eval_start_challenge"
                )
            }
            item {
                EvaluationModeCard(
                    title = "Pre-Test Penelitian Pendidikan",
                    subtitle = "Uji kemampuan awal siswa sebelum praktikum laboratorium virtual.",
                    icon = Icons.Default.Quiz,
                    accent = NavyPrimary,
                    onClick = { onStartQuiz(EvaluationCategory.PRE_TEST) },
                    tag = "eval_start_pretest"
                )
            }
            item {
                EvaluationModeCard(
                    title = "Post-Test Evaluasi Kompetensi",
                    subtitle = "Uji akhir mencakup kalibrasi zero error, galat relatif, dan pembacaan skala.",
                    icon = Icons.Default.AssignmentTurnedIn,
                    accent = ToleranceGreen,
                    onClick = { onStartQuiz(EvaluationCategory.POST_TEST) },
                    tag = "eval_start_posttest"
                )
            }

            if (attempts.isNotEmpty()) {
                item {
                    Text(
                        text = "Riwayat Nilai Evaluasi (${attempts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = NavyPrimary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(attempts.take(6), key = { it.id }) { attempt ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LabOutline)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(attempt.category.label, style = MaterialTheme.typography.titleSmall, color = NavyPrimary)
                                Text(
                                    "${attempt.participantCode} • Benar ${attempt.correctCount}/${attempt.totalCount} • ${attempt.durationSeconds} dtk",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Surface(
                                color = if (attempt.scorePercent >= 75) ToleranceGreenBg else OrangeContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${attempt.scorePercent}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (attempt.scorePercent >= 75) ToleranceGreen else OrangeAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LabOutline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.padding(10.dp).size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Mulai", tint = accent)
        }
    }
}

@Composable
fun QuizRunnerScreen(
    container: AppContainer,
    category: EvaluationCategory,
    onBack: () -> Unit
) {
    val questions = remember(category) { container.evaluationRepository.getQuestions(category) }
    val scope = rememberCoroutineScope()
    val startMillis = remember { System.currentTimeMillis() }

    var index by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableIntStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = category.label,
                subtitle = if (finished) "Hasil Evaluasi" else "Soal ${index + 1} dari ${questions.size}",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (finished || questions.isEmpty()) {
            val score = if (questions.isEmpty()) 0 else (correctCount * 100) / questions.size
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Selesai",
                    tint = OrangeAccent,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("Evaluasi Selesai!", style = MaterialTheme.typography.headlineMedium, color = NavyPrimary)
                Text(
                    text = "Skor Anda: $score% ($correctCount dari ${questions.size} benar)",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (score >= 75) ToleranceGreen else OrangeAccent,
                    modifier = Modifier.testTag("quiz_final_score")
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Hasil telah dicatat otomatis ke database lokal untuk pemantauan progres & penelitian.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Kembali ke Menu Evaluasi") }
            }
        } else {
            val q = questions[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
                    .testTag("quiz_runner_screen"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (index + 1).toFloat() / questions.size.toFloat() },
                    color = OrangeAccent,
                    trackColor = LabOutline,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )

                // Live dynamic scale visualization directly from simulation engine!
                if (q.instrumentType != null && q.simulatedPositionUm != null) {
                    when (q.instrumentType) {
                        InstrumentType.MANUAL_CALIPER -> {
                            VernierMagnifierLoupe(
                                state = ManualCaliperState(
                                    jawPositionUm = q.simulatedPositionUm,
                                    resolution = q.caliperResolution,
                                    workpieceContactUm = null
                                ),
                                showCoincidentHighlight = checked
                            )
                        }
                        InstrumentType.MANUAL_MICROMETER -> {
                            MicrometerSleeveLoupe(
                                state = ManualMicrometerState(
                                    spindlePositionUm = q.simulatedPositionUm,
                                    workpieceContactUm = null
                                ),
                                showReadingHighlight = checked
                            )
                        }
                        InstrumentType.DIGITAL_CALIPER -> {
                            DigitalCaliperCanvas(
                                state = DigitalCaliperState(
                                    jawPositionUm = q.simulatedPositionUm,
                                    workpieceContactUm = q.simulatedPositionUm
                                ),
                                onDragDeltaUm = {},
                                modifier = Modifier.fillMaxWidth().height(190.dp)
                            )
                        }
                        InstrumentType.DIGITAL_MICROMETER -> {
                            DigitalMicrometerCanvas(
                                state = DigitalMicrometerState(
                                    spindlePositionUm = q.simulatedPositionUm,
                                    workpieceContactUm = q.simulatedPositionUm
                                ),
                                onRotateThimbleDeltaUm = { _, _ -> },
                                modifier = Modifier.fillMaxWidth().height(190.dp)
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(q.prompt, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                        q.options.forEachIndexed { optIdx, optText ->
                            val isSelected = selectedOption == optIdx
                            val bg = when {
                                checked && optIdx == q.correctOptionIndex -> ToleranceGreenBg
                                checked && isSelected && optIdx != q.correctOptionIndex -> ToleranceRedBg
                                isSelected -> OrangeContainer
                                else -> Color(0xFFF7FAFC)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bg,
                                border = BorderStroke(1.dp, if (isSelected) OrangeAccent else LabOutline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !checked) { selectedOption = optIdx }
                                    .testTag("quiz_option_$optIdx")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = if (!checked) ({ selectedOption = optIdx }) else null,
                                        colors = RadioButtonDefaults.colors(selectedColor = OrangeAccent)
                                    )
                                    Text(optText, style = MaterialTheme.typography.bodyMedium, color = NavyDark)
                                }
                            }
                        }

                        if (checked) {
                            val isRight = selectedOption == q.correctOptionIndex
                            Surface(
                                color = if (isRight) ToleranceGreenBg else ToleranceRedBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isRight) "Jawaban Benar!" else "Jawaban Kurang Tepat",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isRight) ToleranceGreen else ToleranceRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = NavyDark)
                                }
                            }
                        }
                    }
                }

                if (!checked) {
                    Button(
                        onClick = {
                            checked = true
                            if (selectedOption == q.correctOptionIndex) correctCount++
                        },
                        enabled = selectedOption >= 0,
                        colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("quiz_check_button")
                    ) { Text("Periksa Jawaban") }
                } else {
                    Button(
                        onClick = {
                            if (index + 1 < questions.size) {
                                index++
                                selectedOption = -1
                                checked = false
                            } else {
                                finished = true
                                val score = (correctCount * 100) / questions.size
                                val dur = ((System.currentTimeMillis() - startMillis) / 1000L).toInt().coerceAtLeast(1)
                                scope.launch {
                                    val prefs = container.preferencesStore.preferencesFlow.first()
                                    container.evaluationRepository.saveAttempt(
                                        EvaluationAttempt(
                                            participantCode = prefs.participantCode,
                                            category = category,
                                            scorePercent = score,
                                            correctCount = correctCount,
                                            totalCount = questions.size,
                                            procedureErrors = questions.size - correctCount,
                                            durationSeconds = dur
                                        )
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("quiz_next_button")
                    ) { Text(if (index + 1 < questions.size) "Soal Selanjutnya" else "Selesai & Simpan Nilai") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MachiningChallengeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSimulatorForChallenge: (InstrumentType, String, String) -> Unit
) {
    val challenges = LearningAndQuizCatalog.machiningChallenges
    val scope = rememberCoroutineScope()
    val challengeStartMillis = remember { System.currentTimeMillis() }
    var decisions by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Machining Challenge",
                subtitle = "Inspeksi Gambar Kerja, Toleransi & Keputusan QC",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("machining_challenge_screen"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(challenges, key = { it.id }) { ch ->
                val userPass = decisions[ch.id]
                val upperLimit = ch.nominalUm + ch.upperTolUm
                val lowerLimit = ch.nominalUm - ch.lowerTolUm

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ch.title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary, modifier = Modifier.weight(1f))
                            Surface(color = NavyPrimary, shape = RoundedCornerShape(8.dp)) {
                                Text(ch.drawingCode, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Text(ch.engineeringNote, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Surface(color = Color(0xFFF3F8FB), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Nominal: ${MeasurementUnitConverter.formatUmAsMm(ch.nominalUm, 2)} mm  |  Batas: ${MeasurementUnitConverter.formatUmAsMm(lowerLimit, 2)} – ${MeasurementUnitConverter.formatUmAsMm(upperLimit, 2)} mm",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NavyDark
                                )
                                Text(
                                    "Alat Direkomendasikan: ${ch.recommendedInstrument.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealSecondary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onOpenSimulatorForChallenge(ch.recommendedInstrument, ch.workpieceId, ch.zoneId) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Ukur di Simulator", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buka Benda di Simulator untuk Mengukur")
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    decisions = decisions + (ch.id to true)
                                    val correct = ch.expectedDecisionPass
                                    val elapsedSec = ((System.currentTimeMillis() - challengeStartMillis) / 1000L).toInt().coerceAtLeast(1)
                                    scope.launch {
                                        val prefs = container.preferencesStore.preferencesFlow.first()
                                        container.evaluationRepository.saveAttempt(
                                            EvaluationAttempt(
                                                participantCode = prefs.participantCode,
                                                category = EvaluationCategory.MACHINING_CHALLENGE,
                                                scorePercent = if (correct) 100 else 0,
                                                correctCount = if (correct) 1 else 0,
                                                totalCount = 1,
                                                procedureErrors = if (correct) 0 else 1,
                                                durationSeconds = elapsedSec
                                            )
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ToleranceGreen),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("challenge_pass_${ch.id}")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "PASS", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PASS (Terima)")
                            }
                            Button(
                                onClick = {
                                    decisions = decisions + (ch.id to false)
                                    val correct = !ch.expectedDecisionPass
                                    val elapsedSec = ((System.currentTimeMillis() - challengeStartMillis) / 1000L).toInt().coerceAtLeast(1)
                                    scope.launch {
                                        val prefs = container.preferencesStore.preferencesFlow.first()
                                        container.evaluationRepository.saveAttempt(
                                            EvaluationAttempt(
                                                participantCode = prefs.participantCode,
                                                category = EvaluationCategory.MACHINING_CHALLENGE,
                                                scorePercent = if (correct) 100 else 0,
                                                correctCount = if (correct) 1 else 0,
                                                totalCount = 1,
                                                procedureErrors = if (correct) 0 else 1,
                                                durationSeconds = elapsedSec
                                            )
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ToleranceRed),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("challenge_reject_${ch.id}")
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "REJECT", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("REJECT (Tolak)")
                            }
                        }

                        if (userPass != null) {
                            val isRight = userPass == ch.expectedDecisionPass
                            Surface(
                                color = if (isRight) ToleranceGreenBg else ToleranceRedBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isRight) {
                                        "Keputusan QC Tepat! Dimensi aktual benda adalah ${MeasurementUnitConverter.formatUmAsMm(ch.actualUm, 2)} mm (${if (ch.expectedDecisionPass) "Dalam Toleransi" else "Di Luar Toleransi"})."
                                    } else {
                                        "Keputusan QC Kurang Tepat. Dimensi aktual benda adalah ${MeasurementUnitConverter.formatUmAsMm(ch.actualUm, 2)} mm — seharusnya ${if (ch.expectedDecisionPass) "PASS" else "REJECT"}."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NavyDark,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
