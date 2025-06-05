package com.lebaillyapp.realtimespectrogramfft

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.math.*


/**
 * ### RealTimeSpectrogramFFTDetector
 *
 * Composable plug-and-play Jetpack Compose component to display a real-time spectrogram
 * of the microphone input using FFT with Hanning window, dominant frequency detection,
 * and energy visualization using a scrolling waterfall canvas.
 *
 * 🧩 Ready to plug in any Compose Preview or screen.
 * ✅ Inclut gestion de permission RECORD_AUDIO et compatibilité API 28+
 *
 * @param modifier Modifier to apply to the canvas container
 * @param sampleRate Sampling rate of audio recording (Hz), usually 44100 or 48000
 * @param fftSize Size of FFT, power of 2 (e.g. 512, 1024). Plus grand = meilleure résolution fréquence mais plus lent.
 * @param updateIntervalMs Delay between FFT updates (ms). 30-50 ms est une bonne valeur. 1 ms est trop ambitieux pour Android.
 * @param minFreqFreqHz Minimum frequency (en Hz) affichée sur l’axe horizontal (pour zoom sur les basses, par ex. 100Hz)
 * @param maxFreqHz Maximum fréquence (en Hz) affichée sur l’axe horizontal (pour zoom sur les aigus, par ex. 10000Hz)
 * @param maxHistorySize Nombre maximal de lignes d'historique (vertical scrolling) dans le spectrogramme.
 * @param spectrumColorMap Fonction pour transformer un niveau normalisé [0f..1f] en couleur.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun RealTimeSpectrogramFFTDetector(
    modifier: Modifier = Modifier,
    sampleRate: Int = 44100,
    fftSize: Int = 1024,
    updateIntervalMs: Long = 30L,
    minFreqHz: Float = 0f,
    maxFreqHz: Float = sampleRate / 2f,
    maxHistorySize: Int = 100,
    spectrumColorMap: (Float) -> Color = { level ->
        Color.hsv(
            hue = 260f - (level * 260f),
            saturation = 1f,
            value = level.coerceIn(0.1f, 1f)
        )
    }
) {
    val context = LocalContext.current

    val bufferSize = remember {
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    val audioRecord = remember {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } else null
    }

    val spectrogramHistory = remember { mutableStateListOf<FloatArray>() }

    // Precompute Hanning window once, reuse
    val window = remember(fftSize) {
        FloatArray(fftSize) { i ->
            (0.5f * (1 - cos(2 * Math.PI * i / (fftSize - 1)))).toFloat()
        }
    }

    // Buffer reuse to avoid allocations
    val buffer = remember { ShortArray(fftSize) }
    val samplesFloat = remember { FloatArray(fftSize) }
    val windowedSamples = remember { FloatArray(fftSize) }

    LaunchedEffect(audioRecord) {
        if (audioRecord == null) return@LaunchedEffect

        audioRecord.startRecording()

        while (isActive) {
            // Read audio data (blocking call)
            val readCount = audioRecord.read(buffer, 0, fftSize)

            if (readCount == fftSize) {
                // Convert Short PCM to Float normalized [-1..1] in place
                for (i in 0 until fftSize) {
                    samplesFloat[i] = buffer[i] / Short.MAX_VALUE.toFloat()
                }
                // Apply Hanning window
                for (i in 0 until fftSize) {
                    windowedSamples[i] = samplesFloat[i] * window[i]
                }

                // Compute FFT on windowed samples (blocking, CPU intense)
                val fftResult = withContext(Dispatchers.Default) {
                    FFT.computeFFT(windowedSamples)
                }

                // Calculate magnitudes and normalize
                val magnitudes = FloatArray(fftResult.size / 2)
                var maxMag = 0f
                for (i in magnitudes.indices) {
                    val mag = sqrt(fftResult[i].re * fftResult[i].re + fftResult[i].im * fftResult[i].im)
                    magnitudes[i] = mag
                    if (mag > maxMag) maxMag = mag
                }
                val normMagnitudes = magnitudes.map { if (maxMag > 0f) it / maxMag else 0f }.toFloatArray()

                // Filter magnitudes by minFreqHz / maxFreqHz to zoom frequency range
                val binFreq = sampleRate.toFloat() / fftSize
                val minBin = (minFreqHz / binFreq).toInt().coerceIn(0, normMagnitudes.size - 1)
                val maxBin = (maxFreqHz / binFreq).toInt().coerceIn(minBin + 1, normMagnitudes.size)
                val zoomedMagnitudes = normMagnitudes.sliceArray(minBin until maxBin)

                // Add latest line at the top of history list
                spectrogramHistory.add(0, zoomedMagnitudes)
                if (spectrogramHistory.size > maxHistorySize) {
                    spectrogramHistory.removeLast()
                }
            }

            // Wait before next update
            delay(updateIntervalMs.coerceAtLeast(20L)) // impose un minimum 20ms pour fluidité
        }
        audioRecord.stop()
        audioRecord.release()
    }

    Box(modifier = modifier.background(Color.Black)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val lineHeight = size.height / maxHistorySize
            val binCount = spectrogramHistory.firstOrNull()?.size ?: 0
            val binWidth = if (binCount > 0) size.width / binCount else 0f

            spectrogramHistory.forEachIndexed { row, magnitudes ->
                magnitudes.forEachIndexed { bin, mag ->
                    drawRect(
                        color = spectrumColorMap(mag),
                        topLeft = Offset(x = bin * binWidth, y = row * lineHeight),
                        size = androidx.compose.ui.geometry.Size(binWidth, lineHeight)
                    )
                }
            }

            // Fréquences labels verticales (tick lines)
            val freqStepHz = 1000
            val binFreq = sampleRate.toFloat() / fftSize
            val minBin = (minFreqHz / binFreq).toInt()
            val maxBin = (maxFreqHz / binFreq).toInt()
            val visibleBins = maxBin - minBin
            val labelPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = 24f
                color = android.graphics.Color.WHITE
            }

            for (freqHz in (freqStepHz..maxFreqHz.toInt()).step(freqStepHz)) {
                if (freqHz < minFreqHz) continue
                val bin = freqHz / binFreq - minBin
                val x = bin * binWidth
                if (x < 0 || x > size.width) continue

                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "${freqHz / 1000}k",
                    x + 4,
                    size.height - 8,
                    labelPaint
                )
            }
        }
    }
}
