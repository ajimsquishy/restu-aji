package com.example.domain.engine

import com.example.domain.model.CaliperResolution
import com.example.domain.model.FaultConfig
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementUnit

/**
 * Single Source of Truth state for Manual Vernier Caliper.
 * All visual components (movable jaw, inside jaw, depth rod, vernier alignment, thumb wheel)
 * and the formula readout derive exclusively from [jawPositionUm].
 */
data class ManualCaliperState(
    val jawPositionUm: Int = 25_500,
    val resolution: CaliperResolution = CaliperResolution.RES_0_05_MM,
    val measurementKind: MeasurementKind = MeasurementKind.OUTSIDE_DIAMETER,
    val isLocked: Boolean = false,
    val zeroCalibrationOffsetUm: Int = 0,
    val workpieceContactUm: Int? = 25_500,
    val faultConfig: FaultConfig = FaultConfig(),
    val isInContactWithWorkpiece: Boolean = true,
    val thumbWheelAngleDeg: Float = 0f,
    val procedureWarning: String? = null
) {
    val faultBiasUm: Int
        get() = FaultSimulationEngine.computeFaultBiasUm(
            basePositionUm = jawPositionUm,
            faultConfig = faultConfig,
            hasWorkpieceContact = isInContactWithWorkpiece
        )

    val rawEffectiveUm: Int
        get() = jawPositionUm + faultBiasUm - zeroCalibrationOffsetUm

    val effectiveUm: Int
        get() = rawEffectiveUm.coerceIn(0, ManualCaliperEngine.MAX_RANGE_UM)

    val snappedUm: Int
        get() = MeasurementUnitConverter.roundToStep(effectiveUm, resolution.stepUm)
            .coerceIn(0, ManualCaliperEngine.MAX_RANGE_UM)

    val mainScaleMm: Int
        get() = snappedUm / MeasurementUnitConverter.UM_PER_MM

    val vernierCoincidentIndex: Int
        get() = (snappedUm % MeasurementUnitConverter.UM_PER_MM) / resolution.stepUm

    /**
     * Exact formula: Hasil = Skala Utama + (Nomor garis nonius berimpit × ketelitian).
     * Preserves negative zero-error readings when jaws are closed below zero mark instead of masking them as 0,00 mm.
     */
    val readingUm: Int
        get() = if (rawEffectiveUm < 0) {
            MeasurementUnitConverter.roundToStep(rawEffectiveUm, resolution.stepUm)
        } else {
            mainScaleMm * MeasurementUnitConverter.UM_PER_MM + vernierCoincidentIndex * resolution.stepUm
        }

    val formulaBreakdown: String
        get() {
            if (rawEffectiveUm < 0) {
                val negStr = MeasurementUnitConverter.formatUmAsMm(readingUm, resolution.mmDecimals, commaDecimal = true)
                return "Zero Error Negatif: $negStr mm (garis 0 nonius di kiri 0 skala utama — koreksi saat kalibrasi)"
            }
            val vernierUm = vernierCoincidentIndex * resolution.stepUm
            val vernierStr = MeasurementUnitConverter.formatUmAsMm(vernierUm, resolution.mmDecimals, commaDecimal = true)
            val totalStr = MeasurementUnitConverter.formatUmAsMm(readingUm, resolution.mmDecimals, commaDecimal = true)
            return "$mainScaleMm mm + ($vernierCoincidentIndex × ${resolution.label}) = $mainScaleMm + $vernierStr = $totalStr mm"
        }
}

object ManualCaliperEngine {
    const val MAX_RANGE_UM = 150_000

    fun moveBy(state: ManualCaliperState, deltaUm: Int): ManualCaliperState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Rahang terkunci! Buka sekrup pengunci (Lock) untuk menggeser.")
        }
        return setPosition(state, state.jawPositionUm + deltaUm)
    }

    fun stepFine(state: ManualCaliperState, steps: Int): ManualCaliperState {
        return moveBy(state, steps * state.resolution.stepUm)
    }

    fun setPosition(state: ManualCaliperState, requestedUm: Int): ManualCaliperState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Rahang terkunci! Buka sekrup pengunci (Lock) terlebih dahulu.")
        }

        val contact = state.workpieceContactUm
        var warning: String? = null
        var inContact = false

        val clampedUm = when {
            contact == null -> {
                if (requestedUm < 0) {
                    warning = "Batas minimum rahang (0,00 mm) tercapai."
                } else if (requestedUm > MAX_RANGE_UM) {
                    warning = "Batas maksimum jangka sorong (150,00 mm) tercapai."
                }
                requestedUm.coerceIn(0, MAX_RANGE_UM)
            }
            state.measurementKind == MeasurementKind.OUTSIDE_DIAMETER ||
                state.measurementKind == MeasurementKind.THICKNESS ||
                state.measurementKind == MeasurementKind.STEP -> {
                if (requestedUm <= contact) {
                    inContact = true
                    if (requestedUm < contact - state.resolution.stepUm * 2 && state.faultConfig.overPressureEnabled) {
                        warning = "Tekanan berlebih terdeteksi pada rahang! Kurangi gaya dorong."
                    }
                    contact
                } else {
                    if (requestedUm > MAX_RANGE_UM) {
                        warning = "Batas maksimum jangka sorong (150,00 mm) tercapai."
                    }
                    requestedUm.coerceIn(contact, MAX_RANGE_UM)
                }
            }
            else -> {
                // INSIDE_DIAMETER or DEPTH: movable jaw / depth rod stops at bore wall or hole bottom
                if (requestedUm >= contact) {
                    inContact = true
                    if (requestedUm > contact + state.resolution.stepUm * 2 && state.faultConfig.overPressureEnabled) {
                        warning = "Tekanan berlebih pada rahang dalam / batang kedalaman!"
                    }
                    contact
                } else {
                    if (requestedUm < 0) {
                        warning = "Batas minimum rahang (0,00 mm) tercapai."
                    }
                    requestedUm.coerceIn(0, contact)
                }
            }
        }

        val quantized = MeasurementUnitConverter.roundToStep(clampedUm, state.resolution.stepUm)
        val finalPos = quantized.coerceIn(0, MAX_RANGE_UM)
        val deltaMoved = finalPos - state.jawPositionUm
        val newWheelAngle = (state.thumbWheelAngleDeg + (deltaMoved / 1000f) * 36f) % 360f

        return state.copy(
            jawPositionUm = finalPos,
            isInContactWithWorkpiece = inContact || (contact != null && absDiff(finalPos, contact) <= state.resolution.stepUm),
            thumbWheelAngleDeg = newWheelAngle,
            procedureWarning = warning
        )
    }

    fun toggleLock(state: ManualCaliperState): ManualCaliperState =
        state.copy(isLocked = !state.isLocked, procedureWarning = null)

    fun setResolution(state: ManualCaliperState, resolution: CaliperResolution): ManualCaliperState {
        val snapped = MeasurementUnitConverter.roundToStep(state.jawPositionUm, resolution.stepUm)
            .coerceIn(0, MAX_RANGE_UM)
        return state.copy(resolution = resolution, jawPositionUm = snapped, procedureWarning = null)
    }

    fun calibrateZero(state: ManualCaliperState): ManualCaliperState {
        val offset = state.jawPositionUm + state.faultBiasUm
        return state.copy(
            zeroCalibrationOffsetUm = offset,
            procedureWarning = "Kalibrasi nol (Zero Correction) aktif — referensi nol diselaraskan ke 0,00 mm."
        )
    }

    fun reset(state: ManualCaliperState): ManualCaliperState {
        val startPos = when {
            state.workpieceContactUm == null -> 0
            state.measurementKind == MeasurementKind.INSIDE_DIAMETER ||
                state.measurementKind == MeasurementKind.DEPTH -> 0
            else -> (state.workpieceContactUm + 15_000).coerceAtMost(MAX_RANGE_UM)
        }
        return state.copy(
            jawPositionUm = startPos,
            isLocked = false,
            zeroCalibrationOffsetUm = 0,
            isInContactWithWorkpiece = false,
            thumbWheelAngleDeg = 0f,
            procedureWarning = null
        )
    }

    fun closeToZero(state: ManualCaliperState): ManualCaliperState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Rahang terkunci! Buka sekrup pengunci (Lock) sebelum merapatkan ke 0,00 mm.")
        }
        return state.copy(
            jawPositionUm = 0,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            procedureWarning = null
        )
    }

    private fun absDiff(a: Int, b: Int): Int = kotlin.math.abs(a - b)
}

/**
 * Pure integer/metric geometry helper coupling the mathematical ManualCaliperState
 * to the exact visual graduation ticks drawn on the Main Scale, Vernier Scale, and Magnifier Loupe.
 */
object VernierScaleGeometry {
    /** Distance in µm between adjacent Vernier scale ticks: 950 µm (0.05 mm) or 980 µm (0.02 mm). */
    fun vernierTickPitchUm(resolution: CaliperResolution): Int =
        (resolution.scaleSpanMm * MeasurementUnitConverter.UM_PER_MM) / resolution.divisions

    /** Exact Main Scale position (in µm) directly opposite the [tickIndex]-th Vernier graduation mark. */
    fun vernierTickPositionUm(snappedUm: Int, resolution: CaliperResolution, tickIndex: Int): Int =
        snappedUm + tickIndex * vernierTickPitchUm(resolution)

    /** Exact Main Scale millimeter mark that coincides with [ManualCaliperState.vernierCoincidentIndex]. */
    fun coincidentMainScaleMm(state: ManualCaliperState): Int =
        state.mainScaleMm + state.vernierCoincidentIndex

    /**
     * Computes the visible [minMm..maxMm] window for the 3.5x Magnifier Loupe so that:
     * - For 0.05 mm (19 mm span), both the Vernier 0 mark and the coincident mark are ALWAYS visible.
     * - For 0.02 mm (49 mm span), the coincident mark is ALWAYS centered cleanly inside the viewport,
     *   while also showing the Vernier 0 mark whenever both fit within [windowSpanMm].
     */
    fun loupeWindowRangeMm(state: ManualCaliperState, windowSpanMm: Float = 24f): ClosedFloatingPointRange<Float> {
        val vernierZeroMm = state.snappedUm / 1000f
        val coincidentMm = coincidentMainScaleMm(state).toFloat()
        val half = windowSpanMm / 2f
        val centerMm = when {
            state.resolution.scaleSpanMm <= windowSpanMm - 3f -> vernierZeroMm + (state.resolution.scaleSpanMm / 2f)
            coincidentMm - vernierZeroMm <= windowSpanMm - 4f -> (vernierZeroMm + coincidentMm) / 2f
            else -> coincidentMm
        }
        val minMm = (centerMm - half).coerceAtLeast(-2f)
        return minMm..(minMm + windowSpanMm)
    }
}

/**
 * Single Source of Truth state for Digital Vernier Caliper (0.01 mm resolution).
 */
data class DigitalCaliperState(
    val jawPositionUm: Int = 24_860,
    val measurementKind: MeasurementKind = MeasurementKind.OUTSIDE_DIAMETER,
    val unit: MeasurementUnit = MeasurementUnit.MILLIMETER,
    val isPowerOn: Boolean = true,
    val isLocked: Boolean = false,
    val isHoldActive: Boolean = false,
    val heldValueUm: Int? = null,
    val zeroOffsetUm: Int = 0,
    val workpieceContactUm: Int? = 24_860,
    val faultConfig: FaultConfig = FaultConfig(),
    val isInContactWithWorkpiece: Boolean = true,
    val procedureWarning: String? = null
) {
    val faultBiasUm: Int
        get() = FaultSimulationEngine.computeFaultBiasUm(
            basePositionUm = jawPositionUm,
            faultConfig = faultConfig,
            hasWorkpieceContact = isInContactWithWorkpiece
        )

    val sensorUm: Int
        get() {
            // Preserve negative zero-error and positive fault bias accurately before ZERO offset subtraction
            val raw = (jawPositionUm + faultBiasUm).coerceIn(-1_000, DigitalCaliperEngine.MAX_RANGE_UM + 1_000)
            return MeasurementUnitConverter.roundToStep(raw, DigitalCaliperEngine.RESOLUTION_UM)
        }

    /** Exact value shown on the LCD; null when powered OFF. */
    val displayedUm: Int?
        get() = when {
            !isPowerOn -> null
            isHoldActive && heldValueUm != null -> heldValueUm
            else -> sensorUm - zeroOffsetUm
        }

    val formattedLcdText: String
        get() {
            val value = displayedUm ?: return ""
            return when (unit) {
                MeasurementUnit.MILLIMETER -> MeasurementUnitConverter.formatUmAsMm(value, decimals = 2)
                MeasurementUnit.INCH -> MeasurementUnitConverter.formatUmAsInch(value, decimals = 4)
            }
        }
}

object DigitalCaliperEngine {
    const val MAX_RANGE_UM = 150_000
    const val RESOLUTION_UM = 10 // 0.01 mm

    fun moveBy(state: DigitalCaliperState, deltaUm: Int): DigitalCaliperState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Kunci rahang aktif! Lepas LOCK untuk menggerakkan rahang.")
        }
        return setPosition(state, state.jawPositionUm + deltaUm)
    }

    fun setPosition(state: DigitalCaliperState, requestedUm: Int): DigitalCaliperState {
        if (state.isLocked) {
            return state.copy(procedureWarning = "Kunci rahang aktif! Lepas LOCK terlebih dahulu.")
        }
        val contact = state.workpieceContactUm
        var inContact = false
        var warning: String? = null
        val clamped = when {
            contact == null -> {
                if (requestedUm < 0) warning = "Batas minimum rahang (0,00 mm) tercapai."
                else if (requestedUm > MAX_RANGE_UM) warning = "Batas maksimum jangka sorong (150,00 mm) tercapai."
                requestedUm.coerceIn(0, MAX_RANGE_UM)
            }
            state.measurementKind == MeasurementKind.OUTSIDE_DIAMETER ||
                state.measurementKind == MeasurementKind.THICKNESS ||
                state.measurementKind == MeasurementKind.STEP -> {
                if (requestedUm <= contact) {
                    inContact = true
                    contact
                } else {
                    if (requestedUm > MAX_RANGE_UM) warning = "Batas maksimum jangka sorong (150,00 mm) tercapai."
                    requestedUm.coerceIn(contact, MAX_RANGE_UM)
                }
            }
            else -> {
                if (requestedUm >= contact) {
                    inContact = true
                    contact
                } else {
                    if (requestedUm < 0) warning = "Batas minimum rahang (0,00 mm) tercapai."
                    requestedUm.coerceIn(0, contact)
                }
            }
        }
        val snapped = MeasurementUnitConverter.roundToStep(clamped, RESOLUTION_UM)
        return state.copy(
            jawPositionUm = snapped.coerceIn(0, MAX_RANGE_UM),
            isInContactWithWorkpiece = inContact || (contact != null && kotlin.math.abs(snapped - contact) <= RESOLUTION_UM),
            procedureWarning = warning
        )
    }

    fun togglePower(state: DigitalCaliperState): DigitalCaliperState {
        val nextOn = !state.isPowerOn
        return state.copy(
            isPowerOn = nextOn,
            isHoldActive = if (!nextOn) false else state.isHoldActive,
            heldValueUm = if (!nextOn) null else state.heldValueUm,
            procedureWarning = null
        )
    }

    fun pressZero(state: DigitalCaliperState): DigitalCaliperState {
        if (!state.isPowerOn) {
            return state.copy(procedureWarning = "Nyalakan alat (ON) terlebih dahulu untuk melakukan ZERO.")
        }
        return state.copy(
            zeroOffsetUm = state.sensorUm,
            isHoldActive = false,
            heldValueUm = null,
            procedureWarning = null
        )
    }

    fun toggleUnit(state: DigitalCaliperState): DigitalCaliperState {
        if (!state.isPowerOn) return state
        val nextUnit = if (state.unit == MeasurementUnit.MILLIMETER) MeasurementUnit.INCH else MeasurementUnit.MILLIMETER
        return state.copy(unit = nextUnit, procedureWarning = null)
    }

    fun toggleHold(state: DigitalCaliperState): DigitalCaliperState {
        if (!state.isPowerOn) return state
        return if (state.isHoldActive) {
            state.copy(isHoldActive = false, heldValueUm = null)
        } else {
            state.copy(isHoldActive = true, heldValueUm = state.sensorUm - state.zeroOffsetUm)
        }
    }

    fun toggleLock(state: DigitalCaliperState): DigitalCaliperState =
        state.copy(isLocked = !state.isLocked, procedureWarning = null)

    fun reset(state: DigitalCaliperState): DigitalCaliperState {
        val startPos = when {
            state.workpieceContactUm == null -> 0
            state.measurementKind == MeasurementKind.INSIDE_DIAMETER ||
                state.measurementKind == MeasurementKind.DEPTH -> 0
            else -> (state.workpieceContactUm + 15_000).coerceAtMost(MAX_RANGE_UM)
        }
        return state.copy(
            jawPositionUm = startPos,
            isPowerOn = true,
            isLocked = false,
            isHoldActive = false,
            heldValueUm = null,
            zeroOffsetUm = 0,
            isInContactWithWorkpiece = false,
            procedureWarning = null
        )
    }
}
