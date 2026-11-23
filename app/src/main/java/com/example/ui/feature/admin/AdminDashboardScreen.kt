package com.example.ui.feature.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.ActivationCodeRecord
import com.example.data.firebase.AdminProfile
import com.example.data.firebase.EffectiveCodeStatus
import com.example.data.firebase.GeneratedActivationCode
import com.example.data.firebase.GoogleAuthManager
import com.example.data.firebase.GoogleSignInOutcome
import com.example.data.firebase.TeacherAccount
import com.example.data.firebase.TeacherActivationRepository
import com.example.di.AppContainer
import com.example.ui.components.MeasureLabTopBar
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
import com.example.ui.theme.ToleranceRed
import com.example.ui.theme.ToleranceRedBg
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Primary Admin Dashboard route, completely separated from Teacher Login.
 * Strictly verifies Google Sign-In against /admins/{uid} and owner rules before mounting any Firestore listeners.
 */
@Composable
fun AdminDashboardScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var verifiedAdmin by remember { mutableStateOf<AdminProfile?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    // If an already-signed-in Firebase user opens Admin screen, verify immediately;
    // non-admin teachers are rejected cleanly and NEVER mount admin listeners.
    LaunchedEffect(Unit) {
        val current = GoogleAuthManager.currentUser()
        if (current != null && GoogleAuthManager.isPrimaryAdminEmail(current)) {
            isAuthenticating = true
            val res = container.teacherActivationRepository.verifyAndBootstrapAdmin()
            isAuthenticating = false
            res.onSuccess { admin ->
                verifiedAdmin = admin
                authError = null
            }.onFailure { err ->
                verifiedAdmin = null
                authError = err.message ?: "Akses Admin Utama ditolak."
            }
        }
    }

    val admin = verifiedAdmin
    if (admin == null) {
        AdminLoginGate(
            isAuthenticating = isAuthenticating,
            authError = authError,
            onBack = onBack,
            onSignInClicked = {
                if (isAuthenticating) return@AdminLoginGate
                isAuthenticating = true
                authError = null
                scope.launch {
                    when (val outcome = GoogleAuthManager.signInWithGoogleInteractive(context)) {
                        is GoogleSignInOutcome.Success -> {
                            val verifyResult = container.teacherActivationRepository.verifyAndBootstrapAdmin()
                            withContext(Dispatchers.Main.immediate) {
                                isAuthenticating = false
                                verifyResult.onSuccess { profile ->
                                    verifiedAdmin = profile
                                    authError = null
                                }.onFailure { err ->
                                    verifiedAdmin = null
                                    authError = err.message
                                        ?: "Akses Ditolak: Hanya Admin Utama yang berhak masuk ke dashboard ini."
                                    // Sign out non-admin immediately so they don't stay signed in as admin
                                    scope.launch { GoogleAuthManager.signOut(context) }
                                }
                            }
                        }
                        GoogleSignInOutcome.Cancelled -> {
                            withContext(Dispatchers.Main.immediate) {
                                isAuthenticating = false
                            }
                        }
                        is GoogleSignInOutcome.Error -> {
                            withContext(Dispatchers.Main.immediate) {
                                isAuthenticating = false
                                authError = outcome.message
                            }
                        }
                    }
                }
            }
        )
    } else {
        // Key composition & ViewModel by verified Admin UID so sign-out tears down listeners cleanly
        key(admin.uid) {
            val viewModel: AdminDashboardViewModel = viewModel(
                key = "admin_${admin.uid}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        AdminDashboardViewModel(
                            repository = TeacherActivationRepository(db),
                            adminUid = admin.uid
                        )
                    }
                }
            )
            AdminDashboardAuthenticatedContent(
                admin = admin,
                viewModel = viewModel,
                onSignOut = {
                    scope.launch {
                        GoogleAuthManager.signOut(context)
                        withContext(Dispatchers.Main.immediate) {
                            verifiedAdmin = null
                            onBack()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AdminLoginGate(
    isAuthenticating: Boolean,
    authError: String?,
    onBack: () -> Unit,
    onSignInClicked: () -> Unit
) {
    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Admin Utama",
                subtitle = "Akses Terpisah Pemilik Aplikasi MEASURE LAB",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("admin_login_gate"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = NavyPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Utama",
                            tint = NavyPrimary,
                            modifier = Modifier
                                .padding(16.dp)
                                .size(44.dp)
                        )
                    }

                    Text(
                        text = "DASHBOARD ADMIN UTAMA",
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Halaman khusus pengelola aplikasi untuk membuat kode aktivasi guru (sekali pakai, 7 hari), memantau guru terdaftar, dan menonaktifkan akun guru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = OrangeContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Keamanan Admin",
                                tint = OrangeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Verifikasi ketat via Firebase Auth (Google Sign-In) & Cloud Firestore Security Rules. Akun guru atau pengguna biasa tidak dapat mendaftar atau mengakses halaman ini.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyPrimary
                            )
                        }
                    }

                    if (authError != null) {
                        Surface(
                            color = ToleranceRedBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ToleranceRed.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_access_denied_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Ditolak",
                                    tint = ToleranceRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = authError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ToleranceRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onSignInClicked,
                        enabled = !isAuthenticating,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("admin_google_signin_button")
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Login Admin")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Masuk Admin Utama dengan Google",
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

@Composable
private fun AdminDashboardAuthenticatedContent(
    admin: AdminProfile,
    viewModel: AdminDashboardViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val codesState by viewModel.codesState.collectAsStateWithLifecycle()
    val teachersState by viewModel.teachersState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val lastGenerated by viewModel.lastGeneratedCode.collectAsStateWithLifecycle()
    val feedbackMsg by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMessage.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var codeNote by rememberSaveable { mutableStateOf("") }
    var codeFilter by rememberSaveable { mutableStateOf("ALL") }
    var teacherFilter by rememberSaveable { mutableStateOf("ALL") }
    var copiedBanner by remember { mutableStateOf(false) }

    var pendingToggleTeacher by remember { mutableStateOf<TeacherAccount?>(null) }
    var pendingRevokeCode by remember { mutableStateOf<ActivationCodeRecord?>(null) }
    var pendingDeleteCode by remember { mutableStateOf<ActivationCodeRecord?>(null) }

    val allCodes = (codesState as? AdminListState.Success)?.items ?: emptyList()
    val allTeachers = (teachersState as? AdminListState.Success)?.items ?: emptyList()
    val teacherByUid = remember(allTeachers) { allTeachers.associateBy { it.uid } }

    val activeCodesCount = allCodes.count { it.effectiveStatus() == EffectiveCodeStatus.ACTIVE }
    val usedCodesCount = allCodes.count { it.effectiveStatus() == EffectiveCodeStatus.USED }
    val activeTeachersCount = allTeachers.count { it.accountIsActive() }
    val disabledTeachersCount = allTeachers.count { !it.accountIsActive() }

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Dashboard Admin Utama",
                subtitle = admin.email,
                onBack = null,
                actions = {
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Keluar Admin",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("admin_dashboard_content"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // KPI Summary Strip
            Surface(
                color = NavyDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminStatTile("Kode Aktif", "$activeCodesCount", CyanHighlight, Modifier.weight(1f))
                    AdminStatTile("Terpakai", "$usedCodesCount", OrangeAccent, Modifier.weight(1f))
                    AdminStatTile("Guru Aktif", "$activeTeachersCount", ToleranceGreen, Modifier.weight(1f))
                    AdminStatTile("Nonaktif", "$disabledTeachersCount", ToleranceRed, Modifier.weight(1f))
                }
            }

            // Tab Navigation (Generate Kode | Daftar Kode | Daftar Guru)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = NavyPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; viewModel.clearBanner() },
                    text = { Text("Generate Kode", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("admin_tab_generate")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; viewModel.clearBanner() },
                    text = { Text("Kode (${allCodes.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("admin_tab_codes")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; viewModel.clearBanner() },
                    text = { Text("Guru (${allTeachers.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("admin_tab_teachers")
                )
            }

            // Feedback / Error Banners
            feedbackMsg?.let { msg ->
                Surface(
                    color = ToleranceGreenBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_feedback_banner")
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = ToleranceGreen,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
            errorMsg?.let { msg ->
                Surface(
                    color = ToleranceRedBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_error_banner")
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = ToleranceRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    0 -> GenerateCodeTab(
                        note = codeNote,
                        onNoteChange = { codeNote = it },
                        isBusy = isBusy,
                        lastGenerated = lastGenerated,
                        copiedBanner = copiedBanner,
                        onGenerate = {
                            copiedBanner = false
                            viewModel.generateCode(codeNote)
                            codeNote = ""
                        },
                        onCopyCode = { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Kode Aktivasi Guru MEASURE LAB", code))
                            copiedBanner = true
                        }
                    )

                    1 -> ActivationCodesListTab(
                        state = codesState,
                        filter = codeFilter,
                        onFilterChange = { codeFilter = it },
                        teacherByUid = teacherByUid,
                        isBusy = isBusy,
                        onRevoke = { pendingRevokeCode = it },
                        onDelete = { pendingDeleteCode = it }
                    )

                    2 -> RegisteredTeachersTab(
                        state = teachersState,
                        filter = teacherFilter,
                        onFilterChange = { teacherFilter = it },
                        isBusy = isBusy,
                        onToggleStatus = { pendingToggleTeacher = it }
                    )
                }
            }
        }
    }

    // Confirmation Dialog: Disable / Reactivate Teacher Account
    pendingToggleTeacher?.let { teacher ->
        val nextActive = !teacher.accountIsActive()
        AlertDialog(
            onDismissRequest = { pendingToggleTeacher = null },
            title = {
                Text(
                    text = if (nextActive) "Aktifkan Kembali Akun Guru?" else "Nonaktifkan Akun Guru?",
                    color = NavyPrimary
                )
            },
            text = {
                Text(
                    text = if (nextActive) {
                        "Guru ${teacher.fullName} (${teacher.email}) akan dapat kembali login dan menggunakan fitur Panel Guru MEASURE LAB."
                    } else {
                        "Guru ${teacher.fullName} (${teacher.email}) akan langsung diblokir dan tidak dapat login atau menggunakan fitur Guru sampai Anda mengaktifkannya kembali."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setTeacherActive(teacher, nextActive)
                        pendingToggleTeacher = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (nextActive) ToleranceGreen else ToleranceRed
                    )
                ) {
                    Text(if (nextActive) "Aktifkan" else "Nonaktifkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingToggleTeacher = null }) { Text("Batal") }
            }
        )
    }

    // Confirmation Dialog: Revoke Unused Code
    pendingRevokeCode?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingRevokeCode = null },
            title = { Text("Cabut Kode Aktivasi?", color = NavyPrimary) },
            text = {
                Text("Kode ${record.codeHint} akan ditandai sebagai Dicabut dan tidak dapat digunakan untuk aktivasi guru.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.revokeCode(record.codeHash)
                        pendingRevokeCode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
                ) { Text("Cabut Kode") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevokeCode = null }) { Text("Batal") }
            }
        )
    }

    // Confirmation Dialog: Delete Unused Code
    pendingDeleteCode?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCode = null },
            title = { Text("Hapus Kode Belum Terpakai?", color = NavyPrimary) },
            text = {
                Text("Kode ${record.codeHint} akan dihapus permanen dari daftar kode aktivasi.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUnusedCode(record.codeHash)
                        pendingDeleteCode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ToleranceRed)
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCode = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun GenerateCodeTab(
    note: String,
    onNoteChange: (String) -> Unit,
    isBusy: Boolean,
    lastGenerated: GeneratedActivationCode?,
    copiedBanner: Boolean,
    onGenerate: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LabOutline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = OrangeContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Generate Kode",
                                tint = OrangeAccent,
                                modifier = Modifier.padding(10.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Generate Kode Aktivasi Guru",
                                style = MaterialTheme.typography.titleMedium,
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Format GURU-XXXX-XXXX • Unik • Sekali Pakai • Berlaku 7 Hari",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= 100) onNoteChange(it) },
                        label = { Text("Catatan Penerima (Opsional)") },
                        placeholder = { Text("Contoh: Untuk Pak Budi - Guru X TP 1") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_code_note_input")
                    )

                    Button(
                        onClick = onGenerate,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("admin_generate_code_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(Icons.Default.Key, contentDescription = "Buat Kode")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Generate Kode Aktivasi Baru",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (lastGenerated != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPrimary),
                    border = BorderStroke(1.5.dp, OrangeAccent),
                    modifier = Modifier.testTag("admin_generated_code_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "KODE AKTIVASI BARU (TAMPIL SEKALI)",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanHighlight,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lastGenerated.plaintextCode,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(vertical = 14.dp, horizontal = 12.dp)
                                    .testTag("admin_generated_code_value")
                            )
                        }
                        if (lastGenerated.note.isNotBlank()) {
                            Text(
                                text = "Catatan: ${lastGenerated.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Text(
                            text = "Berlaku hingga: ${formatDateTime(lastGenerated.expiresAtMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = OrangeAccent
                        )
                        Text(
                            text = "Demi keamanan, hanya hash SHA-256 yang disimpan di database. Segera salin dan kirimkan kode ini kepada guru bersangkutan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { onCopyCode(lastGenerated.plaintextCode) },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_copy_code_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin Kode")
                            Spacer(Modifier.width(8.dp))
                            Text(if (copiedBanner) "Tersalin ke Clipboard!" else "Salin Kode Aktivasi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivationCodesListTab(
    state: AdminListState<ActivationCodeRecord>,
    filter: String,
    onFilterChange: (String) -> Unit,
    teacherByUid: Map<String, TeacherAccount>,
    isBusy: Boolean,
    onRevoke: (ActivationCodeRecord) -> Unit,
    onDelete: (ActivationCodeRecord) -> Unit
) {
    when (state) {
        AdminListState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyPrimary)
            }
        }
        is AdminListState.Error -> {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(state.message, color = ToleranceRed, textAlign = TextAlign.Center)
            }
        }
        is AdminListState.Success -> {
            val filtered = remember(state.items, filter) {
                if (filter == "ALL") state.items
                else state.items.filter { it.effectiveStatus().name == filter }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val chips = listOf(
                            "ALL" to "Semua",
                            EffectiveCodeStatus.ACTIVE.name to "Aktif",
                            EffectiveCodeStatus.USED.name to "Digunakan",
                            EffectiveCodeStatus.EXPIRED.name to "Kedaluwarsa",
                            EffectiveCodeStatus.REVOKED.name to "Dicabut"
                        )
                        items(chips) { (key, label) ->
                            FilterChip(
                                selected = filter == key,
                                onClick = { onFilterChange(key) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, LabOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Belum ada kode aktivasi untuk filter ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp)
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.codeHash.ifBlank { it.id } }) { record ->
                        val effective = record.effectiveStatus()
                        val badgeBg = when (effective) {
                            EffectiveCodeStatus.ACTIVE -> ToleranceGreenBg
                            EffectiveCodeStatus.USED -> NavyPrimary.copy(alpha = 0.1f)
                            EffectiveCodeStatus.EXPIRED -> OrangeContainer
                            EffectiveCodeStatus.REVOKED -> ToleranceRedBg
                        }
                        val badgeFg = when (effective) {
                            EffectiveCodeStatus.ACTIVE -> ToleranceGreen
                            EffectiveCodeStatus.USED -> NavyPrimary
                            EffectiveCodeStatus.EXPIRED -> OrangeAccent
                            EffectiveCodeStatus.REVOKED -> ToleranceRed
                        }
                        val usedTeacher = teacherByUid[record.usedByUid]
                        val createdMs = record.createdAt?.toDate()?.time
                        val expiresMs = record.expiresAt?.toDate()?.time

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, LabOutline)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = record.codeHint,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = NavyPrimary
                                    )
                                    Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = effective.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeFg,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (record.note.isNotBlank()) {
                                    Text(
                                        text = "Catatan: ${record.note}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NavyPrimary
                                    )
                                }
                                Text(
                                    text = "Hash SHA-256: ${record.codeHash.take(16)}...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Dibuat: ${createdMs?.let(::formatDateTime) ?: "-"} • Kedaluwarsa: ${expiresMs?.let(::formatDateTime) ?: "-"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                if (record.usedByUid.isNotBlank()) {
                                    Text(
                                        text = if (usedTeacher != null) {
                                            "Digunakan oleh: ${usedTeacher.fullName} (${usedTeacher.email})"
                                        } else {
                                            "UID Pengguna: ${record.usedByUid}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (effective != EffectiveCodeStatus.USED) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (effective == EffectiveCodeStatus.ACTIVE) {
                                            OutlinedButton(
                                                onClick = { onRevoke(record) },
                                                enabled = !isBusy,
                                                border = BorderStroke(1.dp, OrangeAccent),
                                                modifier = Modifier.weight(1f).height(44.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Block,
                                                    contentDescription = "Cabut Kode",
                                                    tint = OrangeAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Cabut", color = OrangeAccent)
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { onDelete(record) },
                                            enabled = !isBusy,
                                            border = BorderStroke(1.dp, ToleranceRed),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Hapus Kode",
                                                tint = ToleranceRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Hapus", color = ToleranceRed)
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
}

@Composable
private fun RegisteredTeachersTab(
    state: AdminListState<TeacherAccount>,
    filter: String,
    onFilterChange: (String) -> Unit,
    isBusy: Boolean,
    onToggleStatus: (TeacherAccount) -> Unit
) {
    when (state) {
        AdminListState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyPrimary)
            }
        }
        is AdminListState.Error -> {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(state.message, color = ToleranceRed, textAlign = TextAlign.Center)
            }
        }
        is AdminListState.Success -> {
            val filtered = remember(state.items, filter) {
                when (filter) {
                    "ACTIVE" -> state.items.filter { it.accountIsActive() }
                    "DISABLED" -> state.items.filter { !it.accountIsActive() }
                    else -> state.items
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL" to "Semua", "ACTIVE" to "Aktif", "DISABLED" to "Dinonaktifkan").forEach { (key, label) ->
                            FilterChip(
                                selected = filter == key,
                                onClick = { onFilterChange(key) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, LabOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Belum ada guru terdaftar untuk filter ini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp)
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.uid.ifBlank { it.id } }) { teacher ->
                        val active = teacher.accountIsActive()
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, LabOutline)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (active) Icons.Default.PersonOutline else Icons.Default.PersonOff,
                                            contentDescription = teacher.fullName,
                                            tint = if (active) NavyPrimary else ToleranceRed
                                        )
                                        Column {
                                            Text(
                                                text = teacher.fullName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = teacher.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    Surface(
                                        color = if (active) ToleranceGreenBg else ToleranceRedBg,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (active) "Aktif" else "Dinonaktifkan",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) ToleranceGreen else ToleranceRed,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Kelas: ${teacher.teachingClass} • Peran: ${teacher.role}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TealSecondary
                                )
                                Text(
                                    text = "Aktivasi: ${teacher.activatedAt?.toDate()?.time?.let(::formatDateTime) ?: "-"} • UID: ${teacher.uid.take(12)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )

                                Button(
                                    onClick = { onToggleStatus(teacher) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) ToleranceRed else ToleranceGreen
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(
                                        imageVector = if (active) Icons.Default.Block else Icons.Default.CheckCircle,
                                        contentDescription = if (active) "Nonaktifkan" else "Aktifkan",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (active) "Nonaktifkan Akun Guru" else "Aktifkan Kembali Akun Guru")
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
private fun AdminStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.09f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
    return formatter.format(Date(epochMillis))
}
