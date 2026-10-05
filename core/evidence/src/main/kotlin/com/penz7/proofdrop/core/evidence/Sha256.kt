package com.penz7.proofdrop.core.evidence

import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    fun of(text: String): String = of(text.toByteArray(Charsets.UTF_8))

    fun of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** Streams the input so large videos never have to fit in memory. */
    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        input.use {
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
