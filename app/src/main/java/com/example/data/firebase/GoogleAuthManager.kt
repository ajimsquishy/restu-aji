package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages Firebase Authentication exclusively through Google Sign-In via Jetpack Credential Manager.
 * Never uses anonymous auth or email/password auth.
 */
object GoogleAuthManager {
    private const val TAG = "Auth"

    /**
     * Verified owner email configured as the sole bootstrap Admin Utama in firestore.rules.
     * No password or secret is ever stored in client code.
     */
    const val PRIMARY_ADMIN_EMAIL = "ajimsquishy@gmail.com"

    fun currentUser(): FirebaseUser? = runCatching { Firebase.auth.currentUser }.getOrNull()

    fun isPrimaryAdminEmail(user: FirebaseUser?): Boolean {
        return user != null &&
            user.isEmailVerified &&
            user.email.equals(PRIMARY_ADMIN_EMAIL, ignoreCase = true)
    }

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val auth = runCatching { Firebase.auth }.getOrNull()
        if (auth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Interactive Google Sign-In using GetSignInWithGoogleOption (exactly one option per request).
     */
    suspend fun signInWithGoogleInteractive(context: Context): GoogleSignInOutcome {
        val activity = context.findActivity()
            ?: return GoogleSignInOutcome.Error("Tidak dapat membuka dialog Google Sign-In dari konteks ini.")
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return GoogleSignInOutcome.Error("Konfigurasi Google Sign-In belum tersedia (default_web_client_id tidak ditemukan).")
        }
        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = Firebase.auth
                    .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                    .await()
                val user = authResult.user
                    ?: return GoogleSignInOutcome.Error("Autentikasi Google berhasil tetapi data pengguna tidak ditemukan.")
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Error("Jenis kredensial Google tidak dikenali.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Gagal masuk menggunakan Google.")
        }
    }

    /**
     * Optional silent auto-sign-in using GetGoogleIdOption (one option per request).
     */
    suspend fun attemptSilentAutoSignIn(context: Context): FirebaseUser? {
        currentUser()?.let { return it }
        val activity = context.findActivity() ?: return null
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return null
        }
        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Safely clears Credential Manager state and signs out of Firebase Auth inside NonCancellable.
     */
    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                withTimeoutOrNull(800L) {
                    CredentialManager.create(context.applicationContext)
                        .clearCredentialState(ClearCredentialStateRequest())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { Firebase.auth.signOut() }
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}
