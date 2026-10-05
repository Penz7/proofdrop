package com.penz7.proofdrop.core.network

import com.penz7.proofdrop.core.model.Order
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** New order assignments pushed by the server as Server-Sent Events. */
@Singleton
class AssignmentStream @Inject constructor(
    client: OkHttpClient,
    private val config: ServerConfig,
    private val json: Json,
) {
    // SSE connections stay open indefinitely, so no read timeout.
    private val sseClient = client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    fun assignments(): Flow<Order> = callbackFlow {
        val url = config.httpUrl.newBuilder().addPathSegment("assignments").build()
        val source = EventSources.createFactory(sseClient).newEventSource(
            Request.Builder().url(url).header("Accept", "text/event-stream").build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (type == "assignment") {
                        runCatching { json.decodeFromString<Order>(data) }.onSuccess { trySend(it) }
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(IOException("Assignment stream failed", t))
                }

                override fun onClosed(eventSource: EventSource) {
                    close(IOException("Assignment stream closed"))
                }
            },
        )
        awaitClose { source.cancel() }
    }
}
