package com.mzazi.phone

import android.content.Context
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/**
 * Reads the call-capable phone accounts (SIM slots) so the dialler can show one
 * call button per SIM and route a call to a specific one.
 */
class SimManager(private val context: Context) {

    fun accounts(): List<PhoneAccountHandle> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return try {
            telecom.callCapablePhoneAccounts.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun label(handle: PhoneAccountHandle): String {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return "SIM"
        return try {
            telecom.getPhoneAccount(handle)?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM"
        } catch (e: Exception) {
            "SIM"
        }
    }

    /** "1" or "2" for the badge next to a call, "•" when unknown. */
    fun slotOf(accountId: String?): String {
        if (accountId.isNullOrBlank()) return "•"
        val list = accounts()
        val index = list.indexOfFirst { it.id == accountId }
        return if (index >= 0) (index + 1).toString() else "•"
    }

    fun slotIndexOf(accountId: String?): Int {
        if (accountId.isNullOrBlank()) return -1
        return accounts().indexOfFirst { it.id == accountId }
    }

    /**
     * Places the call on a specific SIM. Returns false when the platform could
     * not take it (no permission, no SIM, no telephony), so callers can fall back.
     */
    fun placeCall(number: String, handle: PhoneAccountHandle?): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            val extras = Bundle()
            if (handle != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
            telecom.placeCall(android.net.Uri.fromParts("tel", number, null), extras)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
