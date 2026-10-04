package com.guard.agent.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.guard.agent.R
import com.guard.agent.core.Constants
import com.guard.agent.core.PrefsManager
import com.guard.agent.utils.toast

class SetupActivity : AppCompatActivity() {

    private var currentStep = 0
    private val totalSteps = 8

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStepCounter: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDesc: TextView
    private lateinit var btnAction: Button
    private lateinit var btnSkip: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        progressBar = findViewById(R.id.progressBar)
        tvStepCounter = findViewById(R.id.tvStepCounter)
        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepDesc = findViewById(R.id.tvStepDesc)
        btnAction = findViewById(R.id.btnAction)
        btnSkip = findViewById(R.id.btnSkip)

        progressBar.max = totalSteps

        btnAction.setOnClickListener { onActionClick() }
        btnSkip.setOnClickListener { nextStep() }

        showStep()
    }

    private fun onActionClick() {
        when (currentStep) {
            0 -> openPermissionActivity()
            1 -> openAdminSettings()
            2 -> openBatterySettings()
            3 -> openAutostartSettings()
            4 -> openAccessibilitySettings()
            5 -> openNotificationSettings()
            6 -> openBackgroundLocationSettings()
            7 -> finishSetup()
        }
    }

    private fun showStep() {
        progressBar.progress = currentStep + 1
        tvStepCounter.text = "Step ${currentStep + 1} / $totalSteps"

        when (currentStep) {
            0 -> {
                tvStepTitle.text = getString(R.string.wizard_step1)
                tvStepDesc.text = "Grant basic permissions (Camera, Mic, Location, Phone, SMS, Contacts)"
                btnAction.text = "Grant Permissions"
            }
            1 -> {
                tvStepTitle.text = getString(R.string.wizard_step2)
                tvStepDesc.text = "Enable Device Admin for remote lock and wipe."
                btnAction.text = "Open Admin Settings"
            }
            2 -> {
                tvStepTitle.text = getString(R.string.wizard_step3)
                tvStepDesc.text = "Disable battery optimization so service stays alive."
                btnAction.text = "Open Battery Settings"
            }
            3 -> {
                tvStepTitle.text = getString(R.string.wizard_step4)
                tvStepDesc.text = "Enable Autostart for ColorOS. Settings → Apps → App Management → [App] → Autostart ON"
                btnAction.text = "Open App Settings"
            }
            4 -> {
                tvStepTitle.text = getString(R.string.wizard_step5)
                tvStepDesc.text = "Enable Accessibility Service.\n\n" +
                        "⚠️ Sideloaded APK pe pehle:\n" +
                        "Settings → Apps → [App] → 3-dot menu → Allow restricted settings"
                btnAction.text = "Open Accessibility"
            }
            5 -> {
                tvStepTitle.text = getString(R.string.wizard_step6)
                tvStepDesc.text = "Enable Notification Access."
                btnAction.text = "Open Notification Access"
            }
            6 -> {
                tvStepTitle.text = getString(R.string.wizard_step7)
                tvStepDesc.text = "Set Location to 'Allow all the time'.\n" +
                        "Settings → Apps → [App] → Permissions → Location → Allow all the time"
                btnAction.text = "Open Location Settings"
            }
            7 -> {
                tvStepTitle.text = getString(R.string.wizard_step8)
                tvStepDesc.text = "All set! Tap Done to hide icon and start service."
                btnAction.text = getString(R.string.wizard_done)
            }
        }
    }

    private fun nextStep() {
        if (currentStep < totalSteps - 1) {
            currentStep++
            showStep()
        } else {
            finishSetup()
        }
    }

    private fun openPermissionActivity() {
        startActivity(Intent(this, PermissionActivity::class.java))
    }

    private fun openAdminSettings() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openBatterySettings() {
        try {
            val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    private fun openAutostartSettings() {
        try {
            val i = Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            startActivity(i)
        } catch (_: Exception) {
            try {
                val i = Intent().apply {
                    setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                }
                startActivity(i)
            } catch (_: Exception) {
                toast("Manually open: Settings → Apps → App Management → Autostart")
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openNotificationSettings() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    private fun openBackgroundLocationSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
        })
    }

    private fun finishSetup() {
        PrefsManager.putBoolean(Constants.KEY_SETUP_DONE, true)
        toast("Setup complete ✓")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
