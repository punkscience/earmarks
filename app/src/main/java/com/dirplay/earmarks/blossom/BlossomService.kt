package com.dirplay.earmarks.blossom

import android.util.Base64
import com.dirplay.earmarks.data.Chunk
import com.dirplay.earmarks.data.Earmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class BlossomService(private val httpClient: OkHttpClient) {

    /**
     * Downloads, verifies, decrypts, and reassembles all chunks for [earmark],
     * writing the result to [destFile].
     */
    suspend fun downloadAndDecrypt(earmark: Earmark, destFile: File) = withContext(Dispatchers.IO) {
        val manifest = earmark.blossom ?: error("Earmark has no blossom manifest")
        val keyBytes = Base64.decode(manifest.key, Base64.DEFAULT)
        require(keyBytes.size == 32) { "AES key must be 32 bytes" }

        // Download all chunks concurrently
        val encryptedChunks = coroutineScope {
            manifest.chunks.map { chunk ->
                async(Dispatchers.IO) { downloadChunk(chunk) }
            }.awaitAll()
        }

        // Decrypt and concatenate in index order
        val assembled = manifest.chunks
            .sortedBy { it.index }
            .mapIndexed { i, chunk -> decryptChunk(encryptedChunks[i], keyBytes) }
            .reduce { acc, bytes -> acc + bytes }

        destFile.writeBytes(assembled)
    }

    private fun downloadChunk(chunk: Chunk): ByteArray {
        for (server in chunk.servers) {
            try {
                val url = "${server.trimEnd('/')}/${chunk.sha256}"
                val request = Request.Builder().url(url).build()
                val bytes = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()
                } ?: continue

                // Verify SHA-256 of encrypted bytes
                val digest = MessageDigest.getInstance("SHA-256")
                val actual = digest.digest(bytes).joinToString("") { "%02x".format(it) }
                if (actual != chunk.sha256) continue

                return bytes
            } catch (_: Exception) { /* try next server */ }
        }
        throw IOException("All servers failed for chunk ${chunk.sha256}")
    }

    /**
     * Decrypts one AES-256-GCM chunk.
     * Format: [12-byte nonce][ciphertext + 16-byte GCM tag]
     */
    private fun decryptChunk(encrypted: ByteArray, key: ByteArray): ByteArray {
        require(encrypted.size > 12 + 16) { "Encrypted chunk too short" }
        val nonce = encrypted.copyOfRange(0, 12)
        val ciphertextWithTag = encrypted.copyOfRange(12, encrypted.size)

        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
        val output = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
        val len = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }
}
