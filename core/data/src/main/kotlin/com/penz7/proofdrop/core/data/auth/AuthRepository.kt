package com.penz7.proofdrop.core.data.auth

import android.content.Context
import androidx.work.WorkManager
import com.penz7.proofdrop.core.data.di.ApplicationScope
import com.penz7.proofdrop.core.data.repository.EvidenceStorage
import com.penz7.proofdrop.core.data.shift.ShiftController
import com.penz7.proofdrop.core.database.EvidenceDao
import com.penz7.proofdrop.core.database.ProofDropDatabase
import com.penz7.proofdrop.core.model.ChainHead
import com.penz7.proofdrop.core.model.DemoData
import com.penz7.proofdrop.core.model.LoginRequest
import com.penz7.proofdrop.core.model.Role
import com.penz7.proofdrop.core.model.User
import com.penz7.proofdrop.core.network.ProofDropApi
import com.penz7.proofdrop.core.network.ServerConfig
import com.penz7.proofdrop.core.network.session.Session
import com.penz7.proofdrop.core.network.session.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Who is using the app. [demo] = offline demo mode with local sample data. */
data class CurrentUser(val user: User, val demo: Boolean)

sealed interface LoginResult {
    data object Success : LoginResult
    data object InvalidServerUrl : LoginResult
    data object InvalidCredentials : LoginResult
    data object NotACourier : LoginResult
    data class Unreachable(val reason: String) : LoginResult
}

interface AuthRepository {
    val currentUser: StateFlow<CurrentUser?>

    /** Emits when the server rejects the stored token (expired or revoked). */
    val sessionExpired: Flow<Unit>

    val serverUrl: StateFlow<String>

    suspend fun login(serverUrl: String, email: String, password: String): LoginResult
    suspend fun startDemo()

    /** Evidence sealed on this phone but not yet accepted by the server; lost on logout. */
    suspend fun unsyncedEvidenceCount(): Int
    suspend fun logout()
}

@Singleton
class DefaultAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
    private val serverConfig: ServerConfig,
    private val api: ProofDropApi,
    private val database: ProofDropDatabase,
    private val evidenceDao: EvidenceDao,
    private val evidenceStorage: EvidenceStorage,
    private val shift: ShiftController,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    override val currentUser: StateFlow<CurrentUser?> = sessionStore.session
        .map { it?.let { s -> CurrentUser(s.user, s.demo) } }
        .stateIn(scope, SharingStarted.Eagerly, sessionStore.session.value?.let { CurrentUser(it.user, it.demo) })

    override val sessionExpired: Flow<Unit> = sessionStore.unauthorized

    override val serverUrl: StateFlow<String> = serverConfig.baseUrl

    override suspend fun login(serverUrl: String, email: String, password: String): LoginResult {
        if (!serverConfig.update(serverUrl)) return LoginResult.InvalidServerUrl
        return try {
            val response = api.login(LoginRequest(email.trim(), password))
            if (response.user.role != Role.COURIER) return LoginResult.NotACourier
            wipeLocalData()
            sessionStore.save(Session(response.user, response.accessToken, demo = false))
            // Continue this courier's server-side evidence chain instead of starting a new one.
            sessionStore.chainHead = api.evidenceHead()
            LoginResult.Success
        } catch (e: HttpException) {
            if (e.code() == 401) LoginResult.InvalidCredentials else LoginResult.Unreachable("Server error ${e.code()}")
        } catch (e: IOException) {
            LoginResult.Unreachable(e.message ?: "Cannot reach server")
        }
    }

    override suspend fun startDemo() {
        wipeLocalData()
        sessionStore.save(Session(DemoData.demoCourier, token = null, demo = true))
    }

    override suspend fun unsyncedEvidenceCount(): Int =
        if (sessionStore.session.value?.demo == true) 0 else evidenceDao.pendingUpload().size

    override suspend fun logout() {
        shift.stop()
        WorkManager.getInstance(context).cancelAllWork()
        wipeLocalData()
        sessionStore.clear()
    }

    private suspend fun wipeLocalData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        evidenceStorage.deleteAll()
        sessionStore.chainHead = ChainHead(0, "0".repeat(64))
    }
}
