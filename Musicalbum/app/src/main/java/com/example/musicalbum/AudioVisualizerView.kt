package com.example.musicalbum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View

class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var visualizer: Visualizer? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6C63FF.toInt()
        style = Paint.Style.FILL
    }

    private val barCount = 60
    private val bars = FloatArray(barCount) { 0f }
    private val smoothedBars = FloatArray(barCount) { 0f }
    private var isPlaying = false

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                // Smooth animation
                for (i in bars.indices) {
                    val target = bars[i]
                    val diff = target - smoothedBars[i]
                    smoothedBars[i] += diff * 0.3f
                }
                invalidate()
                postDelayed(this, 16)
            }
        }
    }

    fun setPlayer(audioSessionId: Int) {
        release()
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        fft?.let { processFFT(it) }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processFFT(fft: ByteArray) {
        val magnitudes = FloatArray(barCount)
        val captureSize = fft.size / 2

        for (i in 0 until barCount) {
            val idx = (i * captureSize / barCount) * 2
            if (idx + 1 < fft.size) {
                val real = fft[idx].toFloat()
                val imag = fft[idx + 1].toFloat()
                magnitudes[i] = kotlin.math.sqrt(real * real + imag * imag)
            }
        }

        // Update bars with scaling
        for (i in 0 until barCount) {
            bars[i] = (magnitudes[i] / 256f).coerceIn(0f, 1f)
        }
    }

    fun startAnimation() {
        isPlaying = true
        post(animationRunnable)
    }

    fun stopAnimation() {
        isPlaying = false
        removeCallbacks(animationRunnable)
        // Reset bars
        for (i in bars.indices) {
            bars[i] = 0f
            smoothedBars[i] = 0f
        }
        invalidate()
    }

    fun release() {
        stopAnimation()
        visualizer?.release()
        visualizer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / barCount * 0.7f
        val spacing = width / barCount * 0.3f
        val centerY = height / 2f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing) + spacing / 2
            val barHeight = smoothedBars[i] * height * 0.8f

            val rect = RectF(
                x,
                centerY - barHeight / 2,
                x + barWidth,
                centerY + barHeight / 2
            )

            // Gradient effect
            val alpha = (100 + smoothedBars[i] * 155).toInt()
            paint.alpha = alpha
            paint.color = when {
                smoothedBars[i] > 0.7f -> 0xFFFF6584.toInt()
                smoothedBars[i] > 0.4f -> 0xFF9D97FF.toInt()
                else -> 0xFF6C63FF.toInt()
            }

            canvas.drawRoundRect(rect, barWidth / 2, barWidth / 2, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
