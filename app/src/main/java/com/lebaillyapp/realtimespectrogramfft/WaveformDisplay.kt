package com.lebaillyapp.realtimespectrogramfft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * WaveformDisplay : composable dessinant une waveform audio dans un Canvas.
 *
 * @param modifier Modifier pour la taille et position
 * @param samples Buffer circulaire contenant les échantillons audio normalisés [-1..1]
 * @param writePos Position courante d'écriture dans le buffer (pour gérer le décalage circulaire)
 * @param sensitivity Facteur d'amplification vertical de la waveform
 * @param color Couleur du tracé de la waveform
 */
@Composable
fun WaveformDisplay(
    modifier: Modifier = Modifier,
    samples: FloatArray,
    writePos: Int,
    sensitivity: Float = 1f,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.background(Color.Black)) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        val sampleCount = samples.size
        val step = sampleCount / w.toInt().coerceAtLeast(1)

        val path = Path().apply {
            moveTo(0f, midY)
            for (pixelX in 0 until w.toInt()) {
                val sampleIndex = (writePos + pixelX * step) % sampleCount
                val y = midY - (samples[sampleIndex] * midY * sensitivity)
                lineTo(pixelX.toFloat(), y)
            }
        }

        drawPath(path, color, style = Stroke(width = 2f))
    }
}