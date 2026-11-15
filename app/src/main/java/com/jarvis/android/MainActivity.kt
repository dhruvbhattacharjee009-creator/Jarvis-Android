package com.jarvis.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    private var voiceEngine: VoiceEngine? = null
    private lateinit var aiController: AiController

    private val availableVoices = mutableStateListOf<String>()

    private var voiceReady by mutableStateOf(false)
    private var selectedVoice by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        aiController = AiController(this)

        voiceEngine = VoiceEngine(this) {
            runOnUiThread {
                voiceReady = true

                availableVoices.clear()
                availableVoices.addAll(
                    voiceEngine?.getAvailableVoices()
                        ?.map { it.name }
                        ?: emptyList()
                )

                if (availableVoices.isNotEmpty()) {
                    selectedVoice = availableVoices.first()
                }
            }
        }

        setContent {
            JarvisApp(
                aiController = aiController,
                voices = availableVoices,
                voiceReady = voiceReady,
                selectedVoice = selectedVoice,
                onListen = { callback ->
                    startListening(callback)
                },
                onSelectVoice = { name ->
                    val success =
                        voiceEngine?.selectVoice(name) ?: false

                    if (success) {
                        selectedVoice = name
                    }

                    success
                },
                onPreviewVoice = { name ->
                    voiceEngine?.previewVoice(name)
                },
                onSpeak = { message ->
                    voiceEngine?.speak(message)
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
                    callback(
                        "I couldn't recognize that. Please try again."
                    )
                }

                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    callback(
                        words?.firstOrNull() ?: "No speech detected."
                    )
                }
            }
        )

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.US
            )
        }

        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        voiceEngine?.release()

        if (::aiController.isInitialized) {
            aiController.close()
        }

        super.onDestroy()
    }
}

@Composable
fun JarvisApp(
    aiController: AiController,
    voices: List<String>,
    voiceReady: Boolean,
    selectedVoice: String,
    onListen: ((String) -> Unit) -> Unit,
    onSelectVoice: (String) -> Boolean,
    onPreviewVoice: (String) -> Unit,
    onSpeak: (String) -> Unit
) {
    var page by remember { mutableStateOf("HOME") }
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }

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
                        "HOME", "CHAT", "FILES", "VOICE", "ABOUT"
                    )

                    val symbols = listOf(
                        "⌂", "◉", "▤", "♫", "ⓘ"
                    )

                    pages.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = page == name,
                            onClick = { page = name },
                            icon = {
                                Text(
                                    symbols[index],
                                    fontSize = 19.sp
                                )
                            },
                            label = {
                                Text(name, fontSize = 8.sp)
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
                        onChat = { page = "CHAT" },
                        onVoice = { page = "VOICE" }
                    )

                    "CHAT" -> AiChatScreen(
                        controller = aiController,
                        onSpeak = onSpeak
                    )

                    "FILES" -> FilesScreen()

                    "VOICE" -> VoiceSettingsScreen(
                        voices = voices,
                        voiceReady = voiceReady,
                        selectedVoice = selectedVoice,
                        onSelect = { name ->
                            onSelectVoice(name)
                        },
                        onPreview = { name ->
                            onPreviewVoice(name)
                        }
                    )

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
    onChat: () -> Unit,
    onVoice: () -> Unit
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
            "THINK · ASK · ACHIEVE",
            color = Muted,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(34.dp))

        Box(
            modifier = Modifier
                .size(220.dp)
                .border(
                    1.dp,
                    Cyan.copy(alpha = 0.35f),
                    CircleShape
                )
                .padding(13.dp)
                .border(
                    1.dp,
                    Cyan.copy(alpha = 0.55f),
                    CircleShape
                )
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
                .border(
                    2.dp,
                    Cyan.copy(alpha = 0.7f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "J",
                color = Cyan,
                fontSize = 88.sp,
                fontWeight = FontWeight.ExtraLight
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            if (listening) "LISTENING..." else "SYSTEM ONLINE",
            color = Cyan,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )

        Text(
            "Your personal assistant",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF087B9C)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                if (listening) "LISTENING..."
                else "◉   ACTIVATE VOICE",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("OPEN CONVERSATION", color = Cyan)
        }

        TextButton(onClick = onVoice) {
            Text("VOICE SETTINGS", color = Cyan)
        }

        Spacer(Modifier.weight(1f))

        Text(
            "JARVIS ANDROID  •  PHASE 2",
            color = Muted,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun VoiceSettingsScreen(
    voices: List<String>,
    voiceReady: Boolean,
    selectedVoice: String,
    onSelect: (String) -> Unit,
    onPreview: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        Text(
            "VOICE SETTINGS",
            color = Cyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Choose and preview a voice installed on your device.",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(20.dp))

        when {
            !voiceReady -> {
                CircularProgressIndicator(color = Cyan)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Initializing voice engine...",
                    color = Muted
                )
            }

            voices.isEmpty() -> {
                Text(
                    "No English voices were found. Install an English voice in your Android Text-to-Speech settings and reopen JARVIS.",
                    color = Color.White
                )
            }

            else -> {
                Text(
                    "AVAILABLE ENGLISH VOICES",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(voices) { voice ->
                        VoiceItem(
                            voiceName = voice,
                            selected = voice == selectedVoice,
                            onSelect = { onSelect(voice) },
                            onPreview = { onPreview(voice) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Voice availability depends on your phone's installed Text-to-Speech engine. Male and female labels are not guaranteed by Android.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
fun VoiceItem(
    voiceName: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Panel,
                RoundedCornerShape(15.dp)
            )
            .border(
                1.dp,
                if (selected) Cyan else Color.Transparent,
                RoundedCornerShape(15.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Cyan
                )
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    voiceName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    if (selected) "Currently selected"
                    else "Installed voice",
                    color = Muted,
                    fontSize = 10.sp
                )
            }
        }

        OutlinedButton(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("▶  PREVIEW VOICE", color = Cyan)
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
        Text(
            "DOCUMENT INTELLIGENCE",
            color = Cyan,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Text("▤", color = Cyan, fontSize = 64.sp)

        Text(
            "Your document workspace",
            color = Color.White,
            fontSize = 17.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "PDF analysis, document questions, summaries and file uploads are planned for Phase 2.4.",
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
            .padding(22.dp)
    ) {
        Text(
            "ABOUT JARVIS",
            color = Cyan,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "THINK · ASK · ACHIEVE",
            color = Muted,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(24.dp))

        CreditCard(
            "PROJECT CREATOR",
            "Dhruv Bhattacharjee"
        )

        CreditCard(
            "ABOUT THE DEVELOPER",
            "Dhruv is a student and aspiring developer interested in artificial intelligence, software engineering, science and innovation. He enjoys exploring ambitious ideas and turning concepts into useful technology projects."
        )

        CreditCard(
            "AI DEVELOPMENT ASSISTANT",
            "ChatGPT by OpenAI"
        )

        CreditCard(
            "OPENAI CONTRIBUTION",
            "ChatGPT assisted with research, architecture planning, programming guidance, debugging and design exploration. The project vision and ownership belong to its creator."
        )

        CreditCard(
            "PROJECT",
            "JARVIS Android"
        )

        Spacer(Modifier.weight(1f))

        Text(
            "INDEPENDENT PROJECT • VERSION 0.2",
            color = Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CreditCard(
    title: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                Panel,
                RoundedCornerShape(15.dp)
            )
            .padding(15.dp)
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
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
