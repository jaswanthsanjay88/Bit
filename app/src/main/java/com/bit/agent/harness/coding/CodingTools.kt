package com.bit.agent.harness.coding

import android.content.Context
import android.util.Log
import com.bit.agent.harness.model.ToolObservation
import com.bit.agent.harness.tools.AgentTool
import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import com.bit.api.ToolProperty
import com.bit.workspace.WorkspacePaths
import org.json.JSONObject
import java.io.File

private const val TAG = "CodingTools"

/**
 * 1. Read File Slice Tool (`read_slice`).
 * Returns line-numbered code slices within an exact line range.
 */
class ReadSliceTool(
    private val context: Context? = null,
    private val workspaceRoot: File? = null
) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "read_slice",
            description = "Read a specific line range from a file. Output includes 1-indexed line numbers. Always read before editing.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(type = "string", description = "Relative path to target file in workspace"),
                    "start_line" to ToolProperty(type = "integer", description = "1-indexed starting line number"),
                    "end_line" to ToolProperty(type = "integer", description = "1-indexed ending line number (inclusive)")
                ),
                required = listOf("path", "start_line", "end_line")
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val path = args.getString("path").trim()
            val startLine = args.getInt("start_line").coerceAtLeast(1)
            val endLine = args.getInt("end_line")

            val file = resolveWorkspaceFile(context, path, workspaceRoot)
            if (!file.exists()) {
                return ToolObservation.error(
                    summary = "File not found: $path",
                    recoveryHint = "File does not exist. Call list_dir to inspect directory contents."
                )
            }

            val lines = file.readLines(Charsets.UTF_8)
            val totalLines = lines.size
            val effectiveEnd = if (endLine in startLine..totalLines) endLine else totalLines

            if (startLine > totalLines) {
                return ToolObservation.error(
                    summary = "start_line $startLine exceeds total file lines ($totalLines) in $path",
                    recoveryHint = "File has $totalLines lines. Call read_slice with start_line=1 and end_line=${totalLines.coerceAtMost(50)}."
                )
            }

            val sliced = lines.subList(startLine - 1, effectiveEnd)
            val numberedOutput = sliced.mapIndexed { idx, line ->
                val lineNum = startLine + idx
                "$lineNum: $line"
            }.joinToString("\n")

            FileReadTracker.recordRead(file)

            ToolObservation.success(
                summary = "Read lines $startLine..$effectiveEnd of ${file.name} ($totalLines total lines)",
                payload = numberedOutput,
                artifacts = listOf(file.absolutePath),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "read_slice error: ${e.message}", e)
            ToolObservation.error(
                summary = "read_slice failed: ${e.message}",
                recoveryHint = "Ensure valid JSON with integer start_line and end_line.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

/**
 * 2. Edit File Slice Tool (`edit_file_slice`).
 * Line-range anchored replacement with tolerant whitespace matching,
 * pre-flight shadow-git snapshots, and structured drift recovery feedback.
 */
class EditFileSliceTool(
    private val context: Context? = null,
    private val shadowGit: ShadowGitManager = ShadowGitManager,
    private val workspaceRoot: File? = null
) : AgentTool {

    override val requiresApproval: Boolean = false // Gated by Diff review card in UI

    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "edit_file_slice",
            description = "Replace a range of lines in an existing file. Prioritize reading the file with read_slice first to confirm exact line numbers.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(type = "string", description = "Relative path to target file"),
                    "start_line" to ToolProperty(type = "integer", description = "1-indexed start line to replace"),
                    "end_line" to ToolProperty(type = "integer", description = "1-indexed end line to replace (inclusive)"),
                    "new_content" to ToolProperty(type = "string", description = "New replacement code lines to insert"),
                    "expected_anchor" to ToolProperty(type = "string", description = "Short snippet (10-30 chars) expected on start_line for safety validation")
                ),
                required = listOf("path", "start_line", "end_line", "new_content")
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val path = args.getString("path").trim()
            val startLine = args.getInt("start_line")
            val endLine = args.getInt("end_line")
            val newContent = args.getString("new_content")
            val expectedAnchor = args.optString("expected_anchor", "").trim()

            val file = resolveWorkspaceFile(context, path, workspaceRoot)
            if (!file.exists()) {
                return ToolObservation.error(
                    summary = "File not found: $path",
                    recoveryHint = "Target file does not exist. Verify path using list_dir."
                )
            }

            if (!FileReadTracker.hasRead(file)) {
                return ToolObservation.error(
                    summary = "File '${file.name}' must be read before editing.",
                    recoveryHint = "Call read_slice on '$path' to inspect the latest content before making edits."
                )
            }
            if (FileReadTracker.hasChangedSinceRead(file)) {
                return ToolObservation.error(
                    summary = "File '${file.name}' was modified on disk since it was last read.",
                    recoveryHint = "Call read_slice on '$path' to inspect the updated content before making edits."
                )
            }

            val lines = file.readLines(Charsets.UTF_8).toMutableList()
            val totalLines = lines.size

            // 1. Boundary checking with machine-executable recovery hint
            if (startLine < 1 || startLine > totalLines || endLine < startLine || endLine > totalLines) {
                val previewEnd = if (totalLines > 0) totalLines.coerceAtMost(startLine + 2).coerceAtLeast(1) else 0
                val previewStart = if (totalLines > 0) (previewEnd - 4).coerceAtLeast(1) else 0
                val currentSlice = if (totalLines > 0 && previewStart <= previewEnd) {
                    lines.subList(previewStart - 1, previewEnd)
                        .mapIndexed { idx, l -> "${previewStart + idx}: $l" }
                        .joinToString("\n")
                } else {
                    "(empty file)"
                }

                return ToolObservation.error(
                    summary = "Line range $startLine..$endLine is out of bounds in $path ($totalLines lines total).",
                    recoveryHint = "Lines $previewStart..$previewEnd currently contain:\n$currentSlice\nCall edit_file_slice with the revised line range."
                )
            }

            // 2. Tolerant anchor verification (ignoring whitespace variations)
            if (expectedAnchor.isNotBlank()) {
                val actualLine = lines[startLine - 1]
                val normActual = actualLine.replace(Regex("\\s+"), " ")
                val normExpected = expectedAnchor.replace(Regex("\\s+"), " ")
                if (!normActual.contains(normExpected, ignoreCase = true)) {
                    val previewStart = (startLine - 2).coerceAtLeast(1)
                    val previewEnd = (startLine + 2).coerceAtMost(totalLines)
                    val currentSlice = lines.subList(previewStart - 1, previewEnd)
                        .mapIndexed { idx, l -> "${previewStart + idx}: $l" }
                        .joinToString("\n")

                    return ToolObservation.warning(
                        summary = "Anchor mismatch on line $startLine of $path",
                        payload = "Expected anchor '$expectedAnchor' but found '$actualLine'.\nCurrent lines $previewStart..$previewEnd:\n$currentSlice",
                        recoveryHint = "Review current lines above and call edit_file_slice with the updated start_line."
                    )
                }
            }

            // 3. Pre-flight Shadow Git Snapshot
            val root = if (file.isAbsolute) (file.parentFile ?: getWorkspaceRoot(context)) else getWorkspaceRoot(context)
            shadowGit.createSnapshot(root, "turn_${System.currentTimeMillis()}", "Before edit $path ($startLine..$endLine)")

            // 4. Perform line slice replacement
            val replacementLines = newContent.lines()
            val updated = mutableListOf<String>()
            updated.addAll(lines.subList(0, startLine - 1))
            updated.addAll(replacementLines)
            if (endLine < totalLines) {
                updated.addAll(lines.subList(endLine, totalLines))
            }

            file.writeText(updated.joinToString("\n"), Charsets.UTF_8)
            FileReadTracker.recordRead(file)

            ToolObservation.success(
                summary = "Updated lines $startLine..$endLine in ${file.name}",
                payload = "Replaced lines $startLine..$endLine with ${replacementLines.size} lines in $path.",
                artifacts = listOf(file.absolutePath),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "edit_file_slice error: ${e.message}", e)
            ToolObservation.error(
                summary = "Failed to edit file slice: ${e.message}",
                recoveryHint = "Check JSON format and valid line numbers.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

/**
 * 3. Search Code Tool (`search_code`).
 * Fast regex / keyword search with result count capping.
 */
class SearchCodeTool(private val context: Context?) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "search_code",
            description = "Search for a regex or keyword across project files. Returns matching files, line numbers, and snippets.",
            parameters = ToolParameters(
                properties = mapOf(
                    "query" to ToolProperty(type = "string", description = "Keyword or regex pattern to search"),
                    "extension" to ToolProperty(type = "string", description = "Optional file extension filter (e.g. 'kt', 'py', 'ts')"),
                    "max_results" to ToolProperty(type = "integer", description = "Maximum matching lines to return (default: 20)")
                ),
                required = listOf("query")
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val query = args.getString("query").trim()
            val ext = args.optString("extension", "").trim().removePrefix(".").lowercase()
            val maxResults = args.optInt("max_results", 20).coerceIn(1, 50)

            val root = getWorkspaceRoot(context)
            val matches = mutableListOf<String>()
            val regex = Regex(query, RegexOption.IGNORE_CASE)

            root.walkTopDown()
                .filter { f ->
                    f.isFile &&
                    (ext.isBlank() || f.extension.lowercase() == ext) &&
                    !f.path.contains(".git") &&
                    !f.path.contains("node_modules") &&
                    !f.path.contains("/build/") &&
                    !f.path.contains("/.gradle/")
                }
                .forEach { file ->
                    if (matches.size >= maxResults) return@forEach
                    val relPath = file.relativeTo(root).path.replace('\\', '/')
                    file.useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            if (matches.size >= maxResults) return@useLines
                            if (regex.containsMatchIn(line)) {
                                matches.add("$relPath:${idx + 1}: ${line.trim().take(120)}")
                            }
                        }
                    }
                }

            if (matches.isEmpty()) {
                ToolObservation.warning(
                    summary = "No matches found for '$query'",
                    payload = "0 matches found across files. Check query spelling or file extension filter.",
                    recoveryHint = "Try searching with a broader keyword or inspect filenames with list_dir."
                )
            } else {
                ToolObservation.success(
                    summary = "Found ${matches.size} matches for '$query'",
                    payload = matches.joinToString("\n"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "search_code error: ${e.message}", e)
            ToolObservation.error(
                summary = "search_code failed: ${e.message}",
                recoveryHint = "Verify query pattern and parameters.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

/**
 * 4. List Directory Tool (`list_dir`).
 * Filtered directory tree listing bounded by depth.
 */
class ListDirectoryTool(private val context: Context?) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "list_dir",
            description = "List files and subdirectories with depth bounding and .gitignore filtering.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(type = "string", description = "Relative directory path (defaults to root)"),
                    "max_depth" to ToolProperty(type = "integer", description = "Maximum directory traversal depth (default: 2, max: 4)")
                ),
                required = emptyList()
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(if (argumentsJson.isBlank()) "{}" else argumentsJson)
            val relPath = args.optString("path", "").trim()
            val maxDepth = args.optInt("max_depth", 2).coerceIn(1, 4)

            val root = getWorkspaceRoot(context)
            val targetDir = if (relPath.isBlank()) root else File(root, relPath)

            if (!targetDir.exists() || !targetDir.isDirectory) {
                return ToolObservation.error(
                    summary = "Directory not found: $relPath",
                    recoveryHint = "Directory does not exist. Call list_dir with path='' to see root directories."
                )
            }

            val items = mutableListOf<String>()
            targetDir.walkTopDown().maxDepth(maxDepth).filter { f ->
                val p = f.relativeTo(root).path.replace('\\', '/')
                !p.startsWith(".git") &&
                !p.contains("node_modules") &&
                !p.contains("/build/") &&
                !p.contains("/.gradle/") &&
                !p.startsWith(".bit")
            }.take(60).forEach { file ->
                val rel = file.relativeTo(targetDir).path.replace('\\', '/')
                if (rel.isNotBlank()) {
                    val prefix = if (file.isDirectory) "[DIR] " else "      "
                    val lineCount = if (file.isFile) " (${file.readLines().size} lines)" else ""
                    items.add("$prefix$rel$lineCount")
                }
            }

            ToolObservation.success(
                summary = "Found ${items.size} entries in ${relPath.ifBlank { "root" }}",
                payload = if (items.isEmpty()) "Directory is empty." else items.joinToString("\n"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "list_dir error: ${e.message}", e)
            ToolObservation.error(
                summary = "list_dir failed: ${e.message}",
                recoveryHint = "Check valid directory path.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

/**
 * 5. Git Diff Tool (`git_diff`).
 * Read-only inspection of uncommitted modifications.
 */
class GitDiffTool(
    private val context: Context?,
    private val shadowGit: ShadowGitManager = ShadowGitManager
) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "git_diff",
            description = "View uncommitted changes and file diffs compared to the last turn snapshot.",
            parameters = ToolParameters(
                properties = emptyMap(),
                required = emptyList()
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val root = getWorkspaceRoot(context)
        val diffText = shadowGit.getDiff(root)
        return ToolObservation.success(
            summary = "Inspected workspace diff",
            payload = diffText.take(5000)
        )
    }
}

/**
 * 6. Outline Symbols Tool (`outline_symbols`).
 * Extracts classes, functions, and interfaces per file.
 */
class OutlineSymbolsTool(private val context: Context?) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "outline_symbols",
            description = "Extract structural symbols (classes, functions, interfaces, structs) from a code file.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(type = "string", description = "Relative path to target code file")
                ),
                required = listOf("path")
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val args = JSONObject(argumentsJson)
        val path = args.getString("path").trim()
        val file = resolveWorkspaceFile(context, path)
        if (!file.exists()) {
            return ToolObservation.error("File not found: $path", "Verify file exists using list_dir.")
        }

        val symbolRegex = Regex("""^\s*(?:(?:suspend|override|public|private|protected|internal|final|abstract|open)\s+)*(?:fun|class|interface|object|enum\s+class|def|struct|fn)\s+([A-Za-z0-9_]+)""")
        val outlines = mutableListOf<String>()

        file.useLines { lines ->
            lines.forEachIndexed { idx, line ->
                val match = symbolRegex.find(line)
                if (match != null) {
                    outlines.add("line ${idx + 1}: ${line.trim()}")
                }
            }
        }

        return ToolObservation.success(
            summary = "Found ${outlines.size} symbols in ${file.name}",
            payload = if (outlines.isEmpty()) "No top-level symbols detected." else outlines.joinToString("\n")
        )
    }
}

// --- Internal Helper Resolvers ---

private fun getWorkspaceRoot(context: Context?, customRoot: File? = null): File {
    if (customRoot != null) return customRoot.canonicalFile
    return if (context != null) {
        WorkspacePaths.root(context)
    } else {
        File("workspace").canonicalFile
    }
}

private fun resolveWorkspaceFile(context: Context?, path: String, customRoot: File? = null): File {
    if (customRoot != null) {
        val clean = path.trim().replace('\\', '/')
        val f = File(clean)
        val canonicalCustom = customRoot.canonicalFile
        if (f.isAbsolute && f.canonicalPath.startsWith(canonicalCustom.canonicalPath)) {
            return f.canonicalFile
        }
        val target = File(canonicalCustom, clean.removePrefix("workspace/").removePrefix("/workspace/").trimStart('/')).canonicalFile
        if (!target.canonicalPath.startsWith(canonicalCustom.canonicalPath)) {
            throw SecurityException("Access denied: path traverses outside workspace root ('$path')")
        }
        return target
    }
    return WorkspacePaths.resolveFile(context, path)
}

/**
 * File Read Tracker.
 * Enforces the "file must be read before edit, and edit fails if the file changed since" invariant.
 */
object FileReadTracker {
    private val readFiles = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun recordRead(file: File) {
        val canonical = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        readFiles[canonical] = file.lastModified()
    }

    fun hasRead(file: File): Boolean {
        val canonical = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        return readFiles.containsKey(canonical)
    }

    fun hasChangedSinceRead(file: File): Boolean {
        val canonical = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        val lastReadTime = readFiles[canonical] ?: return true
        return file.lastModified() > lastReadTime
    }

    fun clear() {
        readFiles.clear()
    }
}

/**
 * 7. Grep Tool (`grep`).
 * First-class regex / substring pattern search across workspace files with line numbers.
 */
class GrepTool(
    private val context: Context? = null,
    private val workspaceRoot: File? = null
) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "grep",
            description = "Search for a pattern (regex or substring) across files in the workspace. Returns matching paths, line numbers, and content.",
            parameters = ToolParameters(
                properties = mapOf(
                    "pattern" to ToolProperty(type = "string", description = "Regex or text pattern to search for"),
                    "path" to ToolProperty(type = "string", description = "Subdirectory or file path to search within (relative to workspace). Defaults to root."),
                    "extension" to ToolProperty(type = "string", description = "Optional file extension filter (e.g. 'kt', 'py', 'js')"),
                    "max_results" to ToolProperty(type = "integer", description = "Maximum matching lines to return (default: 30, max: 100)")
                ),
                required = listOf("pattern")
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val pattern = args.getString("pattern").trim()
            val subPath = args.optString("path", "").trim()
            val ext = args.optString("extension", "").trim().removePrefix(".").lowercase()
            val maxResults = args.optInt("max_results", 30).coerceIn(1, 100)

            val root = getWorkspaceRoot(context, workspaceRoot)
            val searchBase = if (subPath.isNotBlank()) resolveWorkspaceFile(context, subPath, workspaceRoot) else root
            if (!searchBase.exists()) {
                return ToolObservation.error(
                    summary = "Path not found: $subPath",
                    recoveryHint = "Provide a valid existing directory or file path."
                )
            }

            val regex = try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
            }

            val matches = mutableListOf<String>()
            val filesToSearch = if (searchBase.isFile) {
                listOf(searchBase)
            } else {
                searchBase.walkTopDown()
                    .filter { f ->
                        f.isFile &&
                        (ext.isBlank() || f.extension.lowercase() == ext) &&
                        !f.path.contains(".git") &&
                        !f.path.contains(".bit") &&
                        !f.path.contains("node_modules") &&
                        !f.path.contains("/build/") &&
                        !f.path.contains("/.gradle/")
                    }.toList()
            }

            for (file in filesToSearch) {
                if (matches.size >= maxResults) break
                val relPath = file.relativeTo(root).path.replace('\\', '/')
                try {
                    file.useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            if (matches.size >= maxResults) return@useLines
                            if (regex.containsMatchIn(line)) {
                                matches.add("$relPath:${idx + 1}: ${line.trim().take(160)}")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (matches.isEmpty()) {
                ToolObservation.warning(
                    summary = "No matches found for pattern '$pattern'",
                    payload = "0 matches found across files. Try adjusting pattern or file extension filter.",
                    recoveryHint = "Try a broader substring or use list_dir / glob to check files."
                )
            } else {
                ToolObservation.success(
                    summary = "Found ${matches.size} matches for '$pattern'",
                    payload = matches.joinToString("\n"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "grep error: ${e.message}", e)
            ToolObservation.error(
                summary = "grep failed: ${e.message}",
                recoveryHint = "Ensure valid JSON with 'pattern' parameter.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

// 8. Glob Tool (`glob`).
// Fast file discovery using glob pattern matching.
class GlobTool(
    private val context: Context? = null,
    private val workspaceRoot: File? = null
) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "glob",
            description = "Find files in the workspace matching a glob pattern (e.g., '**/*.py', 'src/**/*.kt', '*.json').",
            parameters = ToolParameters(
                properties = mapOf(
                    "pattern" to ToolProperty(type = "string", description = "Glob pattern to match against"),
                    "path" to ToolProperty(type = "string", description = "Base directory to search from (relative to workspace). Defaults to root.")
                ),
                required = listOf("pattern")
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val pattern = args.getString("pattern").trim()
            val subPath = args.optString("path", "").trim()

            val root = getWorkspaceRoot(context, workspaceRoot)
            val searchBase = if (subPath.isNotBlank()) resolveWorkspaceFile(context, subPath, workspaceRoot) else root
            if (!searchBase.exists() || !searchBase.isDirectory) {
                return ToolObservation.error(
                    summary = "Directory not found: $subPath",
                    recoveryHint = "Provide a valid base directory for glob."
                )
            }

            val regex = globToRegex(pattern)
            val matchedFiles = mutableListOf<String>()

            searchBase.walkTopDown()
                .filter { f ->
                    f.isFile &&
                    !f.path.contains(".git") &&
                    !f.path.contains(".bit") &&
                    !f.path.contains("node_modules") &&
                    !f.path.contains("/build/") &&
                    !f.path.contains("/.gradle/")
                }
                .take(100)
                .forEach { file ->
                    val relPath = file.relativeTo(root).path.replace('\\', '/')
                    if (regex.matches(relPath) || regex.matches(file.name)) {
                        matchedFiles.add(relPath)
                    }
                }

            if (matchedFiles.isEmpty()) {
                ToolObservation.warning(
                    summary = "No files matched glob pattern '$pattern'",
                    payload = "0 files matched '$pattern'.",
                    recoveryHint = "Try a broader pattern or check list_dir."
                )
            } else {
                ToolObservation.success(
                    summary = "Found ${matchedFiles.size} files matching '$pattern'",
                    payload = matchedFiles.joinToString("\n"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "glob error: ${e.message}", e)
            ToolObservation.error(
                summary = "glob failed: ${e.message}",
                recoveryHint = "Ensure valid JSON with 'pattern' parameter.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        sb.append(".*")
                        i++
                        if (i + 1 < glob.length && glob[i + 1] == '/') {
                            i++
                        }
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.' -> sb.append("\\.")
                '\\' -> sb.append("\\\\")
                '+' -> sb.append("\\+")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '[' -> sb.append("\\[")
                ']' -> sb.append("\\]")
                '{' -> sb.append("(?:")
                '}' -> sb.append(")")
                ',' -> sb.append("|")
                else -> sb.append(c)
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}

