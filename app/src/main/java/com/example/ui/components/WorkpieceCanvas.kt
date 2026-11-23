package com.example.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.DimensionGuideStyle
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.engine.WorkpieceGeometryEngine
import com.example.domain.engine.WorkpieceGeometryProfile
import com.example.domain.engine.ZoneDimensionGuide
import com.example.domain.model.MeasurementZone
import com.example.domain.model.Workpiece
import com.example.domain.model.WorkpieceShapeType
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.SteelDark
import com.example.ui.theme.SteelLight
import com.example.ui.theme.SteelMid
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.ToleranceGreen
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

enum class WorkpieceCameraMode(val label: String) {
    ROTATE("Putar 360°"),
    PAN("Geser (Pan)")
}

/**
 * Interactive 360°-rotatable, tiltable, zoomable, pannable parametric CAD Canvas viewer
 * for all machining workpieces.
 *
 * Every geometric proportion, scale bar, and measurement zone anchor is computed directly from
 * WorkpieceGeometryEngine using the workpiece's stored integer-µm actual dimensions.
 */
@Composable
fun WorkpieceViewportCanvas(
    workpiece: Workpiece,
    selectedZone: MeasurementZone?,
    rotationDeg: Float,
    tiltDeg: Float = 14f,
    zoomScale: Float,
    panOffset: Offset,
    cameraMode: WorkpieceCameraMode = WorkpieceCameraMode.ROTATE,
    isBlindMode: Boolean,
    onTransform: (deltaRotationDeg: Float, deltaTiltDeg: Float, zoomFactor: Float, panDelta: Offset) -> Unit,
    onSelectZone: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile = remember(workpiece) { WorkpieceGeometryEngine.buildProfile(workpiece) }
    val currentProfile by rememberUpdatedState(profile)
    val currentCameraMode by rememberUpdatedState(cameraMode)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnSelectZone by rememberUpdatedState(onSelectZone)
    val currentRot by rememberUpdatedState(rotationDeg)
    val currentZoom by rememberUpdatedState(zoomScale)
    val currentPan by rememberUpdatedState(panOffset)

    val animRotationDeg by animateFloatAsState(
        targetValue = rotationDeg,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "wp_rot"
    )
    val animTiltDeg by animateFloatAsState(
        targetValue = tiltDeg,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "wp_tilt"
    )
    val animZoom by animateFloatAsState(
        targetValue = zoomScale.coerceIn(0.6f, 2.5f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "wp_zoom"
    )
    val animPan by animateOffsetAsState(
        targetValue = panOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "wp_pan"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF143C57), Color(0xFF071F30))
                )
            )
            .border(1.5.dp, TealSecondary.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("workpiece_viewport_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapPos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val prof = currentProfile
                        val metrics = computeViewportMetrics(w, h, prof, currentRot, currentZoom, currentPan)
                        var nearestZoneId: String? = null
                        var nearestDist = Float.MAX_VALUE
                        prof.zoneGuides.values.forEach { guide ->
                            val pt = metrics.mmToScreen(guide.hotspotMmX, guide.hotspotMmY)
                            val d = hypot(tapPos.x - pt.x, tapPos.y - pt.y)
                            if (d < 46f && d < nearestDist) {
                                nearestDist = d
                                nearestZoneId = guide.zoneId
                            }
                        }
                        nearestZoneId?.let { currentOnSelectZone(it) }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val isZooming = abs(zoom - 1f) > 0.005f
                        when {
                            isZooming -> {
                                // Multi-touch pinch zooms and pans simultaneously
                                currentOnTransform(0f, 0f, zoom, pan)
                            }
                            currentCameraMode == WorkpieceCameraMode.PAN -> {
                                // Pan mode: drag translates freely on both X and Y axes
                                currentOnTransform(0f, 0f, 1f, pan)
                            }
                            else -> {
                                // Rotate mode: horizontal drags yaw 360°, vertical drags tilt pitch
                                currentOnTransform(pan.x * 0.65f, -pan.y * 0.35f, 1f, Offset.Zero)
                            }
                        }
                    }
                }
        ) {
            drawWorkpieceScene(
                workpiece = workpiece,
                profile = profile,
                selectedZone = selectedZone,
                rotationDeg = animRotationDeg,
                tiltDeg = animTiltDeg,
                zoomScale = animZoom,
                panOffset = animPan,
                isBlindMode = isBlindMode
            )
        }
    }
}

private data class ViewportMetrics(
    val cx: Float,
    val cy: Float,
    val pxPerMm: Float,
    val horizontalForeshorten: Float,
    val ellipseDepth: Float
) {
    fun mmToScreen(mmX: Float, mmY: Float): Offset = Offset(
        x = cx + mmX * pxPerMm * horizontalForeshorten,
        y = cy + mmY * pxPerMm
    )
}

private fun computeViewportMetrics(
    w: Float,
    h: Float,
    profile: WorkpieceGeometryProfile,
    rotationDeg: Float,
    zoomScale: Float,
    panOffset: Offset
): ViewportMetrics {
    val cx = w * 0.50f + panOffset.x.coerceIn(-w * 0.35f, w * 0.35f)
    val cy = h * 0.51f + panOffset.y.coerceIn(-h * 0.30f, h * 0.30f)
    val basePxPerMm = min(
        (w * 0.62f) / profile.boundingWidthMm.coerceAtLeast(10f),
        (h * 0.52f) / profile.boundingHeightMm.coerceAtLeast(10f)
    )
    val pxPerMm = basePxPerMm * zoomScale.coerceIn(0.6f, 2.5f)
    val rad = Math.toRadians(rotationDeg.toDouble())
    val horizontalForeshorten = (0.72f + 0.28f * abs(cos(rad).toFloat()))
    val ellipseDepth = (0.24f + 0.26f * abs(sin(rad).toFloat())).coerceIn(0.18f, 0.52f)
    return ViewportMetrics(cx, cy, pxPerMm, horizontalForeshorten, ellipseDepth)
}

private fun DrawScope.drawWorkpieceScene(
    workpiece: Workpiece,
    profile: WorkpieceGeometryProfile,
    selectedZone: MeasurementZone?,
    rotationDeg: Float,
    tiltDeg: Float,
    zoomScale: Float,
    panOffset: Offset,
    isBlindMode: Boolean
) {
    val w = size.width
    val h = size.height
    val metrics = computeViewportMetrics(w, h, profile, rotationDeg, zoomScale, panOffset)
    val cx = metrics.cx
    val cy = metrics.cy
    val pxPerMm = metrics.pxPerMm
    val hx = metrics.horizontalForeshorten
    val rad = Math.toRadians(rotationDeg.toDouble())
    val sinR = sin(rad).toFloat()
    val tiltFactor = (sin(Math.toRadians(tiltDeg.toDouble())).toFloat()).coerceIn(-0.6f, 0.6f)
    val ellipseDepth = (metrics.ellipseDepth + abs(tiltFactor) * 0.14f).coerceIn(0.18f, 0.58f)

    // 1. CAD Blueprint Grid calibrated to 5 mm divisions
    val gridMmStep = if (pxPerMm < 3.2f) 10f else 5f
    val gridPx = (gridMmStep * pxPerMm).coerceIn(18f, 52f)
    val gridColor = CyanHighlight.copy(alpha = 0.10f)
    var gx = cx % gridPx
    while (gx < w) {
        drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 1f)
        gx += gridPx
    }
    var gy = cy % gridPx
    while (gy < h) {
        drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f)
        gy += gridPx
    }
    // Optical datum centerlines
    drawLine(
        color = CyanHighlight.copy(alpha = 0.22f),
        start = Offset(12f, cy),
        end = Offset(w - 12f, cy),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f, 3f, 6f))
    )

    // 2. Material-aware metallic shaders
    val (metalBrush, capBrush) = materialBrushesFor(workpiece)
    val outlineColor = Color(0xFF0A1B27)

    // 3. Draw Parametric Workpiece strictly from profile millimeter data!
    when (profile.shapeType) {
        WorkpieceShapeType.CYLINDER_SHAFT -> {
            val lenPx = profile.primaryLengthMm * pxPerMm * hx
            val diaPx = profile.primaryOuterDiaMm * pxPerMm
            drawCylinderSegment(cx - lenPx / 2f, cy, lenPx, diaPx, ellipseDepth, metalBrush, capBrush, outlineColor)
        }
        WorkpieceShapeType.STEPPED_SHAFT -> {
            val totalLenPx = profile.primaryLengthMm * pxPerMm * hx
            val stepLenPx = profile.stepLengthMm * pxPerMm * hx
            val largeLenPx = (totalLenPx - stepLenPx).coerceAtLeast(10f)
            val largeDiaPx = profile.primaryOuterDiaMm * pxPerMm
            val smallDiaPx = profile.secondaryDiaMm * pxPerMm
            val leftX = cx - totalLenPx / 2f
            drawCylinderSegment(leftX, cy, largeLenPx, largeDiaPx, ellipseDepth, metalBrush, capBrush, outlineColor)
            drawCylinderSegment(leftX + largeLenPx, cy, stepLenPx, smallDiaPx, ellipseDepth, metalBrush, capBrush, outlineColor)
        }
        WorkpieceShapeType.GROOVED_SHAFT -> {
            val totalLenPx = profile.primaryLengthMm * pxPerMm * hx
            val grooveW = profile.stepLengthMm * pxPerMm * hx
            val segLen = ((totalLenPx - grooveW) / 2f).coerceAtLeast(8f)
            val odPx = profile.primaryOuterDiaMm * pxPerMm
            val grooveDiaPx = profile.secondaryDiaMm * pxPerMm
            val startX = cx - totalLenPx / 2f
            drawCylinderSegment(startX, cy, segLen, odPx, ellipseDepth, metalBrush, capBrush, outlineColor)
            drawCylinderSegment(startX + segLen, cy, grooveW, grooveDiaPx, ellipseDepth, metalBrush, capBrush, outlineColor)
            drawCylinderSegment(startX + segLen + grooveW, cy, segLen, odPx, ellipseDepth, metalBrush, capBrush, outlineColor)
        }
        WorkpieceShapeType.RING, WorkpieceShapeType.BUSHING_RING, WorkpieceShapeType.PIPE -> {
            val lenPx = (profile.primaryLengthMm * pxPerMm * hx).coerceAtLeast(10f)
            val odPx = profile.primaryOuterDiaMm * pxPerMm
            val idPx = profile.secondaryDiaMm * pxPerMm
            drawCylinderSegment(cx - lenPx / 2f, cy, lenPx, odPx, ellipseDepth, metalBrush, capBrush, outlineColor)
            // Inner through-bore ellipse drawn strictly at ID/OD ratio
            val boreW = (idPx * ellipseDepth).coerceAtLeast(6f)
            val rightFaceX = cx + lenPx / 2f
            drawOval(
                color = Color(0xFF081926),
                topLeft = Offset(rightFaceX - boreW / 2f, cy - idPx / 2f),
                size = Size(boreW, idPx)
            )
            // Inner bore cylindrical depth highlight
            drawOval(
                color = CyanHighlight.copy(alpha = 0.85f),
                topLeft = Offset(rightFaceX - boreW / 2f, cy - idPx / 2f),
                size = Size(boreW, idPx),
                style = Stroke(1.8f)
            )
            // Subtle inner dashed bore lines showing hollow passage through body
            val leftFaceX = cx - lenPx / 2f
            drawLine(
                color = CyanHighlight.copy(alpha = 0.35f),
                start = Offset(leftFaceX, cy - idPx / 2f),
                end = Offset(rightFaceX, cy - idPx / 2f),
                strokeWidth = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
            )
            drawLine(
                color = CyanHighlight.copy(alpha = 0.35f),
                start = Offset(leftFaceX, cy + idPx / 2f),
                end = Offset(rightFaceX, cy + idPx / 2f),
                strokeWidth = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
            )
        }
        WorkpieceShapeType.HEX_BOLT -> {
            val totalLenPx = profile.primaryLengthMm * pxPerMm * hx
            val headW = profile.stepLengthMm * pxPerMm * hx
            val shankLen = (totalLenPx - headW).coerceAtLeast(14f)
            val headH = profile.primaryOuterDiaMm * pxPerMm
            val threadDia = profile.secondaryDiaMm * pxPerMm
            val startX = cx - totalLenPx / 2f

            // 3-facet shaded hex head
            drawRect(metalBrush, Offset(startX, cy - headH / 2f), Size(headW, headH))
            drawRect(outlineColor, Offset(startX, cy - headH / 2f), Size(headW, headH), style = Stroke(2f))
            drawLine(outlineColor, Offset(startX, cy - headH * 0.25f), Offset(startX + headW, cy - headH * 0.25f), 1.4f)
            drawLine(outlineColor, Offset(startX, cy + headH * 0.25f), Offset(startX + headW, cy + headH * 0.25f), 1.4f)

            // Cylindrical shank + metric helical threads (drawn proportionally to pitch)
            drawCylinderSegment(startX + headW, cy, shankLen, threadDia, ellipseDepth, metalBrush, capBrush, outlineColor)
            val threadPitchPx = (1.5f * pxPerMm * hx).coerceIn(5f, 12f)
            var tx = startX + headW + shankLen * 0.30f
            while (tx < startX + headW + shankLen - 5f) {
                drawLine(
                    color = outlineColor.copy(alpha = 0.62f),
                    start = Offset(tx, cy - threadDia / 2f),
                    end = Offset(tx + threadPitchPx * 0.55f, cy + threadDia / 2f),
                    strokeWidth = 1.4f
                )
                tx += threadPitchPx
            }
        }
        WorkpieceShapeType.HEX_NUT -> {
            val acrossFlatsPx = profile.primaryOuterDiaMm * pxPerMm
            val circumRadius = acrossFlatsPx / 1.73205f // exact geometry: R = s / sqrt(3)
            val boreRadius = (profile.secondaryDiaMm * pxPerMm) / 2f
            val extrudeDx = profile.stepLengthMm * pxPerMm * 0.35f * sinR
            val extrudeDy = profile.stepLengthMm * pxPerMm * 0.30f * tiltFactor

            fun buildHexPath(centerX: Float, centerY: Float): Path = Path().apply {
                for (i in 0 until 6) {
                    val angle = (i * 60.0 + rotationDeg) * PI / 180.0
                    val px = centerX + (circumRadius * cos(angle)).toFloat()
                    val py = centerY + (circumRadius * sin(angle)).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }

            // Rear extruded nut face for depth realism
            val backHex = buildHexPath(cx - extrudeDx, cy - extrudeDy)
            drawPath(backHex, color = Color(0xFF475C6B))
            drawPath(backHex, color = outlineColor, style = Stroke(1.8f))

            // Front hex face + chamfer circle + threaded bore
            val frontHex = buildHexPath(cx, cy)
            drawPath(frontHex, brush = metalBrush)
            drawPath(frontHex, color = outlineColor, style = Stroke(2.2f))
            drawCircle(
                color = outlineColor.copy(alpha = 0.35f),
                radius = acrossFlatsPx * 0.48f,
                center = Offset(cx, cy),
                style = Stroke(1.2f)
            )
            drawCircle(Color(0xFF081926), radius = boreRadius, center = Offset(cx, cy))
            drawCircle(CyanHighlight, radius = boreRadius, center = Offset(cx, cy), style = Stroke(2.0f))
            // Inner thread ring cue
            drawCircle(
                color = CyanHighlight.copy(alpha = 0.45f),
                radius = (boreRadius * 1.12f).coerceAtMost(acrossFlatsPx * 0.44f),
                center = Offset(cx, cy),
                style = Stroke(1.1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            )
        }
        WorkpieceShapeType.FLAT_PLATE -> {
            val pw = profile.primaryLengthMm * pxPerMm * hx
            val ph = (profile.primaryOuterDiaMm * pxPerMm).coerceAtLeast(10f)
            val depthObliqueY = (pw * 0.16f * (0.5f + abs(tiltFactor))).coerceIn(10f, 38f)
            val skewX = pw * 0.16f * sinR
            val topFace = Path().apply {
                moveTo(cx - pw / 2f + skewX, cy - ph / 2f - depthObliqueY)
                lineTo(cx + pw / 2f + skewX, cy - ph / 2f - depthObliqueY)
                lineTo(cx + pw / 2f, cy - ph / 2f)
                lineTo(cx - pw / 2f, cy - ph / 2f)
                close()
            }
            drawPath(topFace, brush = Brush.horizontalGradient(listOf(SteelLight, Color.White, SteelMid)))
            drawPath(topFace, color = outlineColor, style = Stroke(1.6f))
            drawRect(metalBrush, Offset(cx - pw / 2f, cy - ph / 2f), Size(pw, ph))
            drawRect(outlineColor, Offset(cx - pw / 2f, cy - ph / 2f), Size(pw, ph), style = Stroke(2f))
        }
        WorkpieceShapeType.HOLED_BLOCK, WorkpieceShapeType.STEPPED_BORE_BLOCK -> {
            val bw = profile.primaryLengthMm * pxPerMm * hx
            val bh = profile.primaryOuterDiaMm * pxPerMm
            val topY = cy - bh / 2f
            val leftX = cx - bw / 2f

            // Isometric top face
            val topTiltH = (bh * 0.22f * (0.45f + abs(tiltFactor))).coerceIn(8f, 28f)
            val topSkewX = bw * 0.10f * sinR
            val topPlane = Path().apply {
                moveTo(leftX + topSkewX, topY - topTiltH)
                lineTo(leftX + bw + topSkewX, topY - topTiltH)
                lineTo(leftX + bw, topY)
                lineTo(leftX, topY)
                close()
            }
            drawPath(topPlane, brush = Brush.horizontalGradient(listOf(SteelMid, Color(0xFFEAF2F7), SteelLight)))
            drawPath(topPlane, color = outlineColor, style = Stroke(1.5f))

            // Cross-section block body
            drawRect(metalBrush, Offset(leftX, topY), Size(bw, bh))
            drawRect(outlineColor, Offset(leftX, topY), Size(bw, bh), style = Stroke(2f))

            // Exact parametric cutaway bore(s)
            val topHoleW = profile.secondaryDiaMm * pxPerMm * hx
            if (profile.shapeType == WorkpieceShapeType.STEPPED_BORE_BLOCK) {
                val step1H = profile.stepLengthMm * pxPerMm
                val totalH = profile.totalDepthMm * pxPerMm
                val botHoleW = profile.tertiaryDiaMm * pxPerMm * hx
                drawRect(Color(0xFF102A3C), Offset(cx - topHoleW / 2f, topY), Size(topHoleW, step1H))
                drawRect(CyanHighlight, Offset(cx - topHoleW / 2f, topY), Size(topHoleW, step1H), style = Stroke(1.7f))
                drawRect(Color(0xFF081926), Offset(cx - botHoleW / 2f, topY + step1H), Size(botHoleW, (totalH - step1H).coerceAtLeast(4f)))
                drawRect(CyanHighlight, Offset(cx - botHoleW / 2f, topY + step1H), Size(botHoleW, (totalH - step1H).coerceAtLeast(4f)), style = Stroke(1.7f))
            } else {
                val holeH = profile.totalDepthMm * pxPerMm
                drawRect(Color(0xFF0A1F2E), Offset(cx - topHoleW / 2f, topY), Size(topHoleW, holeH))
                drawRect(CyanHighlight, Offset(cx - topHoleW / 2f, topY), Size(topHoleW, holeH), style = Stroke(1.7f))
            }
        }
    }

    // 4. Draw Tappable Hotspot Dots for all measurement areas + Exact CAD Dimension Overlay for Selected Zone
    profile.zoneGuides.values.forEach { guide ->
        val isSelected = selectedZone?.zoneId == guide.zoneId
        val hotspot = metrics.mmToScreen(guide.hotspotMmX, guide.hotspotMmY)
        if (!isSelected) {
            drawCircle(Color(0xFF0A2233), radius = 8f, center = hotspot)
            drawCircle(CyanHighlight, radius = 8f, center = hotspot, style = Stroke(2f))
            drawCircle(CyanHighlight.copy(alpha = 0.7f), radius = 3.5f, center = hotspot)
        }
    }

    selectedZone?.let { zone ->
        profile.zoneGuides[zone.zoneId]?.let { activeGuide ->
            drawZoneDimensionOverlay(
                guide = activeGuide,
                zone = zone,
                metrics = metrics,
                isBlindMode = isBlindMode
            )
        }
    }

    // 5. Blueprint Telemetry HUD (top-left) + Calibrated Physical Scale Bar (bottom-left)
    val hudPaint = CanvasPaintCache.monoBold(
        colorArgb = CyanHighlight.toArgb(),
        textSizePx = 20f,
        align = Paint.Align.LEFT
    )
    val normRot = ((rotationDeg.toInt() % 360) + 360) % 360
    drawContext.canvas.nativeCanvas.drawText(
        "ROT ${normRot}° • TILT ${tiltDeg.toInt()}° • ZOOM ${(zoomScale * 100).toInt()}%",
        14f,
        28f,
        hudPaint
    )

    // Calibrated Scale Bar directly tied to pxPerMm
    val scaleMm = when {
        pxPerMm >= 8f -> 5
        pxPerMm >= 3.5f -> 10
        else -> 20
    }
    val barPx = scaleMm * pxPerMm
    val barX = 16f
    val barY = h - 18f
    drawLine(ToleranceGreen, Offset(barX, barY), Offset(barX + barPx, barY), 3f, cap = StrokeCap.Round)
    drawLine(ToleranceGreen, Offset(barX, barY - 6f), Offset(barX, barY + 6f), 2f)
    drawLine(ToleranceGreen, Offset(barX + barPx, barY - 6f), Offset(barX + barPx, barY + 6f), 2f)
    val scalePaint = CanvasPaintCache.monoBold(
        colorArgb = Color(0xFFD6F5E3).toArgb(),
        textSizePx = 18f,
        align = Paint.Align.LEFT
    )
    drawContext.canvas.nativeCanvas.drawText("Skala $scaleMm mm", barX, barY - 9f, scalePaint)
}

private fun DrawScope.drawZoneDimensionOverlay(
    guide: ZoneDimensionGuide,
    zone: MeasurementZone,
    metrics: ViewportMetrics,
    isBlindMode: Boolean
) {
    val p1 = metrics.mmToScreen(guide.startMmX, guide.startMmY)
    val p2 = metrics.mmToScreen(guide.endMmX, guide.endMmY)
    val accent = OrangeAccent

    // Offset external dimension lines slightly clear of the part edge for crisp CAD readability
    val (d1, d2) = when (guide.style) {
        DimensionGuideStyle.EXTERNAL_HORIZONTAL, DimensionGuideStyle.STEP_HORIZONTAL -> {
            val offsetY = if (guide.startMmY < 0f) -22f else 24f
            // Extension witness lines
            drawLine(accent.copy(alpha = 0.65f), p1, Offset(p1.x, p1.y + offsetY), 1.4f)
            drawLine(accent.copy(alpha = 0.65f), p2, Offset(p2.x, p2.y + offsetY), 1.4f)
            Offset(p1.x, p1.y + offsetY) to Offset(p2.x, p2.y + offsetY)
        }
        DimensionGuideStyle.EXTERNAL_VERTICAL -> {
            val offsetX = if (guide.startMmX <= 0f) -24f else 24f
            drawLine(accent.copy(alpha = 0.65f), p1, Offset(p1.x + offsetX, p1.y), 1.4f)
            drawLine(accent.copy(alpha = 0.65f), p2, Offset(p2.x + offsetX, p2.y), 1.4f)
            Offset(p1.x + offsetX, p1.y) to Offset(p2.x + offsetX, p2.y)
        }
        DimensionGuideStyle.INTERNAL_BORE, DimensionGuideStyle.DEPTH_VERTICAL -> p1 to p2
    }

    drawLine(
        color = accent,
        start = d1,
        end = d2,
        strokeWidth = 2.6f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 4f))
    )
    drawCircle(accent, radius = 5.5f, center = d1)
    drawCircle(accent, radius = 5.5f, center = d2)

    val valueText = if (isBlindMode) {
        "${zone.label}: ?? (Nom ${MeasurementUnitConverter.formatUmAsMm(zone.nominalUm, 2, commaDecimal = true)} mm)"
    } else {
        "${zone.label}: ${MeasurementUnitConverter.formatUmAsMm(zone.actualUm, 2, commaDecimal = true)} mm"
    }

    val labelPaint = CanvasPaintCache.monoBold(
        colorArgb = Color.White.toArgb(),
        textSizePx = 22f,
        align = Paint.Align.RIGHT
    )
    drawContext.canvas.nativeCanvas.drawText(valueText, size.width - 14f, size.height - 14f, labelPaint)
}

private fun materialBrushesFor(workpiece: Workpiece): Pair<Brush, Brush> {
    val mat = workpiece.material.lowercase()
    return when {
        mat.contains("perunggu") || mat.contains("kuningan") -> {
            Brush.verticalGradient(listOf(Color(0xFF7B5728), Color(0xFFD8AC63), Color(0xFFF6DFB0), Color(0xFFA77A3C), Color(0xFF5D3F1A))) to
                Brush.radialGradient(listOf(Color(0xFFF1D49E), Color(0xFFC08E46), Color(0xFF6B4A20)))
        }
        mat.contains("aluminium") -> {
            Brush.verticalGradient(listOf(Color(0xFF8C9EA9), Color(0xFFE7F0F5), Color.White, Color(0xFFBACAD4), Color(0xFF71838F))) to
                Brush.radialGradient(listOf(Color(0xFFF4F9FC), Color(0xFFC8D6E0), Color(0xFF80929E)))
        }
        mat.contains("stainless") -> {
            Brush.verticalGradient(listOf(Color(0xFF607482), Color(0xFFE5EFF5), Color.White, Color(0xFF9EB3C2), Color(0xFF475B69))) to
                Brush.radialGradient(listOf(Color(0xFFF3F9FC), Color(0xFFB5C7D4), Color(0xFF556A78)))
        }
        else -> {
            Brush.verticalGradient(listOf(SteelDark, SteelLight, Color.White, SteelMid, Color(0xFF5E7180))) to
                Brush.radialGradient(listOf(SteelLight, SteelMid, SteelDark))
        }
    }
}

private fun DrawScope.drawCylinderSegment(
    leftX: Float,
    centerY: Float,
    length: Float,
    diameter: Float,
    ellipseDepth: Float,
    bodyBrush: Brush,
    capBrush: Brush,
    strokeColor: Color
) {
    val capW = (diameter * ellipseDepth).coerceAtLeast(8f)
    // Left rear ellipse
    drawOval(bodyBrush, Offset(leftX - capW / 2f, centerY - diameter / 2f), Size(capW, diameter))
    drawOval(strokeColor, Offset(leftX - capW / 2f, centerY - diameter / 2f), Size(capW, diameter), style = Stroke(1.6f))
    // Cylinder body
    drawRect(bodyBrush, Offset(leftX, centerY - diameter / 2f), Size(length, diameter))
    drawLine(strokeColor, Offset(leftX, centerY - diameter / 2f), Offset(leftX + length, centerY - diameter / 2f), 1.8f)
    drawLine(strokeColor, Offset(leftX, centerY + diameter / 2f), Offset(leftX + length, centerY + diameter / 2f), 1.8f)
    // Right front face
    drawOval(capBrush, Offset(leftX + length - capW / 2f, centerY - diameter / 2f), Size(capW, diameter))
    drawOval(strokeColor, Offset(leftX + length - capW / 2f, centerY - diameter / 2f), Size(capW, diameter), style = Stroke(1.8f))
}
