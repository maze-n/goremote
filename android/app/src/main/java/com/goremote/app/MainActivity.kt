
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
import com.goremote.app.data.remote.PairingClient
import com.goremote.app.domain.DiscoveredMac
import com.goremote.app.ui.screens.PinEntryScreen
import com.goremote.app.ui.screens.RemoteScreen
import com.goremote.app.ui.screens.ServerSelectionScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var discoveryManager: MacDiscoveryManager
    private lateinit var tokenStore: TokenStore
    private lateinit var pairedServerStore: PairedServerStore
    private lateinit var apiClient: ApiClient

    private val pairingClient = PairingClient()

    // Used by onStart() to avoid starting discovery when already paired.
    private var hasPairedServer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discoveryManager = MacDiscoveryManager(applicationContext)
        tokenStore = TokenStore(applicationContext)
        pairedServerStore = PairedServerStore(applicationContext)
        apiClient = ApiClient(tokenStore)

        // Restore the previous pairing, if both server details and token exist.
        var initialPairedServer = pairedServerStore.get()

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

            var selectedMac by remember {
                mutableStateOf<DiscoveredMac?>(null)
            }

            var isPairing by remember {
                mutableStateOf(false)
            }

            var pairingError by remember {
                mutableStateOf<String?>(null)
            }

            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        pairedServer != null -> {
                            RemoteScreen(
                                macName = pairedServer!!.name,
                                macAddress = pairedServer!!.baseUrl,
                                onUnpair = {
                                    // This forgets the Mac locally.
                                    // Server-side token revocation is a separate feature.
                                    tokenStore.clear()
                                    pairedServerStore.clear()

                                    pairedServer = null
                                    selectedMac = null
                                    pairingError = null
                                    hasPairedServer = false

                                    discoveryManager.startDiscovery()
                                }
                            )
                        }

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

                                            withContext(Dispatchers.IO) {
                                                try {
                                                    tokenStore.save(
                                                        result.accessToken
                                                    )

                                                    pairedServerStore.save(
                                                        PairedServer(
                                                            name = mac.name,
                                                            host = mac.host,
                                                            port = mac.port
                                                        )
                                                    )
                                                } catch (e: Exception) {
                                                    tokenStore.clear()
                                                    pairedServerStore.clear()
                                                    throw e
                                                }
                                            }

                                            hasPairedServer = true

                                            pairedServer = PairedServer(
                                                name = mac.name,
                                                host = mac.host,
                                                port = mac.port
                                            )

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
}
