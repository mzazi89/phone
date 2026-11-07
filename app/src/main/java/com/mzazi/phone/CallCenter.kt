package com.mzazi.phone

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Bridge between the [InCallService] (which owns the call and the audio route)
 * and whatever UI is currently showing it.
 */
object CallCenter {

    var service: InCallService? = null
        private set

    var current: Call? = null
        private set

    interface Listener {
        fun onCallChanged(call: Call?)
    }

    private val listeners = mutableListOf<Listener>()

    fun attach(callService: InCallService) {
        service = callService
    }

    fun detach(callService: InCallService) {
        if (service === callService) service = null
    }

    fun setCall(call: Call?) {
        current = call
        listeners.toList().forEach { it.onCallChanged(call) }
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        listener.onCallChanged(current)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private var mutedState = false
    private var speakerState = false

    fun setMuted(muted: Boolean) {
        mutedState = muted
        service?.setMuted(muted)
    }

    fun isMuted(): Boolean = mutedState

    fun setSpeaker(on: Boolean) {
        speakerState = on
        service?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        )
    }

    fun isSpeakerOn(): Boolean = speakerState
}
