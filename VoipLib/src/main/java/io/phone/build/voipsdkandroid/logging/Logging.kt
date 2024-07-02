package io.phone.build.voipsdkandroid.logging

fun interface Logger {
    fun onLogReceived(message: String, level: LogLevel)
}
