package com.goujer.heartstreamer.activity

import android.app.ActivityManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.PersistableBundle
import androidx.fragment.app.FragmentActivity
import com.goujer.heartstreamer.common.R

open class BaseActivity: FragmentActivity() {

	protected lateinit var cm: ConnectivityManager

	override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
		super.onCreate(savedInstanceState, persistentState)
		cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
	}

	protected fun getIPAddresses(): String {
		val ipList = mutableListOf<String>()

		val linkProperties = cm.getLinkProperties(cm.activeNetwork)
		if (linkProperties != null) {
			for (address in linkProperties.linkAddresses) {
				val addr = address.address
				if (!addr.isLoopbackAddress) {
					val sAddr: String = addr.hostAddress ?: continue
					val isIPv4 = sAddr.indexOf(':') < 0
					if (isIPv4) {
						ipList.add("http://$sAddr:12345")
					} else {
						val delim = sAddr.indexOf('%')
						ipList.add("http://[" + (if (delim < 0) sAddr else sAddr.substring(0, delim)) + "]:12345")
					}
				}
			}
		}

		return if (ipList.isEmpty()) {
			getString(R.string.text_no_ip_found)
		} else {
			ipList.sortByDescending { it.length }
			var ipText = ""
			for (i in 0 until ipList.size) {
				ipText += ipList[i]
				if (i != ipList.size-1) {
					ipText += "\n"
				}
			}
			ipText
		}
	}

	protected fun isServiceRunning(vararg serviceNames: String): Boolean {
		val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
		for (service in manager.getRunningServices(Int.MAX_VALUE)) {
			val runningServiceName = service.service.className
			for (serviceName in serviceNames) {
				if (runningServiceName == serviceName) {
					return true
				}
			}
		}
		return false
	}
}