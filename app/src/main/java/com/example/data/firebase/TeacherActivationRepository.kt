package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.auth.ActivationCodeGenerator
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Cloud Firestore repository for Admin Utama management, single-use activation code lifecycle,
 * and atomic teacher onboarding/status enforcement.
 *
 * ALWAYS initialized using the custom database ID from R.string.firestore_database_id.
 */
class TeacherActivationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUser() =
        auth.currentUser
            ?: throw IllegalStateException("Silakan masuk dengan akun Google terlebih dahulu.")

    private fun requireUserId(): String = requireSignedInUser().uid

    // -------------------------------------------------------------------------
    // 1. Admin Utama Verification & Bootstrap
    // -------------------------------------------------------------------------

    /**
     * Verifies whether the currently signed-in Google user is the authorized Admin Utama.
     * Bootstraps /admins/{uid} on first login for the verified owner email; rejects all others.
     */
    suspend fun verifyAndBootstrapAdmin(): Result<AdminProfile> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Silakan login dengan akun Google Admin Utama."))
        val uid = currentUser.uid
        val path = "$COL_ADMINS/$uid"
        return try {
            val docRef = db.collection(COL_ADMINS).document(uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val admin = snapshot.toObject(
                    AdminProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (admin != null && admin.role == "admin") {
                    return Result.success(admin)
                }
            }
            if (!GoogleAuthManager.isPrimaryAdminEmail(currentUser)) {
                return Result.failure(
                    SecurityException("Akses ditolak. Hanya Admin Utama (${GoogleAuthManager.PRIMARY_ADMIN_EMAIL}) yang berhak masuk ke Dashboard Admin.")
                )
            }
            val displayName = currentUser.displayName?.trim()?.take(100)?.ifBlank { "Admin Utama MEASURE LAB" }
                ?: "Admin Utama MEASURE LAB"
            val email = currentUser.email ?: GoogleAuthManager.PRIMARY_ADMIN_EMAIL
            val payload = mapOf(
                "uid" to uid,
                "email" to email,
                "displayName" to displayName,
                "role" to "admin",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                AdminProfile(
                    id = uid,
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    role = "admin",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(
                SecurityException(
                    mapFriendlyFirestoreError(
                        e,
                        "Akses ditolak. Akun ini tidak terdaftar sebagai Admin Utama."
                    )
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // 2. Admin Activation Code Management
    // -------------------------------------------------------------------------

    /**
     * Generates a cryptographically unique single-use 7-day activation code (GURU-XXXX-XXXX)
     * and stores ONLY its SHA-256 hash + masked hint in Firestore.
     */
    suspend fun generateActivationCode(note: String = ""): Result<GeneratedActivationCode> {
        val cleanNote = note.trim().take(100)
        var docPath = COL_ACTIVATION_CODES
        return try {
            val adminUid = requireUserId()
            // Retry on astronomical SHA-256 collision just in case
            repeat(3) {
                val plaintext = ActivationCodeGenerator.generateCode()
                val codeHash = ActivationCodeGenerator.sha256Hex(plaintext)
                val codeHint = ActivationCodeGenerator.maskedHint(plaintext)
                val docRef = db.collection(COL_ACTIVATION_CODES).document(codeHash)
                docPath = docRef.path

                val existing = docRef.get().await()
                if (!existing.exists()) {
                    val expiresAtMillis = ActivationCodeGenerator.computeExpiryMillis()
                    val payload = buildMap<String, Any> {
                        put("codeHash", codeHash)
                        put("codeHint", codeHint)
                        if (cleanNote.isNotEmpty()) {
                            put("note", cleanNote)
                        }
                        put("status", "active")
                        put("createdByAdminUid", adminUid)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("expiresAt", Timestamp(Date(expiresAtMillis)))
                    }
                    docRef.set(payload).await()
                    return Result.success(
                        GeneratedActivationCode(
                            plaintextCode = plaintext,
                            codeHash = codeHash,
                            codeHint = codeHint,
                            note = cleanNote,
                            expiresAtMillis = expiresAtMillis
                        )
                    )
                }
            }
            Result.failure(IllegalStateException("Gagal menghasilkan kode unik. Silakan coba lagi."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(
                IllegalStateException(
                    mapFriendlyFirestoreError(e, "Gagal membuat kode aktivasi baru.")
                )
            )
        }
    }

    fun observeActivationCodes(): Flow<List<ActivationCodeRecord>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_ACTIVATION_CODES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivationCodeRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, COL_ACTIVATION_CODES)
                    }
                    throw error
                }
        )
    }

    suspend fun revokeActivationCode(codeHash: String): Result<Unit> {
        val path = "$COL_ACTIVATION_CODES/$codeHash"
        return try {
            requireUserId()
            require(codeHash.length == 64) { "Hash kode tidak valid." }
            db.collection(COL_ACTIVATION_CODES).document(codeHash)
                .update(
                    mapOf(
                        "status" to "revoked",
                        "revokedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(
                IllegalStateException(
                    mapFriendlyFirestoreError(e, "Gagal mencabut kode aktivasi.")
                )
            )
        }
    }

    suspend fun deleteUnusedActivationCode(codeHash: String): Result<Unit> {
        val path = "$COL_ACTIVATION_CODES/$codeHash"
        return try {
            requireUserId()
            require(codeHash.length == 64) { "Hash kode tidak valid." }
            db.collection(COL_ACTIVATION_CODES).document(codeHash)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(
                IllegalStateException(
                    mapFriendlyFirestoreError(e, "Kode yang sudah digunakan tidak dapat dihapus.")
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // 3. Admin Teacher Account Management
    // -------------------------------------------------------------------------

    fun observeTeachers(): Flow<List<TeacherAccount>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(COL_TEACHERS)
                .orderBy("activatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TeacherAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, COL_TEACHERS)
                    }
                    throw error
                }
        )
    }

    suspend fun setTeacherAccountStatus(teacherUid: String, active: Boolean): Result<Unit> {
        val path = "$COL_TEACHERS/$teacherUid"
        return try {
            requireUserId()
            require(teacherUid.isNotBlank()) { "UID guru tidak valid." }
            val nextStatus = if (active) "active" else "disabled"
            db.collection(COL_TEACHERS).document(teacherUid)
                .update(
                    mapOf(
                        "status" to nextStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(
                IllegalStateException(
                    mapFriendlyFirestoreError(e, "Gagal mengubah status akun guru.")
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // 4. Teacher Atomic Activation & Login Verification
    // -------------------------------------------------------------------------

    /**
     * Atomically redeems a single-use activation code and creates the teacher profile inside a
     * Firestore Transaction, guarded server-side by firestore.rules (get/getAfter + expiry).
     * Concurrent attempts to redeem the same code are serialized and rejected atomically.
     */
    suspend fun activateTeacherAccount(
        fullName: String,
        teachingClass: String,
        rawActivationCode: String
    ): Result<TeacherAccount> {
        val cleanName = fullName.trim()
        val cleanClass = teachingClass.trim()
        val normalizedCode = ActivationCodeGenerator.normalizeCode(rawActivationCode)

        if (cleanName.length < 2 || cleanName.length > 100) {
            return Result.failure(IllegalArgumentException("Nama lengkap guru harus 2–100 karakter."))
        }
        if (cleanClass.length < 2 || cleanClass.length > 50) {
            return Result.failure(IllegalArgumentException("Kelas yang diajar harus 2–50 karakter."))
        }
        if (!ActivationCodeGenerator.isValidFormat(normalizedCode)) {
            return Result.failure(
                IllegalArgumentException("Format kode aktivasi tidak valid. Contoh format: GURU-8K2P-9M4X.")
            )
        }

        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Verifikasi akun Google guru terlebih dahulu."))
        val email = user.email?.trim()
        if (email.isNullOrBlank() || !user.isEmailVerified) {
            return Result.failure(IllegalStateException("Akun Google belum terverifikasi. Silakan pilih akun Google yang valid."))
        }

        val uid = user.uid
        val codeHash = ActivationCodeGenerator.sha256Hex(normalizedCode)
        val codeRef = db.collection(COL_ACTIVATION_CODES).document(codeHash)
        val teacherRef = db.collection(COL_TEACHERS).document(uid)

        return try {
            val createdAccount = db.runTransaction { transaction ->
                val existingTeacher = transaction.get(teacherRef)
                if (existingTeacher.exists()) {
                    throw IllegalStateException("Email Google ini sudah pernah diaktivasi sebagai guru. Silakan langsung login tanpa kode aktivasi.")
                }

                val codeSnap = transaction.get(codeRef)
                if (!codeSnap.exists()) {
                    throw IllegalArgumentException("Kode aktivasi tidak ditemukan. Periksa kembali kode dari Admin Utama.")
                }

                val status = codeSnap.getString("status") ?: ""
                if (status == "used") {
                    throw IllegalStateException("Kode aktivasi ini sudah digunakan dan tidak dapat dipakai kembali.")
                }
                if (status == "revoked") {
                    throw IllegalStateException("Kode aktivasi ini telah dicabut oleh Admin Utama.")
                }
                if (status != "active") {
                    throw IllegalStateException("Kode aktivasi tidak aktif.")
                }

                val expiresAt = codeSnap.getTimestamp("expiresAt")
                if (expiresAt == null || expiresAt.toDate().time <= System.currentTimeMillis()) {
                    throw IllegalStateException("Kode aktivasi telah kedaluwarsa (melebihi masa berlaku 7 hari).")
                }

                // Atomic pair: mark activation_codes/{codeHash} as used + create teachers/{uid}
                transaction.update(
                    codeRef,
                    mapOf(
                        "status" to "used",
                        "usedByUid" to uid,
                        "usedAt" to FieldValue.serverTimestamp()
                    )
                )

                val teacherPayload = mapOf(
                    "uid" to uid,
                    "fullName" to cleanName,
                    "email" to email,
                    "teachingClass" to cleanClass,
                    "role" to "teacher",
                    "status" to "active",
                    "activationCodeHash" to codeHash,
                    "activatedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(teacherRef, teacherPayload)

                TeacherAccount(
                    id = uid,
                    uid = uid,
                    fullName = cleanName,
                    email = email,
                    teachingClass = cleanClass,
                    role = "teacher",
                    status = "active",
                    activationCodeHash = codeHash,
                    activatedAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            }.await()

            Result.success(createdAccount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COL_TEACHERS/$uid")
            Result.failure(
                IllegalStateException(
                    mapFriendlyFirestoreError(e, "Aktivasi akun guru gagal. Pastikan kode valid, belum digunakan, dan belum kedaluwarsa.")
                )
            )
        }
    }

    /**
     * Loads the signed-in teacher's profile for login without needing an activation code again.
     */
    suspend fun fetchSignedInTeacherProfile(): Result<TeacherAccount?> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Silakan masuk dengan akun Google guru."))
        val path = "$COL_TEACHERS/$uid"
        return try {
            val snap = db.collection(COL_TEACHERS).document(uid).get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                val teacher = snap.toObject(
                    TeacherAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                Result.success(teacher)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(
                IllegalStateException(
                    mapFriendlyFirestoreError(e, "Gagal memeriksa status akun guru di server.")
                )
            )
        }
    }

    /**
     * Real-time listener on a teacher's own document so if Admin disables the account mid-session,
     * the app immediately blocks access and signs them out.
     */
    fun observeTeacherAccount(teacherUid: String): Flow<TeacherAccount?> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != teacherUid) {
            emit(null)
            return@flow
        }
        val path = "$COL_TEACHERS/$teacherUid"
        emitAll(
            db.collection(COL_TEACHERS).document(teacherUid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            TeacherAccount::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == teacherUid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateOwnTeachingClass(teachingClass: String): Result<Unit> {
        val cleanClass = teachingClass.trim()
        if (cleanClass.length < 2 || cleanClass.length > 50) {
            return Result.failure(IllegalArgumentException("Nama kelas harus 2–50 karakter."))
        }
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sesi guru tidak ditemukan."))
        val path = "$COL_TEACHERS/$uid"
        return try {
            db.collection(COL_TEACHERS).document(uid)
                .update(
                    mapOf(
                        "teachingClass" to cleanClass,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(
                IllegalStateException(
                    mapFriendlyFirestoreError(e, "Gagal memperbarui kelas ajar.")
                )
            )
        }
    }

    companion object {
        const val COL_ADMINS = "admins"
        const val COL_TEACHERS = "teachers"
        const val COL_ACTIVATION_CODES = "activation_codes"
    }
}
