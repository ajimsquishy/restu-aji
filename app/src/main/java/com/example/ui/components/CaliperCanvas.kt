package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
import com.example.domain.engine.DigitalCaliperState
import com.example.domain.engine.ManualCaliperState
import com.example.domain.engine.VernierScaleGeometry
import com.example.domain.model.CaliperResolution
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.WorkpieceShapeType
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LcdBezelAccent
import com.example.ui.theme.LcdBezelDark
import com.example.ui.theme.LcdDigitInk
import com.example.ui.theme.LcdScreenBg
import com.example.ui.theme.LcdScreenBgOff
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.SteelDark
import com.example.ui.theme.SteelEdge
import com.example.ui.theme.SteelLight
import com.example.ui.theme.SteelMid
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceRed
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CALIPER_ZERO_ORIGIN_FRAC = 0.115f
private const val CALIPER_BEAM_END_FRAC = 0.905f
private const val CALIPER_TOTAL_BEAM_MM = 202f

/**
 * Pure geometric layout metrics shared between touch/drag gestures, Lock Screw hit-testing,
 * and Canvas rendering (Single Source of Truth across Manual & Digital Caliper modes).
 */
internal data class CaliperLayoutMetrics(
    val beamStartX: Float,
    val zeroOriginX: Float,
    val beamEndX: Float,
    val fixedJawWidth: Float,
    val insideFaceShift: Float,
    val fixedInsideFaceX: Float,
    val pxPerMm: Float,
    val visibleBeamMm: Float,
    val maxDepthRodSpace: Float
) {
    fun jawOffsetPx(jawPositionUm: Int): Float =
        ((jawPositionUm / 1000f) * pxPerMm).coerceIn(0f, 150f * pxPerMm)

    fun movableX(jawPositionUm: Int): Float = zeroOriginX + jawOffsetPx(jawPositionUm)

    /** Right-facing outer knife-edge measuring face of the movable inside jaw. */
    fun movableInsideFaceX(jawPositionUm: Int): Float = movableX(jawPositionUm) - insideFaceShift

    /** Exact right tip X coordinate of the protruding depth rod. */
    fun depthRodTipX(jawPositionUm: Int): Float =
        beamEndX + jawOffsetPx(jawPositionUm).coerceIn(0f, maxDepthRodSpace)
}

internal fun computeCaliperLayout(
    canvasWidth: Float,
    measurementKind: MeasurementKind,
    workpieceContactUm: Int?
): CaliperLayoutMetrics {
    val w = canvasWidth.coerceAtLeast(100f)
    val beamStartX: Float
    val zeroOriginX: Float
    val beamEndX: Float
    val visibleBeamMm: Float

    when {
        measurementKind == MeasurementKind.DEPTH && workpieceContactUm != null && workpieceContactUm <= 45_000 -> {
            // Spacious right-side depth stage: fits up to 45 mm hole + 49 mm vernier slider with zero clipping
            beamStartX = w * 0.024f
            zeroOriginX = w * 0.095f
            beamEndX = w * 0.640f
            visibleBeamMm = 104f
        }
        measurementKind == MeasurementKind.DEPTH -> {
            // Full 0..150 mm depth rod fits 1:1 on screen without clamping distortion
            beamStartX = w * 0.022f
            zeroOriginX = w * 0.085f
            beamEndX = w * 0.585f
            visibleBeamMm = CALIPER_TOTAL_BEAM_MM
        }
        measurementKind == MeasurementKind.INSIDE_DIAMETER && workpieceContactUm != null && workpieceContactUm <= 45_000 -> {
            // Enhanced detail framing so even small bores (8.5 mm - 20 mm) show crisp inner-wall jaw contact
            beamStartX = w * 0.028f
            zeroOriginX = w * CALIPER_ZERO_ORIGIN_FRAC
            beamEndX = w * CALIPER_BEAM_END_FRAC
            visibleBeamMm = 110f
        }
        else -> {
            beamStartX = w * 0.028f
            zeroOriginX = w * CALIPER_ZERO_ORIGIN_FRAC
            beamEndX = w * CALIPER_BEAM_END_FRAC
            visibleBeamMm = CALIPER_TOTAL_BEAM_MM
        }
    }

    val pxPerMm = ((beamEndX - zeroOriginX) / visibleBeamMm).coerceAtLeast(1.2f)
    val fixedJawWidth = (zeroOriginX - beamStartX).coerceAtLeast(w * 0.060f)
    val insideFaceShift = fixedJawWidth * 0.38f
    val fixedInsideFaceX = zeroOriginX - insideFaceShift
    val rightMargin = if (measurementKind == MeasurementKind.DEPTH) 18f else 4f
    val maxDepthRodSpace = (w - beamEndX - rightMargin).coerceAtLeast(16f)

    return CaliperLayoutMetrics(
        beamStartX = beamStartX,
        zeroOriginX = zeroOriginX,
        beamEndX = beamEndX,
        fixedJawWidth = fixedJawWidth,
        insideFaceShift = insideFaceShift,
        fixedInsideFaceX = fixedInsideFaceX,
        pxPerMm = pxPerMm,
        visibleBeamMm = visibleBeamMm,
        maxDepthRodSpace = maxDepthRodSpace
    )
}

/**
 * Interactive Realistic 3D-Shaded Canvas for Manual Vernier Caliper.
 * Single Source of Truth: fixed/movable sculpted jaws with ground knife-edge tips,
 * layered 3D upper clamp & knurled lock screw, recessed bright satin main scale track,
 * beveled Vernier plate, depth rod, stop pin, and thumb wheel all derive geometrically
 * from [state.jawPositionUm] and [state.snappedUm].
 */
@Composable
fun ManualCaliperCanvas(
    state: ManualCaliperState,
    onDragDeltaUm: (Int) -> Unit,
    onToggleLock: (() -> Unit)? = null,
    workpieceShape: WorkpieceShapeType? = null,
    showCallouts: Boolean = true,
    explodeProgress: Float = 0f,
    isXRayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentOnDrag by rememberUpdatedState(onDragDeltaUm)
    val currentOnToggleLock by rememberUpdatedState(onToggleLock)
    val currentLocked by rememberUpdatedState(state.isLocked)
    val currentJawUm by rememberUpdatedState(state.jawPositionUm)
    val currentResolution by rememberUpdatedState(state.resolution)
    val currentKind by rememberUpdatedState(state.measurementKind)
    val currentContactUm by rememberUpdatedState(state.workpieceContactUm)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    if (isXRayMode) listOf(Color(0xFF061B29), Color(0xFF0B2A3E))
                    else listOf(Color(0xFF6A737B), Color(0xFF7D868E), Color(0xFF636C74))
                )
            )
            .border(
                1.dp,
                if (isXRayMode) CyanHighlight.copy(alpha = 0.5f) else Color(0xFF47525C),
                RoundedCornerShape(16.dp)
            )
            .testTag("manual_caliper_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val layout = computeCaliperLayout(w, currentKind, currentContactUm)
                        val movableX = layout.movableX(currentJawUm)
                        val sliderWidth = (layout.pxPerMm * (currentResolution.scaleSpanMm + 6f))
                            .coerceIn(w * 0.14f, (layout.beamEndX - layout.zeroOriginX) * 0.54f)
                        val lockCenterX = movableX + sliderWidth * 0.32f
                        val beamY = h * 0.355f
                        val beamH = (h * 0.145f).coerceIn(26f, 44f)
                        val upperClampH = (beamH * 0.56f).coerceIn(14f, 24f)
                        val lockCenterY = beamY - upperClampH - 12f
                        // Direct tap on knurled Lock Screw toggles lock
                        if (kotlin.math.abs(offset.x - lockCenterX) <= 38f &&
                            kotlin.math.abs(offset.y - lockCenterY) <= 36f
                        ) {
                            currentOnToggleLock?.invoke()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        if (!currentLocked) {
                            val layout = computeCaliperLayout(size.width.toFloat(), currentKind, currentContactUm)
                            val deltaUm = ((dragAmount / layout.pxPerMm) * 1000f).roundToInt()
                            if (deltaUm != 0) currentOnDrag(deltaUm)
                        }
                    }
                }
        ) {
            drawCaliperInstrument(
                jawPositionUm = state.jawPositionUm,
                effectiveUm = state.snappedUm,
                isLocked = state.isLocked,
                isInContact = state.isInContactWithWorkpiece,
                measurementKind = state.measurementKind,
                workpieceContactUm = state.workpieceContactUm,
                workpieceShape = workpieceShape,
                resolution = state.resolution,
                vernierCoincidentIndex = state.vernierCoincidentIndex,
                thumbWheelAngleDeg = state.thumbWheelAngleDeg,
                isDigital = false,
                digitalText = "",
                digitalUnit = MeasurementUnit.MILLIMETER,
                digitalPowerOn = false,
                digitalHold = false,
                digitalZeroOffset = false,
                showCallouts = showCallouts,
                explodeProgress = explodeProgress,
                isXRayMode = isXRayMode
            )
        }
    }
}

/**
 * Interactive Realistic Canvas for Digital Vernier Caliper with integrated live LCD display.
 */
@Composable
fun DigitalCaliperCanvas(
    state: DigitalCaliperState,
    onDragDeltaUm: (Int) -> Unit,
    workpieceShape: WorkpieceShapeType? = null,
    showCallouts: Boolean = false,
    explodeProgress: Float = 0f,
    isXRayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentOnDrag by rememberUpdatedState(onDragDeltaUm)
    val currentLocked by rememberUpdatedState(state.isLocked)
    val currentKind by rememberUpdatedState(state.measurementKind)
    val currentContactUm by rememberUpdatedState(state.workpieceContactUm)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    if (isXRayMode) listOf(Color(0xFF061B29), Color(0xFF0B2A3E))
                    else listOf(Color(0xFFD6E5F0), Color(0xFFE9F2F8))
                )
            )
            .border(
                1.dp,
                if (isXRayMode) CyanHighlight.copy(alpha = 0.5f) else Color(0xFFB8CDDC),
                RoundedCornerShape(16.dp)
            )
            .testTag("digital_caliper_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        if (!currentLocked) {
                            val layout = computeCaliperLayout(size.width.toFloat(), currentKind, currentContactUm)
                            val deltaUm = ((dragAmount / layout.pxPerMm) * 1000f).roundToInt()
                            if (deltaUm != 0) currentOnDrag(deltaUm)
                        }
                    }
                }
        ) {
            drawCaliperInstrument(
                jawPositionUm = state.jawPositionUm,
                effectiveUm = state.sensorUm,
                isLocked = state.isLocked,
                isInContact = state.isInContactWithWorkpiece,
                measurementKind = state.measurementKind,
                workpieceContactUm = state.workpieceContactUm,
                workpieceShape = workpieceShape,
                resolution = CaliperResolution.RES_0_05_MM,
                vernierCoincidentIndex = 0,
                thumbWheelAngleDeg = (state.jawPositionUm / 1000f) * 36f,
                isDigital = true,
                digitalText = state.formattedLcdText,
                digitalUnit = state.unit,
                digitalPowerOn = state.isPowerOn,
                digitalHold = state.isHoldActive,
                digitalZeroOffset = state.zeroOffsetUm != 0,
                showCallouts = showCallouts,
                explodeProgress = explodeProgress,
                isXRayMode = isXRayMode
            )
        }
    }
}

private fun DrawScope.drawCaliperInstrument(
    jawPositionUm: Int,
    effectiveUm: Int,
    isLocked: Boolean,
    isInContact: Boolean,
    measurementKind: MeasurementKind,
    workpieceContactUm: Int?,
    workpieceShape: WorkpieceShapeType?,
    resolution: CaliperResolution,
    vernierCoincidentIndex: Int,
    thumbWheelAngleDeg: Float,
    isDigital: Boolean,
    digitalText: String,
    digitalUnit: MeasurementUnit,
    digitalPowerOn: Boolean,
    digitalHold: Boolean,
    digitalZeroOffset: Boolean,
    showCallouts: Boolean,
    explodeProgress: Float,
    isXRayMode: Boolean
) {
    val w = size.width
    val h = size.height
    val beamY = h * 0.355f
    val beamH = (h * 0.145f).coerceIn(26f, 44f)
    val railH = (beamH * 0.18f).coerceIn(4f, 8f)
    val layout = computeCaliperLayout(w, measurementKind, workpieceContactUm)
    val zeroOriginX = layout.zeroOriginX
    val beamStartX = layout.beamStartX
    val beamEndX = layout.beamEndX
    val pxPerMm = layout.pxPerMm
    val fixedJawWidth = layout.fixedJawWidth
    val fixedInsideFaceX = layout.fixedInsideFaceX

    // Exact linear pixel position from single source of truth (jawPositionUm)
    val jawOffsetPx = layout.jawOffsetPx(jawPositionUm)
    val movableX = layout.movableX(jawPositionUm)
    val movableInsideFaceX = layout.movableInsideFaceX(jawPositionUm)

    val explodeY = explodeProgress * (h * 0.075f)
    val explodeX = explodeProgress * (w * 0.025f)

    // 3D Mechanical Material Palette matching crisp technical satin-steel reference
    val jawMetalBrush = if (isXRayMode) {
        Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.20f), TealSecondary.copy(alpha = 0.10f)))
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFE3E8EC), Color(0xFFD0D6DA), Color(0xFFBCC4C9), Color(0xFFA7B0B7)),
            start = Offset(0f, beamY - h * 0.25f),
            end = Offset(0f, beamY + beamH + h * 0.45f)
        )
    }
    val satinScaleTrackBrush = if (isXRayMode) {
        Brush.verticalGradient(listOf(Color(0xFF0A2A3D), Color(0xFF0F3850)))
    } else {
        Brush.verticalGradient(
            0.0f to Color(0xFFC5D8DC),
            0.14f to Color(0xFFE4F1F3),
            0.55f to Color(0xFFDCEDEF),
            0.88f to Color(0xFFD0E3E6),
            1.0f to Color(0xFFB8CDD1)
        )
    }
    val guideRailBrush = if (isXRayMode) {
        Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.26f), TealSecondary.copy(alpha = 0.14f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFB3BCC2), Color(0xFF8F99A0), Color(0xFF727C84)))
    }
    val bevelFacetBrush = if (isXRayMode) {
        Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.35f), CyanHighlight.copy(alpha = 0.15f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE8F6F8), Color(0xFFCFE6EA), Color(0xFFBCD9DF)))
    }
    val sliderCastShadowBrush = if (isXRayMode) {
        Brush.verticalGradient(listOf(TealSecondary.copy(alpha = 0.28f), NavyDark.copy(alpha = 0.4f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF8B949C), Color(0xFF757E86), Color(0xFF666F77)))
    }
    val upperClampFaceBrush = if (isXRayMode) {
        Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.24f), TealSecondary.copy(alpha = 0.12f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE4E9EC), Color(0xFFD2D8DC), Color(0xFFBDC5CB)))
    }
    val strokeColor = if (isXRayMode) CyanHighlight else Color(0xFF1A232B)
    val shadowColor = if (isXRayMode) Color.Transparent else Color(0xFF141B21).copy(alpha = 0.30f)

    // Subtle engineering grid backdrop
    val gridColor = when {
        isXRayMode -> CyanHighlight.copy(alpha = 0.10f)
        isDigital -> Color(0xFF9EB5C7).copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val stepGrid = (w / 24f).coerceAtLeast(18f)
    var gx = 0f
    while (gx < w) {
        drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 1f)
        gx += stepGrid
    }
    var gy = 0f
    while (gy < h) {
        drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f)
        gy += stepGrid
    }

    // Geometry landmarks for jaws & depth stage
    val insideTipY = (beamY - h * 0.275f).coerceAtLeast(10f)
    val upperShoulderY = beamY - railH - (beamY - insideTipY) * 0.26f
    val insideTipW = (fixedJawWidth * 0.22f).coerceIn(6f, 11f)
    val outsideTipY = (beamY + beamH + h * 0.445f).coerceAtMost(h - 10f)
    val outsideJawLen = outsideTipY - (beamY + beamH + railH)

    // Authentic 3D machined metallic material palette matched to selected workpiece shape
    val isBronzePart = workpieceShape == WorkpieceShapeType.BUSHING_RING
    val isDarkSteelPart = workpieceShape == WorkpieceShapeType.HEX_BOLT || workpieceShape == WorkpieceShapeType.HEX_NUT
    val wpBrush = when {
        isXRayMode -> Brush.verticalGradient(
            listOf(CyanHighlight.copy(alpha = 0.25f), TealSecondary.copy(alpha = 0.12f), CyanHighlight.copy(alpha = 0.22f))
        )
        isBronzePart -> Brush.verticalGradient(
            0.00f to Color(0xFF8C692B),
            0.22f to Color(0xFFF3D999),
            0.48f to Color(0xFFD4AA54),
            0.80f to Color(0xFF9C752F),
            1.00f to Color(0xFF6B4E1B)
        )
        isDarkSteelPart -> Brush.verticalGradient(
            0.00f to Color(0xFF475569),
            0.24f to Color(0xFFD0DBE4),
            0.52f to Color(0xFF8A99AA),
            0.85f to Color(0xFF4B5B6C),
            1.00f to Color(0xFF2D3845)
        )
        else -> Brush.verticalGradient(
            0.00f to Color(0xFF6D7E8B),
            0.20f to Color(0xFFE9F1F6),
            0.34f to Color(0xFFFAFDFF),
            0.62f to Color(0xFFB5C4CF),
            0.88f to Color(0xFF728391),
            1.00f to Color(0xFF4D5C68)
        )
    }
    val wpStroke = when {
        isInContact && !isXRayMode -> Color(0xFF15803D)
        isXRayMode -> CyanHighlight
        isBronzePart -> Color(0xFF4A3410)
        else -> Color(0xFF1D2832)
    }
    val boreInteriorBrush = Brush.verticalGradient(
        0.0f to Color(0xFF1E2D36),
        0.35f to Color(0xFF344955),
        0.65f to Color(0xFF2B3E49),
        1.0f to Color(0xFF1A2730)
    )

    // 1. DEPTH STAGE & DEPTH ROD (proper Z-order: hollow cavity -> depth rod -> C-shaped cross-section block!)
    val maxDepthRodSpace = layout.maxDepthRodSpace
    val depthRodLength = jawOffsetPx.coerceIn(0f, maxDepthRodSpace)
    val depthRodH = (beamH * 0.24f).coerceIn(6f, 11f)
    val depthRodY = beamY + (beamH - depthRodH) / 2f + explodeY * 0.35f
    val depthCavityPad = (depthRodH * 0.60f).coerceIn(5f, 8f)
    val depthCavityTop = depthRodY - depthCavityPad
    val depthCavityBottom = depthRodY + depthRodH + depthCavityPad
    val depthWallT = (beamH * 0.44f).coerceIn(13f, 22f)
    val depthBlockTop = depthCavityTop - depthWallT
    val depthBlockBottom = depthCavityBottom + depthWallT
    val depthBottomWallW = 14f

    val wpPx = workpieceContactUm?.let { (it / 1000f) * pxPerMm }
    val holeDepthPx = if (measurementKind == MeasurementKind.DEPTH && wpPx != null) {
        wpPx.coerceIn(4f, maxDepthRodSpace)
    } else {
        null
    }

    // 1A. Recessed hollow blind-hole cavity behind the depth rod (DEPTH mode)
    if (holeDepthPx != null) {
        if (!isXRayMode) {
            drawRect(
                color = shadowColor,
                topLeft = Offset(beamEndX + 3f, depthBlockTop + 5f),
                size = Size(holeDepthPx + depthBottomWallW, depthBlockBottom - depthBlockTop)
            )
        }
        drawRect(
            brush = boreInteriorBrush,
            topLeft = Offset(beamEndX, depthCavityTop),
            size = Size(holeDepthPx, depthCavityBottom - depthCavityTop)
        )
    }

    // 1B. Metallic 3D Depth Rod (protruding from beamEndX by exact jawOffsetPx, flush at 0 mm!)
    if (depthRodLength > 0.5f) {
        if (!isXRayMode && holeDepthPx == null) {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(beamEndX + explodeX + 2f, depthRodY + 3f),
                size = Size(depthRodLength, depthRodH),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF1F5F8), Color(0xFFB5BFC6), Color(0xFF7D8790))),
            topLeft = Offset(beamEndX + explodeX, depthRodY),
            size = Size(depthRodLength, depthRodH),
            cornerRadius = CornerRadius(1.5f, 1.5f)
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(beamEndX + explodeX, depthRodY),
            size = Size(depthRodLength, depthRodH),
            cornerRadius = CornerRadius(1.5f, 1.5f),
            style = Stroke(1.3f)
        )
    }

    // 1C. Single-piece solid C-shaped blind-hole cross-section block around the cavity (DEPTH mode)
    if (holeDepthPx != null) {
        val blindHolePath = Path().apply {
            moveTo(beamEndX, depthBlockTop)
            lineTo(beamEndX + holeDepthPx + depthBottomWallW, depthBlockTop)
            lineTo(beamEndX + holeDepthPx + depthBottomWallW, depthBlockBottom)
            lineTo(beamEndX, depthBlockBottom)
            lineTo(beamEndX, depthCavityBottom)
            lineTo(beamEndX + holeDepthPx, depthCavityBottom)
            lineTo(beamEndX + holeDepthPx, depthCavityTop)
            lineTo(beamEndX, depthCavityTop)
            close()
        }
        drawPath(blindHolePath, brush = wpBrush)
        // Subtle technical cross-section hatching on top & bottom cut walls
        val hatchColor = Color.White.copy(alpha = 0.20f)
        var hx = beamEndX + 6f
        while (hx < beamEndX + holeDepthPx + depthBottomWallW - 4f) {
            drawLine(hatchColor, Offset(hx, depthBlockTop + 2f), Offset((hx - 6f).coerceAtLeast(beamEndX + 1f), depthCavityTop - 2f), 1.0f)
            drawLine(hatchColor, Offset(hx, depthCavityBottom + 2f), Offset((hx - 6f).coerceAtLeast(beamEndX + 1f), depthBlockBottom - 2f), 1.0f)
            hx += 9f
        }
        // Specular top bevel
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(beamEndX + 2f, depthBlockTop + 2f),
            end = Offset(beamEndX + holeDepthPx + depthBottomWallW - 2f, depthBlockTop + 2f),
            strokeWidth = 2f
        )
        drawPath(blindHolePath, color = wpStroke, style = Stroke(1.6f))
        // Crisp contact highlight on hole bottom wall & beam-end datum when depth rod reaches bottom
        if (isInContact && !isXRayMode) {
            drawLine(
                color = ToleranceGreen,
                start = Offset(beamEndX + holeDepthPx, depthCavityTop),
                end = Offset(beamEndX + holeDepthPx, depthCavityBottom),
                strokeWidth = 3f
            )
            drawLine(
                color = ToleranceGreen,
                start = Offset(beamEndX, depthBlockTop),
                end = Offset(beamEndX, depthBlockBottom),
                strokeWidth = 2.4f
            )
        }
        // Depth dimension guide line beneath blind-hole block
        val dimY = depthBlockBottom + 10f
        drawLine(
            color = if (isInContact) ToleranceGreen else CyanHighlight,
            start = Offset(beamEndX, dimY),
            end = Offset(beamEndX + holeDepthPx, dimY),
            strokeWidth = 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f))
        )
        drawLine(
            color = if (isInContact) ToleranceGreen else CyanHighlight,
            start = Offset(beamEndX, depthBlockBottom + 4f),
            end = Offset(beamEndX, dimY + 4f),
            strokeWidth = 1.5f
        )
        drawLine(
            color = if (isInContact) ToleranceGreen else CyanHighlight,
            start = Offset(beamEndX + holeDepthPx, depthBlockBottom + 4f),
            end = Offset(beamEndX + holeDepthPx, dimY + 4f),
            strokeWidth = 1.5f
        )
    }

    // 2. WORKPIECE RENDERED IN ACTIVE MEASUREMENT ZONE (OUTSIDE, INSIDE, STEP — exact 1:1 scale)
    val boreRingTop = insideTipY + (upperShoulderY - insideTipY) * 0.08f
    val boreRingBottom = (upperShoulderY - 3f).coerceAtLeast(boreRingTop + 14f)
    val boreWidthPx = if (measurementKind == MeasurementKind.INSIDE_DIAMETER && wpPx != null) {
        wpPx.coerceIn(6f, 150f * pxPerMm)
    } else {
        null
    }

    if (wpPx != null) {
        when (measurementKind) {
            MeasurementKind.OUTSIDE_DIAMETER, MeasurementKind.THICKNESS -> {
                val wpTop = beamY + beamH + railH + outsideJawLen * 0.24f
                val wpHeight = outsideJawLen * 0.64f
                val clampedWpWidth = wpPx.coerceIn(6f, 150f * pxPerMm)
                val resolvedShape = workpieceShape ?: if (measurementKind == MeasurementKind.THICKNESS) {
                    WorkpieceShapeType.FLAT_PLATE
                } else {
                    WorkpieceShapeType.CYLINDER_SHAFT
                }
                drawRealistic3DWorkpiece(
                    shapeType = resolvedShape,
                    xLeft = zeroOriginX,
                    centerY = wpTop + wpHeight / 2f,
                    widthPx = clampedWpWidth,
                    heightPx = wpHeight,
                    isInContact = isInContact,
                    isOverPressure = false,
                    isRatchetContact = isInContact,
                    isXRayMode = isXRayMode,
                    dropShadowAlpha = if (isDigital) 0.22f else 0.30f
                )
            }
            MeasurementKind.INSIDE_DIAMETER -> {
                // Hollow ring / bore cross-section aligned EXACTLY to fixedInsideFaceX .. fixedInsideFaceX + boreWidth!
                val boreLeftX = fixedInsideFaceX
                val boreRightX = fixedInsideFaceX + (boreWidthPx ?: 0f)
                val ringH = boreRingBottom - boreRingTop
                val wallW = (boreLeftX - 4f).coerceIn(10f, 20f)
                if (!isXRayMode) {
                    drawRect(
                        color = shadowColor,
                        topLeft = Offset(boreLeftX - wallW + 3f, boreRingTop + 4f),
                        size = Size((boreRightX - boreLeftX) + wallW * 2f, ringH)
                    )
                }
                // Concave dark inner cylinder back-wall behind the inside jaws
                drawRect(
                    brush = boreInteriorBrush,
                    topLeft = Offset(boreLeftX, boreRingTop),
                    size = Size(boreRightX - boreLeftX, ringH)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(boreLeftX, boreRingTop + 1f),
                    end = Offset(boreRightX, boreRingTop + 1f),
                    strokeWidth = 1.4f
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = Offset(boreLeftX, boreRingBottom - 1f),
                    end = Offset(boreRightX, boreRingBottom - 1f),
                    strokeWidth = 1.4f
                )
                // Solid cross-section left & right bore walls (inner surfaces at exact boreLeftX & boreRightX)
                drawRect(wpBrush, Offset(boreLeftX - wallW, boreRingTop), Size(wallW, ringH))
                drawRect(wpBrush, Offset(boreRightX, boreRingTop), Size(wallW, ringH))
                val hatchColor = Color.White.copy(alpha = 0.22f)
                var hy = boreRingTop + 6f
                while (hy < boreRingBottom - 2f) {
                    drawLine(hatchColor, Offset(boreLeftX - wallW + 2f, hy), Offset(boreLeftX - 2f, (hy - 6f).coerceAtLeast(boreRingTop + 1f)), 1.0f)
                    drawLine(hatchColor, Offset(boreRightX + 2f, hy), Offset(boreRightX + wallW - 2f, (hy - 6f).coerceAtLeast(boreRingTop + 1f)), 1.0f)
                    hy += 7f
                }
                drawRect(wpStroke, Offset(boreLeftX - wallW, boreRingTop), Size(wallW, ringH), style = Stroke(1.6f))
                drawRect(wpStroke, Offset(boreRightX, boreRingTop), Size(wallW, ringH), style = Stroke(1.6f))
                // Dashed inside-diameter centerline across the bore opening
                drawLine(
                    color = if (isInContact) ToleranceGreen else CyanHighlight,
                    start = Offset(boreLeftX, boreRingTop + ringH / 2f),
                    end = Offset(boreRightX, boreRingTop + ringH / 2f),
                    strokeWidth = 1.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f))
                )
            }
            MeasurementKind.DEPTH -> {
                // Rendered in Step 1 above with exact depth-rod Z-order
            }
            MeasurementKind.STEP -> {
                val stepWidth = wpPx.coerceIn(6f, 150f * pxPerMm)
                val stepTop = beamY + beamH + railH + outsideJawLen * 0.26f
                val stepH = outsideJawLen * 0.56f
                val stepLPath = Path().apply {
                    moveTo(zeroOriginX - 16f, stepTop)
                    lineTo(zeroOriginX + stepWidth, stepTop)
                    lineTo(zeroOriginX + stepWidth, stepTop + stepH)
                    lineTo(zeroOriginX, stepTop + stepH)
                    lineTo(zeroOriginX, stepTop + stepH * 0.46f)
                    lineTo(zeroOriginX - 16f, stepTop + stepH * 0.46f)
                    close()
                }
                if (!isXRayMode) {
                    drawPath(Path().apply { addPath(stepLPath, Offset(3f, 5f)) }, color = shadowColor)
                }
                drawPath(stepLPath, brush = wpBrush)
                drawPath(stepLPath, color = wpStroke, style = Stroke(1.6f))
            }
        }
    }

    // 3. FIXED JAW & MAIN BEAM (Sculpted 3D Silhouette; Fixed Inside Jaw LEFT measuring face at exact fixedInsideFaceX!)
    val fixedLowerOuterBackX = beamStartX + fixedJawWidth * 0.06f
    val fixedLowerTipOuterX = zeroOriginX - fixedJawWidth * 0.46f
    val fixedBevelH = outsideJawLen * 0.22f
    val fixedReliefH = outsideJawLen * 0.14f

    val fixedJawPath = Path().apply {
        // Upper Fixed Inside Jaw: straight vertical LEFT measuring edge at exact fixedInsideFaceX
        moveTo(zeroOriginX, beamY - railH)
        lineTo(zeroOriginX, upperShoulderY)
        lineTo(fixedInsideFaceX + insideTipW * 1.15f, upperShoulderY)
        lineTo(fixedInsideFaceX + insideTipW, insideTipY + (upperShoulderY - insideTipY) * 0.22f)
        lineTo(fixedInsideFaceX, insideTipY)
        // Straight vertical precision inside measuring face down to upper shoulder
        lineTo(fixedInsideFaceX, upperShoulderY)
        // Left shoulder rounding into beamStartX
        lineTo(beamStartX + 4f, upperShoulderY)
        lineTo(beamStartX, beamY - railH)
        // Left spine down to outside jaw chamfer
        lineTo(beamStartX, beamY + beamH + railH + outsideJawLen * 0.18f)
        lineTo(fixedLowerOuterBackX + fixedJawWidth * 0.42f, outsideTipY - fixedBevelH * 0.65f)
        // Precision chamfered tip to exact zeroOriginX bottom point
        lineTo(zeroOriginX, outsideTipY)
        // Vertical precision outside measuring face back up to beam bottom rail
        lineTo(zeroOriginX, beamY + beamH + railH + fixedReliefH)
        lineTo(zeroOriginX - 3f, beamY + beamH + railH + fixedReliefH * 0.65f)
        lineTo(zeroOriginX, beamY + beamH + railH)
        close()
    }

    // 3D Drop Shadow for Fixed Jaw + Main Beam
    if (!isXRayMode) {
        val beamShadowRectTop = beamY - railH + 5f
        drawRect(
            color = shadowColor,
            topLeft = Offset(beamStartX + 4f, beamShadowRectTop),
            size = Size(beamEndX - beamStartX, beamH + railH * 2f)
        )
        drawPath(
            path = Path().apply {
                addPath(fixedJawPath, Offset(4f, 6f))
            },
            color = shadowColor
        )
    }

    // Draw Fixed Jaw metallic body
    drawPath(fixedJawPath, brush = jawMetalBrush)
    // Bright precision-ground knife-edge bevel facet along LEFT measuring face (fixedInsideFaceX)
    val fixedUpperBevelPath = Path().apply {
        val bevelTopY = insideTipY + 2f
        val bevelRightX = fixedInsideFaceX + insideTipW * 0.62f
        moveTo(fixedInsideFaceX, bevelTopY)
        lineTo(bevelRightX, insideTipY + (upperShoulderY - insideTipY) * 0.18f)
        lineTo(bevelRightX, upperShoulderY)
        lineTo(fixedInsideFaceX, upperShoulderY)
        close()
    }
    drawPath(fixedUpperBevelPath, brush = bevelFacetBrush)
    drawPath(fixedUpperBevelPath, color = strokeColor.copy(alpha = 0.7f), style = Stroke(1.0f))

    // Bright precision-ground knife-edge bevel facet on Lower Fixed Outside Jaw tip
    val fixedLowerBevelPath = Path().apply {
        val bevelTopY = outsideTipY - fixedBevelH
        moveTo(fixedLowerTipOuterX + fixedJawWidth * 0.12f, bevelTopY)
        lineTo(zeroOriginX, bevelTopY)
        lineTo(zeroOriginX, outsideTipY)
        lineTo(fixedLowerTipOuterX + fixedJawWidth * 0.12f, outsideTipY - fixedBevelH * 0.30f)
        close()
    }
    drawPath(fixedLowerBevelPath, brush = bevelFacetBrush)
    drawPath(fixedLowerBevelPath, color = strokeColor.copy(alpha = 0.75f), style = Stroke(1.0f))

    // Crisp outer outline on Fixed Jaw
    drawPath(fixedJawPath, color = strokeColor, style = Stroke(1.6f))

    // 4. MAIN BEAM (Raised 3D Top & Bottom Guide Rails + Recessed Bright Satin Scale Track)
    // Top raised guide rail
    drawRect(
        brush = guideRailBrush,
        topLeft = Offset(zeroOriginX, beamY - railH),
        size = Size(beamEndX - zeroOriginX, railH)
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(zeroOriginX, beamY - railH),
        size = Size(beamEndX - zeroOriginX, railH),
        style = Stroke(1.2f)
    )
    // Bottom raised guide rail
    drawRect(
        brush = guideRailBrush,
        topLeft = Offset(zeroOriginX, beamY + beamH),
        size = Size(beamEndX - zeroOriginX, railH)
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(zeroOriginX, beamY + beamH),
        size = Size(beamEndX - zeroOriginX, railH),
        style = Stroke(1.2f)
    )
    // Recessed Bright Satin Scale Track (full width from beamStartX to beamEndX like reference!)
    drawRect(
        brush = satinScaleTrackBrush,
        topLeft = Offset(beamStartX, beamY),
        size = Size(beamEndX - beamStartX, beamH)
    )
    // Subtle 3D inner bevel shadow line at top of recessed track
    if (!isXRayMode) {
        drawLine(
            color = Color(0xFF4F6470).copy(alpha = 0.35f),
            start = Offset(beamStartX + 1f, beamY + 1.5f),
            end = Offset(beamEndX - 1f, beamY + 1.5f),
            strokeWidth = 2f
        )
    }
    drawRect(
        color = strokeColor,
        topLeft = Offset(beamStartX, beamY),
        size = Size(beamEndX - beamStartX, beamH),
        style = Stroke(1.5f)
    )

    // X-Ray internal encoder strip
    if (isXRayMode) {
        val stripY = beamY + beamH * 0.34f
        val stripH = beamH * 0.32f
        drawRect(
            color = OrangeAccent.copy(alpha = 0.28f),
            topLeft = Offset(zeroOriginX, stripY),
            size = Size(beamEndX - zeroOriginX - 8f, stripH)
        )
        var sx = zeroOriginX + 4f
        while (sx < beamEndX - 12f) {
            drawLine(
                color = CyanHighlight.copy(alpha = 0.7f),
                start = Offset(sx, stripY),
                end = Offset(sx, stripY + stripH),
                strokeWidth = 1.5f
            )
            sx += 7f
        }
    }

    // 5. MAIN SCALE ENGRAVED GRADUATIONS (1 mm, 5 mm, 10 mm rising cleanly from bottom edge)
    val labelPaint = CanvasPaintCache.monoBold(
        colorArgb = if (isXRayMode) CyanHighlight.toArgb() else Color(0xFF142029).toArgb(),
        textSizePx = (beamH * 0.31f).coerceIn(13f, 20f),
        align = Paint.Align.CENTER
    )
    val maxMmToDraw = ((beamEndX - zeroOriginX - 18f) / pxPerMm).toInt().coerceAtMost(150)
    val labelStepMm = if (pxPerMm * 10f < 22f) 20 else 10
    for (mm in 0..maxMmToDraw) {
        val tx = zeroOriginX + mm * pxPerMm
        val tickLen = when {
            mm % 10 == 0 -> beamH * 0.48f
            mm % 5 == 0 -> beamH * 0.36f
            else -> beamH * 0.24f
        }
        drawLine(
            color = if (isXRayMode) CyanHighlight else Color(0xFF18242E),
            start = Offset(tx, beamY + beamH),
            end = Offset(tx, beamY + beamH - tickLen),
            strokeWidth = when {
                mm % 10 == 0 -> 1.6f
                mm % 5 == 0 -> 1.25f
                else -> 0.9f
            }
        )
        if (mm % labelStepMm == 0) {
            drawContext.canvas.nativeCanvas.drawText(
                "$mm",
                tx,
                beamY + beamH - tickLen - 4f,
                labelPaint
            )
        }
    }

    // Right-end Engraved Resolution Legend + Precision Stop Pin (matching reference image!)
    val brandPaint = CanvasPaintCache.sansBold(
        colorArgb = if (isXRayMode) CyanHighlight.toArgb() else Color(0xFF1E2D38).toArgb(),
        textSizePx = (beamH * 0.28f).coerceIn(12f, 18f),
        align = Paint.Align.RIGHT
    )
    drawContext.canvas.nativeCanvas.drawText(
        if (isDigital) "0,01 mm DIGITAL" else "Ketelitian ${resolution.label}",
        beamEndX - 10f,
        beamY + beamH * 0.38f,
        brandPaint
    )
    // Circular metallic stop pin at lower-right corner of main beam
    val pinCenter = Offset(beamEndX - 11f, beamY + beamH - 7.5f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFEDF2F5), Color(0xFF8C969E)), center = pinCenter, radius = 4.5f),
        radius = 4.2f,
        center = pinCenter
    )
    drawCircle(strokeColor, radius = 4.2f, center = pinCenter, style = Stroke(1.1f))

    // 6. MOVABLE JAW & LAYERED 3D SLIDER CARRIAGE (driven directly by jawPositionUm)
    val beamSpanPx = (beamEndX - zeroOriginX).coerceAtLeast(100f)
    val sliderWidth = if (isDigital) {
        (beamSpanPx * 0.32f).coerceIn(w * 0.18f, w * 0.26f)
    } else {
        (pxPerMm * (resolution.scaleSpanMm + 6f)).coerceIn(w * 0.14f, beamSpanPx * 0.54f)
    }
    val upperClampH = (beamH * 0.56f).coerceIn(14f, 24f)
    val upperClampTop = beamY - upperClampH - explodeY * 0.65f
    val vernierPlateH = (beamH * 0.64f).coerceIn(18f, 28f)
    val lowerCastH = (beamH * 0.42f).coerceIn(10f, 18f)
    val vernierTopY = beamY + beamH + explodeY * 0.5f
    val carriageBottom = vernierTopY + vernierPlateH + lowerCastH

    // 6A. Upper Movable Inside Jaw (RIGHT vertical measuring edge at exact movableInsideFaceX!)
    // Exact metrological coupling: movableInsideFaceX - fixedInsideFaceX == jawOffsetPx (0.00 mm when closed!)
    val movableJawW = (fixedJawWidth * 0.92f).coerceAtLeast(w * 0.056f)
    val movableUpperJawPath = Path().apply {
        val baseY = beamY - railH - explodeY * 0.5f
        val shoulderY = upperShoulderY - explodeY * 0.5f
        val leftSpineBaseX = movableInsideFaceX - insideTipW * 1.15f
        val rightBridgeX = (movableX + movableJawW * 0.30f).coerceAtLeast(movableInsideFaceX + 4f)
        moveTo(leftSpineBaseX, baseY)
        lineTo(leftSpineBaseX, shoulderY)
        // Curved/tapered left spine rising toward the right-facing vertical knife edge
        lineTo(movableInsideFaceX - insideTipW, insideTipY + (upperShoulderY - insideTipY) * 0.22f - explodeY * 0.5f)
        lineTo(movableInsideFaceX, insideTipY - explodeY * 0.5f)
        // Straight vertical precision inside measuring face dropping to shoulder
        lineTo(movableInsideFaceX, shoulderY)
        lineTo(rightBridgeX, shoulderY)
        lineTo(rightBridgeX, baseY)
        close()
    }
    // 6B. Lower Movable Outside Jaw (mirrors fixed outside jaw with ground knife-edge facet!)
    val movableLowerJawPath = Path().apply {
        val jawTopY = vernierTopY + vernierPlateH
        moveTo(movableX, jawTopY)
        lineTo(movableX, outsideTipY + explodeY * 0.45f)
        // Chamfered tip angling up-right
        lineTo(movableX + movableJawW * 0.46f, outsideTipY - fixedBevelH * 0.65f + explodeY * 0.45f)
        // Tapered outer spine up to stepped slider underside
        lineTo(movableX + movableJawW * 0.96f, carriageBottom)
        lineTo(movableX + sliderWidth, carriageBottom)
        lineTo(movableX + sliderWidth, jawTopY)
        close()
    }

    // 3D Drop shadow beneath Movable Jaws & Slider
    if (!isXRayMode) {
        drawPath(Path().apply { addPath(movableUpperJawPath, Offset(4f, 5f)) }, color = shadowColor)
        drawPath(Path().apply { addPath(movableLowerJawPath, Offset(4f, 6f)) }, color = shadowColor)
    }

    // Draw Upper Movable Inside Jaw + Right-Facing Ground Bevel Facet
    drawPath(movableUpperJawPath, brush = jawMetalBrush)
    val movableUpperBevel = Path().apply {
        val shoulderY = upperShoulderY - explodeY * 0.5f
        val bevelLeftX = movableInsideFaceX - insideTipW * 0.62f
        moveTo(bevelLeftX, insideTipY + (upperShoulderY - insideTipY) * 0.18f - explodeY * 0.5f)
        lineTo(movableInsideFaceX, insideTipY + 2f - explodeY * 0.5f)
        lineTo(movableInsideFaceX, shoulderY)
        lineTo(bevelLeftX, shoulderY)
        close()
    }
    drawPath(movableUpperBevel, brush = bevelFacetBrush)
    drawPath(movableUpperBevel, color = strokeColor.copy(alpha = 0.7f), style = Stroke(1.0f))
    drawPath(movableUpperJawPath, color = strokeColor, style = Stroke(1.5f))

    // Flush contact gleam on BOTH inner bore walls in INSIDE_DIAMETER mode
    if (boreWidthPx != null && isInContact && !isXRayMode) {
        drawLine(
            color = ToleranceGreen,
            start = Offset(fixedInsideFaceX, boreRingTop),
            end = Offset(fixedInsideFaceX, boreRingBottom),
            strokeWidth = 2.8f
        )
        drawLine(
            color = ToleranceGreen,
            start = Offset(fixedInsideFaceX + boreWidthPx, boreRingTop),
            end = Offset(fixedInsideFaceX + boreWidthPx, boreRingBottom),
            strokeWidth = 2.8f
        )
    }

    // Draw Lower Movable Outside Jaw + Shaded 3D Underside Cast Step + Ground Tip Facet
    drawPath(movableLowerJawPath, brush = jawMetalBrush)
    // Darker 3D cast shoulder band directly below the Vernier plate (signature depth cue in reference!)
    val castStepPath = Path().apply {
        val stepTop = vernierTopY + vernierPlateH
        moveTo(movableX, stepTop)
        lineTo(movableX + sliderWidth, stepTop)
        lineTo(movableX + sliderWidth, carriageBottom)
        lineTo(movableX + movableJawW * 0.96f, carriageBottom)
        lineTo(movableX + movableJawW * 0.80f, carriageBottom + lowerCastH * 0.85f)
        lineTo(movableX, carriageBottom + lowerCastH * 0.85f)
        close()
    }
    drawPath(castStepPath, brush = sliderCastShadowBrush)
    drawPath(castStepPath, color = strokeColor.copy(alpha = 0.7f), style = Stroke(1.1f))

    // Ground bright knife-edge tip facet on Lower Movable Outside Jaw
    val movableLowerBevel = Path().apply {
        val bevelTopY = outsideTipY - fixedBevelH + explodeY * 0.45f
        moveTo(movableX, bevelTopY)
        lineTo(movableX + movableJawW * 0.34f, bevelTopY)
        lineTo(movableX + movableJawW * 0.34f, outsideTipY - fixedBevelH * 0.30f + explodeY * 0.45f)
        lineTo(movableX, outsideTipY + explodeY * 0.45f)
        close()
    }
    drawPath(movableLowerBevel, brush = bevelFacetBrush)
    drawPath(movableLowerBevel, color = strokeColor.copy(alpha = 0.75f), style = Stroke(1.0f))
    drawPath(movableLowerJawPath, color = strokeColor, style = Stroke(1.6f))

    // 6C. Upper Layered 3D Clamp Bar (wraps cleanly across top of main beam just like reference!)
    if (!isDigital) {
        val capH = upperClampH * 0.28f
        // Darker top chamfered cap
        drawRect(
            brush = sliderCastShadowBrush,
            topLeft = Offset(movableX, upperClampTop),
            size = Size(sliderWidth, capH)
        )
        // Lighter satin front clamp face
        drawRect(
            brush = upperClampFaceBrush,
            topLeft = Offset(movableX, upperClampTop + capH),
            size = Size(sliderWidth, upperClampH - capH)
        )
        // Subtle 3D shadow line cast onto the main scale below the upper clamp
        if (!isXRayMode) {
            drawLine(
                color = Color.Black.copy(alpha = 0.22f),
                start = Offset(movableX + 1f, upperClampTop + upperClampH + 1.5f),
                end = Offset(movableX + sliderWidth - 1f, upperClampTop + upperClampH + 1.5f),
                strokeWidth = 2.4f
            )
        }
        drawRect(
            color = strokeColor,
            topLeft = Offset(movableX, upperClampTop),
            size = Size(sliderWidth, upperClampH),
            style = Stroke(1.5f)
        )
        drawLine(
            color = strokeColor.copy(alpha = 0.65f),
            start = Offset(movableX, upperClampTop + capH),
            end = Offset(movableX + sliderWidth, upperClampTop + capH),
            strokeWidth = 1.0f
        )
    }

    // 6D. KNURLED CYLINDRICAL LOCK SCREW (top of upper clamp — directly tappable)
    val lockCenterX = movableX + sliderWidth * 0.32f
    val lockHeadW = 24f
    val lockHeadH = 14f
    val lockStemH = 7f
    val lockHeadTopY = upperClampTop - lockStemH - lockHeadH - explodeY * 0.4f

    // Threaded stem collar
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color(0xFF8B959D), Color(0xFFE5EAEF), Color(0xFF6B757E))),
        topLeft = Offset(lockCenterX - 5f, lockHeadTopY + lockHeadH),
        size = Size(10f, lockStemH + 1f)
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(lockCenterX - 5f, lockHeadTopY + lockHeadH),
        size = Size(10f, lockStemH + 1f),
        style = Stroke(1.2f)
    )
    // Cylindrical knurled head (turns orange-accented when locked!)
    val lockHeadBrush = if (isLocked) {
        Brush.horizontalGradient(listOf(Color(0xFFD97706), OrangeAccent, Color(0xFFFBBF24), Color(0xFFB45309)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF9EA8B0), Color(0xFFE8EEF2), Color(0xFFC3CCD3), Color(0xFF7E8891)))
    }
    drawRoundRect(
        brush = lockHeadBrush,
        topLeft = Offset(lockCenterX - lockHeadW / 2f, lockHeadTopY),
        size = Size(lockHeadW, lockHeadH),
        cornerRadius = CornerRadius(2.5f, 2.5f)
    )
    // Vertical knurling flutes across drum head (just like reference image!)
    val fluteCount = 7
    for (f in 1 until fluteCount) {
        val fx = (lockCenterX - lockHeadW / 2f) + (lockHeadW * f / fluteCount.toFloat())
        drawLine(
            color = strokeColor.copy(alpha = 0.55f),
            start = Offset(fx, lockHeadTopY + 1.5f),
            end = Offset(fx, lockHeadTopY + lockHeadH - 1.5f),
            strokeWidth = 1.0f
        )
    }
    drawRoundRect(
        color = if (isLocked) Color(0xFF7C2D12) else strokeColor,
        topLeft = Offset(lockCenterX - lockHeadW / 2f, lockHeadTopY),
        size = Size(lockHeadW, lockHeadH),
        cornerRadius = CornerRadius(2.5f, 2.5f),
        style = Stroke(1.4f)
    )

    // 6E. KNURLED THUMB WHEEL (at bottom-right corner of stepped carriage)
    val wheelCenter = Offset(movableX + sliderWidth - 13f, carriageBottom + 5f + explodeY * 0.4f)
    val wheelRadius = (beamH * 0.28f).coerceIn(8.5f, 14f)
    if (!isXRayMode) {
        drawCircle(shadowColor, radius = wheelRadius, center = wheelCenter + Offset(2f, 3f))
    }
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFF1F5F8), Color(0xFFAEB8C0), Color(0xFF6B7680)), center = wheelCenter, radius = wheelRadius),
        radius = wheelRadius,
        center = wheelCenter
    )
    drawCircle(color = strokeColor, radius = wheelRadius, center = wheelCenter, style = Stroke(1.4f))
    for (i in 0 until 6) {
        val rad = Math.toRadians((thumbWheelAngleDeg + i * 60f).toDouble())
        drawLine(
            color = strokeColor.copy(alpha = 0.78f),
            start = wheelCenter,
            end = Offset(
                wheelCenter.x + (cos(rad) * (wheelRadius - 2f)).toFloat(),
                wheelCenter.y + (sin(rad) * (wheelRadius - 2f)).toFloat()
            ),
            strokeWidth = 1.2f
        )
    }

    if (isDigital) {
        // 7A. DIGITAL LCD HOUSING ON CARRIAGE
        val housingLeft = movableX + 4f
        val housingTop = beamY - beamH * 0.56f - explodeY * 0.4f
        val housingW = sliderWidth - 8f
        val housingH = beamH * 2.24f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(LcdBezelAccent, LcdBezelDark)),
            topLeft = Offset(housingLeft, housingTop),
            size = Size(housingW, housingH),
            cornerRadius = CornerRadius(12f, 12f)
        )
        drawRoundRect(
            color = if (isXRayMode) CyanHighlight else Color(0xFF475569),
            topLeft = Offset(housingLeft, housingTop),
            size = Size(housingW, housingH),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(1.8f)
        )

        val lcdPad = 10f
        val lcdTop = housingTop + housingH * 0.24f
        val lcdH = housingH * 0.48f
        val lcdW = housingW - lcdPad * 2f
        drawRoundRect(
            color = if (digitalPowerOn) LcdScreenBg else LcdScreenBgOff,
            topLeft = Offset(housingLeft + lcdPad, lcdTop),
            size = Size(lcdW, lcdH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color(0xFF10181C),
            topLeft = Offset(housingLeft + lcdPad, lcdTop),
            size = Size(lcdW, lcdH),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(1.5f)
        )

        if (digitalPowerOn) {
            val lcdPaint = CanvasPaintCache.monoBold(
                colorArgb = LcdDigitInk.toArgb(),
                textSizePx = (lcdH * 0.68f).coerceIn(22f, 44f),
                align = Paint.Align.RIGHT
            )
            drawContext.canvas.nativeCanvas.drawText(
                digitalText,
                housingLeft + lcdPad + lcdW - 28f,
                lcdTop + lcdH * 0.76f,
                lcdPaint
            )
            val unitPaint = CanvasPaintCache.sansBold(
                colorArgb = LcdDigitInk.toArgb(),
                textSizePx = (lcdH * 0.28f).coerceIn(11f, 18f),
                align = Paint.Align.RIGHT
            )
            drawContext.canvas.nativeCanvas.drawText(
                digitalUnit.symbol,
                housingLeft + lcdPad + lcdW - 6f,
                lcdTop + lcdH * 0.80f,
                unitPaint
            )
            val statusFlag = buildString {
                if (digitalHold) append("HOLD ")
                if (digitalZeroOffset) append("INC")
            }
            if (statusFlag.isNotEmpty()) {
                val badgePaint = CanvasPaintCache.sansBold(
                    colorArgb = LcdDigitInk.toArgb(),
                    textSizePx = (lcdH * 0.22f).coerceIn(10f, 14f),
                    align = Paint.Align.LEFT
                )
                drawContext.canvas.nativeCanvas.drawText(
                    statusFlag.trim(),
                    housingLeft + lcdPad + 6f,
                    lcdTop + lcdH * 0.30f,
                    badgePaint
                )
            }
        }

        val btnY = housingTop + housingH * 0.78f
        val btnH = housingH * 0.14f
        drawRoundRect(ToleranceRed, Offset(housingLeft + lcdPad + 6f, btnY), Size(22f, btnH), CornerRadius(3f, 3f))
        drawRoundRect(OrangeAccent, Offset(housingLeft + housingW * 0.56f, btnY), Size(24f, btnH), CornerRadius(3f, 3f))
        drawRoundRect(TealSecondary, Offset(housingLeft + housingW * 0.58f, housingTop + 6f), Size(22f, btnH * 0.9f), CornerRadius(3f, 3f))
    } else {
        // 7B. BEVELED BRIGHT VERNIER SCALE PLATE (Flush edge-to-edge under Main Scale like reference!)
        drawRect(
            brush = satinScaleTrackBrush,
            topLeft = Offset(movableX, vernierTopY),
            size = Size(sliderWidth, vernierPlateH)
        )
        drawRect(
            color = strokeColor,
            topLeft = Offset(movableX, vernierTopY),
            size = Size(sliderWidth, vernierPlateH),
            style = Stroke(1.5f)
        )

        // Vernier Graduations derived directly from integer VernierScaleGeometry
        val vernierPitchMm = resolution.scaleSpanMm.toFloat() / resolution.divisions.toFloat()
        val vernierLabelPaint = CanvasPaintCache.monoBold(
            colorArgb = if (isXRayMode) CyanHighlight.toArgb() else Color(0xFF142029).toArgb(),
            textSizePx = (vernierPlateH * 0.42f).coerceIn(9.5f, 14f),
            align = Paint.Align.CENTER
        )
        val vernierOriginX = zeroOriginX + (effectiveUm / 1000f) * pxPerMm
        val labelInterval = when (resolution) {
            CaliperResolution.RES_0_05_MM -> if (pxPerMm * 1.9f < 9f) 4 else 2
            CaliperResolution.RES_0_02_MM -> if (pxPerMm * 4.9f < 15f) 10 else 5
        }

        for (i in 0..resolution.divisions) {
            val vx = vernierOriginX + i * vernierPitchMm * pxPerMm
            if (vx < movableX - 1f || vx > movableX + sliderWidth + 1f) continue
            val isCoincident = i == vernierCoincidentIndex
            val isMajor = when (resolution) {
                CaliperResolution.RES_0_05_MM -> i % 2 == 0
                CaliperResolution.RES_0_02_MM -> i % 5 == 0
            }
            val tickLen = if (isMajor || isCoincident) vernierPlateH * 0.46f else vernierPlateH * 0.30f
            drawLine(
                color = when {
                    isCoincident -> OrangeAccent
                    isXRayMode -> CyanHighlight
                    else -> Color(0xFF142029)
                },
                start = Offset(vx, vernierTopY),
                end = Offset(vx, vernierTopY + tickLen),
                strokeWidth = if (isCoincident) 2.2f else if (isMajor) 1.35f else 0.9f
            )
            if (i % labelInterval == 0 && i <= resolution.divisions) {
                val numberLabel = when (resolution) {
                    CaliperResolution.RES_0_05_MM -> "${i / 2}"
                    CaliperResolution.RES_0_02_MM -> "${i / 5}"
                }
                drawContext.canvas.nativeCanvas.drawText(
                    numberLabel,
                    vx,
                    vernierTopY + vernierPlateH - 3.5f,
                    vernierLabelPaint
                )
            }
        }
    }

    // 8. EDUCATIONAL CALLOUTS (Learn mode / Exploded view)
    if (showCallouts || explodeProgress > 0.25f) {
        drawCalloutBadge("Rahang Dalam", Offset(fixedInsideFaceX, insideTipY + 10f), Offset(zeroOriginX + w * 0.04f, h * 0.08f))
        drawCalloutBadge("Kunci (Lock)", Offset(lockCenterX, lockHeadTopY), Offset((lockCenterX + 8f).coerceIn(w * 0.32f, w * 0.62f), h * 0.08f))
        drawCalloutBadge("Skala Utama", Offset(beamEndX - w * 0.14f, beamY + 2f), Offset(beamEndX - w * 0.12f, h * 0.12f))
        drawCalloutBadge("Rahang Luar", Offset(zeroOriginX - 6f, outsideTipY - 12f), Offset(zeroOriginX + w * 0.04f, h * 0.91f))
        if (!isDigital) {
            drawCalloutBadge(
                "Nonius (${resolution.label})",
                Offset(movableX + sliderWidth * 0.42f, vernierTopY + vernierPlateH),
                Offset((movableX + sliderWidth * 0.42f).coerceIn(w * 0.34f, w * 0.58f), h * 0.91f)
            )
        }
        drawCalloutBadge("Thumb Wheel", wheelCenter, Offset((wheelCenter.x + w * 0.06f).coerceIn(w * 0.56f, w * 0.74f), h * 0.91f))
        drawCalloutBadge(
            "Depth Rod",
            Offset(beamEndX + (depthRodLength * 0.5f).coerceAtLeast(6f), depthRodY + depthRodH),
            Offset((beamEndX + (holeDepthPx ?: depthRodLength) * 0.5f).coerceIn(w * 0.74f, w * 0.89f), h * 0.88f)
        )
    }
}

/**
 * 3.5x Real-time Scale Magnifier Loupe centered on the Vernier zero & coincident graduation mark.
 */
@Composable
fun VernierMagnifierLoupe(
    state: ManualCaliperState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A2233))
            .border(1.5.dp, CyanHighlight, RoundedCornerShape(14.dp))
            .testTag("vernier_magnifier_loupe")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h * 0.50f
            val windowRange = VernierScaleGeometry.loupeWindowRangeMm(state, windowSpanMm = 24f)
            val windowSpan = (windowRange.endInclusive - windowRange.start).coerceAtLeast(1f)
            val pxPerMm = w / windowSpan

            fun mmToX(mm: Float): Float = (mm - windowRange.start) * pxPerMm

            // Top bright satin strip (Main Scale) & bottom beveled strip (Vernier Scale) matching caliper finish
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFCEE0E3), Color(0xFFE6F3F5), Color(0xFFD5E7EA))),
                topLeft = Offset(0f, 0f),
                size = Size(w, midY)
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFE2F0F2), Color(0xFFC9DCE0))),
                topLeft = Offset(0f, midY),
                size = Size(w, h - midY)
            )
            drawLine(NavyDark, Offset(0f, midY), Offset(w, midY), 2f)

            val textPaint = CanvasPaintCache.monoBold(
                colorArgb = NavyDark.toArgb(),
                textSizePx = 22f,
                align = Paint.Align.CENTER
            )

            // Draw Main Scale ticks in visible loupe window (up to 200 mm for 150 mm + 49 mm vernier)
            val startMm = kotlin.math.floor(windowRange.start.toDouble()).toInt().coerceAtLeast(0)
            val endMm = kotlin.math.ceil(windowRange.endInclusive.toDouble()).toInt().coerceAtMost(200)
            for (mm in startMm..endMm) {
                val x = mmToX(mm.toFloat())
                val len = when {
                    mm % 10 == 0 -> midY * 0.56f
                    mm % 5 == 0 -> midY * 0.42f
                    else -> midY * 0.28f
                }
                drawLine(NavyDark, Offset(x, midY), Offset(x, midY - len), if (mm % 5 == 0) 2.2f else 1.3f)
                if (mm % 5 == 0) {
                    drawContext.canvas.nativeCanvas.drawText("$mm", x, midY - len - 6f, textPaint)
                }
            }

            // Draw Vernier Scale ticks directly from integer VernierScaleGeometry (exact 0 µm error at coincident index)
            val smallPaint = CanvasPaintCache.monoBold(
                colorArgb = NavyDark.toArgb(),
                textSizePx = 19f,
                align = Paint.Align.CENTER
            )

            for (i in 0..state.resolution.divisions) {
                val vMm = VernierScaleGeometry.vernierTickPositionUm(state.snappedUm, state.resolution, i) / 1000f
                val x = mmToX(vMm)
                if (x < -10f || x > w + 10f) continue
                val isCoincident = i == state.vernierCoincidentIndex
                val isZero = i == 0
                val isMajor = when (state.resolution) {
                    CaliperResolution.RES_0_05_MM -> i % 2 == 0
                    CaliperResolution.RES_0_02_MM -> i % 5 == 0
                }
                val len = if (isMajor || isCoincident) (h - midY) * 0.48f else (h - midY) * 0.30f

                if (isCoincident) {
                    drawLine(
                        color = OrangeAccent,
                        start = Offset(x, 4f),
                        end = Offset(x, h - 4f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = if (isZero) TealSecondary else NavyDark,
                        start = Offset(x, midY),
                        end = Offset(x, midY + len),
                        strokeWidth = if (isZero || isMajor) 2.2f else 1.2f
                    )
                }

                if (isMajor) {
                    val label = when (state.resolution) {
                        CaliperResolution.RES_0_05_MM -> "${i / 2}"
                        CaliperResolution.RES_0_02_MM -> "${i / 5}"
                    }
                    drawContext.canvas.nativeCanvas.drawText(label, x, h - 8f, smallPaint)
                }
            }
        }
    }
}

internal fun DrawScope.drawCalloutBadge(
    label: String,
    anchor: Offset,
    badgeCenter: Offset
) {
    val paint = CanvasPaintCache.sansBold(
        colorArgb = NavyPrimary.toArgb(),
        textSizePx = 22f,
        align = Paint.Align.CENTER
    )
    val textWidth = paint.measureText(label)
    val boxW = textWidth + 20f
    val boxH = 30f
    val clampedX = badgeCenter.x.coerceIn(boxW / 2f + 6f, size.width - boxW / 2f - 6f)
    val clampedY = badgeCenter.y.coerceIn(boxH / 2f + 4f, size.height - boxH / 2f - 4f)

    drawLine(
        color = OrangeAccent,
        start = anchor,
        end = Offset(clampedX, clampedY),
        strokeWidth = 1.8f
    )
    drawCircle(OrangeAccent, radius = 4f, center = anchor)
    drawRoundRect(
        color = Color.White.copy(alpha = 0.94f),
        topLeft = Offset(clampedX - boxW / 2f, clampedY - boxH / 2f),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = OrangeAccent,
        topLeft = Offset(clampedX - boxW / 2f, clampedY - boxH / 2f),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(1.4f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        clampedX,
        clampedY + 7f,
        paint
    )
}
