package com.mzazi.phone

import android.content.Context
import android.provider.ContactsContract
import java.util.Locale

data class PhoneNumber(val number: String, val label: String)

data class Contact(
    val id: Long,
    val name: String,
    val numbers: List<PhoneNumber>,
    val photoUri: String?
) {
    val primaryNumber: String get() = numbers.firstOrNull()?.number.orEmpty()
}

/**
 * Reads the device address book through [ContactsContract]. Returns an empty
 * list (never throws) when the permission is missing or the provider is empty.
 */
class ContactsRepository(private val context: Context) {

    fun load(): List<Contact> {
        val byId = LinkedHashMap<Long, MutableList<PhoneNumber>>()
        val names = HashMap<Long, String>()
        val photos = HashMap<Long, String?>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val numberIndex = cursor.getColumnIndexOrThrow(projection[2])
                val typeIndex = cursor.getColumnIndexOrThrow(projection[3])
                val labelIndex = cursor.getColumnIndexOrThrow(projection[4])
                val photoIndex = cursor.getColumnIndexOrThrow(projection[5])

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (number.isEmpty()) continue

                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val type = cursor.getInt(typeIndex)
                    val customLabel = cursor.getString(labelIndex)
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources, type, customLabel
                    ).toString()

                    byId.getOrPut(id) { ArrayList() }.add(PhoneNumber(number, label))
                    names[id] = if (name.isEmpty()) T9.formatNumber(number) else name
                    photos[id] = cursor.getString(photoIndex)
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }

        return byId.map { (id, numbers) ->
            Contact(
                id = id,
                name = names[id] ?: T9.formatNumber(numbers.first().number),
                numbers = numbers.distinctBy { it.number },
                photoUri = photos[id]
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}

/** Case-insensitive helper used by the search field. */
fun String.normalizedForSearch(): String = trim().lowercase(Locale.getDefault())
