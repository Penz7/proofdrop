package com.penz7.proofdrop.core.network.session

import android.content.Context
import com.penz7.proofdrop.core.model.ChainHead
import com.penz7.proofdrop.core.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A signed-in courier. Demo sessions have no token and never touch the network. */
data class Session(val user: User, val token: String?, val demo: Boolean)

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: TokenCipher,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Emits when the server rejects our token, so the UI can send the user back to login. */
    val unauthorized: SharedFlow<Unit> = _unauthorized

    val token: String? get() = _session.value?.token

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_USER, json.encodeToString(session.user))
            .putString(KEY_TOKEN, session.token?.let(cipher::encrypt))
            .putBoolean(KEY_DEMO, session.demo)
            .apply()
        _session.value = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    fun onUnauthorized() {
        if (_session.value?.demo == false) _unauthorized.tryEmit(Unit)
    }

    /** Where this courier's chain stood on the server at login; local sealing continues from here. */
    var chainHead: ChainHead
        get() = ChainHead(prefs.getLong(KEY_HEAD_SEQ, 0), prefs.getString(KEY_HEAD_HASH, null) ?: GENESIS)
        set(value) {
            prefs.edit().putLong(KEY_HEAD_SEQ, value.sequence).putString(KEY_HEAD_HASH, value.recordHash).apply()
        }

    private fun load(): Session? {
        val user = prefs.getString(KEY_USER, null)
            ?.let { runCatching { json.decodeFromString<User>(it) }.getOrNull() } ?: return null
        val demo = prefs.getBoolean(KEY_DEMO, false)
        val token = prefs.getString(KEY_TOKEN, null)?.let(cipher::decrypt)
        if (!demo && token == null) return null
        return Session(user, token, demo)
    }

    private companion object {
        const val KEY_USER = "user"
        const val KEY_TOKEN = "token"
        const val KEY_DEMO = "demo"
        const val KEY_HEAD_SEQ = "head_seq"
        const val KEY_HEAD_HASH = "head_hash"
        val GENESIS = "0".repeat(64)
    }
}
