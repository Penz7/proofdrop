package com.penz7.proofdrop.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun okHttp(config: ServerConfig): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(baseUrlInterceptor(config))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): ProofDropApi = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ProofDropApi::class.java)

    /**
     * Retrofit's base URL is fixed at build time, but the server URL is user-editable.
     * Rewrite placeholder requests to the current [ServerConfig] instead.
     */
    internal fun baseUrlInterceptor(config: ServerConfig) = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host != PLACEHOLDER_HOST) return@Interceptor chain.proceed(request)
        val target = config.httpUrl
        val url = request.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        chain.proceed(request.newBuilder().url(url).build())
    }

    private const val PLACEHOLDER_HOST = "proofdrop.placeholder"
    private const val PLACEHOLDER_URL = "http://$PLACEHOLDER_HOST/"
}
