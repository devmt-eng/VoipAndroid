package io.phone.build.voipsdkandroid.helpers

import android.app.Application
import android.content.Context
import android.content.Intent
import io.phone.build.voipsdkandroid.android.ApplicationStateListener
import io.phone.build.voipsdkandroid.PIL
import io.phone.build.voipsdkandroid.configuration.ApplicationSetup.AutomaticallyLaunchCallActivity.NEVER
import io.phone.build.voipsdkandroid.configuration.ApplicationSetup.AutomaticallyLaunchCallActivity.ONLY_FROM_BACKGROUND

fun Context.startCallActivity() {
    val automaticallyLaunchCallActivity = PIL.instance.app.automaticallyLaunchCallActivity

    if (automaticallyLaunchCallActivity == NEVER
        || (automaticallyLaunchCallActivity == ONLY_FROM_BACKGROUND && ApplicationStateListener.isInForeground)) return

    PIL.instance.app.application.startActivity(
        Intent(PIL.instance.app.application, PIL.instance.app.activities.call).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )
}

val Application.isInForeground
    get() = ApplicationStateListener.isInForeground