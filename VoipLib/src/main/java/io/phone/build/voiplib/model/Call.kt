package io.phone.build.voiplib.model

import org.linphone.core.Call.Dir.Incoming
import org.linphone.core.Call.Dir.Outgoing
import io.phone.build.voiplib.repository.PreservedInviteData
import org.linphone.core.Call as MiFoneCall

class Call(val libCall: MiFoneCall) {

    val quality
        get() = Quality(libCall.averageQuality, libCall.currentQuality)

    val state
        get() = CallState.values().firstOrNull { it.name == libCall.state.toString() }
                    ?: CallState.Unknown

    val displayName
        get() = libCall.remoteAddress.displayName ?: ""

    val phoneNumber
        get() = libCall.remoteAddress.username ?: ""

    val duration
        get() = libCall.duration

    /**
     * Check if this is a missed call, this will likely not be valid until the call has been
     * released.
     *
     */
    val wasMissed: Boolean
        get() = try {
            val log = libCall.callLog

            val missedStatuses = arrayOf(
                MiFoneCall.Status.Missed,
                MiFoneCall.Status.Aborted,
                MiFoneCall.Status.EarlyAborted,
            )

            log.dir == Incoming && missedStatuses.contains(log.status)
        } catch (e: NullPointerException) {
            false
        }

    val reason
        get() = libCall.reason.name

    val callId: String
        get() = try {
            libCall.callLog.callId ?: ""
        } catch (e: NullPointerException) {
            "unknown"
        }

    val direction
        get() = when (libCall.callLog.dir) {
            Outgoing -> Direction.OUTGOING
            Incoming -> Direction.INCOMING
            null -> Direction.INCOMING
        }

    val remotePartyId: String
        get() {
            val userData = libCall.userData as? PreservedInviteData ?: return ""

            return userData.remotePartyId ?: ""
        }

    val pAssertedIdentity: String
        get() {
            val userData = libCall.userData as? PreservedInviteData ?: return ""

            return userData.pAssertedIdentity ?: ""
        }

    val isOnHold: Boolean
        get() = when (state) {
            CallState.Paused -> true
            else -> false
        }
}