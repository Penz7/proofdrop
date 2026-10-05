package com.penz7.proofdrop.core.network.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the access token with an AES-GCM key that lives in the Android Keystore,
 * so the raw JWT never touches disk and the key cannot be extracted from the device.
 */
@Singleton
class TokenCipher @Inject constructor() {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun key(): SecretKey =
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()

    private fun generateKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }
        .generateKey()

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** Returns null if the data can't be decrypted (e.g. the key was wiped after a backup restore). */
    fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        String(cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "proofdrop_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
