package com.penz7.proofdrop.core.network

import com.penz7.proofdrop.core.model.CourierPosition
import com.penz7.proofdrop.core.model.PositionReport
import com.penz7.proofdrop.core.network.session.SessionStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Envelope used by the NestJS WsAdapter: `{ "event": "...", "data": ... }`. */
@Serializable
private data class WsMessage(val event: String, val data: JsonElement)

/**
 * Live fleet positions over the `/fleet` WebSocket. While collected, it also reports
 * this courier's own position every few seconds. Fails with [IOException] when the socket drops.
 */
@Singleton
class FleetSocket @Inject constructor(
    private val client: OkHttpClient,
    private val config: ServerConfig,
    private val session: SessionStore,
    private val json: Json,
) {
    fun positions(selfReport: () -> PositionReport?): Flow<List<CourierPosition>> = callbackFlow {
        val url = config.httpUrl.newBuilder()
            .addPathSegment("fleet")
            .addQueryParameter("access_token", session.token.orEmpty())
            .build()
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = runCatching { json.decodeFromString<WsMessage>(text) }.getOrNull() ?: return
                    if (message.event == "fleet") {
                        runCatching { json.decodeFromJsonElement(ListSerializer(CourierPosition.serializer()), message.data) }
                            .onSuccess { trySend(it) }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (response?.code == 401) session.onUnauthorized()
                    close(IOException("Fleet socket failed", t))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (code == UNAUTHORIZED_CLOSE_CODE) session.onUnauthorized()
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close(IOException("Fleet socket closed ($code): $reason"))
                }
            },
        )
        launch {
            while (true) {
                selfReport()?.let { report ->
                    val message = WsMessage("position", json.encodeToJsonElement(PositionReport.serializer(), report))
                    socket.send(json.encodeToString(WsMessage.serializer(), message))
                }
                delay(REPORT_INTERVAL_MS)
            }
        }
        awaitClose { socket.close(1000, "bye") }
    }

    private companion object {
        const val REPORT_INTERVAL_MS = 5_000L
        const val UNAUTHORIZED_CLOSE_CODE = 4401
    }
}
