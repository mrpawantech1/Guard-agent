package com.guard.agent.security

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger
import java.io.File

object SelfUpdateManager {

    private const val TAG = "SelfUpdate"

    /**
     * Firebase se latest version check karo aur agar naya ho toh download karo.
     */
    fun checkAndUpdate(ctx: Context) {
        try {
            val ref = FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_UPDATES)

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val latestVersion = snap.child("latest_version").getValue(Int::class.java) ?: 0
                    val apkUrl = snap.child("apk_url").getValue(String::class.java) ?: ""
                    val currentVersion = getCurrentVersion(ctx)

                    Logger.d(TAG, "Current=$currentVersion Latest=$latestVersion")

                    if (latestVersion > currentVersion && apkUrl.isNotBlank()) {
                        downloadAndInstall(ctx, apkUrl)
                    } else {
                        Logger.d(TAG, "Already up to date")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Logger.e(TAG, "Update check cancelled", error.toException())
                }
            })
        } catch (e: Exception) {
            Logger.e(TAG, "checkAndUpdate failed", e)
        }
    }

    /**
     * APK download karo aur install karo.
     */
    private fun downloadAndInstall(ctx: Context, url: String) {
        try {
            // Unknown sources check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ctx.packageManager.canRequestPackageInstalls()) {
                    Logger.w(TAG, "Unknown sources permission needed")
                    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(i)
                    return
                }
            }

            val fileName = "update_${System.currentTimeMillis()}.apk"
            val destFile = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("System Update")
                setDescription("Downloading update...")
                setDestinationUri(Uri.fromFile(destFile))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            // Download complete hone pe install
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk(c, destFile)
                        try { c.unregisterReceiver(this) } catch (_: Exception) {}
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                ctx.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            Logger.d(TAG, "Download started: $downloadId")
        } catch (e: Exception) {
            Logger.e(TAG, "downloadAndInstall failed", e)
        }
    }

    private fun installApk(ctx: Context, file: File) {
        try {
            if (!file.exists()) return

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
            Logger.d(TAG, "Install intent sent")
        } catch (e: Exception) {
            Logger.e(TAG, "installApk failed", e)
        }
    }

    private fun getCurrentVersion(ctx: Context): Int {
        return try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
        } catch (_: Exception) { 0 }
    }
}
