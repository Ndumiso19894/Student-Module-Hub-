package org.studentmodulehub.app

internal object Validation {
    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun email(value: String): Boolean = value.trim().matches(emailPattern)

    fun auth(
        name: String,
        email: String,
        password: String,
        registration: Boolean,
        confirmPassword: String = password
    ): String = when {
        registration && (name.isBlank() || name.trim().length > 120) ->
            "Enter your name (up to 120 characters)."
        !email(email) ->
            "Enter a valid email address."
        password.isBlank() ->
            "Enter your password."
        registration && password.length < 8 ->
            "Use at least 8 characters for your password."
        registration && password != confirmPassword ->
            "The passwords do not match."
        else -> ""
    }
}