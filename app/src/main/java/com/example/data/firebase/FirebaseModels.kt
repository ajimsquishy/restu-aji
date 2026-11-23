package com.example.data.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class AdminProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "admin",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TeacherAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val teachingClass: String = "",
    val role: String = "teacher",
    val status: String = "active",
    val activationCodeHash: String = "",
    @ServerTimestamp val activatedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun accountIsActive(): Boolean = status.equals("active", ignoreCase = true)
}

@Keep
@IgnoreExtraProperties
data class ActivationCodeRecord(
    @DocumentId val id: String = "",
    val codeHash: String = "",
    val codeHint: String = "",
    val note: String = "",
    val status: String = "active",
    val createdByAdminUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val usedByUid: String = "",
    val usedAt: Timestamp? = null,
    val revokedAt: Timestamp? = null
) {
    fun effectiveStatus(nowMillis: Long = System.currentTimeMillis()): EffectiveCodeStatus {
        return when (status.lowercase()) {
            "used" -> EffectiveCodeStatus.USED
            "revoked" -> EffectiveCodeStatus.REVOKED
            "active" -> {
                val expMs = expiresAt?.toDate()?.time ?: 0L
                if (expMs <= nowMillis) EffectiveCodeStatus.EXPIRED else EffectiveCodeStatus.ACTIVE
            }
            else -> EffectiveCodeStatus.REVOKED
        }
    }
}

enum class EffectiveCodeStatus(val label: String) {
    ACTIVE("Aktif"),
    USED("Digunakan"),
    EXPIRED("Kedaluwarsa"),
    REVOKED("Dicabut")
}

/**
 * Returned once to Admin Utama immediately after generating a code so it can be copied/shared.
 * Plaintext code is never persisted in Firestore.
 */
data class GeneratedActivationCode(
    val plaintextCode: String,
    val codeHash: String,
    val codeHint: String,
    val note: String,
    val expiresAtMillis: Long
)
