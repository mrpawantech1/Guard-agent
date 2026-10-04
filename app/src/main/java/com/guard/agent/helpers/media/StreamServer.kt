package com.guard.agent.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.guard.agent.utils.Logger
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

object StreamServer {

    private const val TAG = "StreamServer"
    private const val PORT = 8080

    private var serverSocket: ServerSocket? = null
    private var isRunning = AtomicBoolean(false)
    private var latestFrame: ByteArray? = null

    /**
     * HTTP server start karo local port pe.
     * Browser se http://phone-ip:8080/ open karke latest frame dekh sakta hai.
     */
    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Logger.d(TAG, "Server started on port $PORT")

                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning.get()) Logger.e(TAG, "Accept failed", e)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Server failed", e)
            }
        }.start()
    }

    private fun handleClient(client: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val request = reader.readLine() ?: return@Thread

                val out: OutputStream = client.getOutputStream()

                when {
                    request.contains("GET / ") || request.contains("GET /latest") -> {
                        val frame = latestFrame
                        if (frame != null) {
                            val header = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: image/jpeg\r\n" +
                                    "Content-Length: ${frame.size}\r\n" +
                                    "Connection: close\r\n" +
                                    "Cache-Control: no-cache\r\n\r\n"
                            out.write(header.toByteArray())
                            out.write(frame)
                        } else {
                            val msg = "No frame available"
                            val header = "HTTP/1.1 503 Service Unavailable\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Content-Length: ${msg.length}\r\n\r\n"
                            out.write(header.toByteArray())
                            out.write(msg.toByteArray())
                        }
                    }

                    request.contains("GET /status") -> {
                        val status = if (latestFrame != null) "streaming" else "idle"
                        val header = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: ${status.length}\r\n\r\n"
                        out.write(header.toByteArray())
                        out.write(status.toByteArray())
                    }

                    else -> {
                        val msg = "Guard Stream Server\n\nEndpoints:\n/latest - Latest frame\n/status - Status"
                        val header = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: ${msg.length}\r\n\r\n"
                        out.write(header.toByteArray())
                        out.write(msg.toByteArray())
                    }
                }

                out.flush()
                out.close()
            } catch (e: Exception) {
                Logger.e(TAG, "handleClient failed", e)
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }.start()
    }

    /**
     * Latest frame update karo (streamer call karega).
     */
    fun updateFrame(bytes: ByteArray) {
        latestFrame = bytes
    }

    fun updateFrame(bitmap: Bitmap) {
        try {
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, bos)
            latestFrame = bos.toByteArray()
        } catch (e: Exception) {
            Logger.e(TAG, "updateFrame failed", e)
        }
    }

    /**
     * Server band karo.
     */
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        latestFrame = null
        Logger.d(TAG, "Server stopped")
    }

    fun isRunning(): Boolean = isRunning.get()
}
