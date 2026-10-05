package com.penz7.proofdrop.core.network

import com.penz7.proofdrop.core.model.CourierPosition
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Live fleet positions over a WebSocket. The flow fails with [IOException] when the socket drops. */
@Singleton
class FleetSocket @Inject constructor(
    private val client: OkHttpClient,
    private val config: ServerConfig,
    private val json: Json,
) {
    fun positions(selfPosition: () -> CourierPosition?): Flow<List<CourierPosition>> = callbackFlow {
        val url = config.httpUrl.newBuilder().addPathSegment("fleet").build()
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString<List<CourierPosition>>(text) }
                        .onSuccess { trySend(it) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(IOException("Fleet socket failed", t))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close(IOException("Fleet socket closed: $reason"))
                }
            },
        )
        // Report our own position so dispatch sees this courier too.
        launch {
            while (true) {
                selfPosition()?.let { socket.send(json.encodeToString(it)) }
                delay(5_000)
            }
        }
        awaitClose { socket.close(1000, "bye") }
    }
}
