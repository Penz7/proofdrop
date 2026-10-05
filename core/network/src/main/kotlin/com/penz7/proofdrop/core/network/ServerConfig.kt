package com.penz7.proofdrop.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the dispatch server lives. Defaults to the host machine as seen from the emulator;
 * on a real phone, set it to your computer's LAN IP from the Fleet screen.
 */
@Singleton
class ServerConfig @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("server_config", Context.MODE_PRIVATE)
    private val _baseUrl = MutableStateFlow(prefs.getString(KEY, null) ?: DEFAULT_URL)
    val baseUrl: StateFlow<String> = _baseUrl

    val httpUrl: HttpUrl get() = baseUrl.value.toHttpUrlOrNull() ?: DEFAULT_URL.toHttpUrlOrNull()!!

    /** Returns false if [url] is not a valid http(s) URL. */
    fun update(url: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        if (normalized.toHttpUrlOrNull() == null) return false
        prefs.edit().putString(KEY, normalized).apply()
        _baseUrl.value = normalized
        return true
    }

    companion object {
        const val DEFAULT_URL = "http://10.0.2.2:8080"
        private const val KEY = "base_url"
    }
}
