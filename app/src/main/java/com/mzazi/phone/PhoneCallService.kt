package com.mzazi.phone

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

/**
 * The in-call service required for the default-dialer role. Android binds this
 * when a call is placed to or received by the device, so the whole call flow
 * happens inside this app.
 */
class PhoneCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallCenter.attach(this)
    }

    override fun onDestroy() {
        CallCenter.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val blocked = BlockedStore(this).contains(number)
        if (blocked && call.state == Call.STATE_RINGING) {
            call.reject(false, null)
            return
        }

        if (CallCenter.current == null) {
            CallCenter.setCall(call)
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val remaining = calls.lastOrNull()
        if (CallCenter.current === call) {
            CallCenter.setCall(remaining)
        }
    }
}
