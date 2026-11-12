package com.goremote.app.data.auth

import android.content.Context

data class PairedServer(
    val name: String,
    val host: String,
    val port: Int,
    val serviceName: String? = null,
    val deviceId: String? = null
) {
    val baseUrl: String
        get() = "http://$host:$port"
}

class PairedServerStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(server: PairedServer) {
        check(
            preferences.edit()
                .putString(KEY_NAME, server.name)
                .putString(KEY_HOST, server.host)
                .putInt(KEY_PORT, server.port)
                .putString(KEY_SERVICE_NAME, server.serviceName)
                .putString(KEY_DEVICE_ID, server.deviceId)
                .commit()
        ) {
            "Could not save paired Mac details"
        }
    }

    fun get(): PairedServer? {
        val name = preferences.getString(KEY_NAME, null) ?: return null
        val host = preferences.getString(KEY_HOST, null) ?: return null
        val port = preferences.getInt(KEY_PORT, -1)
        val serviceName = preferences.getString(KEY_SERVICE_NAME, null)
        val deviceId = preferences.getString(KEY_DEVICE_ID, null)

        if (host.isBlank() || port !in 1..65535) {
            return null
        }

        return PairedServer(
            name = name,
            host = host,
            port = port,
            serviceName = serviceName,
            deviceId = deviceId
        )
    }

    fun clear() {
        preferences.edit()
            .clear()
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "goremote_paired_server"
        const val KEY_NAME = "name"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_SERVICE_NAME = "service_name"
        const val KEY_DEVICE_ID = "device_id"
    }
}