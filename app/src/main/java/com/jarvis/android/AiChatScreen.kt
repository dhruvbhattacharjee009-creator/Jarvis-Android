package com.jarvis.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun AiChatScreen(
    controller: AiController,
    onSpeak: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    var status by remember {
        mutableStateOf(
            AiControllerState("AI model is not set up yet.")
        )
    }

    var busy by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Welcome to MINIMAL. Set up the offline AI model to begin chatting.",
                false
            )
        )
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "Calculating..."
        return String.format(
            Locale.US,
            "%.1f MB",
            bytes / (1024.0 * 1024.0)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Text(
            "MINIMAL AI CHAT",
            color = Color(0xFF00D9FF),
            fontSize = 22.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            status.message,
            color = Color(0xFF91A7BA),
            fontSize = 12.sp
        )

        if (!status.isReady) {
            Spacer(Modifier.height(14.dp))

            if (status.isBusy || status.progress > 0) {
                LinearProgressIndicator(
                    progress = {
                        status.progress.coerceIn(0, 100) / 100f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp),
                    color = Color(0xFF00D9FF),
                    trackColor = Color(0xFF26384A)
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${status.progress.coerceIn(0, 100)}%",
                        color = Color(0xFF00D9FF),
                        fontSize = 14.sp
                    )

                    Text(
                        "${formatSize(status.downloadedBytes)} / ${formatSize(status.totalBytes)}",
                        color = Color(0xFF91A7BA),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    busy = true
                    controller.prepare { newState ->
                        status = newState
                        busy = newState.isBusy
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (busy) "DOWNLOADING / INITIALIZING..."
                    else "DOWNLOAD / RETRY AI SETUP"
                )
            }

            Text(
                "Download requires an unmetered connection (usually Wi-Fi). Keep MINIMAL open while the model downloads.",
                color = Color(0xFF91A7BA),
                fontSize = 11.sp
            )
        } else {
            Text(
                "OFFLINE AI READY",
                color = Color(0xFF00D9FF),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (message.isUser)
                                Color(0xFF14364A)
                            else Color(0xFF101D2D),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        if (message.isUser) "YOU" else "MINIMAL",
                        color = Color(0xFF00D9FF),
                        fontSize = 10.sp
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        message.message,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Row {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask MINIMAL...") },
                singleLine = true,
                enabled = status.isReady && !busy
            )

            Spacer(Modifier.width(8.dp))

            Button(
                enabled = status.isReady &&
                    !busy &&
                    input.isNotBlank(),
                onClick = {
                    val prompt = input.trim()

                    if (prompt.isNotEmpty()) {
                        input = ""
                        messages.add(ChatMessage(prompt, true))
                        messages.add(ChatMessage("", false))
                        busy = true

                        val response = StringBuilder()

                        controller.sendMessage(
                            prompt = prompt,
                            onResponse = { part ->
                                response.append(part)
                                messages[messages.lastIndex] =
                                    ChatMessage(
                                        response.toString(),
                                        false
                                    )
                            },
                            onComplete = {
                                busy = false
                                val answer = response.toString()
                                if (answer.isNotBlank()) {
                                    onSpeak(answer)
                                }
                            },
                            onFailure = { error ->
                                busy = false
                                messages[messages.lastIndex] =
                                    ChatMessage(
                                        "AI error: $error",
                                        false
                                    )
                            }
                        )
                    }
                }
            ) {
                Text("SEND")
            }
        }
    }
}
