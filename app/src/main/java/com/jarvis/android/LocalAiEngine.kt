package com.jarvis.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.Executors

class LocalAiEngine(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    @Volatile
    private var closed = false

    fun initialize(
        modelFile: File,
        onReady: (Boolean, String) -> Unit
    ) {
        executor.execute {
            try {
                check(!closed) { "AI engine is closed." }
                check(modelFile.exists()) {
                    "AI model is not downloaded yet."
                }

                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = appContext.cacheDir.absolutePath
                    )
                )

                newEngine.initialize()

                val newConversation =
                    newEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                """
                                You are MINIMAL, a helpful personal AI assistant.
                                Give accurate, clear and useful answers.
                                Explain your reasoning in concise steps when useful.
                                Never invent facts or pretend to have researched
                                information you have not verified.
                                """.trimIndent()
                            ),
                            samplerConfig = SamplerConfig(
                                topK = 20,
                                topP = 0.9,
                                temperature = 0.7
                            )
                        )
                    )

                engine = newEngine
                conversation = newConversation

                mainHandler.post {
                    onReady(true, "MINIMAL AI is ready.")
                }

            } catch (error: Exception) {
                mainHandler.post {
                    onReady(
                        false,
                        error.message ?: "AI initialization failed."
                    )
                }
            }
        }
    }

    fun sendMessage(
        prompt: String,
        onText: (String) -> Unit,
        onComplete: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        executor.execute {
            try {
                check(!closed) { "AI engine is closed." }

                val activeConversation =
                    conversation ?: error("AI model is not ready.")

                require(prompt.isNotBlank()) {
                    "Message cannot be empty."
                }

                activeConversation.sendMessageAsync(
                    prompt,
                    object : MessageCallback {

                        override fun onMessage(message: Message) {
                            mainHandler.post {
onText(
    message.contents.contents
        .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
        .joinToString(separator = "") { it.text }
)
                            }
                        }

                        override fun onDone() {
                            mainHandler.post {
                                onComplete()
                            }
                        }

                        override fun onError(throwable: Throwable) {
                            mainHandler.post {
                                onFailure(
                                    throwable.message
                                        ?: "AI response failed."
                                )
                            }
                        }
                    }
                )

            } catch (error: Exception) {
                mainHandler.post {
                    onFailure(
                        error.message ?: "Unable to send message."
                    )
                }
            }
        }
    }

    fun close() {
        closed = true

        executor.execute {
            conversation?.close()
            conversation = null

            engine?.close()
            engine = null
        }

        executor.shutdown()
    }
}
