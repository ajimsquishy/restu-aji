package com.example.ui.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.GoogleAuthManager
import com.example.di.AppContainer
import com.example.domain.auth.UserRole
import com.example.domain.model.InstrumentType
import com.example.domain.model.ToleranceStatus
import com.example.ui.theme.CyanHighlight
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpenManualCaliper: () -> Unit,
    onOpenDigitalCaliper: () -> Unit,
    onOpenManualMicrometer: () -> Unit,
    onOpenDigitalMicrometer: () -> Unit,
    onOpenWorkpieceLab: () -> Unit,
    onOpenLearningHub: () -> Unit,
    onOpenPracticeQuiz: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenErrorAnalysis: () -> Unit,
    onOpenToleranceLab: () -> Unit,
    onOpenEvaluationHub: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTeacherMode: () -> Unit,
    onStartTask: (InstrumentType, String, String, Long) -> Unit,
    onLogout: () -> Unit = {},
    onOpenAiTutor: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs by container.preferencesStore.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = com.example.data.local.preferences.UserPreferences()
    )
    val history by container.measurementHistoryRepository.allRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val attempts by container.evaluationRepository.allAttempts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeTasks by container.teacherTaskRepository.activeTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var showAccountDialog by remember { mutableStateOf(false) }

    val passCount = history.count { it.toleranceStatus == ToleranceStatus.IN_TOLERANCE }
    val avgScore = if (attempts.isEmpty()) 0 else attempts.map { it.scorePercent }.average().toInt()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LabBackground)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Industrial Hero Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(NavyDark, NavyPrimary, Color(0xFF0A5274))
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_measure_lab_logo),
                                contentDescription = "MEASURE LAB",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Column {
                                Text(
                                    text = "MEASURE LAB",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SMK X • Teknik Pemesinan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanHighlight
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (prefs.userRole == UserRole.TEACHER) OrangeAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable { showAccountDialog = true }
                                    .testTag("participant_code_chip")
                            ) {
                                Text(
                                    text = if (prefs.session.isAuthenticated) prefs.session.compactBadge else prefs.participantCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                )
                            }
                            if (prefs.userRole == UserRole.TEACHER) {
                                IconButton(
                                    onClick = onOpenTeacherMode,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(CircleShape)
                                        .background(OrangeAccent.copy(alpha = 0.22f))
                                        .testTag("dashboard_teacher_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = "Panel Guru",
                                        tint = OrangeAccent
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showAccountDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .testTag("dashboard_logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Ganti Akun atau Keluar",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Active Role Identity Strip (Murid or Guru)
                    if (prefs.session.isAuthenticated) {
                        Surface(
                            color = if (prefs.userRole == UserRole.TEACHER) OrangeAccent.copy(alpha = 0.18f) else CyanHighlight.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (prefs.userRole == UserRole.TEACHER) OrangeAccent.copy(alpha = 0.5f) else CyanHighlight.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_role_identity_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (prefs.userRole == UserRole.TEACHER) Icons.Default.School else Icons.Default.Person,
                                        contentDescription = prefs.userRole.label,
                                        tint = if (prefs.userRole == UserRole.TEACHER) OrangeAccent else CyanHighlight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (prefs.userRole == UserRole.TEACHER) {
                                                if (prefs.teacherFullName.isNotBlank()) {
                                                    "${prefs.teacherFullName} • ${prefs.activeClassName}"
                                                } else {
                                                    "Akses Guru Aktif • Mengajar ${prefs.activeClassName}"
                                                }
                                            } else {
                                                prefs.studentName
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (prefs.userRole == UserRole.TEACHER) {
                                                prefs.teacherEmail.ifBlank {
                                                    "Kelola tugas praktikum, toleransi & rekap hasil siswa"
                                                }
                                            } else {
                                                "Murid • No. Absensi ${prefs.studentAttendance} • Kelas ${prefs.activeClassName}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.86f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (prefs.userRole == UserRole.TEACHER) {
                                    Surface(
                                        color = OrangeAccent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clickable(onClick = onOpenTeacherMode)
                                            .testTag("dashboard_open_teacher_panel_cta")
                                    ) {
                                        Text(
                                            text = "Panel Guru",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "Laboratorium Virtual Alat Ukur Presisi",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "Simulasi mekanis presisi, benda kerja pemesinan, kalibrasi & analisis toleransi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    // Interactive Telemetry Stats Bar (each tile navigates to its real module)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroStatTile("Pengukuran", "${history.size}", onOpenHistory, Modifier.weight(1f).testTag("stat_tile_history"))
                        HeroStatTile("Sesuai Tol.", "$passCount", onOpenToleranceLab, Modifier.weight(1f).testTag("stat_tile_tolerance"))
                        HeroStatTile("Skor Kuis", "$avgScore%", onOpenEvaluationHub, Modifier.weight(1f).testTag("stat_tile_evaluation"))
                        HeroStatTile("Tugas Aktif", "${activeTasks.size}", onOpenTeacherMode, Modifier.weight(1f).testTag("stat_tile_tasks"))
                    }
                }
            }
        }

        // 2. Empat Simulator Utama (2x2 Grid)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("4 Simulator Alat Ukur Presisi", "Engine mekanis integer µm real-time")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SimulatorCard(
                        title = "Jangka Sorong\nManual",
                        subtitle = "0–150 mm • 0,05 / 0,02 mm",
                        icon = Icons.Default.Straighten,
                        accent = TealSecondary,
                        highlighted = false,
                        onClick = onOpenManualCaliper,
                        modifier = Modifier.weight(1f).testTag("menu_manual_caliper")
                    )
                    SimulatorCard(
                        title = "Jangka Sorong\nDigital",
                        subtitle = "LCD 0,01 mm • ZERO • HOLD",
                        icon = Icons.Default.Calculate,
                        accent = NavyPrimary,
                        highlighted = false,
                        onClick = onOpenDigitalCaliper,
                        modifier = Modifier.weight(1f).testTag("menu_digital_caliper")
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SimulatorCard(
                        title = "Mikrometer\nManual",
                        subtitle = "0–25 mm • Pitch 0,5 • 0,01 mm",
                        icon = Icons.Default.Tune,
                        accent = OrangeAccent,
                        highlighted = true,
                        onClick = onOpenManualMicrometer,
                        modifier = Modifier.weight(1f).testTag("menu_manual_micrometer")
                    )
                    SimulatorCard(
                        title = "Mikrometer\nDigital",
                        subtitle = "LCD 0,01 mm • Ratchet",
                        icon = Icons.Default.TrackChanges,
                        accent = Color(0xFF0A6780),
                        highlighted = false,
                        onClick = onOpenDigitalMicrometer,
                        modifier = Modifier.weight(1f).testTag("menu_digital_micrometer")
                    )
                }
            }
        }

        // 3. Pembelajaran, Latihan & Evaluasi (Dedicated 3-Card Row)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Materi, Latihan & Evaluasi", "Kurikulum SMK Kelas X Teknik Pemesinan")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        title = "Materi",
                        subtitle = "8 Topik",
                        icon = Icons.Default.MenuBook,
                        tint = TealSecondary,
                        onClick = onOpenLearningHub,
                        modifier = Modifier.weight(1f).testTag("menu_learning_hub")
                    )
                    QuickActionCard(
                        title = "Latihan",
                        subtitle = "Baca Skala",
                        icon = Icons.Default.Quiz,
                        tint = OrangeAccent,
                        onClick = onOpenPracticeQuiz,
                        modifier = Modifier.weight(1f).testTag("menu_practice_quiz")
                    )
                    QuickActionCard(
                        title = "Evaluasi",
                        subtitle = "Pre/Post/QC",
                        icon = Icons.Default.Verified,
                        tint = NavyPrimary,
                        onClick = onOpenEvaluationHub,
                        modifier = Modifier.weight(1f).testTag("menu_evaluation_hub")
                    )
                }
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAiTutor)
                        .testTag("menu_ai_tutor"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                    border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OrangeAccent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Asisten AI Alat Ukur",
                                tint = OrangeAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Asisten AI Alat Ukur",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tanya cara pakai, baca skala, kalibrasi & toleransi",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanHighlight,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Buka Asisten AI",
                            tint = OrangeAccent
                        )
                    }
                }
            }
        }

        // 4. Modul Laboratorium & Analisis (Complete 13-Module Access)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Modul Laboratorium & Analisis", "Semua fitur aktif 100% offline")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabModuleTile("Benda Kerja", "10 objek + 360°", Icons.Default.Category, TealSecondary, onOpenWorkpieceLab, Modifier.weight(1f).testTag("menu_workpiece_lab"))
                        LabModuleTile("Kalibrasi", "Zero Setting", Icons.Default.Verified, ToleranceGreen, onOpenCalibration, Modifier.weight(1f).testTag("menu_calibration"))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabModuleTile("Analisis Galat", "5 Fault + Rumus", Icons.Default.Analytics, OrangeAccent, onOpenErrorAnalysis, Modifier.weight(1f).testTag("menu_error_analysis"))
                        LabModuleTile("Toleransi", "PASS / REJECT", Icons.Default.Build, NavyPrimary, onOpenToleranceLab, Modifier.weight(1f).testTag("menu_tolerance_lab"))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabModuleTile("Riwayat Ukur", "${history.size} tersimpan", Icons.Default.History, NavyPrimary, onOpenHistory, Modifier.weight(1f).testTag("menu_history"))
                        LabModuleTile("Teacher Mode", "Tugas & Analitik", Icons.Default.Settings, Color(0xFF0A5C75), onOpenTeacherMode, Modifier.weight(1f).testTag("menu_teacher_mode"))
                    }
                }
            }
        }

        // 4. Tugas Aktif dari Guru (Teacher Mode Integration)
        if (activeTasks.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader("Tugas Praktikum Aktif", "Ditugaskan melalui Teacher Mode")
                }
            }
            items(activeTasks.take(3), key = { it.id }) { task ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable {
                            onStartTask(task.allowedInstrument, task.workpieceId, task.zoneId, task.id)
                        }
                        .testTag("active_task_card_${task.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (task.isBlindMeasurement) OrangeContainer else ToleranceGreenBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrackChanges,
                                contentDescription = task.title,
                                tint = if (task.isBlindMeasurement) OrangeAccent else ToleranceGreen
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = NavyPrimary
                            )
                            Text(
                                text = "${task.allowedInstrument.shortName} • Batas ${task.timeLimitSeconds / 60} mnt" +
                                    if (task.isBlindMeasurement) " • Blind Mode" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Mulai Tugas",
                            tint = TealSecondary
                        )
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = {
                Text(
                    text = if (prefs.userRole == UserRole.TEACHER) "Sesi Guru Aktif" else "Identitas Pengguna Aktif",
                    color = NavyPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prefs.userRole == UserRole.STUDENT) {
                        Text("Peran: Murid", style = MaterialTheme.typography.labelLarge, color = TealSecondary)
                        Text("Nama: ${prefs.studentName}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                        Text("No. Absensi: ${prefs.studentAttendance}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                        Text("Kelas: ${prefs.activeClassName}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                    } else if (prefs.userRole == UserRole.TEACHER) {
                        Text("Peran: Guru Teraktivasi", style = MaterialTheme.typography.labelLarge, color = OrangeAccent)
                        if (prefs.teacherFullName.isNotBlank()) {
                            Text("Nama: ${prefs.teacherFullName}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                        }
                        if (prefs.teacherEmail.isNotBlank()) {
                            Text("Email: ${prefs.teacherEmail}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Text("Kelas Diajar: ${prefs.activeClassName}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                    } else {
                        Text("Kode Peserta: ${prefs.participantCode}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary)
                    }
                    Text(
                        text = "Ingin keluar dan berganti akun Murid atau Guru?",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountDialog = false
                        scope.launch {
                            if (prefs.userRole == UserRole.TEACHER) {
                                GoogleAuthManager.signOut(context)
                            }
                            container.preferencesStore.logout()
                            withContext(Dispatchers.Main.immediate) {
                                onLogout()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Keluar / Ganti Akun")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun HeroStatTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CyanHighlight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(98.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LabOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = NavyPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = NavyPrimary)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun SimulatorCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(126.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) OrangeContainer else Color.White
        ),
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) OrangeAccent else LabOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LabModuleTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LabOutline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = NavyPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
