package com.goremote.app.data.remote

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PairingResult(
    val accessToken: String,
    val tokenType: String
)

class PairingException(message: String) : Exception(message)

class PairingClient {

    suspend fun pair(
        baseUrl: String,
        code: String,
        deviceName: String
    ): PairingResult = withContext(Dispatchers.IO) {

        val connection = URL("$baseUrl/pair")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val requestBody = JSONObject()
                .put("code", code)
                .put("device_name", deviceName)
                .toString()

            connection.outputStream.bufferedWriter().use {
                it.write(requestBody)
            }

            val statusCode = connection.responseCode

            if (statusCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?.trim()

                throw PairingException(
                    errorBody?.takeIf { it.isNotBlank() }
                        ?: "Pairing failed (HTTP $statusCode)"
                )
            }

            val responseBody = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            val response = JSONObject(responseBody)

            PairingResult(
                accessToken = response.getString("access_token"),
                tokenType = response.getString("token_type")
            )
        } catch (e: PairingException) {
            throw e
        } catch (e: IOException) {
            throw PairingException(
                "Could not connect to Mac: ${e.message}"
            )
        } finally {
            connection.disconnect()
        }
    }
}