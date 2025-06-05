package com.lebaillyapp.realtimespectrogramfft

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * ### RealTimeSpectrogramWithWaveform
 *
 * Composable complet affichant en temps réel le spectrogramme FFT audio micro (avec waveform en bas).
 *
 * Les graduations sur l’axe des fréquences sont fixes, ne sont pas affectées par un zoom.
 * Le range min/max de fréquences peut être fixé via des paramètres.
 *
 * @param modifier Modifier pour le container global
 * @param sampleRate Taux d'échantillonnage audio (ex: 44100)
 * @param fftSize Taille de la FFT (puissance de 2)
 * @param updateIntervalMs Intervalle entre calculs FFT (ms)
 * @param maxHistorySize Nombre de lignes d'historique dans le spectrogramme (vertical scroll)
 * @param spectrumColorMap Fonction pour map couleur d'intensité spectre [0..1]
 * @param waveformColor Couleur de la waveform affichée en bas
 * @param waveformSensitivity Sensibilité d'affichage de la waveform (facteur d'amplification, ex: 1f = normal)
 * @param minFrequencyHz Fréquence minimale visible sur le spectrogramme (fixe)
 * @param maxFrequencyHz Fréquence maximale visible sur le spectrogramme (fixe)
 */
@Composable
fun RealTimeSpectrogramWithWaveform(
    modifier: Modifier = Modifier,
    sampleRate: Int = 44100,
    fftSize: Int = 1024,
    updateIntervalMs: Long = 30L,
    maxHistorySize: Int = 100,
    spectrumColorMap: (Float) -> Color = { level ->
        Color.hsv(
            hue = 260f - (level * 260f),
            saturation = 1f,
            value = level.coerceIn(0.1f, 1f)
        )
    },
    waveformColor: Color = Color.White,
    waveformSensitivity: Float = 5f,
    minFrequencyHz: Float = 0f,
    maxFrequencyHz: Float = sampleRate / 2f
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
    val waveformSize = 1024
    val waveformBuffer = remember { FloatArray(waveformSize) }
    var waveformWritePos by remember { mutableStateOf(0) }

    val window = remember(fftSize) {
        FloatArray(fftSize) { i ->
            (0.5f * (1 - kotlin.math.cos(2 * Math.PI * i / (fftSize - 1)))).toFloat()
        }
    }

    val buffer = remember { ShortArray(fftSize) }
    val samplesFloat = remember { FloatArray(fftSize) }
    val windowedSamples = remember { FloatArray(fftSize) }

    // Variables locales pour range fixé (inutile d'avoir de l'état modifiable)
    val minFreqHz = minFrequencyHz.coerceAtLeast(0f)
    val maxFreqHz = maxFrequencyHz.coerceAtMost(sampleRate / 2f).coerceAtLeast(minFreqHz + 1f)

    LaunchedEffect(audioRecord) {
        if (audioRecord == null) return@LaunchedEffect

        audioRecord.startRecording()

        while (isActive) {
            val readCount = audioRecord.read(buffer, 0, fftSize)
            if (readCount == fftSize) {
                for (i in 0 until fftSize) {
                    samplesFloat[i] = buffer[i] / Short.MAX_VALUE.toFloat()
                }
                for (i in 0 until fftSize) {
                    waveformBuffer[(waveformWritePos + i) % waveformSize] = samplesFloat[i]
                }
                waveformWritePos = (waveformWritePos + fftSize) % waveformSize

                for (i in 0 until fftSize) {
                    windowedSamples[i] = samplesFloat[i] * window[i]
                }

                val fftResult = withContext(Dispatchers.Default) {
                    FFT.computeFFT(windowedSamples)
                }

                val magnitudes = FloatArray(fftResult.size / 2)
                var maxMag = 0f
                for (i in magnitudes.indices) {
                    val mag = kotlin.math.sqrt(fftResult[i].re * fftResult[i].re + fftResult[i].im * fftResult[i].im)
                    magnitudes[i] = mag
                    if (mag > maxMag) maxMag = mag
                }
                val normMagnitudes = magnitudes.map { if (maxMag > 0f) it / maxMag else 0f }.toFloatArray()

                val binFreq = sampleRate.toFloat() / fftSize
                val minBin = (minFreqHz / binFreq).toInt().coerceIn(0, normMagnitudes.size - 1)
                val maxBin = (maxFreqHz / binFreq).toInt().coerceIn(minBin + 1, normMagnitudes.size)
                val zoomedMagnitudes = normMagnitudes.sliceArray(minBin until maxBin)

                spectrogramHistory.add(0, zoomedMagnitudes)
                if (spectrogramHistory.size > maxHistorySize) {
                    spectrogramHistory.removeAt(spectrogramHistory.size - 1)
                }
            }

            delay(updateIntervalMs.coerceAtLeast(20L))
        }
        audioRecord.stop()
        audioRecord.release()
    }

    // Tick step fixe, calculé sur range donné (indépendant du pinch zoom)
    val freqRange = maxFreqHz - minFreqHz
    val idealTickCount = 8
    val rawTickStep = freqRange / idealTickCount

    val tickStep = remember(rawTickStep) {
        val base = 10.0.pow(floor(log10(rawTickStep.toDouble()))).toFloat()
        val multiples = listOf(1f, 2f, 5f)
        multiples.minByOrNull { abs(rawTickStep - it * base) }?.let { it * base } ?: rawTickStep
    }
    val subTickStep = tickStep / 5f

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
            // Zoom désactivé (pas de detectTransformGestures)
        ) {
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

                val binFreq = sampleRate.toFloat() / fftSize
                val minBin = (minFreqHz / binFreq).toInt()
                val maxBin = (maxFreqHz / binFreq).toInt()

                val labelPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 24f
                    color = android.graphics.Color.WHITE
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                // Ticks majeurs avec labels - fixes selon range
                val firstTickIndex = floor(minFreqHz / tickStep).toInt()
                val lastTickIndex = ceil(maxFreqHz / tickStep).toInt()

                for (tickIndex in firstTickIndex..lastTickIndex) {
                    val freqTick = tickIndex * tickStep
                    if (freqTick < minFreqHz || freqTick > maxFreqHz) continue

                    val binPos = (freqTick / binFreq) - minBin
                    val x = binPos * binWidth
                    if (x in 0f..size.width) {
                        drawLine(
                            color = Color.White,
                            start = Offset(x, size.height),
                            end = Offset(x, size.height - 20f),
                            strokeWidth = 2f
                        )
                        val labelText = when {
                            freqTick >= 1000f -> "${(freqTick / 1000f).toInt()}k"
                            else -> freqTick.toInt().toString()
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            x,
                            size.height - 5f,
                            labelPaint
                        )
                    }
                }

                // Sous-ticks sans labels (intermédiaires entre ticks majeurs)
                val firstSubTickIndex = floor(minFreqHz / subTickStep).toInt()
                val lastSubTickIndex = ceil(maxFreqHz / subTickStep).toInt()

                for (subTickIndex in firstSubTickIndex..lastSubTickIndex) {
                    val freqTick = subTickIndex * subTickStep
                    if (freqTick < minFreqHz || freqTick > maxFreqHz) continue
                    // ignorer sous-ticks coïncidant avec ticks majeurs
                    val nearestMajorTickMultiple = (freqTick / tickStep).roundToInt()
                    if (abs(freqTick - nearestMajorTickMultiple * tickStep) < 0.01f) continue

                    val binPos = (freqTick / binFreq) - minBin
                    val x = binPos * binWidth
                    if (x in 0f..size.width) {
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(x, size.height),
                            end = Offset(x, size.height - 10f),
                            strokeWidth = 1f
                        )
                    }
                }
            }
        }

        // Waveform en bas, taille fixe 150dp
        WaveformDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            samples = waveformBuffer,
            writePos = waveformWritePos,
            sensitivity = waveformSensitivity,
            color = waveformColor
        )
    }
}



