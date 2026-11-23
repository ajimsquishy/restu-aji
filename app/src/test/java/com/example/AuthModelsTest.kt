package com.example

import com.example.domain.auth.AuthSession
import com.example.domain.auth.AuthValidator
import com.example.domain.auth.PasswordHasher
import com.example.domain.auth.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthModelsTest {

    @Test
    fun passwordHasher_usesUniqueSaltAndVerifiesCorrectly() {
        val hashA = PasswordHasher.hashPassword("GuruTP2026!")
        val hashB = PasswordHasher.hashPassword("GuruTP2026!")

        assertNotEquals("Salted hashes must differ even for identical passwords", hashA, hashB)
        assertTrue(PasswordHasher.verifyPassword("GuruTP2026!", hashA))
        assertTrue(PasswordHasher.verifyPassword("GuruTP2026!", hashB))
        assertFalse(PasswordHasher.verifyPassword("salah123", hashA))
        assertFalse(PasswordHasher.verifyPassword("", hashA))
        assertFalse(PasswordHasher.verifyPassword("GuruTP2026!", "corrupted-hash"))
    }

    @Test
    fun studentValidation_enforcesNamaAbsensiAndKelas() {
        assertNotNull(AuthValidator.validateStudentLogin("", "12", "X TP 1"))
        assertNotNull(AuthValidator.validateStudentLogin("A", "12", "X TP 1"))
        assertNotNull(AuthValidator.validateStudentLogin("Budi Santoso", "0", "X TP 1"))
        assertNotNull(AuthValidator.validateStudentLogin("Budi Santoso", "61", "X TP 1"))
        assertNotNull(AuthValidator.validateStudentLogin("Budi Santoso", "abc", "X TP 1"))
        assertNotNull(AuthValidator.validateStudentLogin("Budi Santoso", "12", ""))

        assertNull(AuthValidator.validateStudentLogin("Budi Santoso", "12", "X TP 1"))
    }

    @Test
    fun teacherValidation_enforcesKelasDiajarAndPasswordRules() {
        assertNotNull(AuthValidator.validateTeacherSetup("", "rahasia123", "rahasia123"))
        assertNotNull(AuthValidator.validateTeacherSetup("X TP 1", "12345", "12345"))
        assertNotNull(AuthValidator.validateTeacherSetup("X TP 1", "rahasia123", "berbeda123"))
        assertNull(AuthValidator.validateTeacherSetup("X TP 1", "rahasia123", "rahasia123"))

        assertNotNull(AuthValidator.validateTeacherLogin("", "rahasia123"))
        assertNotNull(AuthValidator.validateTeacherLogin("X TP 1", ""))
        assertNull(AuthValidator.validateTeacherLogin("X TP 1", "rahasia123"))
    }

    @Test
    fun authSession_formatsStudentAndTeacherIdentifiersCleanly() {
        val student = AuthSession(
            role = UserRole.STUDENT,
            studentName = "Rizky Pratama",
            studentAttendance = "7",
            className = "X TP 2"
        )
        assertTrue(student.isAuthenticated)
        assertEquals("X TP 2 | No.07 | Rizky Pratama", student.participantIdentifier)
        assertEquals("Absen 07 • X TP 2", student.compactBadge)

        val teacher = AuthSession(
            role = UserRole.TEACHER,
            className = "X TP 1"
        )
        assertTrue(teacher.isAuthenticated)
        assertEquals("GURU | X TP 1", teacher.participantIdentifier)
        assertEquals("Guru • X TP 1", teacher.compactBadge)

        val firebaseTeacher = AuthSession(
            role = UserRole.TEACHER,
            className = "X TP 2",
            teacherUid = "uid_123",
            teacherFullName = "Budi Santoso, S.Pd",
            teacherEmail = "budi@smk.sch.id"
        )
        assertTrue(firebaseTeacher.isAuthenticated)
        assertEquals("Budi Santoso, S.Pd • X TP 2", firebaseTeacher.displayIdentity)
        assertEquals("GURU | Budi Santoso, S.Pd | X TP 2", firebaseTeacher.participantIdentifier)

        assertFalse(AuthSession().isAuthenticated)
    }

    @Test
    fun activationCodeGenerator_producesUniqueValidHashed7DayCodes() {
        val generated = (1..200).map { com.example.domain.auth.ActivationCodeGenerator.generateCode() }.toSet()
        assertEquals("All 200 generated codes must be unique", 200, generated.size)

        generated.forEach { code ->
            assertTrue("Code must match GURU-XXXX-XXXX: $code", com.example.domain.auth.ActivationCodeGenerator.isValidFormat(code))
            val hash = com.example.domain.auth.ActivationCodeGenerator.sha256Hex(code)
            assertEquals(64, hash.length)
            assertTrue(hash.matches(Regex("^[a-f0-9]{64}$")))
            assertTrue(com.example.domain.auth.ActivationCodeGenerator.maskedHint(code).startsWith("GURU-****-"))
        }

        // Normalization & deterministic SHA-256 hashing
        val normalized = com.example.domain.auth.ActivationCodeGenerator.normalizeCode(" guru 8k2p 9m4x ")
        assertEquals("GURU-8K2P-9M4X", normalized)
        assertTrue(com.example.domain.auth.ActivationCodeGenerator.isValidFormat(normalized))
        assertEquals(
            com.example.domain.auth.ActivationCodeGenerator.sha256Hex("GURU-8K2P-9M4X"),
            com.example.domain.auth.ActivationCodeGenerator.sha256Hex("guru-8k2p-9m4x")
        )
        assertFalse(com.example.domain.auth.ActivationCodeGenerator.isValidFormat("INVALID-123"))

        // 7-day expiry check
        val nowMs = 1_700_000_000_000L
        val expMs = com.example.domain.auth.ActivationCodeGenerator.computeExpiryMillis(nowMs)
        assertEquals(7L * 24L * 60L * 60L * 1000L, expMs - nowMs)
    }
}

