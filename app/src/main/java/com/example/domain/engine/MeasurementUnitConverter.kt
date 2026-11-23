package com.example.domain.engine

import com.example.domain.model.MeasurementUnit
import kotlin.math.abs

/**
 * Integer-based micrometer (µm) unit conversion and deterministic decimal formatting.
 * 1 mm = 1_000 µm, 1 inch = 25_400 µm.
 * Zero floating-point accumulation errors across scale calculations and display.
 */
object MeasurementUnitConverter {
    const val UM_PER_MM = 1_000
    const val UM_PER_INCH = 25_400

    fun mmToUm(mm: Double): Int = kotlin.math.round(mm * UM_PER_MM).toInt()

    /**
     * Sign-safe integer quantization to nearest multiple of [step] (half away from zero).
     * Avoids Kotlin integer division truncation glitches for negative values (e.g., -40 µm stays -40 µm).
     */
    fun roundToStep(value: Int, step: Int): Int {
        require(step > 0) { "Step must be positive, got $step" }
        val half = step / 2
        return if (value >= 0) {
            ((value + half) / step) * step
        } else {
            ((value - half) / step) * step
        }
    }

    /**
     * Converts micrometers to ten-thousandths of an inch (0.0001 in units) using integer rounding.
     * Example: 12_350 µm -> (12_350 * 10_000 + 12_700) / 25_400 = 4862 -> 0.4862 in.
     */
    fun umToInchTenThousandths(um: Int): Int {
        val sign = if (um < 0) -1 else 1
        val absUm = abs(um.toLong())
        val rounded = (absUm * 10_000L + (UM_PER_INCH / 2L)) / UM_PER_INCH.toLong()
        return (rounded * sign).toInt()
    }

    fun formatUmAsMm(
        um: Int,
        decimals: Int = 2,
        commaDecimal: Boolean = false,
        showPlusSign: Boolean = false
    ): String {
        val clampedDecimals = decimals.coerceIn(0, 3)
        val divisor = when (clampedDecimals) {
            0 -> 1_000
            1 -> 100
            2 -> 10
            else -> 1
        }
        val sign = when {
            um < 0 -> "-"
            um > 0 && showPlusSign -> "+"
            else -> ""
        }
        val absUm = abs(um.toLong())
        // Round half-up cleanly in integer space
        val scaled = (absUm + divisor / 2L) / divisor.toLong()
        val scaleBase = when (clampedDecimals) {
            0 -> 1L
            1 -> 10L
            2 -> 100L
            else -> 1_000L
        }
        val whole = scaled / scaleBase
        if (clampedDecimals == 0) return "$sign$whole"
        val frac = (scaled % scaleBase).toString().padStart(clampedDecimals, '0')
        val sep = if (commaDecimal) "," else "."
        return "$sign$whole$sep$frac"
    }

    fun formatUmAsInch(
        um: Int,
        decimals: Int = 4,
        commaDecimal: Boolean = false,
        showPlusSign: Boolean = false
    ): String {
        val tenThousandths = umToInchTenThousandths(um)
        val sign = when {
            tenThousandths < 0 -> "-"
            tenThousandths > 0 && showPlusSign -> "+"
            else -> ""
        }
        val absVal = abs(tenThousandths.toLong())
        val clampedDecimals = decimals.coerceIn(3, 4)
        val scaled = if (clampedDecimals == 3) (absVal + 5L) / 10L else absVal
        val base = if (clampedDecimals == 3) 1_000L else 10_000L
        val whole = scaled / base
        val frac = (scaled % base).toString().padStart(clampedDecimals, '0')
        val sep = if (commaDecimal) "," else "."
        return "$sign$whole$sep$frac"
    }

    fun formatReading(
        um: Int,
        unit: MeasurementUnit,
        mmDecimals: Int = 2,
        commaDecimal: Boolean = false,
        showPlusSign: Boolean = false
    ): String {
        return when (unit) {
            MeasurementUnit.MILLIMETER ->
                "${formatUmAsMm(um, mmDecimals, commaDecimal, showPlusSign)} mm"
            MeasurementUnit.INCH ->
                "${formatUmAsInch(um, decimals = if (mmDecimals >= 3) 4 else 4, commaDecimal = commaDecimal, showPlusSign = showPlusSign)} in"
        }
    }

    fun formatPercentTimes100(percentTimes100: Int, commaDecimal: Boolean = false): String {
        val absVal = abs(percentTimes100)
        val whole = absVal / 100
        val frac = (absVal % 100).toString().padStart(2, '0')
        val sep = if (commaDecimal) "," else "."
        return "$whole$sep$frac%"
    }
}
