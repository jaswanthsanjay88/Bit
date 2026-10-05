package com.bit.workspace

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Helper to perform a one-time, non-destructive migration of existing user
 * files from legacy locations (filesDir/BIT_files and getExternalFilesDir("workspace"))
 * into the canonical Linux PRoot workspace root.
 *
 * Invariant: NEVER overwrite an existing file in the target workspace root.
 */
object WorkspaceMigrationHelper {
    private const val TAG = "WorkspaceMigration"
    private const val PREFS_NAME = "workspace_migration_prefs"
    private const val KEY_MIGRATED = "has_migrated_legacy_workspace"

    data class MigrationResult(
        val migratedCount: Int,
        val skippedCount: Int,
        val totalBytes: Long
    )

    /**
     * Executes one-time migration on the device using SharedPreferences guard.
     */
    fun migrateIfNeeded(context: Context, rootName: String? = null): MigrationResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) {
            return MigrationResult(0, 0, 0L)
        }

        val appFilesDir = context.applicationContext.filesDir
        val externalDir = context.applicationContext.getExternalFilesDir(null)
        val legacyExternalWorkspace = if (externalDir != null) File(externalDir, "workspace") else null

        val result = migrate(
            appFilesDir = appFilesDir,
            legacyDirs = listOfNotNull(
                File(appFilesDir, "BIT_files"),
                legacyExternalWorkspace
            ),
            rootName = rootName
        )

        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        Log.i(TAG, "Legacy workspace migration completed: $result")
        return result
    }

    /**
     * Core non-destructive migration logic (pure Java/Kotlin File API, fully unit-testable).
     */
    fun migrate(
        appFilesDir: File,
        legacyDirs: List<File>,
        rootName: String? = null
    ): MigrationResult {
        val targetRoot = WorkspacePaths.root(appFilesDir, rootName)
        var migrated = 0
        var skipped = 0
        var bytesCopied = 0L

        for (legacyDir in legacyDirs) {
            if (!legacyDir.exists() || !legacyDir.isDirectory) continue

            legacyDir.walkTopDown().forEach { source ->
                if (source.isFile) {
                    val relativePath = source.relativeTo(legacyDir).path
                    val targetFile = File(targetRoot, relativePath)

                    if (!targetFile.exists()) {
                        try {
                            targetFile.parentFile?.mkdirs()
                            source.copyTo(targetFile, overwrite = false)
                            migrated++
                            bytesCopied += targetFile.length()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to migrate file '${source.name}': ${e.message}")
                            skipped++
                        }
                    } else {
                        // Non-destructive: target already exists, do not overwrite
                        skipped++
                    }
                }
            }
        }

        return MigrationResult(
            migratedCount = migrated,
            skippedCount = skipped,
            totalBytes = bytesCopied
        )
    }
}
