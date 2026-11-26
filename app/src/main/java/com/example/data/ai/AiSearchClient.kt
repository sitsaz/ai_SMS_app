package com.example.data.ai

import android.util.Log
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiContextMode
import com.example.data.local.AiProviderType
import com.example.data.preferences.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AiConnectionTestReport(
    val replyPreview: String,
    val supportsFileUpload: Boolean,
    val supportsImageInput: Boolean,
    val detectedContextLength: Int?,
    val capabilitySummary: String
)

class AiSearchClient(
    private val retrofitProvider: DynamicAiRetrofitProvider = DynamicAiRetrofitProvider()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val xmlTextMediaType = "text/plain; charset=utf-8".toMediaType()

    suspend fun askWithSearchContext(
        settings: AppSettings,
        userQuestion: String,
        contextPayload: String,
        contextMode: AiContextMode,
        priorHistory: List<AiChatMessageEntity>,
        isPersian: Boolean,
        fileUploadPayload: String? = null
    ): Result<AiParsedSearchResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.isAiConfigured) {
                if (isPersian) "تنظیمات API هوش مصنوعی کامل نیست." else "AI API settings are incomplete."
            }
            require(isValidHttpUrl(settings.aiBaseUrl)) {
                if (isPersian) "آدرس Base URL باید با http:// یا https:// شروع شود." else "Base URL must start with http:// or https://"
            }

            val systemPrompt = buildStructuredXmlPrompt(isPersian, contextMode)

            // In SMART_AUTO mode, if the endpoint supports File Upload and a full archive payload is available,
            // upload the full archive as a file first to send maximum information, falling back to budgeted inline text if rejected.
            if (contextMode == AiContextMode.SMART_AUTO &&
                settings.aiSupportsFileUpload &&
                !fileUploadPayload.isNullOrBlank()
            ) {
                val uploadedAnswer = runCatching {
                    executeWithUploadedXmlFile(
                        settings = settings,
                        systemPrompt = systemPrompt + "\nAn archive file containing SMS/notification records is attached.",
                        priorHistory = priorHistory,
                        userQuestion = userQuestion,
                        fileXmlContent = fileUploadPayload
                    )
                }.getOrNull()

                if (!uploadedAnswer.isNullOrBlank()) {
                    return@runCatching AiXmlResponseParser.parse(uploadedAnswer)
                }
            }

            val rawResponseText = try {
                executeAiRequest(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    priorHistory = priorHistory,
                    userQuestion = userQuestion,
                    contextPayload = contextPayload
                )
            } catch (err: Throwable) {
                if (shouldRetryCondensed(err)) {
                    val condensed = condenseContextLines(contextPayload, maxLines = 35)
                    executeAiRequest(
                        settings = settings,
                        systemPrompt = buildStructuredXmlPrompt(isPersian, AiContextMode.COMPACT_TEXT),
                        priorHistory = priorHistory.takeLast(2),
                        userQuestion = userQuestion,
                        contextPayload = condensed
                    )
                } else {
                    throw err
                }
            }

            AiXmlResponseParser.parse(rawResponseText)
        }.onFailure { err ->
            Log.e("AiSearchClient", "AI request failed: ${err.message}")
        }
    }

    suspend fun askWithStructuredXmlPrompt(
        settings: AppSettings,
        structuredXmlPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        isPersian: Boolean,
        smartAuto: Boolean = false,
        fileUploadXmlPayload: String? = null,
        userQuestionForFileMode: String = ""
    ): Result<AiParsedSearchResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.isAiConfigured) {
                if (isPersian) "تنظیمات API هوش مصنوعی کامل نیست." else "AI API settings are incomplete."
            }
            require(isValidHttpUrl(settings.aiBaseUrl)) {
                if (isPersian) "آدرس Base URL باید با http:// یا https:// شروع شود." else "Base URL must start with http:// or https://"
            }

            val systemPrompt = buildStructuredXmlPrompt(
                isPersian,
                if (smartAuto) AiContextMode.SMART_AUTO else AiContextMode.INLINE_XML
            ) + "\nThe user message itself is already formatted as an <ai_query_request> XML document containing <user_query>, <context_scope>, and <conversation_history> or <notification_history>."

            if (smartAuto && settings.aiSupportsFileUpload && !fileUploadXmlPayload.isNullOrBlank()) {
                val uploadedAnswer = runCatching {
                    executeWithUploadedXmlFile(
                        settings = settings,
                        systemPrompt = systemPrompt + "\nAnalyze the attached full <ai_query_request> XML file.",
                        priorHistory = priorHistory,
                        userQuestion = userQuestionForFileMode.ifBlank { "Answer the user_query in the attached XML archive." },
                        fileXmlContent = fileUploadXmlPayload
                    )
                }.getOrNull()

                if (!uploadedAnswer.isNullOrBlank()) {
                    return@runCatching AiXmlResponseParser.parse(uploadedAnswer)
                }
            }

            val rawResponseText = try {
                executeRawUserPrompt(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    priorHistory = priorHistory,
                    rawUserContent = structuredXmlPrompt
                )
            } catch (err: Throwable) {
                if (shouldRetryCondensed(err)) {
                    val condensedXml = condenseStructuredXmlLines(structuredXmlPrompt, maxRecordLines = 30)
                    executeRawUserPrompt(
                        settings = settings,
                        systemPrompt = systemPrompt,
                        priorHistory = priorHistory.takeLast(2),
                        rawUserContent = condensedXml
                    )
                } else {
                    throw err
                }
            }

            AiXmlResponseParser.parse(rawResponseText)
        }.onFailure { err ->
            Log.e("AiSearchClient", "Structured XML AI request failed: ${err.message}")
        }
    }

    private suspend fun executeWithUploadedXmlFile(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        userQuestion: String,
        fileXmlContent: String
    ): String {
        val service = retrofitProvider.provideAiService(settings)
        return when (settings.aiProviderType) {
            AiProviderType.GEMINI_REST -> {
                val uploadUrl = buildGeminiUploadUrl(settings.aiBaseUrl)
                val bytes = fileXmlContent.toByteArray(Charsets.UTF_8)
                val reqBody = bytes.toRequestBody(xmlTextMediaType)
                val uploadResp = service.uploadGeminiFile(
                    url = uploadUrl,
                    apiKey = settings.aiApiKey.trim(),
                    contentLength = bytes.size.toLong(),
                    contentType = "text/xml",
                    fileBody = reqBody
                )
                val uploadRaw = uploadResp.body()?.string() ?: uploadResp.errorBody()?.string().orEmpty()
                if (!uploadResp.isSuccessful) {
                    throw IllegalStateException("Gemini file upload HTTP ${uploadResp.code()}")
                }
                val fileObj = JSONObject(uploadRaw).optJSONObject("file") ?: JSONObject(uploadRaw)
                val fileUri = fileObj.optString("uri")
                require(fileUri.isNotBlank()) { "Missing Gemini uploaded file uri" }

                val base = normalizeBaseUrl(settings.aiBaseUrl)
                val model = settings.aiModelName.trim().removePrefix("models/")
                val genUrl = if (base.contains(":generateContent")) base else "$base/models/$model:generateContent"

                val contents = JSONArray()
                for (msg in priorHistory.takeLast(6)) {
                    if (msg.isError) continue
                    val role = if (msg.role == "USER") "user" else "model"
                    contents.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    })
                }
                val userParts = JSONArray().apply {
                    put(JSONObject().put("fileData", JSONObject().apply {
                        put("mimeType", "text/xml")
                        put("fileUri", fileUri)
                    }))
                    put(JSONObject().put("text", userQuestion))
                }
                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", userParts)
                })
                val payload = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                    put("contents", contents)
                    put("generationConfig", JSONObject().put("temperature", 0.2))
                }
                val resp = service.postGeminiGenerateContent(
                    url = genUrl,
                    apiKey = settings.aiApiKey.trim(),
                    body = payload.toString().toRequestBody(jsonMediaType)
                )
                val rawBody = resp.body()?.string() ?: resp.errorBody()?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Gemini file chat HTTP ${resp.code()}: ${extractErrorMessage(rawBody)}")
                }
                val json = JSONObject(rawBody)
                val candidates = json.optJSONArray("candidates") ?: throw IllegalStateException("No candidates")
                candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text", "").trim()
            }

            AiProviderType.OPENAI_COMPATIBLE,
            AiProviderType.CUSTOM -> {
                val filesUrl = buildOpenAiFilesUrl(settings.aiBaseUrl)
                val purposeBody = "user_data".toRequestBody("text/plain; charset=utf-8".toMediaType())
                val filePart = MultipartBody.Part.createFormData(
                    name = "file",
                    filename = "sms_archive_context.txt",
                    body = fileXmlContent.toByteArray(Charsets.UTF_8).toRequestBody(xmlTextMediaType)
                )
                val uploadResp = service.uploadOpenAiFile(
                    url = filesUrl,
                    purpose = purposeBody,
                    file = filePart
                )
                val uploadRaw = uploadResp.body()?.string() ?: uploadResp.errorBody()?.string().orEmpty()
                if (!uploadResp.isSuccessful) {
                    throw IllegalStateException("OpenAI file upload HTTP ${uploadResp.code()}")
                }
                val fileId = JSONObject(uploadRaw).optString("id")
                require(fileId.isNotBlank()) { "Missing uploaded file id" }

                try {
                    val chatUrl = buildOpenAiChatUrl(settings.aiBaseUrl)
                    val messagesArray = JSONArray()
                    messagesArray.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    for (msg in priorHistory.takeLast(6)) {
                        if (msg.isError) continue
                        val role = if (msg.role == "USER") "user" else "assistant"
                        messagesArray.put(JSONObject().apply {
                            put("role", role)
                            put("content", msg.text)
                        })
                    }
                    val contentParts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "file")
                            put("file", JSONObject().put("file_id", fileId))
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", userQuestion)
                        })
                    }
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", contentParts)
                    })
                    val payload = JSONObject().apply {
                        put("model", settings.aiModelName.trim())
                        put("messages", messagesArray)
                        put("temperature", 0.2)
                    }
                    val resp = service.postChatCompletions(
                        url = chatUrl,
                        body = payload.toString().toRequestBody(jsonMediaType)
                    )
                    val rawBody = resp.body()?.string() ?: resp.errorBody()?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("OpenAI file chat HTTP ${resp.code()}: ${extractErrorMessage(rawBody)}")
                    }
                    val choices = JSONObject(rawBody).optJSONArray("choices") ?: throw IllegalStateException("No choices")
                    choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                } finally {
                    // Best-effort cleanup of temporary archive file on server
                    runCatching { service.deleteOpenAiFile("$filesUrl/$fileId") }
                }
            }
        }
    }

    private suspend fun executeAiRequest(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        userQuestion: String,
        contextPayload: String
    ): String {
        val currentUserContent = buildString {
            append("User question:\n")
            append(userQuestion)
            append("\n\nArchive context:\n")
            append(contextPayload)
        }
        return executeRawUserPrompt(
            settings = settings,
            systemPrompt = systemPrompt,
            priorHistory = priorHistory,
            rawUserContent = currentUserContent
        )
    }

    private suspend fun executeRawUserPrompt(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        rawUserContent: String
    ): String {
        return when (settings.aiProviderType) {
            AiProviderType.GEMINI_REST -> callGeminiRest(
                settings = settings,
                systemPrompt = systemPrompt,
                priorHistory = priorHistory,
                rawUserContent = rawUserContent
            )
            AiProviderType.OPENAI_COMPATIBLE,
            AiProviderType.CUSTOM -> callOpenAiCompatible(
                settings = settings,
                systemPrompt = systemPrompt,
                priorHistory = priorHistory,
                rawUserContent = rawUserContent
            )
        }
    }

    suspend fun testConnection(settings: AppSettings, isPersian: Boolean): Result<String> =
        testConnectionWithCapabilities(settings, isPersian).map { report ->
            "${report.replyPreview} | ${report.capabilitySummary}"
        }

    suspend fun testConnectionWithCapabilities(
        settings: AppSettings,
        isPersian: Boolean
    ): Result<AiConnectionTestReport> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.isAiConfigured) {
                if (isPersian) "لطفاً آدرس API، مدل و کلید را وارد کنید." else "Please enter Base URL, Model ID, and API Key."
            }
            require(isValidHttpUrl(settings.aiBaseUrl)) {
                if (isPersian) "آدرس Base URL باید با http:// یا https:// شروع شود." else "Base URL must start with http:// or https://"
            }

            val reply = executeAiRequest(
                settings = settings,
                systemPrompt = "Reply with OK.",
                priorHistory = emptyList(),
                userQuestion = "Connection check. Reply with OK.",
                contextPayload = "No archive attached."
            )

            // Probe 1: Inspect model metadata for context length & multimodal capabilities
            val detectedCtx = probeModelContextLength(settings)

            // Probe 2: Probe Image Input support with tiny 1x1 PNG
            val supportsImages = probeImageCapability(settings)

            // Probe 3: Probe File Upload support with tiny XML text snippet (and delete probe file if OpenAI)
            val supportsFiles = probeFileUploadCapability(settings)

            val fileText = if (supportsFiles) {
                if (isPersian) "آپلود فایل: پشتیبانی می‌شود" else "File upload: Supported"
            } else {
                if (isPersian) "آپلود فایل: پشتیبانی نمی‌شود" else "File upload: Not supported"
            }
            val imgText = if (supportsImages) {
                if (isPersian) "تصویر: پشتیبانی می‌شود" else "Image input: Supported"
            } else {
                if (isPersian) "تصویر: پشتیبانی نمی‌شود" else "Image input: Not supported"
            }
            val ctxText = detectedCtx?.let {
                if (isPersian) "کانتکس مدل: $it" else "Model ctx: $it"
            }

            val summary = listOfNotNull(fileText, imgText, ctxText).joinToString(" • ")

            AiConnectionTestReport(
                replyPreview = reply.take(80),
                supportsFileUpload = supportsFiles,
                supportsImageInput = supportsImages,
                detectedContextLength = detectedCtx,
                capabilitySummary = summary
            )
        }
    }

    private suspend fun probeModelContextLength(settings: AppSettings): Int? {
        return runCatching {
            val service = retrofitProvider.provideAiService(settings)
            val cleanModel = settings.aiModelName.trim().removePrefix("models/")
            when (settings.aiProviderType) {
                AiProviderType.GEMINI_REST -> {
                    val base = normalizeBaseUrl(settings.aiBaseUrl)
                    val url = if (base.endsWith("/models")) "$base/$cleanModel" else "$base/models/$cleanModel"
                    val resp = service.getGeminiModels(url = url, apiKey = settings.aiApiKey.trim())
                    val raw = resp.body()?.string().orEmpty()
                    if (!resp.isSuccessful || raw.isBlank()) return@runCatching null
                    val obj = JSONObject(raw)
                    obj.optInt("inputTokenLimit").takeIf { it > 0 }
                }
                AiProviderType.OPENAI_COMPATIBLE,
                AiProviderType.CUSTOM -> {
                    val url = buildOpenAiModelsUrl(settings.aiBaseUrl)
                    val resp = service.getModels(url = url)
                    val raw = resp.body()?.string().orEmpty()
                    if (!resp.isSuccessful || raw.isBlank()) return@runCatching null
                    val arr = if (raw.trim().startsWith("[")) {
                        JSONArray(raw)
                    } else {
                        JSONObject(raw).optJSONArray("data") ?: JSONObject(raw).optJSONArray("models")
                    } ?: return@runCatching null

                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").ifBlank { obj.optString("name") }
                        if (id.equals(cleanModel, ignoreCase = true) || id.endsWith("/$cleanModel", ignoreCase = true)) {
                            val ctx = listOf("context_length", "context_window", "max_context_tokens", "input_token_limit")
                                .firstNotNullOfOrNull { key -> obj.optInt(key).takeIf { it > 0 } }
                            if (ctx != null) return@runCatching ctx
                        }
                    }
                    null
                }
            }
        }.getOrNull()
    }

    private suspend fun probeImageCapability(settings: AppSettings): Boolean {
        val tinyPngBase64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
        return runCatching {
            val service = retrofitProvider.provideAiService(settings)
            when (settings.aiProviderType) {
                AiProviderType.GEMINI_REST -> {
                    val base = normalizeBaseUrl(settings.aiBaseUrl)
                    val model = settings.aiModelName.trim().removePrefix("models/")
                    val url = if (base.contains(":generateContent")) base else "$base/models/$model:generateContent"
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/png")
                                    put("data", tinyPngBase64)
                                }))
                                put(JSONObject().put("text", "Reply OK"))
                            })
                        }))
                        put("generationConfig", JSONObject().put("maxOutputTokens", 4))
                    }
                    val resp = service.postGeminiGenerateContent(url, settings.aiApiKey.trim(), payload.toString().toRequestBody(jsonMediaType))
                    resp.isSuccessful
                }
                AiProviderType.OPENAI_COMPATIBLE,
                AiProviderType.CUSTOM -> {
                    val url = buildOpenAiChatUrl(settings.aiBaseUrl)
                    val contentParts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:image/png;base64,$tinyPngBase64"))
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "Reply OK")
                        })
                    }
                    val payload = JSONObject().apply {
                        put("model", settings.aiModelName.trim())
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", contentParts)
                        }))
                        put("max_tokens", 4)
                    }
                    val resp = service.postChatCompletions(url, payload.toString().toRequestBody(jsonMediaType))
                    resp.isSuccessful
                }
            }
        }.getOrDefault(false)
    }

    private suspend fun probeFileUploadCapability(settings: AppSettings): Boolean {
        val sampleXml = "<?xml version=\"1.0\"?><probe>ok</probe>"
        return runCatching {
            val service = retrofitProvider.provideAiService(settings)
            when (settings.aiProviderType) {
                AiProviderType.GEMINI_REST -> {
                    val url = buildGeminiUploadUrl(settings.aiBaseUrl)
                    val bytes = sampleXml.toByteArray(Charsets.UTF_8)
                    val resp = service.uploadGeminiFile(
                        url = url,
                        apiKey = settings.aiApiKey.trim(),
                        contentLength = bytes.size.toLong(),
                        contentType = "text/xml",
                        fileBody = bytes.toRequestBody(xmlTextMediaType)
                    )
                    resp.isSuccessful
                }
                AiProviderType.OPENAI_COMPATIBLE,
                AiProviderType.CUSTOM -> {
                    val filesUrl = buildOpenAiFilesUrl(settings.aiBaseUrl)
                    val purposeBody = "assistants".toRequestBody("text/plain; charset=utf-8".toMediaType())
                    val part = MultipartBody.Part.createFormData(
                        name = "file",
                        filename = "probe_context.txt",
                        body = sampleXml.toByteArray(Charsets.UTF_8).toRequestBody(xmlTextMediaType)
                    )
                    val resp = service.uploadOpenAiFile(filesUrl, purposeBody, part)
                    val body = resp.body()?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val id = runCatching { JSONObject(body).optString("id") }.getOrDefault("")
                        if (id.isNotBlank()) {
                            runCatching { service.deleteOpenAiFile("$filesUrl/$id") }
                        }
                        true
                    } else {
                        false
                    }
                }
            }
        }.getOrDefault(false)
    }

    suspend fun fetchAvailableModels(
        provider: AiProviderType,
        baseUrl: String,
        apiKey: String,
        isPersian: Boolean
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = baseUrl.trim()
            val cleanKey = apiKey.trim()
            require(isValidHttpUrl(cleanUrl)) {
                if (isPersian) "آدرس Base URL معتبر نیست (باید با https:// شروع شود)." else "Enter a valid http:// or https:// Base URL."
            }
            require(cleanKey.isNotEmpty()) {
                if (isPersian) "برای دریافت لیست مدل‌ها ابتدا کلید API را وارد کنید." else "Enter your API key first to fetch available models."
            }

            val service = retrofitProvider.provideAiService(provider, cleanUrl, cleanKey)

            when (provider) {
                AiProviderType.GEMINI_REST -> {
                    val base = normalizeBaseUrl(cleanUrl)
                    val url = if (base.endsWith("/models")) base else "$base/models"
                    val response = service.getGeminiModels(url = url, apiKey = cleanKey)
                    val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code()}: ${extractErrorMessage(rawBody)}")
                    }
                    parseGeminiModels(rawBody)
                }
                AiProviderType.OPENAI_COMPATIBLE,
                AiProviderType.CUSTOM -> {
                    val url = buildOpenAiModelsUrl(cleanUrl)
                    val response = service.getModels(url = url)
                    val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code()}: ${extractErrorMessage(rawBody)}")
                    }
                    parseOpenAiModels(rawBody)
                }
            }
        }
    }

    private fun parseOpenAiModels(rawBody: String): List<String> {
        val ids = linkedSetOf<String>()
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                val id = when (item) {
                    is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                    is String -> item
                    else -> ""
                }.trim()
                if (id.isNotEmpty()) ids.add(id)
            }
        } else {
            val obj = JSONObject(trimmed)
            val dataArr = obj.optJSONArray("data") ?: obj.optJSONArray("models")
            if (dataArr != null) {
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.opt(i)
                    val id = when (item) {
                        is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                        is String -> item
                        else -> ""
                    }.trim()
                    if (id.isNotEmpty()) ids.add(id)
                }
            }
        }
        return ids.toList().sorted()
    }

    private fun parseGeminiModels(rawBody: String): List<String> {
        val ids = linkedSetOf<String>()
        val obj = JSONObject(rawBody)
        val arr = obj.optJSONArray("models")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name").removePrefix("models/").trim()
                if (name.isNotEmpty()) ids.add(name)
            }
        }
        return ids.toList().sorted()
    }

    private fun buildStructuredXmlPrompt(isPersian: Boolean, contextMode: AiContextMode): String {
        val langInstruction = if (isPersian) {
            "Write <answer>, <summary>, <reason>, and <question> in clear, natural Persian (Farsi) and reference Jalali dates when helpful. You may use clean Markdown formatting including GFM tables inside <answer>."
        } else {
            "Write <answer>, <summary>, <reason>, and <question> in clear, natural English. You may use clean Markdown formatting including GFM tables inside <answer>."
        }
        val modeNote = when (contextMode) {
            AiContextMode.SMART_AUTO -> "The user provided a smart token-budgeted digest or attached archive file with the latest relevant records fitting the model context limit."
            AiContextMode.INLINE_XML -> "The user provided inline <archive> XML items with ids like sms:123 or notif:45."
            AiContextMode.COMPACT_TEXT,
            AiContextMode.KEYWORD_PREFILTER -> "The user provided a compact plain-text message/notification digest (no file upload) with ids like [sms:123] or [notif:45]."
        }
        return """
You are an assistant inside AI SMS Manager analyzing the user's SMS messages and device notifications.
$modeNote
$langInstruction
Respond ONLY with valid XML (no outer ```xml code fences) using this schema:
<ai_search_response>
  <summary>Short 1-sentence overview</summary>
  <answer>Helpful, accurate answer strictly grounded in the provided records.</answer>
  <matches>
    <match id="sms:123" source="sms" sender="Sender Name" date="2026/11/25 14:20">
      <snippet>Short relevant quote</snippet>
      <reason>Why this item matches the query</reason>
    </match>
  </matches>
  <follow_ups>
    <question>Helpful short follow-up question</question>
  </follow_ups>
</ai_search_response>
Only include <match> elements whose id (e.g. sms:123 or notif:45) actually appears in the provided data. If nothing matches, leave <matches /> empty and explain clearly in <answer>.
        """.trimIndent()
    }

    private suspend fun callOpenAiCompatible(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        rawUserContent: String
    ): String {
        val url = buildOpenAiChatUrl(settings.aiBaseUrl)
        require(isValidHttpUrl(url)) { "Base URL must start with http:// or https://" }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        for (msg in priorHistory.takeLast(6)) {
            if (msg.isError) continue
            val role = if (msg.role == "USER") "user" else "assistant"
            messagesArray.put(JSONObject().apply {
                put("role", role)
                put("content", msg.text)
            })
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", rawUserContent)
        })

        val payload = JSONObject().apply {
            put("model", settings.aiModelName.trim())
            put("messages", messagesArray)
            put("temperature", 0.2)
        }

        val service = retrofitProvider.provideAiService(settings)
        val response = service.postChatCompletions(
            url = url,
            body = payload.toString().toRequestBody(jsonMediaType)
        )
        val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code()}: ${extractErrorMessage(rawBody)}")
        }
        val json = JSONObject(rawBody)
        val choices = json.optJSONArray("choices")
            ?: throw IllegalStateException("Missing choices in API response")
        if (choices.length() == 0) throw IllegalStateException("Empty choices from AI API")
        val message = choices.getJSONObject(0).getJSONObject("message")
        return message.optString("content", "").trim()
    }

    private suspend fun callGeminiRest(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        rawUserContent: String
    ): String {
        val base = normalizeBaseUrl(settings.aiBaseUrl)
        require(isValidHttpUrl(base)) { "Base URL must start with http:// or https://" }
        val model = settings.aiModelName.trim().removePrefix("models/")
        val url = if (base.contains(":generateContent")) {
            base
        } else {
            "$base/models/$model:generateContent"
        }

        val contents = JSONArray()
        for (msg in priorHistory.takeLast(6)) {
            if (msg.isError) continue
            val role = if (msg.role == "USER") "user" else "model"
            contents.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }

        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", rawUserContent)))
        })

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val service = retrofitProvider.provideAiService(settings)
        val response = service.postGeminiGenerateContent(
            url = url,
            apiKey = settings.aiApiKey.trim(),
            body = payload.toString().toRequestBody(jsonMediaType)
        )
        val rawBody = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code()}: ${extractErrorMessage(rawBody)}")
        }
        val json = JSONObject(rawBody)
        val candidates = json.optJSONArray("candidates")
            ?: throw IllegalStateException("Missing candidates in Gemini response")
        if (candidates.length() == 0) throw IllegalStateException("Empty candidates from Gemini API")
        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        return parts.getJSONObject(0).optString("text", "").trim()
    }

    private fun shouldRetryCondensed(err: Throwable): Boolean {
        val msg = err.message?.lowercase().orEmpty()
        return msg.contains("http 413") ||
            msg.contains("context_length") ||
            msg.contains("maximum context") ||
            (msg.contains("token") && msg.contains("exceed")) ||
            msg.contains("too large") ||
            msg.contains("payload")
    }

    private fun condenseContextLines(rawContext: String, maxLines: Int): String {
        val lines = rawContext.lineSequence().take(maxLines + 6).toList()
        return lines.joinToString("\n")
    }

    private fun condenseStructuredXmlLines(rawXml: String, maxRecordLines: Int): String {
        val allLines = rawXml.lines()
        val recordLines = allLines.filter {
            val t = it.trimStart()
            t.startsWith("<sms ") || t.startsWith("<notification ")
        }
        if (recordLines.size <= maxRecordLines) return rawXml
        val keptSet = recordLines.takeLast(maxRecordLines).toHashSet()
        return buildString {
            for (line in allLines) {
                val t = line.trimStart()
                if ((t.startsWith("<sms ") || t.startsWith("<notification ")) && line !in keptSet) {
                    continue
                }
                append(line).append('\n')
            }
        }.trimEnd()
    }

    private fun extractErrorMessage(rawBody: String): String {
        return runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: rawBody.take(180)
        }.getOrDefault(rawBody.take(180))
    }

    companion object {
        fun isValidHttpUrl(rawUrl: String): Boolean {
            val clean = rawUrl.trim()
            if (!clean.startsWith("http://", ignoreCase = true) &&
                !clean.startsWith("https://", ignoreCase = true)
            ) {
                return false
            }
            val afterScheme = clean.substringAfter("://")
            return afterScheme.isNotBlank() && !afterScheme.contains(' ')
        }

        fun normalizeBaseUrl(rawUrl: String): String {
            return rawUrl.trim().trim('"', '\'', '`').trimEnd('/')
        }

        fun buildOpenAiChatUrl(baseUrl: String): String {
            val base = normalizeBaseUrl(baseUrl)
            if (base.isEmpty()) return ""
            return when {
                base.endsWith("/chat/completions", ignoreCase = true) -> base
                base.endsWith("/models", ignoreCase = true) -> {
                    base.dropLast("/models".length) + "/chat/completions"
                }
                hasEmptyPathAfterHost(base) -> "$base/v1/chat/completions"
                else -> "$base/chat/completions"
            }
        }

        fun buildOpenAiModelsUrl(baseUrl: String): String {
            val base = normalizeBaseUrl(baseUrl)
            if (base.isEmpty()) return ""
            return when {
                base.endsWith("/models", ignoreCase = true) -> base
                base.endsWith("/chat/completions", ignoreCase = true) -> {
                    base.dropLast("/chat/completions".length) + "/models"
                }
                hasEmptyPathAfterHost(base) -> "$base/v1/models"
                else -> "$base/models"
            }
        }

        fun buildOpenAiFilesUrl(baseUrl: String): String {
            val base = normalizeBaseUrl(baseUrl)
            if (base.isEmpty()) return ""
            return when {
                base.endsWith("/files", ignoreCase = true) -> base
                base.endsWith("/chat/completions", ignoreCase = true) -> {
                    base.dropLast("/chat/completions".length) + "/files"
                }
                base.endsWith("/models", ignoreCase = true) -> {
                    base.dropLast("/models".length) + "/files"
                }
                hasEmptyPathAfterHost(base) -> "$base/v1/files"
                else -> "$base/files"
            }
        }

        fun buildGeminiUploadUrl(baseUrl: String): String {
            val base = normalizeBaseUrl(baseUrl)
            return if (base.contains("generativelanguage.googleapis.com")) {
                "https://generativelanguage.googleapis.com/upload/v1beta/files"
            } else {
                "$base/upload/v1beta/files"
            }
        }

        private fun hasEmptyPathAfterHost(normalizedUrl: String): Boolean {
            val schemeIdx = normalizedUrl.indexOf("://")
            if (schemeIdx < 0) return false
            val afterScheme = normalizedUrl.substring(schemeIdx + 3)
            return !afterScheme.contains('/')
        }

        fun parseCredentialsText(rawText: String): Pair<String?, String?> {
            val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
            val urlMatch = urlRegex.find(rawText)?.value
                ?.trimEnd(',', ';', '.', ')', ']', '"', '\'')

            val withoutUrl = if (urlMatch != null) rawText.replace(urlMatch, " ") else rawText
            val ignoredLabels = setOf(
                "api_key", "apikey", "base_url", "baseurl", "endpoint", "bearer",
                "authorization", "token", "secret", "model", "https", "http"
            )
            val tokens = Regex("""[A-Za-z0-9._-]{16,}""")
                .findAll(withoutUrl)
                .map { it.value }
                .filter { token ->
                    val lower = token.lowercase()
                    lower !in ignoredLabels &&
                        !lower.startsWith("http") &&
                        token.any { it.isLetter() } &&
                        token.any { it.isDigit() || it.isUpperCase() || token.startsWith("sk-") }
                }
                .toList()

            val keyMatch = tokens.firstOrNull { it.startsWith("sk-") || it.startsWith("AIza") }
                ?: tokens.maxByOrNull { it.length }

            return urlMatch to keyMatch
        }
    }
}
