package com.example.data.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.example.core.compat.AndroidCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactItem(
    val contactId: Long,
    val displayName: String,
    val phoneNumber: String,
    val normalizedKey: String,
    val label: String = ""
)

class ContactsRepository(private val context: Context) {

    suspend fun loadContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
        if (!AndroidCompat.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            return@withContext emptyList()
        }
        val results = mutableListOf<ContactItem>()
        val seenKeys = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (number.isBlank()) continue
                    val normalized = normalizeNumberKey(number)
                    val dedupeKey = "$normalized|${cursor.getLong(idIdx)}"
                    if (!seenKeys.add(dedupeKey)) continue
                    val name = cursor.getString(nameIdx)?.trim().orEmpty().ifBlank { number }
                    val type = cursor.getInt(typeIdx)
                    val customLabel = cursor.getString(labelIdx)
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        customLabel
                    ).toString()
                    results.add(
                        ContactItem(
                            contactId = cursor.getLong(idIdx),
                            displayName = name,
                            phoneNumber = number,
                            normalizedKey = normalized,
                            label = typeLabel
                        )
                    )
                }
            }
        }
        results
    }

    suspend fun buildLookupMap(): Map<String, String> = withContext(Dispatchers.IO) {
        val map = HashMap<String, String>()
        for (contact in loadContacts()) {
            if (contact.normalizedKey.isNotEmpty() && !map.containsKey(contact.normalizedKey)) {
                map[contact.normalizedKey] = contact.displayName
            }
        }
        map
    }

    fun buildSaveOrEditContactIntent(phoneNumber: String): Intent {
        return Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        fun normalizeNumberKey(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.any { it.isLetter() }) {
                return trimmed.lowercase()
            }
            val digits = trimmed.filter { it.isDigit() }
            if (digits.isEmpty()) return trimmed.lowercase()
            return if (digits.length > 10) digits.takeLast(10) else digits
        }
    }
}
