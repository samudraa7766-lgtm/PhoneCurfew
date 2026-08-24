#!/bin/bash
set -e

# Ensure required directories exist
mkdir -p app/src/main/java/com/example/phonecurfew
mkdir -p app/src/main/res/layout
mkdir -p app/src/main/res/values

echo "==> Creating MatrixRainView.kt..."
cat > app/src/main/java/com/example/phonecurfew/MatrixRainView.kt <<'EOF'
package com.example.phonecurfew

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class MatrixRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN
        textSize = 40f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val columns = 50   // number of falling columns (will be adjusted)
    private val fontSize = 40f
    private val speed = 15     // lower = faster

    private var drops = IntArray(columns) { Random.nextInt(20) }
    private val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, speed.toLong())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(runnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(runnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val newColumns = (w / fontSize).toInt()
        drops = IntArray(newColumns) { Random.nextInt(20) }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        for (i in drops.indices) {
            val char = charSet[Random.nextInt(charSet.length)]
            val x = i * fontSize
            val y = drops[i] * fontSize

            canvas.drawText(char.toString(), x, y, paint)

            if (y > height && Random.nextInt(20) > 18) {
                drops[i] = 0
            }
            drops[i]++
        }
    }
}
EOF

echo "==> Updating activity_main.xml..."
cat > app/src/main/res/layout/activity_main.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.example.phonecurfew.MatrixRainView
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:padding="24dp"
        android:gravity="center">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Lock phone after (minutes):"
            android:textSize="18sp"
            android:textColor="#00FF00"
            android:shadowColor="#00FF00"
            android:shadowDx="0"
            android:shadowDy="0"
            android:shadowRadius="10" />

        <NumberPicker
            android:id="@+id/numberPickerMinutes"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:theme="@style/NumberPickerStyle" />

        <Button
            android:id="@+id/btnStart"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="Start Timer"
            android:textColor="#00FF00"
            android:backgroundTint="#003300" />
    </LinearLayout>
</FrameLayout>
EOF

echo "==> Updating MainActivity.kt..."
cat > app/src/main/java/com/example/phonecurfew/MainActivity.kt <<'EOF'
package com.example.phonecurfew

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
}
EOF

echo "==> Updating themes.xml..."
cat > app/src/main/res/values/themes.xml <<'EOF'
<resources>
    <style name="Theme.PhoneCurfew" parent="Theme.AppCompat.NoActionBar">
        <item name="colorPrimary">#00FF00</item>
        <item name="colorPrimaryDark">#000000</item>
        <item name="colorAccent">#00FF00</item>
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">#000000</item>
    </style>

    <style name="NumberPickerStyle" parent="android:Widget.NumberPicker">
        <item name="android:textColor">#00FF00</item>
        <item name="android:colorAccent">#00FF00</item>
    </style>
</resources>
EOF

echo ""
echo "✅ All changes applied successfully."
echo "Now rebuild the project (or push to GitHub to trigger automatic build)."
