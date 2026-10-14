package com.bit.util

import android.content.Context
import android.net.Uri
import com.bit.models.Skill
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Utility for importing and exporting Claude Agent Skills.
 * Supports SKILL.md format (YAML frontmatter + markdown body) and native JSON format.
 */
object SkillExportImport {

    sealed class ImportResult {
        data class Success(val skill: Skill, val format: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Export a skill to standard SKILL.md format (YAML frontmatter + markdown body).
     */
    fun exportToSkillMd(skill: Skill): String {
        return buildString {
            appendLine("---")
            appendLine("name: ${skill.name}")
            appendLine("description: ${yamlEscape(skill.description)}")
            if (!skill.icon.isNullOrBlank()) {
                appendLine("icon: ${skill.icon}")
            }
            appendLine("---")
            appendLine()
            append(skill.instructions)
        }
    }

    /**
     * Export a skill to native JSON format.
     */
    fun exportToJson(skill: Skill): String {
        val json = JSONObject().apply {
            put("version", 2)
            put("format", "bit_skill")
            put("skill", JSONObject().apply {
                put("id", skill.id)
                put("name", skill.name)
                put("description", skill.description)
                put("icon", skill.icon ?: "")
                put("instructions", skill.instructions)
                put("skillType", skill.skillType.name)
                put("requiresWorkspace", skill.requiresWorkspace)
                put("requiredPermissions", org.json.JSONArray(skill.requiredPermissions))
                put("tools", org.json.JSONArray(skill.tools))
                if (skill.scriptPath != null) {
                    put("scriptPath", skill.scriptPath)
                }
                put("enabled", skill.enabled)
                put("isBuiltIn", skill.isBuiltIn)
                put("createdAt", skill.createdAt)
            })
        }
        return json.toString(2)
    }

    /**
     * Import a skill from string. Auto-detects format (SKILL.md or JSON).
     */
    fun importFromString(content: String): ImportResult {
        val trimmed = content.trim()

        // 1. Try JSON
        if (trimmed.startsWith("{")) {
            val jsonRes = tryImportJson(trimmed)
            if (jsonRes is ImportResult.Success) return jsonRes
        }

        // 2. Try SKILL.md (YAML frontmatter ---)
        if (trimmed.startsWith("---")) {
            val mdRes = tryImportSkillMd(trimmed)
            if (mdRes is ImportResult.Success) return mdRes
        }

        // Fallback attempts
        val fallbackJson = tryImportJson(trimmed)
        if (fallbackJson is ImportResult.Success) return fallbackJson

        val fallbackMd = tryImportSkillMd(trimmed)
        if (fallbackMd is ImportResult.Success) return fallbackMd

        // If neither, treat entire text as raw instructions
        return ImportResult.Success(
            Skill(
                name = "Imported Skill",
                description = "Custom imported prompt skill",
                instructions = trimmed,
                enabled = true,
                isBuiltIn = false
            ),
            format = "raw_text"
        )
    }

    /**
     * Import a skill from a content Uri (supports SKILL.md, JSON, and .skill / .zip packages).
     */
    fun importFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return ImportResult.Error("Could not open file")

            val buffered = java.io.BufferedInputStream(inputStream)
            buffered.mark(8)
            val header = ByteArray(4)
            val bytesRead = buffered.read(header)
            buffered.reset()

            val isZip = bytesRead >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()

            if (isZip) {
                val skillsDir = com.bit.skills.SkillManager.getInstance(context).getSkillsDir()
                importFromZip(buffered, skillsDir)
            } else {
                val content = BufferedReader(InputStreamReader(buffered, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
                importFromString(content)
            }
        } catch (e: Exception) {
            ImportResult.Error("Failed to read file: ${e.message}")
        }
    }

    /**
     * Import a skill package (.skill or .zip archive).
     * Extracts files into targetSkillsDir while preventing Zip Slip vulnerabilities,
     * parses SKILL.md, and returns the configured Skill model.
     */
    fun importFromZip(inputStream: java.io.InputStream, targetSkillsDir: java.io.File): ImportResult {
        return try {
            if (!targetSkillsDir.exists()) {
                targetSkillsDir.mkdirs()
            }
            var skillMdContent: String? = null
            var detectedSkillName: String? = null
            val extractedFiles = mutableListOf<java.io.File>()

            val zis = java.util.zip.ZipInputStream(inputStream)
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                // Guard against Zip Slip vulnerability
                val targetFile = java.io.File(targetSkillsDir, name)
                val canonicalTarget = targetFile.canonicalPath
                val canonicalRoot = targetSkillsDir.canonicalPath
                if (!canonicalTarget.startsWith(canonicalRoot + java.io.File.separator) && canonicalTarget != canonicalRoot) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    val bytes = zis.readBytes()
                    targetFile.writeBytes(bytes)
                    extractedFiles.add(targetFile)

                    if (name.equals("SKILL.md", ignoreCase = true) || name.endsWith("/SKILL.md", ignoreCase = true)) {
                        skillMdContent = String(bytes, StandardCharsets.UTF_8)
                        val folder = name.substringBeforeLast("/", "")
                        if (folder.isNotBlank()) {
                            detectedSkillName = folder.substringAfterLast("/")
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }

            if (skillMdContent != null) {
                val parsed = importFromString(skillMdContent)
                if (parsed is ImportResult.Success) {
                    val baseSkill = parsed.skill
                    val effectiveName = if ((baseSkill.name == "Imported Skill" || baseSkill.name.isBlank()) && !detectedSkillName.isNullOrBlank()) {
                        detectedSkillName
                    } else {
                        baseSkill.name
                    }
                    val isWorkspaceRelated = effectiveName.contains("workspace", ignoreCase = true) ||
                            effectiveName.contains("shell", ignoreCase = true) ||
                            baseSkill.description.contains("workspace_shell", ignoreCase = true) ||
                            baseSkill.instructions.contains("workspace_shell", ignoreCase = true)

                    val updatedSkill = baseSkill.copy(
                        name = effectiveName,
                        requiresWorkspace = baseSkill.requiresWorkspace || isWorkspaceRelated,
                        tools = if (isWorkspaceRelated && !baseSkill.tools.contains("workspace_shell")) {
                            baseSkill.tools + "workspace_shell"
                        } else {
                            baseSkill.tools
                        },
                        skillType = if (isWorkspaceRelated) com.bit.models.SkillType.EXECUTABLE else baseSkill.skillType,
                        icon = if (isWorkspaceRelated && (baseSkill.icon == null || baseSkill.icon == "sparkles")) {
                            "terminal"
                        } else {
                            baseSkill.icon
                        }
                    )
                    ImportResult.Success(updatedSkill, format = "skill_package")
                } else {
                    parsed
                }
            } else {
                val mdFile = extractedFiles.firstOrNull { it.name.endsWith(".md", ignoreCase = true) }
                if (mdFile != null) {
                    val parsed = importFromString(mdFile.readText())
                    if (parsed is ImportResult.Success) {
                        ImportResult.Success(parsed.skill, format = "skill_package")
                    } else {
                        parsed
                    }
                } else {
                    ImportResult.Error("Archive does not contain a valid SKILL.md file")
                }
            }
        } catch (e: Exception) {
            ImportResult.Error("Failed to extract skill archive: ${e.message}")
        }
    }

    private fun tryImportJson(content: String): ImportResult {
        return try {
            val json = JSONObject(content)
            val skillObj = json.optJSONObject("skill") ?: json
            val name = skillObj.optString("name", "Imported Skill")
            val desc = skillObj.optString("description", "")
            val icon = skillObj.optString("icon").ifEmpty { null }
            val instructions = skillObj.optString("instructions", "")
            val typeStr = skillObj.optString("skillType", "INSTRUCTIONAL")
            val skillType = try {
                com.bit.models.SkillType.valueOf(typeStr)
            } catch (_: Exception) {
                com.bit.models.SkillType.INSTRUCTIONAL
            }
            val requiresWorkspace = skillObj.optBoolean("requiresWorkspace", false)
            val scriptPath = if (skillObj.has("scriptPath") && !skillObj.isNull("scriptPath")) skillObj.optString("scriptPath") else null
            val permsJson = skillObj.optJSONArray("requiredPermissions")
            val requiredPermissions = mutableListOf<String>()
            if (permsJson != null) {
                for (p in 0 until permsJson.length()) {
                    val perm = permsJson.optString(p)
                    if (perm.isNotBlank()) requiredPermissions.add(perm)
                }
            }

            val toolsJson = skillObj.optJSONArray("tools")
            val tools = mutableListOf<String>()
            if (toolsJson != null) {
                for (t in 0 until toolsJson.length()) {
                    val item = toolsJson.opt(t)
                    when (item) {
                        is String -> if (item.isNotBlank()) tools.add(item.trim())
                        is JSONObject -> {
                            val toolName = item.optString("name", "").trim()
                            if (toolName.isNotBlank()) tools.add(toolName)
                        }
                    }
                }
            }

            val effectiveSkillType = if (tools.isNotEmpty() && skillType == com.bit.models.SkillType.INSTRUCTIONAL) {
                com.bit.models.SkillType.TOOL
            } else {
                skillType
            }

            ImportResult.Success(
                Skill(
                    name = name,
                    description = desc,
                    icon = icon,
                    instructions = instructions,
                    skillType = effectiveSkillType,
                    requiresWorkspace = requiresWorkspace,
                    requiredPermissions = requiredPermissions,
                    tools = tools,
                    scriptPath = scriptPath,
                    enabled = true,
                    isBuiltIn = false
                ),
                format = "json"
            )
        } catch (e: Exception) {
            ImportResult.Error("Invalid JSON: ${e.message}")
        }
    }

    private fun tryImportSkillMd(content: String): ImportResult {
        val trimmed = content.trim()
        if (!trimmed.startsWith("---")) {
            return ImportResult.Error("Missing frontmatter delimiter (---)")
        }

        val closingIndex = trimmed.indexOf("---", startIndex = 3)
        if (closingIndex < 0) {
            return ImportResult.Error("Unclosed frontmatter (missing second ---)")
        }

        val frontmatter = trimmed.substring(3, closingIndex).trim()
        val body = trimmed.substring(closingIndex + 3).trim()

        val yamlMap = mutableMapOf<String, String>()
        for (line in frontmatter.lines()) {
            val colonIdx = line.indexOf(":")
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                yamlMap[key] = value
            }
        }

        val name = yamlMap["name"] ?: "Imported Skill"
        val desc = yamlMap["description"] ?: ""
        val toolsRaw = yamlMap["tools"]
        val toolsList = mutableListOf<String>()
        if (!toolsRaw.isNullOrBlank()) {
            toolsRaw.removeSurrounding("[", "]").split(",").forEach {
                val t = it.trim().removeSurrounding("\"").removeSurrounding("'")
                if (t.isNotBlank()) toolsList.add(t)
            }
        }

        val requiresWs = yamlMap["requires_workspace"]?.equals("true", ignoreCase = true) == true ||
                yamlMap["requiresworkspace"]?.equals("true", ignoreCase = true) == true ||
                name.contains("workspace-shell", ignoreCase = true) ||
                desc.contains("workspace_shell", ignoreCase = true) ||
                body.contains("workspace_shell", ignoreCase = true)

        if (requiresWs && !toolsList.contains("workspace_shell")) {
            toolsList.add("workspace_shell")
        }

        val typeStr = yamlMap["type"]?.uppercase() ?: if (requiresWs) "EXECUTABLE" else "INSTRUCTIONAL"
        val skillType = try {
            com.bit.models.SkillType.valueOf(typeStr)
        } catch (_: Exception) {
            if (requiresWs) com.bit.models.SkillType.EXECUTABLE else com.bit.models.SkillType.INSTRUCTIONAL
        }
        val scriptPath = yamlMap["script"] ?: yamlMap["script_path"]
        val icon = yamlMap["icon"] ?: if (requiresWs) "terminal" else null

        return ImportResult.Success(
            Skill(
                name = name,
                description = desc,
                icon = icon,
                instructions = body,
                skillType = skillType,
                requiresWorkspace = requiresWs,
                tools = toolsList,
                scriptPath = scriptPath,
                enabled = true,
                isBuiltIn = false
            ),
            format = "skill_md"
        )
    }

    /**
     * Import a skill from an online URL (GitHub repository URL, raw GitHub URL, or direct SKILL.md/JSON link).
     */
    suspend fun importFromUrl(url: String): ImportResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return@withContext ImportResult.Error("URL cannot be empty")

        // 1. Check if it's a GitHub URL
        val githubInfo = parseGitHubUrl(trimmedUrl)
        if (githubInfo != null) {
            return@withContext importFromGitHub(githubInfo)
        }

        // 2. Direct HTTP download
        try {
            val content = downloadText(trimmedUrl) ?: return@withContext ImportResult.Error("Could not fetch content from URL")
            importFromString(content)
        } catch (e: Exception) {
            ImportResult.Error("Failed to download skill: ${e.message}")
        }
    }

    private data class GitHubRepoInfo(
        val owner: String,
        val repo: String,
        val branch: String,
        val path: String
    )

    private fun parseGitHubUrl(url: String): GitHubRepoInfo? {
        val trimmed = url.trim().trimEnd('/')
        // https://github.com/owner/repo
        // https://github.com/owner/repo/tree/branch
        // https://github.com/owner/repo/tree/branch/sub/path
        val regex = Regex("""https://github\.com/([^/]+)/([^/]+)(?:/tree/([^/]+)(/.*)?)?""")
        val match = regex.matchEntire(trimmed) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val branch = match.groupValues[3].ifBlank { "main" }
        val subPath = match.groupValues[4].trimStart('/')
        return GitHubRepoInfo(owner, repo, branch, subPath)
    }

    private fun importFromGitHub(info: GitHubRepoInfo): ImportResult {
        // Try raw SKILL.md download first
        val rawPaths = listOf(
            if (info.path.isNotBlank()) "https://raw.githubusercontent.com/${info.owner}/${info.repo}/${info.branch}/${info.path}/SKILL.md" else null,
            if (info.path.isNotBlank()) "https://raw.githubusercontent.com/${info.owner}/${info.repo}/${info.branch}/${info.path}" else null,
            "https://raw.githubusercontent.com/${info.owner}/${info.repo}/${info.branch}/SKILL.md",
            "https://raw.githubusercontent.com/${info.owner}/${info.repo}/master/SKILL.md"
        ).filterNotNull()

        for (rawUrl in rawPaths) {
            val text = downloadText(rawUrl)
            if (!text.isNullOrBlank()) {
                val res = importFromString(text)
                if (res is ImportResult.Success) return res
            }
        }

        // Fallback: Use GitHub Contents API
        val apiUrl = if (info.path.isNotBlank()) {
            "https://api.github.com/repos/${info.owner}/${info.repo}/contents/${info.path}?ref=${info.branch}"
        } else {
            "https://api.github.com/repos/${info.owner}/${info.repo}/contents?ref=${info.branch}"
        }

        val jsonText = downloadText(apiUrl) ?: return ImportResult.Error("Could not fetch directory contents from GitHub repository")
        return try {
            val array = org.json.JSONArray(jsonText)
            var skillDownloadUrl: String? = null
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("name")
                if (name.equals("SKILL.md", ignoreCase = true) || name.endsWith(".md", ignoreCase = true)) {
                    skillDownloadUrl = item.optString("download_url")
                    if (name.equals("SKILL.md", ignoreCase = true)) break
                }
            }

            if (skillDownloadUrl != null) {
                val skillText = downloadText(skillDownloadUrl) ?: return ImportResult.Error("Could not download SKILL.md")
                importFromString(skillText)
            } else {
                ImportResult.Error("No SKILL.md found in ${info.owner}/${info.repo}/${info.path}")
            }
        } catch (e: Exception) {
            ImportResult.Error("Failed to parse GitHub repository response: ${e.message}")
        }
    }

    private fun downloadText(urlStr: String): String? {
        return try {
            val url = java.net.URL(urlStr)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "BIT-Android-Skills/1.0")
            conn.setRequestProperty("Accept", "application/vnd.github+json, text/plain, */*")
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun yamlEscape(str: String): String {
        return if (str.contains(":") || str.contains("\n") || str.contains("\"") || str.contains("'")) {
            "\"" + str.replace("\"", "\\\"").replace("\n", "\\n") + "\""
        } else {
            str
        }
    }
}
