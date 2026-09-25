package com.melonapp.an_rewind_recorder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.melonapp.an_rewind_recorder.audio.AudioConstants
import java.io.File

data class RecordingItem(
    val file: File,
    val name: String,
    val lastModified: Long,
    val sizeBytes: Long,
    val durationSeconds: Double
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationSeconds.toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                val kb = sizeBytes / 1024.0
                String.format("%.0f KB", kb)
            }
        }
}

class RecordingsRepository(private val context: Context) {

    fun getRecordingsDirectory(): File {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        val dir = externalDir ?: File(context.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun loadRecordings(): List<RecordingItem> {
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".wav", ignoreCase = true)
        } ?: emptyArray()

        return files.map { file ->
            val pcmBytes = (file.length() - 44).coerceAtLeast(0)
            val durationSec = pcmBytes.toDouble() / AudioConstants.BYTES_PER_SECOND
            RecordingItem(
                file = file,
                name = file.name,
                lastModified = file.lastModified(),
                sizeBytes = file.length(),
                durationSeconds = durationSec
            )
        }.sortedByDescending { it.lastModified }
    }

    fun deleteRecording(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun createShareIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
