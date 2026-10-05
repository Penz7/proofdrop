package com.penz7.proofdrop.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Role { COURIER, DISPATCHER }

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: Role,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val accessToken: String, val user: User)

/** The last sealed record of a courier's chain on the server; new records are sealed on top of it. */
@Serializable
data class ChainHead(val sequence: Long, val recordHash: String)
