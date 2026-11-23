package com.example.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Active access role for MEASURE LAB offline classroom sessions.
 */
enum class UserRole(val label: String) {
    NONE("Belum Masuk"),
    STUDENT("Murid"),
    TEACHER("Guru")
}

/**
 * Current authenticated session stored offline in DataStore Preferences.
 */
data class AuthSession(
    val role: UserRole = UserRole.NONE,
    val studentName: String = "",
    val studentAttendance: String = "",
    val className: String = "",
    val teacherUid: String = "",
    val teacherFullName: String = "",
    val teacherEmail: String = ""
) {
    val isAuthenticated: Boolean
        get() = when (role) {
            UserRole.STUDENT -> studentName.isNotBlank() && studentAttendance.isNotBlank() && className.isNotBlank()
            UserRole.TEACHER -> className.isNotBlank()
            UserRole.NONE -> false
        }

    val displayIdentity: String
        get() = when (role) {
            UserRole.STUDENT -> "$studentName • Absen $studentAttendance • $className"
            UserRole.TEACHER -> if (teacherFullName.isNotBlank()) "$teacherFullName • $className" else "Guru • $className"
            UserRole.NONE -> "Belum Masuk"
        }

    val compactBadge: String
        get() = when (role) {
            UserRole.STUDENT -> "Absen ${studentAttendance.padStart(2, '0')} • $className"
            UserRole.TEACHER -> "Guru • $className"
            UserRole.NONE -> "Tamu"
        }

    val participantIdentifier: String
        get() = when (role) {
            UserRole.STUDENT -> "$className | No.${studentAttendance.padStart(2, '0')} | $studentName"
            UserRole.TEACHER -> if (teacherFullName.isNotBlank()) "GURU | $teacherFullName | $className" else "GURU | $className"
            UserRole.NONE -> "SMK-TP-001"
        }
}

/**
 * Standard SMK Kelas X Teknik Pemesinan class presets plus validation rules.
 */
object AuthValidator {
    val studentClassPresets = listOf("X TP 1", "X TP 2", "X TP 3", "X TP 4")
    val teacherClassPresets = listOf("X TP 1", "X TP 2", "X TP 3", "X TP 4", "Semua Kelas X TP")

    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_PASSWORD_LENGTH = 64

    fun validateStudentLogin(
        name: String,
        attendance: String,
        className: String
    ): String? {
        val cleanName = name.trim()
        val cleanAttendance = attendance.trim()
        val cleanClass = className.trim()

        if (cleanName.length < 2) {
            return "Nama lengkap murid minimal 2 karakter."
        }
        if (cleanName.length > 60) {
            return "Nama lengkap murid maksimal 60 karakter."
        }
        if (cleanName.any { it.isISOControl() }) {
            return "Nama mengandung karakter tidak valid."
        }
        val attendanceNum = cleanAttendance.toIntOrNull()
        if (attendanceNum == null || attendanceNum !in 1..60 || cleanAttendance.any { !it.isDigit() }) {
            return "Nomor absensi harus berupa angka bulat antara 1 sampai 60."
        }
        if (cleanClass.length < 2 || cleanClass.length > 30) {
            return "Kelas harus diisi (contoh: X TP 1)."
        }
        return null
    }

    fun validateTeacherSetup(
        teachingClass: String,
        password: String,
        confirmPassword: String
    ): String? {
        val cleanClass = teachingClass.trim()
        if (cleanClass.length < 2 || cleanClass.length > 30) {
            return "Kelas yang diajar harus diisi (contoh: X TP 1)."
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "Password guru minimal $MIN_PASSWORD_LENGTH karakter."
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            return "Password guru maksimal $MAX_PASSWORD_LENGTH karakter."
        }
        if (password != confirmPassword) {
            return "Konfirmasi password tidak cocok."
        }
        return null
    }

    fun validateTeacherLogin(
        teachingClass: String,
        password: String
    ): String? {
        val cleanClass = teachingClass.trim()
        if (cleanClass.length < 2 || cleanClass.length > 30) {
            return "Pilih kelas yang diajar terlebih dahulu."
        }
        if (!ClassTeacherAuth.isSupportedClass(cleanClass)) {
            return "Pilih salah satu kelas resmi: ${teacherClassPresets.joinToString(", ")}."
        }
        if (password.trim().isEmpty()) {
            return "Password autentikasi kelas wajib diisi."
        }
        return null
    }
}

/**
 * Offline per-class teacher authentication using salted SHA-256 hashes.
 * Plaintext class passwords are never stored in Kotlin source code; they are documented
 * separately in Password_Guru_MeasureLab.pdf in the project root folder.
 */
object ClassTeacherAuth {
    private data class ClassCredentialHash(
        val canonicalClass: String,
        val salt: String,
        val sha256Hex: String
    )

    private val credentials: List<ClassCredentialHash> = listOf(
        ClassCredentialHash(
            canonicalClass = "X TP 1",
            salt = "mlab_salt_xtp1_2026",
            sha256Hex = "9b47776890bd0cb9551704a16d4d16f48d3a04721381b023260cce2ec3b58824"
        ),
        ClassCredentialHash(
            canonicalClass = "X TP 2",
            salt = "mlab_salt_xtp2_2026",
            sha256Hex = "fc62404ab39e100fd2e906edab3702ec5669bbd7cffbd3af05fe0b4a57d215d8"
        ),
        ClassCredentialHash(
            canonicalClass = "X TP 3",
            salt = "mlab_salt_xtp3_2026",
            sha256Hex = "1ea1de6344f6435a41b2fa9337acbf2cbae67e7225cbfd069577fff500d23880"
        ),
        ClassCredentialHash(
            canonicalClass = "X TP 4",
            salt = "mlab_salt_xtp4_2026",
            sha256Hex = "adca571444402cbfbdb7844fc84d9404768ce0a6f59ac26db6bcbe6f7c1fd458"
        ),
        ClassCredentialHash(
            canonicalClass = "Semua Kelas X TP",
            salt = "mlab_salt_all_2026",
            sha256Hex = "6b8bdfb278f1898b1b704415625a3ffbc4a957460c9149ffb104a799ef75235f"
        )
    )

    val supportedClasses: List<String> = credentials.map { it.canonicalClass }

    fun canonicalClassOrNull(className: String): String? {
        val cleaned = className.trim()
        return credentials.firstOrNull { it.canonicalClass.equals(cleaned, ignoreCase = true) }?.canonicalClass
    }

    fun isSupportedClass(className: String): Boolean = canonicalClassOrNull(className) != null

    fun verifyClassPassword(className: String, rawPassword: String): Boolean {
        val entry = credentials.firstOrNull { it.canonicalClass.equals(className.trim(), ignoreCase = true) }
            ?: return false
        val cleanedPassword = rawPassword.trim()
        if (cleanedPassword.isEmpty()) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${entry.salt}:$cleanedPassword".toByteArray(Charsets.UTF_8))
        val expected = hexToBytes(entry.sha256Hex) ?: return false
        return MessageDigest.isEqual(expected, digest)
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0 || hex.isEmpty()) return null
        return try {
            ByteArray(hex.length / 2) { idx ->
                hex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}

/**
 * Cryptographic salted PBKDF2-HMAC-SHA256 password hashing & constant-time verification.
 * Uses pure lowercase hex encoding so it runs identically on Android API 24+ and JVM unit tests.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val SEPARATOR = ":"

    private val secureRandom = SecureRandom()

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val chars = password.toCharArray()
        return try {
            val hash = deriveKey(chars, salt)
            "${salt.toHex()}$SEPARATOR${hash.toHex()}"
        } finally {
            chars.fill('\u0000')
        }
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (password.isEmpty() || storedHash.isBlank()) return false
        val parts = storedHash.split(SEPARATOR)
        if (parts.size != 2) return false
        val salt = parts[0].fromHexOrNull() ?: return false
        val expectedHash = parts[1].fromHexOrNull() ?: return false
        val chars = password.toCharArray()
        return try {
            val candidateHash = deriveKey(chars, salt)
            MessageDigest.isEqual(expectedHash, candidateHash)
        } catch (_: Exception) {
            false
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun deriveKey(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private fun String.fromHexOrNull(): ByteArray? {
        if (length % 2 != 0 || isEmpty()) return null
        return try {
            ByteArray(length / 2) { idx ->
                substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
