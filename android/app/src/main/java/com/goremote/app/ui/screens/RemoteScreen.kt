
package com.goremote.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goremote.app.data.remote.ApiClient
import kotlinx.coroutines.launch
import org.json.JSONObject

private val ScreenBackground = Color(0xFF17181D)
private val PrimaryText = Color(0xFFE8E9EF)
private val SecondaryText = Color(0xFF9DA0AC)
private val DividerColor = Color(0xFF393B44)
private val ConnectedGreen = Color(0xFF42D6A0)
private val MutedRed = Color(0xFFFF858B)

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

    var settingsExpanded by remember {
        mutableStateOf(false)
    }

    suspend fun loadVolume() {
        try {
            val response = apiClient.get(macAddress, "/volume")
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

        val wasMuted = isMuted

        try {
            apiClient.post(macAddress, "/volume/mute")
            loadVolume()

            actionMessage = if (wasMuted) "Unmuted" else "Muted"
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
            apiClient.post(macAddress, endpoint)
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
        color = ScreenBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {

            // Connection header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.LaptopMac,
                    contentDescription = "Mac",
                    tint = PrimaryText,
                    modifier = Modifier.size(36.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = macName,
                        color = PrimaryText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(9.dp),
                            shape = CircleShape,
                            color = ConnectedGreen
                        ) {}

                        Text(
                            text = "Connected · Wi-Fi",
                            color = ConnectedGreen,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 9.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { settingsExpanded = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = SecondaryText,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = settingsExpanded,
                        onDismissRequest = {
                            settingsExpanded = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Forget Mac")
                            },
                            onClick = {
                                settingsExpanded = false
                                onUnpair()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            HorizontalDivider(color = DividerColor)

            // Media controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = SecondaryText,
                    modifier = Modifier.size(62.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Mac Media Remote",
                    color = PrimaryText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Ready to control playback",
                    color = SecondaryText,
                    fontSize = 17.sp
                )

                Spacer(modifier = Modifier.height(48.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                performMediaAction(
                                    "/media/previous",
                                    "Previous track"
                                )
                            }
                        },
                        enabled = !isMediaBusy,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = PrimaryText,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.size(24.dp))

                    Surface(
                        modifier = Modifier.size(152.dp),
                        shape = CircleShape,
                        color = PrimaryText
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    performMediaAction(
                                        "/media/play-pause",
                                        "Play / Pause"
                                    )
                                }
                            },
                            enabled = !isMediaBusy,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isMediaBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(42.dp),
                                    color = ScreenBackground,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play or pause",
                                    tint = ScreenBackground,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.size(24.dp))

                    IconButton(
                        onClick = {
                            scope.launch {
                                performMediaAction(
                                    "/media/next",
                                    "Next track"
                                )
                            }
                        },
                        enabled = !isMediaBusy,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = PrimaryText,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerColor)

            // Volume section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 44.dp)
            ) {
                Text(
                    text = "Volume",
                    color = PrimaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.CenterHorizontally),
                        color = PrimaryText
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeDown,
                            contentDescription = "Volume",
                            tint = SecondaryText,
                            modifier = Modifier.size(26.dp)
                        )

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
                            enabled = !isVolumeUpdating && !isMuted,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryText,
                                activeTrackColor = PrimaryText,
                                inactiveTrackColor = DividerColor,
                                disabledThumbColor = SecondaryText,
                                disabledActiveTrackColor = SecondaryText
                            )
                        )

                        Text(
                            text = "${volume.toInt()}%",
                            color = PrimaryText,
                            fontSize = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    toggleMute()
                                }
                            },
                            enabled = !isVolumeUpdating
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeOff,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) MutedRed else SecondaryText,
                                modifier = Modifier.size(27.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    loadVolume()
                                }
                            },
                            enabled = !isVolumeUpdating
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh volume",
                                tint = SecondaryText
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Loud volume",
                            tint = SecondaryText,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerColor)

            // Feedback
            errorMessage?.let {
                Text(
                    text = it,
                    color = MutedRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            actionMessage?.let {
                Text(
                    text = it,
                    color = ConnectedGreen,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "GoRemote",
                color = SecondaryText,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
