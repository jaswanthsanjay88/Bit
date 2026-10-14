package com.bit.agent.harness.coding

import com.bit.agent.harness.model.ToolObservation
import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import com.bit.plugins.GbnfGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Comprehensive Ablation & Unit Test Suite for the BIT Coding Agent Harness.
 * Tests component behaviors across Tool Tiers, Mode Allowlists, Shadow-Git Checkpoints,
 * Line-Slice Editing, Deterministic Pruning, and Structured Test Parsing.
 */
class CodingAgentAblationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- 1. Mode Allowlists & Policies ---

    @Test
    fun testModeToolPolicy_enforcesReadOnlyInAskMode() {
        assertTrue(ModeToolPolicy.isToolAllowed("read_slice", AgentExecutionMode.ASK))
        assertTrue(ModeToolPolicy.isToolAllowed("search_code", AgentExecutionMode.ASK))
        assertTrue(ModeToolPolicy.isToolAllowed("list_dir", AgentExecutionMode.ASK))
        assertTrue(ModeToolPolicy.isToolAllowed("git_diff", AgentExecutionMode.ASK))
        assertTrue(ModeToolPolicy.isToolAllowed("outline_symbols", AgentExecutionMode.ASK))

        // Mutation tools must be strictly forbidden in ASK mode
        assertFalse(ModeToolPolicy.isToolAllowed("edit_file_slice", AgentExecutionMode.ASK))
        assertFalse(ModeToolPolicy.isToolAllowed("workspace_write_file", AgentExecutionMode.ASK))
        assertFalse(ModeToolPolicy.isToolAllowed("workspace_edit_file", AgentExecutionMode.ASK))
    }

    @Test
    fun testModeToolPolicy_allowsPlanningToolsInPlanMode() {
        assertTrue(ModeToolPolicy.isToolAllowed("read_slice", AgentExecutionMode.PLAN))
        assertTrue(ModeToolPolicy.isToolAllowed("todo_write", AgentExecutionMode.PLAN))

        // Modification tools still forbidden in PLAN mode
        assertFalse(ModeToolPolicy.isToolAllowed("edit_file_slice", AgentExecutionMode.PLAN))
        assertFalse(ModeToolPolicy.isToolAllowed("workspace_write_file", AgentExecutionMode.PLAN))
    }

    @Test
    fun testModeToolPolicy_allowsAllToolsInActMode() {
        assertTrue(ModeToolPolicy.isToolAllowed("edit_file_slice", AgentExecutionMode.ACT))
        assertTrue(ModeToolPolicy.isToolAllowed("workspace_write_file", AgentExecutionMode.ACT))
        assertTrue(ModeToolPolicy.isToolAllowed("workspace_shell", AgentExecutionMode.ACT))
    }

    // --- 2. Tool Tiers & Dynamic Promotion ---

    @Test
    fun testToolPromotionPolicy_promotesOnRepositorySizeOrMisses() {
        // Small repo with no misses -> Tier 0
        assertFalse(ToolPromotionPolicy.shouldPromote(fileCount = 5, consecutiveSearchFailures = 0))

        // Large repo (>15 files) -> Promoted to Tier 1
        assertTrue(ToolPromotionPolicy.shouldPromote(fileCount = 20, consecutiveSearchFailures = 0))

        // Small repo with repeated search misses (>=2) -> Promoted to Tier 1
        assertTrue(ToolPromotionPolicy.shouldPromote(fileCount = 5, consecutiveSearchFailures = 2))

        // Explicit user intent -> Promoted to Tier 1
        assertTrue(ToolPromotionPolicy.shouldPromote(fileCount = 2, userExplicitSearchIntent = true))
    }

    // --- 3. Shadow Git Checkpoints & Rollbacks ---

    @Test
    fun testShadowGitManager_snapshotsAndRollsBackCleanly() {
        val projectDir = tempFolder.newFolder("toy_project")
        val mainFile = File(projectDir, "main.py").apply {
            writeText("def calculate():\n    return 42\n")
        }

        ShadowGitManager.init(projectDir)
        val snapshotId = ShadowGitManager.createSnapshot(projectDir, "turn_1", "Initial state")
        assertTrue(snapshotId.isNotBlank())

        // Simulate destructive edit
        mainFile.writeText("def calculate():\n    return -999\n")
        assertEquals("def calculate():\n    return -999\n", mainFile.readText())

        // Perform rollback
        val rollbackSuccess = ShadowGitManager.rollback(projectDir, snapshotId)
        assertTrue(rollbackSuccess)

        // Verify restoration of original file
        val restoredText = mainFile.readText()
        assertTrue(restoredText.contains("return 42"))
        assertFalse(restoredText.contains("-999"))
    }

    // --- 4. Line Slice Reading & Editing Tools ---

    @Test
    fun testReadSliceTool_returnsNumberedLines() = runBlocking {
        val root = tempFolder.newFolder("workspace")
        val codeFile = File(root, "Calculator.kt").apply {
            writeText("package com.test\n\nfun add(a: Int, b: Int): Int {\n    return a + b\n}\n")
        }

        val tool = ReadSliceTool(null)
        val jsonArgs = "{\"path\":\"${codeFile.absolutePath.replace('\\', '/')}\",\"start_line\":3,\"end_line\":5}"
        val obs = tool.execute(jsonArgs)

        assertTrue(obs.isSuccess)
        assertTrue(obs.payload?.contains("3: fun add(a: Int, b: Int): Int {") == true)
        assertTrue(obs.payload?.contains("4:     return a + b") == true)
    }

    @Test
    fun testEditFileSliceTool_updatesLineRangeAndToleratesWhitespace() = runBlocking {
        val root = tempFolder.newFolder("workspace_edit")
        val targetFile = File(root, "Service.kt").apply {
            writeText("class Service {\n    fun execute() {\n        val x = 1\n        val y = 2\n    }\n}")
        }
        FileReadTracker.recordRead(targetFile)

        val tool = EditFileSliceTool(null, ShadowGitManager)
        val args = """
            {
                "path": "${targetFile.absolutePath.replace('\\', '/')}",
                "start_line": 3,
                "end_line": 4,
                "expected_anchor": "val x = 1",
                "new_content": "        val x = 10\n        val y = 20\n        val z = 30"
            }
        """.trimIndent()

        val obs = tool.execute(args)
        assertTrue(obs.isSuccess)

        val updated = targetFile.readText()
        assertTrue(updated.contains("val x = 10"))
        assertTrue(updated.contains("val z = 30"))
        assertFalse(updated.contains("val x = 1\n"))
    }

    @Test
    fun testEditFileSliceTool_rejectsOutOfBoundsWithHelpfulPreview() = runBlocking {
        val root = tempFolder.newFolder("workspace_oob")
        val shortFile = File(root, "Short.kt").apply {
            writeText("fun a() = 1\nfun b() = 2\n")
        }
        FileReadTracker.recordRead(shortFile)

        val tool = EditFileSliceTool(null, ShadowGitManager)
        val args = """
            {
                "path": "${shortFile.absolutePath.replace('\\', '/')}",
                "start_line": 50,
                "end_line": 60,
                "new_content": "fun c() = 3"
            }
        """.trimIndent()

        val obs = tool.execute(args)
        assertFalse(obs.isSuccess)
        assertTrue(obs.summary.contains("out of bounds"))
        assertTrue(obs.recoveryHint?.contains("contain") == true)
    }

    // --- 5. Structured Test Result Parser ("The Numbers Path") ---

    @Test
    fun testStructuredTestParser_parsesPytestFailures() {
        val rawPytest = """
            ============================= test session starts ==============================
            collected 12 items

            tests/test_math.py .........F..                                          [100%]

            =================================== FAILURES ===================================
            _________________________________ test_divide __________________________________
            FAILED tests/test_math.py::test_divide - ZeroDivisionError: division by zero
            ========================= 1 failed, 11 passed in 0.35s =========================
        """.trimIndent()

        val result = StructuredTestParser.parse(rawPytest)
        assertEquals("pytest", result.framework)
        assertEquals(11, result.passed)
        assertEquals(1, result.failed)
        assertFalse(result.isSuccess)
        assertEquals(1, result.failures.size)
        assertEquals("test_divide", result.failures.first().testName)

        val payload = result.toModelPayload()
        assertTrue(payload.contains("\"passed\": 11"))
        assertTrue(payload.contains("\"failed\": 1"))
        assertTrue(payload.contains("ZeroDivisionError"))
    }

    @Test
    fun testStructuredTestParser_parsesGradleAndJest() {
        val rawGradle = "15 tests completed, 2 failed, 1 skipped\nBUILD FAILED in 3s"
        val gradleResult = StructuredTestParser.parse(rawGradle)
        assertEquals("gradle", gradleResult.framework)
        assertEquals(12, gradleResult.passed)
        assertEquals(2, gradleResult.failed)
        assertEquals(1, gradleResult.skipped)

        val rawJest = "Tests:  1 failed, 9 passed, 10 total"
        val jestResult = StructuredTestParser.parse(rawJest)
        assertEquals("jest", jestResult.framework)
        assertEquals(9, jestResult.passed)
        assertEquals(1, jestResult.failed)
    }

    // --- 6. Context Pruning & Pinned Invariants ---

    @Test
    fun testContextPruningManager_stubsOldToolObservationsDeterministically() {
        val verboseObservation = """
            User: Fix tests.
            [Tool: read_slice]
            1: line 1
            2: line 2
            3: line 3
            4: line 4
            5: line 5
            6: line 6
            7: line 7
            8: line 8
            9: line 9
            10: line 10
            11: line 11
            12: line 12
            [/Tool]
        """.trimIndent()

        val stubbed = ContextPruningManager.stubOldToolObservations(verboseObservation, currentTurn = 3)
        assertTrue(stubbed.contains("[Output elided:"))
        assertFalse(stubbed.contains("12: line 12"))
    }

    @Test
    fun testContextPruningManager_reinjectsPinnedInvariants() {
        val invariants = PinnedInvariants(
            goal = "Fix division by zero in calculate()",
            touchedFiles = setOf("src/Math.kt"),
            failingTests = listOf("test_divide")
        )

        val strippedContext = "Here is the summary of recent changes."
        val protected = ContextPruningManager.ensureInvariantsPreserved(strippedContext, invariants)

        assertTrue(protected.contains("PINNED INVARIANTS"))
        assertTrue(protected.contains("Math.kt"))
        assertTrue(protected.contains("test_divide"))
    }

    // --- 7. Stack Detector & Project Grounding ---

    @Test
    fun testStackDetector_detectsPythonAndGradle() {
        val pythonDir = tempFolder.newFolder("py_toy").apply {
            File(this, "pyproject.toml").writeText("[tool.pytest]")
        }
        val pyStack = StackDetector.detect(pythonDir)
        assertEquals("Python", pyStack.stackName)
        assertEquals("pytest", pyStack.defaultTestCommand)

        val gradleDir = tempFolder.newFolder("gradle_toy").apply {
            File(this, "build.gradle.kts").writeText("// gradle")
            File(this, "gradlew").writeText("#!/bin/sh")
        }
        val gradleStack = StackDetector.detect(gradleDir)
        assertEquals("Gradle", gradleStack.stackName)
        assertTrue(gradleStack.defaultTestCommand.contains("gradlew test"))
    }

    // --- 8. Dynamic GBNF Grammar Generation ---

    @Test
    fun testGbnfGenerator_constrainsGrammarByMode() {
        val allTools = listOf("read_slice", "search_code", "list_dir", "edit_file_slice", "workspace_write_file")
        val askGrammar = GbnfGenerator.generateForModeAndTier(allTools, AgentExecutionMode.ASK, ToolTier.TIER_0_CORE)

        // In ASK mode, read tools are in the grammar, write tools MUST NOT be
        assertTrue(askGrammar.contains("read_slice"))
        assertFalse(askGrammar.contains("edit_file_slice"))
        assertFalse(askGrammar.contains("workspace_write_file"))

        val actGrammar = GbnfGenerator.generateForModeAndTier(allTools, AgentExecutionMode.ACT, ToolTier.TIER_0_CORE)
        assertTrue(actGrammar.contains("edit_file_slice"))
    }
}
