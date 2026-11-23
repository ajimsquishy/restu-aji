package com.example.domain.engine

import com.example.domain.model.FaultConfig
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementUnit
import kotlin.math.roundToInt

/**
 * Single Source of Truth geometry connecting leadscrew pitch (0.5 mm / 360°),
 * sleeve half-millimeter graduations, and rotating 50-division thimble scale (0.01 mm).
 * Shared directly between domain math, Canvas rendering, Magnifier Loupe, and unit tests.
 */
object MicrometerScaleGeometry {
    const val MAX_RANGE_UM = 25_000
    const val PITCH_UM = 500        // 0.5 mm axial travel per full 360° revolution
    const val THIMBLE_DIVISIONS = 50
    const val RESOLUTION_UM = 10    // 0.01 mm per thimble division
    const val DEGREES_PER_DIVISION = 360f / THIMBLE_DIVISIONS // 7.2° per 0.01 mm

    fun spindleTravelUmForAngle(deltaDegrees: Float): Int =
        ((deltaDegrees / 360f) * PITCH_UM).roundToInt()

    fun cumulativeAngleDegForUm(positionUm: Int): Float =
        (positionUm.toFloat() / PITCH_UM.toFloat()) * 360f

    fun revolutionsForUm(positionUm: Int): Float =
        positionUm.toFloat() / PITCH_UM.toFloat()

    /** Highest exposed 0.5 mm graduation index on the sleeve (0..50). */
    fun visibleSleeveHalfStepCount(snappedUm: Int): Int =
        (snappedUm.coerceIn(0, MAX_RANGE_UM) / PITCH_UM).coerceIn(0, 50)

    /** Exact division (0..49) aligned with the horizontal datum line on the sleeve. */
    fun thimbleDivisionAtDatum(snappedUm: Int): Int {
        val rem = ((snappedUm % PITCH_UM) + PITCH_UM) % PITCH_UM
        return (rem / RESOLUTION_UM) % THIMBLE_DIVISIONS
    }

    /** Continuous thimble division (0f..50f) for smooth animated drum rotation. */
    fun continuousThimbleDivision(animatedUm: Float): Float {
        val modUm = ((animatedUm % PITCH_UM) + PITCH_UM) % PITCH_UM
        return modUm / RESOLUTION_UM.toFloat()
    }

    /** Axial bevel edge position along the 0..25 mm sleeve scale in millimeters. */
    fun thimbleBevelPositionMm(animatedUm: Float): Float =
        (animatedUm / 1000f).coerceIn(0f, MAX_RANGE_UM / 1000f)
}

/**
 * Single Source of Truth state for Manual Outside Micrometer (0–25 mm, pitch 0.5 mm, 50 divisions, 0.01 mm).
 */
data class ManualMicrometerState(
    val spindlePositionUm: Int = 10_280,
    val measurementKind: MeasurementKind = MeasurementKind.OUTSIDE_DIAMETER,
    val isLocked: Boolean = false,
    val zeroCalibrationOffsetUm: Int = 0,
    val workpieceContactUm: Int? = 10_280,
    val faultConfig: FaultConfig = FaultConfig(),
    val isInContactWithWorkpiece: Boolean = true,
    val ratchetClickCount: Int = 0,
    val isOverPressureWarning: Boolean = false,
    val procedureWarning: String? = null
) {
    val faultBiasUm: Int
        get() = FaultSimulationEngine.computeFaultBiasUm(
            basePositionUm = spindlePositionUm,
            faultConfig = faultConfig,
            hasWorkpieceContact = isInContactWithWorkpiece
        )

    /** Unclamped effective reading before lower-zero saturation (used for visual zero-error inspection). */
    val rawEffectiveUm: Int
        get() = spindlePositionUm + faultBiasUm - zeroCalibrationOffsetUm

    val effectiveUm: Int
        get() = rawEffectiveUm.coerceIn(0, ManualMicrometerEngine.MAX_RANGE_UM)

    val snappedUm: Int
        get() = MeasurementUnitConverter.roundToStep(effectiveUm, ManualMicrometerEngine.RESOLUTION_UM)
            .coerceIn(0, ManualMicrometerEngine.MAX_RANGE_UM)

    /** Sleeve displays upper 1 mm marks and lower 0.5 mm half-marks */
    val sleeveHalfMmSteps: Int
        get() = MicrometerScaleGeometry.visibleSleeveHalfStepCount(snappedUm)

    val sleeveScaleUm: Int
        get() = sleeveHalfMmSteps * ManualMicrometerEngine.PITCH_UM

    /** 0..49 divisions on the rotating thimble drum (wraps below 0 cleanly for negative zero error, e.g., -0.04 mm -> 46). */
    val thimbleDivision: Int
        get() = MicrometerScaleGeometry.thimbleDivisionAtDatum(
            MeasurementUnitConverter.roundToStep(rawEffectiveUm, ManualMicrometerEngine.RESOLUTION_UM)
        )

    /**
     * Exact formula: Hasil = Skala Sleeve + (Skala Thimble × ketelitian 0,01 mm).
     * Preserves negative zero-error reading when closed below zero mark instead of silently masking as 0,00 mm.
     */
    val readingUm: Int
        get() = if (rawEffectiveUm < 0) {
            MeasurementUnitConverter.roundToStep(rawEffectiveUm, ManualMicrometerEngine.RESOLUTION_UM)
        } else {
            sleeveScaleUm + thimbleDivision * ManualMicrometerEngine.RESOLUTION_UM
        }

    /** Total leadscrew revolutions from 0.00 mm (e.g. 10.28 mm = 20.56 revolutions at 0.5 mm pitch). */
    val totalRevolutions: Float
        get() = MicrometerScaleGeometry.revolutionsForUm(spindlePositionUm)

    /** Cumulative rotation angle in degrees (0..18000°) for smooth continuous thimble animation. */
    val cumulativeAngleDeg: Float
        get() = MicrometerScaleGeometry.cumulativeAngleDegForUm(spindlePositionUm)

    /** Rotation angle within current turn (0..360°) */
    val thimbleRotationDeg: Float
        get() = (thimbleDivision.toFloat() / ManualMicrometerEngine.THIMBLE_DIVISIONS.toFloat()) * 360f

    val isAtUpperLimit: Boolean
        get() = spindlePositionUm >= ManualMicrometerEngine.MAX_RANGE_UM

    val isAtLowerLimit: Boolean
        get() = spindlePositionUm <= (workpieceContactUm ?: 0)

    val hasZeroOffset: Boolean
        get() = zeroCalibrationOffsetUm != 0

    val formulaBreakdown: String
        get() {
            if (rawEffectiveUm < 0) {
                val negDivs = kotlin.math.abs(readingUm) / ManualMicrometerEngine.RESOLUTION_UM
                val totalStr = MeasurementUnitConverter.formatUmAsMm(readingUm, decimals = 2, commaDecimal = true)
                return "Zero Error Negatif: 0,0 mm − ($negDivs × 0,01 mm) = $totalStr mm (thimble di angka $thimbleDivision)"
            }
            val sleeveStr = MeasurementUnitConverter.formatUmAsMm(sleeveScaleUm, decimals = 1, commaDecimal = true)
            val thimbleUm = thimbleDivision * ManualMicrometerEngine.RESOLUTION_UM
            val thimbleStr = MeasurementUnitConverter.formatUmAsMm(thimbleUm, decimals = 2, commaDecimal = true)
            val totalStr = MeasurementUnitConverter.formatUmAsMm(readingUm, decimals = 2, commaDecimal = true)
            return "$sleeveStr mm + ($thimbleDivision × 0,01 mm) = $sleeveStr + $thimbleStr = $totalStr mm"
        }
}

object ManualMicrometerEngine {
    const val MAX_RANGE_UM = MicrometerScaleGeometry.MAX_RANGE_UM
    const val PITCH_UM = MicrometerScaleGeometry.PITCH_UM             // 0.5 mm per full 360° revolution
    const val THIMBLE_DIVISIONS = MicrometerScaleGeometry.THIMBLE_DIVISIONS
    const val RESOLUTION_UM = MicrometerScaleGeometry.RESOLUTION_UM   // 0.01 mm

    fun rotateThimbleDegrees(
        state: ManualMicrometerState,
        deltaDegrees: Float,
        usingRatchet: Boolean = false
    ): ManualMicrometerState {
        val deltaUm = MicrometerScaleGeometry.spindleTravelUmForAngle(deltaDegrees)
        if (deltaUm == 0) return state
        return moveSpindleBy(state, deltaUm, usingRatchet)
    }

    fun stepThimble(
        state: ManualMicrometerState,
        steps: Int,
        usingRatchet: Boolean = false
    ): ManualMicrometerState {
        return moveSpindleBy(state, steps * RESOLUTION_UM, usingRatchet)
    }

    fun moveSpindleBy(
        state: ManualMicrometerState,
        deltaUm: Int,
        usingRatchet: Boolean = false
    ): ManualMicrometerState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Lock lever aktif! Buka kunci pengukur sebelum memutar thimble.")
        }
        return setSpindlePosition(state, state.spindlePositionUm + deltaUm, usingRatchet)
    }

    fun setSpindlePosition(
        state: ManualMicrometerState,
        requestedUm: Int,
        usingRatchet: Boolean = false
    ): ManualMicrometerState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Lock lever aktif! Buka kunci pengukur terlebih dahulu.")
        }

        val contact = state.workpieceContactUm
        var inContact = false
        var clicks = state.ratchetClickCount
        var overPressure = false
        var warning: String? = null

        val resolvedUm = when {
            contact != null && requestedUm <= contact -> {
                inContact = true
                if (usingRatchet) {
                    // Ratchet stop slips at constant measuring force (2-3 clicks ideal)
                    clicks = (clicks + 1).coerceAtMost(5)
                    warning = if (clicks >= 2) {
                        "Ratchet klik ($clicks×) — gaya ukur konstan tercapai. Kunci dengan Lock Lever."
                    } else {
                        "Ratchet klik ($clicks×) — putar 1–2 klik lagi untuk gaya ukur stabil."
                    }
                    contact
                } else {
                    // Direct thimble rotation past contact triggers over-pressure protection
                    if (requestedUm < contact) {
                        overPressure = true
                        warning = "Pembatas spindle aktif — tekanan berlebih! Gunakan Ratchet Stop saat menyentuh benda kerja."
                        if (state.faultConfig.overPressureEnabled) {
                            (contact - 30).coerceAtLeast(0)
                        } else {
                            contact
                        }
                    } else {
                        contact
                    }
                }
            }
            contact == null && requestedUm <= 0 -> {
                if (usingRatchet) {
                    clicks = (clicks + 1).coerceAtMost(5)
                    warning = "Anvil & spindle rapat (0,00 mm) — ratchet klik ($clicks×). Siap untuk Zero Setting."
                } else if (requestedUm < 0) {
                    overPressure = true
                    warning = "Batas minimum spindle (0,00 mm) tercapai — gunakan Ratchet Stop agar bidang ukur tidak aus."
                }
                0
            }
            requestedUm >= MAX_RANGE_UM -> {
                clicks = 0
                if (requestedUm > MAX_RANGE_UM) {
                    warning = "Batas maksimum pembatas spindle (25,00 mm) tercapai."
                }
                MAX_RANGE_UM
            }
            else -> {
                clicks = 0
                requestedUm.coerceIn(0, MAX_RANGE_UM)
            }
        }

        val snapped = ((resolvedUm + RESOLUTION_UM / 2) / RESOLUTION_UM) * RESOLUTION_UM
        return state.copy(
            spindlePositionUm = snapped.coerceIn(0, MAX_RANGE_UM),
            isInContactWithWorkpiece = inContact || (contact != null && kotlin.math.abs(snapped - contact) <= RESOLUTION_UM),
            ratchetClickCount = clicks,
            isOverPressureWarning = overPressure,
            procedureWarning = warning
        )
    }

    fun toggleLock(state: ManualMicrometerState): ManualMicrometerState =
        state.copy(
            isLocked = !state.isLocked,
            procedureWarning = if (!state.isLocked) {
                "Lock lever terkunci — posisi spindle ditahan agar hasil pembacaan stabil."
            } else {
                "Lock lever dibuka — thimble dapat diputar kembali."
            }
        )

    fun calibrateZero(state: ManualMicrometerState): ManualMicrometerState {
        val offset = state.spindlePositionUm + state.faultBiasUm
        val msg = if (state.spindlePositionUm == 0 && state.workpieceContactUm == null) {
            "Kalibrasi nol (Zero Setting) berhasil: skala sleeve & thimble diselaraskan ke 0,00 mm."
        } else {
            "Nol referensi (Zero Setting) diset pada posisi saat ini (0,00 mm). Lepas benda kerja & rapatkan ke 0,00 untuk kalibrasi standar."
        }
        return state.copy(
            zeroCalibrationOffsetUm = offset,
            isOverPressureWarning = false,
            procedureWarning = msg
        )
    }

    fun clearZeroOffset(state: ManualMicrometerState): ManualMicrometerState =
        state.copy(
            zeroCalibrationOffsetUm = 0,
            procedureWarning = "Offset Zero Setting direset ke posisi standar pabrik."
        )

    fun closeToZero(state: ManualMicrometerState): ManualMicrometerState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Lock lever aktif! Buka kunci sebelum merapatkan spindle ke 0,00 mm.")
        }
        return state.copy(
            spindlePositionUm = 0,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            ratchetClickCount = 2,
            isOverPressureWarning = false,
            procedureWarning = "Anvil & spindle rapat di 0,00 mm (2× klik ratchet). Tekan Zero Setting bila ada galat nol."
        )
    }

    fun reset(state: ManualMicrometerState): ManualMicrometerState {
        val openPos = if (state.workpieceContactUm != null) {
            (state.workpieceContactUm + 4_000).coerceAtMost(MAX_RANGE_UM)
        } else {
            0
        }
        return state.copy(
            spindlePositionUm = openPos,
            isLocked = false,
            zeroCalibrationOffsetUm = 0,
            isInContactWithWorkpiece = false,
            ratchetClickCount = 0,
            isOverPressureWarning = false,
            procedureWarning = null
        )
    }
}

/**
 * Single Source of Truth state for Digital Outside Micrometer (0–25 mm, 0.01 mm LCD resolution).
 */
data class DigitalMicrometerState(
    val spindlePositionUm: Int = 12_350,
    val measurementKind: MeasurementKind = MeasurementKind.OUTSIDE_DIAMETER,
    val unit: MeasurementUnit = MeasurementUnit.MILLIMETER,
    val isPowerOn: Boolean = true,
    val isLocked: Boolean = false,
    val isHoldActive: Boolean = false,
    val heldValueUm: Int? = null,
    val zeroOffsetUm: Int = 0,
    val workpieceContactUm: Int? = 12_350,
    val faultConfig: FaultConfig = FaultConfig(),
    val isInContactWithWorkpiece: Boolean = true,
    val ratchetClickCount: Int = 0,
    val procedureWarning: String? = null
) {
    val faultBiasUm: Int
        get() = FaultSimulationEngine.computeFaultBiasUm(
            basePositionUm = spindlePositionUm,
            faultConfig = faultConfig,
            hasWorkpieceContact = isInContactWithWorkpiece
        )

    val sensorUm: Int
        get() {
            // Preserve negative zero-error and positive fault bias before ZERO offset subtraction
            val raw = (spindlePositionUm + faultBiasUm).coerceIn(-1_000, DigitalMicrometerEngine.MAX_RANGE_UM + 1_000)
            return MeasurementUnitConverter.roundToStep(raw, DigitalMicrometerEngine.RESOLUTION_UM)
        }

    val displayedUm: Int?
        get() = when {
            !isPowerOn -> null
            isHoldActive && heldValueUm != null -> heldValueUm
            else -> sensorUm - zeroOffsetUm
        }

    val thimbleRotationDeg: Float
        get() = ((spindlePositionUm % DigitalMicrometerEngine.PITCH_UM).toFloat() / DigitalMicrometerEngine.PITCH_UM) * 360f

    val formattedLcdText: String
        get() {
            val value = displayedUm ?: return ""
            return when (unit) {
                MeasurementUnit.MILLIMETER -> MeasurementUnitConverter.formatUmAsMm(value, decimals = 2)
                MeasurementUnit.INCH -> MeasurementUnitConverter.formatUmAsInch(value, decimals = 4)
            }
        }
}

object DigitalMicrometerEngine {
    const val MAX_RANGE_UM = 25_000
    const val PITCH_UM = 500
    const val RESOLUTION_UM = 10 // 0.01 mm

    fun moveSpindleBy(
        state: DigitalMicrometerState,
        deltaUm: Int,
        usingRatchet: Boolean = false
    ): DigitalMicrometerState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Spindle terkunci! Lepas LOCK terlebih dahulu.")
        }
        return setSpindlePosition(state, state.spindlePositionUm + deltaUm, usingRatchet)
    }

    fun setSpindlePosition(
        state: DigitalMicrometerState,
        requestedUm: Int,
        usingRatchet: Boolean = false
    ): DigitalMicrometerState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Spindle terkunci! Lepas LOCK terlebih dahulu.")
        }

        val contact = state.workpieceContactUm
        var inContact = false
        var clicks = state.ratchetClickCount
        var warning: String? = null

        val resolved = when {
            contact != null && requestedUm <= contact -> {
                inContact = true
                if (usingRatchet) {
                    clicks = (clicks + 1).coerceAtMost(5)
                    warning = "Ratchet stop aktif ($clicks× klik) — pembacaan stabil."
                } else if (requestedUm < contact) {
                    warning = "Pembatas spindle aktif — gunakan Ratchet Stop saat menyentuh benda kerja."
                }
                contact
            }
            contact == null && requestedUm <= 0 -> {
                if (usingRatchet) {
                    clicks = (clicks + 1).coerceAtMost(5)
                    warning = "Anvil & spindle rapat (0,00 mm) — ratchet klik ($clicks×)."
                } else if (requestedUm < 0) {
                    warning = "Batas minimum spindle (0,00 mm) tercapai."
                }
                0
            }
            requestedUm >= MAX_RANGE_UM -> {
                clicks = 0
                if (requestedUm > MAX_RANGE_UM) {
                    warning = "Batas maksimum mikrometer (25,00 mm) tercapai."
                }
                MAX_RANGE_UM
            }
            else -> {
                clicks = 0
                requestedUm.coerceIn(0, MAX_RANGE_UM)
            }
        }

        val snapped = MeasurementUnitConverter.roundToStep(resolved, RESOLUTION_UM)
        return state.copy(
            spindlePositionUm = snapped.coerceIn(0, MAX_RANGE_UM),
            isInContactWithWorkpiece = inContact || (contact != null && kotlin.math.abs(snapped - contact) <= RESOLUTION_UM),
            ratchetClickCount = clicks,
            procedureWarning = warning
        )
    }

    fun togglePower(state: DigitalMicrometerState): DigitalMicrometerState {
        val next = !state.isPowerOn
        return state.copy(
            isPowerOn = next,
            isHoldActive = if (!next) false else state.isHoldActive,
            heldValueUm = if (!next) null else state.heldValueUm,
            procedureWarning = null
        )
    }

    fun pressZero(state: DigitalMicrometerState): DigitalMicrometerState {
        if (!state.isPowerOn) {
            return state.copy(procedureWarning = "Nyalakan mikrometer (ON) terlebih dahulu.")
        }
        return state.copy(
            zeroOffsetUm = state.sensorUm,
            isHoldActive = false,
            heldValueUm = null,
            procedureWarning = "ZERO setting berhasil (0.00 mm)."
        )
    }

    fun toggleUnit(state: DigitalMicrometerState): DigitalMicrometerState {
        if (!state.isPowerOn) return state
        val nextUnit = if (state.unit == MeasurementUnit.MILLIMETER) MeasurementUnit.INCH else MeasurementUnit.MILLIMETER
        return state.copy(unit = nextUnit)
    }

    fun toggleHold(state: DigitalMicrometerState): DigitalMicrometerState {
        if (!state.isPowerOn) return state
        return if (state.isHoldActive) {
            state.copy(isHoldActive = false, heldValueUm = null)
        } else {
            state.copy(isHoldActive = true, heldValueUm = state.sensorUm - state.zeroOffsetUm)
        }
    }

    fun toggleLock(state: DigitalMicrometerState): DigitalMicrometerState =
        state.copy(isLocked = !state.isLocked, procedureWarning = null)

    fun reset(state: DigitalMicrometerState): DigitalMicrometerState {
        val openPos = if (state.workpieceContactUm != null) {
            (state.workpieceContactUm + 4_000).coerceAtMost(MAX_RANGE_UM)
        } else {
            0
        }
        return state.copy(
            spindlePositionUm = openPos,
            isPowerOn = true,
            isLocked = false,
            isHoldActive = false,
            heldValueUm = null,
            zeroOffsetUm = 0,
            isInContactWithWorkpiece = false,
            ratchetClickCount = 0,
            procedureWarning = null
        )
    }
}
