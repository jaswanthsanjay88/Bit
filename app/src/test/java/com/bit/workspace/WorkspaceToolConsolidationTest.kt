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
 * Tests verifying the consolidation of file listing and document reading
 * into the canonical workspace root.
 */
class WorkspaceToolConsolidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appFilesDir: File
    private lateinit var canonicalRoot: File

    @Before
    fun setup() {
        appFilesDir = tempFolder.newFolder("app_files")
        canonicalRoot = WorkspacePaths.root(appFilesDir, "test_root")
    }

    @Test
    fun testListFiles_nonRecursive_listsRootFilesOnly() {
        File(canonicalRoot, "file1.txt").writeText("content 1")
        File(canonicalRoot, "file2.py").writeText("print('hello')")
        val subDir = File(canonicalRoot, "src").apply { mkdirs() }
        File(subDir, "nested.kt").writeText("val x = 1")

        val files = canonicalRoot.listFiles()?.map { it.name }.orEmpty()
        assertEquals(3, files.size)
        assertTrue(files.contains("file1.txt"))
        assertTrue(files.contains("file2.py"))
        assertTrue(files.contains("src"))
        assertFalse(files.contains("nested.kt"))
    }

    @Test
    fun testListFiles_recursive_listsAllDescendants() {
        File(canonicalRoot, "file1.txt").writeText("content 1")
        val subDir = File(canonicalRoot, "src").apply { mkdirs() }
        File(subDir, "nested.kt").writeText("val x = 1")
        val deepDir = File(subDir, "deep").apply { mkdirs() }
        File(deepDir, "deep.txt").writeText("deep content")

        val relPaths = canonicalRoot.walkTopDown()
            .filter { it != canonicalRoot }
            .map { it.relativeTo(canonicalRoot).path.replace('\\', '/') }
            .toList()

        assertTrue(relPaths.contains("file1.txt"))
        assertTrue(relPaths.contains("src"))
        assertTrue(relPaths.contains("src/nested.kt"))
        assertTrue(relPaths.contains("src/deep"))
        assertTrue(relPaths.contains("src/deep/deep.txt"))
    }

    @Test
    fun testResolveFile_relativeDocumentResolution() {
        val docFile = File(canonicalRoot, "docs/readme.md").apply {
            parentFile?.mkdirs()
            writeText("# Workspace Readme")
        }

        val resolved = WorkspacePaths.resolveFile(appFilesDir, "docs/readme.md", "test_root")
        assertEquals(docFile.canonicalPath, resolved.canonicalPath)
        assertTrue(resolved.exists())
        assertEquals("# Workspace Readme", resolved.readText())
    }

    @Test
    fun testResolveFile_virtualWorkspacePrefixResolution() {
        val reportFile = File(canonicalRoot, "reports/analysis.txt").apply {
            parentFile?.mkdirs()
            writeText("Analysis report data")
        }

        val resolved = WorkspacePaths.resolveFile(appFilesDir, "/workspace/reports/analysis.txt", "test_root")
        assertEquals(reportFile.canonicalPath, resolved.canonicalPath)
        assertTrue(resolved.exists())
        assertEquals("Analysis report data", resolved.readText())
    }

    @Test(expected = SecurityException::class)
    fun testResolveFile_traversalRejected() {
        WorkspacePaths.resolveFile(appFilesDir, "../outside.txt", "test_root")
    }
}
