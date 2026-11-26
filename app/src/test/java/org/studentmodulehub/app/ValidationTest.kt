package org.studentmodulehub.app
import org.junit.Assert.*
import org.junit.Test
class ValidationTest {
    @Test fun registrationRequiresName() { assertTrue(Validation.auth(" ", "a@b.test", "12345678", true).isNotEmpty()) }
    @Test fun registrationRequiresEightCharacterPassword() { assertTrue(Validation.auth("Student", "a@b.test", "1234567", true).isNotEmpty()) }
    @Test fun emailRequiredForLogin() { assertTrue(Validation.auth("", "invalid", "password", false).isNotEmpty()) }
    @Test fun validRegistrationPasses() { assertEquals("", Validation.auth("Student", "student@example.test", "12345678", true)) }
    @Test fun loginDoesNotEnforceRegistrationMinimum() { assertEquals("", Validation.auth("", "a@b.test", "abc", false)) }
}