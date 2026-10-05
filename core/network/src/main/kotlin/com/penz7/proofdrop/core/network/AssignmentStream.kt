package com.penz7.proofdrop.core.network

import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.network.session.SessionStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
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

sealed interface AssignmentEvent {
    /** An order was assigned to me, or a dispatcher changed one of mine. */
    data class Assigned(val order: Order) : AssignmentEvent

    /** An order was taken away from me. */
    data class Unassigned(val orderId: String) : AssignmentEvent
}

@Serializable
private data class OrderRef(val id: String)

/** Courier assignment events pushed by the server as Server-Sent Events (`/api/assignments`). */
@Singleton
class AssignmentStream @Inject constructor(
    client: OkHttpClient,
    private val config: ServerConfig,
    private val session: SessionStore,
    private val json: Json,
) {
    // SSE connections stay open indefinitely, so no read timeout (the server pings every 25 s).
    private val sseClient = client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    fun events(): Flow<AssignmentEvent> = callbackFlow {
        val url = config.httpUrl.newBuilder().addPathSegments("api/assignments").build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer ${session.token.orEmpty()}")
            .build()
        val source = EventSources.createFactory(sseClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val event = runCatching {
                        when (type) {
                            "assignment" -> AssignmentEvent.Assigned(json.decodeFromString<Order>(data))
                            "unassigned" -> AssignmentEvent.Unassigned(json.decodeFromString<OrderRef>(data).id)
                            else -> null
                        }
                    }.getOrNull()
                    event?.let { trySend(it) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (response?.code == 401) session.onUnauthorized()
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
