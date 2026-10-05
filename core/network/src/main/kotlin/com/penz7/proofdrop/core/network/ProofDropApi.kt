package com.penz7.proofdrop.core.network

import com.penz7.proofdrop.core.model.ChainHead
import com.penz7.proofdrop.core.model.CheckoutRequest
import com.penz7.proofdrop.core.model.Device
import com.penz7.proofdrop.core.model.EvidenceUploadResult
import com.penz7.proofdrop.core.model.LoginRequest
import com.penz7.proofdrop.core.model.LoginResponse
import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.model.OrderStatusUpdate
import com.penz7.proofdrop.core.model.User
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Courier-facing endpoints of the backend. Contract: docs/API.md. */
interface ProofDropApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("auth/me")
    suspend fun me(): User

    @GET("orders")
    suspend fun orders(): List<Order>

    @POST("orders/{id}/status")
    suspend fun updateStatus(@Path("id") id: String, @Body update: OrderStatusUpdate): Order

    @GET("devices")
    suspend fun devices(): List<Device>

    @POST("devices/{id}/checkout")
    suspend fun checkout(@Path("id") id: String, @Body request: CheckoutRequest): Device

    @POST("devices/{id}/return")
    suspend fun returnDevice(@Path("id") id: String): Device

    @GET("evidence/head")
    suspend fun evidenceHead(): ChainHead

    /** 200 = stored, 409 = an earlier record must be uploaded first, 422 = rejected (hash mismatch, bad seal…). */
    @Multipart
    @POST("evidence")
    suspend fun uploadEvidence(
        @Part("record") record: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<EvidenceUploadResult>
}
