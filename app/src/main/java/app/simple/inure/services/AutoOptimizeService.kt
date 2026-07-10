package app.simple.inure.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import app.simple.inure.R
import app.simple.inure.helpers.ShizukuServiceHelper
import app.simple.inure.preferences.AutoOptimizePreferences
import app.simple.inure.preferences.ConfigurationPreferences
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

class AutoOptimizeService : Service() {

    private val notificationId = 202
    private val channelId = "inure_auto_optimize"

    private var packageReceiver: BroadcastReceiver? = null
    private var shizukuHelper: ShizukuServiceHelper? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        registerPackageReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !AutoOptimizePreferences.isAutoOptimizeEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterPackageReceiver()
        shizukuHelper?.unbindUserService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun startForegroundService() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.auto_optimize_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_battery_charging)
            .setContentTitle(getString(R.string.auto_optimize_notification_title))
            .setContentText(getString(R.string.auto_optimize_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .build()
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }
        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                    val packageName = intent.data?.schemeSpecificPart ?: return
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (!isReplacing) {
                        Log.d(TAG, "New app installed: $packageName")
                        optimizePackage(packageName)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageReceiver, filter)
        }
    }

    private fun unregisterPackageReceiver() {
        packageReceiver?.let { unregisterReceiver(it) }
        packageReceiver = null
    }

    private fun optimizePackage(packageName: String) {
        Thread {
            try {
                val command = "dumpsys deviceidle whitelist -$packageName"
                Log.d(TAG, "Running: $command")

                if (ConfigurationPreferences.isUsingRoot()) {
                    runRootCommand(command)
                } else if (ConfigurationPreferences.isUsingShizuku()) {
                    runShizukuCommand(command)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to optimize $packageName", e)
            }
        }.start()
    }

    private fun runRootCommand(command: String) {
        Shell.cmd(command).exec().let { result ->
            if (result.isSuccess) {
                Log.d(TAG, "Root success: ${result.out}")
            } else {
                Log.e(TAG, "Root failed: ${result.err}")
            }
        }
    }

    private fun runShizukuCommand(command: String) {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku not available")
            return
        }

        if (shizukuHelper == null) {
            shizukuHelper = ShizukuServiceHelper.getInstance()
        }

        shizukuHelper!!.bindUserService {
            try {
                val result = shizukuHelper!!.service!!.simpleExecute(command)
                Log.d(TAG, "Shizuku result: ${result.output}")
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku command failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "AutoOptimizeService"
        private const val ACTION_STOP = "app.simple.inure.action.STOP_AUTO_OPTIMIZE"

        fun start(context: Context) {
            val intent = Intent(context, AutoOptimizeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutoOptimizeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
