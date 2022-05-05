package com.goujer.heartstreamer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.goujer.heartstreamer.KILL_ACTION
import com.goujer.heartstreamer.common.R

abstract class BaseService : Service() {
	private lateinit var wakeLock : PowerManager.WakeLock

	protected var heartRate = 0

	private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent) {
			if (intent.action == KILL_ACTION){
				finish()
			}
		}
	}

	//Overrides
	override fun onCreate() {
		super.onCreate()

		registerReceiver(broadcastReceiver, IntentFilter(KILL_ACTION))

		wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run{
			newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartStreamer::BackgroundStreaming").apply{
				acquire(10*60*1000L)
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)
		//Create Notification Channel
		if (Build.VERSION.SDK_INT >= 26) {
			val serviceChannel = NotificationChannel(getString(R.string.service_notification_channel_id), getString(R.string.service_notification_name), NotificationManager.IMPORTANCE_LOW)
			getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
		}

		//Notification Intents
		//val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
		val openIntent = PendingIntent.getActivity(this, 0, Intent(this, Class.forName("com.goujer.heartstreamer.activity.MainActivity")), PendingIntent.FLAG_IMMUTABLE)
		val killIntent = PendingIntent.getBroadcast(this, 12345, Intent(KILL_ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

		// Notification
		val notification = NotificationCompat.Builder(this, getString(R.string.service_notification_channel_id))
				.setContentTitle(getString(R.string.app_name))
				.setContentText(getString(R.string.service_notification_text))
				.setSmallIcon(R.drawable.ic_stat_name)
				.setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
				.addAction(NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.text_stop), killIntent))
				.setContentIntent(openIntent)
				.build()


		startForeground(1, notification)

		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()

		unregisterReceiver(broadcastReceiver)
		wakeLock.release()
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	//New Functions
	protected abstract fun broadcastHR()

	protected open fun finish() {
		stopSelf()
		android.os.Process.killProcess(android.os.Process.myPid())
	}

	companion object {
		private const val TAG = "BaseService"
	}
}