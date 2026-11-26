package com.example.data.ai

import android.util.Log
import com.example.data.local.AiChatMessageEntity
import com.example.data.local.AiProviderType
import com.example.data.preferences.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiChatResponse(
    val answer: String,
    val matchedRefs: List<String>
)

class AiSearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun askWithXmlArchive(
        settings: AppSettings,
        userQuestion: String,
        xmlArchive: String,
        priorHistory: List<AiChatMessageEntity>,
        isPersian: Boolean
    ): Result<AiChatResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.isAiConfigured) {
                if (isPersian) "تنظیمات API هوش مصنوعی کامل نیست." else "AI API settings are incomplete."
            }

            val systemPrompt = buildSystemPrompt(isPersian)
            val rawResponseText = when (settings.aiProviderType) {
                AiProviderType.GEMINI_REST -> callGeminiRest(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    priorHistory = priorHistory,
                    userQuestion = userQuestion,
                    xmlArchive = xmlArchive
                )
                AiProviderType.OPENAI_COMPATIBLE,
                AiProviderType.CUSTOM -> callOpenAiCompatible(
                    settings = settings,
                    systemPrompt = systemPrompt,
                    priorHistory = priorHistory,
                    userQuestion = userQuestion,
                    xmlArchive = xmlArchive
                )
            }

            parseStructuredOrFallback(rawResponseText)
        }.onFailure { err ->
            Log.e("AiSearchClient", "AI request failed: ${err.message}")
        }
    }

    suspend fun testConnection(settings: AppSettings, isPersian: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(settings.isAiConfigured) {
                    if (isPersian) "لطفاً آدرس API، مدل و کلید را وارد کنید." else "Please enter Base URL, Model ID, and API Key."
                }
                val reply = when (settings.aiProviderType) {
                    AiProviderType.GEMINI_REST -> callGeminiRest(
                        settings = settings,
                        systemPrompt = "Reply with OK.",
                        priorHistory = emptyList(),
                        userQuestion = "Connection check. Reply with OK.",
                        xmlArchive = "<archive />"
                    )
                    AiProviderType.OPENAI_COMPATIBLE,
                    AiProviderType.CUSTOM -> callOpenAiCompatible(
                        settings = settings,
                        systemPrompt = "Reply with OK.",
                        priorHistory = emptyList(),
                        userQuestion = "Connection check. Reply with OK.",
                        xmlArchive = "<archive />"
                    )
                }
                reply.take(120)
            }
        }

    private fun buildSystemPrompt(isPersian: Boolean): String {
        val langInstruction = if (isPersian) {
            "Answer in clear, natural Persian (Farsi) and reference Jalali dates (dateJalali) when helpful."
        } else {
            "Answer in clear, natural English and reference dates cleanly."
        }
        return """
You are an assistant inside AI SMS Manager analyzing the user's XML archive of SMS messages and device notifications.
$langInstruction
Each item in the <archive> XML has a unique id attribute like "sms:123" or "notif:45".
You MUST respond with valid JSON ONLY (no markdown fences) using this exact schema:
{
  "answer": "Helpful, accurate answer to the user's question based strictly on the XML archive.",
  "matchedIds": ["sms:123", "notif:45"]
}
Only include IDs in matchedIds that actually exist in the provided XML archive and directly relate to the answer. If nothing matches, return an empty array [] and explain clearly in answer.
        """.trimIndent()
    }

    private fun callOpenAiCompatible(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        userQuestion: String,
        xmlArchive: String
    ): String {
        val base = settings.aiBaseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // Include recent conversation turns (without duplicating huge old XMLs)
        for (msg in priorHistory.takeLast(8)) {
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
            append("\n\nAttached XML archive file:\n")
            append(xmlArchive)
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", currentUserContent)
        })

        val payload = JSONObject().apply {
            put("model", settings.aiModelName)
            put("messages", messagesArray)
            put("temperature", 0.2)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.aiApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${extractErrorMessage(rawBody)}")
            }
            val json = JSONObject(rawBody)
            val choices = json.optJSONArray("choices")
                ?: throw IllegalStateException("Missing choices in API response")
            if (choices.length() == 0) throw IllegalStateException("Empty choices from AI API")
            val message = choices.getJSONObject(0).getJSONObject("message")
            return message.optString("content", "").trim()
        }
    }

    private fun callGeminiRest(
        settings: AppSettings,
        systemPrompt: String,
        priorHistory: List<AiChatMessageEntity>,
        userQuestion: String,
        xmlArchive: String
    ): String {
        val base = settings.aiBaseUrl.trim().trimEnd('/')
        val model = settings.aiModelName.trim().removePrefix("models/")
        val url = if (base.contains(":generateContent")) {
            base
        } else {
            "$base/models/$model:generateContent"
        }

        val contents = JSONArray()
        for (msg in priorHistory.takeLast(8)) {
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
            append("\n\nAttached XML archive file:\n")
            append(xmlArchive)
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
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", settings.aiApiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${extractErrorMessage(rawBody)}")
            }
            val json = JSONObject(rawBody)
            val candidates = json.optJSONArray("candidates")
                ?: throw IllegalStateException("Missing candidates in Gemini response")
            if (candidates.length() == 0) throw IllegalStateException("Empty candidates from Gemini API")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).optString("text", "").trim()
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        return runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: rawBody.take(180)
        }.getOrDefault(rawBody.take(180))
    }

    private fun parseStructuredOrFallback(raw: String): AiChatResponse {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val parsedJson = runCatching {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JSONObject(cleaned.substring(start, end + 1))
            } else {
                null
            }
        }.getOrNull()

        if (parsedJson != null && parsedJson.has("answer")) {
            val answer = parsedJson.optString("answer", cleaned)
            val arr = parsedJson.optJSONArray("matchedIds")
            val ids = mutableListOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.startsWith("sms:") || id.startsWith("notif:")) {
                        ids.add(id)
                    }
                }
            }
            return AiChatResponse(answer = answer, matchedRefs = ids)
        }

        // Fallback: extract any sms:123 or notif:45 references mentioned in plain text
        val regex = Regex("""\b(?:sms|notif):\d+\b""")
        val refs = regex.findAll(raw).map { it.value }.distinct().toList()
        return AiChatResponse(answer = raw, matchedRefs = refs)
    }
}
