package com.penz7.proofdrop.core.data.repository

import com.penz7.proofdrop.core.data.DemoSeeder
import com.penz7.proofdrop.core.data.di.ApplicationScope
import com.penz7.proofdrop.core.data.sync.SyncScheduler
import com.penz7.proofdrop.core.database.OrderDao
import com.penz7.proofdrop.core.database.toEntity
import com.penz7.proofdrop.core.database.toModel
import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.model.OrderStatus
import com.penz7.proofdrop.core.network.AssignmentEvent
import com.penz7.proofdrop.core.network.AssignmentStream
import com.penz7.proofdrop.core.network.ProofDropApi
import com.penz7.proofdrop.core.network.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

interface OrderRepository {
    fun observeOrders(): Flow<List<Order>>
    fun observeOrder(id: String): Flow<Order?>

    /** Pulls the latest orders. Returns false when the server is unreachable (local data is kept). */
    suspend fun refresh(): Boolean

    /** Applied locally right away, synced to the server in the background. */
    suspend fun updateStatus(id: String, status: OrderStatus)

    /** Orders newly assigned to this courier, pushed by the server; reconnects with backoff while collected. */
    fun liveAssignments(): Flow<Order>
}

@Singleton
class OfflineFirstOrderRepository @Inject constructor(
    private val dao: OrderDao,
    private val api: ProofDropApi,
    private val assignmentStream: AssignmentStream,
    private val session: SessionStore,
    private val seeder: DemoSeeder,
    private val syncScheduler: SyncScheduler,
    @ApplicationScope scope: CoroutineScope,
) : OrderRepository {

    private val isDemo get() = session.session.value?.demo != false

    // One SSE connection shared by the Orders screen and the shift service.
    private val assignmentEvents = assignmentStream.events()
        .onEach { event ->
            when (event) {
                is AssignmentEvent.Assigned -> dao.upsert(listOf(event.order.toEntity()))
                is AssignmentEvent.Unassigned -> dao.delete(event.orderId)
            }
        }
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            delay(min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (attempt + 1)))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000))

    override fun observeOrders(): Flow<List<Order>> =
        dao.observeAll()
            .onStart { if (isDemo) seeder.seedIfEmpty() }
            .map { list -> list.map { it.toModel() } }

    override fun observeOrder(id: String): Flow<Order?> = dao.observe(id).map { it?.toModel() }

    override suspend fun refresh(): Boolean {
        if (isDemo) return false
        return try {
            dao.mergeFromServer(api.orders().map { it.toEntity() })
            true
        } catch (e: IOException) {
            false
        } catch (e: HttpException) {
            false
        }
    }

    override suspend fun updateStatus(id: String, status: OrderStatus) {
        dao.updateStatusLocally(id, status)
        if (!isDemo) syncScheduler.requestSync()
    }

    override fun liveAssignments(): Flow<Order> =
        if (isDemo) emptyFlow()
        else assignmentEvents.filterIsInstance<AssignmentEvent.Assigned>().map { it.order }

    private companion object {
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
