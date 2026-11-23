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
    fun classTeacherAuth_verifiesExactClassPasswordsAndRejectsCrossClassOrWrongPasswords() {
        val validPairs = listOf(
            "X TP 1" to "MLAB-XTP1-4829",
            "X TP 2" to "MLAB-XTP2-7394",
            "X TP 3" to "MLAB-XTP3-6158",
            "X TP 4" to "MLAB-XTP4-9273",
            "Semua Kelas X TP" to "MLAB-KOOR-5046"
        )

        validPairs.forEach { (cls, pwd) ->
            assertTrue("Expected supported class $cls", com.example.domain.auth.ClassTeacherAuth.isSupportedClass(cls))
            assertTrue("Expected valid password for $cls", com.example.domain.auth.ClassTeacherAuth.verifyClassPassword(cls, pwd))
            assertFalse("Wrong password must fail for $cls", com.example.domain.auth.ClassTeacherAuth.verifyClassPassword(cls, "SALAH-1234"))
            assertFalse("Empty password must fail for $cls", com.example.domain.auth.ClassTeacherAuth.verifyClassPassword(cls, ""))
        }

        // Cross-class isolation: password for X TP 1 must NOT unlock X TP 2
        assertFalse(com.example.domain.auth.ClassTeacherAuth.verifyClassPassword("X TP 2", "MLAB-XTP1-4829"))
        assertFalse(com.example.domain.auth.ClassTeacherAuth.verifyClassPassword("X TP 1", "MLAB-XTP2-7394"))
        assertFalse(com.example.domain.auth.ClassTeacherAuth.verifyClassPassword("Kelas Tidak Ada", "MLAB-XTP1-4829"))
    }
}

