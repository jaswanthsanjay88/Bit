package com.bit.workspace

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Unified workspace directory path resolver.
 * Ensures tools (file read/write/edit) and the PRoot terminal mount reference
 * the exact same underlying directory (/workspace).
 */
object WorkspacePaths {

    /**
     * Resolves the canonical host directory mounted to `/workspace` in PRoot.
     * Path: `filesDir/workspaces/<active_root>/files`.
     */
    fun root(ctx: Context, rootName: String? = null): File {
        return root(ctx.applicationContext.filesDir, rootName)
    }

    /**
     * Overload for testability and headless environments.
     */
    fun root(appFilesDir: File, rootName: String? = null): File {
        val workspacesDir = File(appFilesDir, "workspaces")
        workspacesDir.mkdirs()

        val active = if (!rootName.isNullOrBlank()) {
            File(workspacesDir, rootName)
        } else {
            val candidates = workspacesDir.listFiles()?.filter { it.isDirectory && it.name != "rootfs_cache" }.orEmpty()
            candidates.maxByOrNull { dir ->
                val filesSub = File(dir, "files")
                if (filesSub.exists() && (filesSub.list()?.isNotEmpty() == true)) {
                    filesSub.lastModified() + 100_000L
                } else {
                    dir.lastModified()
                }
            } ?: File(workspacesDir, "default")
        }

        val filesDir = File(active, "files")
        filesDir.mkdirs()
        return filesDir.canonicalFile
    }

    /**
     * Resolves a user or model supplied path to the canonical workspace file.
     * Handles:
     * - `/workspace/portfolio.html` -> `<root>/portfolio.html`
     * - `portfolio.html` -> `<root>/portfolio.html`
     * - `/storage/emulated/0/.../files/workspace/portfolio.html` -> `<root>/portfolio.html`
     */
    fun resolveFile(ctx: Context, path: String, rootName: String? = null): File {
        return resolveFile(ctx.applicationContext.filesDir, path, rootName)
    }

    /**
     * Overload for testability and headless environments.
     */
    fun resolveFile(appFilesDir: File, path: String, rootName: String? = null): File {
        val clean = path.trim().replace('\\', '/')
        val rootDir = root(appFilesDir, rootName)

        val relative = when {
            clean.startsWith("/workspace/") -> clean.removePrefix("/workspace/")
            clean == "/workspace" -> ""
            clean.startsWith("workspace/") -> clean.removePrefix("workspace/")
            clean.contains("/files/workspace/") -> clean.substringAfter("/files/workspace/")
            clean.startsWith("/") -> {
                val f = File(clean)
                val canonical = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
                if (canonical.startsWith(rootDir.canonicalPath)) {
                    f.canonicalFile.relativeTo(rootDir).path
                } else {
                    f.name
                }
            }
            else -> clean
        }

        val target = if (relative.isBlank()) rootDir else File(rootDir, relative)
        target.parentFile?.mkdirs()
        return target
    }

    /**
     * Returns the path as viewed inside the Linux PRoot terminal (e.g. `/workspace/portfolio.html`).
     */
    fun terminalPath(ctx: Context, file: File, rootName: String? = null): String {
        return terminalPath(ctx.applicationContext.filesDir, file, rootName)
    }

    /**
     * Overload for testability and headless environments.
     */
    fun terminalPath(appFilesDir: File, file: File, rootName: String? = null): String {
        val rootDir = root(appFilesDir, rootName)
        return try {
            if (file.canonicalPath.startsWith(rootDir.canonicalPath)) {
                val rel = file.canonicalFile.relativeTo(rootDir.canonicalFile).path.replace('\\', '/')
                "/workspace/$rel"
            } else {
                "/workspace/${file.name}"
            }
        } catch (_: Exception) {
            "/workspace/${file.name}"
        }
    }

    /**
     * Human-readable byte size.
     */
    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}
