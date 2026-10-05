package com.penz7.proofdrop.core.network

import com.penz7.proofdrop.core.network.session.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.internal.platform.Platform
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
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(config: ServerConfig, session: SessionStore): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(baseUrlInterceptor(config))
        .addInterceptor(authInterceptor(session))
        .addInterceptor(loggingInterceptor())
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

    /** Adds the bearer token and reports 401s so the app can return to the login screen. */
    internal fun authInterceptor(session: SessionStore) = Interceptor { chain ->
        val token = session.token
        val request = if (token != null && chain.request().header("Authorization") == null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request).also { response ->
            val isLogin = request.url.encodedPath.endsWith("/auth/login")
            if (response.code == 401 && !isLogin) session.onUnauthorized()
        }
    }

    /** BASIC request logs, with the JWT stripped from WebSocket/SSE query strings. */
    private fun loggingInterceptor() = HttpLoggingInterceptor { message ->
        Platform.get().log(message.replace(TOKEN_IN_URL, "access_token=***"))
    }.apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val TOKEN_IN_URL = Regex("""access_token=[^&\s]+""")

    private const val PLACEHOLDER_HOST = "proofdrop.placeholder"
    private const val PLACEHOLDER_URL = "http://$PLACEHOLDER_HOST/api/"
}
