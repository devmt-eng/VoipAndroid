package io.phone.build.voiplib.repository.call.session

import org.linphone.core.Address
import org.linphone.core.CoreException
import org.linphone.core.Reason
import io.phone.build.voipsdkandroid.PIL
import io.phone.build.voiplib.model.Call
import io.phone.build.voiplib.repository.MiFoneCoreInstanceManager
import org.linphone.core.Call as LinphoneCall

internal class MiFoneSipSessionRepository(private val pil: PIL, private val mifoneCoreInstanceManager: MiFoneCoreInstanceManager) {

    fun acceptIncoming(call: Call) {
        try {
            call.libCall.accept()
        } catch (e: CoreException) {
            e.printStackTrace()
        }
    }

    fun declineIncoming(call: Call, reason: Reason) {
        try {
            call.libCall.decline(reason)
        } catch (e: CoreException) {
            e.printStackTrace()
        }
    }

    fun callTo(number: String): Call {
        if (!mifoneCoreInstanceManager.state.initialized) {
            throw Exception("MiFone is not ready")
        }

        if (number.isEmpty()) {
            throw IllegalArgumentException("Phone number is not valid")
        }

        val auth = pil.auth ?: throw IllegalArgumentException("No authentication")

        val connectionParameters = ConnectionParameters(number, auth.domain)

        return Call(callTo(connectionParameters) ?: throw Exception("Call failed"))
    }

    private fun callTo(connectionParameters: ConnectionParameters) : LinphoneCall? {
        val core = mifoneCoreInstanceManager.safeLinphoneCore ?: return null

        val address: Address = try {
            core.interpretUrl(connectionParameters.asUrl())!!
        } catch (e: CoreException) {
            e.printStackTrace()
            return null
        }

        val params = core.createCallParams(null)?.apply {
            isVideoEnabled = false
        } ?: return null

        return core.inviteAddressWithParams(address, params)
    }

    fun end(call: Call) {
        call.libCall.terminate()
        if (mifoneCoreInstanceManager.safeLinphoneCore?.isInConference == true) {
            mifoneCoreInstanceManager.safeLinphoneCore?.terminateConference()
        }
    }

    data class ConnectionParameters(
            val username: String,
            val host: String
    ) {
        fun asUrl() = "$username@$host"
    }
}