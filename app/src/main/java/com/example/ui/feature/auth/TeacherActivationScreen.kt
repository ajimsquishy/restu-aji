package com.example.ui.feature.auth

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.firebase.GoogleAuthManager
import com.example.data.firebase.GoogleSignInOutcome
import com.example.data.firebase.TeacherAccount
import com.example.di.AppContainer
import com.example.domain.auth.ActivationCodeGenerator
import com.example.domain.auth.AuthValidator
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
import com.example.ui.theme.ToleranceRedBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated Teacher Account Activation Screen ("AKTIVASI AKUN GURU").
 * Pairs Google Sign-In verification (Jetpack Credential Manager) with an atomic Firestore transaction
 * redeeming a single-use 7-day activation code issued by the Primary Admin.
 */
@Composable
fun TeacherActivationScreen(
    container: AppContainer,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialUser = remember { GoogleAuthManager.currentUser() }
    var verifiedEmail by rememberSaveable { mutableStateOf(initialUser?.email.orEmpty()) }
    var fullName by rememberSaveable { mutableStateOf(initialUser?.displayName.orEmpty()) }
    var teachingClass by rememberSaveable { mutableStateOf(AuthValidator.teacherClassPresets.first()) }
    var activationCode by rememberSaveable { mutableStateOf("") }

    var isSigningIn by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activatedAccount by remember { mutableStateOf<TeacherAccount?>(null) }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "AKTIVASI AKUN GURU",
                subtitle = "Verifikasi Akun Google & Kode Aktivasi Sekali Pakai",
                onBack = onBackToLogin
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
                .testTag("teacher_activation_screen"),
            contentAlignment = Alignment.TopCenter
        ) {
            val doneAccount = activatedAccount
            if (doneAccount != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, ToleranceGreen),
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .testTag("activation_success_card")
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = ToleranceGreenBg,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Aktivasi Berhasil",
                                tint = ToleranceGreen,
                                modifier = Modifier.padding(16.dp).size(48.dp)
                            )
                        }
                        Text(
                            text = "Aktivasi Berhasil!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Akun guru Anda telah aktif dan kode aktivasi otomatis ditandai sudah digunakan. Untuk login berikutnya, Anda tidak perlu memasukkan kode aktivasi lagi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            color = LabBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LabOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Nama: ${doneAccount.fullName}", style = MaterialTheme.typography.bodyMedium, color = NavyPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Email: ${doneAccount.email}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("Kelas: ${doneAccount.teachingClass}", style = MaterialTheme.typography.bodySmall, color = TealSecondary)
                                Text("Status: Aktif", style = MaterialTheme.typography.labelMedium, color = ToleranceGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = onBackToLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("activation_go_to_login_button")
                        ) {
                            Text(
                                text = "Lanjut ke Halaman Login Guru",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Banner
                    Surface(
                        color = NavyPrimary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "AKTIVASI AKUN GURU",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Masukkan kode aktivasi unik dari Admin Utama sekolah untuk mengaktifkan akun guru MEASURE LAB Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f)
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
                                .testTag("activation_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
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

                    // Step 1: Google Account Verification Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LabOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "1. Verifikasi Email Google Guru",
                                style = MaterialTheme.typography.titleSmall,
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (verifiedEmail.isNotBlank()) {
                                Surface(
                                    color = ToleranceGreenBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ToleranceGreen.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Email, contentDescription = "Email Terverifikasi", tint = ToleranceGreen)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Email Google Terverifikasi", style = MaterialTheme.typography.labelSmall, color = ToleranceGreen)
                                            Text(verifiedEmail, style = MaterialTheme.typography.bodyMedium, color = NavyPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (isSigningIn) return@OutlinedButton
                                        isSigningIn = true
                                        errorMessage = null
                                        scope.launch {
                                            GoogleAuthManager.signOut(context)
                                            when (val res = GoogleAuthManager.signInWithGoogleInteractive(context)) {
                                                is GoogleSignInOutcome.Success -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSigningIn = false
                                                        verifiedEmail = res.user.email.orEmpty()
                                                        if (fullName.isBlank()) {
                                                            fullName = res.user.displayName.orEmpty()
                                                        }
                                                    }
                                                }
                                                GoogleSignInOutcome.Cancelled -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSigningIn = false
                                                        verifiedEmail = ""
                                                    }
                                                }
                                                is GoogleSignInOutcome.Error -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSigningIn = false
                                                        verifiedEmail = ""
                                                        errorMessage = res.message
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isSigningIn && !isSubmitting,
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Text("Ganti Akun Google")
                                }
                            } else {
                                Text(
                                    text = "Pilih akun Google yang akan Anda gunakan untuk login guru secara permanen (tanpa password teks biasa).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = {
                                        if (isSigningIn) return@Button
                                        isSigningIn = true
                                        errorMessage = null
                                        scope.launch {
                                            when (val res = GoogleAuthManager.signInWithGoogleInteractive(context)) {
                                                is GoogleSignInOutcome.Success -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSigningIn = false
                                                        verifiedEmail = res.user.email.orEmpty()
                                                        if (fullName.isBlank()) {
                                                            fullName = res.user.displayName.orEmpty()
                                                        }
                                                    }
                                                }
                                                GoogleSignInOutcome.Cancelled -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSigningIn = false
                                                    }
                                                }
                                                is GoogleSignInOutcome.Error -> {
                                                    withContext(Dispatchers.Main.immediate) {
                                                        isSigningIn = false
                                                        errorMessage = res.message
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isSigningIn && !isSubmitting,
                                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("activation_google_signin_button")
                                ) {
                                    if (isSigningIn) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = "Verifikasi Google")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Pilih Akun Google Guru", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Step 2: Teacher Data & Activation Code Form
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LabOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "2. Data Guru & Kode Aktivasi",
                                style = MaterialTheme.typography.titleSmall,
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = fullName,
                                onValueChange = {
                                    if (it.length <= 100) {
                                        fullName = it
                                        errorMessage = null
                                    }
                                },
                                label = { Text("Nama Lengkap Guru") },
                                placeholder = { Text("Contoh: Budi Santoso, S.Pd") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Nama Guru", tint = NavyPrimary) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("activation_fullname_input")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Kelas yang Diajar",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NavyPrimary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 2.dp)
                                ) {
                                    items(AuthValidator.teacherClassPresets) { preset ->
                                        FilterChip(
                                            selected = teachingClass.equals(preset, ignoreCase = true),
                                            onClick = { teachingClass = preset; errorMessage = null },
                                            label = { Text(preset) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = OrangeAccent,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = teachingClass,
                                    onValueChange = {
                                        if (it.length <= 50) {
                                            teachingClass = it
                                            errorMessage = null
                                        }
                                    },
                                    label = { Text("Kelas yang Diajar") },
                                    leadingIcon = { Icon(Icons.Default.Class, contentDescription = "Kelas", tint = OrangeAccent) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("activation_class_input")
                                )
                            }

                            OutlinedTextField(
                                value = activationCode,
                                onValueChange = { raw ->
                                    activationCode = raw.uppercase().take(16)
                                    errorMessage = null
                                },
                                label = { Text("Kode Aktivasi dari Admin") },
                                placeholder = { Text("GURU-8K2P-9M4X") },
                                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = "Kode Aktivasi", tint = OrangeAccent) },
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("activation_code_input")
                            )

                            Surface(
                                color = OrangeContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Setiap kode aktivasi unik hanya berlaku 1 kali penggunaan dan kedaluwarsa otomatis setelah 7 hari.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyPrimary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Spacer(Modifier.height(2.dp))

                            Button(
                                onClick = {
                                    if (isSubmitting) return@Button
                                    val normalized = ActivationCodeGenerator.normalizeCode(activationCode)
                                    when {
                                        verifiedEmail.isBlank() -> {
                                            errorMessage = "Silakan pilih dan verifikasi akun Google guru terlebih dahulu."
                                            return@Button
                                        }
                                        fullName.trim().length < 2 -> {
                                            errorMessage = "Nama lengkap guru minimal 2 karakter."
                                            return@Button
                                        }
                                        teachingClass.trim().length < 2 -> {
                                            errorMessage = "Kelas yang diajar wajib diisi."
                                            return@Button
                                        }
                                        !ActivationCodeGenerator.isValidFormat(normalized) -> {
                                            errorMessage = "Format kode aktivasi tidak valid. Gunakan format GURU-XXXX-XXXX."
                                            return@Button
                                        }
                                    }
                                    isSubmitting = true
                                    errorMessage = null
                                    scope.launch {
                                        val result = container.teacherActivationRepository.activateTeacherAccount(
                                            fullName = fullName,
                                            teachingClass = teachingClass,
                                            rawActivationCode = normalized
                                        )
                                        withContext(Dispatchers.Main.immediate) {
                                            isSubmitting = false
                                            result.onSuccess { account ->
                                                activatedAccount = account
                                                errorMessage = null
                                            }.onFailure { err ->
                                                errorMessage = err.message
                                                    ?: "Aktivasi gagal. Periksa kembali kode aktivasi Anda."
                                            }
                                        }
                                    }
                                },
                                enabled = !isSubmitting && !isSigningIn,
                                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("activation_submit_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = "AKTIFKAN AKUN",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold
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
