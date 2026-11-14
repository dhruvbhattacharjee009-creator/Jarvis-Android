
package com.jarvis.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

data class ChatMessage(
    val message: String,
    val isUser: Boolean
)

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US

                val femaleVoice = textToSpeech?.voices
                    ?.firstOrNull {
                        it.name.contains("female", ignoreCase = true) &&
                        it.locale.language == "en"
                    }

                if (femaleVoice != null) {
                    textToSpeech?.voice = femaleVoice
                }
            }
        }

        setContent {
            JarvisApp()
        }
    }

    private fun speak(text: String) {
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "JARVIS_RESPONSE"
        )
    }

    private fun startListening(onResult: (String) -> Unit) {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onResult("Speech recognition is unavailable on this device.")
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

                override fun onError(error: Int) {
                    onResult("I couldn't recognize that. Please try again.")
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    matches?.firstOrNull()?.let {
                        onResult(it)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
            }
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer?.startListening(intent)
    }

    @Composable
    private fun JarvisApp() {

        val messages = remember {
            mutableStateListOf(
                ChatMessage(
                    "Hello! I am JARVIS. Your assistant interface is ready.",
                    false
                )
            )
        }

        var input by remember { mutableStateOf("") }
        var listening by remember { mutableStateOf(false) }

        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    listening = true

                    startListening { result ->
                        input = result
                        listening = false
                    }
                } else {
                    listening = false
                    messages.add(
                        ChatMessage(
                            "Microphone permission is required for voice input.",
                            false
                        )
                    )
                }
            }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF00D9FF),
                background = Color(0xFF080E18),
                surface = Color(0xFF111C2C)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF080E18)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {

                    Text(
                        text = "J.A.R.V.I.S",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D9FF)
                    )

                    Text(
                        text = "PERSONAL AI ASSISTANT",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        letterSpacing = 3.sp
                    )

                    Spacer(modifier = Modifier.height(25.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(
                                Color(0xFF10263A),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "◉",
                                fontSize = 55.sp,
                                color = Color(0xFF00D9FF)
                            )

                            Text(
                                text = if (listening)
                                    "LISTENING..."
                                else
                                    "SYSTEM ONLINE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "CONVERSATION",
                        color = Color(0xFF00D9FF),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message ->

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (message.isUser)
                                            Color(0xFF16364A)
                                        else
                                            Color(0xFF172131),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(14.dp)
                            ) {

                                Text(
                                    text = if (message.isUser)
                                        "YOU"
                                    else
                                        "JARVIS",
                                    color = Color(0xFF00D9FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(5.dp))

                                Text(
                                    text = message.message,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text("Ask JARVIS...")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(15.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val command = input.trim()

                                if (command.isNotEmpty()) {
                                    messages.add(
                                        ChatMessage(command, true)
                                    )

                                    val response =
                                        "Your message has been received. My cloud AI connection is being developed."

                                    messages.add(
                                        ChatMessage(response, false)
                                    )

                                    speak(response)
                                    input = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF007C9E)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("SEND")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!listening) {
                                val permission =
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.RECORD_AUDIO
                                    )

                                if (permission ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    listening = true

                                    startListening { result ->
                                        input = result
                                        listening = false
                                    }
                                } else {
                                    permissionLauncher.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF007C9E)
                        ),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text(
                            text = if (listening)
                                "LISTENING..."
                            else
                                "🎙  ACTIVATE VOICE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
