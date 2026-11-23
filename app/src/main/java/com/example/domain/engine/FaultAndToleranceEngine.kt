package com.example.domain.engine

import com.example.domain.model.ErrorAnalysisResult
import com.example.domain.model.FaultConfig
import com.example.domain.model.InstrumentType
import com.example.domain.model.MeasurementKind
import com.example.domain.model.ToleranceEvaluation
import com.example.domain.model.ToleranceSpec
import com.example.domain.model.ToleranceStatus
import com.example.domain.model.ToolCompatibilityResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Evaluates deterministic mechanical measurement faults in integer micrometers (µm).
 */
object FaultSimulationEngine {
    const val OVER_PRESSURE_COMPRESSION_UM = -30
    const val UNLOCK_DRIFT_UM = 60

    fun computeFaultBiasUm(
        basePositionUm: Int,
        faultConfig: FaultConfig,
        hasWorkpieceContact: Boolean,
        unlockedAfterRemoval: Boolean = false
    ): Int {
        var bias = faultConfig.zeroErrorUm

        if (faultConfig.dirtySurfaceEnabled && hasWorkpieceContact) {
            bias += faultConfig.dirtyParticleUm
        }

        if (faultConfig.overPressureEnabled && hasWorkpieceContact) {
            bias += OVER_PRESSURE_COMPRESSION_UM
        }

        if (faultConfig.tiltAngleDeg > 0 && basePositionUm > 0) {
            val radians = Math.toRadians(faultConfig.tiltAngleDeg.coerceIn(0, 20).toDouble())
            val cosVal = cos(radians).coerceAtLeast(0.5)
            val cosineError = (basePositionUm.toDouble() * ((1.0 / cosVal) - 1.0)).roundToInt()
            bias += cosineError
        }

        if (faultConfig.unlockDriftEnabled && unlockedAfterRemoval) {
            bias += UNLOCK_DRIFT_UM
        }

        return bias
    }
}

/**
 * Enforces real-world mechanical engineering tool limits.
 * Standard Outside Micrometers cannot measure internal bores, depths, or steps, nor > 25 mm.
 */
object ToolCompatibilityGuard {
    const val CALIPER_MAX_UM = 150_000
    const val MICROMETER_MAX_UM = 25_000

    fun validate(
        instrumentType: InstrumentType,
        measurementKind: MeasurementKind,
        dimensionUm: Int
    ): ToolCompatibilityResult {
        return when (instrumentType) {
            InstrumentType.MANUAL_CALIPER,
            InstrumentType.DIGITAL_CALIPER -> {
                if (dimensionUm > CALIPER_MAX_UM) {
                    ToolCompatibilityResult.Incompatible(
                        title = "Melebihi Rentang Jangka Sorong",
                        reason = "Dimensi benda kerja (${MeasurementUnitConverter.formatUmAsMm(dimensionUm, 2)} mm) melebihi kapasitas maksimal jangka sorong 150 mm.",
                        recommendation = "Pilih zona ukur ≤ 150 mm atau ubah dimensi benda kerja kustom."
                    )
                } else {
                    ToolCompatibilityResult.Compatible
                }
            }

            InstrumentType.MANUAL_MICROMETER,
            InstrumentType.DIGITAL_MICROMETER -> {
                when (measurementKind) {
                    MeasurementKind.INSIDE_DIAMETER -> ToolCompatibilityResult.Incompatible(
                        title = "Jenis Pengukuran Tidak Didukung Mikrometer Luar",
                        reason = "Mikrometer sekrup luar standar (0–25 mm) hanya dirancang untuk mengukur diameter luar dan ketebalan, tidak dapat masuk ke dalam lubang.",
                        recommendation = "Gunakan Jangka Sorong (rahang dalam) atau Inside Micrometer khusus untuk mengukur diameter dalam."
                    )
                    MeasurementKind.DEPTH -> ToolCompatibilityResult.Incompatible(
                        title = "Kedalaman Tidak Didukung Mikrometer Luar",
                        reason = "Mikrometer luar standar tidak memiliki batang kedalaman (depth rod).",
                        recommendation = "Gunakan Jangka Sorong (batang kedalaman) atau Depth Micrometer."
                    )
                    MeasurementKind.STEP -> ToolCompatibilityResult.Incompatible(
                        title = "Pengukuran Step Tidak Didukung Mikrometer Luar",
                        reason = "Anvil dan spindle mikrometer luar memerlukan dua bidang sejajar berhadapan, tidak cocok untuk permukaan step bertingkat.",
                        recommendation = "Gunakan Jangka Sorong Manual atau Digital pada mode Step."
                    )
                    MeasurementKind.OUTSIDE_DIAMETER,
                    MeasurementKind.THICKNESS -> {
                        if (dimensionUm > MICROMETER_MAX_UM) {
                            ToolCompatibilityResult.Incompatible(
                                title = "Melebihi Rentang Mikrometer (0–25 mm)",
                                reason = "Dimensi aktual (${MeasurementUnitConverter.formatUmAsMm(dimensionUm, 2)} mm) melebihi kapasitas mikrometer 0–25 mm.",
                                recommendation = "Gunakan Jangka Sorong (0–150 mm) atau pilih benda kerja dengan ukuran ≤ 25 mm."
                            )
                        } else {
                            ToolCompatibilityResult.Compatible
                        }
                    }
                }
            }
        }
    }
}

/**
 * Machining tolerance calculator with 3-tier industrial quality decision:
 * Green = In Tolerance, Orange = Borderline (within 10% of tolerance band edge), Red = Out of Tolerance.
 */
object ToleranceAnalyzer {
    fun evaluate(spec: ToleranceSpec, measuredUm: Int): ToleranceEvaluation {
        val rawUpper = spec.upperLimitUm
        val rawLower = spec.lowerLimitUm
        val upper = maxOf(rawUpper, rawLower)
        val lower = minOf(rawUpper, rawLower)
        val deviation = measuredUm - spec.nominalUm
        val bandWidth = upper - lower

        val status = when {
            measuredUm < lower || measuredUm > upper -> ToleranceStatus.OUT_OF_TOLERANCE
            bandWidth == 0 -> ToleranceStatus.IN_TOLERANCE
            else -> {
                val warningMargin = (bandWidth / 10).coerceAtLeast(5).coerceAtMost(bandWidth / 4)
                if ((measuredUm - lower) <= warningMargin || (upper - measuredUm) <= warningMargin) {
                    ToleranceStatus.BORDERLINE_CHECK
                } else {
                    ToleranceStatus.IN_TOLERANCE
                }
            }
        }

        val advice = when (status) {
            ToleranceStatus.IN_TOLERANCE ->
                "Dimensi benda kerja berada aman di dalam batas toleransi spesifikasi. Komponen LAYAK DITERIMA (PASS)."
            ToleranceStatus.BORDERLINE_CHECK ->
                "Dimensi sangat mendekati batas toleransi. Disarankan lakukan pengukuran ulang & periksa keausan pahat serta kalibrasi alat."
            ToleranceStatus.OUT_OF_TOLERANCE -> {
                if (measuredUm > upper) {
                    "Dimensi melebihi batas atas (oversize +${MeasurementUnitConverter.formatUmAsMm(measuredUm - upper, 2)} mm). Perlu pengecekan setting kedalaman potong / pemesinan ulang."
                } else {
                    "Dimensi kurang dari batas bawah (undersize -${MeasurementUnitConverter.formatUmAsMm(lower - measuredUm, 2)} mm). Komponen DITOLAK (REJECT / SCRAP)."
                }
            }
        }

        return ToleranceEvaluation(
            spec = spec,
            measuredUm = measuredUm,
            deviationUm = deviation,
            status = status,
            qualityAdvice = advice
        )
    }
}

/**
 * Computes absolute error (E = Measured - Reference) and relative error (%).
 */
object ErrorAnalyzer {
    fun analyze(measuredUm: Int, referenceUm: Int): ErrorAnalysisResult {
        val absError = measuredUm - referenceUm
        val isUndefined = referenceUm == 0 && absError != 0
        val relPercentTimes100 = if (referenceUm == 0) {
            0
        } else {
            // (|E| / |Ref|) * 100 * 100 (for 2 decimal places) with half-up integer rounding
            val numerator = abs(absError.toLong()) * 10_000L
            val denom = abs(referenceUm.toLong())
            ((numerator + denom / 2L) / denom).toInt()
        }
        return ErrorAnalysisResult(
            measuredUm = measuredUm,
            referenceUm = referenceUm,
            absoluteErrorUm = absError,
            relativeErrorPercentTimes100 = relPercentTimes100,
            isRelativeErrorUndefined = isUndefined
        )
    }
}
