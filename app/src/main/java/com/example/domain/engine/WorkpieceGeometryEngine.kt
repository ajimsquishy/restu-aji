package com.example.domain.engine

import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementZone
import com.example.domain.model.Workpiece
import com.example.domain.model.WorkpieceShapeType
import kotlin.math.hypot
import kotlin.math.max

enum class DimensionGuideStyle {
    EXTERNAL_VERTICAL,
    EXTERNAL_HORIZONTAL,
    INTERNAL_BORE,
    DEPTH_VERTICAL,
    STEP_HORIZONTAL
}

/**
 * Exact millimeter-space anchor for a workpiece measurement area.
 * Coordinates are centered around the workpiece origin (0f, 0f) in millimeters.
 * Both the Canvas dimension overlay and direct tap hit-testing use this single source of truth.
 */
data class ZoneDimensionGuide(
    val zoneId: String,
    val label: String,
    val kind: MeasurementKind,
    val actualUm: Int,
    val actualMm: Float,
    val style: DimensionGuideStyle,
    val startMmX: Float,
    val startMmY: Float,
    val endMmX: Float,
    val endMmY: Float,
    val hotspotMmX: Float,
    val hotspotMmY: Float
) {
    val spanMm: Float
        get() = hypot(endMmX - startMmX, endMmY - startMmY)
}

/**
 * Complete parametric millimeter geometry derived strictly from stored integer-µm workpiece data.
 * Guarantees zero static or detached images: changing any zone's actualUm immediately changes
 * the rendered part geometry, scale bar, and measurement overlay.
 */
data class WorkpieceGeometryProfile(
    val workpieceId: String,
    val shapeType: WorkpieceShapeType,
    val primaryLengthMm: Float,
    val primaryOuterDiaMm: Float,
    val secondaryDiaMm: Float,
    val tertiaryDiaMm: Float,
    val stepLengthMm: Float,
    val totalDepthMm: Float,
    val wallThicknessMm: Float,
    val boundingWidthMm: Float,
    val boundingHeightMm: Float,
    val zoneGuides: Map<String, ZoneDimensionGuide>
)

object WorkpieceGeometryEngine {

    fun buildProfile(workpiece: Workpiece): WorkpieceGeometryProfile {
        val zones = workpiece.zones
        val primaryActualMm = (zones.firstOrNull()?.actualUm ?: 20_000) / 1000f

        return when (workpiece.shapeType) {
            WorkpieceShapeType.CYLINDER_SHAFT -> buildCylinderShaft(workpiece, primaryActualMm)
            WorkpieceShapeType.STEPPED_SHAFT -> buildSteppedShaft(workpiece, primaryActualMm)
            WorkpieceShapeType.GROOVED_SHAFT -> buildGroovedShaft(workpiece, primaryActualMm)
            WorkpieceShapeType.RING -> buildRingOrBushing(workpiece, primaryActualMm, isThinRing = true)
            WorkpieceShapeType.BUSHING_RING -> buildRingOrBushing(workpiece, primaryActualMm, isThinRing = false)
            WorkpieceShapeType.PIPE -> buildPipe(workpiece, primaryActualMm)
            WorkpieceShapeType.HEX_BOLT -> buildHexBolt(workpiece, primaryActualMm)
            WorkpieceShapeType.HEX_NUT -> buildHexNut(workpiece, primaryActualMm)
            WorkpieceShapeType.FLAT_PLATE -> buildFlatPlate(workpiece, primaryActualMm)
            WorkpieceShapeType.HOLED_BLOCK -> buildHoledBlock(workpiece, primaryActualMm)
            WorkpieceShapeType.STEPPED_BORE_BLOCK -> buildSteppedBoreBlock(workpiece, primaryActualMm)
        }
    }

    private fun buildCylinderShaft(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val odZone = wp.zones.firstOrNull { it.zoneId.contains("od") || (it.kind == MeasurementKind.OUTSIDE_DIAMETER && !it.zoneId.contains("len")) }
            ?: wp.zones.firstOrNull { it.kind == MeasurementKind.OUTSIDE_DIAMETER }
        val lenZone = wp.zones.firstOrNull { it.zoneId.contains("len") }
            ?: wp.zones.drop(1).firstOrNull { it.kind == MeasurementKind.OUTSIDE_DIAMETER || it.kind == MeasurementKind.STEP }

        val odMm = (odZone?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(4f)
        val lenMm = (lenZone?.actualUm?.div(1000f) ?: (odMm * 2.35f)).coerceAtLeast(odMm * 0.8f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                val isLength = z.zoneId == lenZone?.zoneId || z.zoneId.contains("len")
                if (isLength) {
                    put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_HORIZONTAL,
                            startMmX = -lenMm / 2f, startMmY = odMm / 2f,
                            endMmX = lenMm / 2f, endMmY = odMm / 2f,
                            hotspotMmX = 0f, hotspotMmY = odMm / 2f
                        )
                    )
                } else {
                    put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = -lenMm * 0.15f, startMmY = -odMm / 2f,
                            endMmX = -lenMm * 0.15f, endMmY = odMm / 2f,
                            hotspotMmX = -lenMm * 0.15f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = lenMm,
            primaryOuterDiaMm = odMm,
            secondaryDiaMm = odMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = 0f,
            totalDepthMm = 0f,
            wallThicknessMm = 0f,
            boundingWidthMm = lenMm,
            boundingHeightMm = odMm,
            zoneGuides = guides
        )
    }

    private fun buildSteppedShaft(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val odZones = wp.zones.filter { it.kind == MeasurementKind.OUTSIDE_DIAMETER }.sortedByDescending { it.actualUm }
        val stepZone = wp.zones.firstOrNull { it.kind == MeasurementKind.STEP }

        val largeDiaMm = (odZones.firstOrNull()?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(6f)
        val smallDiaMm = (odZones.getOrNull(1)?.actualUm?.div(1000f) ?: (largeDiaMm * 0.52f)).coerceIn(3f, largeDiaMm * 0.92f)
        val stepLenMm = (stepZone?.actualUm?.div(1000f) ?: (largeDiaMm * 0.78f)).coerceAtLeast(6f)
        val largeLenMm = (stepLenMm * 1.25f).coerceAtLeast(largeDiaMm * 0.85f)
        val totalLenMm = largeLenMm + stepLenMm
        val leftX = -totalLenMm / 2f
        val shoulderX = leftX + largeLenMm

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when {
                    z.kind == MeasurementKind.STEP -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.STEP_HORIZONTAL,
                            startMmX = shoulderX, startMmY = -largeDiaMm / 2f,
                            endMmX = shoulderX + stepLenMm, endMmY = -largeDiaMm / 2f,
                            hotspotMmX = shoulderX + stepLenMm * 0.5f, hotspotMmY = -smallDiaMm * 0.5f
                        )
                    )
                    odZones.size >= 2 && z.zoneId == odZones[1].zoneId -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = shoulderX + stepLenMm * 0.52f, startMmY = -smallDiaMm / 2f,
                            endMmX = shoulderX + stepLenMm * 0.52f, endMmY = smallDiaMm / 2f,
                            hotspotMmX = shoulderX + stepLenMm * 0.52f, hotspotMmY = 0f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = leftX + largeLenMm * 0.45f, startMmY = -largeDiaMm / 2f,
                            endMmX = leftX + largeLenMm * 0.45f, endMmY = largeDiaMm / 2f,
                            hotspotMmX = leftX + largeLenMm * 0.45f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = totalLenMm,
            primaryOuterDiaMm = largeDiaMm,
            secondaryDiaMm = smallDiaMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = stepLenMm,
            totalDepthMm = 0f,
            wallThicknessMm = 0f,
            boundingWidthMm = totalLenMm,
            boundingHeightMm = largeDiaMm,
            zoneGuides = guides
        )
    }

    private fun buildGroovedShaft(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val odZones = wp.zones.filter { it.kind == MeasurementKind.OUTSIDE_DIAMETER }.sortedByDescending { it.actualUm }
        val depthZone = wp.zones.firstOrNull { it.kind == MeasurementKind.DEPTH }

        val odMm = (odZones.firstOrNull()?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(8f)
        val grooveDepthMm = (depthZone?.actualUm?.div(1000f)
            ?: odZones.getOrNull(1)?.let { (odMm - it.actualUm / 1000f) / 2f }
            ?: (odMm * 0.14f)).coerceIn(1f, odMm * 0.40f)
        val grooveDiaMm = (odZones.getOrNull(1)?.actualUm?.div(1000f) ?: (odMm - grooveDepthMm * 2f))
            .coerceIn(3f, odMm - 1.5f)
        val grooveWidthMm = (odMm * 0.36f).coerceAtLeast(5f)
        val totalLenMm = (odMm * 2.15f).coerceAtLeast(grooveWidthMm * 3f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when {
                    z.kind == MeasurementKind.DEPTH -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.DEPTH_VERTICAL,
                            startMmX = 0f, startMmY = -odMm / 2f,
                            endMmX = 0f, endMmY = -odMm / 2f + grooveDepthMm,
                            hotspotMmX = 0f, hotspotMmY = -odMm / 2f + grooveDepthMm * 0.5f
                        )
                    )
                    odZones.size >= 2 && z.zoneId == odZones[1].zoneId -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = 0f, startMmY = -grooveDiaMm / 2f,
                            endMmX = 0f, endMmY = grooveDiaMm / 2f,
                            hotspotMmX = 0f, hotspotMmY = 0f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = -totalLenMm * 0.32f, startMmY = -odMm / 2f,
                            endMmX = -totalLenMm * 0.32f, endMmY = odMm / 2f,
                            hotspotMmX = -totalLenMm * 0.32f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = totalLenMm,
            primaryOuterDiaMm = odMm,
            secondaryDiaMm = grooveDiaMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = grooveWidthMm,
            totalDepthMm = grooveDepthMm,
            wallThicknessMm = 0f,
            boundingWidthMm = totalLenMm,
            boundingHeightMm = odMm,
            zoneGuides = guides
        )
    }

    private fun buildRingOrBushing(wp: Workpiece, fallbackMm: Float, isThinRing: Boolean): WorkpieceGeometryProfile {
        val odZone = wp.zones.firstOrNull { it.kind == MeasurementKind.OUTSIDE_DIAMETER }
        val idZone = wp.zones.firstOrNull { it.kind == MeasurementKind.INSIDE_DIAMETER }
        val thickZone = wp.zones.firstOrNull { it.kind == MeasurementKind.THICKNESS || it.kind == MeasurementKind.STEP }

        val odMm = (odZone?.actualUm?.div(1000f)
            ?: idZone?.let { it.actualUm / 1000f * 1.48f }
            ?: fallbackMm).coerceAtLeast(8f)
        val idMm = (idZone?.actualUm?.div(1000f) ?: (odMm * 0.65f)).coerceIn(3f, odMm * 0.88f)
        val lenMm = (thickZone?.actualUm?.div(1000f)
            ?: if (isThinRing) odMm * 0.22f else odMm * 0.55f).coerceAtLeast(2.5f)
        val wallMm = ((odMm - idMm) / 2f).coerceAtLeast(1f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when (z.kind) {
                    MeasurementKind.INSIDE_DIAMETER -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.INTERNAL_BORE,
                            startMmX = lenMm / 2f, startMmY = -idMm / 2f,
                            endMmX = lenMm / 2f, endMmY = idMm / 2f,
                            hotspotMmX = lenMm * 0.42f, hotspotMmY = 0f
                        )
                    )
                    MeasurementKind.THICKNESS, MeasurementKind.STEP -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_HORIZONTAL,
                            startMmX = -lenMm / 2f, startMmY = odMm / 2f,
                            endMmX = lenMm / 2f, endMmY = odMm / 2f,
                            hotspotMmX = 0f, hotspotMmY = odMm / 2f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = -lenMm * 0.25f, startMmY = -odMm / 2f,
                            endMmX = -lenMm * 0.25f, endMmY = odMm / 2f,
                            hotspotMmX = -lenMm * 0.25f, hotspotMmY = -odMm * 0.35f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = lenMm,
            primaryOuterDiaMm = odMm,
            secondaryDiaMm = idMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = lenMm,
            totalDepthMm = 0f,
            wallThicknessMm = wallMm,
            boundingWidthMm = max(lenMm + odMm * 0.55f, odMm * 1.15f),
            boundingHeightMm = odMm,
            zoneGuides = guides
        )
    }

    private fun buildPipe(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val odZone = wp.zones.firstOrNull { it.kind == MeasurementKind.OUTSIDE_DIAMETER }
        val idZone = wp.zones.firstOrNull { it.kind == MeasurementKind.INSIDE_DIAMETER }
        val wallZone = wp.zones.firstOrNull { it.kind == MeasurementKind.THICKNESS }

        val odMm = (odZone?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(8f)
        val wallMm = (wallZone?.actualUm?.div(1000f)
            ?: idZone?.let { (odMm - it.actualUm / 1000f) / 2f }
            ?: (odMm * 0.13f)).coerceIn(1f, odMm * 0.40f)
        val idMm = (idZone?.actualUm?.div(1000f) ?: (odMm - 2f * wallMm)).coerceIn(3f, odMm - 2f)
        val pipeLenMm = (odMm * 2.10f).coerceAtLeast(24f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when (z.kind) {
                    MeasurementKind.INSIDE_DIAMETER -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.INTERNAL_BORE,
                            startMmX = pipeLenMm / 2f, startMmY = -idMm / 2f,
                            endMmX = pipeLenMm / 2f, endMmY = idMm / 2f,
                            hotspotMmX = pipeLenMm * 0.45f, hotspotMmY = 0f
                        )
                    )
                    MeasurementKind.THICKNESS -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.DEPTH_VERTICAL,
                            startMmX = pipeLenMm / 2f, startMmY = -odMm / 2f,
                            endMmX = pipeLenMm / 2f, endMmY = -idMm / 2f,
                            hotspotMmX = pipeLenMm * 0.45f, hotspotMmY = -(odMm + idMm) / 4f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = -pipeLenMm * 0.18f, startMmY = -odMm / 2f,
                            endMmX = -pipeLenMm * 0.18f, endMmY = odMm / 2f,
                            hotspotMmX = -pipeLenMm * 0.18f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = pipeLenMm,
            primaryOuterDiaMm = odMm,
            secondaryDiaMm = idMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = 0f,
            totalDepthMm = 0f,
            wallThicknessMm = wallMm,
            boundingWidthMm = pipeLenMm + odMm * 0.35f,
            boundingHeightMm = odMm,
            zoneGuides = guides
        )
    }

    private fun buildHexBolt(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val majorZone = wp.zones.firstOrNull { it.kind == MeasurementKind.OUTSIDE_DIAMETER }
        val headZone = wp.zones.firstOrNull { it.kind == MeasurementKind.THICKNESS }
        val lenZone = wp.zones.firstOrNull { it.kind == MeasurementKind.STEP }

        val threadDiaMm = (majorZone?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(4f)
        val headAcrossFlatsMm = (headZone?.actualUm?.div(1000f) ?: (threadDiaMm * 1.7f)).coerceAtLeast(threadDiaMm * 1.35f)
        val shankLenMm = (lenZone?.actualUm?.div(1000f) ?: (threadDiaMm * 4.2f)).coerceAtLeast(threadDiaMm * 1.8f)
        val headThickMm = (threadDiaMm * 0.68f).coerceAtLeast(4f)
        val totalLenMm = headThickMm + shankLenMm
        val leftX = -totalLenMm / 2f
        val shankStartX = leftX + headThickMm

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when (z.kind) {
                    MeasurementKind.THICKNESS -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = leftX + headThickMm * 0.5f, startMmY = -headAcrossFlatsMm / 2f,
                            endMmX = leftX + headThickMm * 0.5f, endMmY = headAcrossFlatsMm / 2f,
                            hotspotMmX = leftX + headThickMm * 0.5f, hotspotMmY = 0f
                        )
                    )
                    MeasurementKind.STEP -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.STEP_HORIZONTAL,
                            startMmX = shankStartX, startMmY = headAcrossFlatsMm / 2f,
                            endMmX = shankStartX + shankLenMm, endMmY = headAcrossFlatsMm / 2f,
                            hotspotMmX = shankStartX + shankLenMm * 0.5f, hotspotMmY = threadDiaMm * 0.55f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = shankStartX + shankLenMm * 0.65f, startMmY = -threadDiaMm / 2f,
                            endMmX = shankStartX + shankLenMm * 0.65f, endMmY = threadDiaMm / 2f,
                            hotspotMmX = shankStartX + shankLenMm * 0.65f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = totalLenMm,
            primaryOuterDiaMm = headAcrossFlatsMm,
            secondaryDiaMm = threadDiaMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = headThickMm,
            totalDepthMm = 0f,
            wallThicknessMm = 0f,
            boundingWidthMm = totalLenMm,
            boundingHeightMm = headAcrossFlatsMm,
            zoneGuides = guides
        )
    }

    private fun buildHexNut(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val flatsZone = wp.zones.firstOrNull { it.zoneId.contains("flat") || it.kind == MeasurementKind.OUTSIDE_DIAMETER }
            ?: wp.zones.filter { it.kind == MeasurementKind.THICKNESS }.maxByOrNull { it.actualUm }
        val boreZone = wp.zones.firstOrNull { it.kind == MeasurementKind.INSIDE_DIAMETER }
        val heightZone = wp.zones.firstOrNull { it.zoneId.contains("height") }
            ?: wp.zones.filter { it.kind == MeasurementKind.THICKNESS }.minByOrNull { it.actualUm }

        val acrossFlatsMm = (flatsZone?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(8f)
        val boreDiaMm = (boreZone?.actualUm?.div(1000f) ?: (acrossFlatsMm * 0.52f)).coerceIn(3f, acrossFlatsMm * 0.78f)
        val nutHeightMm = (heightZone?.actualUm?.div(1000f) ?: (acrossFlatsMm * 0.48f)).coerceAtLeast(3f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when {
                    z.kind == MeasurementKind.INSIDE_DIAMETER -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.INTERNAL_BORE,
                            startMmX = 0f, startMmY = -boreDiaMm / 2f,
                            endMmX = 0f, endMmY = boreDiaMm / 2f,
                            hotspotMmX = 0f, hotspotMmY = 0f
                        )
                    )
                    z.zoneId == heightZone?.zoneId && z.zoneId != flatsZone?.zoneId -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_HORIZONTAL,
                            startMmX = -nutHeightMm / 2f, startMmY = acrossFlatsMm * 0.62f,
                            endMmX = nutHeightMm / 2f, endMmY = acrossFlatsMm * 0.62f,
                            hotspotMmX = 0f, hotspotMmY = acrossFlatsMm * 0.56f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = acrossFlatsMm * 0.60f, startMmY = -acrossFlatsMm / 2f,
                            endMmX = acrossFlatsMm * 0.60f, endMmY = acrossFlatsMm / 2f,
                            hotspotMmX = acrossFlatsMm * 0.42f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = acrossFlatsMm * 1.155f,
            primaryOuterDiaMm = acrossFlatsMm,
            secondaryDiaMm = boreDiaMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = nutHeightMm,
            totalDepthMm = 0f,
            wallThicknessMm = nutHeightMm,
            boundingWidthMm = acrossFlatsMm * 1.35f,
            boundingHeightMm = acrossFlatsMm * 1.20f,
            zoneGuides = guides
        )
    }

    private fun buildFlatPlate(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val thickZone = wp.zones.firstOrNull { it.kind == MeasurementKind.THICKNESS }
        val widthZone = wp.zones.firstOrNull { it.kind == MeasurementKind.OUTSIDE_DIAMETER || it.kind == MeasurementKind.STEP }

        val thickMm = (thickZone?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(1.5f)
        val widthMm = (widthZone?.actualUm?.div(1000f) ?: (thickMm * 4.5f)).coerceAtLeast(thickMm * 2f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                if (z.kind == MeasurementKind.THICKNESS) {
                    put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = widthMm * 0.48f, startMmY = -thickMm / 2f,
                            endMmX = widthMm * 0.48f, endMmY = thickMm / 2f,
                            hotspotMmX = widthMm * 0.35f, hotspotMmY = 0f
                        )
                    )
                } else {
                    put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_HORIZONTAL,
                            startMmX = -widthMm / 2f, startMmY = thickMm / 2f,
                            endMmX = widthMm / 2f, endMmY = thickMm / 2f,
                            hotspotMmX = 0f, hotspotMmY = thickMm / 2f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = widthMm,
            primaryOuterDiaMm = thickMm,
            secondaryDiaMm = 0f,
            tertiaryDiaMm = 0f,
            stepLengthMm = 0f,
            totalDepthMm = 0f,
            wallThicknessMm = thickMm,
            boundingWidthMm = widthMm * 1.15f,
            boundingHeightMm = max(thickMm * 2.4f, widthMm * 0.42f),
            zoneGuides = guides
        )
    }

    private fun buildHoledBlock(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val heightZone = wp.zones.firstOrNull { it.kind == MeasurementKind.THICKNESS || it.kind == MeasurementKind.OUTSIDE_DIAMETER }
        val boreZone = wp.zones.firstOrNull { it.kind == MeasurementKind.INSIDE_DIAMETER }
        val depthZone = wp.zones.firstOrNull { it.kind == MeasurementKind.DEPTH }

        val blockHeightMm = (heightZone?.actualUm?.div(1000f)
            ?: depthZone?.let { it.actualUm / 1000f * 1.65f }
            ?: fallbackMm).coerceAtLeast(10f)
        val holeDiaMm = (boreZone?.actualUm?.div(1000f) ?: (blockHeightMm * 0.65f)).coerceAtLeast(4f)
        val holeDepthMm = (depthZone?.actualUm?.div(1000f) ?: (blockHeightMm * 0.56f)).coerceIn(2f, blockHeightMm * 0.90f)
        val blockWidthMm = max(holeDiaMm * 1.95f, blockHeightMm * 1.45f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when (z.kind) {
                    MeasurementKind.INSIDE_DIAMETER -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.INTERNAL_BORE,
                            startMmX = -holeDiaMm / 2f, startMmY = -blockHeightMm / 2f + holeDepthMm * 0.35f,
                            endMmX = holeDiaMm / 2f, endMmY = -blockHeightMm / 2f + holeDepthMm * 0.35f,
                            hotspotMmX = 0f, hotspotMmY = -blockHeightMm / 2f + holeDepthMm * 0.35f
                        )
                    )
                    MeasurementKind.DEPTH -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.DEPTH_VERTICAL,
                            startMmX = 0f, startMmY = -blockHeightMm / 2f,
                            endMmX = 0f, endMmY = -blockHeightMm / 2f + holeDepthMm,
                            hotspotMmX = holeDiaMm * 0.22f, hotspotMmY = -blockHeightMm / 2f + holeDepthMm * 0.5f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = -blockWidthMm * 0.5f, startMmY = -blockHeightMm / 2f,
                            endMmX = -blockWidthMm * 0.5f, endMmY = blockHeightMm / 2f,
                            hotspotMmX = -blockWidthMm * 0.38f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = blockWidthMm,
            primaryOuterDiaMm = blockHeightMm,
            secondaryDiaMm = holeDiaMm,
            tertiaryDiaMm = 0f,
            stepLengthMm = 0f,
            totalDepthMm = holeDepthMm,
            wallThicknessMm = blockHeightMm,
            boundingWidthMm = blockWidthMm,
            boundingHeightMm = blockHeightMm,
            zoneGuides = guides
        )
    }

    private fun buildSteppedBoreBlock(wp: Workpiece, fallbackMm: Float): WorkpieceGeometryProfile {
        val heightZone = wp.zones.firstOrNull { it.kind == MeasurementKind.THICKNESS }
        val boreZones = wp.zones.filter { it.kind == MeasurementKind.INSIDE_DIAMETER }.sortedByDescending { it.actualUm }
        val depthZones = wp.zones.filter { it.kind == MeasurementKind.DEPTH }.sortedBy { it.actualUm }

        val blockHeightMm = (heightZone?.actualUm?.div(1000f) ?: fallbackMm).coerceAtLeast(14f)
        val topDiaMm = (boreZones.firstOrNull()?.actualUm?.div(1000f) ?: (blockHeightMm * 0.78f)).coerceAtLeast(6f)
        val botDiaMm = (boreZones.getOrNull(1)?.actualUm?.div(1000f) ?: (topDiaMm * 0.58f)).coerceIn(3f, topDiaMm * 0.88f)
        val step1DepthMm = (depthZones.firstOrNull()?.actualUm?.div(1000f) ?: (blockHeightMm * 0.35f)).coerceIn(2f, blockHeightMm * 0.65f)
        val totalDepthMm = (depthZones.lastOrNull()?.actualUm?.div(1000f) ?: (blockHeightMm * 0.80f)).coerceIn(step1DepthMm + 2f, blockHeightMm * 0.94f)
        val blockWidthMm = max(topDiaMm * 1.75f, blockHeightMm * 1.45f)

        val guides = buildMap {
            wp.zones.forEach { z ->
                val mm = z.actualUm / 1000f
                when {
                    boreZones.isNotEmpty() && z.zoneId == boreZones[0].zoneId -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.INTERNAL_BORE,
                            startMmX = -topDiaMm / 2f, startMmY = -blockHeightMm / 2f + step1DepthMm * 0.5f,
                            endMmX = topDiaMm / 2f, endMmY = -blockHeightMm / 2f + step1DepthMm * 0.5f,
                            hotspotMmX = 0f, hotspotMmY = -blockHeightMm / 2f + step1DepthMm * 0.5f
                        )
                    )
                    boreZones.size >= 2 && z.zoneId == boreZones[1].zoneId -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.INTERNAL_BORE,
                            startMmX = -botDiaMm / 2f, startMmY = -blockHeightMm / 2f + (step1DepthMm + totalDepthMm) * 0.5f,
                            endMmX = botDiaMm / 2f, endMmY = -blockHeightMm / 2f + (step1DepthMm + totalDepthMm) * 0.5f,
                            hotspotMmX = 0f, hotspotMmY = -blockHeightMm / 2f + (step1DepthMm + totalDepthMm) * 0.5f
                        )
                    )
                    depthZones.isNotEmpty() && z.zoneId == depthZones[0].zoneId -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.DEPTH_VERTICAL,
                            startMmX = topDiaMm * 0.32f, startMmY = -blockHeightMm / 2f,
                            endMmX = topDiaMm * 0.32f, endMmY = -blockHeightMm / 2f + step1DepthMm,
                            hotspotMmX = topDiaMm * 0.32f, hotspotMmY = -blockHeightMm / 2f + step1DepthMm * 0.5f
                        )
                    )
                    z.kind == MeasurementKind.DEPTH -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.DEPTH_VERTICAL,
                            startMmX = -botDiaMm * 0.25f, startMmY = -blockHeightMm / 2f,
                            endMmX = -botDiaMm * 0.25f, endMmY = -blockHeightMm / 2f + totalDepthMm,
                            hotspotMmX = -botDiaMm * 0.25f, hotspotMmY = -blockHeightMm / 2f + totalDepthMm * 0.5f
                        )
                    )
                    else -> put(
                        z.zoneId,
                        ZoneDimensionGuide(
                            zoneId = z.zoneId, label = z.label, kind = z.kind, actualUm = z.actualUm, actualMm = mm,
                            style = DimensionGuideStyle.EXTERNAL_VERTICAL,
                            startMmX = -blockWidthMm * 0.5f, startMmY = -blockHeightMm / 2f,
                            endMmX = -blockWidthMm * 0.5f, endMmY = blockHeightMm / 2f,
                            hotspotMmX = -blockWidthMm * 0.38f, hotspotMmY = 0f
                        )
                    )
                }
            }
        }

        return WorkpieceGeometryProfile(
            workpieceId = wp.id,
            shapeType = wp.shapeType,
            primaryLengthMm = blockWidthMm,
            primaryOuterDiaMm = blockHeightMm,
            secondaryDiaMm = topDiaMm,
            tertiaryDiaMm = botDiaMm,
            stepLengthMm = step1DepthMm,
            totalDepthMm = totalDepthMm,
            wallThicknessMm = blockHeightMm,
            boundingWidthMm = blockWidthMm,
            boundingHeightMm = blockHeightMm,
            zoneGuides = guides
        )
    }
}
