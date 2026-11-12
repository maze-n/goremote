
package com.goremote.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goremote.app.data.remote.ApiClient
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun RemoteScreen(
    macName: String,
    macAddress: String,
    apiClient: ApiClient,
    onUnpair: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var volume by remember(macAddress) {
        mutableFloatStateOf(0f)
    }

    var isMuted by remember(macAddress) {
        mutableStateOf(false)
    }

    var isLoading by remember(macAddress) {
        mutableStateOf(true)
    }

    var isVolumeUpdating by remember {
        mutableStateOf(false)
    }

    var isMediaBusy by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var actionMessage by remember {
        mutableStateOf<String?>(null)
    }

    suspend fun loadVolume() {
        try {
            val response = apiClient.get(
                macAddress,
                "/volume"
            )

            val json = JSONObject(response.body)

            volume = json.getInt("volume").toFloat()
            isMuted = json.getBoolean("muted")
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = e.message ?: "Could not retrieve volume"
        } finally {
            isLoading = false
        }
    }

    suspend fun updateVolume(level: Int) {
        isVolumeUpdating = true
        errorMessage = null

        try {
            val body = JSONObject().put("level", level)

            apiClient.put(
                macAddress,
                "/volume",
                body
            )

            actionMessage = "Volume updated"
        } catch (e: Exception) {
            errorMessage = e.message ?: "Could not update volume"
            loadVolume()
        } finally {
            isVolumeUpdating = false
        }
    }

    suspend fun toggleMute() {
        errorMessage = null

        try {
            apiClient.post(
                macAddress,
                "/volume/mute"
            )

            loadVolume()
            actionMessage = if (isMuted) "Muted" else "Unmuted"
        } catch (e: Exception) {
            errorMessage = e.message ?: "Could not toggle mute"
        }
    }

    suspend fun performMediaAction(
        endpoint: String,
        label: String
    ) {
        if (isMediaBusy) return

        isMediaBusy = true
        errorMessage = null
        actionMessage = null

        try {
            apiClient.post(
                macAddress,
                endpoint
            )

            actionMessage = label
        } catch (e: Exception) {
            errorMessage = e.message ?: "Media command failed"
        } finally {
            isMediaBusy = false
        }
    }

    LaunchedEffect(macAddress) {
        loadVolume()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Header
            Column {
                Text(
                    text = "GoRemote",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = macName,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Volume controls
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Volume",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${volume.toInt()}%",
                                style = MaterialTheme.typography.headlineMedium
                            )

                            Text(
                                text = if (isMuted) "Muted" else "Unmuted",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Slider(
                            value = volume,
                            onValueChange = {
                                volume = it
                                actionMessage = null
                            },
                            onValueChangeFinished = {
                                scope.launch {
                                    updateVolume(volume.toInt())
                                }
                            },
                            valueRange = 0f..100f,
                            enabled = !isVolumeUpdating && !isMuted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        toggleMute()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isVolumeUpdating
                            ) {
                                Text(
                                    if (isMuted) "Unmute" else "Mute"
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        loadVolume()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isVolumeUpdating
                            ) {
                                Text("Refresh")
                            }
                        }
                    }
                }
            }

            // Media controls
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Media",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    performMediaAction(
                                        "/media/previous",
                                        "Previous track"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isMediaBusy
                        ) {
                            Text("Previous")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    performMediaAction(
                                        "/media/play-pause",
                                        "Play / Pause"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isMediaBusy
                        ) {
                            Text("Play / Pause")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    performMediaAction(
                                        "/media/next",
                                        "Next track"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isMediaBusy
                        ) {
                            Text("Next")
                        }
                    }

                    if (isMediaBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // Feedback
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            actionMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onUnpair,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Forget Mac")
            }
        }
    }
}
