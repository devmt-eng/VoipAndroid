package io.phone.build.voipsdkandroid.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.phone.build.voipsdkandroid.call.Calls
import io.phone.build.voipsdkandroid.events.Event
import io.phone.build.voipsdkandroid.events.EventsManager
import io.phone.build.voipsdkandroid.log
import io.phone.build.voipsdkandroid.telecom.AndroidCallFramework
import io.phone.build.voipsdkandroid.telecom.Connection
import io.phone.build.voiplib.VoIPLib
import io.phone.build.voiplib.model.Codec

class AudioManager internal constructor(
    private val context: Context,
    private val phoneLib: VoIPLib,
    private val androidCallFramework: AndroidCallFramework,
    private val events: EventsManager,
    private val audioManager: AudioManager,
    private val calls: Calls,
) {
    var isMicrophoneMuted: Boolean
        get() = phoneLib.microphoneMuted
        private set(value) {
            phoneLib.microphoneMuted = value
            events.broadcast(Event.CallSessionEvent.AudioStateUpdated::class)
        }

    val state: AudioState
        get() = createAudioStateObject()

    /**
     * Route audio to a general type of audio device.
     *
     */
    fun routeAudio(route: AudioRoute) {
        val connection = androidCallFramework.connection

        if (connection == null) {
            log("There is no android call framework connection, unable to route audio")
            return
        }

        connection.setAudioRoute(pilRouteToNativeRoute(route))

        // The echo limiter is a brute-force method to prevent echo, it should only be used
        // when it is really necessary, such as when the user is using the phone's speaker.
        if (route == AudioRoute.SPEAKER) {
            calls.forEach {
                it.libCall.isEchoLimiterEnabled = true
                it.libCall.isEchoCancellationEnabled = false
            }
        } else {
            calls.forEach {
                it.libCall.isEchoLimiterEnabled = false
                it.libCall.isEchoCancellationEnabled = true
            }
        }

        if (route != AudioRoute.SPEAKER) return

        // After changing the audio route to speaker, on some devices, the volume will be set to
        // 0 internally, for some reason. By changing the volume by one, the volume is back.
        Handler(Looper.getMainLooper()).postDelayed(
            {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)

                audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    // There needs to be a volume *change*, otherwise there's no effect.
                    if (currentVolume >= maxVolume) currentVolume - 1 else currentVolume + 1,
                    0
                )
            },
            // A delay is necessary since the volume change under the hood does not
            // happen immediately. A delay less than 1000ms seems to not always work.
            1000
        )
    }

    /**
     *  Route audio to a specific bluetooth device.
     *
     */
    @SuppressLint("MissingPermission")
    fun routeAudio(route: BluetoothAudioRoute) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            log("Android version too low to route audio to specific bluetooth device")
            return
        }

        if (hasBluetoothPermission) {
            log("[BLUETOOTH_CONNECT] is missing, unable to route to specific BT device")
            return
        }

        val connection = androidCallFramework.connection ?: return
        val callAudioState = connection.callAudioState ?: return

        callAudioState.supportedBluetoothDevices?.firstOrNull {
            it.name == route.identifier
        }?.let {
            log("Requesting bluetooth audio be routed to ${it.name}")
            connection.requestBluetoothAudio(it)
        }

        if (state.currentRoute != AudioRoute.BLUETOOTH) {
            routeAudio(AudioRoute.BLUETOOTH)
        }
    }

    fun mute() {
        isMicrophoneMuted = true
    }

    fun unmute() {
        isMicrophoneMuted = false
    }

    fun toggleMute() {
        isMicrophoneMuted = !isMicrophoneMuted
    }

    private fun createAudioStateObject(): AudioState {
        val default = AudioState(
            AudioRoute.PHONE,
            arrayOf(),
            null,
            false,
            arrayOf()
        )

        val connection = androidCallFramework.connection ?: return default

        if (connection.callAudioState == null) {
            return default
        }

        val currentRoute = nativeRouteToPilRoute(connection.callAudioState.route)

        val routes = arrayOf(
            ROUTE_BLUETOOTH,
            ROUTE_EARPIECE,
            ROUTE_WIRED_HEADSET,
            ROUTE_WIRED_OR_EARPIECE,
            ROUTE_SPEAKER
        )

        val supportedRoutes = mutableListOf<AudioRoute>()

        routes.forEach {
            if (connection.callAudioState.supportedRouteMask and it == it) {
                supportedRoutes.add(nativeRouteToPilRoute(it))
            }
        }

        return AudioState(
            currentRoute,
            supportedRoutes.toTypedArray(),
            bluetoothAudioRouteName(connection),
            phoneLib.microphoneMuted,
            bluetoothAudioRoutes(connection).toTypedArray()
        )
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAudioRouteName(connection: Connection): String? = when {
        !hasBluetoothPermission -> null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> connection.callAudioState.activeBluetoothDevice?.name
        else -> null
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAudioRoutes(connection: Connection): List<BluetoothAudioRoute> {
        val bluetoothRoutes = mutableListOf<BluetoothAudioRoute>()

        if (!hasBluetoothPermission) return bluetoothRoutes

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return bluetoothRoutes

        connection.callAudioState.supportedBluetoothDevices.forEach {
            if (!it.name.isNullOrEmpty()) {
                bluetoothRoutes.add(BluetoothAudioRoute(it.name, it.name))
            }
        }

        return bluetoothRoutes
    }

    private val hasBluetoothPermission
        get() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private fun nativeRouteToPilRoute(nativeRoute: Int) = when (nativeRoute) {
        ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
        ROUTE_EARPIECE, ROUTE_WIRED_HEADSET, ROUTE_WIRED_OR_EARPIECE -> AudioRoute.PHONE
        ROUTE_SPEAKER -> AudioRoute.SPEAKER
        else -> AudioRoute.PHONE
    }

    private fun pilRouteToNativeRoute(pilRoute: AudioRoute) = when (pilRoute) {
        AudioRoute.SPEAKER -> ROUTE_SPEAKER
        AudioRoute.PHONE -> ROUTE_WIRED_OR_EARPIECE
        AudioRoute.BLUETOOTH -> ROUTE_BLUETOOTH
    }
}
