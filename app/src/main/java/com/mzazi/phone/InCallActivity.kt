package com.mzazi.phone

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * The in-call screen: it is shown by [PhoneCallService] for both incoming and
 * outgoing calls, so no third-party dialler UI is involved.
 */
class InCallActivity : AppCompatActivity(), CallCenter.Listener {

    private lateinit var tvName: TextView
    private lateinit var tvNumber: TextView
    private lateinit var tvState: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var ringingControls: View
    private lateinit var activeControls: View
    private lateinit var keypad: GridLayout
    private lateinit var btnMute: TextView
    private lateinit var btnSpeaker: TextView

    private var call: Call? = null
    private var callback: Call.Callback? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateState()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incall)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        tvName = findViewById(R.id.tvCallName)
        tvNumber = findViewById(R.id.tvCallNumber)
        tvState = findViewById(R.id.tvCallState)
        tvAvatar = findViewById(R.id.tvCallAvatar)
        ringingControls = findViewById(R.id.ringingControls)
        activeControls = findViewById(R.id.activeControls)
        keypad = findViewById(R.id.dtmfPad)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)

        findViewById<View>(R.id.btnAnswer).setOnClickListener {
            call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        findViewById<View>(R.id.btnDecline).setOnClickListener {
            call?.reject(false, null)
        }
        findViewById<View>(R.id.btnEnd).setOnClickListener {
            call?.disconnect()
        }
        btnMute.setOnClickListener {
            CallCenter.setMuted(!CallCenter.isMuted())
            renderControls()
        }
        btnSpeaker.setOnClickListener {
            CallCenter.setSpeaker(!CallCenter.isSpeakerOn())
            renderControls()
        }
        findViewById<View>(R.id.btnDtmfToggle).setOnClickListener {
            keypad.visibility = if (keypad.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        buildDtmfPad()
        CallCenter.addListener(this)
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        CallCenter.removeListener(this)
        callback?.let { cb -> call?.unregisterCallback(cb) }
        super.onDestroy()
    }

    override fun onCallChanged(call: Call?) {
        callback?.let { cb -> this.call?.unregisterCallback(cb) }
        this.call = call
        callback = null

        if (call == null) {
            finish()
            return
        }

        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = updateState()
            override fun onDetailsChanged(call: Call, details: Call.Details) = updateState()
        }
        callback = cb
        call.registerCallback(cb)
        updateState()
    }

    // ---------------------------------------------------------------- render

    private fun updateState() {
        val call = this.call ?: return
        val details = call.details
        val number = details?.handle?.schemeSpecificPart.orEmpty()
        val name = details?.callerDisplayName?.toString()?.takeIf { it.isNotBlank() }

        val display = name ?: if (number.isNotBlank()) T9.formatNumber(number) else getString(R.string.unknown_caller)
        tvName.text = display
        tvNumber.text = if (name != null && number.isNotBlank()) T9.formatNumber(number) else ""
        tvAvatar.text = T9.initials(display)

        val state = call.state
        tvState.text = when (state) {
            Call.STATE_RINGING -> getString(R.string.call_incoming)
            Call.STATE_DIALING -> getString(R.string.call_dialing)
            Call.STATE_ACTIVE -> durationText(details?.creationTimeMillis ?: System.currentTimeMillis())
            Call.STATE_HOLDING -> getString(R.string.call_held)
            Call.STATE_DISCONNECTING -> getString(R.string.call_ending)
            Call.STATE_DISCONNECTED -> getString(R.string.call_ended)
            else -> getString(R.string.call_connecting)
        }

        val ringing = state == Call.STATE_RINGING
        ringingControls.visibility = if (ringing) View.VISIBLE else View.GONE
        activeControls.visibility = if (ringing) View.GONE else View.VISIBLE
        if (state == Call.STATE_DISCONNECTED) {
            handler.postDelayed({ if (!isFinishing) finish() }, 800)
        }
        renderControls()
    }

    private fun renderControls() {
        val muted = CallCenter.isMuted()
        val speaker = CallCenter.isSpeakerOn()
        btnMute.alpha = if (muted) 1f else 0.55f
        btnMute.setTextColor(
            ContextCompat.getColor(this, if (muted) R.color.text_primary else R.color.text_secondary)
        )
        btnSpeaker.alpha = if (speaker) 1f else 0.55f
        btnSpeaker.setTextColor(
            ContextCompat.getColor(this, if (speaker) R.color.text_primary else R.color.text_secondary)
        )
    }

    private fun durationText(startedAt: Long): String {
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun buildDtmfPad() {
        keypad.removeAllViews()
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        keys.forEachIndexed { index, key ->
            val view = TextView(this).apply {
                text = key
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@InCallActivity, R.color.text_primary))
                background = ContextCompat.getDrawable(this@InCallActivity, R.drawable.key_dial_bg)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    call?.playDtmfTone(key.first())
                    it.postDelayed({ call?.stopDtmfTone() }, 120)
                }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1f),
                GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            keypad.addView(view, params)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
