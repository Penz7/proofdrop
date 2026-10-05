package com.penz7.proofdrop.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.sse.SSE
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module(state: DispatchState = DispatchState(), simulate: Boolean = true) {
    install(ContentNegotiation) { json(AppJson) }
    install(WebSockets)
    install(SSE)
    install(CallLogging)

    if (simulate) {
        launch { state.runFleetSimulation() }
        launch { state.runAssignmentGenerator() }
    }

    configureRoutes(state)
}
