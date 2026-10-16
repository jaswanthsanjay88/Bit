package com.bit.utils

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FontFileManager"

data class CustomFontItem(
    val path: String,
    val name: String,
    val sizeBytes: Long
)

class FontFileManager(private val context: Context) {

    private val fontsDir: File = context.filesDir.resolve("custom_fonts").also { it.mkdirs() }

    fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query displayName from content resolver", e)
            }
        }
        val lastSegment = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        return if (!lastSegment.isNullOrBlank() && (lastSegment.endsWith(".ttf", ignoreCase = true) || lastSegment.endsWith(".otf", ignoreCase = true))) {
            lastSegment
        } else {
            "custom_font.ttf"
        }
    }

    suspend fun importFont(uri: Uri, displayName: String): String? = withContext(Dispatchers.IO) {
        try {
            val isOtf = displayName.endsWith(".otf", ignoreCase = true)
            val ext = if (isOtf) ".otf" else ".ttf"
            val baseName = displayName.replace(Regex("(?i)\\.(ttf|otf)$"), "")
            val sanitized = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_').ifBlank { "custom_font" }
            val fileName = "${System.currentTimeMillis()}_${sanitized}${ext}"
            val destFile = File(fontsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (destFile.length() < 1024) {
                destFile.delete()
                return@withContext null
            }

            // Validate typeface creation and font rasterization
            try {
                val tf = Typeface.createFromFile(destFile)
                if (tf == null) {
                    destFile.delete()
                    return@withContext null
                }
                val paint = android.graphics.Paint().apply { typeface = tf }
                paint.measureText("Manrope 123 BIT AI")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create typeface from font file", e)
                destFile.delete()
                return@withContext null
            }

            Log.d(TAG, "Imported font: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import font", e)
            null
        }
    }

    fun deleteFont(internalPath: String): Boolean {
        return try {
            File(internalPath).delete()
        } catch (e: Exception) {
            false
        }
    }

    fun listCustomFonts(): List<CustomFontItem> {
        return fontsDir.listFiles()?.mapNotNull { file ->
            try {
                val tf = Typeface.createFromFile(file)
                if (tf != null) {
                    val rawName = file.name.substringAfter('_').substringBeforeLast('.')
                    CustomFontItem(
                        path = file.absolutePath,
                        name = rawName.replace('_', ' ').ifBlank { file.name },
                        sizeBytes = file.length()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }
}
