package com.bit.agent.eval

import org.junit.Test
import java.io.File

class LiveNimEvalTest {

    @Test
    fun runLiveNimEvaluationsIfKeyPresent() {
        val apiKey = System.getenv("NIM_API_KEY") ?: System.getenv("NVIDIA_API_KEY")
        val results = LiveNimEvalRunner.runAll(
            apiKey = apiKey,
            outputDir = File("evals/baseline")
        )

        if (results.isEmpty()) {
            println("[LiveNimEvalTest] NIM_API_KEY not set; status is NOT MEASURED.")
        } else {
            println("[LiveNimEvalTest] Finished ${results.size} runs. Pass count: ${results.count { it.passed }}/${results.size}")
        }
    }
}
