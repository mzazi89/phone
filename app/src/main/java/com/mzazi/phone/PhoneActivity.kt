package com.mzazi.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import kotlin.concurrent.thread

class PhoneActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var btnSettings: View

    private lateinit var pageRecents: View
    private lateinit var pageKeypad: View
    private lateinit var pageContacts: View

    private lateinit var tabAll: TextView
    private lateinit var tabMissed: TextView
    private lateinit var callLogList: RecyclerView
    private lateinit var tvRecentsEmpty: View
    private lateinit var callLogPermissionCard: View

    private lateinit var tvNumber: TextView
    private lateinit var tvMatchedName: TextView
    private lateinit var tvSmartHint: TextView
    private lateinit var t9List: RecyclerView
    private lateinit var keypad: GridLayout
    private lateinit var callButtons: LinearLayout
    private lateinit var btnWhatsapp: View

    private lateinit var etSearch: EditText
    private lateinit var contactsList: RecyclerView
    private lateinit var tvContactsEmpty: View
    private lateinit var contactsPermissionCard: View

    private lateinit var navRecents: View
    private lateinit var navKeypad: View
    private lateinit var navContacts: View
    private lateinit var ivNavRecents: ImageView
    private lateinit var ivNavKeypad: ImageView
    private lateinit var ivNavContacts: ImageView
    private lateinit var tvNavRecents: TextView
    private lateinit var tvNavKeypad: TextView
    private lateinit var tvNavContacts: TextView

    private lateinit var contactsRepository: ContactsRepository
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var simManager: SimManager
    private lateinit var prefs: Prefs

    private val t9Adapter = ContactAdapter { callContact(it) }
    private val contactAdapter = ContactAdapter { callContact(it) }
    private lateinit var callLogAdapter: CallLogAdapter

    private var contacts: List<Contact> = emptyList()
    private var callEntries: List<CallEntry> = emptyList()
    private var missedOnly = false
    private var dialed = ""
    private var currentPage = PAGE_RECENTS

    private var pendingNumber: String? = null
    private var pendingHandle: PhoneAccountHandle? = null

    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val number = pendingNumber
            val handle = pendingHandle
            pendingNumber = null
            pendingHandle = null
            if (number == null) return@registerForActivityResult
            if (granted) {
                placeCallNow(number, handle)
            } else {
                toast(getString(R.string.call_permission_denied))
                fallbackDial(number)
            }
        }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadContacts() else updateContactsState()
        }

    private val requestRecentsPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                refreshCallLog()
                rebuildCallButtons()
            } else {
                updateCallLogState()
            }
        }

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            rebuildCallButtons()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)

        contactsRepository = ContactsRepository(this)
        callLogRepository = CallLogRepository(this)
        simManager = SimManager(this)
        prefs = Prefs(this)

        tvTitle = findViewById(R.id.tvTitle)
        btnSettings = findViewById(R.id.btnSettings)

        pageRecents = findViewById(R.id.pageRecents)
        pageKeypad = findViewById(R.id.pageKeypad)
        pageContacts = findViewById(R.id.pageContacts)

        tabAll = findViewById(R.id.tabAll)
        tabMissed = findViewById(R.id.tabMissed)
        callLogList = findViewById(R.id.callLogList)
        tvRecentsEmpty = findViewById(R.id.tvRecentsEmpty)
        callLogPermissionCard = findViewById(R.id.callLogPermissionCard)

        tvNumber = findViewById(R.id.tvNumber)
        tvMatchedName = findViewById(R.id.tvMatchedName)
        tvSmartHint = findViewById(R.id.tvSmartHint)
        t9List = findViewById(R.id.t9List)
        keypad = findViewById(R.id.keypad)
        callButtons = findViewById(R.id.callButtons)
        btnWhatsapp = findViewById(R.id.btnWhatsapp)

        etSearch = findViewById(R.id.etSearch)
        contactsList = findViewById(R.id.contactsList)
        tvContactsEmpty = findViewById(R.id.tvContactsEmpty)
        contactsPermissionCard = findViewById(R.id.contactsPermissionCard)

        navRecents = findViewById(R.id.navRecents)
        navKeypad = findViewById(R.id.navKeypad)
        navContacts = findViewById(R.id.navContacts)
        ivNavRecents = findViewById(R.id.ivNavRecents)
        ivNavKeypad = findViewById(R.id.ivNavKeypad)
        ivNavContacts = findViewById(R.id.ivNavContacts)
        tvNavRecents = findViewById(R.id.tvNavRecents)
        tvNavKeypad = findViewById(R.id.tvNavKeypad)
        tvNavContacts = findViewById(R.id.tvNavContacts)

        callLogAdapter = CallLogAdapter(simManager, countryLabel()) { entry ->
            placeCall(entry.number, null)
        }

        setupLists()
        buildKeypad()
        wireButtons()

        dialed = savedInstanceState?.getString(STATE_DIALED) ?: ""
        refreshDial()
        selectPage(PAGE_RECENTS)
        handleIntent(intent)
        maybeAskDefaultDialer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DIALED, dialed)
    }

    override fun onResume() {
        super.onResume()
        rebuildCallButtons()
        if (currentPage == PAGE_RECENTS) refreshCallLog()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "tel" && uri.scheme != "telprompt") return
        val number = uri.schemeSpecificPart?.trim().orEmpty()
        if (number.isEmpty()) return
        dialed = number
        refreshDial()
        selectPage(PAGE_KEYPAD)
    }

    // ------------------------------------------------------------- view setup

    private fun setupLists() {
        t9List.layoutManager = LinearLayoutManager(this)
        t9List.adapter = t9Adapter
        t9List.itemAnimator = null

        contactsList.layoutManager = LinearLayoutManager(this)
        contactsList.adapter = contactAdapter

        callLogList.layoutManager = LinearLayoutManager(this)
        callLogList.adapter = callLogAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyContactFilter()
        })
    }

    private fun buildKeypad() {
        keypad.removeAllViews()
        VALID_KEYS.forEachIndexed { index, key ->
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@PhoneActivity, R.drawable.key_dial_bg)
                isClickable = true
                isFocusable = true
                contentDescription = if (key.letters.isEmpty()) key.digit else "${key.digit} ${key.letters}"
                setOnClickListener { view ->
                    if (prefs.haptics) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    onDigit(key.digit)
                }
                if (key.digit == "0") {
                    setOnLongClickListener { view ->
                        if (prefs.haptics) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        onPlus()
                        true
                    }
                }
            }

            container.addView(TextView(this).apply {
                text = key.digit
                textSize = 24f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(ContextCompat.getColor(this@PhoneActivity, R.color.text_primary))
            })

            if (key.letters.isNotEmpty()) {
                container.addView(TextView(this).apply {
                    text = key.letters
                    textSize = 9f
                    letterSpacing = 0.12f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(ContextCompat.getColor(this@PhoneActivity, R.color.text_muted))
                })
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1f),
                GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            keypad.addView(container, params)
        }
    }

    private fun wireButtons() {
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        navRecents.setOnClickListener { selectPage(PAGE_RECENTS) }
        navKeypad.setOnClickListener { selectPage(PAGE_KEYPAD) }
        navContacts.setOnClickListener { selectPage(PAGE_CONTACTS) }

        tabAll.setOnClickListener { setCallFilter(false) }
        tabMissed.setOnClickListener { setCallFilter(true) }

        findViewById<View>(R.id.btnBackspace).setOnClickListener { onBackspace() }
        findViewById<View>(R.id.btnBackspace).setOnLongClickListener {
            dialed = ""
            refreshDial()
            true
        }
        findViewById<View>(R.id.btnAddContact).setOnClickListener { addToContacts() }
        findViewById<View>(R.id.btnWhatsapp).setOnClickListener { openWhatsApp() }
        findViewById<View>(R.id.btnAllowContacts).setOnClickListener { askContactsPermission() }
        findViewById<View>(R.id.btnAllowCallLog).setOnClickListener { askRecentsPermissions() }
    }

    // ------------------------------------------------------------- navigation

    private fun selectPage(page: Int) {
        currentPage = page
        pageRecents.visibility = if (page == PAGE_RECENTS) View.VISIBLE else View.GONE
        pageKeypad.visibility = if (page == PAGE_KEYPAD) View.VISIBLE else View.GONE
        pageContacts.visibility = if (page == PAGE_CONTACTS) View.VISIBLE else View.GONE

        tvTitle.setText(
            when (page) {
                PAGE_KEYPAD -> R.string.tab_keypad
                PAGE_CONTACTS -> R.string.tab_contacts
                else -> R.string.tab_recents
            }
        )

        styleNav(navRecents, ivNavRecents, tvNavRecents, page == PAGE_RECENTS)
        styleNav(navKeypad, ivNavKeypad, tvNavKeypad, page == PAGE_KEYPAD)
        styleNav(navContacts, ivNavContacts, tvNavContacts, page == PAGE_CONTACTS)

        when (page) {
            PAGE_RECENTS -> refreshCallLog()
            PAGE_KEYPAD -> rebuildCallButtons()
            PAGE_CONTACTS -> ensureContacts()
        }
    }

    private fun styleNav(container: View, icon: ImageView, label: TextView, selected: Boolean) {
        val color = ContextCompat.getColor(this, if (selected) R.color.accent else R.color.text_muted)
        icon.setColorFilter(color)
        label.setTextColor(color)
        container.isSelected = selected
    }

    // ---------------------------------------------------------------- keypad

    private fun onDigit(digit: String) {
        if (dialed.length >= MAX_DIAL_LENGTH) return
        dialed += digit
        refreshDial()
    }

    private fun onPlus() {
        if (dialed.isEmpty()) {
            dialed = "+"
            refreshDial()
        }
    }

    private fun onBackspace() {
        if (dialed.isEmpty()) return
        dialed = dialed.dropLast(1)
        refreshDial()
    }

    private fun refreshDial() {
        tvNumber.text = if (dialed.isEmpty()) "" else T9.formatNumber(dialed)
        tvMatchedName.text = findExactContact()?.name.orEmpty()

        val results = smartDialResults()
        t9Adapter.submit(results)
        t9List.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
        tvSmartHint.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        tvSmartHint.setText(if (dialed.isEmpty()) R.string.smart_dial_empty else R.string.no_matches)
    }

    private fun smartDialResults(): List<Contact> {
        if (dialed.isEmpty()) return emptyList()
        val query = dialed.filter { it.isDigit() }
        if (query.isEmpty()) return emptyList()
        return contacts.filter { T9.matches(query, it.name, it.primaryNumber) }.take(8)
    }

    private fun findExactContact(): Contact? {
        val query = T9.normalizeNumber(dialed).trimStart('+')
        if (query.length < 3) return null
        return contacts.firstOrNull { contact ->
            val number = T9.normalizeNumber(contact.primaryNumber).trimStart('+')
            number == query || number.endsWith(query) || query.endsWith(number)
        }
    }

    // ----------------------------------------------------------------- calls

    private fun rebuildCallButtons() {
        callButtons.removeAllViews()

        val accounts = if (hasPhoneStatePermission()) simManager.accounts() else emptyList()
        if (accounts.size >= 2) {
            accounts.take(2).forEachIndexed { index, handle ->
                callButtons.addView(
                    makeCallPill((index + 1).toString(), handle, weight = 1f, compact = true)
                )
            }
        } else {
            callButtons.addView(
                makeCallPill(getString(R.string.call), accounts.firstOrNull(), weight = 1f, compact = false)
            )
        }
    }

    private fun makeCallPill(
        label: String,
        handle: PhoneAccountHandle?,
        weight: Float,
        compact: Boolean
    ): TextView {
        val pill = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@PhoneActivity, android.R.color.white))
            textSize = if (compact) 17f else 16f
            if (!compact) letterSpacing = 0.06f
            background = ContextCompat.getDrawable(this@PhoneActivity, R.drawable.pill_green)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.call)
            setOnClickListener {
                val number = dialed.trim()
                if (number.isEmpty()) {
                    toast(getString(R.string.enter_number))
                } else {
                    placeCall(number, handle)
                }
            }
        }
        val params = LinearLayout.LayoutParams(0, dp(56), weight)
        params.setMargins(dp(4), 0, dp(4), 0)
        pill.layoutParams = params
        return pill
    }

    private fun placeCall(number: String, handle: PhoneAccountHandle?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingNumber = number
            pendingHandle = handle
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
            return
        }
        placeCallNow(number, handle)
    }

    private fun placeCallNow(number: String, handle: PhoneAccountHandle?) {
        val placed = simManager.placeCall(number, handle)
        if (!placed) fallbackDial(number)
    }

    /** Last resort: hand the number to the system so the user can still call. */
    private fun fallbackDial(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_phone_app))
        }
    }

    private fun callContact(contact: Contact) {
        when {
            contact.numbers.isEmpty() -> toast(getString(R.string.enter_number))
            contact.numbers.size == 1 -> placeCall(contact.numbers[0].number, null)
            else -> {
                val labels = contact.numbers
                    .map { "${it.label}: ${T9.formatNumber(it.number)}" }
                    .toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("${getString(R.string.choose_number)} — ${contact.name}")
                    .setItems(labels) { _, which ->
                        placeCall(contact.numbers[which].number, null)
                    }
                    .show()
            }
        }
    }

    private fun addToContacts() {
        if (dialed.isBlank()) {
            toast(getString(R.string.enter_number))
            return
        }
        try {
            startActivity(
                Intent(ContactsContract.Intents.Insert.ACTION).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.PHONE, dialed)
                }
            )
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_contacts_app))
        }
    }

    private fun openWhatsApp() {
        val number = T9.normalizeNumber(dialed).trimStart('+')
        if (number.isEmpty()) {
            toast(getString(R.string.enter_number))
            return
        }
        val uri = Uri.parse("https://wa.me/$number")
        try {
            val direct = packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (direct != null) {
                startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_whatsapp))
        }
    }

    // --------------------------------------------------------------- recents

    private fun setCallFilter(missed: Boolean) {
        missedOnly = missed
        tabAll.background = if (!missed) {
            ContextCompat.getDrawable(this, R.drawable.segment_underline)
        } else {
            null
        }
        tabMissed.background = if (missed) {
            ContextCompat.getDrawable(this, R.drawable.segment_underline)
        } else {
            null
        }
        tabAll.setTextColor(ContextCompat.getColor(this, if (missed) R.color.text_muted else R.color.text_primary))
        tabMissed.setTextColor(ContextCompat.getColor(this, if (missed) R.color.text_primary else R.color.text_muted))
        applyCallFilter()
    }

    private fun refreshCallLog() {
        if (!hasCallLogPermission()) {
            updateCallLogState()
            return
        }
        thread {
            val entries = callLogRepository.load()
            runOnUiThread {
                callEntries = entries
                updateCallLogState()
                applyCallFilter()
            }
        }
    }

    private fun applyCallFilter() {
        val filtered = if (missedOnly) callEntries.filter { it.isMissed } else callEntries
        callLogAdapter.submit(filtered)
        tvRecentsEmpty.visibility =
            if (hasCallLogPermission() && filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateCallLogState() {
        val granted = hasCallLogPermission()
        callLogPermissionCard.visibility = if (granted) View.GONE else View.VISIBLE
        callLogList.visibility = if (granted) View.VISIBLE else View.GONE
        if (!granted) tvRecentsEmpty.visibility = View.GONE
    }

    private fun askRecentsPermissions() {
        requestRecentsPermissions.launch(
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    // -------------------------------------------------------------- contacts

    private fun ensureContacts() {
        if (hasContactsPermission()) {
            if (contacts.isEmpty()) loadContacts() else updateContactsState()
        } else {
            contactsPermissionCard.visibility = View.VISIBLE
            contactsList.visibility = View.GONE
            tvContactsEmpty.visibility = View.GONE
        }
    }

    private fun askContactsPermission() {
        val prefs = getSharedPreferences("permissions", MODE_PRIVATE)
        val asked = prefs.getBoolean(KEY_CONTACTS_ASKED, false)
        val canAsk = !asked || shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
        if (!canAsk) {
            openAppSettings()
            return
        }
        prefs.edit().putBoolean(KEY_CONTACTS_ASKED, true).apply()
        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    private fun loadContacts() {
        if (!hasContactsPermission()) {
            updateContactsState()
            return
        }
        thread {
            val loaded = contactsRepository.load()
            runOnUiThread {
                contacts = loaded
                applyContactFilter()
                updateContactsState()
                refreshDial()
            }
        }
    }

    private fun updateContactsState() {
        val granted = hasContactsPermission()
        contactsPermissionCard.visibility = if (granted) View.GONE else View.VISIBLE
        contactsList.visibility = if (granted) View.VISIBLE else View.GONE
        if (!granted) {
            tvContactsEmpty.visibility = View.GONE
            return
        }
        tvContactsEmpty.visibility = if (contactAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun applyContactFilter() {
        val query = etSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            contactAdapter.submit(contacts)
        } else {
            val lower = query.lowercase()
            val digits = T9.normalizeNumber(query).trimStart('+')
            val t9Query = T9.digitsFor(query)
            contactAdapter.submit(
                contacts.filter { contact ->
                    contact.name.lowercase().contains(lower) ||
                        (digits.isNotEmpty() &&
                            T9.normalizeNumber(contact.primaryNumber).contains(digits)) ||
                        (t9Query.isNotEmpty() && T9.digitsFor(contact.name).contains(t9Query))
                }
            )
        }
        tvContactsEmpty.visibility =
            if (hasContactsPermission() && contactAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------- role flow

    private fun maybeAskDefaultDialer() {
        if (DialerRole.isDefault(this)) return
        if (prefs.roleAsked) return
        prefs.roleAsked = true

        AlertDialog.Builder(this)
            .setTitle(R.string.role_dialog_title)
            .setMessage(R.string.role_dialog_body)
            .setPositiveButton(R.string.role_dialog_confirm) { _, _ ->
                DialerRole.requestIntent(this)?.let { roleRequest.launch(it) }
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    // ------------------------------------------------------------------ misc

    private fun countryLabel(): String {
        val iso = try {
            val telephony = getSystemService(TelephonyManager::class.java)
            telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: return ""

        return try {
            Locale("", iso.uppercase(Locale.US)).displayCountry
        } catch (e: Exception) {
            ""
        }
    }

    private fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class DialKey(val digit: String, val letters: String)

    private companion object {
        const val STATE_DIALED = "dialed"
        const val KEY_CONTACTS_ASKED = "contacts_asked"
        const val MAX_DIAL_LENGTH = 20

        const val PAGE_RECENTS = 0
        const val PAGE_KEYPAD = 1
        const val PAGE_CONTACTS = 2

        val VALID_KEYS = listOf(
            DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF"),
            DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
            DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
            DialKey("*", ""), DialKey("0", "+"), DialKey("#", "")
        )
    }
}
