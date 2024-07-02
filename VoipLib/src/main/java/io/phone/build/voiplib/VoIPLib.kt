package io.phone.build.voiplib

import android.Manifest.permission.RECORD_AUDIO
import androidx.annotation.RequiresPermission
import org.linphone.core.Factory
import io.phone.build.voipsdkandroid.di.di
import io.phone.build.voiplib.config.Config
import io.phone.build.voiplib.model.Call
import io.phone.build.voiplib.model.RegistrationState
import io.phone.build.voiplib.repository.MiFoneCoreInstanceManager
import io.phone.build.voiplib.repository.call.controls.MiFoneSipActiveCallControlsRepository
import io.phone.build.voiplib.repository.call.session.MiFoneSipSessionRepository
import io.phone.build.voiplib.repository.registration.MiFoneSipRegisterRepository

typealias RegistrationCallback = (RegistrationState) -> Unit

class VoIPLib {
    private val sipRegisterRepository: MiFoneSipRegisterRepository by di.koin.inject()
    private val sipCallControlsRepository: MiFoneSipActiveCallControlsRepository by di.koin.inject()
    private val sipSessionRepository: MiFoneSipSessionRepository by di.koin.inject()
    private val mifoneCoreInstanceManager: MiFoneCoreInstanceManager by di.koin.inject()

    /**
     * This needs to be called whenever this library needs to initialize. Without it, no other calls
     * can be done.
     */
    fun initialize(config: Config): VoIPLib {
        Factory.instance()
        mifoneCoreInstanceManager.initializeMiFone(config)
        return this
    }

    /**
     * Check to see if the phonelib is initialized and ready to make calls.
     *
     */
    val isInitialized: Boolean
        get() = mifoneCoreInstanceManager.state.initialized

    val isNetworkReachable
        get() = mifoneCoreInstanceManager.safeLinphoneCore?.isNetworkReachable ?: false

    /**
     * This registers your user on SIP. You need this before placing a call.
     *
     */
    fun register(callback: RegistrationCallback): VoIPLib {
        sipRegisterRepository.register(callback)
        return this
    }

    /**
     * This unregisters your user on SIP.
     */
    fun unregister() = sipRegisterRepository.unregister()

    /**
     * This method audio calls a phone number
     * @param number the number dialed to
     * @return returns true when call succeeds, false when the number is an empty string or the
     * phone service isn't ready.
     */
    @RequiresPermission(RECORD_AUDIO)
    fun callTo(number: String) = sipSessionRepository.callTo(number)

    /**
     * A method that should be called when the application has received a push message and
     * is waking from the background.
     *
     */
    fun wake() = mifoneCoreInstanceManager.safeLinphoneCore?.ensureRegistered()

    /**
     * Whether or not the microphone is currently muted.
     *
     * Set to TRUE to mute the microphone and prevent voice transmission.
     */
    var microphoneMuted
            get() = sipCallControlsRepository.isMicrophoneMuted()
            set(muted) = sipCallControlsRepository.setMicrophone(!muted)

    /**
     * Perform actions on the given call.
     *
     */
    fun actions(call: Call) = Actions(call)

    /**
     * Return the current version of the underlying voip library.
     *
     */
    val version
        get() = mifoneCoreInstanceManager.safeLinphoneCore?.version ?: ""

    fun refreshRegistration() {
        mifoneCoreInstanceManager.safeLinphoneCore?.refreshRegisters()
    }

    fun startEchoCancellerCalibration() {
        mifoneCoreInstanceManager.safeLinphoneCore?.startEchoCancellerCalibration()
    }
}