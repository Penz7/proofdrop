package com.penz7.proofdrop.server

import com.penz7.proofdrop.core.evidence.EvidenceChain
import com.penz7.proofdrop.core.evidence.Sha256
import com.penz7.proofdrop.core.model.CheckoutRequest
import com.penz7.proofdrop.core.model.CourierPosition
import com.penz7.proofdrop.core.model.EvidenceRecord
import com.penz7.proofdrop.core.model.EvidenceUploadResult
import com.penz7.proofdrop.core.model.OrderStatusUpdate
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.server.websocket.webSocket
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.toByteArray
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.launch

fun Application.configureRoutes(state: DispatchState) = routing {
    get("/") { call.respondText("ProofDrop dispatch server is running") }

    route("/orders") {
        get { call.respond(state.orders.values.sortedBy { it.assignedAt }) }
        post("/{id}/status") {
            val update = call.receive<OrderStatusUpdate>()
            val order = state.updateStatus(call.parameters["id"]!!, update.status)
            if (order == null) call.respond(HttpStatusCode.NotFound) else call.respond(order)
        }
    }

    route("/devices") {
        get { call.respond(state.devices.values.sortedBy { it.id }) }
        post("/{id}/checkout") {
            val device = state.checkout(call.parameters["id"]!!, call.receive<CheckoutRequest>())
            when {
                device == null -> call.respond(HttpStatusCode.NotFound)
                else -> call.respond(device)
            }
        }
        post("/{id}/return") {
            val device = state.returnDevice(call.parameters["id"]!!)
            if (device == null) call.respond(HttpStatusCode.NotFound) else call.respond(device)
        }
    }

    // Multipart upload: "record" (JSON EvidenceRecord) + "file" (the photo).
    post("/evidence") {
        var record: EvidenceRecord? = null
        var bytes: ByteArray? = null
        call.receiveMultipart(formFieldLimit = 25L * 1024 * 1024).forEachPart { part ->
            when (part) {
                is PartData.FormItem -> if (part.name == "record") {
                    record = AppJson.decodeFromString<EvidenceRecord>(part.value)
                }
                is PartData.FileItem -> bytes = part.provider().toByteArray()
                else -> Unit
            }
            part.dispose()
        }
        val r = record
        val b = bytes
        val result = when {
            r == null || b == null -> EvidenceUploadResult(false, "Missing record or file")
            Sha256.of(b) != r.fileSha256 -> EvidenceUploadResult(false, "File hash mismatch")
            !EvidenceChain.isSealedCorrectly(r) -> EvidenceUploadResult(false, "Record seal is invalid")
            else -> {
                state.evidence[r.id] = r
                EvidenceUploadResult(true, "Stored evidence #${r.sequence}")
            }
        }
        call.respond(if (result.accepted) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity, result)
    }

    // Live fleet positions: server pushes snapshots, couriers push their own position.
    webSocket("/fleet") {
        val pusher = launch {
            state.fleet.collect { snapshot ->
                send(Frame.Text(AppJson.encodeToString(snapshot.values.toList())))
            }
        }
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    runCatching { AppJson.decodeFromString<CourierPosition>(frame.readText()) }
                        .onSuccess(state::report)
                }
            }
        } finally {
            pusher.cancel()
        }
    }

    // New order assignments as Server-Sent Events.
    sse("/assignments") {
        state.assignments.collect { order ->
            send(ServerSentEvent(data = AppJson.encodeToString(order), event = "assignment", id = order.id))
        }
    }
}
