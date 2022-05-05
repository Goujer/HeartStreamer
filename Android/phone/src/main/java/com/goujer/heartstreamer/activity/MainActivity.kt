package com.goujer.heartstreamer.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import com.goujer.heartstreamer.KILL_ACTION
import com.goujer.heartstreamer.R
import com.goujer.heartstreamer.UPDATE_HR_ACTION
import com.goujer.heartstreamer.WEAR_CAPABILITY
import com.goujer.heartstreamer.databinding.ActivityMainBinding
import com.goujer.heartstreamer.service.PhoneHRService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

class MainActivity : BaseActivity() {
	private val scope = CoroutineScope(Dispatchers.IO)
	private lateinit var binding: ActivityMainBinding

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

		if (!isServiceRunning(PhoneHRService::class.java.name)) {
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

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		val inflater: MenuInflater = menuInflater
		inflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return if (item.itemId == R.id.action_close) {
			this.sendBroadcast(Intent(KILL_ACTION))
			return true
		} else {
			super.onOptionsItemSelected(item)
		}
	}

	//Helpers
	private fun updateIPAddress() {
		binding.textIpAddress.text = getIPAddresses()
	}

	private fun launchWearApp() {
		scope.launch {
			try {
				val capabilityInfo: CapabilityInfo = Tasks.await(cc.getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE))
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
											.addCategory(Intent.CATEGORY_BROWSABLE), node.id).await()
						} catch (e: Exception) {
							//Log.w(TAG, "Exception while launching Wear app")
							e.printStackTrace()
						}
					}
				}
			} catch (e: ExecutionException) {
				//TODO, No idea why this happens
			}
		}
	}

	//TODO Consider adding button to install on all connected devices

	companion object {
		private const val TAG = "MainActivity"
	}
}