package com.goujer.heartstreamer.service

import android.content.Intent
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.goujer.heartstreamer.PHONE_CAPABILITY
import com.goujer.heartstreamer.PHONE_KILL_PATH
import com.goujer.heartstreamer.PHONE_PATH
import com.goujer.heartstreamer.WEAR_KILL_PATH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MessageHRService : BaseHRService() {
	private val scope = CoroutineScope(Dispatchers.IO)

	private var messageNodeId: String? = null

	private val capabilityListener = OnCapabilityChangedListener {
		capabilityInfo: CapabilityInfo -> updateMessageCapability(capabilityInfo)
	}

	private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
		if (messageEvent.path == WEAR_KILL_PATH) {
			finish()
		}
	}

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			val capabilityInfo: CapabilityInfo = Tasks.await(
					Wearable.getCapabilityClient(this@MessageHRService)
							.getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE))
			updateMessageCapability(capabilityInfo)
		}
		Wearable.getCapabilityClient(this).addListener(capabilityListener, PHONE_CAPABILITY)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)

		//Start Wear Kill Listener Service
		Wearable.getMessageClient(this).addListener(messageListener)

		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		Wearable.getCapabilityClient(this).removeListener(capabilityListener)
		scope.cancel()
	}

	override fun finish() {
		messageNodeId?.also { nodeId ->
			val sendTask: Task<*> = Wearable.getMessageClient(this).sendMessage(
					nodeId,
					PHONE_KILL_PATH,
					"DiePotato".encodeToByteArray()
			).apply {
				addOnSuccessListener {
					//Log.i(TAG, "Successful Delivery")
				}
				addOnFailureListener {
					//Log.i(TAG, "Failure Delivery")
				}
			}
		}
		super.finish()
	}

	override fun broadcastHR() {
		super.broadcastHR()

		messageNodeId?.also { nodeId ->
			val sendTask: Task<*> = Wearable.getMessageClient(this).sendMessage(
					nodeId,
					PHONE_PATH,
					heartRate.toString().encodeToByteArray()
			).apply {
				addOnSuccessListener {
					//Log.i(TAG, "Successful Delivery")
				}
				addOnFailureListener {
					//Log.i(TAG, "Failure Delivery")
				}
			}
		}
	}

	private fun updateMessageCapability(capabilityInfo: CapabilityInfo) {
		val nodes = capabilityInfo.nodes
		// Find a nearby node or pick one arbitrarily
		messageNodeId = nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
	}

	companion object {
		private const val TAG = "MessageHRService"
	}
}