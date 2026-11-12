package com.goremote.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goremote.app.domain.DiscoveredMac

@Composable
fun ServerSelectionScreen(
    discoveredMacs: List<DiscoveredMac>,
    onRefresh: () -> Unit,
    onSelectMac: (DiscoveredMac) -> Unit
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

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Control your Mac remotely",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Available Macs",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan for Macs")
        }

        Spacer(Modifier.height(16.dp))

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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = mac.name,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "${mac.host}:${mac.port}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { onSelectMac(mac) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Select Mac")
                            }
                        }
                    }
                }
            }
        }
    }
}