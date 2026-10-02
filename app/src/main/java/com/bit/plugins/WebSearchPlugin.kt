package com.bit.plugins
 
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.bit.models.plugins.PluginInfo
import com.bit.plugins.api.SuperPlugin
import com.bit.network.DuckDuckGoScraper
import com.bit.network.HttpClient
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

import android.content.Context
import com.bit.agent.harness.model.ResearchEvent
import com.bit.agent.harness.model.ResearchEventSink
import kotlinx.coroutines.flow.first
import org.json.JSONArray

class WebSearchPlugin(private val context: Context) : SuperPlugin {

    companion object {
        private const val TAG = "WebSearchPlugin"
        const val TOOL_WEB_SEARCH = "web_search"
        const val TOOL_WEB_FETCH = "web_fetch"
        const val TOOL_SEARCH_WEB_ALIAS = "search_web"
        const val TOOL_SCRAPE_WEB_ALIAS = "scrape_web"
        const val TOOL_FETCH_PAGE_ALIAS = "fetch_page"

        private const val WEB_FETCH_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val MAX_WEB_FETCH_HTML_LENGTH = 600_000
    }

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            name = "Web Search",
            description = "Search the web and fetch full page contents for LLM grounding",
            author = "BIT",
            version = "3.0.0",
            toolDefinitionBuilder = listOf(
                ToolDefinitionBuilder(
                    TOOL_WEB_SEARCH,
                    "Search the web for current information. Use this to find facts, news, or data not in your training set."
                )
                    .stringParam("query", "The search query to execute", required = true)
                    .numberParam("num_results", "Number of results to return (1-10, default 5)", required = false),
                ToolDefinitionBuilder(
                    TOOL_WEB_FETCH,
                    "Fetch and read the full text content of a web page. Use this after web_search when you need more detail from a specific page."
                )
                    .stringParam("url", "The URL of the page to fetch", required = true)
                    .numberParam("maxChars", "Maximum characters of text to return (default 8000, max 100000)", required = false),
                ToolDefinitionBuilder(
                    TOOL_SEARCH_WEB_ALIAS,
                    "Search the web for real-time information, articles, and documentation."
                )
                    .stringParam("query", "Search keywords or question", required = true),
                ToolDefinitionBuilder(
                    TOOL_SCRAPE_WEB_ALIAS,
                    "Scrape and extract clean text content from a web URL."
                )
                    .stringParam("url", "The URL to scrape", required = true),
                ToolDefinitionBuilder(
                    TOOL_FETCH_PAGE_ALIAS,
                    "Fetches a URL and extracts readable article text as clean markdown."
                )
                    .stringParam("url", "The URL of the page to fetch", required = true)
                    .numberParam("maxChars", "Maximum characters of text to return (default 8000)", required = false)
            )
        )
    }

    override fun serializeResult(data: Any): String = when (data) {
        is WebSearchResponse -> data.toJSON().toString()
        is WebFetchResponse -> data.toJSON().toString()
        is JSONObject -> data.toString()
        else -> data.toString()
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> {
        return executeToolWithSink(toolCall, ResearchEventSink.NoOp)
    }

    override suspend fun executeToolWithSink(
        toolCall: ToolCall,
        eventSink: ResearchEventSink
    ): Result<Any> {
        return try {
            when (toolCall.name) {
                TOOL_WEB_SEARCH, TOOL_SEARCH_WEB_ALIAS -> executeSearch(toolCall, eventSink)
                TOOL_WEB_FETCH, TOOL_SCRAPE_WEB_ALIAS, TOOL_FETCH_PAGE_ALIAS -> executeFetch(toolCall, eventSink)
                else -> Result.failure(IllegalArgumentException("Unknown tool: ${toolCall.name}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeSearch(
        toolCall: ToolCall,
        eventSink: ResearchEventSink = ResearchEventSink.NoOp
    ): Result<Any> = withContext(Dispatchers.IO) {
        val rawQueries = mutableListOf<String>()
        val queriesArray = toolCall.arguments.optJSONArray("queries")
        if (queriesArray != null && queriesArray.length() > 0) {
            for (i in 0 until queriesArray.length()) {
                val q = queriesArray.optString(i).trim()
                if (q.isNotBlank()) rawQueries.add(q)
            }
        }
        val singleQuery = toolCall.getString("query").trim()
        if (singleQuery.isNotBlank() && !rawQueries.contains(singleQuery)) {
            rawQueries.add(0, singleQuery)
        }
        if (rawQueries.isEmpty()) {
            rawQueries.add("information")
        }

        val queries = rawQueries.take(3)
        val primaryQuery = queries.first()
        val numResults = toolCall.getInt("num_results", 5).coerceIn(1, 10)
        val topFetchCount = toolCall.getInt("top_fetch_count", 3).coerceIn(1, 5)
        val startTime = System.currentTimeMillis()
        val searchPhaseId = "phase_search_${System.currentTimeMillis()}"

        Log.d(TAG, "Search: queries=$queries (numResults=$numResults, topFetch=$topFetchCount)")

        eventSink.emit(ResearchEvent.PhaseStarted(searchPhaseId, "Searching the web"))
        for (q in queries) {
            eventSink.emit(ResearchEvent.Query(searchPhaseId, q))
        }

        try {
            val settings = com.bit.data.AppSettingsDataStore(context)
            val provider = settings.webSearchProvider.first()
            val apiKey = settings.webSearchApiKey.first()
            val baseUrl = settings.webSearchBaseUrl.first()

            val semaphore = Semaphore(3)
            val allResults = coroutineScope {
                queries.map { q ->
                    async {
                        semaphore.withPermit {
                            executeSingleQuery(
                                query = q,
                                numResults = numResults,
                                provider = provider,
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                eventSink = eventSink,
                                phaseId = searchPhaseId
                            )
                        }
                    }
                }.awaitAll().flatten()
            }

            val deduplicated = allResults.distinctBy { it.url }
            val finalResults = autoScrapeTopResults(
                rawResults = deduplicated,
                limit = topFetchCount,
                eventSink = eventSink,
                searchPhaseId = searchPhaseId
            )

            val duration = System.currentTimeMillis() - startTime
            val response = WebSearchResponse(
                query = primaryQuery,
                results = finalResults,
                totalResults = finalResults.size,
                searchTimeMs = duration,
                status = if (finalResults.isEmpty()) "ERROR" else "SUCCESS",
                error = if (finalResults.isEmpty()) "No results found across search engines" else null,
                provider = provider
            )
            Result.success(response.copy(summary = response.generateSummary()))
        } catch (e: Exception) {
            Log.e(TAG, "Search execution error: ${e.message}", e)
            val duration = System.currentTimeMillis() - startTime
            Result.success(
                WebSearchResponse(
                    query = primaryQuery,
                    results = emptyList(),
                    totalResults = 0,
                    searchTimeMs = duration,
                    status = "ERROR",
                    error = e.message ?: "Unknown search error",
                    provider = "unknown"
                ).let { it.copy(summary = it.generateSummary()) }
            )
        } finally {
            val duration = System.currentTimeMillis() - startTime
            eventSink.emit(ResearchEvent.Finished(duration))
        }
    }

    private suspend fun executeSingleQuery(
        query: String,
        numResults: Int,
        provider: String,
        apiKey: String,
        baseUrl: String,
        eventSink: ResearchEventSink,
        phaseId: String
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val resultsList = mutableListOf<WebSearchResult>()

        if (provider == "duckduckgo") {
            val scraper = DuckDuckGoScraper()
            val r = scraper.search(query, numResults)
            if (r is DuckDuckGoScraper.SearchResponse.Success && r.results.isNotEmpty()) {
                r.results.forEachIndexed { index, webResult ->
                    val domain = extractDomain(webResult.url)
                    val isHttps = webResult.url.startsWith("https://", ignoreCase = true)
                    eventSink.emit(ResearchEvent.Source(phaseId, webResult.title, domain, webResult.url, isHttps))
                    resultsList.add(
                        WebSearchResult(
                            title = webResult.title,
                            url = webResult.url,
                            snippet = webResult.snippet,
                            content = "",
                            domain = domain,
                            scraped = false,
                            index = index
                        )
                    )
                }
                return@withContext resultsList
            }

            Log.i(TAG, "DDG returned 0 results or error for '$query'. Activating Bing fallback.")
            val bingClient = com.bit.network.BingSearchFallbackClient()
            val bingRes = bingClient.search(query, numResults)
            if (bingRes.isSuccess && bingRes.getOrNull()?.isNotEmpty() == true) {
                bingRes.getOrNull().orEmpty().forEachIndexed { index, bResult ->
                    val domain = extractDomain(bResult.url)
                    val isHttps = bResult.url.startsWith("https://", ignoreCase = true)
                    eventSink.emit(ResearchEvent.Source(phaseId, bResult.title, domain, bResult.url, isHttps))
                    resultsList.add(
                        WebSearchResult(
                            title = bResult.title,
                            url = bResult.url,
                            snippet = bResult.snippet,
                            content = "",
                            domain = domain,
                            scraped = false,
                            index = index
                        )
                    )
                }
                return@withContext resultsList
            }
            return@withContext resultsList
        }

        if (provider != "searxng" && apiKey.isBlank()) {
            return@withContext resultsList
        }

        val body = when (provider) {
            "serper" -> HttpClient.post(
                "https://google.serper.dev/search",
                JSONObject().apply {
                    put("q", query)
                    put("num", numResults)
                }.toString(),
                mapOf("X-API-KEY" to apiKey)
            )
            "tavily" -> HttpClient.post(
                "https://api.tavily.com/search",
                JSONObject().apply {
                    put("api_key", apiKey)
                    put("query", query)
                    put("max_results", numResults)
                    put("search_depth", "advanced")
                    put("include_answer", true)
                }.toString(),
                emptyMap()
            )
            "searxng" -> {
                val resolvedBase = baseUrl.ifBlank { "https://searx.be" }
                HttpClient.fetchModels(
                    "$resolvedBase/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&engines=google,brave"
                )
            }
            else -> HttpClient.fetchModels( // brave
                "https://api.search.brave.com/res/v1/web/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&count=$numResults",
                mapOf("Accept" to "application/json", "X-Subscription-Token" to apiKey)
            )
        }

        if (body.isNullOrBlank()) return@withContext resultsList

        try {
            val json = JSONObject(body)
            when (provider) {
                "tavily" -> {
                    val resultsArray = json.optJSONArray("results")
                    if (resultsArray != null) {
                        for (i in 0 until resultsArray.length()) {
                            val obj = resultsArray.getJSONObject(i)
                            val u = obj.optString("url")
                            val title = obj.optString("title")
                            val domain = extractDomain(u)
                            val isHttps = u.startsWith("https://", ignoreCase = true)
                            eventSink.emit(ResearchEvent.Source(phaseId, title, domain, u, isHttps))
                            resultsList.add(
                                WebSearchResult(
                                    title = title,
                                    url = u,
                                    snippet = obj.optString("content"),
                                    content = "",
                                    domain = domain,
                                    index = i
                                )
                            )
                        }
                    }
                }
                "serper" -> {
                    val organic = json.optJSONArray("organic")
                    if (organic != null) {
                        for (i in 0 until organic.length()) {
                            val obj = organic.getJSONObject(i)
                            val u = obj.optString("link")
                            val title = obj.optString("title")
                            val domain = extractDomain(u)
                            val isHttps = u.startsWith("https://", ignoreCase = true)
                            eventSink.emit(ResearchEvent.Source(phaseId, title, domain, u, isHttps))
                            resultsList.add(
                                WebSearchResult(
                                    title = title,
                                    url = u,
                                    snippet = obj.optString("snippet"),
                                    content = "",
                                    domain = domain,
                                    index = i
                                )
                            )
                        }
                    }
                }
                "searxng" -> {
                    val resultsArray = json.optJSONArray("results")
                    if (resultsArray != null) {
                        for (i in 0 until resultsArray.length()) {
                            val obj = resultsArray.getJSONObject(i)
                            val u = obj.optString("url")
                            val title = obj.optString("title")
                            val domain = extractDomain(u)
                            val isHttps = u.startsWith("https://", ignoreCase = true)
                            eventSink.emit(ResearchEvent.Source(phaseId, title, domain, u, isHttps))
                            resultsList.add(
                                WebSearchResult(
                                    title = title,
                                    url = u,
                                    snippet = obj.optString("content"),
                                    content = "",
                                    domain = domain,
                                    index = i
                                )
                            )
                        }
                    }
                }
                else -> { // brave
                    val web = json.optJSONObject("web")
                    val resultsArray = web?.optJSONArray("results")
                    if (resultsArray != null) {
                        for (i in 0 until resultsArray.length()) {
                            val obj = resultsArray.getJSONObject(i)
                            val u = obj.optString("url")
                            val title = obj.optString("title")
                            val domain = extractDomain(u)
                            val isHttps = u.startsWith("https://", ignoreCase = true)
                            eventSink.emit(ResearchEvent.Source(phaseId, title, domain, u, isHttps))
                            resultsList.add(
                                WebSearchResult(
                                    title = title,
                                    url = u,
                                    snippet = obj.optString("description"),
                                    content = "",
                                    domain = domain,
                                    index = i
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing provider response for '$query': ${e.message}")
        }

        resultsList
    }

    private suspend fun autoScrapeTopResults(
        rawResults: List<WebSearchResult>,
        limit: Int = 3,
        eventSink: ResearchEventSink = ResearchEventSink.NoOp,
        searchPhaseId: String? = null
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        if (rawResults.isEmpty() || limit <= 0) return@withContext rawResults
        val list = rawResults.toMutableList()
        val toScrape = list.take(limit)
        val phaseId = searchPhaseId ?: "phase_search_${System.currentTimeMillis()}"

        for (item in toScrape) {
            val url = item.url
            if (url.isBlank() || url.startsWith("javascript:")) continue
            eventSink.emit(ResearchEvent.Fetching(phaseId, url))
            try {
                val html = HttpClient.fetchModels(
                    url,
                    mapOf(
                        "User-Agent" to WEB_FETCH_USER_AGENT,
                        "Accept" to "text/html,application/xhtml+xml,*/*"
                    )
                )
                if (!html.isNullOrBlank()) {
                    val readable = htmlToReadableText(html).take(4000)
                    if (readable.isNotBlank()) {
                        val idx = list.indexOf(item)
                        if (idx != -1) {
                            list[idx] = item.copy(
                                content = readable,
                                scraped = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed auto-scraping top result for $url: ${e.message}")
            } finally {
                eventSink.emit(ResearchEvent.FetchDone(phaseId, url))
            }
        }
        list
    }

    private suspend fun executeFetch(
        toolCall: ToolCall,
        eventSink: ResearchEventSink = ResearchEventSink.NoOp
    ): Result<Any> = withContext(Dispatchers.IO) {
        val url = toolCall.getString("url").trim()
        val maxChars = toolCall.getInt("maxChars", 8000).coerceIn(1, 100_000)
        val startTime = System.currentTimeMillis()
        val readingPhaseId = "phase_read_${System.currentTimeMillis()}"
        val domain = extractDomain(url)
        val isValidHttpUrl = url.isNotBlank() && (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))

        if (isValidHttpUrl) {
            eventSink.emit(ResearchEvent.PhaseStarted(readingPhaseId, "Reading source: $domain"))
            eventSink.emit(
                ResearchEvent.Source(
                    phaseId = readingPhaseId,
                    title = domain.ifBlank { url.take(30) },
                    domain = domain,
                    url = url,
                    isHttps = url.startsWith("https://", ignoreCase = true)
                )
            )
            eventSink.emit(ResearchEvent.Fetching(readingPhaseId, url))
        }

        try {
            if (!isValidHttpUrl) {
                return@withContext Result.success(
                    WebFetchResponse(
                        url = url,
                        text = "Invalid or empty URL provided for web_fetch: '$url'",
                        truncated = false,
                        totalChars = 0,
                        error = null
                    )
                )
            }

            Log.d(TAG, "Fetch URL: '$url' (maxChars $maxChars)")
            val html = HttpClient.fetchModels(
                url,
                mapOf(
                    "User-Agent" to WEB_FETCH_USER_AGENT,
                    "Accept" to "text/html,application/xhtml+xml,*/*"
                )
            )
            if (html == null) {
                return@withContext Result.success(
                    WebFetchResponse(
                        url = url,
                        text = "Unable to fetch content from $url (server returned no response or access restricted).",
                        truncated = false,
                        totalChars = 0,
                        error = null
                    )
                )
            }

            val fullText = htmlToReadableText(html)
            val text = fullText.take(maxChars)

            Result.success(
                WebFetchResponse(
                    url = url,
                    text = text,
                    truncated = fullText.length > text.length,
                    totalChars = fullText.length
                )
            )
        } catch (e: Exception) {
            Result.success(
                WebFetchResponse(
                    url = url,
                    text = "Error fetching $url: ${e.message}",
                    truncated = false,
                    totalChars = 0,
                    error = null
                )
            )
        } finally {
            if (isValidHttpUrl) {
                eventSink.emit(ResearchEvent.FetchDone(readingPhaseId, url))
            }
            eventSink.emit(ResearchEvent.Finished(System.currentTimeMillis() - startTime))
        }
    }

    private fun extractDomain(url: String): String {
        return try { java.net.URL(url).host?.removePrefix("www.") ?: url } catch (_: Exception) { url }
    }

    private fun htmlToReadableText(rawHtml: String): String {
        val stripped = rawHtml
            .take(MAX_WEB_FETCH_HTML_LENGTH)
            .replace(Regex("<!--[\\s\\S]*?-->"), " ")
            .replace(
                Regex("<(script|style|noscript|svg|head)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE),
                " "
            )
            .replace(
                Regex("<(nav|header|footer|aside)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE),
                " "
            )
        val text = HtmlCompat.fromHtml(stripped, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        return text
            .replace(Regex("[ \\t\\x0B\\u000C\\r]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    // ── UI ──

    @Composable
    override fun ToolCallUI() { }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        val type = data.optString("type", "")
        if (type == "web_fetch" || data.has("text") || data.has("totalChars")) {
            WebFetchResultUI(data)
        } else if (data.has("query") && data.has("results")) {
            SearchResultsUI(data)
        } else {
            Text(
                text = data.toString(2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }

    @Composable
    private fun WebFetchResultUI(data: JSONObject) {
        val url = data.optString("url", "")
        val text = data.optString("text", "")
        val truncated = data.optBoolean("truncated", false)
        val totalChars = data.optInt("totalChars", 0)
        val error = data.optString("error", "")

        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Fetched: $url",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (error.isNotEmpty()) {
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "Retrieved ${text.length} chars of $totalChars total" + if (truncated) " (truncated)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = text.take(500) + if (text.length > 500) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp),
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun SearchResultsUI(data: JSONObject) {
        val query = data.optString("query", "")
        val resultsArray = data.optJSONArray("results")
        val totalResults = data.optInt("totalResults", 0)
        val searchTimeMs = data.optLong("searchTimeMs", 0)

        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Search: \"$query\"",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$totalResults results · ${searchTimeMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (resultsArray != null && resultsArray.length() > 0) {
                for (i in 0 until resultsArray.length()) {
                    val r = resultsArray.getJSONObject(i)
                    SearchResultCard(
                        title = r.optString("title", ""),
                        snippet = r.optString("snippet", ""),
                        url = r.optString("url", ""),
                        position = i + 1
                    )
                    if (i < resultsArray.length() - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchResultCard(title: String, snippet: String, url: String, position: Int) {
        val expandedState = remember { mutableStateOf(false) }
        val scrapedTextState = remember { mutableStateOf<String?>(null) }
        val isLoadingScrapeState = remember { mutableStateOf(false) }
        val scrapeErrorState = remember { mutableStateOf<String?>(null) }

        LaunchedEffect(expandedState.value) {
            if (expandedState.value && scrapedTextState.value == null && !isLoadingScrapeState.value) {
                isLoadingScrapeState.value = true
                scrapeErrorState.value = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        val html = HttpClient.fetchModels(url, mapOf(
                            "User-Agent" to WEB_FETCH_USER_AGENT,
                            "Accept" to "text/html,application/xhtml+xml,*/*"
                        ))
                        if (html != null) {
                            htmlToReadableText(html)
                        } else {
                            null
                        }
                    }
                    if (result != null) {
                        scrapedTextState.value = result
                    } else {
                        scrapeErrorState.value = "Could not load page content."
                    }
                } catch (e: Exception) {
                    scrapeErrorState.value = e.message ?: "Failed to scrape page."
                } finally {
                    isLoadingScrapeState.value = false
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedState.value = !expandedState.value },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = if (expandedState.value) 5 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "#$position",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (snippet.isNotBlank() && !expandedState.value) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = expandedState.value,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        
                        if (snippet.isNotBlank()) {
                            Text(
                                text = "Snippet:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Scraped Content:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (isLoadingScrapeState.value) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Scraping web page content…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (scrapeErrorState.value != null) {
                            Text(
                                text = scrapeErrorState.value ?: "Failed to scrape page",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (scrapedTextState.value != null) {
                            val cleanScraped = scrapedTextState.value ?: ""
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = cleanScraped.take(8000) + if (cleanScraped.length > 8000) "\n\n[Content Truncated...]" else "",
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
