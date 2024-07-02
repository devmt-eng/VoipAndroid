package io.phone.build.voiplib.repository.registration

import org.linphone.core.*
import io.phone.build.voipsdkandroid.PIL
import io.phone.build.voipsdkandroid.configuration.Auth
import io.phone.build.voipsdkandroid.logWithContext
import io.phone.build.voipsdkandroid.logging.LogLevel
import io.phone.build.voipsdkandroid.logging.LogLevel.ERROR
import io.phone.build.voipsdkandroid.logging.LogLevel.INFO
import io.phone.build.voiplib.RegistrationCallback
import io.phone.build.voiplib.model.RegistrationState.FAILED
import io.phone.build.voiplib.model.RegistrationState.REGISTERED
import io.phone.build.voiplib.repository.MiFoneCoreInstanceManager
import io.phone.build.voiplib.repository.SimpleCoreListener
import java.util.*
import kotlin.concurrent.schedule

internal class MiFoneSipRegisterRepository(
    private val pil: PIL,
    private val mifoneCoreInstanceManager: MiFoneCoreInstanceManager,
) {
    private val registrationListener = RegistrationListener()

    private var callback: RegistrationCallback? = null

    /**
     * We're going to store the auth object that we used to authenticate with successfully, so we
     * know we need to re-register if it has changed.
     */
    private var lastRegisteredCredentials: Auth? = null

    @Throws(CoreException::class)
    fun register(callback: RegistrationCallback) {
        val core = mifoneCoreInstanceManager.safeLinphoneCore ?: run {
            log("Unable to register, no linphone core", ERROR)
            callback(FAILED)
            return
        }

        val auth = pil.auth ?: run {
            log("Unable to register with no auth", ERROR)
            callback(FAILED)
            return
        }

        if (lastRegisteredCredentials != pil.auth && lastRegisteredCredentials != null) {
            log("Auth appears to have changed, unregistering old.")
            unregister()
        }

        core.apply {
            removeListener(registrationListener)
            addListener(registrationListener)
        }

        this.callback = callback

        if (core.accountList.isNotEmpty()) {
            log("SIP account not found, re-registering.")
            core.refreshRegisters()
            return
        }

        log("No SIP account found, registering for the first time.")

        if (auth.port < 1 || auth.port > 65535) {
            throw IllegalArgumentException("Unable to register with a server when port is invalid: ${auth.port}")
        }

        val account = createAccount(core, auth)

        if (core.addAccount(account) == -1) {
            this.callback = null
            callback(FAILED)
            return
        }

        core.apply {
            addAuthInfo(createAuthInfo(auth))
            defaultAccount = account
        }

        lastRegisteredCredentials = auth
    }

    private fun createAuthInfo(auth: Auth) =
        Factory.instance().createAuthInfo(
            auth.username,
            auth.username,
            auth.password,
            null,
            null,
            auth.domain,
        )

    private fun createAccount(
        core: Core,
        auth: Auth
    ): Account = core.createAccount(
        core.createAccountParams().apply {
            val authInfo =
                Factory.instance().createAuthInfo(auth.username, null, auth.password, null, null, auth.domain, null)

            // Account object replaces deprecated ProxyConfig object
            // Account object is configured through an AccountParams object that we can obtain from the Core
            val accountParams = core.createAccountParams()

            val identity = Factory.instance().createAddress("sip:${auth.username}@${auth.domain}")
            accountParams.identityAddress = identity
            // We also need to configure where the proxy server is located

            val address = Factory.instance().createAddress("sip:${auth.proxy}:${auth.port};transport=${auth.transport}")
            // We use the Address object to easily set the transport protocol
            address?.transport = TransportType.Tls
            address?.port = auth.port
            accountParams.serverAddress = address
            // And we ensure the account will start the registration process
            accountParams.isRegisterEnabled = true

            // Now that our AccountParams is configured, we can create the Account object
            val account = core.createAccount(accountParams)
            // Now let's add our objects to the Core
            core.addAuthInfo(authInfo)
            core.addAccount(account)
        }
    )

    fun unregister() =  mifoneCoreInstanceManager.safeLinphoneCore?.apply {
        clearAccounts()
        clearAllAuthInfo()
    }.also {
        log("Unregister complete")
    }

    private inner class RegistrationListener : SimpleCoreListener {

        /**
         * It is sometimes possible that a failed registration will occur before a successful one
         * so we will track the time of the first registration update before determining it has
         * failed.
         */
        private var startTime: Long? = null

        private val currentTime: Long
            get() = System.currentTimeMillis()

        private var timer: Timer? = null
            set(timer) {
                timer?.cancel()
                timer?.purge()
                field = timer
            }

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String,
        ) {
            log("State change: ${state?.name} - $message")

            val callback = this@MiFoneSipRegisterRepository.callback ?: run {
                log("Callback not set so registration state change has not done anything.")
                reset()
                return
            }

            // If the registration was successful, just immediately invoke the callback and reset
            // all timers.
            if (state == RegistrationState.Ok) {
                log("Successful, resetting timers.")
                this@MiFoneSipRegisterRepository.callback = null
                callback.invoke(REGISTERED)
                reset()
                return
            }

            // If there is no start time, we want to set it to begin the timer.
            val startTime = this.startTime ?: run {
                val startTime = currentTime
                this.startTime = startTime
                log("Started registration timer: $startTime.")
                startTime
            }

            if (hasExceededTimeout(startTime)) {
                this@MiFoneSipRegisterRepository.callback = null
                unregister()
                log("Registration timeout has been exceeded, registration failed.", ERROR)
                callback.invoke(FAILED)
                reset()
                return
            }

            // Queuing call of this method so we ensure that the callback is eventually invoked
            // even if there are no future registration updates.
            timer = Timer("Registration").also {
                it.schedule(cleanUpDelay) {
                    onAccountRegistrationStateChanged(
                        core,
                        account,
                        state,
                        "Automatically called to ensure callback is executed"
                    )
                }
            }
        }

        /*override fun onSubscribeReceived(
            core: Core,
            linphoneEvent: Event,
            subscribeEvent: String,
            body: Content
        ) {
            TODO("Not yet implemented")
        }

        override fun onConferenceInfoOnParticipantError(
            core: Core,
            conferenceInfo: ConferenceInfo,
            participant: Address,
            error: ConferenceInfoError?
        ) {
            TODO("Not yet implemented")
        }

        override fun onConferenceInfoOnSent(core: Core, conferenceInfo: ConferenceInfo) {
            TODO("Not yet implemented")
        }

        override fun onConferenceInfoOnParticipantSent(
            core: Core,
            conferenceInfo: ConferenceInfo,
            participant: Address
        ) {
            TODO("Not yet implemented")
        }*/

        override fun onCallReceiveMasterKeyChanged(core: Core, call: Call, masterKey: String?) {
            log("onCallReceiveMasterKeyChanged: Not implemented")
        }

        override fun onPushNotificationReceived(core: Core, payload: String?) {
            TODO("Not yet implemented")
        }

        override fun onConferenceInfoReceived(core: Core, conferenceInfo: ConferenceInfo) {
            TODO("Not yet implemented")
        }

        override fun onCallSendMasterKeyChanged(core: Core, call: Call, masterKey: String?) {
            log("onCallSendMasterKeyChanged: Not implemented")
        }

        override fun onMessagesReceived(
            core: Core,
            chatRoom: ChatRoom,
            messages: Array<out ChatMessage>
        ) {
            TODO("Not yet implemented")
        }

        override fun onChatRoomSessionStateChanged(
            core: Core,
            chatRoom: ChatRoom,
            state: Call.State?,
            message: String
        ) {
            log("onChatRoomSessionStateChanged: Not implemented")
        }

        override fun onNotifySent(core: Core, linphoneEvent: Event, body: Content) {
            TODO("Not yet implemented")
        }

        override fun onSubscribeReceived(
            core: Core,
            linphoneEvent: Event,
            subscribeEvent: String,
            body: Content?
        ) {
            log("onSubscribeReceived: Not implemented")
        }

        override fun onPreviewDisplayErrorOccurred(core: Core, errorCode: Int) {
            log("onPreviewDisplayErrorOccurred: Not implemented")
        }

        override fun onCallGoclearAckSent(core: Core, call: Call) {
            TODO("Not yet implemented")
        }

        private fun hasExceededTimeout(startTime: Long): Boolean =
            (startTime + registrationTimeoutMs) < currentTime

        private fun reset() {
            startTime = null
            timer?.apply {
                cancel()
                purge()
            }
        }
    }

    private fun log(message: String, level: LogLevel = INFO) =
        logWithContext(message, "SIP-REGISTER", level)

    companion object {
        /**
         * The amount of time to wait before determining registration has failed.
         */
        const val registrationTimeoutMs = 5000L

        /**
         * The time that we will wait before executing the method again to clean-up.
         */
        const val cleanUpDelay = 1000L
    }
}