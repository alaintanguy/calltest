package com.example.deviceproto

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactsHelper {
  fun isInContacts(context: Context, phoneNumber: String): Boolean {
    val uri = Uri.withAppendedPath(
      ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
      Uri.encode(phoneNumber)
    )
    val projection = arrayOf(ContactsContract.PhoneLookup._ID)
    context.contentResolver.query(uri, projection, null, null, null).use { c ->
      if (c == null) return false
      return c.moveToFirst()
    }
  }
}
