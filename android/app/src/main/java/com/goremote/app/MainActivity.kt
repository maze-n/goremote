
package com.goremote.app

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.goremote.app.data.discovery.MacDiscoveryManager
import com.goremote.app.data.remote.PairingClient
import com.goremote.app.data.remote.PairingResult
import com.goremote.app.data.auth.TokenStore
import com.goremote.app.domain.DiscoveredMac
import com.goremote.app.ui.screens.PinEntryScreen
import com.goremote.app.ui.screens.ServerSelectionScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var discoveryManager: MacDiscoveryManager
    private lateinit var tokenStore: TokenStore
    
    private val pairingClient = PairingClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discoveryManager = MacDiscoveryManager(applicationContext)
        tokenStore = TokenStore(applicationContext)

        setContent {
            val discoveredMacs by
                discoveryManager.discoveredMacs.collectAsState()

            var selectedMac by remember {
                mutableStateOf<DiscoveredMac?>(null)
            }

            var pairedMacName by remember {
                mutableStateOf<String?>(null)
            }

            var isPairing by remember {
                mutableStateOf(false)
            }

            var pairingError by remember {
                mutableStateOf<String?>(null)
            }

            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        pairedMacName != null -> {
                            PairingSuccessScreen(
                                macName = pairedMacName!!
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

                                            // Encrypt and persist the token off the main thread.
                                            withContext(Dispatchers.IO) {
                                                tokenStore.save(result.accessToken)
                                            }

                                            pairedMacName = mac.name
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

        if (::discoveryManager.isInitialized) {
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

@Composable
private fun PairingSuccessScreen(macName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Paired successfully",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Your Android device is now paired with $macName.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
