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
        scope.launch {
            syncBundledSkillsToDisk()
        }
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
            ),
            Skill(
                id = "skill-bit-workspace-shell",
                name = "bit-workspace-shell",
                description = "How to autonomously build, edit, run, and test projects with the BIT workspace_shell tool inside the on-device Linux PRoot sandbox (Alpine or Debian/Ubuntu). Use whenever the user asks to create, fix, refactor, run, install, build, or test code or a project, mentions the terminal, shell, workspace, proot, apk, apt, npm, pip, python, gcc, or any command line task, even if they never say \"shell\". Makes the agent behave like Claude Code or Antigravity, with a plan, small steps, verification after every change, a fix loop, and honest reporting.",
                instructions = """
# BIT workspace_shell: autonomous coding agent

You work in a Linux PRoot sandbox on an Android phone. Work like a coding agent (Claude Code, Antigravity): do the task end to end, verify it, report what actually happened. Do not hand the user commands to run themselves.

## Sandbox facts (they change how you write commands)

- Tool: `workspace_shell` with `command` (required), `cwd` (relative to `/workspace`), `timeout` (seconds, default 30, max 600). Agent mode may only expose `command`; then use `cd dir && ...`.
- **Every call is a fresh process.** cwd resets to `/workspace`, `export` does not persist, background processes are killed when the call returns. **Files and installed packages do persist.**
  - Chain dependent steps in ONE call: `cd app && npm install && npm run build`.
  - To test a server: start, probe, kill in one call: `(node server.js & p=${'$'}!; sleep 2; curl -s localhost:3000; kill ${'$'}p)`.
- Non-interactive only. Always pass `-y`/`--no-input`/`--yes`. Never run editors, REPLs, `read`, or anything that prompts. Write files with heredocs (`cat > f <<'EOF'`) or the file tools if available.
- You are root (fake uid 0). `/workspace` is the project area; keep projects there. Hardlinks are emulated as symlinks.
- Output you see is capped (~4000 chars per stdout/stderr). Filter noisy commands: `| tail -n 40`, `| grep -E "error|FAIL"`, `-q` flags. Never `cat` huge files; use `head`, `sed -n '1,80p'`, `grep -n`.
- No systemd/services/cgroups. `systemctl`/`service` are stubs that do nothing. Run the program directly.
- Network works. Slow installs: set `timeout` to 120-600.

## Know your distro first (once per session)

```
cat /etc/os-release | head -3; uname -m; command -v bash python3 node gcc git
```
Alpine (`apk`, musl, `ash`, no bash unless installed): read `references/alpine.md`.
Debian/Ubuntu (`apt-get`, glibc, bash): read `references/debian.md`.
Do not guess the package manager. Do not use `apt` on Alpine or `apk` on Debian.

## The loop: orient, plan, act, verify, fix, report

1. **Orient before touching anything.** `ls -la`, `git status` (if a repo), read `package.json` / `pyproject.toml` / `Makefile` / `README` to find the real build and test commands. Infer them; do not ask the user "which npm command?". Ask only when the goal itself is ambiguous.
2. **Plan briefly.** For anything over ~3 steps, write a short numbered plan, and save it to `/workspace/<project>/.bit/progress.md` (goal, plan, done items, current state, next step). Update it after each milestone. This file is your memory across calls and turns; re-read it if you are unsure what you already built.
3. **Act in small steps.** One logical change at a time (one file, one install, one fix). Prefer minimal edits over rewrites. Match the project's existing style and tools.
4. **Verify after EVERY create or edit.** Never move on blind. See the table below.
5. **Fix failures.** Read the actual error (exit_code + stderr), form a hypothesis, change one thing, re-run the same check. After 3 failed attempts on the same error, stop and change approach or tell the user what is blocking and what you tried. Never loop on the same command.
6. **Final verification, then report.** Re-run the full check (build + tests, or run the program) after the last edit. Then summarize.

## What "verify" means by situation

| You just did | Run at minimum |
|---|---|
| Created/edited Python | `python3 -m py_compile file.py`, then run it or its tests |
| Created/edited JS/TS | `node --check file.js` or `npx tsc --noEmit`, then run or test |
| Created/edited shell script | `sh -n script.sh`, then run it |
| Edited C/C++/Rust/Go | compile (`gcc -Wall`, `cargo check`, `go build ./...`), then run tests |
| Installed dependencies | import/`--version` smoke check, e.g. `python3 -c "import x"` |
| Fixed a bug | re-run the exact failing command/test, then the wider suite for regressions |
| Finished a feature | full test suite + run the real entry point once |
| Project has no tests | write one small smoke test or run the program with a sample input; say so |

Run tests with plain commands that BIT recognizes: `pytest -q`, `npm test`, `cargo test`, `python3 -m unittest`. Test runs are parsed into pass/fail counts for you.

The user can scale this: "just write it, don't test" or "run everything and fix until green" both override the defaults. Follow what they asked.

## Safety rules

- Stay inside `/workspace`. No `rm -rf` on anything you did not create this session; no `rm -rf /`, `~`, or `*` at a bare path. Check `pwd` and `ls` before deleting.
- Do not overwrite existing files you have not read. Check with `ls`/`git diff` first; back up with `cp f f.bak` if replacing wholesale.
- Do not run `curl ... | sh` from unknown sources. Prefer distro packages.
- Never print or commit secrets (API keys, tokens). Don't paste `.env` contents into output.
- Python: use a venv, never global `pip install` (PEP 668 blocks it). Check `ls /opt/bit-env` first; otherwise `python3 -m venv .venv && . .venv/bin/activate`. Activate inside the same call as the command that uses it.

## Honest reporting

- A step succeeded only if `exit_code: 0` (or `status: success`). Never claim tests pass without seeing them pass. If you could not verify something, say exactly that.
- Final message: what you built or changed (files), what you ran, the real result (e.g. "12 passed, 0 failed"), and anything left undone. Short and factual. Do not paste full logs.
- If something failed, show the key error line and what you tried, not a wall of output.

## Common gotchas

- `command not found`: install it (`apk add` / `apt-get install`), then re-check with `command -v`.
- `timedOut: true` / exit code -1: raise `timeout`, run long work in smaller steps, or filter output so the command can finish.
- Stale package index: Debian needs `apt-get update` before the first install; Alpine `apk update`.
- Empty output with exit 0 is normal for many commands. Confirm effects with `ls`, `cat`, or `git diff --stat`.
- Anything that needs a display, systemd, Docker, or `mount` will not work here. Choose a headless alternative.
                """.trimIndent(),
                icon = "terminal",
                skillType = SkillType.EXECUTABLE,
                requiresWorkspace = true,
                requiredPermissions = listOf("terminal", "filesystem"),
                tools = listOf("workspace_shell"),
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

    /**
     * Unpacks bundled skills from assets/skills/ into on-device context.filesDir/skills/
     * so they are available for local file resolution and mounted into Linux PRoot sandbox at /skills.
     */
    fun syncBundledSkillsToDisk() {
        try {
            copyAssetFolderRecursively("skills", getSkillsDir())
            com.bit.workspace.WorkspacePaths.ensureBundledReferences(
                context.filesDir,
                com.bit.workspace.WorkspacePaths.root(context.filesDir)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing bundled skills to disk", e)
        }
    }

    private fun copyAssetFolderRecursively(assetPath: String, targetDir: java.io.File) {
        val assetManager = context.assets
        val children = assetManager.list(assetPath) ?: return
        if (children.isEmpty()) {
            try {
                assetManager.open(assetPath).use { input ->
                    targetDir.parentFile?.mkdirs()
                    java.io.FileOutputStream(targetDir).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {
            }
            return
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        for (child in children) {
            val childAssetPath = if (assetPath.isBlank()) child else "$assetPath/$child"
            val childTargetFile = java.io.File(targetDir, child)
            val subChildren = assetManager.list(childAssetPath)
            if (!subChildren.isNullOrEmpty()) {
                copyAssetFolderRecursively(childAssetPath, childTargetFile)
            } else {
                try {
                    assetManager.open(childAssetPath).use { input ->
                        childTargetFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(childTargetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {
                    if (!childTargetFile.exists()) {
                        childTargetFile.mkdirs()
                    }
                }
            }
        }
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
     *
     * Injects core built-in skills and query-matched domain skills first, strictly bounded
     * within [maxBudgetTokens] to prevent KV-cache context exhaustion.
     */
    fun getSkillCatalogPrompt(
        isWorkspaceAvailable: Boolean = isWorkspaceAvailable(),
        maxBudgetTokens: Int = 350,
        userQuery: String = ""
    ): String {
        val active = _skills.value.filter { skill ->
            skill.enabled &&
            (skill.instructions.isNotBlank() || skill.isExecutable) &&
            (!skill.requiresWorkspace || isWorkspaceAvailable)
        }
        if (active.isEmpty()) return ""

        val queryLower = userQuery.lowercase().trim()
        val queryWords = queryLower.split(Regex("[^a-zA-Z0-9_-]+")).filter { it.length > 2 }

        val coreSlugs = setOf(
            "file-ops", "terminal-ops", "coding-standards",
            "security-review", "research-ops", "git-workflow"
        )

        val coreSkills = mutableListOf<Skill>()
        val matchedSkills = mutableListOf<Skill>()
        val otherSkills = mutableListOf<Skill>()

        for (skill in active) {
            val slug = getSkillSlug(skill)
            if (slug in coreSlugs || skill.isBuiltIn) {
                coreSkills.add(skill)
            } else if (queryWords.isNotEmpty() && queryWords.any { w ->
                slug.contains(w) || skill.name.lowercase().contains(w) || skill.description.lowercase().contains(w)
            }) {
                matchedSkills.add(skill)
            } else {
                otherSkills.add(skill)
            }
        }

        val prioritized = (coreSkills + matchedSkills + otherSkills).distinctBy { it.id }

        // Progressive disclosure with budget capping (~20 tokens per entry)
        val selected = mutableListOf<Skill>()
        var approxTokens = 0
        val tokensPerSkill = 20

        for (skill in prioritized) {
            if (approxTokens + tokensPerSkill > maxBudgetTokens && selected.size >= 4) {
                break
            }
            selected.add(skill)
            approxTokens += tokensPerSkill
        }

        val remainingCount = active.size - selected.size

        return buildString {
            appendLine("<available_skills>")
            selected.forEach { skill ->
                val slug = getSkillSlug(skill)
                val typeTag = if (skill.isExecutable) "executable" else "instructional"
                val desc = skill.description.ifBlank { "Specialized agent routine" }
                    .trim().replace("\n", " ").replace("<", "").replace(">", "")
                appendLine("  <skill name=\"$slug\" type=\"$typeTag\">$desc</skill>")
            }
            if (remainingCount > 0) {
                appendLine("  <!-- $remainingCount additional domain skills available on-demand. To inspect or load, invoke `use_skill(name = \"...\")` -->")
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
