package com.example.ui.feature.teacher

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.catalog.WorkpieceCatalog
import com.example.data.firebase.GoogleAuthManager
import com.example.data.firebase.GoogleSignInOutcome
import com.example.data.local.preferences.UserPreferences
import com.example.di.AppContainer
import com.example.domain.auth.AuthValidator
import com.example.domain.auth.UserRole
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.engine.ToolCompatibilityGuard
import com.example.domain.model.EvaluationCategory
import com.example.domain.model.InstrumentType
import com.example.domain.model.TeacherTask
import com.example.domain.model.ToleranceStatus
import com.example.domain.model.ToolCompatibilityResult
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
import com.example.ui.theme.ToleranceRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TeacherModeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLaunchTask: (InstrumentType, String, String, Long) -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs by container.preferencesStore.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )
    val tasks by container.teacherTaskRepository.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val records by container.measurementHistoryRepository.allRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val attempts by container.evaluationRepository.allAttempts.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    val isTeacherAuthenticated = prefs.userRole == UserRole.TEACHER
    var isSigningInTeacher by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var filterByTeacherClass by remember { mutableStateOf(false) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }

    val activeTeacherClass = prefs.activeClassName.ifBlank { "Semua Kelas X TP" }
    val teacherHeaderSubtitle = if (isTeacherAuthenticated) {
        val namePrefix = prefs.teacherFullName.takeIf { it.isNotBlank() }?.let { "$it • " } ?: ""
        "${namePrefix}Mengajar: $activeTeacherClass"
    } else {
        "Verifikasi Akun Guru Teraktivasi"
    }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Panel Guru",
                subtitle = teacherHeaderSubtitle,
                onBack = onBack,
                actions = {
                    if (isTeacherAuthenticated) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    GoogleAuthManager.signOut(context)
                                    container.preferencesStore.logout()
                                    withContext(Dispatchers.Main.immediate) {
                                        onLogout()
                                    }
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("teacher_logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Keluar Akun", tint = Color.White)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!isTeacherAuthenticated) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .testTag("teacher_pin_gate"),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(color = NavyPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = "Panel Guru",
                        tint = NavyPrimary,
                        modifier = Modifier.padding(18.dp).size(42.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Akses Khusus Akun Guru",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NavyPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Hanya guru yang sudah diaktivasi menggunakan kode dari Admin Utama yang dapat membuka Panel Guru untuk mengelola tugas dan analitik siswa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (authError != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ToleranceRed,
                        modifier = Modifier.testTag("teacher_gate_error")
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (isSigningInTeacher) return@Button
                        isSigningInTeacher = true
                        authError = null
                        scope.launch {
                            when (val outcome = GoogleAuthManager.signInWithGoogleInteractive(context)) {
                                is GoogleSignInOutcome.Success -> {
                                    val profileRes = container.teacherActivationRepository.fetchSignedInTeacherProfile()
                                    profileRes.onSuccess { teacher ->
                                        when {
                                            teacher == null -> {
                                                GoogleAuthManager.signOut(context)
                                                withContext(Dispatchers.Main.immediate) {
                                                    isSigningInTeacher = false
                                                    authError = "Akun Google ini belum diaktivasi sebagai guru. Keluar ke halaman login lalu pilih 'Aktivasi Akun Guru'."
                                                }
                                            }
                                            !teacher.accountIsActive() -> {
                                                GoogleAuthManager.signOut(context)
                                                withContext(Dispatchers.Main.immediate) {
                                                    isSigningInTeacher = false
                                                    authError = "Akun guru Anda sedang dinonaktifkan oleh Admin Utama."
                                                }
                                            }
                                            else -> {
                                                container.preferencesStore.saveFirebaseTeacherSession(
                                                    uid = teacher.uid,
                                                    fullName = teacher.fullName,
                                                    email = teacher.email,
                                                    teachingClass = teacher.teachingClass
                                                )
                                                withContext(Dispatchers.Main.immediate) {
                                                    isSigningInTeacher = false
                                                }
                                            }
                                        }
                                    }.onFailure { err ->
                                        GoogleAuthManager.signOut(context)
                                        withContext(Dispatchers.Main.immediate) {
                                            isSigningInTeacher = false
                                            authError = err.message ?: "Gagal memverifikasi akun guru."
                                        }
                                    }
                                }
                                GoogleSignInOutcome.Cancelled -> {
                                    withContext(Dispatchers.Main.immediate) {
                                        isSigningInTeacher = false
                                    }
                                }
                                is GoogleSignInOutcome.Error -> {
                                    withContext(Dispatchers.Main.immediate) {
                                        isSigningInTeacher = false
                                        authError = outcome.message
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSigningInTeacher,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("teacher_unlock_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Masuk Guru")
                    Spacer(Modifier.width(8.dp))
                    Text(if (isSigningInTeacher) "Memverifikasi..." else "Masuk Guru dengan Google")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            container.preferencesStore.logout()
                            withContext(Dispatchers.Main.immediate) {
                                onLogout()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Kembali ke Halaman Login / Aktivasi Guru")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("teacher_mode_content")
            ) {

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = NavyPrimary
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tugas (${tasks.size})") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Analitik Siswa") })
                }

                if (selectedTab == 0) {
                    LazyColumn(
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Button(
                                onClick = { showCreateTaskDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("teacher_add_task_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Buat Tugas")
                                Spacer(Modifier.width(6.dp))
                                Text("Buat Tugas Pengukuran Baru")
                            }
                        }
                        items(tasks, key = { it.id }) { task ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, LabOutline)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(task.title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = task.isActive,
                                            onCheckedChange = { active ->
                                                scope.launch { container.teacherTaskRepository.setActive(task.id, active) }
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ToleranceGreen)
                                        )
                                    }
                                    Text(task.instructions, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(
                                        "Alat: ${task.allowedInstrument.displayName} • Target: ${MeasurementUnitConverter.formatUmAsMm(task.nominalUm, 2)} ± ${MeasurementUnitConverter.formatUmAsMm(task.upperTolUm, 2)} mm • Waktu: ${task.timeLimitSeconds / 60} mnt" +
                                            if (task.isBlindMeasurement) " • Blind" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealSecondary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onLaunchTask(task.allowedInstrument, task.workpieceId, task.zoneId, task.id) },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Uji Tugas", modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Pratinjau")
                                        }
                                        IconButton(
                                            onClick = { scope.launch { container.teacherTaskRepository.deleteTask(task.id) } },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus Tugas", tint = ToleranceRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val canFilterSpecificClass = !activeTeacherClass.equals("Semua Kelas X TP", ignoreCase = true)
                    val filteredRecords = if (filterByTeacherClass && canFilterSpecificClass) {
                        records.filter { it.participantCode.contains(activeTeacherClass, ignoreCase = true) }
                    } else {
                        records
                    }
                    val filteredAttempts = if (filterByTeacherClass && canFilterSpecificClass) {
                        attempts.filter { it.participantCode.contains(activeTeacherClass, ignoreCase = true) }
                    } else {
                        attempts
                    }
                    val passCount = filteredRecords.count { it.toleranceStatus == ToleranceStatus.IN_TOLERANCE }
                    val passRate = if (filteredRecords.isEmpty()) 0 else (passCount * 100) / filteredRecords.size
                    val preAvg = filteredAttempts.filter { it.category == EvaluationCategory.PRE_TEST }.map { it.scorePercent }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
                    val postAvg = filteredAttempts.filter { it.category == EvaluationCategory.POST_TEST }.map { it.scorePercent }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
                    val byParticipant = filteredRecords.groupBy { it.participantCode }

                    LazyColumn(
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (canFilterSpecificClass) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !filterByTeacherClass,
                                        onClick = { filterByTeacherClass = false },
                                        label = { Text("Semua Kelas (${records.size})") }
                                    )
                                    FilterChip(
                                        selected = filterByTeacherClass,
                                        onClick = { filterByTeacherClass = true },
                                        label = { Text("Kelas $activeTeacherClass") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = OrangeAccent,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (filterByTeacherClass && canFilterSpecificClass) {
                                            "Rekap Kelas $activeTeacherClass"
                                        } else {
                                            "Ringkasan Analitik Siswa ($activeTeacherClass)"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        AnalyticCol("Total Ukur", "${filteredRecords.size}")
                                        AnalyticCol("Lulus Tol.", "$passRate%")
                                        AnalyticCol("Pre-Test", "$preAvg%")
                                        AnalyticCol("Post-Test", "$postAvg%")
                                    }
                                }
                            }
                        }
                        if (byParticipant.isEmpty()) {
                            item {
                                Text(
                                    text = "Belum ada data pengukuran siswa yang tersimpan untuk filter ini.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            items(byParticipant.entries.toList()) { (code, list) ->
                                val pPass = list.count { it.toleranceStatus == ToleranceStatus.IN_TOLERANCE }
                                val avgErr = list.map { kotlin.math.abs(it.errorUm) }.average().toInt()
                                val avgDur = list.map { it.durationSeconds }.average().toInt()
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, LabOutline)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(code, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                                            Text(
                                                "${list.size} pengukuran • Rata-rata galat ${MeasurementUnitConverter.formatUmAsMm(avgErr, 2)} mm • ${avgDur} dtk",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = if (pPass * 2 >= list.size) ToleranceGreenBg else OrangeContainer,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "$pPass/${list.size} PASS",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (pPass * 2 >= list.size) ToleranceGreen else OrangeAccent,
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
        }
    }

    if (showCreateTaskDialog) {
        CreateTeacherTaskDialog(
            onDismiss = { showCreateTaskDialog = false },
            onSave = { task ->
                scope.launch { container.teacherTaskRepository.saveTask(task) }
                showCreateTaskDialog = false
            }
        )
    }
}

@Composable
private fun AnalyticCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = OrangeAccent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun CreateTeacherTaskDialog(
    onDismiss: () -> Unit,
    onSave: (TeacherTask) -> Unit
) {
    var title by remember { mutableStateOf("Tugas Praktikum: Inspeksi Poros") }
    var instructions by remember { mutableStateOf("Ukur benda kerja sesuai prosedur, kunci alat, lalu simpan ke riwayat.") }
    var instrument by remember { mutableStateOf(InstrumentType.MANUAL_CALIPER) }
    var selectedWp by remember { mutableStateOf(WorkpieceCatalog.defaultWorkpieces.first()) }
    var selectedZoneId by remember { mutableStateOf(selectedWp.zones.first().zoneId) }
    var timeMinutes by remember { mutableStateOf("3") }
    var blind by remember { mutableStateOf(true) }

    val selectedZone = remember(selectedWp, selectedZoneId) {
        selectedWp.zones.firstOrNull { it.zoneId == selectedZoneId } ?: selectedWp.zones.first()
    }
    val compatibility = remember(instrument, selectedZone) {
        ToolCompatibilityGuard.validate(instrument, selectedZone.kind, selectedZone.actualUm)
    }
    val minutesVal = timeMinutes.trim().toIntOrNull()
    val isTitleValid = title.trim().isNotEmpty()
    val isInstructionsValid = instructions.trim().isNotEmpty()
    val isTimeValid = minutesVal != null && minutesVal in 1..60

    val validationError = when {
        !isTitleValid -> "Judul tugas tidak boleh kosong."
        !isInstructionsValid -> "Instruksi tugas tidak boleh kosong."
        !isTimeValid -> "Waktu harus berupa bilangan bulat 1 – 60 menit."
        compatibility is ToolCompatibilityResult.Incompatible -> "${compatibility.reason} ${compatibility.recommendation}"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Tugas Baru (Panel Guru)", color = NavyPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Tugas") },
                    isError = !isTitleValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("teacher_task_title_input")
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instruksi") },
                    isError = !isInstructionsValid,
                    modifier = Modifier.fillMaxWidth().testTag("teacher_task_instructions_input")
                )
                Text("Alat Ukur:", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(InstrumentType.entries) { inst ->
                        FilterChip(
                            selected = instrument == inst,
                            onClick = { instrument = inst },
                            label = { Text(inst.shortName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Benda Kerja:", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WorkpieceCatalog.defaultWorkpieces) { wp ->
                        FilterChip(
                            selected = selectedWp.id == wp.id,
                            onClick = {
                                selectedWp = wp
                                selectedZoneId = wp.zones.first().zoneId
                            },
                            label = { Text(wp.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Zona Pengukuran:", style = MaterialTheme.typography.labelMedium, color = NavyPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(selectedWp.zones) { z ->
                        FilterChip(
                            selected = selectedZone.zoneId == z.zoneId,
                            onClick = { selectedZoneId = z.zoneId },
                            label = { Text("${z.label} (${z.kind.shortLabel})", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = timeMinutes,
                        onValueChange = { timeMinutes = it },
                        label = { Text("Waktu (mnt)") },
                        isError = !isTimeValid,
                        singleLine = true,
                        modifier = Modifier.width(116.dp).testTag("teacher_task_time_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Blind Mode", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(6.dp))
                        Switch(checked = blind, onCheckedChange = { blind = it })
                    }
                }
                if (validationError != null) {
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ToleranceRed,
                        modifier = Modifier.testTag("teacher_task_validation_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validationError == null && minutesVal != null) {
                        onSave(
                            TeacherTask(
                                title = title.trim(),
                                instructions = instructions.trim(),
                                allowedInstrument = instrument,
                                workpieceId = selectedWp.id,
                                zoneId = selectedZone.zoneId,
                                nominalUm = selectedZone.nominalUm,
                                upperTolUm = selectedZone.upperTolUm,
                                lowerTolUm = selectedZone.lowerTolUm,
                                isBlindMeasurement = blind,
                                timeLimitSeconds = minutesVal * 60
                            )
                        )
                    }
                },
                enabled = validationError == null,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                modifier = Modifier.testTag("teacher_task_save_button")
            ) { Text("Simpan Tugas") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
