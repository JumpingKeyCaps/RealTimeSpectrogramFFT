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



                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF070707))){

                        Column(modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 40.dp, start = 10.dp, end = 10.dp, bottom = 40.dp)
                            .fillMaxWidth()
                            .height(500.dp)
                            .clip(RoundedCornerShape(16.dp)),
                            ){

/**
                            RealTimeSpectrogramFFTDetector(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF151313)),
                                sampleRate = 44100,
                                fftSize = 512,
                                updateIntervalMs = 1L,

                                )

     */





                            RealTimeSpectrogramWithWaveform(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF151313)),
                                sampleRate = 44100,
                                fftSize = 512,
                                updateIntervalMs = 10L,
                                maxHistorySize = 20,
                                waveformColor = Color.White,
                                waveformSensitivity = 2f,
                                minFrequencyHz = 0f,
                                maxFrequencyHz = 20000f,

                            )






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