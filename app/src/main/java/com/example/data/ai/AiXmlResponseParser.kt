package com.example.data.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

@Serializable
data class AiSearchMatch(
    val refId: String,
    val source: String = "",
    val sender: String = "",
    val snippet: String = "",
    val relevanceReason: String = "",
    val dateText: String = ""
)

@Serializable
data class AiParsedSearchResponse(
    val answer: String,
    val summary: String = "",
    val matchedRefs: List<String> = emptyList(),
    val matches: List<AiSearchMatch> = emptyList(),
    val suggestedFollowUps: List<String> = emptyList(),
    val responseFormat: String = "xml"
)

/**
 * Parses structured XML responses from the AI search API into typed Kotlin objects,
 * with safe fallbacks for JSON or plain-text replies from OpenAI-compatible models.
 */
object AiXmlResponseParser {

    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val refRegex = Regex("""\b(?:sms|notif):\d+\b""")
    private val strayAmpRegex = Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9a-fA-F]+;)""")

    fun parse(rawResponse: String): AiParsedSearchResponse {
        val cleaned = stripMarkdownFences(rawResponse)
        if (cleaned.isEmpty()) {
            return AiParsedSearchResponse(answer = "", responseFormat = "text")
        }

        // 1. Primary path: Structured XML response (<ai_search_response> ... </ai_search_response>)
        extractXmlBlock(cleaned)?.let { xmlBlock ->
            parseXmlBlock(xmlBlock)?.let { return it }
        }

        // 2. Secondary path: Structured JSON fallback
        parseJsonBlock(cleaned)?.let { return it }

        // 3. Final fallback: Plain text with inline sms:/notif: ref extraction
        val refs = refRegex.findAll(cleaned).map { it.value }.distinct().toList()
        return AiParsedSearchResponse(
            answer = cleaned,
            matchedRefs = refs,
            responseFormat = "text"
        )
    }

    fun encodeToJson(response: AiParsedSearchResponse): String =
        runCatching { jsonCodec.encodeToString(response) }.getOrDefault("")

    fun decodeFromJson(rawJson: String): AiParsedSearchResponse? {
        if (rawJson.isBlank()) return null
        return runCatching { jsonCodec.decodeFromString<AiParsedSearchResponse>(rawJson) }.getOrNull()
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        val fenceRegex = Regex("""^```(?:xml|json|XML|JSON)?\s*([\s\S]*?)\s*```$""")
        val match = fenceRegex.find(trimmed)
        return (match?.groupValues?.getOrNull(1) ?: trimmed).trim()
    }

    private fun extractXmlBlock(text: String): String? {
        val tags = listOf("ai_search_response", "search_response", "response")
        for (tag in tags) {
            val startTag = "<$tag"
            val endTag = "</$tag>"
            val startIdx = text.indexOf(startTag, ignoreCase = true)
            val endIdx = text.lastIndexOf(endTag, ignoreCase = true)
            if (startIdx >= 0 && endIdx > startIdx) {
                return text.substring(startIdx, endIdx + endTag.length)
            }
        }
        return null
    }

    private fun parseXmlBlock(xmlText: String): AiParsedSearchResponse? {
        return runCatching {
            val sanitizedXml = xmlText.replace(strayAmpRegex, "&amp;")
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                isExpandEntityReferences = false
                isCoalescing = true
            }
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(InputSource(StringReader(sanitizedXml)))
            val root = document.documentElement ?: return@runCatching null

            val answer = childText(root, "answer")
                .ifBlank { childText(root, "message") }
                .ifBlank { root.textContent?.trim().orEmpty() }
            val summary = childText(root, "summary")

            val matches = mutableListOf<AiSearchMatch>()
            val refSet = linkedSetOf<String>()

            val matchNodes = root.getElementsByTagName("match")
            for (i in 0 until matchNodes.length) {
                val el = matchNodes.item(i) as? Element ?: continue
                val refId = el.getAttribute("id")
                    .ifBlank { childText(el, "id") }
                    .trim()
                if (!isValidRefId(refId)) continue
                refSet.add(refId)

                val source = el.getAttribute("source")
                    .ifBlank { childText(el, "source") }
                    .ifBlank { if (refId.startsWith("notif:")) "notification" else "sms" }
                    .trim()
                val sender = el.getAttribute("sender")
                    .ifBlank { childText(el, "sender") }
                    .trim()
                val dateText = el.getAttribute("date")
                    .ifBlank { childText(el, "date") }
                    .trim()
                val snippet = childText(el, "snippet")
                    .ifBlank { childText(el, "excerpt") }
                    .trim()
                val reason = childText(el, "reason")
                    .ifBlank { childText(el, "relevance") }
                    .trim()

                matches.add(
                    AiSearchMatch(
                        refId = refId,
                        source = source,
                        sender = sender,
                        snippet = snippet,
                        relevanceReason = reason,
                        dateText = dateText
                    )
                )
            }

            // Also collect any standalone <id> or <matched_id> tags
            val idTagNames = listOf("id", "matched_id", "ref")
            for (tag in idTagNames) {
                val nodes = root.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val candidate = nodes.item(i)?.textContent?.trim().orEmpty()
                    if (isValidRefId(candidate)) refSet.add(candidate)
                }
            }

            // Fallback: pick up any refs mentioned inside <answer>
            for (m in refRegex.findAll(answer)) {
                refSet.add(m.value)
            }

            val followUps = mutableListOf<String>()
            val qNodes = root.getElementsByTagName("question")
            for (i in 0 until qNodes.length) {
                val q = qNodes.item(i)?.textContent?.trim().orEmpty()
                if (q.isNotEmpty() && q !in followUps) followUps.add(q)
            }

            AiParsedSearchResponse(
                answer = answer,
                summary = summary,
                matchedRefs = refSet.toList(),
                matches = matches,
                suggestedFollowUps = followUps.take(3),
                responseFormat = "xml"
            )
        }.getOrNull()
    }

    private fun parseJsonBlock(text: String): AiParsedSearchResponse? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return runCatching {
            val obj = JSONObject(text.substring(start, end + 1))
            if (!obj.has("answer")) return@runCatching null

            val answer = obj.optString("answer", text).trim()
            val summary = obj.optString("summary", "").trim()
            val refSet = linkedSetOf<String>()
            val matches = mutableListOf<AiSearchMatch>()

            val matchedIdsArr = obj.optJSONArray("matchedIds")
            if (matchedIdsArr != null) {
                for (i in 0 until matchedIdsArr.length()) {
                    val id = matchedIdsArr.optString(i).trim()
                    if (isValidRefId(id)) refSet.add(id)
                }
            }

            val matchesArr = obj.optJSONArray("matches")
            if (matchesArr != null) {
                for (i in 0 until matchesArr.length()) {
                    val item = matchesArr.optJSONObject(i) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("refId") }.trim()
                    if (!isValidRefId(id)) continue
                    refSet.add(id)
                    matches.add(
                        AiSearchMatch(
                            refId = id,
                            source = item.optString("source").trim(),
                            sender = item.optString("sender").trim(),
                            snippet = item.optString("snippet").trim(),
                            relevanceReason = item.optString("reason").trim(),
                            dateText = item.optString("date").trim()
                        )
                    )
                }
            }

            val followUps = mutableListOf<String>()
            val fArr = obj.optJSONArray("followUps") ?: obj.optJSONArray("follow_ups")
            if (fArr != null) {
                for (i in 0 until fArr.length()) {
                    val q = fArr.optString(i).trim()
                    if (q.isNotEmpty()) followUps.add(q)
                }
            }

            AiParsedSearchResponse(
                answer = answer,
                summary = summary,
                matchedRefs = refSet.toList(),
                matches = matches,
                suggestedFollowUps = followUps.take(3),
                responseFormat = "json"
            )
        }.getOrNull()
    }

    private fun childText(parent: Element, tagName: String): String {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.tagName.equals(tagName, ignoreCase = true)) {
                return node.textContent?.trim().orEmpty()
            }
        }
        return ""
    }

    private fun isValidRefId(refId: String): Boolean {
        if (!refId.startsWith("sms:") && !refId.startsWith("notif:")) return false
        val idPart = refId.substringAfter(':')
        return idPart.toLongOrNull() != null
    }
}
