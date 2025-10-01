package com.example.deviceproto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.annotation.RequiresPermission

object ContactsUtils {

    private val PROJECTION_ID = arrayOf(ContactsContract.PhoneLookup.CONTACT_ID)
    private val PROJECTION_STARRED = arrayOf(ContactsContract.Contacts.STARRED)

    /**
     * Returns true if [phoneNumber] belongs to a starred (favorite) contact.
     *
     * Requires READ_CONTACTS at runtime.
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    @SuppressLint("Recycle") // handled by `use { }`
    fun isNumberFavorited(context: Context, phoneNumber: String): Boolean {
        val cr = context.contentResolver

        // 1) Resolve contactId from number via PhoneLookup (handles formatting variants)
        val lookupUri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        val contactId: Long = cr.query(
            lookupUri,
            PROJECTION_ID,
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else return false
        } ?: return false

        // 2) Check STARRED flag for that contact id
        val isStarred = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            PROJECTION_STARRED,
            "${ContactsContract.Contacts._ID}=?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            c.moveToFirst() && c.getInt(0) == 1
        } ?: false

        return isStarred
    }
}
