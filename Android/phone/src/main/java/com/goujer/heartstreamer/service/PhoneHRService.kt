package com.goujer.heartstreamer.service

import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.goujer.heartstreamer.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.html.*

data class Request(val bpm: Int)

data class HeartRateData(val heartRate: Int, val timeStamp: Long)

class PhoneHRService : BaseService() {
	private val scope = CoroutineScope(Dispatchers.IO)
	private val mapMutex = Mutex()
	private lateinit var engine: NettyApplicationEngine

	private val connectedNodeIds = mutableListOf<String>()
	private val heartRateMap = mutableMapOf<String, HeartRateData>()

	private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { capabilityInfo: CapabilityInfo ->
		updateMessageCapability(capabilityInfo)
	}

	private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
		if (messageEvent.path == PHONE_PATH) {
			//Log.i(TAG, "Received Message")
			scope.launch {
				updateHR(messageEvent.sourceNodeId, messageEvent.data.decodeToString().toInt())
			}
		} else if (messageEvent.path == PHONE_KILL_PATH) {
			finish()
		}
	}

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			val capabilityInfo: CapabilityInfo = Tasks.await(
					Wearable.getCapabilityClient(this@PhoneHRService)
							.getCapability(WEAR_KILL_CAPABILITY, CapabilityClient.FILTER_REACHABLE))
			updateMessageCapability(capabilityInfo)
		}
		Wearable.getCapabilityClient(this).addListener(capabilityListener, WEAR_KILL_CAPABILITY)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)

		// Start Web Server
		scope.launch {
			engine = embeddedServer(Netty, 12345) {
				install(StatusPages) {
					exception<Throwable> { e ->
						call.respondText(e.localizedMessage!!, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
					}
				}
				install(ContentNegotiation) {
					gson {}
				}
				routing {
					get("/") {
						call.respondHtml {
							head {
								title {
									+"Heart Streamer"
								}
								styleLink("/static/styles.css")
								script(src = "/static/script.js") {}
							}
							body {
								div("container") {
									img(src = "static/heart.png") {
										alt = "Heart"
									}
									div("textContainer") {
										p {
											id = "bpm"
											+heartRate
										}
									}
								}
							}
						}
					}

					post("/") {
						val request = call.receive<Request>()
						//Log.i(TAG, "Received POST request: $request")
						updateHR("fitbit", request.bpm)
						call.respond(request)
					}

					get("/hr") {
						call.respondText(heartRate.toString(), ContentType.Text.Plain, status = HttpStatusCode.OK)
					}

					static("static") {
						resource("heart.png")
						resource("styles.css")
						resource("script.js")
					}
				}
			}.start()
		}
		//Start Wear Service
		Wearable.getMessageClient(this).addListener(messageListener)

		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		//stop webserver on destroy of service or process
		engine.stop(1000, 1000)

		scope.cancel()

		//Stop Wear Service
		Wearable.getMessageClient(this).removeListener(messageListener)
	}

	override fun finish() {
		for (nodeId in connectedNodeIds) {
			val sendTask: Task<*> = Wearable.getMessageClient(this).sendMessage(
					nodeId,
					WEAR_KILL_PATH,
					"DiePotato".encodeToByteArray()
			).apply {
				addOnSuccessListener {
					Log.i(TAG, "Successful Delivery")
				}
				addOnFailureListener {
					Log.i(TAG, "Failure Delivery")
				}
			}
		}
		Thread.sleep(100)
		super.finish()
	}

	//Helpers
	override fun broadcastHR() {
		val updateHRIntent = Intent(UPDATE_HR_ACTION)
		updateHRIntent.putExtra("bpm", heartRate)
		this.sendBroadcast(updateHRIntent)
	}

	private suspend fun updateHR(senderID: String, heartRate: Int) {
		mapMutex.withLock {
			//Add HR to Map
			heartRateMap[senderID] = HeartRateData(heartRate, System.currentTimeMillis())

			//Remove old HRs from map
			for (key in heartRateMap.keys) {
				val currentTimeStamp = System.currentTimeMillis()
				if (currentTimeStamp - heartRateMap[key]!!.timeStamp >= 60000) {
					heartRateMap.remove(key)
				}
			}

			//Calculate Average HR
			var heartRateTotal = 0
			for (hrd in heartRateMap.values) {
				heartRateTotal += hrd.heartRate
			}
			this.heartRate = heartRateTotal / heartRateMap.size

			withContext(Dispatchers.Main) {
				broadcastHR()
			}
		}
	}

	private fun updateMessageCapability(capabilityInfo: CapabilityInfo) {
		for (node in capabilityInfo.nodes) {
			connectedNodeIds.add(node.id)
		}
	}

	companion object {
		private const val TAG = "PhoneHRService"
	}
}