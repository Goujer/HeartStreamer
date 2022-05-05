package com.goujer.heartstreamer.service

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.goujer.heartstreamer.UPDATE_HR_ACTION
import kotlin.math.roundToInt

open class BaseHRService : BaseService(), SensorEventListener {
	private lateinit var heartRateSensor: Sensor
	private lateinit var sensorManager : SensorManager

	private var sensorAccuracy = SensorManager.SENSOR_STATUS_NO_CONTACT

	override fun onCreate() {
		super.onCreate()
		sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
		heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)

		// Register Heartrate Listener
		heartRateSensor.also { heartRate ->
			sensorManager.registerListener(this, heartRate, 272727)	// Based on maximum 220bpm, one update per beat
		}

		return START_STICKY
	}

	override fun onDestroy() {
		sensorManager.unregisterListener(this)
		super.onDestroy()
	}

	//SensorListener
	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
		sensorAccuracy = accuracy
	}

	override fun onSensorChanged(event: SensorEvent?) {
		if (sensorAccuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) return
		val newHeartRate: Int = event?.values?.get(0)?.roundToInt() ?: return
		if (newHeartRate == heartRate) return else heartRate = newHeartRate	// Only broadcast if HR changes

		broadcastHR()
	}

	override fun broadcastHR() {
		val updateHRIntent = Intent(UPDATE_HR_ACTION)
		updateHRIntent.putExtra("bpm", heartRate)
		updateHRIntent.putExtra("accuracy", sensorAccuracy)
		this.sendBroadcast(updateHRIntent)
	}

	companion object {
		private const val TAG = "BaseHRService"
	}
}