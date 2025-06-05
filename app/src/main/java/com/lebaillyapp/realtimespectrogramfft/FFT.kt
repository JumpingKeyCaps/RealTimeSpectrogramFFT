package com.lebaillyapp.realtimespectrogramfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --- Minimal FFT Utility ---

object FFT {
    data class Complex(val re: Float, val im: Float)

    fun computeFFT(signal: FloatArray): List<Complex> {
        val n = signal.size
        if (n == 0 || (n and (n - 1)) != 0) throw IllegalArgumentException("Signal size must be power of 2")

        val result = Array(n) { Complex(signal[it], 0f) }
        fft(result)
        return result.toList()
    }

    private fun fft(a: Array<Complex>) {
        val n = a.size
        if (n == 1) return

        val even = Array(n / 2) { a[2 * it] }
        val odd = Array(n / 2) { a[2 * it + 1] }

        fft(even)
        fft(odd)

        for (k in 0 until n / 2) {
            val t = polar(1f, -2f * PI.toFloat() * k / n) * odd[k]
            a[k] = even[k] + t
            a[k + n / 2] = even[k] - t
        }
    }

    private fun polar(r: Float, theta: Float): Complex = Complex(r * cos(theta), r * sin(theta))

    private operator fun Complex.plus(other: Complex) = Complex(re + other.re, im + other.im)
    private operator fun Complex.minus(other: Complex) = Complex(re - other.re, im - other.im)
    private operator fun Complex.times(other: Complex): Complex =
        Complex(re * other.re - im * other.im, re * other.im + im * other.re)
}