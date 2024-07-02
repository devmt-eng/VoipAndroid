package io.phone.build.voipsdkandroid

import io.phone.build.voipsdkandroid.audio.AudioState
import io.phone.build.voipsdkandroid.call.Call

data class CallSessionState(
    val activeCall: Call?,
    val inactiveCall: Call?,
    val audioState: AudioState
)