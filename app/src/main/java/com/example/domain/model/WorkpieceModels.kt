package com.example.domain.model

enum class WorkpieceShapeType(val label: String) {
    CYLINDER_SHAFT("Poros"),
    STEPPED_SHAFT("Poros Bertingkat"),
    GROOVED_SHAFT("Poros Beralur"),
    RING("Cincin"),
    BUSHING_RING("Bushing"),
    HEX_BOLT("Baut"),
    HEX_NUT("Mur"),
    FLAT_PLATE("Plat"),
    HOLED_BLOCK("Blok Berlubang"),
    PIPE("Pipa"),
    STEPPED_BORE_BLOCK("Blok Berlubang Bertingkat")
}

enum class DifficultyLevel(val label: String) {
    BASIC("Dasar"),
    INTERMEDIATE("Menengah"),
    ADVANCED("Lanjut")
}

data class MeasurementZone(
    val zoneId: String,
    val label: String,
    val kind: MeasurementKind,
    val actualUm: Int,
    val nominalUm: Int,
    val upperTolUm: Int,
    val lowerTolUm: Int
) {
    val toleranceSpec: ToleranceSpec
        get() = ToleranceSpec(
            nominalUm = nominalUm,
            upperToleranceUm = upperTolUm,
            lowerToleranceUm = lowerTolUm
        )
}

data class Workpiece(
    val id: String,
    val name: String,
    val shapeType: WorkpieceShapeType,
    val material: String,
    val description: String,
    val difficulty: DifficultyLevel = DifficultyLevel.BASIC,
    val isCustom: Boolean = false,
    val zones: List<MeasurementZone>
)

sealed class ToolCompatibilityResult {
    data object Compatible : ToolCompatibilityResult()
    data class Incompatible(
        val title: String,
        val reason: String,
        val recommendation: String
    ) : ToolCompatibilityResult()
}
