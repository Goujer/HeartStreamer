package com.goujer.heartstreamer.activity

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import com.goujer.heartstreamer.R
import com.goujer.heartstreamer.UPDATE_HR_ACTION
import com.goujer.heartstreamer.databinding.ActivityMainBinding
import com.goujer.heartstreamer.service.PhoneHRService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class MainActivity : Activity() {
	private val scope = CoroutineScope(Dispatchers.IO)
	private lateinit var binding: ActivityMainBinding

	private lateinit var cm: ConnectivityManager
	private lateinit var cc: CapabilityClient

	private val intentFilter = IntentFilter(UPDATE_HR_ACTION)

	private var broadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			val hr: Any = intent?.extras?.get("bpm") ?: return
			binding.textHR.text = hr.toString()
		}
	}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
	    binding = ActivityMainBinding.inflate(layoutInflater)
	    setContentView(binding.root)

	    binding.layoutIndividualHR.visibility = View.GONE

	    //Managers
	    cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
	    cc = Wearable.getCapabilityClient(this)

        // Devices with a display should not go to sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

	    //Zero out HR
	    binding.textHR.text = "000"

	    launchWearApp()
    }

	override fun onStart() {
		super.onStart()
		registerReceiver(broadcastReceiver, intentFilter)

		if (!isHRServiceRunning()) {
			Intent(this, PhoneHRService::class.java).also { intent ->
				startService(intent)
			}
		}
	}

	override fun onResume() {
		super.onResume()

		//IP Address
		updateIPAddress()
	}

	override fun onStop() {
		super.onStop()
		unregisterReceiver(broadcastReceiver)
		Toast.makeText(this, getString(R.string.text_background_streaming), Toast.LENGTH_LONG).show()
	}

	//Helpers
	private fun updateIPAddress() {
		var ipText = ""
		val linkProperties = cm.getLinkProperties(cm.activeNetwork)
		if (linkProperties != null) {
			var newLine = false
			for (address in linkProperties.linkAddresses) {
				val addr = address.address
				if (!addr.isLoopbackAddress) {
					val sAddr: String = addr.hostAddress ?: continue
					val isIPv4 = sAddr.indexOf(':') < 0
					if (newLine) {
						ipText += "\n"
					}
					ipText += if (isIPv4) {
						"http://$sAddr:12345"
					} else {
						val delim = sAddr.indexOf('%')
						"http://[" + (if (delim < 0) sAddr else sAddr.substring(0, delim)) + "]:12345"
					}
					newLine = true
				}
			}
		}
		if (ipText.isEmpty()) {
			binding.textIpAddress.text = getString(R.string.text_no_ip_found)
		} else {
			binding.textIpAddress.text = ipText
		}
	}

	private fun isHRServiceRunning(): Boolean {
		val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
		for (service in manager.getRunningServices(Int.MAX_VALUE)) {
			val serviceName = service.service.className
			if (PhoneHRService::class.java.name == serviceName) {
				return true
			}
		}
		return false
	}

	private fun launchWearApp() {
		scope.launch {
			val capabilityInfo: CapabilityInfo = Tasks.await(cc.getCapability(getString(R.string.capability_wear), CapabilityClient.FILTER_REACHABLE))
			if (capabilityInfo.nodes.isEmpty()) {
				runOnUiThread {
					Toast.makeText(this@MainActivity, R.string.app_not_found, Toast.LENGTH_LONG).show()
				}
				val connectedNodes = Tasks.await(Wearable.getNodeClient(this@MainActivity).connectedNodes)
				if (connectedNodes.isEmpty()) {
					runOnUiThread {
						Toast.makeText(this@MainActivity, R.string.no_devices_found, Toast.LENGTH_LONG).show()
					}
				} else {
					for (node in connectedNodes) {
						try {
							RemoteActivityHelper(this@MainActivity).startRemoteActivity(
									Intent(Intent.ACTION_VIEW)
											.setData(Uri.parse("market://details?id=com.goujer.heartstreamer"))
											.addCategory(Intent.CATEGORY_BROWSABLE), node.id)
						} catch (e: Exception) {
							//Log.w(TAG, "Exception while launching Play Store on Wear")
							e.printStackTrace()
						}
					}
				}
			} else {
				for (node in capabilityInfo.nodes) {
					try {
						RemoteActivityHelper(this@MainActivity).startRemoteActivity(
								Intent(Intent.ACTION_VIEW)
										.setData(Uri.parse("app://com.goujer.heartstreamer"))
										.addCategory(Intent.CATEGORY_BROWSABLE)
								, node.id).await()
					} catch (e: Exception) {
						//Log.w(TAG, "Exception while launching Wear app")
						e.printStackTrace()
					}
				}
			}
		}
	}

	//TODO Consider adding button to install on all connected devices

	companion object {
		private const val TAG = "MainActivity"
	}
}