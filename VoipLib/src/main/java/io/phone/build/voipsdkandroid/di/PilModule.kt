package io.phone.build.voipsdkandroid.di

import android.app.NotificationManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import io.phone.build.voipsdkandroid.PIL
import io.phone.build.voipsdkandroid.android.PlatformIntegrator
import io.phone.build.voipsdkandroid.audio.AudioManager
import io.phone.build.voipsdkandroid.audio.LocalDtmfToneGenerator
import io.phone.build.voipsdkandroid.call.*
import io.phone.build.voipsdkandroid.call.Calls.Companion.MAX_CALLS
import io.phone.build.voipsdkandroid.configuration.Preferences
import io.phone.build.voipsdkandroid.contacts.Contacts
import io.phone.build.voipsdkandroid.events.EventsManager
import io.phone.build.voipsdkandroid.helpers.VoIPLibHelper
import io.phone.build.voipsdkandroid.logging.LogManager
import io.phone.build.voipsdkandroid.notifications.CallNotification
import io.phone.build.voipsdkandroid.notifications.IncomingCallNotification
import io.phone.build.voipsdkandroid.notifications.IncomingCallRinger
import io.phone.build.voipsdkandroid.telecom.AndroidCallFramework
import io.phone.build.voipsdkandroid.telecom.Connection
import io.phone.build.voiplib.VoIPLib
import io.phone.build.voiplib.repository.Dns
import io.phone.build.voiplib.repository.MiFoneCoreInstanceManager
import io.phone.build.voiplib.repository.call.controls.MiFoneSipActiveCallControlsRepository
import io.phone.build.voiplib.repository.call.session.MiFoneSipSessionRepository
import io.phone.build.voiplib.repository.registration.MiFoneSipRegisterRepository

fun getModules() = listOf(pilModule)

// Resolves the current preferences from the [PIL] that doesn't require depending on the whole
// [PIL] object.
typealias CurrentPreferencesResolver = () -> Preferences

val pilModule = module {

    single {
        AndroidCallFramework(
            androidContext(),
            get(),
            androidContext().getSystemService(TelecomManager::class.java)
        )
    }

    single { CallFactory(get()) }

    single { Contacts(androidContext(), get()) }

    single { PlatformIntegrator(get(), get(), get()) }

    single { PIL.instance }

    factory <CurrentPreferencesResolver>{ {get<PIL>().preferences}  }

    single { VoIPLib() }

    single { CallActions(get(), get(), get(), get()) }

    single { AudioManager(androidContext(), get(), get(), get(), get(), get()) }

    single { EventsManager(get()) }

    single { VoIPLibHelper(get(), get()) }

    factory { Connection(get(), get(), get(), get()) }

    single { Calls(get(), CallArrayList(MAX_CALLS)) }

    single { androidContext().getSystemService(NotificationManager::class.java) }

    single { androidContext().getSystemService(android.media.AudioManager::class.java) }

    single { androidContext().getSystemService(TelephonyManager::class.java) }

    single { IncomingCallNotification(get()) }

    single { IncomingCallRinger(androidContext(), get(), get()) }

    single { CallNotification() }

    single { LocalDtmfToneGenerator(get()) }

    single { LogManager(get()) }

    single { VoipLibEventTranslator(get(), get(), get()) }

    single {
        io.phone.build.voipsdkandroid.notifications.NotificationManager(
            get(),
            get(),
        )
    }

    single { Dns(get()) }
    single { MiFoneCoreInstanceManager(get()) }
    single { MiFoneSipRegisterRepository(get(), get()) }

    single { MiFoneSipActiveCallControlsRepository(get()) }
    single { MiFoneSipSessionRepository(get(), get()) }
}