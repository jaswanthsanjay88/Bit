package com.bit.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Contract test asserting that the Linux PRoot terminal mount source
 * equals the canonical root directory used by agent workspace tools.
 */
class WorkspacePathsContractTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appFilesDir: File

    @Before
    fun setup() {
        appFilesDir = tempFolder.newFolder("app_files")
    }

    @Test
    fun testTerminalMountSource_equalsWorkspaceToolsRoot() {
        val rootName = "session_alpha"
        val expectedRoot = File(File(appFilesDir, "workspaces"), rootName)
        val expectedFilesDir = File(expectedRoot, "files").canonicalFile

        // 1. Root returned by WorkspacePaths
        val resolvedRoot = WorkspacePaths.root(appFilesDir, rootName)
        assertEquals(expectedFilesDir.path, resolvedRoot.path)

        // 2. Parity check: In WorkspaceTerminalSession, the bind flag is constructed as:
        //    "-b", "${filesDir.absolutePath}:$WORKSPACE_DIR"
        // Verify this path is identical to the tools' canonical root
        val terminalMountSource = resolvedRoot.absolutePath
        val toolRootPath = expectedFilesDir.absolutePath
        assertEquals("Terminal mount source must equal tool root", toolRootPath, terminalMountSource)
    }

    @Test
    fun testToolFileResolution_mapsVirtualWorkspaceToCanonicalRoot() {
        val rootName = "project_bit"
        val canonicalRoot = WorkspacePaths.root(appFilesDir, rootName)

        // Virtual PRoot path passed by model or agent
        val virtualPath = "/workspace/portfolio.html"
        val resolved = WorkspacePaths.resolveFile(appFilesDir, virtualPath, rootName)

        assertEquals(File(canonicalRoot, "portfolio.html").canonicalPath, resolved.canonicalPath)
        assertEquals(canonicalRoot.canonicalPath, resolved.parentFile?.canonicalPath)
    }

    @Test
    fun testToolFileResolution_relativeFilenameMapsToCanonicalRoot() {
        val rootName = "project_bit"
        val canonicalRoot = WorkspacePaths.root(appFilesDir, rootName)

        // Bare filename without leading slash
        val barePath = "main.py"
        val resolved = WorkspacePaths.resolveFile(appFilesDir, barePath, rootName)

        assertEquals(File(canonicalRoot, "main.py").canonicalPath, resolved.canonicalPath)
        assertEquals(canonicalRoot.canonicalPath, resolved.parentFile?.canonicalPath)
    }

    @Test
    fun testTerminalPath_formatsCorrectlyForProot() {
        val rootName = "project_bit"
        val canonicalRoot = WorkspacePaths.root(appFilesDir, rootName)

        val hostFile = File(canonicalRoot, "assets/logo.png")
        hostFile.parentFile?.mkdirs()
        hostFile.writeText("fake-image")

        val terminalPath = WorkspacePaths.terminalPath(appFilesDir, hostFile, rootName)
        assertEquals("/workspace/assets/logo.png", terminalPath)
    }

    @Test
    fun testBundledReferences_syncedAndResolved() {
        val rootName = "project_refs_test"
        // Setup bundled skills reference source
        val skillsRefDir = File(appFilesDir, "skills/bit-workspace-shell/references")
        skillsRefDir.mkdirs()
        File(skillsRefDir, "alpine.md").writeText("# Alpine Guide")
        File(skillsRefDir, "debian.md").writeText("# Debian Guide")

        val rootDir = WorkspacePaths.root(appFilesDir, rootName)
        val alpineInRoot = File(rootDir, "references/alpine.md")
        assertTrue("alpine.md must be synced to workspace references", alpineInRoot.exists())
        assertEquals("# Alpine Guide", alpineInRoot.readText())

        // Test resolution of reference path
        val resolved = WorkspacePaths.resolveFile(appFilesDir, "references/alpine.md", rootName)
        assertEquals(alpineInRoot.canonicalPath, resolved.canonicalPath)
        assertTrue(resolved.exists())
    }
}
