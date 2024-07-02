package io.phone.build.voipsdkandroid.debug

import android.content.Context
import io.phone.build.voiplib.R
import io.phone.build.voiplib.VoIPLib

data class VersionInfo(
    val pil: String,
    val voip: String
) {
    override fun toString(): String {
        return "Version Info: Android Phone Integration Lib: $pil | Underlying VoIP Library: $voip"
    }

    companion object {
        internal fun build(context: Context, voipLib: VoIPLib) = VersionInfo(
            context.getString(R.string.notification_answer_action),
            if (voipLib.isInitialized) voipLib.version else "Not initialized"
        )
    }
}