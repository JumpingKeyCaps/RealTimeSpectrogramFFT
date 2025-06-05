package com.lebaillyapp.realtimespectrogramfft

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lebaillyapp.realtimespectrogramfft.ui.theme.RealTimeSpectrogramFFTTheme
import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.pow

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RealTimeSpectrogramFFTTheme {

                var hasAudioPermission by remember { mutableStateOf(false) }

                // Demande de permission (une seule fois)
                LaunchedEffect(Unit) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!granted) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            1001
                        )
                    } else {
                        hasAudioPermission = true
                    }
                }

                // Observe la callback système pour mettre à jour l'état
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(Unit) {
                    val observer = object : DefaultLifecycleObserver {
                        override fun onResume(owner: LifecycleOwner) {
                            hasAudioPermission = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (hasAudioPermission) {


                    var maxHistorySize by remember { mutableStateOf(30) }
                    var updateIntervalMs by remember { mutableStateOf(1L) }
                    var maxFrequencyHz by remember { mutableStateOf(20000f) }
                    var minFrequencyHz by remember { mutableStateOf(0f) }
                    var fftSize by remember { mutableStateOf(512) } // give 93hz resolution by mark (48kh/512)
                    var sampleRate by remember { mutableStateOf(48000) }


                    // Define your list of standard sample rates
                    val standardSampleRates = listOf(
                        8000,   // Telephone
                        16000,  // Wideband speech
                        22050,  // Half CD
                        44100,  // CD Quality
                        48000,  // Professional
                        96000,  // High-Res
                        192000  // Ultra High-Res
                    )
                    var currentSampleRateIndex by remember { mutableStateOf(4) }



                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070707))){

                        Column(modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 40.dp, start = 10.dp, end = 10.dp, bottom = 20.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp)),
                            ){

                            RealTimeSpectrogramV2(
                                modifier = Modifier
                                    .height(380.dp)
                                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 10.dp)
                                    .background(Color(0xFF151313)),
                                sampleRate = sampleRate,
                                fftSize = fftSize,
                                updateIntervalMs = { updateIntervalMs },
                                maxHistorySize = { maxHistorySize },
                                minFrequencyHz = { minFrequencyHz },
                                maxFrequencyHz = { maxFrequencyHz }
                            )


                            Spacer(modifier = Modifier.height(20.dp))

                            //Knob setting line 1
                            Row(modifier = Modifier.align(Alignment.CenterHorizontally)){
                                Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text("History size: ${maxHistorySize}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(
                                        Alignment.CenterHorizontally))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    RotaryKnob(
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                        size = 55.dp,
                                        steps = 10,
                                        onValueChanged = { step ->
                                            maxHistorySize = step*10
                                        },
                                        showBackground = false,
                                        backgroundColor = Color(0x741A1A1A),
                                        backgroundShadowColor = Color.Black.copy(alpha = 0.2f),
                                        tickColor = Color(0xFF4B4A4A),
                                        activeTickColor = Color(0xFFFF6B35),
                                        tickLength = 4.dp,
                                        tickWidth = 2.dp,
                                        tickSpacing = 12.dp,
                                        indicatorColor = Color(0xFFFFA500),
                                        indicatorSecondaryColor = Color(0xAAFF8C00),
                                        bevelSizeRatio = 0.80f,
                                        knobSizeRatio = 0.65f,

                                        )
                                }
                                Spacer(modifier = Modifier.width(40.dp))
                                Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text("Scale Max: ${maxFrequencyHz}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(
                                        Alignment.CenterHorizontally))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    RotaryKnob(
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                        size = 55.dp,
                                        steps = 100,
                                        onValueChanged = { step ->
                                            maxFrequencyHz = step*250f
                                        },
                                        showBackground = false,
                                        backgroundColor = Color(0x741A1A1A),
                                        backgroundShadowColor = Color.Black.copy(alpha = 0.2f),
                                        tickColor = Color(0xFF4B4A4A),
                                        activeTickColor = Color(0xFFFF6B35),
                                        tickLength = 4.dp,
                                        tickWidth = 2.dp,
                                        tickSpacing = 12.dp,
                                        indicatorColor = Color(0xFFFFA500),
                                        indicatorSecondaryColor = Color(0xAAFF8C00),
                                        bevelSizeRatio = 0.80f,
                                        knobSizeRatio = 0.65f,

                                        )
                                }
                                Spacer(modifier = Modifier.width(40.dp))
                                Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text("Scale Min: ${minFrequencyHz}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(
                                        Alignment.CenterHorizontally))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    RotaryKnob(
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                        size = 55.dp,
                                        steps = 51,
                                        onValueChanged = { step ->
                                            minFrequencyHz = step*20f
                                        },
                                        showBackground = false,
                                        backgroundColor = Color(0x741A1A1A),
                                        backgroundShadowColor = Color.Black.copy(alpha = 0.2f),
                                        tickColor = Color(0xFF4B4A4A),
                                        activeTickColor = Color(0xFFFF6B35),
                                        tickLength = 4.dp,
                                        tickWidth = 2.dp,
                                        tickSpacing = 12.dp,
                                        indicatorColor = Color(0xFFFFA500),
                                        indicatorSecondaryColor = Color(0xAAFF8C00),
                                        bevelSizeRatio = 0.80f,
                                        knobSizeRatio = 0.65f,

                                        )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            //Knob setting line 2
                            Row(modifier = Modifier.align(Alignment.CenterHorizontally)){
                                Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text("sampleRate: ${sampleRate}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(
                                        Alignment.CenterHorizontally))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    RotaryKnob(
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                        size = 55.dp,
                                        steps = 7,
                                        onValueChanged = { step ->
                                            // Ensure the step index stays within the bounds of your list
                                            currentSampleRateIndex = step.coerceIn(0, standardSampleRates.size - 1)
                                            sampleRate = standardSampleRates[currentSampleRateIndex]
                                        },
                                        showBackground = false,
                                        backgroundColor = Color(0x741A1A1A),
                                        backgroundShadowColor = Color.Black.copy(alpha = 0.2f),
                                        tickColor = Color(0xFF4B4A4A),
                                        activeTickColor = Color(0xFFFF6B35),
                                        tickLength = 4.dp,
                                        tickWidth = 2.dp,
                                        tickSpacing = 12.dp,
                                        indicatorColor = Color(0xFFFFA500),
                                        indicatorSecondaryColor = Color(0xAAFF8C00),
                                        bevelSizeRatio = 0.80f,
                                        knobSizeRatio = 0.65f,

                                        )
                                }
                                Spacer(modifier = Modifier.width(40.dp))
                                Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                                    Text("Update interval: ${updateIntervalMs}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(
                                        Alignment.CenterHorizontally))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    RotaryKnob(
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                        size = 55.dp,
                                        steps = 21,
                                        onValueChanged = { step ->
                                            updateIntervalMs = step.toLong()
                                        },
                                        showBackground = false,
                                        backgroundColor = Color(0x741A1A1A),
                                        backgroundShadowColor = Color.Black.copy(alpha = 0.2f),
                                        tickColor = Color(0xFF4B4A4A),
                                        activeTickColor = Color(0xFFFF6B35),
                                        tickLength = 4.dp,
                                        tickWidth = 2.dp,
                                        tickSpacing = 12.dp,
                                        indicatorColor = Color(0xFFFFA500),
                                        indicatorSecondaryColor = Color(0xAAFF8C00),
                                        bevelSizeRatio = 0.80f,
                                        knobSizeRatio = 0.65f,

                                        )
                                }
                            }

                        }

                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Microphone permission required",
                            color = Color.White,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}