package com.example.phonecurfew

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TimerService : Service() {

    private lateinit var countDownTimer: CountDownTimer
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate() {
        super.onCreate()
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val minutes = intent?.getIntExtra("minutes", 0) ?: 0
        if (minutes <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification(minutes)

        val durationMillis = minutes * 60_000L
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateNotification(millisUntilFinished)
            }
            override fun onFinish() {
                lockScreen()
                stopForeground(true)
                stopSelf()
            }
        }.start()

        return START_NOT_STICKY
    }

    private fun lockScreenAndSuspendApps() {
    if (dpm.isAdminActive(adminComponent)) {
        // 1. Lock the screen
        dpm.lockNow()

        // 2. Suspend all user-installed packages except our own
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val packagesToSuspend = ArrayList<String>()

        for (app in packages) {
            // Skip our own app
            if (app.packageName == packageName) continue

            // Suspend only user-installed apps (not system apps)
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                packagesToSuspend.add(app.packageName)
            }
        }

        if (packagesToSuspend.isNotEmpty()) {
            dpm.setPackagesSuspended(adminComponent, packagesToSuspend.toTypedArray(), true)
        }

        // Optional: Show a notification that apps are suspended
        NotificationManagerCompat.from(this).notify(
            3,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Apps Suspended")
                .setContentText("YouTube, Instagram, and other apps have been stopped.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    } else {
        // Fallback if admin is not active
        NotificationManagerCompat.from(this).notify(
            2,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Timer finished")
                .setContentText("Please lock your phone manually.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        )
    }
}

    private fun startForegroundNotification(minutes: Int) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phone Curfew Active")
            .setContentText("Locking in $minutes minutes")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }

    private fun updateNotification(millisUntilFinished: Long) {
        val totalSeconds = millisUntilFinished / 1000
        val minutesLeft = totalSeconds / 60
        val secs = totalSeconds % 60
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phone Curfew Active")
            .setContentText("Locking in $minutesLeft:%02d".format(secs))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(this).notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "timer_service_channel"
    }
}
