package com.example.deviceproto

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactsUtils {

    /** Returns true if [phoneNumber] belongs to a starred (favorite) contact. */
    fun isNumberFavorited(context: Context, phoneNumber: String): Boolean {
        val cr = context.contentResolver

        // 1) Resolve contactId from number
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        var contactId: Long? = null
        cr.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) contactId = c.getLong(0)
        }
        if (contactId == null) return false

        // 2) Check STARRED flag for that contact
        var isStarred = false
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.STARRED),
            "${ContactsContract.Contacts._ID}=?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            if (c.moveToFirst()) isStarred = (c.getInt(0) == 1)
        }
        return isStarred
    }
}
