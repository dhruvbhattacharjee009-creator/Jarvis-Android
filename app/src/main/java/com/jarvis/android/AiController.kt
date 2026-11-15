package com.jarvis.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.app.DownloadManager

data class AiControllerState(
    val message: String,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isReady: Boolean = false,
    val isBusy: Boolean = false
)

class AiController(context: Context) {

    private val downloader = ModelDownloader(context)
    private val aiEngine = LocalAiEngine(context)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false

    private var downloadId: Long? = null
    private var currentCallback: ((AiControllerState) -> Unit)? = null

    private val pollTask = object : Runnable {
        override fun run() {
            if (closed) return

            val id = downloadId ?: return
            val status = downloader.getDownloadStatus(id)

            if (status == null) {
                publish(
                    message = "Checking download status...",
                    busy = true
                )
                handler.postDelayed(this, 1500)
                return
            }

            when (status.status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING -> {
                    publish(
                        message = "Downloading MINIMAL AI model...",
                        progress = status.progress,
                        downloadedBytes = status.downloadedBytes,
                        totalBytes = status.totalBytes,
                        busy = true
                    )
                    handler.postDelayed(this, 1000)
                }

                DownloadManager.STATUS_PAUSED -> {
                    publish(
                        message = "Download paused. Connect to Wi-Fi and check your connection.",
                        progress = status.progress,
                        downloadedBytes = status.downloadedBytes,
                        totalBytes = status.totalBytes,
                        busy = true
                    )
                    handler.postDelayed(this, 3000)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadId = null
                    initializeModel()
                }

                DownloadManager.STATUS_FAILED -> {
                    downloadId = null
                    publish(
                        message = "Model download failed. Error code: ${status.reason}. Tap retry to try again.",
                        progress = status.progress,
                        downloadedBytes = status.downloadedBytes,
                        totalBytes = status.totalBytes
                    )
                }
            }
        }
    }

    fun prepare(
        onState: (AiControllerState) -> Unit
    ) {
        if (closed) return

        currentCallback = onState

        if (downloadId != null) {
            handler.removeCallbacks(pollTask)
            handler.post(pollTask)
            return
        }

        if (downloader.isModelReady()) {
            initializeModel()
            return
        }

        try {
            val id = downloader.startDownload()
            downloadId = id

            publish(
                message = "Starting MINIMAL AI download...",
                busy = true
            )

            handler.removeCallbacks(pollTask)
            handler.post(pollTask)

        } catch (error: Exception) {
            publish(
                message = error.message
                    ?: "Could not start model download."
            )
        }
    }

    private fun initializeModel() {
        if (closed) return

        publish(
            message = "Download complete. Loading MINIMAL AI model...",
            progress = 100,
            busy = true
        )

        aiEngine.initialize(downloader.modelFile) { success, message ->
            if (closed) return@initialize

            if (success) {
                publish(
                    message = message,
                    progress = 100,
                    ready = true
                )
            } else {
                publish(
                    message = message,
                    progress = 100
                )
            }
        }
    }

    fun sendMessage(
        prompt: String,
        onResponse: (String) -> Unit,
        onComplete: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (closed) {
            onFailure("AI controller is closed.")
            return
        }

        if (!downloader.isModelReady()) {
            onFailure("Download the AI model first.")
            return
        }

        aiEngine.sendMessage(
            prompt = prompt,
            onText = onResponse,
            onComplete = onComplete,
            onFailure = onFailure
        )
    }

    private fun publish(
        message: String,
        progress: Int = 0,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        ready: Boolean = false,
        busy: Boolean = false
    ) {
        if (closed) return

        handler.post {
            if (!closed) {
                currentCallback?.invoke(
                    AiControllerState(
                        message = message,
                        progress = progress.coerceIn(0, 100),
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        isReady = ready,
                        isBusy = busy
                    )
                )
            }
        }
    }

    fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        currentCallback = null
        aiEngine.close()
    }
}

