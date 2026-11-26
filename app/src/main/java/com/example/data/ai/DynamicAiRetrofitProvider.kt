package com.example.data.ai

import com.example.data.local.AiProviderType
import com.example.data.preferences.AppSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface DynamicAiApiService {
    @POST
    suspend fun postChatCompletions(
        @Url url: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET
    suspend fun getModels(
        @Url url: String
    ): Response<ResponseBody>

    @POST
    suspend fun postGeminiGenerateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET
    suspend fun getGeminiModels(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String
    ): Response<ResponseBody>

    @Multipart
    @POST
    suspend fun uploadOpenAiFile(
        @Url url: String,
        @Part("purpose") purpose: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @DELETE
    suspend fun deleteOpenAiFile(
        @Url url: String
    ): Response<ResponseBody>

    @POST
    suspend fun uploadGeminiFile(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Header("X-Goog-Upload-Protocol") uploadProtocol: String = "raw",
        @Header("X-Goog-Upload-Header-Content-Length") contentLength: Long,
        @Header("X-Goog-Upload-Header-Content-Type") contentType: String = "text/xml",
        @Body fileBody: RequestBody
    ): Response<ResponseBody>
}

/**
 * Dynamically initializes and caches a Retrofit client using the active Base URL
 * and API Key saved by the user in DataStore.
 */
class DynamicAiRetrofitProvider {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Volatile
    private var cachedSignature: String? = null

    @Volatile
    private var cachedRetrofit: Retrofit? = null

    @Volatile
    private var cachedService: DynamicAiApiService? = null

    @Synchronized
    fun getRetrofit(settings: AppSettings): Retrofit {
        val normalizedBase = ensureRetrofitBaseUrl(settings.aiBaseUrl, settings.aiProviderType)
        val cleanKey = settings.aiApiKey.trim()
        val signature = "${settings.aiProviderType.name}|$normalizedBase|${cleanKey.hashCode()}|${cleanKey.length}"

        val existing = cachedRetrofit
        if (existing != null && cachedSignature == signature) {
            return existing
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")
            if (settings.aiProviderType != AiProviderType.GEMINI_REST && cleanKey.isNotEmpty()) {
                builder.header("Authorization", "Bearer $cleanKey")
            }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("x-goog-api-key")
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        cachedSignature = signature
        cachedRetrofit = retrofit
        cachedService = retrofit.create(DynamicAiApiService::class.java)
        return retrofit
    }

    fun provideAiService(settings: AppSettings): DynamicAiApiService {
        getRetrofit(settings)
        return cachedService ?: getRetrofit(settings).create(DynamicAiApiService::class.java)
    }

    /**
     * Convenience helper when initializing a dynamic Retrofit instance directly from
     * explicit Base URL and API Key inputs (e.g., Settings -> Fetch Models before saving).
     */
    fun provideAiService(
        provider: AiProviderType,
        baseUrl: String,
        apiKey: String
    ): DynamicAiApiService {
        return provideAiService(
            AppSettings(
                aiProviderType = provider,
                aiBaseUrl = baseUrl,
                aiApiKey = apiKey
            )
        )
    }

    companion object {
        fun ensureRetrofitBaseUrl(rawUrl: String, provider: AiProviderType): String {
            val cleaned = AiSearchClient.normalizeBaseUrl(rawUrl)
            val fallback = when (provider) {
                AiProviderType.GEMINI_REST -> "https://generativelanguage.googleapis.com/v1beta/"
                AiProviderType.OPENAI_COMPATIBLE,
                AiProviderType.CUSTOM -> "https://api.openai.com/v1/"
            }
            if (!AiSearchClient.isValidHttpUrl(cleaned)) return fallback
            return if (cleaned.endsWith('/')) cleaned else "$cleaned/"
        }
    }
}
