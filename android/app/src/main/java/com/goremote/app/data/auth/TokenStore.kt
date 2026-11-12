
package com.goremote.app.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(token: String) {
        require(token.isNotBlank()) { "Token cannot be empty" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

        val iv = Base64.encodeToString(
            cipher.iv,
            Base64.NO_WRAP
        )

        val ciphertext = Base64.encodeToString(
            encrypted,
            Base64.NO_WRAP
        )

        val storedValue = "$iv.$ciphertext"

        check(
            preferences.edit()
                .putString(TOKEN_KEY, storedValue)
                .commit()
        ) {
            "Could not persist access token"
        }
    }

    fun get(): String? {
        val storedValue = preferences.getString(TOKEN_KEY, null)
            ?: return null

        try {
            val parts = storedValue.split(".", limit = 2)
            require(parts.size == 2) { "Invalid encrypted token format" }

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )

            return String(
                cipher.doFinal(ciphertext),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Could not decrypt stored access token",
                e
            )
        }
    }

    fun clear() {
        preferences.edit()
            .remove(TOKEN_KEY)
            .commit()

        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null)

        if (existingKey is SecretKey) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .build()
        )

        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "goremote_access_token_key"
        const val PREFERENCES_NAME = "goremote_secure_storage"
        const val TOKEN_KEY = "encrypted_access_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
