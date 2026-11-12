package com.goremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goremote.app.data.discovery.MacDiscoveryManager
import com.goremote.app.domain.DiscoveredMac

class MainActivity : ComponentActivity() {

    private lateinit var discoveryManager: MacDiscoveryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discoveryManager = MacDiscoveryManager(applicationContext)

        setContent {
            val discoveredMacs by
                discoveryManager.discoveredMacs.collectAsState()

            GoRemoteApp(
                discoveredMacs = discoveredMacs,
                onRefresh = {
                    discoveryManager.stopDiscovery()
                    discoveryManager.startDiscovery()
                }
            )
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
fun GoRemoteApp(
    discoveredMacs: List<DiscoveredMac>,
    onRefresh: () -> Unit
) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "GoRemote",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Control your Mac remotely",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Available Macs",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan for Macs")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (discoveredMacs.isEmpty()) {
                    Text(
                        text = "Looking for GoRemote servers on your network...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = discoveredMacs,
                            key = { it.serviceName }
                        ) { mac ->
                            MacCard(mac = mac)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacCard(mac: DiscoveredMac) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = mac.name,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${mac.host}:${mac.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "GoRemote server discovered",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}