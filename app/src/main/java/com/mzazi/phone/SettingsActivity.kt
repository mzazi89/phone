package com.mzazi.phone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var blocked: BlockedStore

    private lateinit var tvDefaultStatus: TextView
    private lateinit var tvBlockedCount: TextView
    private lateinit var switchWhatsapp: SwitchCompat
    private lateinit var switchHaptics: SwitchCompat

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        blocked = BlockedStore(this)

        tvDefaultStatus = findViewById(R.id.tvDefaultPhoneStatus)
        tvBlockedCount = findViewById(R.id.tvBlockedCount)
        switchWhatsapp = findViewById(R.id.switchWhatsapp)
        switchHaptics = findViewById(R.id.switchHaptics)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.rowDefaultPhone).setOnClickListener { requestDefaultRole() }
        findViewById<android.view.View>(R.id.rowBlocked).setOnClickListener { showBlocked() }
        findViewById<android.view.View>(R.id.rowAbout).setOnClickListener { showAbout() }
        findViewById<android.view.View>(R.id.rowSim).setOnClickListener {
            openSystem(Settings.ACTION_WIRELESS_SETTINGS)
        }
        findViewById<android.view.View>(R.id.rowSounds).setOnClickListener {
            openSystem(Settings.ACTION_SOUND_SETTINGS)
        }

        switchWhatsapp.setOnCheckedChangeListener { _, checked ->
            prefs.whatsappButton = checked
        }
        switchHaptics.setOnCheckedChangeListener { _, checked ->
            prefs.haptics = checked
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tvDefaultStatus.text = getString(
            if (DialerRole.isDefault(this)) R.string.default_phone_status_on
            else R.string.default_phone_status_off
        )

        val count = blocked.all().size
        tvBlockedCount.text =
            if (count == 0) getString(R.string.blocked_none)
            else getString(R.string.blocked_count, count)

        switchWhatsapp.isChecked = prefs.whatsappButton
        switchHaptics.isChecked = prefs.haptics
    }

    private fun requestDefaultRole() {
        if (DialerRole.isDefault(this)) {
            toast(getString(R.string.default_phone_status_on))
            return
        }
        val intent = DialerRole.requestIntent(this)
        if (intent == null) {
            toast(getString(R.string.no_phone_app))
            return
        }
        roleRequest.launch(intent)
    }

    private fun showBlocked() {
        val numbers = blocked.all()
        val items = (numbers + getString(R.string.blocked_add)).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.blocked_numbers)
            .setItems(items) { _, which ->
                if (which == numbers.size) {
                    addBlockedNumber()
                } else {
                    confirmRemove(numbers[which])
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun addBlockedNumber() {
        val input = EditText(this).apply {
            hint = getString(R.string.dial_hint)
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.blocked_add)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                blocked.add(input.text.toString())
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(number: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.blocked_remove_confirm, number))
            .setPositiveButton(R.string.remove) { _, _ ->
                blocked.remove(number)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage(getString(R.string.about_body, appVersion()))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun openSystem(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            toast(getString(R.string.no_phone_app))
        }
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
