package com.example.data.ai

import android.util.Log
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiContextMode
import com.example.data.local.AiProviderType
import com.example.data.preferences.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiSearchClient(
    private val retrofitProvider: DynamicAiRetrofitProvider = DynamicAiRetrofitProvider()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun askWithSearchContext(
        settings: AppSettings,
        userQuestion: String,
        contextPayload: String,
        contextMode: AiContextMode,
        priorHistory: List<AiChatMessageEntity>,
        isPersian: Boolean
    ): Result<AiParsedSearchResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.isAiConfigured) {
                if (isPersian) "تنظیمات API هوش مصنوعی کامل نیست." else "AI API settings are incomplete."
            }
            require(isValidHttpUrl(settings.aiBaseUrl)) {
                if (isPersian) "آدرس Base URL باید با http:// یا https:// شروع شود." else "Base URL must start with http:// or https://"
            }

            val systemPrompt = buildStructuredXmlPrompt(isPersian, contextMode)
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

    private suspend fun executeAiRequest(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        userQuestion: String,
        contextPayload: String
    ): String {
        return when (settings.aiProviderType) {
            AiProviderType.GEMINI_REST -> callGeminiRest(
                settings = settings,
                systemPrompt = systemPrompt,
                priorHistory = priorHistory,
                userQuestion = userQuestion,
                contextPayload = contextPayload
            )
            AiProviderType.OPENAI_COMPATIBLE,
            AiProviderType.CUSTOM -> callOpenAiCompatible(
                settings = settings,
                systemPrompt = systemPrompt,
                priorHistory = priorHistory,
                userQuestion = userQuestion,
                contextPayload = contextPayload
            )
        }
    }

    suspend fun testConnection(settings: AppSettings, isPersian: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
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
                reply.take(120)
            }
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
            "Write <answer>, <summary>, <reason>, and <question> in clear, natural Persian (Farsi) and reference Jalali dates when helpful."
        } else {
            "Write <answer>, <summary>, <reason>, and <question> in clear, natural English."
        }
        val modeNote = when (contextMode) {
            AiContextMode.INLINE_XML -> "The user provided inline <archive> XML items with ids like sms:123 or notif:45."
            AiContextMode.COMPACT_TEXT,
            AiContextMode.KEYWORD_PREFILTER -> "The user provided a compact plain-text message/notification digest (no file upload) with ids like [sms:123] or [notif:45]."
        }
        return """
You are an assistant inside AI SMS Manager analyzing the user's SMS messages and device notifications.
$modeNote
$langInstruction
Respond ONLY with valid XML (no markdown code fences) using this schema:
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
        userQuestion: String,
        contextPayload: String
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

        val currentUserContent = buildString {
            append("User question:\n")
            append(userQuestion)
            append("\n\nArchive context:\n")
            append(contextPayload)
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", currentUserContent)
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
        userQuestion: String,
        contextPayload: String
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

        val currentPrompt = buildString {
            append("User question:\n")
            append(userQuestion)
            append("\n\nArchive context:\n")
            append(contextPayload)
        }
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", currentPrompt)))
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
            msg.contains("token") && msg.contains("exceed") ||
            msg.contains("too large") ||
            msg.contains("payload")
    }

    private fun condenseContextLines(rawContext: String, maxLines: Int): String {
        val lines = rawContext.lineSequence().take(maxLines + 6).toList()
        return lines.joinToString("\n")
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
