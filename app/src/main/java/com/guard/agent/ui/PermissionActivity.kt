package com.guard.agent.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.guard.agent.R
import com.guard.agent.utils.PermissionUtils
import com.guard.agent.utils.toast

class PermissionActivity : AppCompatActivity() {

    private val REQ_PERMS = 1001
    private lateinit var permList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        permList = findViewById(R.id.permList)
        val btnGrantAll = findViewById<Button>(R.id.btnGrantAll)

        buildPermissionList()
        btnGrantAll.setOnClickListener { requestAll() }
    }

    private fun buildPermissionList() {
        permList.removeAllViews()
        val missing = PermissionUtils.getMissingSpecialPermissions(this)

        // Runtime permissions check
        if (!PermissionUtils.hasAllRuntimePermissions(this)) {
            addPermissionRow("Runtime Permissions (Camera, Mic, Location, etc)", false)
        } else {
            addPermissionRow("Runtime Permissions", true)
        }

        missing.forEach { addPermissionRow(it, false) }
    }

    private fun addPermissionRow(name: String, granted: Boolean) {
        val row = TextView(this).apply {
            text = if (granted) "✓ $name" else "✗ $name"
            textSize = 16f
            setPadding(16, 24, 16, 24)
            setTextColor(
                if (granted) 0xFF10B981.toInt() else 0xFFEF4444.toInt()
            )
        }
        permList.addView(row)
    }

    private fun requestAll() {
        // 1. Runtime permissions
        ActivityCompat.requestPermissions(
            this,
            PermissionUtils.getRuntimePermissions(),
            REQ_PERMS
        )

        // 2. Admin
        if (!PermissionUtils.isAdminActive(this)) {
            toast("Enable Device Admin from Main screen")
        }

        // 3. Accessibility
        if (!PermissionUtils.isAccessibilityEnabled(this)) {
            toast("Enable Accessibility from Settings")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            val denied = grantResults.count { it != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (denied == 0) {
                toast("All permissions granted ✓")
            } else {
                toast("$denied permission(s) denied")
            }
            buildPermissionList()
        }
    }

    override fun onResume() {
        super.onResume()
        buildPermissionList()
    }
}
