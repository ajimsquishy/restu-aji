package com.example.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Logs structured JSON diagnostic information for every Firestore error
 * and returns the serialized JSON payload.
 */
fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

/**
 * Converts Firestore or validation exceptions into user-friendly Indonesian messages.
 */
fun mapFriendlyFirestoreError(exception: Throwable, fallback: String): String {
    val firestoreError = generateSequence(exception) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreError?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Akses ditolak oleh aturan keamanan server. Pastikan akun Anda berwenang atau kode aktivasi masih valid."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Koneksi internet tidak tersedia. Periksa jaringan Anda lalu coba lagi."
        FirebaseFirestoreException.Code.ABORTED ->
            "Permintaan bersamaan terdeteksi. Silakan coba lagi atau periksa status kode aktivasi."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "Kuota harian Cloud Firestore tercapai. Coba lagi besok atau periksa konsol Firebase."
        else -> exception.message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
