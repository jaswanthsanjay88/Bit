package com.bit.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Security unit tests for WorkspacePaths canonical jail.
 * Verifies that directory traversals, absolute escapes, symlink attacks,
 * and vault access attempts are strictly blocked with SecurityException.
 */
class WorkspaceSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appFilesDir: File
    private lateinit var vaultDir: File
    private val workspaceRootName = "secure_test_ws"

    @Before
    fun setup() {
        appFilesDir = tempFolder.newFolder("bit_files")
        vaultDir = File(appFilesDir, "vault").apply { mkdirs() }
        File(vaultDir, "master.key").writeText("secret-vault-key-bytes")
    }

    @Test(expected = SecurityException::class)
    fun testTraversal_parentDirectory_throwsSecurityException() {
        WorkspacePaths.resolveFile(appFilesDir, "../", workspaceRootName)
    }

    @Test(expected = SecurityException::class)
    fun testTraversal_vaultEscape_throwsSecurityException() {
        WorkspacePaths.resolveFile(appFilesDir, "../../vault/master.key", workspaceRootName)
    }

    @Test(expected = SecurityException::class)
    fun testTraversal_virtualWorkspaceVaultEscape_throwsSecurityException() {
        WorkspacePaths.resolveFile(appFilesDir, "/workspace/../../vault/master.key", workspaceRootName)
    }

    @Test(expected = SecurityException::class)
    fun testTraversal_systemFile_throwsSecurityException() {
        WorkspacePaths.resolveFile(appFilesDir, "/etc/passwd", workspaceRootName)
    }

    @Test(expected = SecurityException::class)
    fun testTraversal_absoluteHostPath_throwsSecurityException() {
        WorkspacePaths.resolveFile(appFilesDir, "/data/data/com.bit.agent/databases", workspaceRootName)
    }

    @Test(expected = SecurityException::class)
    fun testTraversal_nestedEscape_throwsSecurityException() {
        WorkspacePaths.resolveFile(appFilesDir, "subdir/../../../escaped.txt", workspaceRootName)
    }

    @Test
    fun testTraversal_symlinkEscape_throwsSecurityException() {
        val rootDir = WorkspacePaths.root(appFilesDir, workspaceRootName)
        val outsideSecret = File(appFilesDir, "outside_secret.txt").apply { writeText("outside") }
        val linkFile = File(rootDir, "link_to_outside")

        try {
            Files.createSymbolicLink(linkFile.toPath(), outsideSecret.toPath())
            // If the symlink was created (supported by OS), resolveFile MUST detect target outside jail
            WorkspacePaths.resolveFile(appFilesDir, "link_to_outside", workspaceRootName)
            org.junit.Assert.fail("Expected SecurityException on symlink escape")
        } catch (_: SecurityException) {
            // Success: blocked
        } catch (_: java.lang.UnsupportedOperationException) {
            // Symlink creation not supported on this test platform, test passes
        } catch (_: java.nio.file.FileSystemException) {
            // Privilege required for symlinks on Windows, test passes
        }
    }

    @Test
    fun testAllowedPath_validFileInsideWorkspace_succeeds() {
        val resolved = WorkspacePaths.resolveFile(appFilesDir, "portfolio.html", workspaceRootName)
        val root = WorkspacePaths.root(appFilesDir, workspaceRootName)
        assertEquals(File(root, "portfolio.html").canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun testAllowedPath_validNestedFile_succeeds() {
        val resolved = WorkspacePaths.resolveFile(appFilesDir, "src/components/Header.kt", workspaceRootName)
        val root = WorkspacePaths.root(appFilesDir, workspaceRootName)
        assertEquals(File(root, "src/components/Header.kt").canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun testAllowedPath_virtualWorkspacePath_succeeds() {
        val resolved = WorkspacePaths.resolveFile(appFilesDir, "/workspace/index.html", workspaceRootName)
        val root = WorkspacePaths.root(appFilesDir, workspaceRootName)
        assertEquals(File(root, "index.html").canonicalPath, resolved.canonicalPath)
    }
}
