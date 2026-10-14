package com.bit.agent.presentation

import com.bit.agent.harness.event.ChipKind
import com.bit.agent.harness.event.SourceChip
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

@Serializable
data class SourceItem(
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String = ""
)

sealed interface ToolUiModel {
    data class WebSearchUi(
        val query: String,
        val status: String,
        val totalResults: Int,
        val results: List<SourceItem>,
        val errorMessage: String? = null,
        val rawJson: String = ""
    ) : ToolUiModel

    data class WebFetchUi(
        val url: String,
        val domain: String,
        val pageTitle: String,
        val charCount: Int,
        val contentSnippet: String,
        val rawJson: String = ""
    ) : ToolUiModel

    data class WorkspaceShellUi(
        val command: String,
        val exitCode: Int?,
        val output: String,
        val isSuccess: Boolean,
        val rawJson: String = ""
    ) : ToolUiModel

    data class FileOpUi(
        val operation: String, // "Read", "Wrote", "Edited", "Listed"
        val path: String,
        val lineCount: Int?,
        val summary: String,
        val rawJson: String = ""
    ) : ToolUiModel

    data class GenericUi(
        val toolName: String,
        val summary: String,
        val rawJson: String = ""
    ) : ToolUiModel
}

object ToolResultPresenter {

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Maps raw tool output + optional tool arguments into a typed, clean ToolUiModel.
     * Guarantees that raw JSON is never displayed by default.
     */
    fun present(toolName: String, resultString: String, toolArgs: Map<String, Any?>): ToolUiModel {
        val argsJson = if (toolArgs.isNotEmpty()) {
            try { JSONObject(toolArgs).toString() } catch (_: Exception) { null }
        } else null
        return present(toolName, resultString, argsJson)
    }

    /**
     * Maps raw tool output + optional tool arguments into a typed, clean ToolUiModel.
     * Guarantees that raw JSON is never displayed by default.
     */
    fun present(toolName: String, resultString: String, argumentsJson: String? = null): ToolUiModel {
        val t = toolName.lowercase().trim()
        val raw = resultString.trim()

        return try {
            when {
                t.contains("web_search") || t.contains("search_web") -> {
                    presentWebSearch(raw, argumentsJson)
                }
                t.contains("web_fetch") || t.contains("scrape_web") || t.contains("fetch_page") -> {
                    presentWebFetch(raw, argumentsJson)
                }
                t.contains("shell") || t.contains("exec") || t.contains("terminal") || t.contains("bash") -> {
                    presentWorkspaceShell(raw, argumentsJson)
                }
                t.contains("file") || t.contains("read") || t.contains("write") || t.contains("patch") || t.contains("edit") || t.contains("list_dir") -> {
                    presentFileOp(t, raw, argumentsJson)
                }
                else -> {
                    presentGeneric(toolName, raw)
                }
            }
        } catch (_: Exception) {
            presentGeneric(toolName, raw)
        }
    }

    private fun presentWebSearch(raw: String, argumentsJson: String?): ToolUiModel.WebSearchUi {
        val queryFromArgs = argumentsJson?.let {
            try {
                val obj = JSONObject(it)
                obj.optString("query").ifBlank { obj.optString("q") }
            } catch (_: Exception) { null }
        }

        if (raw.isBlank()) {
            return ToolUiModel.WebSearchUi(
                query = queryFromArgs ?: "web",
                status = "ERROR",
                totalResults = 0,
                results = emptyList(),
                errorMessage = "No search results returned.",
                rawJson = raw
            )
        }

        return try {
            val json = JSONObject(raw)
            val query = json.optString("query").ifBlank { queryFromArgs ?: "web" }
            val status = json.optString("status", "SUCCESS")
            val error = json.optString("error").takeIf { it.isNotBlank() }
            val totalResults = json.optInt("totalResults", 0)
            val resultsArr = json.optJSONArray("results")

            val sources = mutableListOf<SourceItem>()
            if (resultsArr != null) {
                for (i in 0 until resultsArr.length()) {
                    val item = resultsArr.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val url = item.optString("url").trim()
                    var domain = item.optString("domain").trim()
                    if (domain.isBlank() && url.isNotBlank()) {
                        domain = extractDomain(url)
                    }
                    val snippet = stripModelFacingArtifacts(item.optString("snippet").trim())
                    if (title.isNotBlank() || url.isNotBlank()) {
                        sources.add(
                            SourceItem(
                                title = if (title.isNotBlank()) title else domain.ifBlank { "Source" },
                                url = url,
                                domain = domain,
                                snippet = snippet
                            )
                        )
                    }
                }
            }

            ToolUiModel.WebSearchUi(
                query = query,
                status = status,
                totalResults = if (totalResults > 0) totalResults else sources.size,
                results = sources,
                errorMessage = error,
                rawJson = raw
            )
        } catch (_: Exception) {
            // Fallback: structured recovery without raw JSON dump
            ToolUiModel.WebSearchUi(
                query = queryFromArgs ?: "web",
                status = "SUCCESS",
                totalResults = 0,
                results = emptyList(),
                errorMessage = null,
                rawJson = raw
            )
        }
    }

    private fun presentWebFetch(raw: String, argumentsJson: String?): ToolUiModel.WebFetchUi {
        val urlFromArgs = argumentsJson?.let {
            try { JSONObject(it).optString("url") } catch (_: Exception) { null }
        }

        var url = urlFromArgs ?: ""
        var charCount = raw.length
        var snippet = ""

        try {
            val json = JSONObject(raw)
            if (json.has("url")) url = json.optString("url")
            if (json.has("totalChars")) charCount = json.optInt("totalChars", raw.length)
            snippet = stripModelFacingArtifacts(json.optString("text").take(300))
        } catch (_: Exception) {
            snippet = stripModelFacingArtifacts(raw.take(300))
        }

        val domain = extractDomain(url)
        val pageTitle = if (domain.isNotBlank()) domain else "Web Document"

        return ToolUiModel.WebFetchUi(
            url = url,
            domain = domain,
            pageTitle = pageTitle,
            charCount = charCount,
            contentSnippet = snippet,
            rawJson = raw
        )
    }

    private fun presentWorkspaceShell(raw: String, argumentsJson: String?): ToolUiModel.WorkspaceShellUi {
        val command = argumentsJson?.let {
            try {
                val obj = JSONObject(it)
                obj.optString("command").ifBlank { obj.optString("cmd") }
            } catch (_: Exception) { "" }
        }.orEmpty()

        var exitCode: Int? = null
        var output = raw

        try {
            val json = JSONObject(raw)
            if (json.has("exitCode")) exitCode = json.optInt("exitCode")
            if (json.has("exit_code")) exitCode = json.optInt("exit_code")
            val stdout = json.optString("stdout")
            val stderr = json.optString("stderr")
            val combined = (stdout + if (stderr.isNotBlank()) "\n$stderr" else "").trim()
            if (combined.isNotBlank()) output = combined
        } catch (_: Exception) {
            val exitCodeMatch = Regex("""exit_code:\s*(\d+)""").find(raw)
            if (exitCodeMatch != null) {
                exitCode = exitCodeMatch.groupValues[1].toIntOrNull()
            }
        }

        val cleanOutput = stripModelFacingArtifacts(output)
        return ToolUiModel.WorkspaceShellUi(
            command = command,
            exitCode = exitCode,
            output = cleanOutput,
            isSuccess = exitCode == null || exitCode == 0,
            rawJson = raw
        )
    }

    private fun presentFileOp(toolName: String, raw: String, argumentsJson: String?): ToolUiModel.FileOpUi {
        val path = argumentsJson?.let {
            try {
                val obj = JSONObject(it)
                obj.optString("path").ifBlank { obj.optString("file") }
            } catch (_: Exception) { "" }
        }.orEmpty()

        val op = when {
            toolName.contains("read") -> "Read"
            toolName.contains("write") -> "Wrote"
            toolName.contains("edit") || toolName.contains("patch") -> "Edited"
            toolName.contains("list") -> "Listed"
            else -> "Accessed"
        }

        var lineCount: Int? = null
        var summary = "$op ${path.substringAfterLast('/').ifBlank { path }}"

        try {
            val json = JSONObject(raw)
            if (json.has("summary")) {
                summary = stripModelFacingArtifacts(json.optString("summary"))
            }
            if (json.has("lines")) {
                lineCount = json.optInt("lines")
            }
        } catch (_: Exception) {
            val lineCountMatch = Regex("""(\d+)\s+lines""").find(raw)
            if (lineCountMatch != null) {
                lineCount = lineCountMatch.groupValues[1].toIntOrNull()
            }
        }

        return ToolUiModel.FileOpUi(
            operation = op,
            path = path,
            lineCount = lineCount,
            summary = summary,
            rawJson = raw
        )
    }

    private fun presentGeneric(toolName: String, raw: String): ToolUiModel.GenericUi {
        val summary = try {
            val json = JSONObject(raw)
            val s = json.optString("summary").ifBlank { json.optString("message") }
            if (s.isNotBlank()) stripModelFacingArtifacts(s) else "Execution finished"
        } catch (_: Exception) {
            stripModelFacingArtifacts(raw.lines().firstOrNull { it.isNotBlank() } ?: "Execution finished")
        }

        return ToolUiModel.GenericUi(
            toolName = toolName,
            summary = summary.take(120),
            rawJson = raw
        )
    }

    private fun extractDomain(url: String): String {
        if (url.isBlank()) return ""
        return try {
            val uri = URI(if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url)
            uri.host?.removePrefix("www.").orEmpty()
        } catch (_: Exception) {
            url.substringAfter("://").substringBefore('/').removePrefix("www.")
        }
    }

    /**
     * Strips model-facing tags, JSON wrappers, and protocol metadata from human-visible UI strings.
     */
    fun stripModelFacingArtifacts(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</?think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<antigravity>[\\s\\S]*?</antigravity>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</?antigravity>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<env>[\\s\\S]*?</env>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<project_instructions[\\s\\S]*?</project_instructions>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<project_file>[\\s\\S]*?</project_file>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[truncated for context budget\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(0 results found; try one reworded query[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(Note: already executed with identical arguments[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
