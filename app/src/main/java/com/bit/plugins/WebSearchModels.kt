package com.bit.plugins

import org.json.JSONArray
import org.json.JSONObject

/** Flat search result — one per URL. */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val content: String,
    val domain: String = "",
    val scraped: Boolean = false,
    val index: Int = -1
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("title", title)
        put("url", url)
        put("snippet", snippet)
        put("content", content.take(4000))
        put("scraped", scraped)
        put("domain", domain)
        put("index", index)
    }
}

/** Flat search response — one per query. */
data class WebSearchResponse(
    val query: String,
    val results: List<WebSearchResult>,
    val totalResults: Int,
    val searchTimeMs: Long,
    val scrapingTimeMs: Long = 0,
    val status: String = "SUCCESS",
    val error: String? = null,
    val provider: String = "ddg_html",
    val summary: String = ""
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("query", query)
        put("status", status)
        put("totalResults", totalResults)
        put("searchTimeMs", searchTimeMs)
        put("scrapingTimeMs", scrapingTimeMs)
        put("provider", provider)
        put("summary", summary)
        val arr = JSONArray()
        results.forEach { arr.put(it.toJSON()) }
        put("results", arr)
        error?.let { put("error", it) }
    }

    fun generateSummary(): String {
        if (results.isEmpty()) return "No results found for query: $query"
        return buildString {
            appendLine("Search Results for \"$query\": Found $totalResults sources.")
            results.forEachIndexed { idx, r ->
                val num = idx + 1
                appendLine("[$num] ${r.title} (${r.url})")
                val s = r.snippet.trim().replace("\n", " ")
                if (s.isNotBlank()) appendLine("    Snippet: $s")
                if (r.scraped && r.content.isNotBlank()) {
                    appendLine("    Excerpt: ${r.content.take(800).trim()}")
                }
            }
        }.trim()
    }
}

/** Flat fetch response — one per URL. */
data class WebFetchResponse(
    val url: String,
    val text: String,
    val truncated: Boolean,
    val totalChars: Int,
    val error: String? = null
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("type", "web_fetch")
        put("url", url)
        if (error != null) {
            put("error", error)
        } else {
            put("text", text)
            put("truncated", truncated)
            put("totalChars", totalChars)
        }
    }
}

