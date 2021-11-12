package com.goujer.heartstreamer.service

import android.content.Intent
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.goujer.heartstreamer.UPDATE_HR_ACTION
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.html.*

data class Request(val bpm: Int)

data class HeartRateData(val heartRate: Int, val timeStamp: Long)

class PhoneHRService : BaseService() {
	private val scope = CoroutineScope(Dispatchers.IO)
	private lateinit var engine: NettyApplicationEngine

	private val heartRateMap = mutableMapOf<String, HeartRateData>()

	private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
		if (messageEvent.path == MESSAGE_PATH) {
			//Log.i(TAG, "Received Message")
			updateHRMap(messageEvent.sourceNodeId, messageEvent.data.decodeToString().toInt())
			broadcastHR()
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)

		// Start Web Server
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
					updateHRMap("fitbit", request.bpm)
					broadcastHR()
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

		//Start Wear Service
		Wearable.getMessageClient(this).addListener(messageListener)

		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		//stop webserver on destroy of service or process
		engine.stop(1000, 1000)

		//Stop Wear Service
		Wearable.getMessageClient(this).removeListener(messageListener)
	}

	//Helpers
	override fun broadcastHR() {
		val updateHRIntent = Intent(UPDATE_HR_ACTION)
		updateHRIntent.putExtra("bpm", heartRate)
		this.sendBroadcast(updateHRIntent)
	}

	private fun updateHRMap(senderID: String, heartRate: Int) {
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
	}

	companion object {
		private const val TAG = "PhoneHRService"
		private const val MESSAGE_PATH = "/heart_streamer_phone"
	}
}