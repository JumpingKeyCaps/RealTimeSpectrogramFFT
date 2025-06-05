package com.lebaillyapp.realtimespectrogramfft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --- Minimal FFT Utility (No changes needed, but added `result` parameter for pre-allocation) ---

object FFT2 {
    data class Complex(val re: Float, val im: Float)

    // Modified to accept a pre-allocated result array
    fun computeFFT(signal: FloatArray, result: Array<Complex>) {
        val n = signal.size
        if (n == 0 || (n and (n - 1)) != 0) throw IllegalArgumentException("Signal size must be power of 2")
        if (result.size != n) throw IllegalArgumentException("Result array size must match signal size")

        // Initialize result array with signal values
        for (i in 0 until n) {
            result[i] = Complex(signal[i], 0f)
        }
        fft(result)
    }

    private fun fft(a: Array<Complex>) {
        val n = a.size
        if (n == 1) return

        // Create temporary arrays for even/odd parts, or optimize with in-place operations if possible
        // For simplicity and correctness with current FFT implementation, we keep them.
        // For ultimate performance, consider iterative FFT (bit-reversal) or optimized native libraries.
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