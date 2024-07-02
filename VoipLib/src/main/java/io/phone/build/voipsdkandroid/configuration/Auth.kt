package io.phone.build.voipsdkandroid.configuration

data class Auth(
    val username: String,
    val password: String,
    val domain: String,
    val port: Int,
    val proxy: String,
    val transport: String,
    val secure: Boolean
) {
    val isValid: Boolean
        get() = username.isNotBlank() && password.isNotBlank() && domain.isNotBlank() && port != 0 && proxy.isNotBlank() && transport.isNotBlank()
}
