package com.bit.agent.harness.coding

import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shadow Git Checkpoint Manager (Reversibility & Instant Undo).
 * Operates an isolated shadow repository in `.bit/shadow.git` with `--work-tree=.`.
 * Preserves the user's primary repository `.git` completely untouched while providing
 * instant one-tap rollback per agent turn and backing the UI Diff Review Card.
 *
 * Includes an automated file-mirror fallback for minimal offline Android environments
 * where the git binary is not yet installed in PRoot or host.
 */
object ShadowGitManager {

    private const val TAG = "ShadowGitManager"
    private const val SHADOW_DIR_NAME = ".bit"
    private const val SHADOW_GIT_DIR = "shadow.git"
    private const val SNAPSHOTS_FALLBACK_DIR = "shadow_snapshots"

    /**
     * Initializes the shadow repository in the target workspace root.
     */
    fun init(projectRoot: File): Boolean {
        if (!projectRoot.exists() || !projectRoot.isDirectory) return false
        val bitDir = File(projectRoot, SHADOW_DIR_NAME)
        bitDir.mkdirs()

        val shadowGit = File(bitDir, SHADOW_GIT_DIR)
        val fallbackDir = File(bitDir, SNAPSHOTS_FALLBACK_DIR)
        fallbackDir.mkdirs()

        if (!shadowGit.exists()) {
            val success = runGitCommand(projectRoot, "init", "--bare")
            if (success) {
                runGitCommand(projectRoot, "config", "core.worktree", projectRoot.absolutePath)
                runGitCommand(projectRoot, "config", "user.name", "BIT Agent")
                runGitCommand(projectRoot, "config", "user.email", "agent@bit.internal")
                runGitCommand(projectRoot, "config", "core.filemode", "false")
                Log.d(TAG, "Initialized shadow git repository in ${shadowGit.absolutePath}")
                return true
            }
        }
        return shadowGit.exists() || fallbackDir.exists()
    }

    /**
     * Creates a pre-flight snapshot before any file modification tool executes.
     * Returns the snapshot identifier or commit hash.
     */
    fun createSnapshot(projectRoot: File, turnId: String, description: String = ""): String {
        init(projectRoot)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val snapshotId = "snap_${turnId}_$timestamp"

        // 1. Always create resilient file-mirror fallback snapshot
        tryFallbackSnapshot(projectRoot, snapshotId)

        // 2. Also try shadow git commit
        tryGitCommit(projectRoot, snapshotId, description)

        return snapshotId
    }

    /**
     * Rolls back all modified and added files to the last snapshot.
     */
    fun rollback(projectRoot: File, snapshotId: String? = null): Boolean {
        // Try shadow git checkout first
        val gitSuccess = runGitCommand(projectRoot, "checkout", "HEAD", "--", ".")
        if (gitSuccess) {
            Log.i(TAG, "Successfully rolled back workspace via shadow git")
            return true
        }

        // Fallback to file mirror restore
        return restoreFallbackSnapshot(projectRoot, snapshotId)
    }

    /**
     * Produces a unified diff between current workspace state and the last snapshot.
     */
    fun getDiff(projectRoot: File): String {
        val result = runGitCommandWithOutput(projectRoot, "diff", "HEAD")
        if (result.isNotBlank()) {
            return result
        }

        // Check untracked files
        val untracked = runGitCommandWithOutput(projectRoot, "status", "--porcelain")
        if (untracked.isNotBlank()) {
            return "Pending changes:\n$untracked"
        }

        return "No changes detected."
    }

    // --- Private Helper Implementations ---

    private fun tryGitCommit(projectRoot: File, snapshotId: String, description: String): Boolean {
        val addOk = runGitCommand(projectRoot, "add", "-A")
        if (!addOk) return false

        val msg = "$snapshotId: ${description.ifBlank { "Agent turn snapshot" }}"
        return runGitCommand(projectRoot, "commit", "-m", msg, "--allow-empty")
    }

    private fun tryFallbackSnapshot(projectRoot: File, snapshotId: String): String {
        try {
            val snapshotsDir = File(File(projectRoot, SHADOW_DIR_NAME), SNAPSHOTS_FALLBACK_DIR)
            val targetDir = File(snapshotsDir, snapshotId)
            targetDir.mkdirs()

            // Copy non-hidden source files, skipping .bit, .git, node_modules, build
            projectRoot.walkTopDown()
                .filter { file ->
                    val path = file.relativeTo(projectRoot).path.replace('\\', '/')
                    !path.startsWith(".bit") &&
                    !path.startsWith(".git") &&
                    !path.contains("node_modules") &&
                    !path.contains("/build/") &&
                    !path.contains("/.gradle/")
                }
                .forEach { file ->
                    val relative = file.relativeTo(projectRoot)
                    val dest = File(targetDir, relative.path)
                    if (file.isDirectory) {
                        dest.mkdirs()
                    } else if (file.isFile && file.length() < 1024 * 1024 * 2) { // Under 2MB
                        dest.parentFile?.mkdirs()
                        file.copyTo(dest, overwrite = true)
                    }
                }
            return snapshotId
        } catch (e: Exception) {
            Log.w(TAG, "Fallback snapshot failed: ${e.message}")
            return snapshotId
        }
    }

    private fun restoreFallbackSnapshot(projectRoot: File, snapshotId: String?): Boolean {
        try {
            val snapshotsDir = File(File(projectRoot, SHADOW_DIR_NAME), SNAPSHOTS_FALLBACK_DIR)
            val sourceDir = if (!snapshotId.isNullOrBlank()) {
                File(snapshotsDir, snapshotId)
            } else {
                snapshotsDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.lastModified() }
            } ?: return false

            if (!sourceDir.exists()) return false

            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relative = file.relativeTo(sourceDir)
                    val target = File(projectRoot, relative.path)
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore fallback snapshot: ${e.message}", e)
            return false
        }
    }

    private fun runGitCommand(projectRoot: File, vararg gitArgs: String): Boolean {
        val bitDir = File(projectRoot, SHADOW_DIR_NAME)
        val shadowGit = File(bitDir, SHADOW_GIT_DIR)

        val fullArgs = mutableListOf("git")
        if (gitArgs.firstOrNull() != "init") {
            fullArgs.add("--git-dir=${shadowGit.absolutePath}")
            fullArgs.add("--work-tree=${projectRoot.absolutePath}")
        } else {
            fullArgs.add("--git-dir=${shadowGit.absolutePath}")
        }
        fullArgs.addAll(gitArgs)

        return try {
            val process = ProcessBuilder(fullArgs)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()
            val exit = process.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun runGitCommandWithOutput(projectRoot: File, vararg gitArgs: String): String {
        val bitDir = File(projectRoot, SHADOW_DIR_NAME)
        val shadowGit = File(bitDir, SHADOW_GIT_DIR)

        val fullArgs = mutableListOf(
            "git",
            "--git-dir=${shadowGit.absolutePath}",
            "--work-tree=${projectRoot.absolutePath}"
        )
        fullArgs.addAll(gitArgs)

        return try {
            val process = ProcessBuilder(fullArgs)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text.trim()
        } catch (_: Exception) {
            ""
        }
    }
}
