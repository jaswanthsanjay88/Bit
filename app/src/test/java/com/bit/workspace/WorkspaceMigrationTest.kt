package com.bit.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests verifying one-time, non-destructive migration of legacy files
 * from BIT_files and external workspace into the canonical PRoot workspace root.
 */
class WorkspaceMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appFilesDir: File
    private lateinit var legacyBitFilesDir: File
    private lateinit var legacyExternalDir: File
    private lateinit var canonicalRoot: File

    @Before
    fun setup() {
        appFilesDir = tempFolder.newFolder("app_files")
        legacyBitFilesDir = File(appFilesDir, "BIT_files").apply { mkdirs() }
        legacyExternalDir = tempFolder.newFolder("external_workspace")
        canonicalRoot = WorkspacePaths.root(appFilesDir, "main_workspace")
    }

    @Test
    fun testMigration_copiesFilesFromLegacyBitFilesToTargetWorkspace() {
        val legacyFile1 = File(legacyBitFilesDir, "project.json").apply {
            writeText("{\"name\": \"test\"}")
        }
        val subDir = File(legacyBitFilesDir, "nested").apply { mkdirs() }
        val legacyFile2 = File(subDir, "readme.txt").apply {
            writeText("nested legacy file")
        }

        val result = WorkspaceMigrationHelper.migrate(
            appFilesDir = appFilesDir,
            legacyDirs = listOf(legacyBitFilesDir),
            rootName = "main_workspace"
        )

        assertEquals(2, result.migratedCount)
        assertEquals(0, result.skippedCount)

        val targetFile1 = File(canonicalRoot, "project.json")
        val targetFile2 = File(canonicalRoot, "nested/readme.txt")

        assertTrue(targetFile1.exists())
        assertEquals("{\"name\": \"test\"}", targetFile1.readText())

        assertTrue(targetFile2.exists())
        assertEquals("nested legacy file", targetFile2.readText())
    }

    @Test
    fun testMigration_neverOverwritesExistingFiles() {
        val targetExisting = File(canonicalRoot, "portfolio.html").apply {
            writeText("PROOT NEW PORTFOLIO")
        }

        val legacyConflict = File(legacyBitFilesDir, "portfolio.html").apply {
            writeText("OLD BIT_FILES PORTFOLIO")
        }

        val legacyNew = File(legacyBitFilesDir, "new_file.md").apply {
            writeText("brand new content")
        }

        val result = WorkspaceMigrationHelper.migrate(
            appFilesDir = appFilesDir,
            legacyDirs = listOf(legacyBitFilesDir),
            rootName = "main_workspace"
        )

        assertEquals(1, result.migratedCount)
        assertEquals(1, result.skippedCount)

        // Verify the existing file was NOT overwritten
        assertEquals("PROOT NEW PORTFOLIO", targetExisting.readText())

        // Verify the new file was copied
        val targetNew = File(canonicalRoot, "new_file.md")
        assertTrue(targetNew.exists())
        assertEquals("brand new content", targetNew.readText())
    }

    @Test
    fun testMigration_combinesMultipleLegacyDirectories() {
        File(legacyBitFilesDir, "file_from_bit_files.txt").writeText("bit files content")
        File(legacyExternalDir, "file_from_external.txt").writeText("external content")

        val result = WorkspaceMigrationHelper.migrate(
            appFilesDir = appFilesDir,
            legacyDirs = listOf(legacyBitFilesDir, legacyExternalDir),
            rootName = "main_workspace"
        )

        assertEquals(2, result.migratedCount)
        assertEquals(0, result.skippedCount)

        val target1 = File(canonicalRoot, "file_from_bit_files.txt")
        val target2 = File(canonicalRoot, "file_from_external.txt")

        assertTrue(target1.exists())
        assertEquals("bit files content", target1.readText())
        assertTrue(target2.exists())
        assertEquals("external content", target2.readText())
    }

    @Test
    fun testMigration_nonExistentLegacyDirs_completesSafely() {
        val nonExistent = File(appFilesDir, "does_not_exist")

        val result = WorkspaceMigrationHelper.migrate(
            appFilesDir = appFilesDir,
            legacyDirs = listOf(nonExistent),
            rootName = "main_workspace"
        )

        assertEquals(0, result.migratedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0L, result.totalBytes)
    }
}
