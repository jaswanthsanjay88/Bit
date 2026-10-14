package com.bit.ui.screen.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceTerminalSessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var masterSkillsDir: File
    private lateinit var sessionSkillsDir: File

    @Before
    fun setup() {
        val root = tempFolder.newFolder("skills_test_root")
        masterSkillsDir = File(root, "skills")
        masterSkillsDir.mkdirs()

        // Create sample skills hierarchy:
        // skills/
        //   bit-workspace-shell/
        //     SKILL.md
        //   web-search/
        //     SKILL.md
        val shellDir = File(masterSkillsDir, "bit-workspace-shell").apply { mkdirs() }
        File(shellDir, "SKILL.md").writeText("# Workspace Shell Skill")

        val searchDir = File(masterSkillsDir, "web-search").apply { mkdirs() }
        File(searchDir, "SKILL.md").writeText("# Web Search Skill")

        val tmpDir = File(root, "tmp").apply { mkdirs() }
        sessionSkillsDir = File(tmpDir, "skills_session")
    }

    @Test
    fun testSafePrepareSessionSkillsDir_initialCopy() {
        safePrepareSessionSkillsDir(masterSkillsDir, sessionSkillsDir)

        assertTrue("Session skills dir must exist", sessionSkillsDir.exists())
        val copiedShell = File(sessionSkillsDir, "bit-workspace-shell/SKILL.md")
        assertTrue("Copied skill file must exist", copiedShell.exists())
        assertEquals("# Workspace Shell Skill", copiedShell.readText())

        // Skill file should be read-only
        assertFalse("Skill file should not be writable by proot", copiedShell.canWrite())

        // Parent directory must REMAIN writable so future cleanups/deletions succeed
        val shellDir = File(sessionSkillsDir, "bit-workspace-shell")
        assertTrue("Subdirectory must remain writable for cleanups", shellDir.canWrite())
        assertTrue("Session root directory must remain writable for cleanups", sessionSkillsDir.canWrite())
    }

    @Test
    fun testSafePrepareSessionSkillsDir_idempotentWhenFilesAlreadyExistAndAreReadOnly() {
        // Run once: sets up session skills where files are read-only
        safePrepareSessionSkillsDir(masterSkillsDir, sessionSkillsDir)
        val copiedShell = File(sessionSkillsDir, "bit-workspace-shell/SKILL.md")
        assertTrue(copiedShell.exists())
        assertFalse(copiedShell.canWrite())

        // Run second time: must NOT throw FileAlreadyExistsException even though destination files exist and are read-only!
        safePrepareSessionSkillsDir(masterSkillsDir, sessionSkillsDir)

        assertTrue("Session skills dir must still exist", sessionSkillsDir.exists())
        assertTrue("Copied skill file must exist after second sync", copiedShell.exists())
        assertEquals("# Workspace Shell Skill", copiedShell.readText())
    }

    @Test
    fun testSafePrepareSessionSkillsDir_repeatedRunsMultipleTimes() {
        // Simulate rapid tab switches or recompositions opening/closing terminal
        for (i in 1..5) {
            safePrepareSessionSkillsDir(masterSkillsDir, sessionSkillsDir)
            assertTrue("Iteration $i must succeed", sessionSkillsDir.exists())
            val file = File(sessionSkillsDir, "bit-workspace-shell/SKILL.md")
            assertTrue("File must exist on iteration $i", file.exists())
        }
    }

    @Test
    fun testSafeSessionCleanup_walkBottomUp() {
        safePrepareSessionSkillsDir(masterSkillsDir, sessionSkillsDir)
        assertTrue(sessionSkillsDir.exists())

        // Simulate onSessionFinished cleanup:
        sessionSkillsDir.walkBottomUp().forEach { file ->
            file.setWritable(true, false)
            file.delete()
        }

        assertFalse("Session skills directory should be fully deleted", sessionSkillsDir.exists())
    }
}
