package com.goremote.app.domain

data class DiscoveredMac(
    val name: String,
    val host: String,
    val port: Int,
    val serviceName: String
) {
    val baseUrl: String
        get() = "http://$host:$port"
}