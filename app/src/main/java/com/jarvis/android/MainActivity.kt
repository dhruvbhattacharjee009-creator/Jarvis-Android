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
    val isUser: Booleanpackage com.jarvis.android

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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

data class ChatMessage(
    val message: String,
    val isUser: Boolean
)

private val Cyan = Color(0xFF38DDF5)
private val Background = Color(0xFF070B13)
private val Panel = Color(0xFF111B2A)
private val Muted = Color(0xFF91A4B8)
private val White = Color(0xFFF1F6FC)

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
                        ?.map { it.name } ?: emptyList()
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
                    val success = voiceEngine?.selectVoice(name) ?: false
                    if (success) selectedVoice = name
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

    override fun onDestroy() {
        speechRecognizer?.destroy()
        voiceEngine?.release()
        if (::aiController.isInitialized) aiController.close()
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
    var listening by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            listening = true
            onListen { result ->
                listening = false
                if (result.isNotBlank()) {
                    // Recognition result is passed to the chat screen
                    // in the next interaction through its own input.
                    page = "CHAT"
                }
            }
        } else {
            listening = false
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            background = Background,
            surface = Panel,
            onBackground = White,
            onSurface = White
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0C1420),
                    tonalElevation = 0.dp
                ) {
                    val pages = listOf(
                        "HOME", "CHAT", "FILES", "VOICE", "ABOUT"
                    )
                    val symbols = listOf("⌂", "◉", "▤", "♫", "ⓘ")

                    pages.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = page == name,
                            onClick = { page = name },
                            icon = {
                                Text(symbols[index], fontSize = 20.sp)
                            },
                            label = {
                                Text(name, fontSize = 9.sp)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan,
                                selectedTextColor = Cyan,
                                indicatorColor = Color(0xFF173448),
                                unselectedIconColor = Muted,
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
                                Color(0xFF101C2B),
                                Background,
                                Color(0xFF080F19)
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
                        onVoice = { page = "VOICE" },
                        onFiles = { page = "FILES" }
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
                        onSelect = { onSelectVoice(it) },
                        onPreview = { onPreviewVoice(it) }
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
    onVoice: () -> Unit,
    onFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "MINIMAL",
                    color = White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
                Text(
                    "PERSONAL AI ASSISTANT",
                    color = Muted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF123044))
                    .border(1.dp, Cyan.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                if (listening) Color(0xFFFFC857) else Cyan,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (listening) "LISTENING" else "STANDBY",
                        color = Cyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        AssistantCore(listening = listening)

        Spacer(Modifier.height(24.dp))

        Text(
            if (listening) "I'M LISTENING" else "Good to have you here.",
            color = White,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(7.dp))

        Text(
            if (listening) "Speak clearly. I'll do my best to understand."
            else "Your ideas. Your questions. One place.",
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF087D9D)
            )
        ) {
            Text(
                if (listening) "VOICE ACTIVE" else "◉   ACTIVATE VOICE",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "QUICK ACCESS",
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickAction(
                modifier = Modifier.weight(1f),
                symbol = "◉",
                title = "AI CHAT",
                subtitle = "Talk to MINIMAL",
                onClick = onChat
            )
            QuickAction(
                modifier = Modifier.weight(1f),
                symbol = "♫",
                title = "VOICE",
                subtitle = "Voice preferences",
                onClick = onVoice
            )
        }

        Spacer(Modifier.height(10.dp))

        QuickAction(
            modifier = Modifier.fillMaxWidth(),
            symbol = "▤",
            title = "DOCUMENTS",
            subtitle = "Your file workspace",
            onClick = onFiles
        )

        Spacer(Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF132B3D),
                            Color(0xFF101B2A)
                        )
                    )
                )
                .border(
                    1.dp,
                    Color(0xFF24445A),
                    RoundedCornerShape(18.dp)
                )
                .padding(17.dp)
        ) {
            Text(
                "BUILT FOR YOUR EVERYDAY",
                color = Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "A thoughtful assistant, designed to keep things simple.",
                color = White,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "MINIMAL  •  VERSION 0.2",
            color = Muted,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AssistantCore(listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "core")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 14000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(225.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(205.dp)
                .graphicsLayer { rotationZ = rotation }
                .border(
                    1.dp,
                    Cyan.copy(alpha = 0.22f),
                    CircleShape
                )
                .padding(15.dp)
                .border(
                    1.dp,
                    Cyan.copy(alpha = 0.42f),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(145.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF267C9A),
                            Color(0xFF153D56),
                            Color(0xFF0A1725)
                        )
                    ),
                    CircleShape
                )
                .border(
                    2.dp,
                    Cyan.copy(alpha = 0.75f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "M",
                color = Cyan,
                fontSize = 76.sp,
                fontWeight = FontWeight.ExtraLight
            )
        }

        Box(
            modifier = Modifier
                .offset(x = 78.dp, y = (-62).dp)
                .size(9.dp)
                .background(Cyan, CircleShape)
        )

        Box(
            modifier = Modifier
                .offset(x = (-84).dp, y = 48.dp)
                .size(6.dp)
                .background(Cyan.copy(alpha = 0.7f), CircleShape)
        )
    }
}

@Composable
fun QuickAction(
    modifier: Modifier = Modifier,
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(
                1.dp,
                Color(0xFF22384B),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF173448), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Cyan, fontSize = 22.sp)
        }

        Spacer(Modifier.width(11.dp))

        Column {
            Text(
                title,
                color = White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = Muted,
                fontSize = 9.sp
            )
        }
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
            .verticalScroll(rememberScrollState())
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
                Text("Initializing voice engine...", color = Muted)
            }

            voices.isEmpty() -> {
                Text(
                    "No voices were found. Install a voice in your Android Text-to-Speech settings and reopen MINIMAL.",
                    color = White
                )
            }

            else -> {
                Text(
                    "AVAILABLE VOICES",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                voices.forEach { voice ->
                    VoiceItem(
                        voiceName = voice,
                        selected = voice == selectedVoice,
                        onSelect = { onSelect(voice) },
                        onPreview = { onPreview(voice) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
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
            .background(Panel, RoundedCornerShape(15.dp))
            .border(
                1.dp,
                if (selected) Cyan else Color(0xFF22384B),
                RoundedCornerShape(15.dp)
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Cyan
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    voiceName,
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (selected) "Currently selected" else "Installed voice",
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
            "DOCUMENT WORKSPACE",
            color = Cyan,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Text("▤", color = Cyan, fontSize = 64.sp)
        Text(
            "Your files, in one place.",
            color = White,
            fontSize = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Document upload and analysis are planned for a future update.",
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
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        Text(
            "ABOUT MINIMAL",
            color = Cyan,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "THINK  •  ASK  •  ACHIEVE",
            color = Muted,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(24.dp))

        CreditCard("PROJECT CREATOR", "Dhruv Bhattacharjee")
        CreditCard(
            "ABOUT THE PROJECT",
            "MINIMAL is an independent personal AI assistant project focused on a simple interface, useful voice interaction and local AI."
        )
        CreditCard(
            "AI DEVELOPMENT ASSISTANCE",
            "ChatGPT by OpenAI assisted with architecture planning, programming guidance and debugging."
        )
        CreditCard(
            "PROJECT PRINCIPLE",
            "Useful technology, thoughtful design and a focus on user control."
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "INDEPENDENT PROJECT  •  VERSION 0.2",
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
            .background(Panel, RoundedCornerShape(15.dp))
            .border(
                1.dp,
                Color(0xFF22384B),
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
            color = White,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
