package com.dirplay.earmarks.nostr

import com.dirplay.earmarks.data.Earmark
import com.dirplay.earmarks.data.parseEarmarkList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

private val DEFAULT_RELAYS = listOf(
    "wss://relay.damus.io",
    "wss://nos.lol",
    "wss://relay.primal.net",
    "wss://nostr.wine"
)

class NostrService(private val httpClient: OkHttpClient) {

    /**
     * Fetches and decrypts the dirplay earmark list for [privKeyHex].
     * Queries all default relays in parallel and keeps the most recent event.
     */
    suspend fun fetchEarmarks(privKeyHex: String): List<Earmark> = withContext(Dispatchers.IO) {
        val pubKeyHex = Nip44.derivePubKeyHex(privKeyHex)
        val subscriptionId = "earmarks-${System.currentTimeMillis()}"

        val filterJson = JSONObject().apply {
            put("kinds", JSONArray().put(30001))
            put("authors", JSONArray().put(pubKeyHex))
            put("#d", JSONArray().put("dirplay-earmarks"))
            put("limit", 1)
        }
        val reqMessage = JSONArray().apply {
            put("REQ")
            put(subscriptionId)
            put(filterJson)
        }.toString()

        // Query all relays in parallel, collect first valid event from each
        val events = coroutineScope {
            DEFAULT_RELAYS.map { relay ->
                async { queryRelay(relay, reqMessage, subscriptionId) }
            }.awaitAll()
        }.filterNotNull()

        if (events.isEmpty()) return@withContext emptyList()

        // Use the most recent event
        val bestEvent = events.maxByOrNull { it.getLong("created_at") }
            ?: return@withContext emptyList()

        val encryptedContent = bestEvent.getString("content")
        val plaintext = Nip44.decrypt(privKeyHex, encryptedContent)
        parseEarmarkList(plaintext)
    }

    /**
     * Opens a WebSocket to [relayUrl], sends [reqMessage], and waits for a matching EVENT.
     * Returns the event JSONObject or null on failure/timeout.
     */
    private suspend fun queryRelay(
        relayUrl: String,
        reqMessage: String,
        subscriptionId: String
    ): JSONObject? = withTimeoutOrNull(15_000L) {
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(relayUrl).build()
            val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(reqMessage)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = JSONArray(text)
                        val type = msg.getString(0)
                        if (type == "EVENT" && msg.getString(1) == subscriptionId) {
                            val event = msg.getJSONObject(2)
                            if (cont.isActive) {
                                webSocket.close(1000, null)
                                cont.resume(event)
                            }
                        } else if (type == "EOSE") {
                            // End of stored events — nothing found
                            webSocket.close(1000, null)
                            if (cont.isActive) cont.resume(null)
                        }
                    } catch (_: Exception) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (cont.isActive) cont.resume(null)
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
    }
}
