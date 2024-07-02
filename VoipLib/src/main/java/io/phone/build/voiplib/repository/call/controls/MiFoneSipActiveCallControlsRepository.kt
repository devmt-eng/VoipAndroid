package io.phone.build.voiplib.repository.call.controls

import com.google.gson.GsonBuilder
import org.linphone.core.AudioDevice
import org.linphone.core.AudioDevice.Capabilities
import org.linphone.core.Core
import org.linphone.core.StreamType
import io.phone.build.voipsdkandroid.logWithContext
import io.phone.build.voipsdkandroid.logging.LogLevel
import io.phone.build.voiplib.model.AttendedTransferSession
import io.phone.build.voiplib.model.Call
import io.phone.build.voiplib.repository.MiFoneCoreInstanceManager

internal class MiFoneSipActiveCallControlsRepository(private val mifoneCoreInstanceManager: MiFoneCoreInstanceManager) {

    private val core: Core
        get() = mifoneCoreInstanceManager.safeLinphoneCore!!

    fun setMicrophone(on: Boolean) {
        mifoneCoreInstanceManager.safeLinphoneCore?.isMicEnabled = on
    }

    fun setHold(call: Call, on: Boolean) {
        if (on) {
            call.libCall.pause()
        } else {
            call.libCall.resume()
        }
    }

    fun isMicrophoneMuted(): Boolean {
        val core = mifoneCoreInstanceManager.safeLinphoneCore ?: return false

        return !core.isMicEnabled
    }

    fun transferUnattended(call: Call, to: String) {
        call.libCall.transfer(to)
    }

    fun finishAttendedTransfer(attendedTransferSession: AttendedTransferSession) {
        attendedTransferSession.from.libCall.transferToAnother(attendedTransferSession.to.libCall)
    }

    fun pauseCall(call: Call) {
        call.libCall.pause()
    }

    fun resumeCall(call: Call) {
        call.libCall.resume()
    }

    fun sendDtmf(call: Call, dtmf: String) {
        if (dtmf.length == 1) {
            call.libCall.sendDtmf(dtmf[0])
        } else {
            call.libCall.sendDtmfs(dtmf)
        }
    }

    fun provideCallInfo(call: Call): String =
        GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create()
            .toJson(buildCallInfo(call.libCall))

    internal fun routeAudioTo(types: List<AudioDevice.Type>, call: org.linphone.core.Call? = null) {
        if (core.callsNb == 0) {
            log("No call found, aborting [${types.displayName}] audio route change")
            return
        }

        val currentCall = call ?: (core.currentCall ?: core.calls[0])

        logAudioDevices()

        findAudioDeviceForRoute(types, Capabilities.CapabilityPlay)?.let {
            currentCall.outputAudioDevice = it
        }

        findAudioDeviceForRoute(
            if (types.shouldUseSpecificAudioInput) types else listOf(AudioDevice.Type.Microphone),
            Capabilities.CapabilityRecord,
        )?.let {
            currentCall.inputAudioDevice = it
        }
    }

    private fun findAudioDeviceForRoute(
        types: List<AudioDevice.Type>,
        capability: Capabilities,
    ): AudioDevice? {
        for (audioDevice in core.audioDevices) {
            if (types.contains(audioDevice.type) && audioDevice.hasCapability(capability)) {
                log("Routing to [${audioDevice.deviceName}] for [${capability.displayName}]")
                return audioDevice
            }
        }

        log("Couldn't find [${types.displayName}] ${capability.displayName} audio device")

        return null
    }

    private fun logAudioDevices() = core.audioDevices.forEach {
        log("Audio Device: ${it.debugString}")
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) =
        logWithContext(message, "ACTIVE-SIP-CALLS", level)

    private fun buildCallInfo(call: org.linphone.core.Call): Map<String, Any> = mapOf(
        "audio" to mapOf(
            "codec" to call.currentParams.usedAudioPayloadType?.description,
            "codecMime" to call.currentParams.usedAudioPayloadType?.mimeType,
            "codecRecvFmtp" to call.currentParams.usedAudioPayloadType?.recvFmtp,
            "codecSendFmtp" to call.currentParams.usedAudioPayloadType?.sendFmtp,
            "codecChannels" to call.currentParams.usedAudioPayloadType?.channels,
            "downloadBandwidth" to call.getStats(StreamType.Audio)?.downloadBandwidth,
            "estimatedDownloadBandwidth" to call.getStats(StreamType.Audio)?.estimatedDownloadBandwidth,
            "jitterBufferSizeMs" to call.getStats(StreamType.Audio)?.jitterBufferSizeMs,
            "localLateRate" to call.getStats(StreamType.Audio)?.localLateRate,
            "localLossRate" to call.getStats(StreamType.Audio)?.localLossRate,
            "receiverInterarrivalJitter" to call.getStats(StreamType.Audio)?.receiverInterarrivalJitter,
            "receiverLossRate" to call.getStats(StreamType.Audio)?.receiverLossRate,
            "roundTripDelay" to call.getStats(StreamType.Audio)?.roundTripDelay,
            "rtcpDownloadBandwidth" to call.getStats(StreamType.Audio)?.rtcpDownloadBandwidth,
            "rtcpUploadBandwidth" to call.getStats(StreamType.Audio)?.rtcpUploadBandwidth,
            "senderInterarrivalJitter" to call.getStats(StreamType.Audio)?.senderInterarrivalJitter,
            "senderLossRate" to call.getStats(StreamType.Audio)?.senderLossRate,
            "iceState" to call.getStats(StreamType.Audio)?.iceState?.name,
            "uploadBandwidth" to call.getStats(StreamType.Audio)?.uploadBandwidth,
        ),
        "advanced-settings" to mapOf(
            "mtu" to call.core.mtu,
            "echoCancellationEnabled" to call.core.isEchoCancellationEnabled,
            "adaptiveRateControlEnabled" to call.core.isAdaptiveRateControlEnabled,
            "audioAdaptiveJittcompEnabled" to call.core.isAudioAdaptiveJittcompEnabled,
            "rtpBundleEnabled" to call.core.isRtpBundleEnabled,
            "adaptiveRateAlgorithm" to call.core.adaptiveRateAlgorithm,
        ),
        "to-address" to mapOf(
            "transport" to call.toAddress.transport.name,
            "domain" to call.toAddress.domain,
        ),
        "remote-params" to mapOf(
            "encryption" to call.remoteParams?.mediaEncryption?.name,
            "sessionName" to call.remoteParams?.sessionName,
            "remotePartyId" to call.remoteParams?.getCustomHeader("Remote-Party-ID"),
            "pAssertedIdentity" to call.remoteParams?.getCustomHeader("P-Asserted-Identity"),
        ),
        "params" to mapOf(
            "encryption" to call.params.mediaEncryption.name,
            "sessionName" to call.params.sessionName
        ),
        "call" to mapOf(
            "callId" to call.callLog.callId,
            "refKey" to call.callLog.refKey,
            "status" to call.callLog.status,
            "direction" to call.callLog.dir.name,
            "quality" to call.callLog.quality,
            "startDate" to call.callLog.startDate,
            "reason" to call.reason.name,
            "duration" to call.duration
        ),
        "network" to mapOf(
            "stunEnabled" to call.core.natPolicy?.isStunEnabled,
            "stunServer" to call.core.natPolicy?.stunServer,
            "upnpEnabled" to call.core.natPolicy?.isUpnpEnabled,
            "ipv6Enabled" to call.core.isIpv6Enabled,
        ),
        "error" to mapOf(
            "phrase" to call.errorInfo.phrase,
            "protocol" to call.errorInfo.protocol,
            "reason" to call.errorInfo.reason,
            "protocolCode" to call.errorInfo.protocolCode
        ),
    )

    fun switchCall(from: Call, to: Call) {
        from.libCall.pause()
        to.libCall.resume()
    }
}

private val List<AudioDevice.Type>.displayName
    get() = joinToString(separator = ", ") { it.name }

// We only want to look for a specific audio input when it's one of these, otherwise we always
// just want to fallback to the phone's microphone.
private val List<AudioDevice.Type>.shouldUseSpecificAudioInput
    get() = first() in listOf(
        AudioDevice.Type.Headset,
        AudioDevice.Type.Headphones,
        AudioDevice.Type.Bluetooth,
    )

private val Capabilities.displayName
    get() = when (this) {
        Capabilities.CapabilityRecord -> "recorder"
        Capabilities.CapabilityPlay -> "playback"
        Capabilities.CapabilityAll -> "all"
    }

private val AudioDevice.debugString
    get() = "id=${id} name=${deviceName} type=${type} capability=${capabilities}"