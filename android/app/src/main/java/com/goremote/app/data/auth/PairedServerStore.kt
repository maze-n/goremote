
package com.goremote.app.data.auth

import android.content.Context

data class PairedServer(
    val name: String,
    val host: String,
    val port: Int
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
                .commit()
        ) {
            "Could not save paired Mac details"
        }
    }

    fun get(): PairedServer? {
        val name = preferences.getString(KEY_NAME, null) ?: return null
        val host = preferences.getString(KEY_HOST, null) ?: return null
        val port = preferences.getInt(KEY_PORT, -1)

        if (host.isBlank() || port !in 1..65535) {
            return null
        }

        return PairedServer(
            name = name,
            host = host,
            port = port
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
    }
}
