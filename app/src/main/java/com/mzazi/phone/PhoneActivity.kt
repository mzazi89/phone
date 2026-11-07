package com.mzazi.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class PhoneActivity : AppCompatActivity() {

    private lateinit var tvNumber: TextView
    private lateinit var tvMatchedName: TextView
    private lateinit var tvSmartHint: TextView
    private lateinit var t9List: RecyclerView
    private lateinit var keypad: GridLayout

    private lateinit var pageKeypad: View
    private lateinit var pageContacts: View
    private lateinit var pageRecents: View

    private lateinit var tabKeypad: TextView
    private lateinit var tabContacts: TextView
    private lateinit var tabRecents: TextView

    private lateinit var etSearch: EditText
    private lateinit var contactsList: RecyclerView
    private lateinit var tvContactsEmpty: View
    private lateinit var permissionCard: View

    private lateinit var recentsList: RecyclerView
    private lateinit var tvRecentsEmpty: View
    private lateinit var btnClearRecents: View

    private val t9Adapter = ContactAdapter { callContact(it) }
    private val contactAdapter = ContactAdapter { callContact(it) }
    private val recentAdapter = RecentAdapter { onRecentClicked(it) }

    private lateinit var contactsRepository: ContactsRepository
    private lateinit var recentsStore: RecentsStore

    private var contacts: List<Contact> = emptyList()
    private var dialed: String = ""
    private var currentTab = TAB_KEYPAD

    private var pendingNumber: String? = null
    private var pendingName: String? = null

    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val number = pendingNumber
            val name = pendingName
            pendingNumber = null
            pendingName = null
            if (number == null) return@registerForActivityResult
            if (granted) {
                startCall(number, name)
            } else {
                toast(getString(R.string.call_permission_denied))
                dialOnly(number)
            }
        }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadContacts() else updateContactsState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)

        contactsRepository = ContactsRepository(this)
        recentsStore = RecentsStore(this)

        tvNumber = findViewById(R.id.tvNumber)
        tvMatchedName = findViewById(R.id.tvMatchedName)
        tvSmartHint = findViewById(R.id.tvSmartHint)
        t9List = findViewById(R.id.t9List)
        keypad = findViewById(R.id.keypad)

        pageKeypad = findViewById(R.id.pageKeypad)
        pageContacts = findViewById(R.id.pageContacts)
        pageRecents = findViewById(R.id.pageRecents)

        tabKeypad = findViewById(R.id.tabKeypad)
        tabContacts = findViewById(R.id.tabContacts)
        tabRecents = findViewById(R.id.tabRecents)

        etSearch = findViewById(R.id.etSearch)
        contactsList = findViewById(R.id.contactsList)
        tvContactsEmpty = findViewById(R.id.tvContactsEmpty)
        permissionCard = findViewById(R.id.contactsPermissionCard)

        recentsList = findViewById(R.id.recentsList)
        tvRecentsEmpty = findViewById(R.id.tvRecentsEmpty)
        btnClearRecents = findViewById(R.id.btnClearRecents)

        setupLists()
        buildKeypad()
        wireButtons()

        dialed = savedInstanceState?.getString(STATE_DIALED) ?: ""
        refreshDial()
        selectTab(TAB_KEYPAD)
        refreshRecents()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Pre-fills the dialler when another app opens a `tel:` link with us. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "tel" && uri.scheme != "telprompt") return
        val number = uri.schemeSpecificPart?.trim().orEmpty()
        if (number.isEmpty()) return
        dialed = number
        refreshDial()
        selectTab(TAB_KEYPAD)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DIALED, dialed)
    }

    override fun onResume() {
        super.onResume()
        if (currentTab == TAB_CONTACTS &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadContacts()
        }
    }

    // ------------------------------------------------------------- view setup

    private fun setupLists() {
        val linear = LinearLayoutManager(this)
        t9List.layoutManager = linear
        t9List.adapter = t9Adapter
        t9List.itemAnimator = null

        contactsList.layoutManager = LinearLayoutManager(this)
        contactsList.adapter = contactAdapter

        recentsList.layoutManager = LinearLayoutManager(this)
        recentsList.adapter = recentAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyContactFilter()
        })
    }

    private fun buildKeypad() {
        keypad.removeAllViews()
        VALID_KEYS.forEachIndexed { index, key ->
            val row = index / 3
            val column = index % 3

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@PhoneActivity, R.drawable.key_dial_bg)
                isClickable = true
                isFocusable = true
                contentDescription = if (key.letters.isEmpty()) key.digit else "${key.digit} ${key.letters}"
                setOnClickListener { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDigit(key.digit)
                }
                if (key.digit == "0") {
                    setOnLongClickListener { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onPlus()
                        true
                    }
                }
            }

            container.addView(TextView(this).apply {
                text = key.digit
                textSize = 25f
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
                GridLayout.spec(row, 1f),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            keypad.addView(container, params)
        }
    }

    private fun wireButtons() {
        findViewById<View>(R.id.btnCall).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onCallPressed()
        }
        findViewById<View>(R.id.btnBackspace).setOnClickListener { onBackspace() }
        findViewById<View>(R.id.btnBackspace).setOnLongClickListener {
            dialed = ""
            refreshDial()
            true
        }
        findViewById<View>(R.id.btnAddContact).setOnClickListener { addToContacts() }
        findViewById<View>(R.id.btnAllowContacts).setOnClickListener { askContactsPermission() }
        btnClearRecents.setOnClickListener { confirmClearRecents() }

        tabKeypad.setOnClickListener { selectTab(TAB_KEYPAD) }
        tabContacts.setOnClickListener { selectTab(TAB_CONTACTS) }
        tabRecents.setOnClickListener { selectTab(TAB_RECENTS) }
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

        val exact = findExactContact()
        tvMatchedName.text = exact?.name.orEmpty()

        val results = smartDialResults()
        t9Adapter.submit(results)
        t9List.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE

        tvSmartHint.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        if (dialed.isEmpty()) {
            tvSmartHint.setText(R.string.smart_dial_empty)
        } else {
            tvSmartHint.setText(R.string.no_matches)
        }
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

    private fun onCallPressed() {
        val target = dialed.trim()
        if (target.isEmpty()) {
            toast(getString(R.string.enter_number))
            return
        }
        startCall(target, findExactContact()?.name)
    }

    private fun startCall(number: String, name: String?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingNumber = number
            pendingName = name
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
            return
        }

        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
            recentsStore.add(number, name)
            refreshRecents()
        } catch (e: SecurityException) {
            dialOnly(number)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_phone_app))
            dialOnly(number)
        }
    }

    /** Fallback that always works: hand the number to the system dialler. */
    private fun dialOnly(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_phone_app))
        }
    }

    private fun callContact(contact: Contact) {
        when {
            contact.numbers.isEmpty() -> toast(getString(R.string.enter_number))
            contact.numbers.size == 1 -> startCall(contact.numbers[0].number, contact.name)
            else -> {
                val labels = contact.numbers
                    .map { "${it.label}: ${T9.formatNumber(it.number)}" }
                    .toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("${getString(R.string.choose_number)} — ${contact.name}")
                    .setItems(labels) { _, which ->
                        startCall(contact.numbers[which].number, contact.name)
                    }
                    .show()
            }
        }
    }

    private fun onRecentClicked(call: RecentCall) {
        startCall(call.number, call.name)
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

    // -------------------------------------------------------------- contacts

    private fun selectTab(tab: Int) {
        currentTab = tab
        tabKeypad.isSelected = tab == TAB_KEYPAD
        tabContacts.isSelected = tab == TAB_CONTACTS
        tabRecents.isSelected = tab == TAB_RECENTS

        pageKeypad.visibility = if (tab == TAB_KEYPAD) View.VISIBLE else View.GONE
        pageContacts.visibility = if (tab == TAB_CONTACTS) View.VISIBLE else View.GONE
        pageRecents.visibility = if (tab == TAB_RECENTS) View.VISIBLE else View.GONE

        btnClearRecents.visibility =
            if (tab == TAB_RECENTS && recentAdapter.itemCount > 0) View.VISIBLE else View.GONE

        if (tab == TAB_CONTACTS) {
            if (hasContactsPermission()) {
                if (contacts.isEmpty()) loadContacts() else updateContactsState()
            } else {
                permissionCard.visibility = View.VISIBLE
                contactsList.visibility = View.GONE
                tvContactsEmpty.visibility = View.GONE
            }
        }
        if (tab == TAB_RECENTS) refreshRecents()
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

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
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
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
            }
        }
    }

    private fun updateContactsState() {
        val granted = hasContactsPermission()
        permissionCard.visibility = if (granted) View.GONE else View.VISIBLE
        contactsList.visibility = if (granted) View.VISIBLE else View.GONE
        if (!granted) {
            tvContactsEmpty.visibility = View.GONE
            return
        }
        tvContactsEmpty.visibility =
            if (contactAdapter.itemCount == 0) View.VISIBLE else View.GONE
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

    // --------------------------------------------------------------- recents

    private fun refreshRecents() {
        val calls = recentsStore.load()
        recentAdapter.submit(calls)
        tvRecentsEmpty.visibility = if (calls.isEmpty()) View.VISIBLE else View.GONE
        btnClearRecents.visibility =
            if (currentTab == TAB_RECENTS && calls.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmClearRecents() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recents_clear))
            .setMessage(getString(R.string.recents_clear_message))
            .setPositiveButton(getString(R.string.recents_clear)) { _, _ ->
                recentsStore.clear()
                refreshRecents()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ------------------------------------------------------------------ misc

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class DialKey(val digit: String, val letters: String)

    private companion object {
        const val STATE_DIALED = "dialed"
        const val KEY_CONTACTS_ASKED = "contacts_asked"
        const val MAX_DIAL_LENGTH = 20

        const val TAB_KEYPAD = 0
        const val TAB_CONTACTS = 1
        const val TAB_RECENTS = 2

        val VALID_KEYS = listOf(
            DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF"),
            DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
            DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
            DialKey("*", ""), DialKey("0", "+"), DialKey("#", "")
        )
    }
}
