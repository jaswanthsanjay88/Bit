package com.bit.agent.harness.coding

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured Test and Build Output (The "Numbers Path").
 * Instead of dumping 500+ lines of raw compiler or runner logs into the model's context window,
 * test output is parsed deterministically into structured metrics and surgical failure snippets.
 */
data class TestRunResult(
    val framework: String,
    val passed: Int,
    val failed: Int,
    val errors: Int = 0,
    val skipped: Int = 0,
    val total: Int = passed + failed + errors + skipped,
    val isSuccess: Boolean = failed == 0 && errors == 0 && total > 0,
    val failures: List<TestFailureDetail> = emptyList(),
    val summary: String = "",
    val rawLogExcerpt: String = ""
) {
    /**
     * Compact, token-lean format presented to the 3B-7B model (under 150 tokens).
     */
    fun toModelPayload(): String {
        val root = JSONObject()
        root.put("framework", framework)
        root.put("passed", passed)
        root.put("failed", failed)
        root.put("errors", errors)
        root.put("total", total)
        root.put("status", if (isSuccess) "PASSED" else "FAILED")

        if (failures.isNotEmpty()) {
            val failureArr = JSONArray()
            failures.take(5).forEach { f ->
                val item = JSONObject()
                item.put("test", f.testName)
                f.file?.let { item.put("file", it) }
                f.line?.let { item.put("line", it) }
                item.put("message", f.message.take(200))
                failureArr.put(item)
            }
            root.put("failures", failureArr)
        }
        return root.toString(2)
    }
}

data class TestFailureDetail(
    val testName: String,
    val file: String? = null,
    val line: Int? = null,
    val message: String,
    val traceExcerpt: String? = null
)

object StructuredTestParser {

    private val PYTEST_SUMMARY_REGEX = Regex(
        """=+ (?:(\d+)\s+failed[,\s]*)?(?:(\d+)\s+passed[,\s]*)?(?:(\d+)\s+skipped[,\s]*)?(?:(\d+)\s+error[,\s]*)?.*in\s+[\d\.]+s\s*=+""",
        RegexOption.IGNORE_CASE
    )
    private val PYTEST_FAILURE_LINE = Regex("""^FAILED\s+([^:]+)::(\w+)\s*(?:-\s*(.*))?""")

    private val GRADLE_SUMMARY_REGEX = Regex(
        """(\d+)\s+tests completed,\s+(\d+)\s+failed(?:,\s+(\d+)\s+skipped)?""",
        RegexOption.IGNORE_CASE
    )
    private val GRADLE_FAILURE_LINE = Regex("""^([\w\.]+)\s*>\s*([\w]+)\s+FAILED""")

    private val JEST_SUMMARY_REGEX = Regex(
        """Tests:\s+(?:(\d+)\s+failed,\s+)?(?:(\d+)\s+passed,\s+)?(?:(\d+)\s+skipped,\s+)?(\d+)\s+total""",
        RegexOption.IGNORE_CASE
    )
    private val JEST_FAILURE_LINE = Regex("""✕\s+([^\(]+)(?:\s+\(([^:]+):(\d+):(\d+)\))?""")

    private val CARGO_SUMMARY_REGEX = Regex(
        """test result:\s+(FAILED|ok)\.\s+(\d+)\s+passed;\s+(\d+)\s+failed;\s+(\d+)\s+ignored""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Auto-detects test framework and parses raw terminal output.
     */
    fun parse(rawOutput: String): TestRunResult {
        val clean = rawOutput.trim()

        if (clean.contains("pytest") || PYTEST_SUMMARY_REGEX.containsMatchIn(clean)) {
            return parsePytest(clean)
        }
        if (clean.contains("tests completed") || clean.contains("Task :") || GRADLE_SUMMARY_REGEX.containsMatchIn(clean)) {
            return parseGradle(clean)
        }
        if (clean.contains("Jest") || JEST_SUMMARY_REGEX.containsMatchIn(clean)) {
            return parseJest(clean)
        }
        if (clean.contains("test result:") || CARGO_SUMMARY_REGEX.containsMatchIn(clean)) {
            return parseCargo(clean)
        }

        return parseGeneric(clean)
    }

    fun parsePytest(text: String): TestRunResult {
        var passed = 0
        var failed = 0
        var errors = 0
        var skipped = 0

        val lines = text.lines()
        val failures = mutableListOf<TestFailureDetail>()

        for (line in lines) {
            val match = PYTEST_SUMMARY_REGEX.find(line)
            if (match != null) {
                failed = match.groups[1]?.value?.toIntOrNull() ?: 0
                passed = match.groups[2]?.value?.toIntOrNull() ?: 0
                skipped = match.groups[3]?.value?.toIntOrNull() ?: 0
                errors = match.groups[4]?.value?.toIntOrNull() ?: 0
            }

            val failMatch = PYTEST_FAILURE_LINE.find(line.trim())
            if (failMatch != null) {
                val file = failMatch.groups[1]?.value
                val test = failMatch.groups[2]?.value ?: "unknown"
                val msg = failMatch.groups[3]?.value ?: "AssertionError"
                failures.add(TestFailureDetail(testName = test, file = file, message = msg))
            }
        }

        return TestRunResult(
            framework = "pytest",
            passed = passed,
            failed = failed,
            errors = errors,
            skipped = skipped,
            failures = failures,
            summary = "pytest: $passed passed, $failed failed",
            rawLogExcerpt = text.takeLast(1000)
        )
    }

    fun parseGradle(text: String): TestRunResult {
        var passed = 0
        var failed = 0
        var skipped = 0

        val lines = text.lines()
        val failures = mutableListOf<TestFailureDetail>()

        for (line in lines) {
            val match = GRADLE_SUMMARY_REGEX.find(line)
            if (match != null) {
                val completed = match.groups[1]?.value?.toIntOrNull() ?: 0
                failed = match.groups[2]?.value?.toIntOrNull() ?: 0
                skipped = match.groups[3]?.value?.toIntOrNull() ?: 0
                passed = (completed - failed - skipped).coerceAtLeast(0)
            }

            val failMatch = GRADLE_FAILURE_LINE.find(line.trim())
            if (failMatch != null) {
                val suite = failMatch.groups[1]?.value ?: ""
                val test = failMatch.groups[2]?.value ?: ""
                failures.add(TestFailureDetail(testName = "$suite.$test", message = "Test failed in Gradle execution"))
            }
        }

        if (failed == 0 && text.contains("BUILD SUCCESSFUL")) {
            if (passed == 0) passed = 1
        } else if (text.contains("BUILD FAILED") && failed == 0) {
            failed = 1
        }

        return TestRunResult(
            framework = "gradle",
            passed = passed,
            failed = failed,
            skipped = skipped,
            failures = failures,
            summary = "gradle: $passed passed, $failed failed",
            rawLogExcerpt = text.takeLast(1000)
        )
    }

    fun parseJest(text: String): TestRunResult {
        var passed = 0
        var failed = 0
        var skipped = 0
        var total = 0

        val lines = text.lines()
        val failures = mutableListOf<TestFailureDetail>()

        for (line in lines) {
            val match = JEST_SUMMARY_REGEX.find(line)
            if (match != null) {
                failed = match.groups[1]?.value?.toIntOrNull() ?: 0
                passed = match.groups[2]?.value?.toIntOrNull() ?: 0
                skipped = match.groups[3]?.value?.toIntOrNull() ?: 0
                total = match.groups[4]?.value?.toIntOrNull() ?: (passed + failed + skipped)
            }

            val failMatch = JEST_FAILURE_LINE.find(line.trim())
            if (failMatch != null) {
                val test = failMatch.groups[1]?.value?.trim() ?: "unknown"
                val file = failMatch.groups[2]?.value
                val lineNum = failMatch.groups[3]?.value?.toIntOrNull()
                failures.add(TestFailureDetail(testName = test, file = file, line = lineNum, message = "Jest assertion failure"))
            }
        }

        return TestRunResult(
            framework = "jest",
            passed = passed,
            failed = failed,
            skipped = skipped,
            failures = failures,
            summary = "jest: $passed passed, $failed failed",
            rawLogExcerpt = text.takeLast(1000)
        )
    }

    fun parseCargo(text: String): TestRunResult {
        var passed = 0
        var failed = 0
        var ignored = 0

        val lines = text.lines()
        val failures = mutableListOf<TestFailureDetail>()

        for (line in lines) {
            val match = CARGO_SUMMARY_REGEX.find(line)
            if (match != null) {
                passed = match.groups[2]?.value?.toIntOrNull() ?: 0
                failed = match.groups[3]?.value?.toIntOrNull() ?: 0
                ignored = match.groups[4]?.value?.toIntOrNull() ?: 0
            }
        }

        return TestRunResult(
            framework = "cargo",
            passed = passed,
            failed = failed,
            skipped = ignored,
            failures = failures,
            summary = "cargo test: $passed passed, $failed failed",
            rawLogExcerpt = text.takeLast(1000)
        )
    }

    fun parseGeneric(text: String): TestRunResult {
        val hasFailedMarker = text.contains("FAILED") || text.contains("Error:") || text.contains("Exception")
        val passed = if (!hasFailedMarker) 1 else 0
        val failed = if (hasFailedMarker) 1 else 0

        val errorLines = text.lines()
            .filter { it.contains("Error", ignoreCase = true) || it.contains("Fail", ignoreCase = true) }
            .take(3)
            .map { TestFailureDetail(testName = "generic_check", message = it.trim()) }

        return TestRunResult(
            framework = "generic",
            passed = passed,
            failed = failed,
            failures = errorLines,
            summary = if (hasFailedMarker) "Execution failed with errors" else "Execution completed",
            rawLogExcerpt = text.takeLast(800)
        )
    }
}
