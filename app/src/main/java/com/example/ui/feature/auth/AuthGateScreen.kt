package com.example.ui.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.GoogleAuthManager
import com.example.data.firebase.GoogleSignInOutcome
import com.example.data.local.preferences.UserPreferences
import com.example.di.AppContainer
import com.example.domain.auth.AuthValidator
import com.example.domain.auth.UserRole
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceRed
import com.example.ui.theme.ToleranceRedBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthGateScreen(
    container: AppContainer,
    initialNotice: String? = null,
    onAuthenticated: (UserRole) -> Unit,
    onOpenTeacherActivation: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs by container.preferencesStore.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )
    val scope = rememberCoroutineScope()

    var selectedRole by rememberSaveable { mutableStateOf(UserRole.STUDENT) }

    // Student fields (preserved 100%)
    var studentName by rememberSaveable { mutableStateOf(prefs.studentName) }
    var studentAttendance by rememberSaveable { mutableStateOf(prefs.studentAttendance) }
    var studentClass by rememberSaveable {
        mutableStateOf(
            if (prefs.userRole == UserRole.STUDENT && prefs.activeClassName.isNotBlank()) {
                prefs.activeClassName
            } else {
                AuthValidator.studentClassPresets.first()
            }
        )
    }

    var errorMessage by remember(initialNotice) { mutableStateOf(initialNotice) }
    var isSubmitting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LabBackground)
            .testTag("auth_gate_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Precision Blueprint Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(NavyDark, NavyPrimary, Color(0xFF0A5274))
                        )
                    )
                    .drawBehind {
                        val step = 28.dp.toPx()
                        val gridColor = Color.White.copy(alpha = 0.055f)
                        var x = 0f
                        while (x < size.width) {
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                            y += step
                        }
                    }
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 8.dp,
                        color = Color(0xFF0B3855),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_measure_lab_logo),
                            contentDescription = "Logo MEASURE LAB",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                        )
                    }
                    Text(
                        text = "MEASURE LAB",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pilih akses Murid atau Guru sebelum memasuki laboratorium alat ukur presisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = CyanHighlight.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "SMK Kelas X • Teknik Pemesinan • Lab Presisi",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanHighlight,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Centered Responsive Auth Card
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Segmented Role Switcher (Murid vs Guru)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, LabOutline),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RoleSwitchButton(
                            label = "Akses Murid",
                            subtitle = "Nama • Absen • Kelas",
                            icon = Icons.Default.Person,
                            selected = selectedRole == UserRole.STUDENT,
                            activeColor = TealSecondary,
                            onClick = {
                                selectedRole = UserRole.STUDENT
                                errorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_student")
                        )
                        RoleSwitchButton(
                            label = "Akses Guru",
                            subtitle = "Akun Teraktivasi",
                            icon = Icons.Default.School,
                            selected = selectedRole == UserRole.TEACHER,
                            activeColor = OrangeAccent,
                            onClick = {
                                selectedRole = UserRole.TEACHER
                                errorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_teacher")
                        )
                    }
                }

                // Error Banner
                errorMessage?.let { msg ->
                    Surface(
                        color = ToleranceRedBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ToleranceRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Kesalahan",
                                tint = ToleranceRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = ToleranceRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Form Content Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = selectedRole,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "auth_role_form_transition"
                    ) { role ->
                        if (role == UserRole.STUDENT) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Masuk Murid Praktikum",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lengkapi nama, nomor absensi, dan kelas agar seluruh hasil pengukuran serta nilai evaluasi tercatat rapi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                OutlinedTextField(
                                    value = studentName,
                                    onValueChange = {
                                        studentName = it
                                        errorMessage = null
                                    },
                                    label = { Text("Nama Lengkap Murid") },
                                    placeholder = { Text("Contoh: Ahmad Fauzan") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Person, contentDescription = "Nama", tint = TealSecondary)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_student_name_input")
                                )

                                OutlinedTextField(
                                    value = studentAttendance,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(2)
                                        studentAttendance = digits
                                        errorMessage = null
                                    },
                                    label = { Text("Nomor Absensi (1–60)") },
                                    placeholder = { Text("Contoh: 12") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Badge, contentDescription = "Absensi", tint = TealSecondary)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_student_attendance_input")
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Pilih Kelas",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NavyPrimary
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(vertical = 2.dp)
                                    ) {
                                        items(AuthValidator.studentClassPresets) { preset ->
                                            val selected = studentClass.equals(preset, ignoreCase = true)
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    studentClass = preset
                                                    errorMessage = null
                                                },
                                                label = { Text(preset) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = TealSecondary,
                                                    selectedLabelColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("auth_student_class_chip_${preset.replace(" ", "_")}")
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = studentClass,
                                        onValueChange = {
                                            studentClass = it
                                            errorMessage = null
                                        },
                                        label = { Text("Kelas") },
                                        placeholder = { Text("Contoh: X TP 1") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Class, contentDescription = "Kelas", tint = TealSecondary)
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters,
                                            imeAction = ImeAction.Done
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("auth_student_class_input")
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        if (isSubmitting) return@Button
                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            val result = container.preferencesStore.loginStudent(
                                                name = studentName,
                                                attendance = studentAttendance,
                                                className = studentClass
                                            )
                                            withContext(Dispatchers.Main.immediate) {
                                                isSubmitting = false
                                                result.onSuccess {
                                                    onAuthenticated(UserRole.STUDENT)
                                                }.onFailure { err ->
                                                    errorMessage = err.message ?: "Gagal masuk sebagai murid."
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("auth_student_submit_button")
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Masuk Murid")
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Masuk sebagai Murid",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Teacher Login via Firebase Auth (Google Sign-In) + Cloud Firestore Verification
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Login Guru Teraktivasi",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    color = NavyPrimary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Keamanan Guru",
                                            tint = NavyPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = "Guru yang sudah mengaktifkan akun cukup login menggunakan akun Google masing-masing tanpa memasukkan kode aktivasi lagi.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NavyPrimary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (isSubmitting) return@Button
                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            when (val outcome = GoogleAuthManager.signInWithGoogleInteractive(context)) {
                                                is GoogleSignInOutcome.Success -> {
                                                    val profileRes = container.teacherActivationRepository.fetchSignedInTeacherProfile()
                                                    profileRes.onSuccess { teacher ->
                                                        when {
                                                            teacher == null -> {
                                                                GoogleAuthManager.signOut(context)
                                                                withContext(Dispatchers.Main.immediate) {
                                                                    isSubmitting = false
                                                                    errorMessage = "Akun Google ini belum diaktivasi sebagai guru. Silakan klik 'Aktivasi Akun Guru' dan masukkan kode aktivasi dari Admin Utama."
                                                                }
                                                            }
                                                            !teacher.accountIsActive() -> {
                                                                GoogleAuthManager.signOut(context)
                                                                container.preferencesStore.logout()
                                                                withContext(Dispatchers.Main.immediate) {
                                                                    isSubmitting = false
                                                                    errorMessage = "Akun guru Anda telah dinonaktifkan oleh Admin Utama dan tidak dapat login atau menggunakan aplikasi."
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
                                                                    isSubmitting = false
                                                                    onAuthenticated(UserRole.TEACHER)
                                                                }
                                                            }
                                                        }
                                                    }.onFailure { err ->
                                                        GoogleAuthManager.signOut(context)
                                                        withContext(Dispatchers.Main.immediate) {
                                                            isSubmitting = false
                                                            errorMessage = err.message
                                                                ?: "Gagal memverifikasi akun guru di server."
                                                        }
                                                    }
                                                }
                                                GoogleSignInOutcome.Cancelled -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSubmitting = false
                                                    }
                                                }
                                                is GoogleSignInOutcome.Error -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSubmitting = false
                                                        errorMessage = outcome.message
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("auth_teacher_google_signin_button")
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = "Login Guru Google")
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Masuk Guru dengan Google",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                HorizontalDivider(color = LabOutline)

                                Surface(
                                    color = OrangeContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Guru Baru? Punya Kode Aktivasi?",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = NavyPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Aktifkan akun guru Anda sekali saja menggunakan kode unik (GURU-XXXX-XXXX) yang diberikan oleh Admin Utama sekolah.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NavyPrimary.copy(alpha = 0.85f)
                                        )
                                        OutlinedButton(
                                            onClick = onOpenTeacherActivation,
                                            enabled = !isSubmitting,
                                            border = BorderStroke(1.5.dp, OrangeAccent),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("auth_open_teacher_activation_button")
                                        ) {
                                            Icon(Icons.Default.VpnKey, contentDescription = "Aktivasi Guru", tint = OrangeAccent)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Aktivasi Akun Guru",
                                                color = OrangeAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Strictly Separated Primary Admin Portal Entry
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, LabOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isSubmitting, onClick = onOpenAdminDashboard)
                        .testTag("auth_open_admin_dashboard_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = NavyPrimary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Utama",
                                    tint = NavyPrimary,
                                    modifier = Modifier.padding(8.dp).size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Dashboard Admin Utama",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Halaman khusus pemilik untuk kode aktivasi & status guru",
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
}

@Composable
private fun RoleSwitchButton(
    label: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) activeColor else Color.Transparent,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) Color.White else NavyPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) Color.White else NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White.copy(alpha = 0.88f) else TextSecondary,
                maxLines = 1
            )
        }
    }
}
