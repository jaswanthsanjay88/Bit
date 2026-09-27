package com.bit.skills

import android.content.Context
import android.util.Log
import com.bit.models.Skill
import com.bit.models.SkillType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("bit_skills_store", Context.MODE_PRIVATE)

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private val _skills = MutableStateFlow<List<Skill>>(loadSavedSkills())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    init {
        instance = this
    }

    companion object {
        private const val TAG = "SkillManager"
        private const val KEY_SKILLS_JSON = "skills_json"

        @Volatile
        private var instance: SkillManager? = null

        fun getInstance(context: Context): SkillManager {
            return instance ?: synchronized(this) {
                instance ?: SkillManager(context.applicationContext).also { instance = it }
            }
        }

        fun getSkillSlug(skill: Skill): String {
            return skill.name.lowercase()
                .replace(Regex("""[^a-z0-9]+"""), "-")
                .trim('-')
                .ifBlank { skill.id.take(8) }
        }

        val DEFAULT_BUILTIN_SKILLS = listOf(
            Skill(
                id = "skill-plan",
                name = "Plan",
                description = "Implementation planning and task breakdown before execution.",
                instructions = """
                    - Decompose Objectives: Break complex tasks into distinct, verifiable phases.
                    - Identify Risks: Check dependencies, file boundaries, and breaking changes before execution.
                    - Verification Gates: Define clear testable criteria for every phase.
                """.trimIndent(),
                icon = "code",
                skillType = SkillType.INSTRUCTIONAL,
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-web-search",
                name = "Web Search & Scraping",
                description = "Searches the live web via DuckDuckGo and automatically fetches top result pages into clean markdown.",
                instructions = """
                    When you need current information, follow this exact sequence — do not skip steps:

                    1. Call web_search with a concise query (2-6 words). If the user's request is vague (e.g. "latest news", "what's happening"), rewrite it into something a search engine can actually answer — add a topic, region, or category (e.g. "India news today", "technology news this week") rather than passing the vague phrase through.

                    2. From the web_search results, select the top 2-3 URLs that are NOT generic homepages (skip bare domains like cnn.com or bbc.co.uk/news — prefer specific article/story URLs with dated slugs or headlines in the path).

                    3. Call web_fetch (fetch_page) on each selected URL to get the actual article content. Do this even if the search snippet looks sufficient — snippets are too short to answer from reliably.

                    4. Only after fetch_page returns real content, write your answer using that content. Cite which source each fact came from.

                    5. If web_search returns only homepage-level results with no specific articles, say so explicitly to the user rather than presenting homepage links as "the latest news." Do not fabricate headlines, dates, or facts under any circumstance.

                    6. If web_search or web_fetch returns empty, an error, or a CAPTCHA/anomaly page, report the search failed — never fill the gap from memory.
                """.trimIndent(),
                icon = "search",
                skillType = SkillType.TOOL,
                tools = listOf("web_search", "web_fetch", "fetch_page"),
                requiredPermissions = listOf("INTERNET"),
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-memory-vault",
                name = "AI Memory Vault",
                description = "Autonomously writes important user facts and extracts knowledge graph triples.",
                instructions = "Store persistent user preferences, names, and key facts into the episodic memory vault.",
                icon = "storage",
                skillType = SkillType.INSTRUCTIONAL,
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-file-ops",
                name = "File Operations",
                description = "Read and write project files, exports, and markdown documents.",
                instructions = "Execute file inspection and directory listing safely.",
                icon = "terminal",
                skillType = SkillType.INSTRUCTIONAL,
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-coding-standards",
                name = "Coding Standards",
                description = "Enforces clean code, immutability, readability, KISS/DRY principles, and error handling across languages.",
                instructions = """
                    - Readability First: Choose clear, intention-revealing names. Self-documenting code over excessive comments.
                    - Immutability: Always create new copies with updates rather than mutating state in-place.
                    - KISS & DRY: Prefer the simplest working solution; extract shared logic without premature abstraction.
                    - Error Boundaries: Handle errors at boundaries. Never silently swallow exceptions.
                """.trimIndent(),
                icon = "code",
                skillType = SkillType.INSTRUCTIONAL,
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-terminal-ops",
                name = "Terminal & Linux Ops",
                description = "Executes safe bash commands, checks git status, and inspects files inside the Linux PRoot workspace.",
                instructions = """
                    - Evidence-First: Inspect current directory, file contents, and git status before executing commands.
                    - Non-Destructive: Do not run recursive deletes (e.g. rm -rf) or modify outside the designated workspace.
                    - Verification: Re-run status or test commands to verify results before declaring a task finished.
                """.trimIndent(),
                icon = "terminal",
                skillType = SkillType.EXECUTABLE,
                requiresWorkspace = true,
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-security-review",
                name = "Security Review",
                description = "Audits code and configurations for exposed API keys, credential leaks, and unsafe inputs.",
                instructions = """
                    - Secrets Management: Never hardcode API keys, tokens, or passwords in code or prompts.
                    - Input Validation: Validate and sanitize all external parameters, URLs, and file uploads at boundaries.
                    - Safe Defaults: Follow principle of least privilege and verify permission checks before executing actions.
                """.trimIndent(),
                icon = "security",
                skillType = SkillType.INSTRUCTIONAL,
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-research-ops",
                name = "Research Ops",
                description = "Synthesizes evidence-first reports with verified facts, citations, and clear separation from inferences.",
                instructions = """
                    - Verify Facts: Use search to verify temporal and empirical facts before answering.
                    - Layer Separation: Clearly separate verified facts, user-provided evidence, and model inferences.
                    - Structured Reports: Format multi-source findings with concise bullet points and source attribution.
                """.trimIndent(),
                icon = "psychology",
                skillType = SkillType.INSTRUCTIONAL,
                enabled = true,
                isBuiltIn = true
            ),
            Skill(
                id = "skill-ui-polish",
                name = "UI/UX Design Polish",
                description = "Applies concrete design-engineering details: concentric radius, 48dp touch targets, and optical alignment.",
                instructions = """
                    - Concentric Radius: For nested surfaces, outer corner radius = inner corner radius + padding.
                    - Touch Targets: Ensure all clickable controls have a minimum touch hit area of 48dp x 48dp.
                    - Optical Alignment: Align asymmetric icons and visual centroids optically rather than purely geometrically.
                    - Spacing Rhythm: Use consistent 4dp/8dp spatial increments and avoid cramped typography.
                """.trimIndent(),
                icon = "brush",
                skillType = SkillType.INSTRUCTIONAL,
                enabled = true,
                isBuiltIn = true
            )
        )
    }

    private fun loadSavedSkills(): List<Skill> {
        val rawJson = prefs.getString(KEY_SKILLS_JSON, null) ?: return DEFAULT_BUILTIN_SKILLS

        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<Skill>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id")
                val name = obj.optString("name")
                // Exclude removed calculator skill
                if (id == "skill-calculator" || name.contains("calculator", ignoreCase = true)) {
                    continue
                }
                val typeStr = obj.optString("skillType", "INSTRUCTIONAL")
                val skillType = try {
                    com.bit.models.SkillType.valueOf(typeStr)
                } catch (_: Exception) {
                    com.bit.models.SkillType.INSTRUCTIONAL
                }
                val requiresWorkspace = obj.optBoolean("requiresWorkspace", false)
                val scriptPath = if (obj.has("scriptPath") && !obj.isNull("scriptPath")) obj.optString("scriptPath") else null
                val permsJson = obj.optJSONArray("requiredPermissions")
                val requiredPermissions = mutableListOf<String>()
                if (permsJson != null) {
                    for (p in 0 until permsJson.length()) {
                        val perm = permsJson.optString(p)
                        if (perm.isNotBlank()) requiredPermissions.add(perm)
                    }
                }
                val toolsJson = obj.optJSONArray("tools")
                val tools = mutableListOf<String>()
                if (toolsJson != null) {
                    for (t in 0 until toolsJson.length()) {
                        val tn = toolsJson.optString(t)
                        if (tn.isNotBlank()) tools.add(tn)
                    }
                }

                list.add(
                    Skill(
                        id = id,
                        name = name,
                        description = obj.optString("description"),
                        instructions = obj.optString("instructions"),
                        icon = obj.optString("icon", "sparkles"),
                        skillType = skillType,
                        requiresWorkspace = requiresWorkspace,
                        requiredPermissions = requiredPermissions,
                        tools = tools,
                        scriptPath = scriptPath,
                        enabled = obj.optBoolean("enabled", true),
                        isBuiltIn = obj.optBoolean("isBuiltIn", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            // Auto-merge any default built-in skills that don't exist yet in the saved list or update them
            val existingIds = list.map { it.id }.toSet()
            val existingSlugs = list.map { getSkillSlug(it) }.toSet()
            var addedAny = false
            for (defaultSkill in DEFAULT_BUILTIN_SKILLS) {
                val index = list.indexOfFirst { it.id == defaultSkill.id }
                if (index != -1) {
                    val existing = list[index]
                    if (existing.isBuiltIn && (existing.instructions != defaultSkill.instructions || existing.skillType != defaultSkill.skillType || existing.tools != defaultSkill.tools)) {
                        list[index] = existing.copy(
                            instructions = defaultSkill.instructions,
                            description = defaultSkill.description,
                            skillType = defaultSkill.skillType,
                            tools = defaultSkill.tools
                        )
                        addedAny = true
                    }
                } else if (getSkillSlug(defaultSkill) !in existingSlugs) {
                    list.add(defaultSkill)
                    addedAny = true
                }
            }
            if (addedAny) {
                persistSkills(list)
            }
            if (list.isEmpty()) DEFAULT_BUILTIN_SKILLS else list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved skills", e)
            DEFAULT_BUILTIN_SKILLS
        }
    }

    private fun persistSkills(list: List<Skill>) {
        try {
            val arr = JSONArray()
            for (s in list) {
                arr.put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("description", s.description)
                    put("instructions", s.instructions)
                    put("icon", s.icon ?: "sparkles")
                    put("skillType", s.skillType.name)
                    put("requiresWorkspace", s.requiresWorkspace)
                    put("requiredPermissions", JSONArray(s.requiredPermissions))
                    put("tools", JSONArray(s.tools))
                    if (s.scriptPath != null) {
                        put("scriptPath", s.scriptPath)
                    }
                    put("enabled", s.enabled)
                    put("isBuiltIn", s.isBuiltIn)
                    put("createdAt", s.createdAt)
                })
            }
            prefs.edit().putString(KEY_SKILLS_JSON, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting skills", e)
        }
    }

    /**
     * Synchronous in-memory update on caller thread.
     * Disk persistence is fire-and-forget on background IO.
     */
    fun setOrderedSkills(newOrder: List<Skill>) {
        _skills.value = newOrder
        scope.launch {
            persistSkills(newOrder)
        }
    }

    fun addSkill(skill: Skill) {
        val updated = _skills.value + skill
        _skills.value = updated
        scope.launch {
            persistSkills(updated)
        }
    }

    fun updateSkill(skill: Skill) {
        val updated = _skills.value.map {
            if (it.id == skill.id) skill else it
        }
        _skills.value = updated
        scope.launch {
            persistSkills(updated)
        }
    }

    fun removeSkill(skillId: String) {
        val updated = _skills.value.filter { it.id != skillId }
        _skills.value = updated
        scope.launch {
            persistSkills(updated)
        }
    }

    fun toggleSkill(skillId: String, enabled: Boolean) {
        val updated = _skills.value.map {
            if (it.id == skillId) it.copy(enabled = enabled) else it
        }
        _skills.value = updated
        scope.launch {
            persistSkills(updated)
        }
    }

    fun reorderSkills(fromIndex: Int, toIndex: Int) {
        val list = _skills.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _skills.value = list
            scope.launch {
                persistSkills(list)
            }
        }
    }

    fun getSkillsDir(): java.io.File {
        val dir = context.filesDir.resolve("skills")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isWorkspaceAvailable(): Boolean {
        return try {
            val wsDir = context.filesDir.resolve("workspaces")
            wsDir.exists() && (wsDir.listFiles()?.isNotEmpty() == true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds lightweight progressive disclosure catalog for tool-capable models.
     * Exposes ~15-20 tokens per skill in compact <available_skills> XML format.
     * Excludes executable skills requiring PRoot if the workspace is not active/available.
     */
    fun getSkillCatalogPrompt(isWorkspaceAvailable: Boolean = isWorkspaceAvailable()): String {
        val active = _skills.value.filter { skill ->
            skill.enabled &&
            (skill.instructions.isNotBlank() || skill.isExecutable) &&
            (!skill.requiresWorkspace || isWorkspaceAvailable)
        }
        if (active.isEmpty()) return ""

        return buildString {
            appendLine("<available_skills>")
            active.forEach { skill ->
                val slug = getSkillSlug(skill)
                val typeTag = if (skill.isExecutable) "executable" else "instructional"
                val desc = skill.description.ifBlank { "Specialized agent routine" }
                    .trim().replace("\n", " ").replace("<", "").replace(">", "")
                appendLine("  <skill name=\"$slug\" type=\"$typeTag\">$desc</skill>")
            }
            appendLine("</available_skills>")
            appendLine("To load a skill's full instructions or capability, invoke `use_skill(name = \"...\")`.")
        }
    }

    /**
     * Legacy full instructions prompt (used when tool calling is disabled or for base completion models).
     */
    fun getActiveSkillsPrompt(): String {
        val active = _skills.value.filter { it.enabled && it.instructions.isNotBlank() }
        if (active.isEmpty()) return ""

        return buildString {
            appendLine("## Active Skills & Specializations")
            active.forEach { skill ->
                appendLine("### ${skill.name}")
                if (skill.description.isNotBlank()) {
                    appendLine("Description: ${skill.description}")
                }
                appendLine(skill.instructions)
                appendLine()
            }
        }
    }

    fun getSkillSlug(skill: Skill): String = Companion.getSkillSlug(skill)

    fun findSkill(query: String): Skill? {
        val q = query.trim().lowercase()
        if (q.isBlank()) return null
        val cleanSlug = q.removePrefix("/")
        return _skills.value.find { skill ->
            val slug = getSkillSlug(skill)
            skill.id.equals(q, ignoreCase = true) ||
            skill.id.removePrefix("skill-").equals(cleanSlug, ignoreCase = true) ||
            skill.name.equals(q, ignoreCase = true) ||
            slug.equals(cleanSlug, ignoreCase = true) ||
            slug.replace("-linux-", "-").equals(cleanSlug, ignoreCase = true) ||
            skill.name.lowercase().contains(cleanSlug)
        }
    }

    fun getSkillBySlug(slug: String): Skill? {
        val clean = slug.trim().removePrefix("/").lowercase()
        return _skills.value.find {
            val s = getSkillSlug(it)
            s == clean || s.replace("-linux-", "-") == clean || it.id.removePrefix("skill-") == clean
        }
    }
}
