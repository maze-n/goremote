
package com.goremote.app.data.remote

import com.goremote.app.data.auth.PairedServer
import com.goremote.app.data.auth.PairedServerStore
import com.goremote.app.data.discovery.MacDiscoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

class MacConnectionManager(
    private val discoveryManager: MacDiscoveryManager,
    private val pairedServerStore: PairedServerStore
) {

    suspend fun reconnect(
        savedServer: PairedServer
    ): PairedServer? {

        // First try the previously saved address.
        if (isHealthy(savedServer.baseUrl)) {
            return savedServer
        }

        // Saved address is unavailable. Resolve the same Bonjour service.
        discoveryManager.startDiscovery()

        try {
            val resolvedMac = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                discoveryManager.discoveredMacs
                    .mapNotNull { discoveredMacs ->

                        discoveredMacs.firstOrNull { mac ->
                            matches(
                                savedServer = savedServer,
                                discoveredName = mac.name,
                                discoveredServiceName = mac.serviceName
                            ) && isHealthy(mac.baseUrl)
                        }
                    }
                    .first()
            } ?: return null

            val updatedServer = PairedServer(
                name = resolvedMac.name,
                host = resolvedMac.host,
                port = resolvedMac.port,
                serviceName = resolvedMac.serviceName
            )

            withContext(Dispatchers.IO) {
                pairedServerStore.save(updatedServer)
            }

            return updatedServer

        } finally {
            discoveryManager.stopDiscovery()
        }
    }

    private fun matches(
        savedServer: PairedServer,
        discoveredName: String,
        discoveredServiceName: String
    ): Boolean {
        val savedServiceName = savedServer.serviceName

        return if (savedServiceName != null) {
            savedServiceName == discoveredServiceName
        } else {
            // Compatibility with servers paired before service identity
            // was persisted.
            savedServer.name == discoveredName ||
                savedServer.name == discoveredServiceName
        }
    }

    private suspend fun isHealthy(baseUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            val connection = try {
                URL("${baseUrl.trimEnd('/')}/health")
                    .openConnection() as HttpURLConnection
            } catch (_: Exception) {
                return@withContext false
            }

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = HEALTH_TIMEOUT_MS
                connection.readTimeout = HEALTH_TIMEOUT_MS

                connection.responseCode == HttpURLConnection.HTTP_OK
            } catch (_: Exception) {
                false
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val HEALTH_TIMEOUT_MS = 2000
        const val DISCOVERY_TIMEOUT_MS = 15000L
    }
}
