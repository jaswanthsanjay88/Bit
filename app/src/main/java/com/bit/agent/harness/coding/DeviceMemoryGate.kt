package com.bit.agent.harness.coding

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Pre-flight hardware memory safety gate (RAM Contention Protection).
 * Prevents Android Low Memory Killer (LMK) from killing inference or the PRoot container
 * during heavy compilation or test execution.
 */
object DeviceMemoryGate {

    private const val TAG = "DeviceMemoryGate"
    private const val MIN_SAFE_MEMORY_BYTES = 450L * 1024L * 1024L // 450 MB threshold

    data class MemoryStatus(
        val availableBytes: Long,
        val totalBytes: Long,
        val isLowMemory: Boolean,
        val isSafeForHeavyTask: Boolean
    ) {
        val availableMb: Long get() = availableBytes / (1024 * 1024)
        val totalMb: Long get() = totalBytes / (1024 * 1024)
    }

    /**
     * Checks available system RAM.
     */
    fun checkMemory(context: Context?): MemoryStatus {
        if (context == null) {
            return MemoryStatus(
                availableBytes = Long.MAX_VALUE,
                totalBytes = Long.MAX_VALUE,
                isLowMemory = false,
                isSafeForHeavyTask = true
            )
        }

        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                val safe = !memInfo.lowMemory && memInfo.availMem >= MIN_SAFE_MEMORY_BYTES
                MemoryStatus(
                    availableBytes = memInfo.availMem,
                    totalBytes = memInfo.totalMem,
                    isLowMemory = memInfo.lowMemory,
                    isSafeForHeavyTask = safe
                )
            } else {
                MemoryStatus(Long.MAX_VALUE, Long.MAX_VALUE, false, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query system memory: ${e.message}")
            MemoryStatus(Long.MAX_VALUE, Long.MAX_VALUE, false, true)
        }
    }

    /**
     * Generates a safe command prefix with capped parallelism (e.g. -j2)
     * if memory is tight but not exhausted.
     */
    fun adaptCommandForMemory(command: String, status: MemoryStatus): String {
        if (!status.isSafeForHeavyTask) {
            // Cap make / cargo / gradle parallelism
            if (command.contains("make") && !command.contains("-j")) {
                return "$command -j2"
            }
            if (command.contains("cargo build") && !command.contains("-j")) {
                return "$command -j 2"
            }
            if (command.contains("gradle") && !command.contains("--max-workers")) {
                return "$command --max-workers=2"
            }
        }
        return command
    }
}
