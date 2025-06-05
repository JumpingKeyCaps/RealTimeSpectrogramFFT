#  Real Time audio Spectrogram

A Jetpack Compose component for displaying real-time audio spectrograms on Android, built for audio signal visualization and debugging.
Ideal for signal processing, acoustic experiments, or retro audio interfaces.

## 🎯 Purpose

`RealTimeSpectrogram` renders a scrolling time-frequency map that visualizes the energy distribution of incoming audio frames across frequency bins in real time. It’s particularly useful in audio tools involving:

- FSK signal analysis
- Acoustic frequency monitoring
- Synth and tone visualization
- Audio debugging and UI feedback

## 🚀 Features

- 🔄 **Real-time rendering** of FFT data as a dynamic color grid
- 🎨 **Customizable color mapping** (from low to high intensity)
- 🕓 **Horizontal scrolling buffer**: recent FFT frames are pushed from right to left
- ⚡ **Lightweight and efficient**: uses `Canvas` drawing inside Compose
- 🎛️ **Fully configurable resolution**:
  - Number of FFT bins (vertical resolution)
  - Time steps (horizontal resolution)
- 💡 Supports optional normalization and intensity scaling

## 🧱 Architecture

- Accepts audio data as a list of `FloatArray` representing FFT magnitudes
- Internally buffers recent FFT frames into a rolling matrix
- Each column = one FFT frame over time
- Each row = one frequency bin
- Color is mapped based on magnitude per pixel
- Uses `drawIntoCanvas` and `ImageBitmap` for performant rendering

## 🛠️ Integration

This component is designed to be driven by a real-time FFT pipeline, typically provided by a `AudioTrack` data record. 

## 📦 Dependencies

- Jetpack Compose (Canvas, ImageBitmap, Modifier)
- No external libraries — pure Kotlin

## 🧪 Typical Use Cases

- Live display of spectral energy in audio experiments
- Visual feedback for sound synthesis tools
- Debugging FSK encoders/decoders
- Educational apps involving acoustic waveforms
