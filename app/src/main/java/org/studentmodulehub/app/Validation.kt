package org.studentmodulehub.app

internal object Validation {
    fun auth(name: String, email: String, password: String, registration: Boolean): String = when {
        registration && (name.isBlank() || name.length > 120) -> "Enter your name (up to 120 characters)."
        !email.contains("@") -> "Enter a valid email address."
        password.isBlank() -> "Enter your password."
        registration && password.length < 8 -> "Use at least 8 characters for your password."
        else -> ""
    }
}