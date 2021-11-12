package com.goujer.heartstreamer.service

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
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

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			val capabilityInfo: CapabilityInfo = Tasks.await(
					Wearable.getCapabilityClient(this@MessageHRService)
							.getCapability(CAPABILITY_NAME, CapabilityClient.FILTER_REACHABLE))
			updateMessageCapability(capabilityInfo)
		}
		Wearable.getCapabilityClient(this).addListener(capabilityListener, CAPABILITY_NAME)
	}

	override fun onDestroy() {
		super.onDestroy()
		Wearable.getCapabilityClient(this).removeListener(capabilityListener)
		scope.cancel()
	}

	override fun broadcastHR() {
		super.broadcastHR()

		messageNodeId?.also { nodeId ->
			val sendTask: Task<*> = Wearable.getMessageClient(this).sendMessage(
					nodeId,
					MESSAGE_PATH,
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
		private const val CAPABILITY_NAME = "heart_streamer_phone"
		private const val MESSAGE_PATH = "/heart_streamer_phone"
	}
}