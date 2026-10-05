package com.penz7.proofdrop.core.data

import com.penz7.proofdrop.core.network.ServerConfig
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Lets the UI change the dispatch server without depending on the network module. */
@Singleton
class ServerSettings @Inject constructor(private val config: ServerConfig) {
    val baseUrl: StateFlow<String> = config.baseUrl

    fun update(url: String): Boolean = config.update(url)
}
