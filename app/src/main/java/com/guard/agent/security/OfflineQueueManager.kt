package com.guard.agent.security

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guard.agent.core.Constants
import com.guard.agent.core.PrefsManager
import com.guard.agent.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

object OfflineQueueManager {

    private const val TAG = "OfflineQueue"
    private const val KEY_QUEUE = "offline_queue"

    /**
     * Command ko local queue mein save karo (jab internet na ho).
     */
    fun enqueue(cmd: String) {
        try {
            val queue = getQueue().toMutableList()
            queue.add(cmd)
            saveQueue(queue)
            Logger.d(TAG, "Enqueued: $cmd (total=${queue.size})")
        } catch (e: Exception) {
            Logger.e(TAG, "enqueue failed", e)
        }
    }

    /**
     * Pending commands ko Firebase pe flush karo.
     */
    fun flush(ctx: Context, deviceKey: String) {
        try {
            val queue = getQueue()
            if (queue.isEmpty()) return

            Logger.d(TAG, "Flushing ${queue.size} commands")

            val ref = FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_OFFLINE_QUEUE).child(deviceKey)

            queue.forEach { cmd ->
                ref.push().setValue(
                    mapOf(
                        "cmd" to cmd,
                        "time" to System.currentTimeMillis()
                    )
                )
            }

            saveQueue(emptyList())
        } catch (e: Exception) {
            Logger.e(TAG, "flush failed", e)
        }
    }

    /**
     * Firebase se remote commands queue fetch karo (jab panel se bulk bheje).
     */
    fun fetchRemoteQueue(ctx: Context, deviceKey: String, onCommand: (String) -> Unit) {
        try {
            val ref = FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_OFFLINE_QUEUE).child(deviceKey)

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    snap.children.forEach { child ->
                        val cmd = child.child("cmd").getValue(String::class.java)
                        if (!cmd.isNullOrBlank()) {
                            onCommand(cmd)
                            child.ref.removeValue()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Logger.e(TAG, "fetchRemoteQueue cancelled", error.toException())
                }
            })
        } catch (e: Exception) {
            Logger.e(TAG, "fetchRemoteQueue failed", e)
        }
    }

    private fun getQueue(): List<String> {
        return try {
            val json = PrefsManager.getString(KEY_QUEUE) ?: return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveQueue(queue: List<String>) {
        try {
            val arr = JSONArray()
            queue.forEach { arr.put(it) }
            PrefsManager.putString(KEY_QUEUE, arr.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "saveQueue failed", e)
        }
    }

    fun clear() {
        PrefsManager.remove(KEY_QUEUE)
    }
}
