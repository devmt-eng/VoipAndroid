package io.phone.build.voiplib.config

import io.phone.build.voiplib.model.Codec
import io.phone.build.voiplib.model.Codec.*
import io.phone.build.voiplib.repository.initialize.CallListener
import io.phone.build.voiplib.repository.initialize.LogListener
import io.phone.build.voipsdkandroid.logging.LogManager
import io.phone.build.voipsdkandroid.service.LicenceRequest

typealias GlobalStateCallback = () -> Unit

data class Config(
        val callListener: CallListener = object : CallListener {},
        val stun: String? = null,
        val ring: String? = null,
        val logListener: LogListener? = null,
        val codecs: Array<Codec> = arrayOf(
                G722,
                G729,
                GSM,
                ILBC,
                ISAC,
                L16,
                OPUS,
                PCMA,
                PCMU,
                SPEEX,
        ),
        val userAgent: String = "AndroidVoIPLib",
        val onReady: GlobalStateCallback = {},
        val onDestroy: GlobalStateCallback = {},
        val onLicence: suspend (licenceKey: String, accessToken: String) -> Boolean = { _, _ -> true }
)