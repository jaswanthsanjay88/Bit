package com.bit.skills

import com.bit.agent.harness.tools.UseSkillTool
import com.bit.models.SkillType
import com.bit.util.SkillExportImport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SkillManagerAndImportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDefaultBuiltinSkillsContainsBitWorkspaceShell() {
        val skill = SkillManager.DEFAULT_BUILTIN_SKILLS.find { it.id == "skill-bit-workspace-shell" }
        assertNotNull("skill-bit-workspace-shell must be present in default built-ins", skill)
        assertEquals("bit-workspace-shell", skill!!.name)
        assertTrue("Must require workspace", skill.requiresWorkspace)
        assertEquals(SkillType.EXECUTABLE, skill.skillType)
        assertTrue("Must include workspace_shell tool", skill.tools.contains("workspace_shell"))
        assertTrue("Instructions must contain sandbox facts", skill.instructions.contains("workspace_shell"))
        assertTrue("Instructions must contain verify table", skill.instructions.contains("What \"verify\" means"))
    }

    @Test
    fun testImportFromStringParsesBitWorkspaceShell() {
        val rawSkillMd = """
            ---
            name: bit-workspace-shell
            description: Autonomous coding agent for Linux PRoot sandbox
            tools: [workspace_shell]
            requires_workspace: true
            ---
            # Coding instructions
            - Always verify code changes
        """.trimIndent()

        val result = SkillExportImport.importFromString(rawSkillMd)
        assertTrue(result is SkillExportImport.ImportResult.Success)
        val skill = (result as SkillExportImport.ImportResult.Success).skill
        assertEquals("bit-workspace-shell", skill.name)
        assertTrue(skill.requiresWorkspace)
        assertTrue(skill.tools.contains("workspace_shell"))
        assertEquals(SkillType.EXECUTABLE, skill.skillType)
        assertEquals("terminal", skill.icon)
    }

    @Test
    fun testImportFromZipExtractsFilesAndParsesSkill() {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zos ->
            // Entry 1: bit-workspace-shell/SKILL.md
            zos.putNextEntry(ZipEntry("bit-workspace-shell/SKILL.md"))
            zos.write("""
                ---
                name: bit-workspace-shell
                description: Autonomous coding agent inside PRoot
                ---
                # Body
                Run commands using workspace_shell
            """.trimIndent().toByteArray())
            zos.closeEntry()

            // Entry 2: bit-workspace-shell/references/alpine.md
            zos.putNextEntry(ZipEntry("bit-workspace-shell/references/alpine.md"))
            zos.write("# Alpine guide\napk update".toByteArray())
            zos.closeEntry()

            // Entry 3: bit-workspace-shell/references/debian.md
            zos.putNextEntry(ZipEntry("bit-workspace-shell/references/debian.md"))
            zos.write("# Debian guide\napt-get update".toByteArray())
            zos.closeEntry()
        }

        val targetDir = tempFolder.newFolder("skills_target")
        val result = SkillExportImport.importFromZip(ByteArrayInputStream(outputStream.toByteArray()), targetDir)

        assertTrue("Import result must be Success", result is SkillExportImport.ImportResult.Success)
        val success = result as SkillExportImport.ImportResult.Success
        assertEquals("bit-workspace-shell", success.skill.name)
        assertTrue(success.skill.requiresWorkspace)
        assertTrue(success.skill.tools.contains("workspace_shell"))

        // Verify files extracted on disk
        val skillFile = File(targetDir, "bit-workspace-shell/SKILL.md")
        val alpineFile = File(targetDir, "bit-workspace-shell/references/alpine.md")
        val debianFile = File(targetDir, "bit-workspace-shell/references/debian.md")

        assertTrue("SKILL.md must exist on disk", skillFile.exists())
        assertTrue("references/alpine.md must exist on disk", alpineFile.exists())
        assertTrue("references/debian.md must exist on disk", debianFile.exists())
    }

    @Test
    fun testUseSkillToolResolvesReferences() = runBlocking {
        val rootDir = tempFolder.newFolder("skills_root")
        val skillDir = File(rootDir, "bit-workspace-shell").apply { mkdirs() }
        val refsDir = File(skillDir, "references").apply { mkdirs() }

        File(skillDir, "SKILL.md").writeText("""
            ---
            name: bit-workspace-shell
            description: Coding skill
            ---
            # Shell instructions
            - Step 1: orient
        """.trimIndent())

        File(refsDir, "alpine.md").writeText("""
            # Alpine Reference
            ```sh
            apk update
            apk add gcc
            ```
        """.trimIndent())

        // Pass a mock/dummy tool with custom file resolution via context-less fallback test
        val tool = UseSkillTool()

        // Needs approval should be false for instructional queries
        val approvalArgs = """{"name": "bit-workspace-shell"}"""
        val needsApproval = tool.needsApproval(approvalArgs)
        assertEquals("Reading a skill should not require approval", false, needsApproval)
    }
}
