package com.example.ui.feature.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.ActivationCodeRecord
import com.example.data.firebase.GeneratedActivationCode
import com.example.data.firebase.TeacherAccount
import com.example.data.firebase.TeacherActivationRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdminListState<out T> {
    data object Loading : AdminListState<Nothing>
    data class Success<T>(val items: List<T>) : AdminListState<T>
    data class Error(val message: String) : AdminListState<Nothing>
}

/**
 * ViewModel instantiated ONLY after Admin Utama authentication succeeds (keyed by adminUid).
 * Receives constructor-injected TeacherActivationRepository configured with R.string.firestore_database_id.
 */
class AdminDashboardViewModel(
    private val repository: TeacherActivationRepository,
    @Suppress("unused") private val adminUid: String
) : ViewModel() {

    val codesState: StateFlow<AdminListState<ActivationCodeRecord>> =
        repository.observeActivationCodes()
            .map<List<ActivationCodeRecord>, AdminListState<ActivationCodeRecord>> {
                AdminListState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing activation codes", rootError)
                emit(AdminListState.Error(rootError.message ?: "Gagal memuat daftar kode aktivasi."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AdminListState.Loading
            )

    val teachersState: StateFlow<AdminListState<TeacherAccount>> =
        repository.observeTeachers()
            .map<List<TeacherAccount>, AdminListState<TeacherAccount>> {
                AdminListState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing teachers", rootError)
                emit(AdminListState.Error(rootError.message ?: "Gagal memuat daftar guru terdaftar."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AdminListState.Loading
            )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _lastGeneratedCode = MutableStateFlow<GeneratedActivationCode?>(null)
    val lastGeneratedCode: StateFlow<GeneratedActivationCode?> = _lastGeneratedCode.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun generateCode(note: String) {
        if (_isBusy.value) return
        _isBusy.value = true
        _errorMessage.value = null
        _feedbackMessage.value = null
        viewModelScope.launch {
            val result = repository.generateActivationCode(note)
            _isBusy.value = false
            result.onSuccess { generated ->
                _lastGeneratedCode.value = generated
                _feedbackMessage.value = "Kode aktivasi unik berhasil dibuat (berlaku 7 hari, sekali pakai)."
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal membuat kode aktivasi."
            }
        }
    }

    fun revokeCode(codeHash: String) {
        if (_isBusy.value) return
        _isBusy.value = true
        _errorMessage.value = null
        _feedbackMessage.value = null
        viewModelScope.launch {
            val result = repository.revokeActivationCode(codeHash)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Kode aktivasi berhasil dicabut dan tidak dapat digunakan lagi."
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal mencabut kode aktivasi."
            }
        }
    }

    fun deleteUnusedCode(codeHash: String) {
        if (_isBusy.value) return
        _isBusy.value = true
        _errorMessage.value = null
        _feedbackMessage.value = null
        viewModelScope.launch {
            val result = repository.deleteUnusedActivationCode(codeHash)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Kode aktivasi yang belum digunakan telah dihapus."
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal menghapus kode aktivasi."
            }
        }
    }

    fun setTeacherActive(teacher: TeacherAccount, active: Boolean) {
        if (_isBusy.value) return
        _isBusy.value = true
        _errorMessage.value = null
        _feedbackMessage.value = null
        viewModelScope.launch {
            val result = repository.setTeacherAccountStatus(teacher.uid, active)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = if (active) {
                    "Akun guru ${teacher.fullName} telah diaktifkan kembali."
                } else {
                    "Akun guru ${teacher.fullName} telah dinonaktifkan dan tidak dapat login."
                }
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal memperbarui status akun guru."
            }
        }
    }

    fun clearBanner() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    private companion object {
        const val TAG = "AdminDashboardVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
