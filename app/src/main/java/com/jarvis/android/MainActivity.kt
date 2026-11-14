
package com.jarvis.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

data class ChatMessage(
    val message: String,
    val isUser: Boolean
)

private val Cyan = Color(0xFF00D9FF)
private val Background = Color(0xFF050B14)
private val Panel = Color(0xFF101D2D)
private val Muted = Color(0xFF91A7BA)

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
            }
        }

        setContent {
            JarvisApp(
                onListen = { callback ->
                    startListening(callback)
                }
            )
        }
    }

    private fun startListening(callback: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            callback("Speech recognition is unavailable.")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(results: Bundle?) {}

                override fun onError(error: Int) {
                    callback("I couldn't recognize that. Please try again.")
                }

                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    callback(words?.firstOrNull() ?: "No speech detected.")
                }
            }
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun speak(text: String) {
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "JARVIS_RESPONSE"
        )
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun JarvisApp(onListen: ((String) -> Unit) -> Unit) {

    var page by remember { mutableStateOf("HOME") }
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Good day. JARVIS interface initialized. Cloud intelligence is the next development milestone.",
                false
            )
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                listening = true
                onListen { result ->
                    input = result
                    listening = false
                }
            } else {
                listening = false
            }
        }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            background = Background,
            surface = Panel
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0B1421)
                ) {
                    val pages = listOf(
                        "HOME", "CHAT", "FILES", "ABOUT"
                    )
                    val symbols = listOf("⌂", "◉", "▤", "ⓘ")

                    pages.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = page == name,
                            onClick = { page = name },
                            icon = {
                                Text(
                                    symbols[index],
                                    fontSize = 21.sp
                                )
                            },
                            label = {
                                Text(name, fontSize = 9.sp)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan,
                                selectedTextColor = Cyan,
                                indicatorColor = Color(0xFF18364A),
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0D1B2B),
                                Background,
                                Color(0xFF07111E)
                            )
                        )
                    )
            ) {
                when (page) {
                    "HOME" -> HomeScreen(
                        listening = listening,
                        onActivate = {
                            if (!listening) {
                                permissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        },
                        onChat = { page = "CHAT" }
                    )

                    "CHAT" -> ChatScreen(
                        messages = messages,
                        input = input,
                        onInput = { input = it },
                        onSend = {
                            val command = input.trim()
                            if (command.isNotEmpty()) {
                                messages.add(
                                    ChatMessage(command, true)
                                )
                                messages.add(
                                    ChatMessage(
                                        "Your message is received. Cloud AI integration is coming in Phase 2.3.",
                                        false
                                    )
                                )
                                input = ""
                            }
                        }
                    )

                    "FILES" -> FilesScreen()

                    "ABOUT" -> AboutScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    listening: Boolean,
    onActivate: () -> Unit,
    onChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "J.A.R.V.I.S",
            color = Cyan,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp
        )

        Text(
            "PERSONAL INTELLIGENCE SYSTEM",
            color = Muted,
            fontSize = 9.sp,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(34.dp))

        Box(
            modifier = Modifier
                .size(230.dp)
                .border(1.dp, Cyan.copy(alpha = 0.35f), CircleShape)
                .padding(13.dp)
                .border(1.dp, Cyan.copy(alpha = 0.55f), CircleShape)
                .padding(13.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF1C607A),
                            Color(0xFF102C43),
                            Color(0xFF07111D)
                        )
                    ),
                    CircleShape
                )
                .border(2.dp, Cyan.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "J",
                    color = Cyan,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.ExtraLight
                )
                Text(
                    "ARTIFICIAL INTELLIGENCE",
                    color = Color.White,
                    fontSize = 8.sp,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            if (listening) "LISTENING..." else "SYSTEM ONLINE",
            color = Cyan,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )

        Text(
            "Awaiting your command",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(30.dp))

        Button(
            onClick = onActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF087B9C)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                if (listening) "LISTENING..." else "◉   ACTIVATE VOICE",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            border = ButtonDefaults.outlinedButtonBorder
        ) {
            Text("OPEN CONVERSATION", color = Cyan)
        }

        Spacer(Modifier.weight(1f))

        Text(
            "JARVIS CORE  •  PHASE 2",
            color = Muted,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Text(
            "JARVIS CHAT",
            color = Cyan,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "INTELLIGENT CONVERSATION",
            color = Muted,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (message.isUser)
                                Color(0xFF14364A)
                            else
                                Panel,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(15.dp)
                ) {
                    Text(
                        if (message.isUser) "YOU" else "JARVIS",
                        color = Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        message.message,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask JARVIS...")
                },
                shape = RoundedCornerShape(15.dp),
                singleLine = true
            )

            Spacer(Modifier.width(8.dp))

            Button(onClick = onSend) {
                Text("SEND")
            }
        }
    }
}

@Composable
fun FilesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DOCUMENT INTELLIGENCE", color = Cyan, fontSize = 21.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        Spacer(Modifier.height(20.dp))

        Text("▤", color = Cyan, fontSize = 64.sp)

        Text(
            "Your document workspace",
            color = Color.White,
            fontSize = 17.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "PDF analysis, document questions, summaries and file uploads will be implemented in Phase 2.4.",
            color = Muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))

        Text(
            "ABOUT JARVIS",
            color = Cyan,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "A personal AI assistant project",
            color = Muted
        )

        Spacer(Modifier.height(35.dp))

        CreditCard("PROJECT CREATOR", "Dhruv Bhattacharjee")

        CreditCard("AI DEVELOPMENT ASSISTANT", "ChatGPT by OpenAI")

        CreditCard("PROJECT", "JARVIS Android")

        Spacer(Modifier.height(25.dp))

        Text(
            "Our vision is to build a powerful personal assistant using cloud AI, voice interaction and intelligent tools.",
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp
        )

        Spacer(Modifier.weight(1f))

        Text(
            "VERSION 0.2  •  PHASE 2",
            color = Muted,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun CreditCard(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .background(Panel, RoundedCornerShape(15.dp))
            .padding(17.dp)
    ) {
        Text(
            title,
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            value,
            color = Color.White,
            fontSize = 16.sp
        )
    }
}
