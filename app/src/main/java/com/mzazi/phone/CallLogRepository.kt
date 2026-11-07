package com.mzazi.phone

import android.content.Context
import android.provider.CallLog

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val accountId: String?
) {
    val isMissed: Boolean
        get() = type == CallLog.Calls.MISSED_TYPE ||
            type == CallLog.Calls.REJECTED_TYPE ||
            type == CallLog.Calls.BLOCKED_TYPE
}

/**
 * Reads the system call log. Available to this app because it holds the default
 * dialer role; returns an empty list instead of throwing when it does not.
 */
class CallLogRepository(private val context: Context) {

    fun load(limit: Int = 400): List<CallEntry> {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val entries = ArrayList<CallEntry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val accountIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (cursor.moveToNext() && entries.size < limit) {
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    entries.add(
                        CallEntry(
                            id = cursor.getLong(idIndex),
                            number = number,
                            name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() },
                            type = cursor.getInt(typeIndex),
                            date = cursor.getLong(dateIndex),
                            duration = cursor.getLong(durationIndex),
                            accountId = if (accountIndex >= 0) cursor.getString(accountIndex) else null
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        return entries
    }

    fun clear() {
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (e: Exception) {
            // Ignored: clearing the system log needs WRITE_CALL_LOG and user consent.
        }
    }
}
