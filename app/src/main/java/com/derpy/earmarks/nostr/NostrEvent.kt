package com.derpy.earmarks.nostr

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Builds and signs Nostr events per NIP-01.
 *
 * Event id = sha256( JSON serialization of [0, pubkey, created_at, kind, tags, content] )
 * Signature = BIP-340 schnorr( private key, event id )
 */
object NostrEvent {

    /**
     * Builds a signed Nostr event JSON object ready to send to a relay.
     *
     * @param tags list of tag arrays, e.g. listOf(listOf("d", "derpy-earmarks"))
     */
    fun build(
        privKeyHex: String,
        kind: Int,
        content: String,
        tags: List<List<String>>,
        createdAt: Long = System.currentTimeMillis() / 1000
    ): JSONObject {
        val pubKeyHex = Nip44.derivePubKeyHex(privKeyHex)

        // Canonical JSON serialization for the id hash, per NIP-01.
        val tagsArr = JSONArray().apply {
            tags.forEach { tag ->
                put(JSONArray().apply { tag.forEach { put(it) } })
            }
        }
        val serial = JSONArray().apply {
            put(0)
            put(pubKeyHex)
            put(createdAt)
            put(kind)
            put(tagsArr)
            put(content)
        }.toString()

        val id = sha256(serial.toByteArray(Charsets.UTF_8)).toHex()

        val skBytes = privKeyHex.hexToBytes()
        val sig = Schnorr.sign(skBytes, id.hexToBytes()).toHex()

        return JSONObject().apply {
            put("id", id)
            put("pubkey", pubKeyHex)
            put("created_at", createdAt)
            put("kind", kind)
            put("tags", tagsArr)
            put("content", content)
            put("sig", sig)
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
