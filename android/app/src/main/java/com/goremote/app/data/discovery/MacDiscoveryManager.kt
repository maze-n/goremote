
package com.goremote.app.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.goremote.app.domain.DiscoveredMac
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class MacDiscoveryManager(context: Context) {

    companion object {
        private const val TAG = "MacDiscovery"
        private const val SERVICE_TYPE = "_goremote._tcp."
    }

    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val executor = context.mainExecutor

    private val _discoveredMacs =
        MutableStateFlow<List<DiscoveredMac>>(emptyList())

    val discoveredMacs: StateFlow<List<DiscoveredMac>> =
        _discoveredMacs.asStateFlow()

    private val services =
        ConcurrentHashMap<String, DiscoveredMac>()

    private val serviceCallbacks =
        ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

    private var isDiscovering = false

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        if (isDiscovering) return

        isDiscovering = true
        services.clear()
        publishServices()

        val listener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")

                if (!serviceInfo.serviceType.startsWith(SERVICE_TYPE)) {
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    registerServiceInfoCallback(serviceInfo)
                } else {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")

                removeService(serviceInfo.serviceName)
                unregisterServiceCallback(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int
            ) {
                Log.e(TAG, "Discovery start failed: $errorCode")

                isDiscovering = false
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int
            ) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        discoveryListener = listener

        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    fun stopDiscovery() {
        if (!isDiscovering) return

        isDiscovering = false

        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Discovery listener not active", exception)
            }
        }

        discoveryListener = null

        serviceCallbacks.values.forEach { callback ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    nsdManager.unregisterServiceInfoCallback(callback)
                }
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Service callback not registered", exception)
            }
        }

        serviceCallbacks.clear()
        services.clear()
        publishServices()
    }

    @Suppress("NewApi")
    private fun registerServiceInfoCallback(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName

        if (serviceCallbacks.containsKey(serviceName)) return

        val callback = object : NsdManager.ServiceInfoCallback {

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                updateService(serviceInfo)
            }

            override fun onServiceLost() {
                Log.d(TAG, "Service callback lost: $serviceName")
                removeService(serviceName)
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG, "Service callback registration failed: $errorCode")
                serviceCallbacks.remove(serviceName)
            }

            override fun onServiceInfoCallbackUnregistered() {
                Log.d(TAG, "Service callback unregistered: $serviceName")
            }
        }

        serviceCallbacks[serviceName] = callback

        try {
            nsdManager.registerServiceInfoCallback(
                serviceInfo,
                executor,
                callback
            )
        } catch (exception: Exception) {
            serviceCallbacks.remove(serviceName)
            Log.e(TAG, "Could not register service callback", exception)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {

                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int
                ) {
                    Log.e(
                        TAG,
                        "Resolution failed: ${serviceInfo.serviceName}, error=$errorCode"
                    )
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    updateService(resolved)
                }
            }
        )
    }

    @Suppress("NewApi")
    private fun unregisterServiceCallback(serviceName: String) {
        val callback = serviceCallbacks.remove(serviceName) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                nsdManager.unregisterServiceInfoCallback(callback)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Could not unregister service callback", exception)
            }
        }
    }

    private fun updateService(serviceInfo: NsdServiceInfo) {
        if (!isDiscovering) return

        val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            serviceInfo.host?.hostAddress
        } ?: return

        val mac = DiscoveredMac(
            name = serviceInfo.serviceName,
            host = host,
            port = serviceInfo.port,
            serviceName = serviceInfo.serviceName
        )

        services[serviceInfo.serviceName] = mac
        publishServices()

        Log.d(TAG, "Resolved Mac: ${mac.name} at ${mac.baseUrl}")
    }

    private fun removeService(serviceName: String) {
        services.remove(serviceName)
        publishServices()
    }

    private fun publishServices() {
        _discoveredMacs.value = services.values
            .sortedBy { it.name.lowercase() }
            .toList()
    }
}
