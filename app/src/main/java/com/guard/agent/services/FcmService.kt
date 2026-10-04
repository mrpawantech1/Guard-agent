package com.guard.agent.services

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.guard.agent.command.CommandHandler
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.utils.Logger

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Logger.d("FcmService", "New token: $token")
        try {
            val key = DeviceKey.get(this)
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(key).child(Constants.SUB_FCM_TOKEN)
                .setValue(token)
        } catch (e: Exception) {
            Logger.e("FcmService", "Token save failed", e)
        }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        super.onMessageReceived(msg)
        Logger.d("FcmService", "FCM message: ${msg.data}")

        val cmd = msg.data["cmd"] ?: return
        try {
            CommandHandler.handle(applicationContext, cmd)
        } catch (e: Exception) {
            Logger.e("FcmService", "Command failed: $cmd", e)
        }
    }
}
