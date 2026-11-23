package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface

/**
 * Shared UI-thread Paint and Typeface cache for custom engineering Canvas renderers
 * (CaliperCanvas, MicrometerCanvas, WorkpieceCanvas).
 *
 * Eliminates per-frame `Paint()` and `Typeface.create()` allocations during 60fps drag,
 * rotation, and spring animations without altering visual appearance.
 */
internal object CanvasPaintCache {
    private val monoRegularTypeface: Typeface = Typeface.MONOSPACE
    private val monoBoldTypeface: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val sansBoldTypeface: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private val monoRegularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoRegularTypeface
    }
    private val monoBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoBoldTypeface
    }
    private val sansBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sansBoldTypeface
    }

    fun monoRegular(
        colorArgb: Int,
        textSizePx: Float,
        align: Paint.Align = Paint.Align.CENTER,
        alphaInt: Int = 255
    ): Paint = monoRegularPaint.apply {
        color = colorArgb
        textSize = textSizePx
        textAlign = align
        alpha = alphaInt
    }

    fun monoBold(
        colorArgb: Int,
        textSizePx: Float,
        align: Paint.Align = Paint.Align.CENTER,
        alphaInt: Int = 255
    ): Paint = monoBoldPaint.apply {
        color = colorArgb
        textSize = textSizePx
        textAlign = align
        alpha = alphaInt
    }

    fun sansBold(
        colorArgb: Int,
        textSizePx: Float,
        align: Paint.Align = Paint.Align.CENTER,
        alphaInt: Int = 255
    ): Paint = sansBoldPaint.apply {
        color = colorArgb
        textSize = textSizePx
        textAlign = align
        alpha = alphaInt
    }
}
