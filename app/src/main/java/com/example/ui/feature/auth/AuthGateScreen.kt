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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.preferences.UserPreferences
import com.example.di.AppContainer
import com.example.domain.auth.AuthValidator
import com.example.domain.auth.ClassTeacherAuth
import com.example.domain.auth.UserRole
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceRed
import com.example.ui.theme.ToleranceRedBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthGateScreen(
    container: AppContainer,
    initialNotice: String? = null,
    onAuthenticated: (UserRole) -> Unit
) {
    val prefs by container.preferencesStore.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = UserPreferences()
    )
    val scope = rememberCoroutineScope()

    var selectedRole by rememberSaveable { mutableStateOf(UserRole.STUDENT) }

    // Student fields
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

    // Teacher per-class password fields (no Google Sign-In required)
    var teacherClass by rememberSaveable {
        mutableStateOf(
            if (prefs.userRole == UserRole.TEACHER && ClassTeacherAuth.isSupportedClass(prefs.activeClassName)) {
                prefs.activeClassName
            } else {
                ClassTeacherAuth.supportedClasses.first()
            }
        )
    }
    var teacherPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

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
                            subtitle = "Kelas • Password",
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
                            // Offline Per-Class Teacher Password Login
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Masuk Guru Pengampu",
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
                                            contentDescription = "Autentikasi Kelas",
                                            tint = NavyPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = "Pilih kelas yang diajar lalu masukkan password autentikasi khusus kelas tersebut (100% offline, tanpa akun Google).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NavyPrimary
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Pilih Kelas yang Diajar",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NavyPrimary
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ClassTeacherAuth.supportedClasses.forEach { preset ->
                                            val selected = teacherClass.equals(preset, ignoreCase = true)
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    teacherClass = preset
                                                    errorMessage = null
                                                },
                                                label = { Text(preset) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = OrangeAccent,
                                                    selectedLabelColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("auth_teacher_class_chip_${preset.replace(" ", "_")}")
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = teacherPassword,
                                    onValueChange = {
                                        teacherPassword = it
                                        errorMessage = null
                                    },
                                    label = { Text("Password Kelas $teacherClass") },
                                    placeholder = { Text("Masukkan password kelas...") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = "Password", tint = OrangeAccent)
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { showPassword = !showPassword },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showPassword) "Sembunyikan Password" else "Tampilkan Password"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_teacher_password_input")
                                )

                                Spacer(Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        if (isSubmitting) return@Button
                                        isSubmitting = true
                                        errorMessage = null
                                        scope.launch {
                                            val result = container.preferencesStore.loginTeacher(
                                                teachingClass = teacherClass,
                                                password = teacherPassword
                                            )
                                            withContext(Dispatchers.Main.immediate) {
                                                isSubmitting = false
                                                result.onSuccess {
                                                    teacherPassword = ""
                                                    onAuthenticated(UserRole.TEACHER)
                                                }.onFailure { err ->
                                                    errorMessage = err.message
                                                        ?: "Password autentikasi kelas tidak sesuai."
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
                                        .testTag("auth_teacher_submit_button")
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Masuk Guru")
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Masuk sebagai Guru",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
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
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White.copy(alpha = 0.88f) else TextSecondary
                )
            }
        }
    }
}
