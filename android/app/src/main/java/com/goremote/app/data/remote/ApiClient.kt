
package com.goremote.app.data.remote

import com.goremote.app.data.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ApiResponse(
    val statusCode: Int,
    val body: String
) {
    fun json(): JSONObject {
        return JSONObject(body)
    }
}

class ApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

class ApiClient(
    private val tokenStore: TokenStore
) {

    suspend fun get(
        baseUrl: String,
        path: String
    ): ApiResponse {
        return request(
            baseUrl = baseUrl,
            path = path,
            method = "GET"
        )
    }

    suspend fun post(
        baseUrl: String,
        path: String,
        body: JSONObject? = null
    ): ApiResponse {
        return request(
            baseUrl = baseUrl,
            path = path,
            method = "POST",
            body = body
        )
    }

    suspend fun put(
        baseUrl: String,
        path: String,
        body: JSONObject? = null
    ): ApiResponse {
        return request(
            baseUrl = baseUrl,
            path = path,
            method = "PUT",
            body = body
        )
    }

    suspend fun delete(
        baseUrl: String,
        path: String
    ): ApiResponse {
        return request(
            baseUrl = baseUrl,
            path = path,
            method = "DELETE"
        )
    }

    private suspend fun request(
        baseUrl: String,
        path: String,
        method: String,
        body: JSONObject? = null
    ): ApiResponse = withContext(Dispatchers.IO) {

        require(path.startsWith("/") && !path.startsWith("//")) {
            "API path must start with a single slash"
        }

        val token = tokenStore.get()
            ?: throw IllegalStateException("Device is not paired")

        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val connection = URL("$normalizedBaseUrl$path")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            if (body != null) {
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.outputStream.bufferedWriter().use {
                    it.write(body.toString())
                }
            }

            val statusCode = connection.responseCode

            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = responseStream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (statusCode !in 200..299) {
                val message = responseBody
                    .trim()
                    .ifEmpty { "API request failed" }

                throw ApiException(statusCode, message)
            }

            ApiResponse(
                statusCode = statusCode,
                body = responseBody
            )
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw IOException(
                "Could not connect to GoRemote server: ${e.message}",
                e
            )
        } finally {
            connection.disconnect()
        }
    }
}
