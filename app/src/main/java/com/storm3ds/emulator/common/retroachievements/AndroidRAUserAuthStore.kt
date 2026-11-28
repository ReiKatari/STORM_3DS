package com.storm3ds.emulator.common.retroachievements

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAUserAuth
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidRAUserAuthStore(private val sharedPreferences: SharedPreferences) : RAUserAuthStore {

    private val authMutex = Mutex()

    private companion object {
        const val USERNAME_KEY = "ra_username"
        const val TOKEN_KEY = "ra_token"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "storm3ds_ra_token_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENC_PREFIX = "enc_v1:"
        const val GCM_TAG_BITS = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encryptToken(plainToken: String): String {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plainToken.toByteArray(StandardCharsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            "$ENC_PREFIX$ivB64:$ctB64"
        }.getOrDefault(plainToken)
    }

    private fun decryptToken(storedValue: String): Pair<String?, Boolean> {
        if (!storedValue.startsWith(ENC_PREFIX)) {
            return storedValue to true
        }
        val payload = storedValue.removePrefix(ENC_PREFIX)
        val sep = payload.indexOf(':')
        if (sep <= 0) return null to false
        return runCatching {
            val iv = Base64.decode(payload.substring(0, sep), Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.substring(sep + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
            plain to false
        }.getOrElse { null to false }
    }

    override suspend fun storeUserAuth(userAuth: RAUserAuth.Authenticated) = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val encrypted = encryptToken(userAuth.token)
            sharedPreferences.edit {
                putString(USERNAME_KEY, userAuth.username)
                putString(TOKEN_KEY, encrypted)
            }
        }
    }

    override suspend fun getUserAuth(): RAUserAuth? = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val username = sharedPreferences.getString(USERNAME_KEY, null) ?: return@withLock null
            val storedToken = sharedPreferences.getString(TOKEN_KEY, null)
                ?: return@withLock RAUserAuth.AuthenticationExpired(username)

            val (plainToken, needsMigration) = decryptToken(storedToken)
            if (plainToken.isNullOrBlank()) {
                return@withLock RAUserAuth.AuthenticationExpired(username)
            }
            if (needsMigration) {
                val reEncrypted = encryptToken(plainToken)
                if (reEncrypted != storedToken) {
                    sharedPreferences.edit { putString(TOKEN_KEY, reEncrypted) }
                }
            }

            RAUserAuth.Authenticated(username, plainToken)
        }
    }

    override suspend fun clearUserAuth() = withContext(Dispatchers.IO) {
        authMutex.withLock {
            sharedPreferences.edit {
                remove(USERNAME_KEY)
                remove(TOKEN_KEY)
            }
        }
    }

    override suspend fun clearUserAuthIfMatches(
        expectedUsername: String,
        expectedToken: String,
    ): Boolean = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val currentUsername = sharedPreferences.getString(USERNAME_KEY, null)
            val storedToken = sharedPreferences.getString(TOKEN_KEY, null)
            val currentToken = storedToken?.let { decryptToken(it).first }
            if (currentUsername != expectedUsername || currentToken != expectedToken) {
                return@withLock false
            }

            sharedPreferences.edit {
                remove(USERNAME_KEY)
                remove(TOKEN_KEY)
            }
            true
        }
    }

    override suspend fun clearUserTokenIfMatches(
        expectedUsername: String,
        expectedToken: String,
    ): Boolean = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val currentUsername = sharedPreferences.getString(USERNAME_KEY, null)
            val storedToken = sharedPreferences.getString(TOKEN_KEY, null)
            val currentToken = storedToken?.let { decryptToken(it).first }
            if (currentUsername != expectedUsername || currentToken != expectedToken) {
                return@withLock false
            }

            sharedPreferences.edit {
                remove(TOKEN_KEY)
            }
            true
        }
    }
}
