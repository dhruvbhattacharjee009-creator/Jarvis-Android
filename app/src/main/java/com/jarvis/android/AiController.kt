package com.jarvis.android

import android.app.DownloadManager
import android.content.Context
import android.os.Handler
import android.os.Looper

data class AiControllerState(
    val message: String,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isReady: Boolean = false,
    val isBusy: Boolean = false
) {
    val progressFraction: Float
        get() = (progress.coerceIn(0, 100) / 100f)

    val downloadedMb: Float
        get() = downloadedBytes / (1024f * 1024f)

    val totalMb: Float
        get() = totalBytes / (1024f * 1024f)
}

class AiController(context: Context) {

    private val downloader = ModelDownloader(context)
    private val aiEngine = LocalAiEngine(context)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false

    private var downloadId: Long? = null
    private var currentCallback: ((AiControllerState) -> Unit)? = null
    private var initialized = false

    private val pollTask = object : Runnable {
        override fun run() {
            if (closed) return

            val id = downloadId ?: return
            val status = downloader.getDownloadStatus(id)

            if (status == null) {
                publish(
                    "Checking download status...",
                    busy = true
                )
                handler.postDelayed(this, 1500)
                return
            }

            when (status.status) {

                DownloadManager.STATUS_PENDING -> {
                    publish(
                        "Preparing AI download...",
                        status.progress,
                        status.downloadedBytes,
                        status.totalBytes,
                        busy = true
                    )
                    handler.postDelayed(this, 1000)
                }

                DownloadManager.STATUS_RUNNING -> {
                    publish(
                        "Downloading MINIMAL AI...",
                        status.progress,
                        status.downloadedBytes,
                        status.totalBytes,
                        busy = true
                    )
                    handler.postDelayed(this, 1000)
                }

                DownloadManager.STATUS_PAUSED -> {
                    val message = when (status.reason) {
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI ->
                            "Waiting for an unmetered Wi-Fi connection..."

                        DownloadManager.PAUSED_WAITING_FOR_NETWORK ->
                            "Waiting for internet connection..."

                        DownloadManager.PAUSED_WAITING_TO_RETRY ->
                            "Connection interrupted. Retrying..."

                        else -> "Download paused. Checking connection..."
                    }

                    publish(
                        message,
                        status.progress,
                        status.downloadedBytes,
                        status.totalBytes,
                        busy = true
                    )

                    handler.postDelayed(this, 2000)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadId = null
                    handler.removeCallbacks(this)
                    initializeModel()
                }

                DownloadManager.STATUS_FAILED -> {
                    downloadId = null
                    handler.removeCallbacks(this)

                    publish(
                        describeFailure(status.reason),
                        status.progress,
                        status.downloadedBytes,
                        status.totalBytes
                    )
                }
            }
        }
    }

    fun prepare(onState: (AiControllerState) -> Unit) {
        if (closed) return

        currentCallback = onState

        if (initialized) {
            publish(
                "MINIMAL AI is ready.",
                progress = 100,
                ready = true
            )
            return
        }

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
            downloader.modelFile.delete()

            publish(
                "Connecting to model server...",
                busy = true
            )

            downloadId = downloader.startDownload()

            publish(
                "Starting MINIMAL AI download...",
                busy = true
            )

            handler.removeCallbacks(pollTask)
            handler.post(pollTask)

        } catch (error: Exception) {
            publish(
                error.message ?: "Could not start AI download."
            )
        }
    }

    private fun initializeModel() {
        if (closed) return

        publish(
            "Download complete. Loading AI into memory...",
            progress = 100,
            busy = true
        )

        aiEngine.initialize(downloader.modelFile) { success, message ->

            if (closed) return@initialize

            initialized = success

            if (success) {
                publish(
                    message,
                    progress = 100,
                    ready = true
                )
            } else {
                publish(
                    "AI initialization failed: $message",
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

        if (!initialized) {
            onFailure("MINIMAL AI is not ready yet.")
            return
        }

        if (prompt.isBlank()) {
            onFailure("Please enter a message.")
            return
        }

        aiEngine.sendMessage(
            prompt = prompt.trim(),
            onText = onResponse,
            onComplete = onComplete,
            onFailure = onFailure
        )
    }

    private fun describeFailure(reason: Int): String {
        val detail = when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                "Not enough storage space."

            DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                "Storage is unavailable."

            DownloadManager.ERROR_FILE_ERROR ->
                "Could not write the model file."

            DownloadManager.ERROR_HTTP_DATA_ERROR ->
                "Network connection interrupted."

            DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                "Too many server redirects."

            DownloadManager.ERROR_CANNOT_RESUME ->
                "Download could not resume."

            404 -> "Model file not found."

            401, 403 -> "Server access was denied."

            else -> "Download error code: $reason"
        }

        return "AI download failed. $detail Tap retry to try again."
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


