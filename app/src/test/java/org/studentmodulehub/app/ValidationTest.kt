package org.studentmodulehub.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun registrationRequiresName() {
        assertTrue(Validation.auth(" ", "a@b.test", "12345678", true).isNotEmpty())
    }

    @Test fun registrationRequiresEightCharacterPassword() {
        assertTrue(Validation.auth("Student", "a@b.test", "1234567", true).isNotEmpty())
    }

    @Test fun loginRequiresValidEmail() {
        assertTrue(Validation.auth("", "not-an-email", "password", false).isNotEmpty())
    }

    @Test fun emailValidationRejectsMissingDomain() {
        assertTrue(!Validation.email("student@"))
    }

    @Test fun emailValidationAcceptsTrimmedAddress() {
        assertTrue(Validation.email(" student@example.test "))
    }

    @Test fun validRegistrationPasses() {
        assertEquals("", Validation.auth("Student", "student@example.test", "12345678", true, "12345678"))
    }

    @Test fun registrationRequiresMatchingPasswords() {
        assertTrue(Validation.auth("Student", "student@example.test", "12345678", true, "87654321").isNotEmpty())
    }

    @Test fun loginDoesNotEnforceRegistrationPasswordMinimum() {
        assertEquals("", Validation.auth("", "a@b.test", "abc", false))
    }

    @Test fun emailIsTrimmedForValidation() {
        assertEquals("", Validation.auth("Student", " student@example.test ", "12345678", true, "12345678"))
    }
}