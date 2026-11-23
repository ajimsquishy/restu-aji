package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.auth.AuthSession
import com.example.domain.auth.AuthValidator
import com.example.domain.auth.PasswordHasher
import com.example.domain.auth.UserRole
import com.example.domain.model.CaliperResolution
import com.example.domain.model.LearningMode
import com.example.domain.model.MeasurementUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.measureLabDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "measure_lab_preferences"
)

data class UserPreferences(
    val participantCode: String = "SMK-TP-001",
    val defaultUnit: MeasurementUnit = MeasurementUnit.MILLIMETER,
    val defaultCaliperResolution: CaliperResolution = CaliperResolution.RES_0_05_MM,
    val learningMode: LearningMode = LearningMode.LEARN,
    val blindMeasurementDefault: Boolean = false,
    val userRole: UserRole = UserRole.NONE,
    val studentName: String = "",
    val studentAttendance: String = "",
    val activeClassName: String = "",
    val teacherPasswordHash: String = "",
    val teacherUid: String = "",
    val teacherFullName: String = "",
    val teacherEmail: String = ""
) {
    val isTeacherPasswordConfigured: Boolean
        get() = teacherPasswordHash.isNotBlank()

    val session: AuthSession
        get() = AuthSession(
            role = userRole,
            studentName = studentName,
            studentAttendance = studentAttendance,
            className = activeClassName,
            teacherUid = teacherUid,
            teacherFullName = teacherFullName,
            teacherEmail = teacherEmail
        )
}

class UserPreferencesStore(private val context: Context) {
    private object Keys {
        val PARTICIPANT_CODE = stringPreferencesKey("participant_code")
        val DEFAULT_UNIT = stringPreferencesKey("default_unit")
        val CALIPER_RES_UM = intPreferencesKey("caliper_res_um")
        val LEARNING_MODE = stringPreferencesKey("learning_mode")
        val BLIND_DEFAULT = booleanPreferencesKey("blind_default")
        val USER_ROLE = stringPreferencesKey("user_role")
        val STUDENT_NAME = stringPreferencesKey("student_name")
        val STUDENT_ATTENDANCE = stringPreferencesKey("student_attendance")
        val ACTIVE_CLASS_NAME = stringPreferencesKey("active_class_name")
        val TEACHER_PASSWORD_HASH = stringPreferencesKey("teacher_password_hash")
        val TEACHER_UID = stringPreferencesKey("teacher_uid")
        val TEACHER_FULL_NAME = stringPreferencesKey("teacher_full_name")
        val TEACHER_EMAIL = stringPreferencesKey("teacher_email")
    }

    val preferencesFlow: Flow<UserPreferences> = context.measureLabDataStore.data.map { prefs ->
        UserPreferences(
            participantCode = prefs[Keys.PARTICIPANT_CODE] ?: "SMK-TP-001",
            defaultUnit = prefs[Keys.DEFAULT_UNIT]
                ?.let { runCatching { MeasurementUnit.valueOf(it) }.getOrNull() }
                ?: MeasurementUnit.MILLIMETER,
            defaultCaliperResolution = prefs[Keys.CALIPER_RES_UM]
                ?.let { CaliperResolution.fromStepUm(it) }
                ?: CaliperResolution.RES_0_05_MM,
            learningMode = prefs[Keys.LEARNING_MODE]
                ?.let { runCatching { LearningMode.valueOf(it) }.getOrNull() }
                ?: LearningMode.LEARN,
            blindMeasurementDefault = prefs[Keys.BLIND_DEFAULT] ?: false,
            userRole = prefs[Keys.USER_ROLE]
                ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
                ?: UserRole.NONE,
            studentName = prefs[Keys.STUDENT_NAME] ?: "",
            studentAttendance = prefs[Keys.STUDENT_ATTENDANCE] ?: "",
            activeClassName = prefs[Keys.ACTIVE_CLASS_NAME] ?: "",
            teacherPasswordHash = prefs[Keys.TEACHER_PASSWORD_HASH] ?: "",
            teacherUid = prefs[Keys.TEACHER_UID] ?: "",
            teacherFullName = prefs[Keys.TEACHER_FULL_NAME] ?: "",
            teacherEmail = prefs[Keys.TEACHER_EMAIL] ?: ""
        )
    }

    suspend fun loginStudent(
        name: String,
        attendance: String,
        className: String
    ): Result<AuthSession> {
        val validationError = AuthValidator.validateStudentLogin(name, attendance, className)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val cleanName = name.trim()
        val normalizedAttendance = attendance.trim().toInt().toString()
        val cleanClass = className.trim()
        val session = AuthSession(
            role = UserRole.STUDENT,
            studentName = cleanName,
            studentAttendance = normalizedAttendance,
            className = cleanClass
        )
        context.measureLabDataStore.edit { prefs ->
            prefs[Keys.USER_ROLE] = UserRole.STUDENT.name
            prefs[Keys.STUDENT_NAME] = cleanName
            prefs[Keys.STUDENT_ATTENDANCE] = normalizedAttendance
            prefs[Keys.ACTIVE_CLASS_NAME] = cleanClass
            prefs[Keys.PARTICIPANT_CODE] = session.participantIdentifier
        }
        return Result.success(session)
    }

    suspend fun setupAndLoginTeacher(
        teachingClass: String,
        password: String,
        confirmPassword: String
    ): Result<AuthSession> {
        val current = preferencesFlow.first()
        if (current.isTeacherPasswordConfigured) {
            return Result.failure(IllegalStateException("Password guru sudah diatur sebelumnya. Silakan login menggunakan password Anda."))
        }
        val validationError = AuthValidator.validateTeacherSetup(teachingClass, password, confirmPassword)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val cleanClass = teachingClass.trim()
        val saltedHash = PasswordHasher.hashPassword(password)
        val session = AuthSession(
            role = UserRole.TEACHER,
            studentName = "",
            studentAttendance = "",
            className = cleanClass
        )
        context.measureLabDataStore.edit { prefs ->
            prefs[Keys.TEACHER_PASSWORD_HASH] = saltedHash
            prefs[Keys.USER_ROLE] = UserRole.TEACHER.name
            prefs[Keys.STUDENT_NAME] = ""
            prefs[Keys.STUDENT_ATTENDANCE] = ""
            prefs[Keys.ACTIVE_CLASS_NAME] = cleanClass
            prefs[Keys.PARTICIPANT_CODE] = session.participantIdentifier
        }
        return Result.success(session)
    }

    suspend fun loginTeacher(
        teachingClass: String,
        password: String
    ): Result<AuthSession> {
        val validationError = AuthValidator.validateTeacherLogin(teachingClass, password)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val current = preferencesFlow.first()
        if (!current.isTeacherPasswordConfigured) {
            return Result.failure(IllegalStateException("Password guru belum dibuat. Silakan atur password terlebih dahulu."))
        }
        if (!PasswordHasher.verifyPassword(password, current.teacherPasswordHash)) {
            return Result.failure(SecurityException("Password guru tidak sesuai. Periksa kembali password Anda."))
        }
        val cleanClass = teachingClass.trim()
        val session = AuthSession(
            role = UserRole.TEACHER,
            studentName = "",
            studentAttendance = "",
            className = cleanClass
        )
        context.measureLabDataStore.edit { prefs ->
            prefs[Keys.USER_ROLE] = UserRole.TEACHER.name
            prefs[Keys.STUDENT_NAME] = ""
            prefs[Keys.STUDENT_ATTENDANCE] = ""
            prefs[Keys.ACTIVE_CLASS_NAME] = cleanClass
            prefs[Keys.PARTICIPANT_CODE] = session.participantIdentifier
        }
        return Result.success(session)
    }

    suspend fun changeTeacherPassword(
        currentPassword: String,
        newPassword: String,
        confirmNewPassword: String
    ): Result<Unit> {
        val current = preferencesFlow.first()
        if (!current.isTeacherPasswordConfigured) {
            return Result.failure(IllegalStateException("Password guru belum dikonfigurasi."))
        }
        if (!PasswordHasher.verifyPassword(currentPassword, current.teacherPasswordHash)) {
            return Result.failure(SecurityException("Password lama tidak sesuai."))
        }
        val validationError = AuthValidator.validateTeacherSetup(
            teachingClass = current.activeClassName.ifBlank { "X TP 1" },
            password = newPassword,
            confirmPassword = confirmNewPassword
        )
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val newSaltedHash = PasswordHasher.hashPassword(newPassword)
        context.measureLabDataStore.edit { prefs ->
            prefs[Keys.TEACHER_PASSWORD_HASH] = newSaltedHash
        }
        return Result.success(Unit)
    }

    suspend fun saveFirebaseTeacherSession(
        uid: String,
        fullName: String,
        email: String,
        teachingClass: String
    ): AuthSession {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim()
        val cleanClass = teachingClass.trim().ifBlank { "Semua Kelas X TP" }
        val session = AuthSession(
            role = UserRole.TEACHER,
            studentName = "",
            studentAttendance = "",
            className = cleanClass,
            teacherUid = uid,
            teacherFullName = cleanName,
            teacherEmail = cleanEmail
        )
        context.measureLabDataStore.edit { prefs ->
            prefs[Keys.USER_ROLE] = UserRole.TEACHER.name
            prefs[Keys.STUDENT_NAME] = ""
            prefs[Keys.STUDENT_ATTENDANCE] = ""
            prefs[Keys.ACTIVE_CLASS_NAME] = cleanClass
            prefs[Keys.TEACHER_UID] = uid
            prefs[Keys.TEACHER_FULL_NAME] = cleanName
            prefs[Keys.TEACHER_EMAIL] = cleanEmail
            prefs[Keys.PARTICIPANT_CODE] = session.participantIdentifier
        }
        return session
    }

    suspend fun logout() {
        context.measureLabDataStore.edit { prefs ->
            prefs[Keys.USER_ROLE] = UserRole.NONE.name
            prefs[Keys.TEACHER_UID] = ""
            prefs[Keys.TEACHER_FULL_NAME] = ""
            prefs[Keys.TEACHER_EMAIL] = ""
        }
    }

    suspend fun updateParticipantCode(code: String) {
        val cleaned = code.trim().ifEmpty { "SMK-TP-001" }
        context.measureLabDataStore.edit { it[Keys.PARTICIPANT_CODE] = cleaned }
    }

    suspend fun updateDefaultUnit(unit: MeasurementUnit) {
        context.measureLabDataStore.edit { it[Keys.DEFAULT_UNIT] = unit.name }
    }

    suspend fun updateCaliperResolution(resolution: CaliperResolution) {
        context.measureLabDataStore.edit { it[Keys.CALIPER_RES_UM] = resolution.stepUm }
    }

    suspend fun updateLearningMode(mode: LearningMode) {
        context.measureLabDataStore.edit { it[Keys.LEARNING_MODE] = mode.name }
    }

    suspend fun updateBlindDefault(enabled: Boolean) {
        context.measureLabDataStore.edit { it[Keys.BLIND_DEFAULT] = enabled }
    }
}
