package com.example.phonecurfew

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val REQUEST_CODE_ENABLE_ADMIN = 1

    private val requestIgnoreBatteryOptimizations =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        val numberPickerMinutes = findViewById<NumberPicker>(R.id.numberPickerMinutes)
        numberPickerMinutes.minValue = 1
        numberPickerMinutes.maxValue = 120

        val btnStart = findViewById<Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            } else {
                checkAdminAndStart()
            }
        }

        // Optional: Unsuspend button
        val btnUnsuspend = findViewById<Button>(R.id.btnUnsuspend)
        btnUnsuspend.setOnClickListener {
            unsuspendAllApps()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            checkAdminAndStart()
        }
    }

    private fun checkAdminAndStart() {
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "This app needs admin permission to lock your screen after the timer ends."
            )
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
        } else {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                requestIgnoreBatteryOptimizations.launch(intent)
            } else {
                startTimer()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (dpm.isAdminActive(adminComponent)) {
                Toast.makeText(this, "Admin enabled", Toast.LENGTH_SHORT).show()
                checkAdminAndStart()
            } else {
                Toast.makeText(this, "Admin not enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTimer() {
        val numberPickerMinutes = findViewById<NumberPicker>(R.id.numberPickerMinutes)
        val minutes = numberPickerMinutes.value
        val serviceIntent = Intent(this, TimerService::class.java)
        serviceIntent.putExtra("minutes", minutes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Timer started for $minutes minute(s)", Toast.LENGTH_SHORT).show()
    }

    private fun unsuspendAllApps() {
        if (dpm.isAdminActive(adminComponent)) {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val packagesToUnsuspend = ArrayList<String>()
            for (app in packages) {
                if (app.packageName == packageName) continue
                if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    packagesToUnsuspend.add(app.packageName)
                }
            }
            if (packagesToUnsuspend.isNotEmpty()) {
                dpm.setPackagesSuspended(adminComponent, packagesToUnsuspend.toTypedArray(), false)
                Toast.makeText(this, "Apps unsuspended", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Device Admin not active", Toast.LENGTH_SHORT).show()
        }
    }
}
