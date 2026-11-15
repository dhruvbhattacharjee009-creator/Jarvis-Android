package com.jarvis.android

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

data class ModelDownloadStatus(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val progress: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes * 100) / totalBytes).toInt()
        } else {
            0
        }

    val isComplete: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL

    val isFailed: Boolean
        get() = status == DownloadManager.STATUS_FAILED
}

class ModelDownloader(context: Context) {

    private val appContext = context.applicationContext

    private val downloadManager =
        appContext.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

    val modelFile: File
        get() = File(
            appContext.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: appContext.filesDir,
            MODEL_FILE_NAME
        )

    fun isModelReady(): Boolean {
        return modelFile.isFile &&
            modelFile.length() > MIN_MODEL_SIZE
    }

    fun startDownload(): Long {
        check(!isModelReady()) {
            "The AI model is already downloaded."
        }

        val request = DownloadManager.Request(
            Uri.parse(MODEL_URL)
        )
            .setTitle("MINIMAL AI Model")
            .setDescription(
                "Downloading SmolLM2 360M for offline AI."
            )
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(
                DownloadManager.Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                MODEL_FILE_NAME
            )

        return downloadManager.enqueue(request)
    }

    fun getDownloadStatus(
        downloadId: Long
    ): ModelDownloadStatus? {

        val cursor = downloadManager.query(
            DownloadManager.Query().setFilterById(downloadId)
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null

            return ModelDownloadStatus(
                status = it.getInt(
                    it.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_STATUS
                    )
                ),
                downloadedBytes = it.getLong(
                    it.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                    )
                ),
                totalBytes = it.getLong(
                    it.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                    )
                ),
                reason = it.getInt(
                    it.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_REASON
                    )
                )
            )
        }
    }

    companion object {
        const val MODEL_FILE_NAME =
            "SmolLM2_360M_instruct.litertlm"

        const val MODEL_URL =
            "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/resolve/main/SmolLM2_360M_instruct.litertlm"

        private const val MIN_MODEL_SIZE =
            300_000_000L
    }
}
