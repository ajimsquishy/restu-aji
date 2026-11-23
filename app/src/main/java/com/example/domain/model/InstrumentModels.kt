package com.example.domain.model

enum class InstrumentType(
    val displayName: String,
    val shortName: String,
    val rangeLabel: String,
    val defaultResolutionLabel: String
) {
    MANUAL_CALIPER(
        displayName = "Jangka Sorong Manual",
        shortName = "J. Sorong Manual",
        rangeLabel = "0–150 mm",
        defaultResolutionLabel = "0,05 mm"
    ),
    DIGITAL_CALIPER(
        displayName = "Jangka Sorong Digital",
        shortName = "J. Sorong Digital",
        rangeLabel = "0–150 mm",
        defaultResolutionLabel = "0,01 mm"
    ),
    MANUAL_MICROMETER(
        displayName = "Mikrometer Sekrup Manual",
        shortName = "Mikrometer Manual",
        rangeLabel = "0–25 mm",
        defaultResolutionLabel = "0,01 mm"
    ),
    DIGITAL_MICROMETER(
        displayName = "Mikrometer Sekrup Digital",
        shortName = "Mikrometer Digital",
        rangeLabel = "0–25 mm",
        defaultResolutionLabel = "0,01 mm"
    )
}

enum class MeasurementKind(val label: String, val shortLabel: String) {
    OUTSIDE_DIAMETER("Diameter Luar (Outside)", "Ø Luar"),
    INSIDE_DIAMETER("Diameter Dalam (Inside)", "Ø Dalam"),
    DEPTH("Kedalaman (Depth)", "Kedalaman"),
    STEP("Pengukuran Step / Tingkat", "Step"),
    THICKNESS("Ketebalan (Thickness)", "Tebal")
}

enum class MeasurementUnit(val symbol: String) {
    MILLIMETER("mm"),
    INCH("in")
}

enum class LearningMode(val label: String, val description: String) {
    LEARN("Belajar", "Dilengkapi panduan langkah demi langkah, callout komponen, dan bantuan rumus."),
    EXPLORE("Eksplorasi", "Bebas mengoperasikan alat ukur dan benda kerja tanpa petunjuk."),
    CHALLENGE("Tantangan", "Ukur benda kerja secara mandiri dan nilai ketepatan serta prosedur.")
}

enum class CaliperResolution(
    val stepUm: Int,
    val divisions: Int,
    val label: String,
    val mmDecimals: Int,
    val scaleSpanMm: Int
) {
    RES_0_05_MM(stepUm = 50, divisions = 20, label = "0,05 mm", mmDecimals = 2, scaleSpanMm = 19),
    RES_0_02_MM(stepUm = 20, divisions = 50, label = "0,02 mm", mmDecimals = 2, scaleSpanMm = 49);

    companion object {
        fun fromStepUm(stepUm: Int): CaliperResolution =
            entries.firstOrNull { it.stepUm == stepUm } ?: RES_0_05_MM
    }
}

data class FaultConfig(
    val zeroErrorUm: Int = 0,
    val overPressureEnabled: Boolean = false,
    val tiltAngleDeg: Int = 0,
    val dirtySurfaceEnabled: Boolean = false,
    val dirtyParticleUm: Int = 40,
    val unlockDriftEnabled: Boolean = false
) {
    val hasAnyFault: Boolean
        get() = zeroErrorUm != 0 || overPressureEnabled || tiltAngleDeg > 0 || dirtySurfaceEnabled || unlockDriftEnabled

    fun activeSummaryList(): List<String> = buildList {
        if (zeroErrorUm != 0) {
            val sign = if (zeroErrorUm > 0) "+" else ""
            add("Zero Error ($sign${zeroErrorUm} µm)")
        }
        if (overPressureEnabled) add("Tekanan Berlebih")
        if (tiltAngleDeg > 0) add("Posisi Miring (${tiltAngleDeg}°)")
        if (dirtySurfaceEnabled) add("Permukaan Kotor (+${dirtyParticleUm} µm)")
        if (unlockDriftEnabled) add("Risiko Tidak Dikunci")
    }
}

enum class ToleranceStatus(val label: String) {
    IN_TOLERANCE("Dalam Toleransi"),
    BORDERLINE_CHECK("Perlu Pemeriksaan"),
    OUT_OF_TOLERANCE("Tidak Sesuai")
}

data class ToleranceSpec(
    val nominalUm: Int,
    val upperToleranceUm: Int,
    val lowerToleranceUm: Int
) {
    val upperLimitUm: Int get() = nominalUm + upperToleranceUm
    val lowerLimitUm: Int get() = nominalUm - kotlin.math.abs(lowerToleranceUm)
}

data class ToleranceEvaluation(
    val spec: ToleranceSpec,
    val measuredUm: Int,
    val deviationUm: Int,
    val status: ToleranceStatus,
    val qualityAdvice: String
)

data class ErrorAnalysisResult(
    val measuredUm: Int,
    val referenceUm: Int,
    val absoluteErrorUm: Int,
    val relativeErrorPercentTimes100: Int, // e.g., 56 means 0.56% (integer-based)
    val isRelativeErrorUndefined: Boolean = false
)
