package io.phone.build.voipsdkandroid.android

import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.util.Log
import io.phone.build.voipsdkandroid.PIL
import io.phone.build.voipsdkandroid.call.Call
import io.phone.build.voipsdkandroid.call.CallFactory
import io.phone.build.voipsdkandroid.call.VoipLibCall
import io.phone.build.voipsdkandroid.events.Event
import io.phone.build.voipsdkandroid.events.Event.CallSessionEvent.*
import io.phone.build.voipsdkandroid.events.PILEventListener
import io.phone.build.voipsdkandroid.helpers.startCallActivity
import io.phone.build.voipsdkandroid.logging.LogLevel
import io.phone.build.voipsdkandroid.notifications.MissedCallNotification
import io.phone.build.voipsdkandroid.telecom.AndroidCallFramework

internal class PlatformIntegrator(
    private val pil: PIL, private val
    androidCallFramework: AndroidCallFramework,
    private val factory: CallFactory,
) : PILEventListener {

    private fun handle(event: Event.CallSessionEvent, call: Call) = when(event) {

        is IncomingCallReceived -> {
            print("INCOMMING RECEIVED")
            androidCallFramework.addNewIncomingCall(call.remoteNumber)
        }

        is OutgoingCallStarted -> {
            Log.d("[LOGGER-CALL]","OutgoingCallStarted RECEIVED")
            if (androidCallFramework.connection == null) {
                pil.writeLog("There is no connection object!", LogLevel.ERROR)
            }

            androidCallFramework.connection?.apply {
                setCallerDisplayName(
                    pil.calls.active?.remotePartyHeading,
                    TelecomManager.PRESENTATION_ALLOWED
                )
                setDialing()
            }

            pil.app.application.startCallActivity()
        }

        is CallConnected -> {
            pil.app.application.startCallActivity()
        }

        is CallEnded -> {
            pil.audio.unmute()
            androidCallFramework.connection?.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
            androidCallFramework.connection?.destroy()
            androidCallFramework.connection = null
        }

       else -> {}
    }

    internal fun notifyIfMissedCall(call: VoipLibCall) {
        if (call.wasMissed && pil.app.notifyOnMissedCall) {
            factory.make(call)?.let {
                MissedCallNotification().notify(it)
            }
        }
    }

    override fun onEvent(event: Event) {
        pil.notifications.dismissStale()

        if (event !is Event.CallSessionEvent) {
            return
        }

        handle(event, event.state.activeCall ?: return)
    }
}