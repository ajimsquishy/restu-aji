package com.example.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Cryptographic generator, validator, and SHA-256 hasher for single-use teacher activation codes.
 * Format: GURU-XXXX-XXXX using unambiguous uppercase alphanumeric characters (excludes 0/O, 1/I/L).
 */
object ActivationCodeGenerator {
    private const val PREFIX = "GURU"
    private const val CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val SEGMENT_LENGTH = 4
    const val EXPIRY_DAYS = 7L
    const val EXPIRY_MILLIS = EXPIRY_DAYS * 24L * 60L * 60L * 1000L

    private val secureRandom = SecureRandom()
    private val codePattern = Regex("^GURU-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}$")

    /**
     * Generates a cryptographically random, unique activation code such as GURU-8K2P-9M4X.
     */
    fun generateCode(): String {
        val seg1 = randomSegment()
        val seg2 = randomSegment()
        return "$PREFIX-$seg1-$seg2"
    }

    /**
     * Normalizes user input by trimming, uppercasing, and standardizing hyphen grouping.
     */
    fun normalizeCode(raw: String): String {
        val stripped = raw.trim()
            .uppercase(Locale.US)
            .replace(" ", "")
            .replace("_", "-")
        if (stripped.startsWith("$PREFIX-") && stripped.length == 14) {
            return stripped
        }
        val alnum = stripped.filter { it.isLetterOrDigit() }
        if (alnum.length == 12 && alnum.startsWith(PREFIX)) {
            val body = alnum.removePrefix(PREFIX)
            return "$PREFIX-${body.substring(0, 4)}-${body.substring(4, 8)}"
        }
        return stripped
    }

    fun isValidFormat(raw: String): Boolean {
        val normalized = normalizeCode(raw)
        return codePattern.matches(normalized)
    }

    /**
     * Computes lowercase 64-character SHA-256 hex digest of the normalized activation code.
     * Plaintext codes are never persisted in Firestore; only this hash is stored.
     */
    fun sha256Hex(rawCode: String): String {
        val normalized = normalizeCode(rawCode)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Produces a non-sensitive masked display hint (e.g., GURU-****-9M4X) for the Admin list.
     */
    fun maskedHint(rawCode: String): String {
        val normalized = normalizeCode(rawCode)
        val suffix = normalized.takeLast(SEGMENT_LENGTH)
        return "$PREFIX-****-$suffix"
    }

    fun computeExpiryMillis(createdAtMillis: Long = System.currentTimeMillis()): Long {
        return createdAtMillis + EXPIRY_MILLIS
    }

    private fun randomSegment(): String {
        val sb = StringBuilder(SEGMENT_LENGTH)
        repeat(SEGMENT_LENGTH) {
            sb.append(CHARSET[secureRandom.nextInt(CHARSET.length)])
        }
        return sb.toString()
    }
}
