package com.lebaillyapp.realtimespectrogramfft
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*


@Composable
fun RealTimeSpectrogramV2(
    modifier: Modifier = Modifier,
    sampleRate: Int = 44100,
    fftSize: Int = 1024,
    updateIntervalMs: () -> Long,
    maxHistorySize: () -> Int,
    minFrequencyHz: () -> Float,
    maxFrequencyHz: () -> Float,
    spectrumColorMap: (Float) -> Color = { level ->
        Color.hsv(
            hue = 260f - (level * 260f),
            saturation = 1f,
            value = level.coerceIn(0.1f, 1f)
        )
    }
) {
    val context = LocalContext.current

    val bufferSize = remember(sampleRate) {
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    val audioRecord = remember(sampleRate, fftSize) {
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

    val audioBuffer = remember { ShortArray(fftSize) }
    val samplesFloat = remember { FloatArray(fftSize) }
    val windowedSamples = remember { FloatArray(fftSize) }
    val fftResultArray = remember(fftSize) { Array(fftSize) { FFT2.Complex(0f, 0f) } }
    val magnitudes = remember(fftSize) { FloatArray(fftSize / 2) }
    val normMagnitudes = remember(fftSize) { FloatArray(fftSize / 2) }


    LaunchedEffect(audioRecord, sampleRate, fftSize) {
        if (audioRecord == null) {
            return@LaunchedEffect
        }

        try {
            audioRecord.startRecording()

            while (isActive) {
                val currentUpdateIntervalMs = updateIntervalMs()
                val currentMaxHistorySize = maxHistorySize()
                val currentMinFrequencyHz = minFrequencyHz()
                val currentMaxFrequencyHz = maxFrequencyHz()

                val readCount = audioRecord.read(audioBuffer, 0, fftSize)
                if (readCount == fftSize) {
                    for (i in 0 until fftSize) {
                        val sample = audioBuffer[i] / Short.MAX_VALUE.toFloat()
                        samplesFloat[i] = sample
                        windowedSamples[i] = sample * window[i]
                    }

                    for (i in 0 until fftSize) {
                        waveformBuffer[(waveformWritePos + i) % waveformSize] = samplesFloat[i]
                    }
                    waveformWritePos = (waveformWritePos + fftSize) % waveformSize

                    withContext(Dispatchers.Default) {
                        FFT2.computeFFT(windowedSamples, fftResultArray)
                    }

                    var maxMag = 0f
                    val halfFftSize = fftSize / 2
                    for (i in 0 until halfFftSize) {
                        val re = fftResultArray[i].re
                        val im = fftResultArray[i].im
                        val mag = kotlin.math.sqrt(re * re + im * im)
                        magnitudes[i] = mag
                        if (mag > maxMag) maxMag = mag
                    }

                    for (i in 0 until halfFftSize) {
                        normMagnitudes[i] = if (maxMag > 0f) magnitudes[i] / maxMag else 0f
                    }

                    val binFreq = sampleRate.toFloat() / fftSize
                    val minBin = (currentMinFrequencyHz / binFreq).toInt().coerceIn(0, halfFftSize - 1)
                    val maxBin = (currentMaxFrequencyHz / binFreq).toInt().coerceIn(minBin + 1, halfFftSize)

                    val zoomedMagnitudes = FloatArray(maxBin - minBin)
                    System.arraycopy(normMagnitudes, minBin, zoomedMagnitudes, 0, zoomedMagnitudes.size)

                    spectrogramHistory.add(0, zoomedMagnitudes)
                    while (spectrogramHistory.size > currentMaxHistorySize) {
                        spectrogramHistory.removeAt(spectrogramHistory.size - 1)
                    }
                }
                delay(currentUpdateIntervalMs.coerceAtLeast(20L))
            }
        } finally {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
            audioRecord.release()
        }
    }

    val currentMinFreqHz = minFrequencyHz().coerceAtLeast(0f)
    val currentMaxFreqHz = maxFrequencyHz().coerceAtMost(sampleRate / 2f).coerceAtLeast(currentMinFreqHz + 1f)

    val freqRange = currentMaxFreqHz - currentMinFreqHz
    val idealTickCount = 8
    val rawTickStep = freqRange / idealTickCount

    val tickStep = remember(rawTickStep) {
        val base = 10.0.pow(floor(log10(rawTickStep.toDouble()))).toFloat()
        val multiples = listOf(1f, 2f, 5f)
        multiples.minByOrNull { abs(rawTickStep - it * base) }?.let { it * base } ?: rawTickStep
    }
    val subTickStep = tickStep / 5f

    // Define a base font size for labels
    val baseLabelFontSizeSp = 10.sp
    // Convert sp to px for native Paint
    val density = LocalContext.current.resources.displayMetrics.density
    val labelFontSizePx = baseLabelFontSizeSp.value * density

    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = labelFontSizePx // Use calculated px size
            color = android.graphics.Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val currentMaxHistorySize = maxHistorySize()
                val currentMinFrequencyHz = minFrequencyHz()
                val currentMaxFrequencyHz = maxFrequencyHz()

                val lineHeight = size.height / currentMaxHistorySize.toFloat()
                val binCount = spectrogramHistory.firstOrNull()?.size ?: 0
                val binWidth = if (binCount > 0) size.width / binCount else 0f

                // Draw spectrogram lines
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
                val minBinDrawing = (currentMinFrequencyHz / binFreq).toInt()

                // Calculate a minimum pixel spacing for labels to avoid overlap
                val minLabelSpacingPx = labelPaint.measureText("88888") * 1.5f

                // --- Draw TOP Graduations ---
                var lastDrawnLabelXTop = -Float.MAX_VALUE // Track last drawn label's x position

                // Ticks majeurs avec labels - fixes selon range
                val firstTickIndex = floor(currentMinFrequencyHz / tickStep).toInt()
                val lastTickIndex = ceil(currentMaxFrequencyHz / tickStep).toInt()

                for (tickIndex in firstTickIndex..lastTickIndex) {
                    val freqTick = tickIndex * tickStep
                    // Ensure frequency is within the visible range
                    if (freqTick < currentMinFrequencyHz || freqTick > currentMaxFrequencyHz) continue

                    val binPos = (freqTick / binFreq) - minBinDrawing
                    val x = binPos * binWidth

                    // Only draw if within canvas bounds
                    if (x >= 0f && x <= size.width) {
                        val labelText = freqTick.toInt().toString() // Always full number
                        val textWidth = labelPaint.measureText(labelText)

                        // Check if there's enough space to draw this label
                        if (x - textWidth / 2f > lastDrawnLabelXTop + minLabelSpacingPx || lastDrawnLabelXTop == -Float.MAX_VALUE) {
                            // Draw the major tick line
                            drawLine(
                                color = Color.White,
                                start = Offset(x, 0f), // Draw from top
                                end = Offset(x, 20f), // End 20px down from top
                                strokeWidth = 2f
                            )
                            // Draw the label
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                x,
                                45f, // Position text below the line, adjust as needed
                                labelPaint
                            )
                            lastDrawnLabelXTop = x + textWidth / 2f
                        } else {
                            // Draw major tick line even if label is skipped
                            drawLine(
                                color = Color.White,
                                start = Offset(x, 0f), // Draw from top
                                end = Offset(x, 10f), // Shorter line if label skipped
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                // Sous-ticks sans labels (intermédiaires entre ticks majeurs)
                // Use the same firstTickIndex and lastTickIndex logic for calculating subtick positions
                val firstTickForSub = floor(currentMinFrequencyHz / subTickStep).toInt() // Recalculate based on subTickStep
                val lastTickForSub = ceil(currentMaxFrequencyHz / subTickStep).toInt()   // Recalculate based on subTickStep

                for (subTickIndex in firstTickForSub..lastTickForSub) {
                    val freqTick = subTickIndex * subTickStep
                    if (freqTick < currentMinFrequencyHz || freqTick > currentMaxFrequencyHz) continue
                    // ignorer sous-ticks coïncidant avec ticks majeurs
                    val nearestMajorTickMultiple = (freqTick / tickStep).roundToInt()
                    if (abs(freqTick - nearestMajorTickMultiple * tickStep) < 0.01f) continue

                    val binPos = (freqTick / binFreq) - minBinDrawing
                    val x = binPos * binWidth
                    if (x in 0f..size.width) {
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(x, 0f), // Draw from top
                            end = Offset(x, 10f), // End 10px down from top
                            strokeWidth = 1f
                        )
                    }
                }

                // --- Draw BOTTOM Graduations ---
                var lastDrawnLabelXBottom = -Float.MAX_VALUE // Track last drawn label's x position for bottom

                // Ticks majeurs avec labels - fixes selon range
                // Reuse firstTickIndex and lastTickIndex for bottom major ticks
                for (tickIndex in firstTickIndex..lastTickIndex) {
                    val freqTick = tickIndex * tickStep
                    // Ensure frequency is within the visible range
                    if (freqTick < currentMinFrequencyHz || freqTick > currentMaxFrequencyHz) continue

                    val binPos = (freqTick / binFreq) - minBinDrawing
                    val x = binPos * binWidth

                    // Only draw if within canvas bounds
                    if (x >= 0f && x <= size.width) {
                        val labelText = freqTick.toInt().toString() // Always full number
                        val textWidth = labelPaint.measureText(labelText)

                        // Check if there's enough space to draw this label (independent spacing for bottom)
                        if (x - textWidth / 2f > lastDrawnLabelXBottom + minLabelSpacingPx || lastDrawnLabelXBottom == -Float.MAX_VALUE) {
                            // Draw the major tick line at the bottom
                            drawLine(
                                color = Color.White,
                                start = Offset(x, size.height), // Draw from bottom
                                end = Offset(x, size.height - 20f), // End 20px up from bottom
                                strokeWidth = 2f
                            )
                            // Draw the label at the bottom
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                x,
                                size.height - 25f, // Position text above the line, adjust as needed
                                labelPaint
                            )
                            lastDrawnLabelXBottom = x + textWidth / 2f
                        } else {
                            // Draw major tick line even if label is skipped
                            drawLine(
                                color = Color.White,
                                start = Offset(x, size.height), // Draw from bottom
                                end = Offset(x, size.height - 10f), // Shorter line if label skipped
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                // Sous-ticks sans labels (intermédiaires entre ticks majeurs) at the bottom
                // Reuse firstTickForSub and lastTickForSub for bottom subticks
                for (subTickIndex in firstTickForSub..lastTickForSub) {
                    val freqTick = subTickIndex * subTickStep
                    if (freqTick < currentMinFrequencyHz || freqTick > currentMaxFrequencyHz) continue
                    // ignore sub-ticks that coincide with major ticks
                    val nearestMajorTickMultiple = (freqTick / tickStep).roundToInt()
                    if (abs(freqTick - nearestMajorTickMultiple * tickStep) < 0.01f) continue

                    val binPos = (freqTick / binFreq) - minBinDrawing
                    val x = binPos * binWidth
                    if (x in 0f..size.width) {
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(x, size.height), // Draw from bottom
                            end = Offset(x, size.height - 10f), // End 10px up from bottom
                            strokeWidth = 1f
                        )
                    }
                }
            }
        }
    }
}