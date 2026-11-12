
package com.goremote.app.data.remote

import com.goremote.app.data.auth.PairedServer
import com.goremote.app.data.auth.PairedServerStore
import com.goremote.app.data.auth.TokenStore
import com.goremote.app.data.discovery.MacDiscoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ConnectionResult {
    data class Connected(val server: PairedServer) : ConnectionResult
    data object Unauthorized : ConnectionResult
    data object Unavailable : ConnectionResult
}

class MacConnectionManager(
    private val discoveryManager: MacDiscoveryManager,
    private val pairedServerStore: PairedServerStore,
    private val tokenStore: TokenStore
) {

    suspend fun reconnect(
        savedServer: PairedServer
    ): ConnectionResult {

        val token = withContext(Dispatchers.IO) {
            tokenStore.get()
        } ?: return ConnectionResult.Unauthorized

        when (checkAuthentication(savedServer.baseUrl, token)) {
            AuthResult.AUTHORIZED -> return ConnectionResult.Connected(savedServer)
            AuthResult.UNAUTHORIZED -> return ConnectionResult.Unauthorized
            AuthResult.UNAVAILABLE -> Unit
        }

        discoveryManager.startDiscovery()

        try {
            val resolvedMac = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                discoveryManager.discoveredMacs
                    .mapNotNull { macs ->
                        macs.firstOrNull { mac ->
                            matches(savedServer, mac.deviceId, mac.name, mac.serviceName)
                        }
                    }
                    .first()
            } ?: return ConnectionResult.Unavailable

            when (checkAuthentication(resolvedMac.baseUrl, token)) {
                AuthResult.AUTHORIZED -> {
                    val updatedServer = PairedServer(
                        name = resolvedMac.name,
                        host = resolvedMac.host,
                        port = resolvedMac.port,
                        serviceName = resolvedMac.serviceName,
                        deviceId = resolvedMac.deviceId ?: savedServer.deviceId
                    )

                    withContext(Dispatchers.IO) {
                        pairedServerStore.save(updatedServer)
                    }

                    return ConnectionResult.Connected(updatedServer)
                }

                AuthResult.UNAUTHORIZED -> return ConnectionResult.Unauthorized
                AuthResult.UNAVAILABLE -> return ConnectionResult.Unavailable
            }
        } finally {
            discoveryManager.stopDiscovery()
        }
    }

    private fun matches(
        savedServer: PairedServer,
        discoveredDeviceId: String?,
        discoveredName: String,
        discoveredServiceName: String
    ): Boolean {
        if (savedServer.deviceId != null) {
            return savedServer.deviceId == discoveredDeviceId
        }

        return if (savedServer.serviceName != null) {
            savedServer.serviceName == discoveredServiceName
        } else {
            savedServer.name == discoveredName ||
                savedServer.name == discoveredServiceName
        }
    }

    private suspend fun checkAuthentication(
        baseUrl: String,
        token: String
    ): AuthResult = withContext(Dispatchers.IO) {

        val connection = try {
            URL("${baseUrl.trimEnd('/')}/status")
                .openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return@withContext AuthResult.UNAVAILABLE
        }

        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = HEALTH_TIMEOUT_MS
            connection.readTimeout = HEALTH_TIMEOUT_MS

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use {
                        it.readText()
                    }

                    val json = JSONObject(body)

                    if (
                        json.optString("status") == "ok" &&
                        json.optBoolean("authenticated")
                    ) {
                        AuthResult.AUTHORIZED
                    } else {
                        AuthResult.UNAVAILABLE
                    }
                }

                HttpURLConnection.HTTP_UNAUTHORIZED ->
                    AuthResult.UNAUTHORIZED

                else -> AuthResult.UNAVAILABLE
            }
        } catch (_: Exception) {
            AuthResult.UNAVAILABLE
        } finally {
            connection.disconnect()
        }
    }

    private enum class AuthResult {
        AUTHORIZED,
        UNAUTHORIZED,
        UNAVAILABLE
    }

    private companion object {
        const val HEALTH_TIMEOUT_MS = 2000
        const val DISCOVERY_TIMEOUT_MS = 15000L
    }
}
