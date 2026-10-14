package com.bit.workspace

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Unified workspace directory path resolver.
 * Ensures tools (file read/write/edit) and the PRoot terminal mount reference
 * the exact same underlying directory (/workspace).
 * Enforces strict canonical jail isolation against path traversal attacks.
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
        ensureBundledReferences(appFilesDir, filesDir)
        return filesDir.canonicalFile
    }

    /**
     * Ensures bundled references (e.g. Alpine & Debian command manuals) exist inside /workspace/references.
     */
    fun ensureBundledReferences(appFilesDir: File, rootDir: File) {
        try {
            val skillsRefDir = File(appFilesDir, "skills/bit-workspace-shell/references")
            if (skillsRefDir.exists() && skillsRefDir.isDirectory) {
                val files = skillsRefDir.listFiles()?.filter { it.isFile }.orEmpty()
                if (files.isNotEmpty()) {
                    val targetRefsDir = File(rootDir, "references")
                    targetRefsDir.mkdirs()
                    files.forEach { src ->
                        val dest = File(targetRefsDir, src.name)
                        if (!dest.exists() || dest.length() != src.length()) {
                            src.copyTo(dest, overwrite = true)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Resolves a user or model supplied path to the canonical workspace file.
     * Enforces canonical jail: candidate path must reside inside rootDir.
     * Throws SecurityException on traversal attempts or unauthorized host access.
     */
    fun resolveFile(ctx: Context?, path: String, rootName: String? = null): File {
        if (ctx != null) {
            return resolveFile(ctx.applicationContext.filesDir, path, rootName)
        }
        // Headless / Context-free mode (e.g. unit tests)
        val clean = path.trim().replace('\\', '/')
        val f = File(clean)

        // Block attempts to escape to system sensitive locations or memory vault
        val sensitivePrefixes = listOf("/etc", "/system", "/proc", "/sys", "/dev", "vault", ".ssh")
        if (sensitivePrefixes.any { clean.startsWith(it) || clean.contains("/$it") }) {
            throw SecurityException("Access denied: path traverses outside workspace root ('$path')")
        }

        if (f.isAbsolute) {
            val canonical = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
            val tmpDir = System.getProperty("java.io.tmpdir")
            val isTempOrWorkspace = (tmpDir != null && canonical.startsWith(File(tmpDir).canonicalPath)) ||
                    canonical.contains("workspace")
            if (isTempOrWorkspace) {
                return f.canonicalFile
            }
            throw SecurityException("Access denied: path traverses outside workspace root ('$path')")
        }

        // Relative path: resolve against working workspace directory
        val baseDir = File("workspace").canonicalFile
        val target = File(baseDir, clean.removePrefix("workspace/").removePrefix("/workspace/").trimStart('/')).canonicalFile
        val basePath = baseDir.canonicalPath
        val targetPath = target.canonicalPath
        if (targetPath != basePath && !targetPath.startsWith(basePath + File.separator)) {
            throw SecurityException("Access denied: path traverses outside workspace root ('$path')")
        }
        return target
    }

    /**
     * Overload for testability and headless environments.
     */
    fun resolveFile(appFilesDir: File, path: String, rootName: String? = null): File {
        val clean = path.trim().replace('\\', '/')
        val rootDir = root(appFilesDir, rootName).canonicalFile
        val rootCanonicalPath = rootDir.canonicalPath

        // 1. Guard against unauthorized host absolute paths
        if (clean.startsWith("/")) {
            val isVirtualWorkspace = clean.startsWith("/workspace/") || clean == "/workspace"
            if (!isVirtualWorkspace) {
                val f = File(clean)
                val canonical = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
                if (!canonical.startsWith(rootCanonicalPath)) {
                    throw SecurityException("Access denied: path traverses outside workspace root ('$path')")
                }
            }
        }

        // 2. Strip known virtual /workspace prefixes or legacy external paths
        val relative = when {
            clean.startsWith("/workspace/") -> clean.removePrefix("/workspace/")
            clean == "/workspace" -> ""
            clean.startsWith("workspace/") -> clean.removePrefix("workspace/")
            clean.contains("/files/workspace/") -> clean.substringAfter("/files/workspace/")
            clean.startsWith("/") -> {
                val f = File(clean)
                val canonical = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
                if (canonical.startsWith(rootCanonicalPath)) {
                    f.canonicalFile.relativeTo(rootDir).path
                } else {
                    throw SecurityException("Access denied: path traverses outside workspace root ('$path')")
                }
            }
            else -> clean
        }

        val target = if (relative.isBlank()) rootDir else File(rootDir, relative)
        val canonicalTarget = target.canonicalFile
        val targetCanonicalPath = canonicalTarget.canonicalPath

        // 3. Canonical Jail Verification
        val isInsideJail = targetCanonicalPath == rootCanonicalPath ||
                targetCanonicalPath.startsWith(rootCanonicalPath + File.separator)

        if (!isInsideJail) {
            throw SecurityException("Access denied: path traverses outside workspace root ('$path')")
        }

        // 4. Vault Safeguard: cannot target user's encrypted memory vault
        val vaultDir = File(appFilesDir, "vault").canonicalFile
        val vaultCanonicalPath = vaultDir.canonicalPath
        if (targetCanonicalPath == vaultCanonicalPath ||
            targetCanonicalPath.startsWith(vaultCanonicalPath + File.separator)) {
            throw SecurityException("Access denied: path targets protected vault ('$path')")
        }

        target.parentFile?.mkdirs()
        if (!canonicalTarget.exists()) {
            val fileName = canonicalTarget.name
            val bundledRef = File(appFilesDir, "skills/bit-workspace-shell/references/$fileName")
            if (bundledRef.exists() && bundledRef.isFile) {
                try { bundledRef.copyTo(canonicalTarget, overwrite = true) } catch (_: Exception) {}
            }
        }
        return canonicalTarget
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
