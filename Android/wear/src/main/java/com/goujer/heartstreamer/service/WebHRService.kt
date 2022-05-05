package com.goujer.heartstreamer.service

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.html.*

class WebHRService : BaseHRService() {
	private val scope = CoroutineScope(Dispatchers.IO)
	private lateinit var engine: NettyApplicationEngine

	private lateinit var cm: ConnectivityManager

	val networkCallback = object : ConnectivityManager.NetworkCallback() {
		override fun onAvailable(network: Network) {
			super.onAvailable(network)
			// The Wi-Fi network has been acquired, bind it to use this network by default
			cm.bindProcessToNetwork(network)
		}
	}

	override fun onCreate() {
		super.onCreate()

		cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

		//Bind To Wi-Fi or Ethernet Network
		cm.requestNetwork(
				NetworkRequest.Builder()
						.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
						.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(), networkCallback)
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
		return START_STICKY
	}

	override fun onDestroy() {
		//stop webserver on destroy of service or process
		engine.stop(1000, 1000)

		super.onDestroy()

		cm.bindProcessToNetwork(null)
		cm.unregisterNetworkCallback(networkCallback)
	}
}