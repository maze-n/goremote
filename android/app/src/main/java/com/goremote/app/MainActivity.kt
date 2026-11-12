
package com.goremote.app

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.goremote.app.data.auth.PairedServer
import com.goremote.app.data.auth.PairedServerStore
import com.goremote.app.data.auth.TokenStore
import com.goremote.app.data.discovery.MacDiscoveryManager
import com.goremote.app.data.remote.ApiClient
import com.goremote.app.data.remote.MacConnectionManager
import com.goremote.app.data.remote.PairingClient
import com.goremote.app.domain.DiscoveredMac
import com.goremote.app.ui.screens.ConnectionUnavailableScreen
import com.goremote.app.ui.screens.ConnectingScreen
import com.goremote.app.ui.screens.PinEntryScreen
import com.goremote.app.ui.screens.RemoteScreen
import com.goremote.app.ui.screens.ServerSelectionScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var discoveryManager: MacDiscoveryManager
    private lateinit var tokenStore: TokenStore
    private lateinit var pairedServerStore: PairedServerStore
    private lateinit var connectionManager: MacConnectionManager
    private lateinit var apiClient: ApiClient

    private val pairingClient = PairingClient()

    private var hasPairedServer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discoveryManager = MacDiscoveryManager(applicationContext)
        tokenStore = TokenStore(applicationContext)
        pairedServerStore = PairedServerStore(applicationContext)

        connectionManager = MacConnectionManager(
            discoveryManager = discoveryManager,
            pairedServerStore = pairedServerStore
        )

        apiClient = ApiClient(tokenStore)

        // Restore the previous pairing.
        var initialPairedServer = pairedServerStore.get()

        // Check whether the encrypted token is still accessible.
        val hasValidToken = try {
            tokenStore.get() != null
        } catch (_: Exception) {
            false
        }

        if (initialPairedServer != null && !hasValidToken) {
            initialPairedServer = null
            pairedServerStore.clear()
            tokenStore.clear()
        }

        if (initialPairedServer == null) {
            pairedServerStore.clear()
        }

        hasPairedServer = initialPairedServer != null

        setContent {
            val discoveredMacs by
                discoveryManager.discoveredMacs.collectAsState()

            var pairedServer by remember {
                mutableStateOf(initialPairedServer)
            }

            var activeServer by remember {
                mutableStateOf<PairedServer?>(null)
            }

            var selectedMac by remember {
                mutableStateOf<DiscoveredMac?>(null)
            }

            var isPairing by remember {
                mutableStateOf(false)
            }

            var pairingError by remember {
                mutableStateOf<String?>(null)
            }

            var isConnecting by remember {
                mutableStateOf(initialPairedServer != null)
            }

            var connectionError by remember {
                mutableStateOf<String?>(null)
            }

            var retryCount by remember {
                mutableIntStateOf(0)
            }

            // Automatic connection and reconnection.
            LaunchedEffect(pairedServer, retryCount) {
                val savedServer = pairedServer
                    ?: return@LaunchedEffect

                isConnecting = activeServer == null
                connectionError = null

                while (true) {
                    try {
                        val resolvedServer =
                            connectionManager.reconnect(
                                activeServer ?: savedServer
                            )

                        if (resolvedServer != null) {
                            activeServer = resolvedServer
                            pairedServer = resolvedServer
                            connectionError = null
                            isConnecting = false
                        } else {
                            activeServer = null
                            connectionError =
                                "Mac is unavailable. Retrying automatically."
                            isConnecting = false
                        }
                    } catch (e: Exception) {
                        activeServer = null
                        connectionError =
                            e.message ?: "Could not connect to Mac"
                        isConnecting = false
                    }

                    delay(RETRY_INTERVAL_MS)

                    isConnecting = activeServer == null
                }
            }

            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {

                        // Previously paired and currently connecting.
                        pairedServer != null &&
                            activeServer == null &&
                            isConnecting -> {

                            ConnectingScreen()
                        }

                        // Paired and connected.
                        pairedServer != null &&
                            activeServer != null -> {

                            RemoteScreen(
                                macName = activeServer!!.name,
                                macAddress = activeServer!!.baseUrl,
                                onUnpair = {
                                    forgetMac(
                                        tokenStore = tokenStore,
                                        pairedServerStore = pairedServerStore
                                    )

                                    activeServer = null
                                    pairedServer = null
                                    selectedMac = null
                                    pairingError = null
                                    connectionError = null
                                    hasPairedServer = false

                                    discoveryManager.startDiscovery()
                                }
                            )
                        }

                        // Previously paired but currently unavailable.
                        pairedServer != null -> {

                            ConnectionUnavailableScreen(
                                macName = pairedServer!!.name,
                                message = connectionError
                                    ?: "Waiting for your Mac...",
                                onRetry = {
                                    retryCount++
                                },
                                onUnpair = {
                                    forgetMac(
                                        tokenStore = tokenStore,
                                        pairedServerStore = pairedServerStore
                                    )

                                    activeServer = null
                                    pairedServer = null
                                    selectedMac = null
                                    connectionError = null
                                    hasPairedServer = false

                                    discoveryManager.startDiscovery()
                                }
                            )
                        }

                        // PIN entry.
                        selectedMac != null -> {

                            val mac = selectedMac!!

                            PinEntryScreen(
                                macName = mac.name,
                                macAddress = mac.baseUrl,
                                isPairing = isPairing,
                                errorMessage = pairingError,
                                onBack = {
                                    selectedMac = null
                                    pairingError = null
                                },
                                onPair = { code ->
                                    lifecycleScope.launch {
                                        isPairing = true
                                        pairingError = null

                                        try {
                                            val deviceName =
                                                "${Build.MANUFACTURER} ${Build.MODEL}"
                                                    .trim()

                                            val result = pairingClient.pair(
                                                baseUrl = mac.baseUrl,
                                                code = code,
                                                deviceName = deviceName
                                            )

                                            val server = PairedServer(
                                                name = mac.name,
                                                host = mac.host,
                                                port = mac.port,
                                                serviceName = mac.serviceName
                                            )

                                            // Persist both credentials and
                                            // server details before navigating.
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    tokenStore.save(
                                                        result.accessToken
                                                    )

                                                    pairedServerStore.save(
                                                        server
                                                    )
                                                } catch (e: Exception) {
                                                    tokenStore.clear()
                                                    pairedServerStore.clear()
                                                    throw e
                                                }
                                            }

                                            discoveryManager.stopDiscovery()

                                            hasPairedServer = true
                                            activeServer = null
                                            pairedServer = server
                                            selectedMac = null

                                        } catch (e: Exception) {
                                            pairingError =
                                                e.message ?: "Pairing failed"
                                        } finally {
                                            isPairing = false
                                        }
                                    }
                                }
                            )
                        }

                        // First-time server selection.
                        else -> {

                            ServerSelectionScreen(
                                discoveredMacs = discoveredMacs,
                                onRefresh = {
                                    discoveryManager.stopDiscovery()
                                    discoveryManager.startDiscovery()
                                },
                                onSelectMac = {
                                    selectedMac = it
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (
            ::discoveryManager.isInitialized &&
            !hasPairedServer
        ) {
            discoveryManager.startDiscovery()
        }
    }

    override fun onStop() {
        if (::discoveryManager.isInitialized) {
            discoveryManager.stopDiscovery()
        }

        super.onStop()
    }

    private fun forgetMac(
        tokenStore: TokenStore,
        pairedServerStore: PairedServerStore
    ) {
        tokenStore.clear()
        pairedServerStore.clear()
    }

    private companion object {
        const val RETRY_INTERVAL_MS = 10_000L
    }
}
