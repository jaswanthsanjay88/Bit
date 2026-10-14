package com.bit.agent.harness.coding

import java.io.File

/**
 * Detected technology stack and default test/build runners.
 */
data class DetectedProjectStack(
    val stackName: String,
    val defaultTestCommand: String,
    val defaultBuildCommand: String,
    val manifestFile: String
)

object StackDetector {

    fun detect(projectRoot: File): DetectedProjectStack {
        if (!projectRoot.exists() || !projectRoot.isDirectory) {
            return fallbackStack()
        }

        val files = projectRoot.listFiles().orEmpty().map { it.name }

        return when {
            "gradlew" in files || "build.gradle.kts" in files || "build.gradle" in files -> {
                val runner = if ("gradlew" in files) "./gradlew" else "gradle"
                DetectedProjectStack(
                    stackName = "Gradle",
                    defaultTestCommand = "$runner test",
                    defaultBuildCommand = "$runner build",
                    manifestFile = if ("build.gradle.kts" in files) "build.gradle.kts" else "build.gradle"
                )
            }
            "Cargo.toml" in files -> {
                DetectedProjectStack(
                    stackName = "Rust / Cargo",
                    defaultTestCommand = "cargo test",
                    defaultBuildCommand = "cargo build",
                    manifestFile = "Cargo.toml"
                )
            }
            "package.json" in files -> {
                DetectedProjectStack(
                    stackName = "Node / NPM",
                    defaultTestCommand = "npm test",
                    defaultBuildCommand = "npm run build",
                    manifestFile = "package.json"
                )
            }
            "pyproject.toml" in files || "requirements.txt" in files || "pytest.ini" in files -> {
                DetectedProjectStack(
                    stackName = "Python",
                    defaultTestCommand = "pytest",
                    defaultBuildCommand = "python -m build",
                    manifestFile = if ("pyproject.toml" in files) "pyproject.toml" else "requirements.txt"
                )
            }
            "Makefile" in files -> {
                DetectedProjectStack(
                    stackName = "Make",
                    defaultTestCommand = "make test",
                    defaultBuildCommand = "make",
                    manifestFile = "Makefile"
                )
            }
            else -> fallbackStack()
        }
    }

    private fun fallbackStack() = DetectedProjectStack(
        stackName = "Generic",
        defaultTestCommand = "test",
        defaultBuildCommand = "build",
        manifestFile = ""
    )
}

/**
 * Project Context Manager.
 * Loads BIT.md / AGENTS.md and generates a compact 600-800 token prefix.
 */
object ProjectContextManager {

    private const val MAX_DOC_LINES = 200

    /**
     * Builds the compact startup prefix for the agent.
     */
    fun buildStartupPrefix(projectRoot: File): String {
        val stack = StackDetector.detect(projectRoot)
        val instructions = readProjectInstructions(projectRoot)
        val tree = generateCompactTree(projectRoot, maxDepth = 2, maxEntries = 25)

        return buildString {
            appendLine("## PROJECT GROUNDING")
            appendLine("ROOT: ${projectRoot.name} | STACK: ${stack.stackName}")
            appendLine("DEFAULT TEST: `${stack.defaultTestCommand}` | BUILD: `${stack.defaultBuildCommand}`")
            appendLine()
            if (instructions.isNotBlank()) {
                appendLine("## PROJECT INSTRUCTIONS")
                appendLine(instructions)
                appendLine()
            }
            appendLine("## DIRECTORY STRUCTURE")
            appendLine("```")
            appendLine(tree)
            appendLine("```")
        }.trim()
    }

    /**
     * Looks for BIT.md or AGENTS.md in project root.
     */
    fun readProjectInstructions(projectRoot: File): String {
        val candidates = listOf(
            File(projectRoot, "BIT.md"),
            File(projectRoot, "AGENTS.md"),
            File(projectRoot, "CLAUDE.md")
        )
        val file = candidates.firstOrNull { it.exists() && it.isFile } ?: return ""
        return file.readLines(Charsets.UTF_8).take(MAX_DOC_LINES).joinToString("\n").trim()
    }

    /**
     * Compact directory tree with line counts for code files.
     */
    fun generateCompactTree(projectRoot: File, maxDepth: Int = 2, maxEntries: Int = 30): String {
        if (!projectRoot.exists() || !projectRoot.isDirectory) return "(empty directory)"

        val lines = mutableListOf<String>()
        projectRoot.walkTopDown().maxDepth(maxDepth).filter { f ->
            val p = f.relativeTo(projectRoot).path.replace('\\', '/')
            !p.startsWith(".git") &&
            !p.startsWith(".bit") &&
            !p.contains("node_modules") &&
            !p.contains("/build/") &&
            !p.contains("/.gradle/") &&
            !p.contains("__pycache__")
        }.take(maxEntries).forEach { file ->
            val rel = file.relativeTo(projectRoot).path.replace('\\', '/')
            if (rel.isNotBlank()) {
                val prefix = if (file.isDirectory) "dir/  " else "file: "
                val count = if (file.isFile) " (${file.readLines().size} lines)" else ""
                lines.add("$prefix$rel$count")
            }
        }

        return if (lines.isEmpty()) "(empty project)" else lines.joinToString("\n")
    }
}
