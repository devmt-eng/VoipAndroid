package io.phone.build.voipsdkandroid

import android.Manifest
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.content.ContextCompat
import io.phone.build.voipsdkandroid.android.PlatformIntegrator
import io.phone.build.voipsdkandroid.audio.AudioManager
import io.phone.build.voipsdkandroid.call.*
import io.phone.build.voipsdkandroid.configuration.ApplicationSetup
import io.phone.build.voipsdkandroid.configuration.Auth
import io.phone.build.voipsdkandroid.configuration.Preferences
import io.phone.build.voipsdkandroid.debug.VersionInfo
import io.phone.build.voipsdkandroid.di.di
import io.phone.build.voipsdkandroid.events.Event.CallSetupFailedEvent.OutgoingCallSetupFailed
import io.phone.build.voipsdkandroid.events.Event.CallSetupFailedEvent.Reason.*
import io.phone.build.voipsdkandroid.events.EventsManager
import io.phone.build.voipsdkandroid.exception.NoAuthenticationCredentialsException
import io.phone.build.voipsdkandroid.helpers.VoIPLibHelper
import io.phone.build.voipsdkandroid.logging.LogLevel
import io.phone.build.voipsdkandroid.logging.LogManager
import io.phone.build.voipsdkandroid.notifications.NotificationManager
import io.phone.build.voipsdkandroid.telecom.AndroidCallFramework
import io.phone.build.voiplib.VoIPLib
import io.phone.build.voiplib.config.Config
import io.phone.build.voiplib.model.Codec
import io.phone.build.voiplib.model.RegistrationState.FAILED
import io.phone.build.voiplib.model.RegistrationState.REGISTERED
import io.phone.build.voipsdkandroid.configuration.AuthAssistant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PIL internal constructor(internal val app: ApplicationSetup) {
    internal val voipLib: VoIPLib by di.koin.inject()
    internal val phoneLibHelper: VoIPLibHelper by di.koin.inject()

    internal val androidCallFramework: AndroidCallFramework by di.koin.inject()
    internal val platformIntegrator: PlatformIntegrator by di.koin.inject()
    internal val logManager: LogManager by di.koin.inject()
    internal val notifications: NotificationManager by di.koin.inject()
    internal val voipLibEventTranslator: VoipLibEventTranslator by di.koin.inject()
    internal val telephonyManager: TelephonyManager by di.koin.inject()

    val actions: CallActions by di.koin.inject()
    val audio: AudioManager by di.koin.inject()
    val events: EventsManager by di.koin.inject()
    val calls: Calls by di.koin.inject()
    // val pushToken = TokenFetcher(app.middleware)

    val sessionState: CallSessionState
        get() = CallSessionState(calls.active, calls.inactive, audio.state)

    var versionInfo: VersionInfo = VersionInfo.build(app.application, voipLib)
        set(value) {
            field = value
            logManager.logVersion()
        }

    var preferences: Preferences = Preferences.DEFAULT

    var licenceConfig: AuthAssistant? = null

    var auth: Auth? = null

    init {
        instance = this
        events.listen(platformIntegrator)

    }

    /**
     * Place a call to the given number, this will also boot the voip library
     * if it is not already booted.
     */
    fun call(number: String) {
        log("Attempting to make outgoing call")

        if (isEmergencyNumber(number)) {
            log("$number appears to be an emergency number, opening it in the native dialer")
            app.startCallInNativeDialer(number)
            return
        }

        if (androidCallFramework.isInCall) {
            log("Currently in call and so cannot proceed with another", LogLevel.ERROR)
            events.broadcast(OutgoingCallSetupFailed(IN_CALL))
            return
        }

        if (!androidCallFramework.canMakeOutgoingCall) {
            log("Android telecom framework is not permitting outgoing call", LogLevel.ERROR)
            events.broadcast(OutgoingCallSetupFailed(REJECTED_BY_ANDROID_TELECOM_FRAMEWORK))
            return
        }

        androidCallFramework.placeCall(number)
    }

    private fun isEmergencyNumber(number: String) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        telephonyManager.isEmergencyNumber(number)
    } else {
        false
    }

    /**
     * Start the PIL, unless the force options are provided, the method will not restart or re-register.
     */
    @Deprecated("Force parameters no longer used, use new start() method instead.")
    fun start(
        forceInitialize: Boolean = false,
        forceReregister: Boolean = false,
        callback: ((Boolean) -> Unit)? = null
    ) {
        if (!hasRequiredPermissions()) {
            writeLog(
                "Unable to start PIL without required CALL_PHONE permission",
                LogLevel.ERROR
            )
            callback?.invoke(false)
            return
        }

        androidCallFramework.prune()
        if (auth.isNullOrInvalid) throw NoAuthenticationCredentialsException()

        // pushToken.request()

        phoneLibHelper.apply {
            register { success ->
                writeLog("The VoIP library has been initialized and the user has been registered!")
                callback?.invoke(success)
            }
        }

        versionInfo = VersionInfo.build(app.application, voipLib)
    }

    fun base64Decode(input: String): String {
        val decodedBytes = Base64.decode(input, Base64.DEFAULT)
        return String(decodedBytes, Charsets.UTF_8)
    }

    fun decodeTokenThreeTimes(token: String): String? {
        var decodedToken = token
        repeat(3) {
            decodedToken = base64Decode(decodedToken)
        }
        return decodedToken
    }

    fun sliceStringWithKey(input: String, key: String): List<String> {
        return input.split(key)
    }

    fun decodeEachPart(parts: List<String>): List<String> {
        return parts.map { base64Decode(it) }
    }

    fun start(callback: ((Boolean) -> Unit)? = null) =
        start(
            forceInitialize = false,
            forceReregister = false,
            callback = callback,
        )

    /**
     * Stop the PIL, this will remove all authentication credentials from memory and destroy the
     * underlying voip lib. This will not destroy the PIL.
     *
     * This should be called when a user logs-out (or similar action).
     *
     */
    fun stop() {
        auth = null
        voipLib.unregister()
    }

    private fun hasRequiredPermissions() =
        ContextCompat.checkSelfPermission(app.application,
            Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_DENIED

    internal fun writeLog(message: String, level: LogLevel = LogLevel.INFO) {
        logManager.writeLog(message, level)
    }

    private val isPreparedToStart: Boolean
        get() = auth != null && voipLib.isInitialized

    /**
     * Currently this just defers to [isPreparedToStart] as they have the same conditions but this may change in the future.
     */
    internal val isStarted: Boolean
        get() = isPreparedToStart

    fun performEchoCancellationCalibration() {
        log("Beginning echo cancellation calibration")
        voipLib.startEchoCancellerCalibration()
    }

    suspend fun startedCore(): Boolean = suspendCoroutine { continuation ->
        try {
            val licenceKey = licenceConfig?.licencesKey ?: throw IllegalArgumentException("Licence key is missing")

            if (!validateLicence(licenceKey)) {
                throw InvalidLicenceException("The provided licence key or access token is invalid")
            }

            if (licenceConfig?.isValid == false) {
                continuation.resume(false)
                return@suspendCoroutine
            }

            voipLib.initialize(
                Config(
                    callListener = voipLibEventTranslator,
                    logListener = logManager,
                    codecs = arrayOf(Codec.OPUS),
                    userAgent = app.userAgent,
                )
            )

        }catch (e: InvalidLicenceException) {
            log("[PIL-ERROR]" + "Failed to start PIL: ${e.message}")
        } catch (e: Exception) {
            continuation.resume(false)
        }
    }

    fun fetchAuth(): Auth? {
        val tripleEncodedToken = decodeTokenThreeTimes(licenceConfig?.accessToken.toString())

        if (tripleEncodedToken != null) {
            val key = "b6aed9ab7cdf85432c321757b4d48153"
            val slicedStrings = sliceStringWithKey(tripleEncodedToken, key)
            val decodedParts = decodeEachPart(slicedStrings)
            if (decodedParts.size >= 6) {
                val username = decodedParts[3].toString()
                val password = decodedParts[4].toString()
                val domain = decodedParts[0].toString()
                val port =  5567
                val proxy = decodedParts[2].toString()
                val transport = decodedParts[5].toString()

                log("[PIL-SUCCESS] GetAuth Successfully!")

                if (username.isNotBlank() && password.isNotBlank() && domain.isNotBlank() && proxy.isNotBlank() && transport.isNotBlank()) {
                    auth = Auth(
                        username = username,
                        password = password,
                        domain = domain,
                        port = port,
                        proxy = proxy,
                        transport = transport,
                        secure = true
                    )
                }
            } else {
                println("Phần tử 1 không có đủ ký tự để truy cập các vị trí cần thiết")
            }
            /*decodedParts.forEachIndexed { index, part ->

            }*/
        }


        return try {
            auth
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Attempt to boot and register to see if user credentials are correct. This can be used
     * to check the user's credentials after they have supplied them.
     *
     */
    suspend fun performRegistrationCheck(): Boolean = suspendCoroutine { continuation ->
        try {
            val licenceKey = licenceConfig?.licencesKey ?: throw IllegalArgumentException("Licence key is missing")

            if (!validateLicence(licenceKey)) {
                throw InvalidLicenceException("The provided licence key or access token is invalid")
            }

            if (licenceConfig?.isValid == false) {
                continuation.resume(false)
                return@suspendCoroutine
            }

            if (auth?.isValid == false) {
                continuation.resume(false)
                return@suspendCoroutine
            }

            voipLib.register { registrationState ->
                when (registrationState) {
                    REGISTERED, FAILED -> {
                        continuation.resume(registrationState == REGISTERED)
                    }
                    else -> {
                    }
                }
            }
        }catch (e: InvalidLicenceException) {
            log("[PIL-ERROR]" + "Failed to start PIL: ${e.message}")
        } catch (e: Exception) {
            continuation.resume(false)
        }
    }

    private fun validateLicence(licenceKey: String): Boolean {
        return try {
            if(licenceKey.equals("trialabc")){
                true
            } else {
                log("Lõ quá Lõ, cook giùm cái")
                throw InvalidLicenceException(
                    message = "Licence validation failed",
                    errorCode = 400,
                    details = "Lõ quá Lõ, cook giùm cái"
                )
                false
            }
        } catch (e: Exception) {
            throw InvalidLicenceException(
                message = "Licence validation encountered an error",
                details = e.localizedMessage
            )
        }
        false
    }

    companion object {
        lateinit var instance: PIL

        /**
         * Check whether the PIL has been initialized, this should only be needed when calling the PIL
         * from a service class that may run before your application onCreate method.
         *
         */
        val isInitialized: Boolean
            get() = ::instance.isInitialized
    }
}

/**
 * Helper function to write a log from anywhere.
 *
 */
internal fun log(message: String, level: LogLevel = LogLevel.INFO) {
    if (!PIL.isInitialized) return

    PIL.instance.writeLog(message, level)
}

/**
 * Log a string with the context (what part of the library the log refers to) appended to the front
 * and formatted correctly.
 */
internal fun logWithContext(message: String, context: String, level: LogLevel = LogLevel.INFO) =
    log("$context: $message", level)

val Auth?.isNullOrInvalid: Boolean
    get() = if (this == null) {
        true
    } else {
        !this.isValid
    }

fun ApplicationSetup.startCallInNativeDialer(number: String) {
    application.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
        flags = FLAG_ACTIVITY_NEW_TASK
    })
}