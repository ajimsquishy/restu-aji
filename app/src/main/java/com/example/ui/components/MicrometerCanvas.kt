package com.example.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.DigitalMicrometerState
import com.example.domain.engine.ManualMicrometerState
import com.example.domain.engine.MicrometerScaleGeometry
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.WorkpieceShapeType
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LcdBezelAccent
import com.example.ui.theme.LcdBezelDark
import com.example.ui.theme.LcdDigitInk
import com.example.ui.theme.LcdScreenBg
import com.example.ui.theme.LcdScreenBgOff
import com.example.ui.theme.MicrometerFrameBlue
import com.example.ui.theme.MicrometerFrameHighlight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.SteelDark
import com.example.ui.theme.SteelEdge
import com.example.ui.theme.SteelLight
import com.example.ui.theme.SteelMid
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceRed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive Realistic Animated Canvas for Manual Outside Micrometer
 * (0–25 mm range, 0.5 mm leadscrew pitch, 50 thimble divisions, 0.01 mm resolution).
 *
 * Mechanical Coupling:
 * - Vertical drag on Thimble rotates the drum and advances/retracts the spindle proportionally to the 0.5 mm pitch.
 * - Vertical drag or tap on Ratchet Stop engages constant-torque slip clicks.
 * - Direct tap on Lock Lever toggles the spindle clamp lock.
 */
@Composable
fun ManualMicrometerCanvas(
    state: ManualMicrometerState,
    onRotateThimbleDeltaUm: (Int, Boolean) -> Unit,
    onToggleLock: () -> Unit = {},
    onRatchetClick: () -> Unit = {},
    workpieceShape: WorkpieceShapeType? = null,
    showCallouts: Boolean = true,
    explodeProgress: Float = 0f,
    isXRayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentOnRotate by rememberUpdatedState(onRotateThimbleDeltaUm)
    val currentOnToggleLock by rememberUpdatedState(onToggleLock)
    val currentOnRatchetClick by rememberUpdatedState(onRatchetClick)
    val currentLocked by rememberUpdatedState(state.isLocked)
    val currentEffectiveUm by rememberUpdatedState(state.readingUm.coerceAtLeast(0))
    val currentExplode by rememberUpdatedState(explodeProgress)

    // Smooth mechanical animations coupled to spindle position & screw rotation
    val animatedSpindleUm by animateFloatAsState(
        targetValue = state.spindlePositionUm.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spindle_travel"
    )
    val animatedEffectiveUm by animateFloatAsState(
        targetValue = state.readingUm.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "effective_scale_travel"
    )
    val animatedLockAngleDeg by animateFloatAsState(
        targetValue = if (state.isLocked) 34f else -34f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lock_lever_pivot"
    )
    val ratchetPulse = remember { Animatable(0f) }
    LaunchedEffect(state.ratchetClickCount) {
        if (state.ratchetClickCount > 0) {
            ratchetPulse.snapTo(1f)
            ratchetPulse.animateTo(0f, animationSpec = tween(durationMillis = 360))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    if (isXRayMode) listOf(Color(0xFF051824), Color(0xFF0A273B))
                    else listOf(Color(0xFF092234), Color(0xFF143C57))
                )
            )
            .border(
                1.dp,
                if (isXRayMode) CyanHighlight else Color(0xFF285573),
                RoundedCornerShape(16.dp)
            )
            .testTag("manual_micrometer_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val axisY = h * 0.37f
                        val rodH = (h * 0.088f).coerceIn(13f, 22f)
                        val anvilFaceX = w * 0.145f
                        val maxGapPx = w * 0.175f
                        val frameRightBaseX = anvilFaceX + maxGapPx + w * 0.035f
                        val lockCenter = Offset(frameRightBaseX + w * 0.004f, axisY + rodH * 0.92f)

                        if (hypot(tapOffset.x - lockCenter.x, tapOffset.y - lockCenter.y) <= (w * 0.075f).coerceAtLeast(36f)) {
                            currentOnToggleLock()
                            return@detectTapGestures
                        }

                        val sleeveStartX = frameRightBaseX + w * 0.032f
                        val sleeveZeroX = sleeveStartX + w * 0.015f
                        val sleevePxPerMm = (w * 0.245f) / 25f
                        val explodeX = currentExplode * (w * 0.022f)
                        val thimbleEdgeX = sleeveZeroX + (currentEffectiveUm / 1000f) * sleevePxPerMm + explodeX * 0.6f
                        val ratchetLeft = thimbleEdgeX + w * 0.062f + w * 0.155f + explodeX * 0.4f
                        if (tapOffset.x >= ratchetLeft - 14f && abs(tapOffset.y - axisY) <= h * 0.30f) {
                            currentOnRatchetClick()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (!currentLocked) {
                            val w = size.width.toFloat()
                            val sleeveStartX = w * 0.387f
                            val sleeveZeroX = sleeveStartX + w * 0.015f
                            val sleevePxPerMm = (w * 0.245f) / 25f
                            val thimbleEdgeX = sleeveZeroX + (currentEffectiveUm / 1000f) * sleevePxPerMm
                            val ratchetLeft = thimbleEdgeX + w * 0.217f
                            val isRatchetZone = change.position.x >= ratchetLeft - 10f
                            // Drag down opens spindle (+um), drag up closes spindle (-um)
                            val deltaUm = ((dragAmount / 2.0f) * 10f).roundToInt()
                            if (deltaUm != 0) currentOnRotate(deltaUm, isRatchetZone)
                        }
                    }
                }
        ) {
            drawMicrometerInstrument(
                animatedSpindleUm = animatedSpindleUm,
                animatedEffectiveUm = animatedEffectiveUm,
                isLocked = state.isLocked,
                lockLeverAngleDeg = animatedLockAngleDeg,
                measurementKind = state.measurementKind,
                workpieceContactUm = state.workpieceContactUm,
                workpieceShape = workpieceShape,
                isInContact = state.isInContactWithWorkpiece,
                isOverPressure = state.isOverPressureWarning,
                ratchetClicks = state.ratchetClickCount,
                ratchetPulse = ratchetPulse.value,
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
 * Interactive Realistic 3D Canvas for Digital Outside Micrometer (0–25 mm, 0.01 mm LCD).
 * Styled closely after a real satin-nickel cast C-frame digital micrometer with integrated
 * ribbed encoder collar, knurled top lock knob, tapered matte-black faceplate, olive-bronze
 * LCD bezel, and directly tappable ON/OFF & mm/in buttons.
 */
@Composable
fun DigitalMicrometerCanvas(
    state: DigitalMicrometerState,
    onRotateThimbleDeltaUm: (Int, Boolean) -> Unit,
    onToggleLock: () -> Unit = {},
    onRatchetClick: () -> Unit = {},
    onTogglePower: () -> Unit = {},
    onToggleUnit: () -> Unit = {},
    workpieceShape: WorkpieceShapeType? = null,
    showCallouts: Boolean = false,
    explodeProgress: Float = 0f,
    isXRayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentOnRotate by rememberUpdatedState(onRotateThimbleDeltaUm)
    val currentOnToggleLock by rememberUpdatedState(onToggleLock)
    val currentOnRatchetClick by rememberUpdatedState(onRatchetClick)
    val currentOnTogglePower by rememberUpdatedState(onTogglePower)
    val currentOnToggleUnit by rememberUpdatedState(onToggleUnit)
    val currentLocked by rememberUpdatedState(state.isLocked)

    val animatedSpindleUm by animateFloatAsState(
        targetValue = state.spindlePositionUm.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "digital_spindle_travel"
    )
    val animatedEffectiveUm by animateFloatAsState(
        targetValue = state.sensorUm.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "digital_scale_travel"
    )
    val animatedLockAngleDeg by animateFloatAsState(
        targetValue = if (state.isLocked) 34f else -34f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "digital_lock_pivot"
    )
    val ratchetPulse = remember { Animatable(0f) }
    LaunchedEffect(state.ratchetClickCount) {
        if (state.ratchetClickCount > 0) {
            ratchetPulse.snapTo(1f)
            ratchetPulse.animateTo(0f, animationSpec = tween(durationMillis = 360))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    if (isXRayMode) listOf(Color(0xFF051824), Color(0xFF0A273B))
                    else listOf(Color(0xFFECEFF3), Color(0xFFF8F9FB), Color(0xFFE3E7EC))
                )
            )
            .border(1.dp, if (isXRayMode) CyanHighlight else Color(0xFFC5CED7), RoundedCornerShape(16.dp))
            .testTag("digital_micrometer_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val axisY = h * 0.36f
                        val rodH = (h * 0.078f).coerceIn(12f, 22f)
                        val sleeveH = rodH * 2.25f
                        val anvilFaceX = w * 0.140f
                        val maxGapPx = w * 0.160f
                        val frameRightBaseX = anvilFaceX + maxGapPx + w * 0.012f
                        val housingLeft = frameRightBaseX - w * 0.004f
                        val housingW = w * 0.216f
                        // Direct tap on top-left knurled lock knob
                        val lockKnobCenter = Offset(frameRightBaseX + w * 0.024f, axisY - h * 0.075f)
                        if (hypot(tapOffset.x - lockKnobCenter.x, tapOffset.y - lockKnobCenter.y) <= (w * 0.055f).coerceAtLeast(32f)) {
                            currentOnToggleLock()
                            return@detectTapGestures
                        }
                        // Direct tap on tactile faceplate buttons (ON/OFF·0 and mm/in)
                        val plateTop = axisY + sleeveH * 0.54f
                        val bezelTop = plateTop + 11f
                        val bezelH = (h * 0.165f).coerceIn(30f, 44f)
                        val btnRowY = bezelTop + bezelH + (h * 0.048f).coerceIn(10f, 15f)
                        val powerBtnCenter = Offset(housingLeft + housingW * 0.27f, btnRowY)
                        val unitBtnCenter = Offset(housingLeft + housingW * 0.62f, btnRowY)
                        if (hypot(tapOffset.x - powerBtnCenter.x, tapOffset.y - powerBtnCenter.y) <= 30f) {
                            currentOnTogglePower()
                            return@detectTapGestures
                        }
                        if (hypot(tapOffset.x - unitBtnCenter.x, tapOffset.y - unitBtnCenter.y) <= 30f) {
                            currentOnToggleUnit()
                            return@detectTapGestures
                        }
                        // Direct tap on Ratchet Stop
                        if (tapOffset.x >= w * 0.80f && abs(tapOffset.y - axisY) <= h * 0.26f) {
                            currentOnRatchetClick()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (!currentLocked) {
                            val isRatchetZone = change.position.x >= size.width * 0.80f
                            val deltaUm = ((dragAmount / 2.0f) * 10f).roundToInt()
                            if (deltaUm != 0) currentOnRotate(deltaUm, isRatchetZone)
                        }
                    }
                }
        ) {
            drawMicrometerInstrument(
                animatedSpindleUm = animatedSpindleUm,
                animatedEffectiveUm = animatedEffectiveUm,
                isLocked = state.isLocked,
                lockLeverAngleDeg = animatedLockAngleDeg,
                measurementKind = state.measurementKind,
                workpieceContactUm = state.workpieceContactUm,
                workpieceShape = workpieceShape,
                isInContact = state.isInContactWithWorkpiece,
                isOverPressure = false,
                ratchetClicks = state.ratchetClickCount,
                ratchetPulse = ratchetPulse.value,
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

private fun DrawScope.drawMicrometerInstrument(
    animatedSpindleUm: Float,
    animatedEffectiveUm: Float,
    isLocked: Boolean,
    lockLeverAngleDeg: Float,
    measurementKind: MeasurementKind,
    workpieceContactUm: Int?,
    workpieceShape: WorkpieceShapeType?,
    isInContact: Boolean,
    isOverPressure: Boolean,
    ratchetClicks: Int,
    ratchetPulse: Float,
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
    val axisY = if (isDigital) h * 0.36f else h * 0.37f
    val explodeX = explodeProgress * (w * 0.022f)
    val explodeY = explodeProgress * (h * 0.05f)

    // Subtle workshop grid behind instrument
    val gridColor = when {
        isXRayMode -> CyanHighlight.copy(alpha = 0.08f)
        isDigital -> Color(0xFF94A3B8).copy(alpha = 0.14f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val gridStep = (w / 22f).coerceAtLeast(18f)
    var gx = gridStep
    while (gx < w) {
        drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 1f)
        gx += gridStep
    }
    var gy = gridStep
    while (gy < h) {
        drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f)
        gy += gridStep
    }

    // Calibrated pixel scale for 0..25 mm spindle travel (guaranteed zero clip across 0..25 mm)
    val anvilFaceX = if (isDigital) w * 0.140f else w * 0.145f
    val maxGapPx = if (isDigital) w * 0.160f else w * 0.175f
    val pxPerMm = maxGapPx / 25f
    val gapPx = (animatedSpindleUm / 1000f).coerceIn(0f, 25f) * pxPerMm
    val spindleTipX = anvilFaceX + gapPx

    val rodH = (h * (if (isDigital) 0.078f else 0.088f)).coerceIn(12f, 22f)
    val sleeveH = rodH * (if (isDigital) 2.25f else 1.98f)
    val thimbleH = sleeveH * (if (isDigital) 1.38f else 1.50f)

    // Authentic warm champagne-nickel satin metal for Digital Micrometer (Image 2), crisp chrome for Manual
    val satinChromeBrush = when {
        isXRayMode -> Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.24f), TealSecondary.copy(alpha = 0.10f), CyanHighlight.copy(alpha = 0.18f)))
        isDigital -> Brush.verticalGradient(
            0.00f to Color(0xFF9C9889),
            0.18f to Color(0xFFE7E4D8),
            0.35f to Color(0xFFF6F5EE),
            0.62f to Color(0xFFC8C4B4),
            0.86f to Color(0xFF8B8778),
            1.00f to Color(0xFF6B675A)
        )
        else -> Brush.verticalGradient(
            0.0f to Color(0xFFB5C4CE),
            0.22f to Color(0xFFF3F8FB),
            0.48f to Color(0xFFD6E1E8),
            0.78f to Color(0xFF9FB0BC),
            1.0f to Color(0xFF6B7D8A)
        )
    }
    val spindleChromeBrush = when {
        isXRayMode -> Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.28f), TealSecondary.copy(alpha = 0.12f)))
        isDigital -> Brush.verticalGradient(
            0.00f to Color(0xFF8E8979),
            0.26f to Color(0xFFF3F1E8),
            0.55f to Color(0xFFC5C0AE),
            1.00f to Color(0xFF5D594C)
        )
        else -> Brush.verticalGradient(
            0.0f to Color(0xFF8C9DA8),
            0.25f to Color(0xFFF7FBFD),
            0.55f to Color(0xFFCBD7DF),
            1.0f to Color(0xFF5D6F7C)
        )
    }
    val frameBrush = when {
        isXRayMode -> Brush.linearGradient(listOf(CyanHighlight.copy(alpha = 0.22f), NavyDark.copy(alpha = 0.45f)))
        isDigital -> Brush.linearGradient(
            colors = listOf(Color(0xFFE3E2DA), Color(0xFFC9C7BC), Color(0xFFAEACA0), Color(0xFF8E8C80)),
            start = Offset(anvilFaceX - w * 0.08f, axisY - h * 0.10f),
            end = Offset(anvilFaceX + maxGapPx + w * 0.22f, h * 0.90f)
        )
        else -> Brush.linearGradient(
            colors = listOf(Color(0xFF2D536D), MicrometerFrameHighlight, MicrometerFrameBlue, Color(0xFF0E2130)),
            start = Offset(anvilFaceX - w * 0.08f, axisY - h * 0.10f),
            end = Offset(anvilFaceX + maxGapPx + w * 0.06f, h * 0.90f)
        )
    }
    val strokeColor = when {
        isXRayMode -> CyanHighlight
        isDigital -> Color(0xFF262A2E)
        else -> Color(0xFF091822)
    }
    val studioShadow = if (isXRayMode) Color.Transparent else Color(0xFF141B22).copy(alpha = if (isDigital) 0.22f else 0.30f)

    // Frame & housing landmarks
    val frameRightBaseX = anvilFaceX + maxGapPx + (if (isDigital) w * 0.012f else w * 0.035f)
    val digitalHousingW = w * 0.212f
    val digitalCollarRightX = frameRightBaseX + digitalHousingW
    val sleeveStartX = if (isDigital) digitalCollarRightX - w * 0.004f else frameRightBaseX + w * 0.032f

    // 1. C-SHAPED CAST FRAME (Reference Image 2 satin-nickel cast frame for Digital; blue-steel for Manual)
    if (isDigital) {
        val digitalFramePath = Path().apply {
            val leftOuterX = anvilFaceX - w * 0.095f
            val anvilWallLeft = anvilFaceX - w * 0.048f
            val anvilWallRight = anvilFaceX - w * 0.012f
            val throatRight = frameRightBaseX - w * 0.008f
            val frameBottomY = h * 0.855f
            moveTo(anvilWallLeft, axisY - rodH * 0.80f)
            lineTo(anvilWallRight, axisY - rodH * 0.80f)
            lineTo(anvilWallRight, axisY + h * 0.10f)
            // Inner curved C-throat
            cubicTo(
                anvilWallRight, axisY + h * 0.32f,
                throatRight, axisY + h * 0.32f,
                throatRight, axisY + h * 0.09f
            )
            lineTo(throatRight, axisY - rodH * 1.15f)
            lineTo(digitalCollarRightX - w * 0.015f, axisY - rodH * 1.15f)
            lineTo(digitalCollarRightX - w * 0.004f, axisY + h * 0.34f)
            // Diagonal sculpted bottom contour hugging the digital faceplate (matching Image 2!)
            cubicTo(
                digitalCollarRightX - w * 0.012f, h * 0.77f,
                frameRightBaseX + w * 0.04f, frameBottomY,
                anvilFaceX + maxGapPx * 0.42f, frameBottomY
            )
            cubicTo(
                leftOuterX - w * 0.005f, frameBottomY,
                leftOuterX - w * 0.012f, axisY + h * 0.22f,
                anvilWallLeft, axisY - rodH * 0.80f
            )
            close()
        }
        // Soft grounded studio shadow under digital micrometer frame
        if (!isXRayMode) {
            drawOval(
                color = studioShadow.copy(alpha = 0.16f),
                topLeft = Offset(anvilFaceX - w * 0.09f, h * 0.835f),
                size = Size(w * 0.46f, h * 0.075f)
            )
            drawPath(Path().apply { addPath(digitalFramePath, Offset(3f, 5f)) }, color = studioShadow)
        }
        drawPath(digitalFramePath, brush = frameBrush)
        drawPath(digitalFramePath, color = strokeColor, style = Stroke(1.8f))

        // Curved Matte-Black Heat-Insulating Grip Plate on Left C-Arch (Image 2 signature!)
        val gripPadPath = Path().apply {
            val padLeft = anvilFaceX - w * 0.080f
            val padRight = frameRightBaseX - w * 0.020f
            val padTopY = axisY + h * 0.21f
            val padBotY = h * 0.815f
            moveTo(padLeft + w * 0.014f, padTopY)
            cubicTo(
                padLeft + w * 0.055f, padTopY + h * 0.12f,
                padRight - w * 0.045f, padTopY + h * 0.12f,
                padRight, padTopY + h * 0.08f
            )
            cubicTo(
                padRight + w * 0.008f, padBotY - h * 0.04f,
                padRight - w * 0.035f, padBotY,
                padLeft + w * 0.075f, padBotY
            )
            cubicTo(
                padLeft - w * 0.012f, padBotY - h * 0.02f,
                padLeft - w * 0.018f, padTopY + h * 0.09f,
                padLeft + w * 0.014f, padTopY
            )
            close()
        }
        val gripBrush = if (isXRayMode) {
            Brush.verticalGradient(listOf(NavyDark.copy(alpha = 0.65f), NavyDark.copy(alpha = 0.85f)))
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFF3E454E), Color(0xFF282E36), Color(0xFF1A1F25)),
                start = Offset(anvilFaceX - w * 0.08f, axisY + h * 0.20f),
                end = Offset(frameRightBaseX, h * 0.82f)
            )
        }
        drawPath(gripPadPath, brush = gripBrush)
        drawPath(gripPadPath, color = Color(0xFF14171B), style = Stroke(1.4f))

        // Crisp white engraved spec typography on the black grip pad ("0-25mm" / "0,01mm")
        val padTextCenterX = anvilFaceX + maxGapPx * 0.22f
        val specPaint = CanvasPaintCache.sansBold(
            colorArgb = Color(0xFFF1F5F9).toArgb(),
            textSizePx = (h * 0.064f).coerceIn(12f, 18f),
            align = Paint.Align.CENTER
        )
        drawContext.canvas.nativeCanvas.drawText("0–25mm", padTextCenterX, h * 0.665f, specPaint)
        drawContext.canvas.nativeCanvas.drawText("0,01mm", padTextCenterX, h * 0.745f, specPaint)
    } else {
        val framePath = Path().apply {
            moveTo(anvilFaceX - w * 0.056f, axisY - rodH * 0.85f)
            lineTo(anvilFaceX - w * 0.012f, axisY - rodH * 0.85f)
            lineTo(anvilFaceX - w * 0.012f, axisY + h * 0.10f)
            cubicTo(
                anvilFaceX - w * 0.012f, axisY + h * 0.37f,
                frameRightBaseX - w * 0.032f, axisY + h * 0.37f,
                frameRightBaseX - w * 0.032f, axisY + h * 0.10f
            )
            lineTo(frameRightBaseX - w * 0.032f, axisY - rodH * 0.95f)
            lineTo(sleeveStartX, axisY - rodH * 0.95f)
            lineTo(sleeveStartX, axisY + h * 0.14f)
            cubicTo(
                sleeveStartX, h * 0.90f,
                anvilFaceX - w * 0.082f, h * 0.90f,
                anvilFaceX - w * 0.082f, axisY + h * 0.14f
            )
            close()
        }
        drawPath(framePath, brush = frameBrush)
        drawPath(framePath, color = strokeColor, style = Stroke(2.2f))

        if (!isXRayMode) {
            val innerRibPath = Path().apply {
                moveTo(anvilFaceX - w * 0.044f, axisY + h * 0.14f)
                cubicTo(
                    anvilFaceX - w * 0.044f, h * 0.82f,
                    frameRightBaseX + w * 0.002f, h * 0.82f,
                    frameRightBaseX + w * 0.002f, axisY + h * 0.14f
                )
            }
            drawPath(innerRibPath, color = Color(0xFF0A1B27).copy(alpha = 0.55f), style = Stroke(width = 7f, cap = StrokeCap.Round))
            drawPath(innerRibPath, color = Color(0xFF477899).copy(alpha = 0.35f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
        }

        val badgeCenterX = (anvilFaceX + frameRightBaseX) / 2f - w * 0.015f
        val badgeW = w * 0.175f
        val badgeH = (h * 0.125f).coerceIn(22f, 34f)
        val badgeTop = h * 0.695f
        drawRoundRect(
            color = Color(0xFF0B2030).copy(alpha = 0.90f),
            topLeft = Offset(badgeCenterX - badgeW / 2f, badgeTop),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = if (isXRayMode) CyanHighlight else Color(0xFF467594),
            topLeft = Offset(badgeCenterX - badgeW / 2f, badgeTop),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(1.2f)
        )
        val specPaint = CanvasPaintCache.monoBold(
            colorArgb = Color(0xFFE3F2FA).toArgb(),
            textSizePx = (badgeH * 0.42f).coerceIn(10f, 15f),
            align = Paint.Align.CENTER
        )
        drawContext.canvas.nativeCanvas.drawText("0–25 mm • 0,01 mm", badgeCenterX, badgeTop + badgeH * 0.66f, specPaint)
    }

    // 2. REALISTIC 3D MACHINED WORKPIECE CLAMPED BETWEEN ANVIL & SPINDLE
    if (workpieceContactUm != null) {
        val wpWidthPx = ((workpieceContactUm / 1000f) * pxPerMm).coerceIn(6f, maxGapPx)
        val resolvedShape = workpieceShape ?: if (measurementKind == MeasurementKind.THICKNESS) {
            com.example.domain.model.WorkpieceShapeType.FLAT_PLATE
        } else {
            com.example.domain.model.WorkpieceShapeType.CYLINDER_SHAFT
        }
        val wpHeight = when (resolvedShape) {
            com.example.domain.model.WorkpieceShapeType.FLAT_PLATE -> h * 0.30f
            com.example.domain.model.WorkpieceShapeType.HEX_BOLT -> h * 0.34f
            else -> h * 0.255f
        }
        drawRealistic3DWorkpiece(
            shapeType = resolvedShape,
            xLeft = anvilFaceX,
            centerY = axisY,
            widthPx = wpWidthPx,
            heightPx = wpHeight,
            isInContact = isInContact,
            isOverPressure = isOverPressure,
            isRatchetContact = isInContact && ratchetClicks >= 1,
            isXRayMode = isXRayMode,
            dropShadowAlpha = if (isDigital) 0.20f else 0.34f
        )
    }

    // 3. FIXED ANVIL (with tungsten-carbide measuring face)
    val anvilW = w * 0.026f
    val anvilLeft = anvilFaceX - anvilW - explodeX * 0.45f
    drawRect(brush = spindleChromeBrush, topLeft = Offset(anvilLeft, axisY - rodH / 2f), size = Size(anvilW, rodH))
    drawRect(color = Color(0xFF2B3137), topLeft = Offset(anvilFaceX - 3.5f - explodeX * 0.45f, axisY - rodH / 2f), size = Size(3.5f, rodH))
    drawRect(color = strokeColor, topLeft = Offset(anvilLeft, axisY - rodH / 2f), size = Size(anvilW, rodH), style = Stroke(1.3f))

    // 4. MOVING SPINDLE (carbide-tipped left face, translates with 0.5 mm leadscrew pitch)
    val spindleRightX = if (isDigital) frameRightBaseX + w * 0.015f else sleeveStartX
    val spindleWidth = (spindleRightX - spindleTipX).coerceAtLeast(4f)
    drawRect(brush = spindleChromeBrush, topLeft = Offset(spindleTipX, axisY - rodH / 2f), size = Size(spindleWidth, rodH))
    drawRect(color = Color(0xFF2B3137), topLeft = Offset(spindleTipX, axisY - rodH / 2f), size = Size(3.5f, rodH))
    drawRect(color = strokeColor, topLeft = Offset(spindleTipX, axisY - rodH / 2f), size = Size(spindleWidth, rodH), style = Stroke(1.3f))

    if (isInContact && (ratchetClicks > 0 || isOverPressure)) {
        val glowColor = if (isOverPressure) ToleranceRed else ToleranceGreen
        drawLine(
            color = glowColor.copy(alpha = 0.85f),
            start = Offset(spindleTipX, axisY - rodH * 0.72f),
            end = Offset(spindleTipX, axisY + rodH * 0.72f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    // 5. LOCK MECHANISM (Manual = pivoting cam lever; Digital = round black knurled top lock knob per Image 2)
    val lockCenterX = if (isDigital) frameRightBaseX + w * 0.024f else frameRightBaseX + w * 0.004f
    val lockCenterY = if (isDigital) axisY - h * 0.075f else axisY + rodH * 0.92f
    val leverLen = (rodH * 1.25f).coerceIn(18f, 28f)
    if (!isDigital) {
        val lockCollarW = w * 0.022f
        drawRoundRect(
            brush = satinChromeBrush,
            topLeft = Offset(lockCenterX - lockCollarW / 2f, axisY - rodH * 0.76f),
            size = Size(lockCollarW, rodH * 1.52f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = if (isLocked) OrangeAccent else strokeColor,
            topLeft = Offset(lockCenterX - lockCollarW / 2f, axisY - rodH * 0.76f),
            size = Size(lockCollarW, rodH * 1.52f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(if (isLocked) 2.0f else 1.3f)
        )
        val rad = (lockLeverAngleDeg * PI / 180.0)
        val leverEnd = Offset(
            x = lockCenterX + (sin(rad) * leverLen).toFloat(),
            y = lockCenterY + (cos(rad) * leverLen).toFloat()
        )
        val lockMetalColor = if (isLocked) OrangeAccent else Color(0xFF94A3B8)
        drawLine(strokeColor, Offset(lockCenterX, lockCenterY), leverEnd, strokeWidth = 7.5f, cap = StrokeCap.Round)
        drawLine(lockMetalColor, Offset(lockCenterX, lockCenterY), leverEnd, strokeWidth = 5f, cap = StrokeCap.Round)
        drawCircle(lockMetalColor, radius = 9f, center = Offset(lockCenterX, lockCenterY))
        drawCircle(strokeColor, radius = 9f, center = Offset(lockCenterX, lockCenterY), style = Stroke(1.5f))
        drawCircle(Color(0xFF1E293B), radius = 3.2f, center = Offset(lockCenterX, lockCenterY))
    }

    // 6. SLEEVE / BARREL (0..25 mm upper 1 mm & lower 0.5 mm precision scale)
    val sleeveScaleSpan = if (isDigital) w * 0.130f else w * 0.245f
    val sleeveW = if (isDigital) w * 0.148f else w * 0.278f
    val sleevePxPerMm = sleeveScaleSpan / 25f
    val sleeveZeroX = sleeveStartX + (if (isDigital) w * 0.008f else w * 0.015f)

    if (isDigital && !isXRayMode) {
        drawRect(
            color = studioShadow.copy(alpha = 0.18f),
            topLeft = Offset(sleeveStartX + 2f, axisY - sleeveH / 2f + 4f),
            size = Size(sleeveW, sleeveH)
        )
    }
    drawRect(
        brush = satinChromeBrush,
        topLeft = Offset(sleeveStartX + explodeX * 0.25f, axisY - sleeveH / 2f),
        size = Size(sleeveW, sleeveH)
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(sleeveStartX + explodeX * 0.25f, axisY - sleeveH / 2f),
        size = Size(sleeveW, sleeveH),
        style = Stroke(1.5f)
    )

    if (isXRayMode) {
        val threadPitchPx = (sleevePxPerMm * 0.5f).coerceAtLeast(3.5f)
        val phasePx = ((animatedSpindleUm / 500f) % 1f) * threadPitchPx
        var tx = sleeveStartX + 4f + phasePx
        val maxTx = sleeveStartX + sleeveW + w * 0.10f
        while (tx < maxTx) {
            drawLine(
                color = OrangeAccent.copy(alpha = 0.72f),
                start = Offset(tx, axisY - rodH * 0.44f),
                end = Offset(tx + threadPitchPx * 0.55f, axisY + rodH * 0.44f),
                strokeWidth = 1.5f
            )
            tx += threadPitchPx
        }
    }

    drawLine(
        color = if (isXRayMode) CyanHighlight else Color(0xFF1F2933),
        start = Offset(sleeveZeroX - 3f, axisY),
        end = Offset(sleeveStartX + sleeveW, axisY),
        strokeWidth = 1.7f
    )

    val sleeveTextPaint = CanvasPaintCache.monoBold(
        colorArgb = if (isXRayMode) CyanHighlight.toArgb() else Color(0xFF1F2933).toArgb(),
        textSizePx = (sleeveH * 0.23f).coerceIn(9f, 14f),
        align = Paint.Align.CENTER
    )

    val bevelPosMm = MicrometerScaleGeometry.thimbleBevelPositionMm(animatedEffectiveUm)
    val thimbleEdgeX = sleeveZeroX + bevelPosMm * sleevePxPerMm + explodeX * 0.6f

    for (halfStep in 0..50) {
        val mmVal = halfStep * 0.5f
        val x = sleeveZeroX + mmVal * sleevePxPerMm
        if (x > thimbleEdgeX + 0.8f && explodeProgress < 0.1f) continue
        val isWhole = halfStep % 2 == 0
        val wholeMm = halfStep / 2
        if (isWhole) {
            val len = if (wholeMm % 5 == 0) sleeveH * 0.26f else sleeveH * 0.16f
            drawLine(
                color = if (isXRayMode) CyanHighlight else Color(0xFF1F2933),
                start = Offset(x, axisY),
                end = Offset(x, axisY - len),
                strokeWidth = if (wholeMm % 5 == 0) 1.5f else 1.0f
            )
            if (wholeMm % 5 == 0 && (!isDigital || wholeMm % 10 == 0)) {
                drawContext.canvas.nativeCanvas.drawText("$wholeMm", x, axisY - len - 3f, sleeveTextPaint)
            }
        } else {
            drawLine(
                color = if (isXRayMode) OrangeAccent else if (isDigital) Color(0xFF3A3E44) else TealSecondary,
                start = Offset(x, axisY),
                end = Offset(x, axisY + sleeveH * 0.20f),
                strokeWidth = 1.15f
            )
        }
    }

    // 7. ROTATING THIMBLE DRUM (tapered bevel + cylindrical rotating scale + diamond knurled grip)
    val thimbleBevelW = if (isDigital) w * 0.048f else w * 0.062f
    val thimbleSmoothW = if (isDigital) w * 0.072f else 0f
    val thimbleBodyW = if (isDigital) w * 0.108f else w * 0.155f
    val continuousDiv = MicrometerScaleGeometry.continuousThimbleDivision(animatedEffectiveUm)

    // Tapered satin bevel nose
    val bevelPath = Path().apply {
        moveTo(thimbleEdgeX, axisY - sleeveH * 0.52f)
        lineTo(thimbleEdgeX + thimbleBevelW, axisY - thimbleH / 2f)
        lineTo(thimbleEdgeX + thimbleBevelW, axisY + thimbleH / 2f)
        lineTo(thimbleEdgeX, axisY + sleeveH * 0.52f)
        close()
    }
    if (isDigital && !isXRayMode) {
        drawPath(Path().apply { addPath(bevelPath, Offset(2f, 4f)) }, color = studioShadow.copy(alpha = 0.18f))
        drawRect(
            color = studioShadow.copy(alpha = 0.18f),
            topLeft = Offset(thimbleEdgeX + thimbleBevelW + 2f, axisY - thimbleH / 2f + 4f),
            size = Size(thimbleSmoothW + thimbleBodyW, thimbleH)
        )
    }
    drawPath(bevelPath, brush = satinChromeBrush)
    drawPath(bevelPath, color = strokeColor, style = Stroke(1.5f))

    // Smooth satin collar section before knurled grip (matching Image 2's long sleek barrel!)
    val smoothLeft = thimbleEdgeX + thimbleBevelW
    if (thimbleSmoothW > 1f) {
        drawRect(
            brush = satinChromeBrush,
            topLeft = Offset(smoothLeft, axisY - thimbleH / 2f),
            size = Size(thimbleSmoothW, thimbleH)
        )
        drawRect(
            color = strokeColor,
            topLeft = Offset(smoothLeft, axisY - thimbleH / 2f),
            size = Size(thimbleSmoothW, thimbleH),
            style = Stroke(1.4f)
        )
    }

    // Cylindrical diamond-knurled thimble grip
    val gripLeft = smoothLeft + thimbleSmoothW
    val gripH = if (isDigital) thimbleH * 1.08f else thimbleH
    drawRoundRect(
        brush = satinChromeBrush,
        topLeft = Offset(gripLeft, axisY - gripH / 2f),
        size = Size(thimbleBodyW, gripH),
        cornerRadius = CornerRadius(3f, 3f)
    )

    val knurlInsetX = if (isDigital) thimbleBodyW * 0.06f else thimbleBodyW * 0.14f
    val knurlLeft = gripLeft + knurlInsetX
    val knurlRight = gripLeft + thimbleBodyW - knurlInsetX
    val knurlTop = axisY - gripH * 0.46f
    val knurlBottom = axisY + gripH * 0.46f
    val knurlStep = if (isDigital) 5.5f else 7f
    val knurlPhaseY = ((continuousDiv / 50f) * knurlStep * 6f) % knurlStep
    val knurlLineColor = if (isXRayMode) CyanHighlight.copy(alpha = 0.30f) else Color(0xFF47443A).copy(alpha = 0.42f)

    drawRect(
        color = if (isXRayMode) Color.Transparent else Color(0xFF535046).copy(alpha = 0.14f),
        topLeft = Offset(knurlLeft, knurlTop),
        size = Size(knurlRight - knurlLeft, knurlBottom - knurlTop)
    )
    var ky = knurlTop + knurlPhaseY
    while (ky < knurlBottom) {
        drawLine(knurlLineColor, Offset(knurlLeft, ky), Offset(knurlRight, ky), 0.9f)
        ky += knurlStep
    }
    var kx = knurlLeft
    while (kx < knurlRight - 4f) {
        val segH = (knurlBottom - knurlTop)
        drawLine(knurlLineColor.copy(alpha = 0.34f), Offset(kx, knurlTop), Offset((kx + segH * 0.25f).coerceAtMost(knurlRight), knurlBottom), 0.9f)
        drawLine(knurlLineColor.copy(alpha = 0.26f), Offset((kx + segH * 0.25f).coerceAtMost(knurlRight), knurlTop), Offset(kx, knurlBottom), 0.9f)
        kx += 6.5f
    }
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(gripLeft, axisY - gripH / 2f),
        size = Size(thimbleBodyW, gripH),
        cornerRadius = CornerRadius(3f, 3f),
        style = Stroke(1.5f)
    )

    // Smoothly scrolling Thimble Graduations (0..49) with cylindrical foreshortening
    val thimbleLabelPaint = CanvasPaintCache.monoBold(
        colorArgb = if (isXRayMode) CyanHighlight.toArgb() else Color(0xFF1E242B).toArgb(),
        textSizePx = (thimbleH * 0.155f).coerceIn(9.5f, 14f),
        align = Paint.Align.LEFT
    )
    val baseDivInt = continuousDiv.roundToInt()
    val fracDiv = continuousDiv - baseDivInt
    val maxHalfCyl = thimbleH * 0.45f
    for (offsetDiv in -10..10) {
        val rawDiv = baseDivInt + offsetDiv
        val divVal = ((rawDiv % 50) + 50) % 50
        val relDiv = offsetDiv - fracDiv
        val theta = (relDiv / 11.5f) * (PI.toFloat() / 2.2f)
        if (abs(theta) > 1.38f) continue
        val ty = axisY - sin(theta) * maxHalfCyl
        val foreshorten = cos(theta).coerceIn(0.25f, 1f)
        val isMajor = divVal % 5 == 0
        val isAligned = abs(relDiv) < 0.35f
        val tickW = if (isMajor) thimbleBevelW * 0.46f else thimbleBevelW * 0.27f
        val tickAlpha = (0.35f + 0.65f * foreshorten).coerceIn(0.35f, 1f)
        drawLine(
            color = (if (isAligned) OrangeAccent else if (isXRayMode) CyanHighlight else Color(0xFF1F2933)).copy(alpha = tickAlpha),
            start = Offset(thimbleEdgeX + 2f, ty),
            end = Offset(thimbleEdgeX + 2f + tickW, ty),
            strokeWidth = if (isAligned) 2.3f else if (isMajor) 1.45f else 0.95f
        )
        if (isMajor && foreshorten > 0.45f) {
            thimbleLabelPaint.alpha = (tickAlpha * 255).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                "$divVal",
                thimbleEdgeX + tickW + 4f,
                ty + thimbleLabelPaint.textSize * 0.35f,
                thimbleLabelPaint
            )
        }
    }

    // 8. RATCHET STOP (stepped constant-torque friction end cap — directly tappable & draggable)
    val ratchetNeckW = w * 0.015f
    val ratchetNeckH = thimbleH * 0.44f
    val ratchetNeckLeft = gripLeft + thimbleBodyW + explodeX * 0.35f
    drawRect(brush = satinChromeBrush, topLeft = Offset(ratchetNeckLeft, axisY - ratchetNeckH / 2f), size = Size(ratchetNeckW, ratchetNeckH))
    drawRect(color = strokeColor, topLeft = Offset(ratchetNeckLeft, axisY - ratchetNeckH / 2f), size = Size(ratchetNeckW, ratchetNeckH), style = Stroke(1.3f))

    val ratchetLeft = ratchetNeckLeft + ratchetNeckW
    val ratchetW = (w * 0.056f).coerceAtMost((w - ratchetLeft - 6f).coerceAtLeast(14f))
    val ratchetH = thimbleH * 0.64f
    val pulseKickY = sin(ratchetPulse * PI.toFloat()) * 2.5f
    drawRoundRect(
        brush = satinChromeBrush,
        topLeft = Offset(ratchetLeft, axisY - ratchetH / 2f + pulseKickY),
        size = Size(ratchetW, ratchetH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    var ry = axisY - ratchetH / 2f + 4f + (knurlPhaseY * 0.7f)
    while (ry < axisY + ratchetH / 2f - 3f) {
        drawLine(knurlLineColor, Offset(ratchetLeft + 3f, ry + pulseKickY), Offset(ratchetLeft + ratchetW - 3f, ry + pulseKickY), 0.95f)
        ry += 4.2f
    }
    drawRoundRect(
        color = if (ratchetClicks > 0) ToleranceGreen else strokeColor,
        topLeft = Offset(ratchetLeft, axisY - ratchetH / 2f + pulseKickY),
        size = Size(ratchetW, ratchetH),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(if (ratchetClicks > 0) 2.3f else 1.4f)
    )
    if (ratchetPulse > 0.02f) {
        val expand = (1f - ratchetPulse) * 14f
        drawRoundRect(
            color = ToleranceGreen.copy(alpha = ratchetPulse * 0.85f),
            topLeft = Offset(ratchetLeft - expand * 0.4f, axisY - ratchetH / 2f - expand * 0.5f),
            size = Size(ratchetW + expand * 0.8f, ratchetH + expand),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(2.4f)
        )
    }

    // 9. INTEGRATED DIGITAL ELECTRONICS HOUSING (Faithful to Reference Image 2!)
    if (isDigital) {
        val housingLeft = frameRightBaseX - w * 0.004f
        val housingRight = digitalCollarRightX
        val housingW = housingRight - housingLeft

        // 9A. Top-Left Round Black Knurled Lock Knob (directly tappable!)
        val knobR = (h * 0.056f).coerceIn(10f, 16f)
        val knobCenter = Offset(lockCenterX, lockCenterY)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF474E57), Color(0xFF20242A), Color(0xFF0E1014)),
                center = knobCenter - Offset(2f, 2f),
                radius = knobR * 1.2f
            ),
            radius = knobR,
            center = knobCenter
        )
        drawCircle(
            color = if (isLocked) OrangeAccent else Color(0xFF0D0F12),
            radius = knobR,
            center = knobCenter,
            style = Stroke(if (isLocked) 2.4f else 1.4f)
        )
        // Subtle knurl notches around top lock knob
        for (k in 0 until 8) {
            val a = k * PI / 4.0
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(knobCenter.x + (cos(a) * knobR * 0.55f).toFloat(), knobCenter.y + (sin(a) * knobR * 0.55f).toFloat()),
                end = Offset(knobCenter.x + (cos(a) * knobR * 0.92f).toFloat(), knobCenter.y + (sin(a) * knobR * 0.92f).toFloat()),
                strokeWidth = 1.1f
            )
        }

        // 9B. Ribbed Black Cylindrical Encoder Collar over sleeve bridge (Image 2 top block!)
        val collarLeft = housingLeft + housingW * 0.28f
        val collarW = housingRight - collarLeft
        val collarTop = axisY - sleeveH * 0.78f - explodeY * 0.3f
        val collarH = sleeveH * 1.54f
        drawRoundRect(
            brush = Brush.verticalGradient(
                0.00f to Color(0xFF171B20),
                0.28f to Color(0xFF3A424D),
                0.55f to Color(0xFF252B33),
                0.85f to Color(0xFF15191F),
                1.00f to Color(0xFF232830)
            ),
            topLeft = Offset(collarLeft, collarTop),
            size = Size(collarW, collarH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        // Moulded horizontal grip ribs across encoder collar
        val ribCount = 7
        for (r in 1 until ribCount) {
            val ribY = collarTop + collarH * (r / ribCount.toFloat())
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(collarLeft + 3f, ribY), Offset(collarLeft + collarW - 3f, ribY), 1.4f)
            drawLine(Color.White.copy(alpha = 0.14f), Offset(collarLeft + 3f, ribY + 1.4f), Offset(collarLeft + collarW - 3f, ribY + 1.4f), 0.9f)
        }
        drawRoundRect(
            color = Color(0xFF0E1116),
            topLeft = Offset(collarLeft, collarTop),
            size = Size(collarW, collarH),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(1.5f)
        )

        // 9C. Tapered Matte-Charcoal Digital Faceplate integrated into right C-frame leg
        val plateTop = axisY + sleeveH * 0.54f + explodeY * 0.25f
        val plateRightBotY = h * 0.735f + explodeY * 0.25f
        val plateLeftBotY = h * 0.825f + explodeY * 0.25f
        val faceplatePath = Path().apply {
            moveTo(housingLeft, plateTop)
            lineTo(housingRight, plateTop)
            lineTo(housingRight, plateRightBotY)
            // Angled bottom contour matching Image 2's ergonomic digital micrometer!
            lineTo(housingLeft + housingW * 0.08f, plateLeftBotY)
            lineTo(housingLeft, plateLeftBotY - h * 0.03f)
            close()
        }
        drawPath(
            faceplatePath,
            brush = Brush.verticalGradient(listOf(Color(0xFF333942), Color(0xFF232830), Color(0xFF181C22)))
        )
        drawPath(faceplatePath, color = Color(0xFF0F1216), style = Stroke(1.5f))

        // 4 subtle metallic micro-screws at corners of faceplate
        val screwOffsets = listOf(
            Offset(housingLeft + 8f, plateTop + 7f),
            Offset(housingRight - 8f, plateTop + 7f),
            Offset(housingLeft + 10f, plateLeftBotY - 12f),
            Offset(housingRight - 10f, plateRightBotY - 10f)
        )
        for (sc in screwOffsets) {
            drawCircle(Color(0xFF111418), radius = 3.2f, center = sc)
            drawCircle(Color(0xFF6B7280), radius = 1.6f, center = sc)
        }

        // 9D. Warm Olive-Bronze Recessed Bezel + Olive-Grey LCD Screen (Image 2 signature!)
        val bezelLeft = housingLeft + 6f
        val bezelTop = plateTop + 11f
        val bezelW = housingW - 12f
        val bezelH = (h * 0.165f).coerceIn(30f, 44f)
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF6A5835), Color(0xFF816D43), Color(0xFF5A4A2B))),
            topLeft = Offset(bezelLeft, bezelTop),
            size = Size(bezelW, bezelH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFF1E1A12),
            topLeft = Offset(bezelLeft, bezelTop),
            size = Size(bezelW, bezelH),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(1.2f)
        )

        val lcdInset = 4f
        val lcdLeft = bezelLeft + lcdInset
        val lcdTop = bezelTop + lcdInset
        val lcdW = bezelW - lcdInset * 2f
        val lcdH = bezelH - lcdInset * 2f
        drawRoundRect(
            color = if (digitalPowerOn) Color(0xFF9EA58B) else Color(0xFF6E7464),
            topLeft = Offset(lcdLeft, lcdTop),
            size = Size(lcdW, lcdH),
            cornerRadius = CornerRadius(2.5f, 2.5f)
        )
        drawRoundRect(
            color = Color(0xFF3E4234),
            topLeft = Offset(lcdLeft, lcdTop),
            size = Size(lcdW, lcdH),
            cornerRadius = CornerRadius(2.5f, 2.5f),
            style = Stroke(1.0f)
        )

        if (digitalPowerOn) {
            val lcdPaint = CanvasPaintCache.monoBold(
                colorArgb = Color(0xFF171E14).toArgb(),
                textSizePx = (lcdH * 0.74f).coerceIn(17f, 30f),
                align = Paint.Align.RIGHT
            )
            drawContext.canvas.nativeCanvas.drawText(
                digitalText,
                lcdLeft + lcdW - 22f,
                lcdTop + lcdH * 0.78f,
                lcdPaint
            )
            val unitPaint = CanvasPaintCache.sansBold(
                colorArgb = Color(0xFF171E14).toArgb(),
                textSizePx = (lcdH * 0.30f).coerceIn(8.5f, 13f),
                align = Paint.Align.RIGHT
            )
            drawContext.canvas.nativeCanvas.drawText(
                digitalUnit.symbol,
                lcdLeft + lcdW - 4f,
                lcdTop + lcdH * 0.74f,
                unitPaint
            )
            val statusFlag = buildString {
                if (digitalHold) append("H ")
                if (digitalZeroOffset) append("INC")
            }.trim()
            if (statusFlag.isNotEmpty()) {
                val badgePaint = CanvasPaintCache.sansBold(
                    colorArgb = Color(0xFF1E261A).toArgb(),
                    textSizePx = (lcdH * 0.25f).coerceIn(8f, 11f),
                    align = Paint.Align.LEFT
                )
                drawContext.canvas.nativeCanvas.drawText(statusFlag, lcdLeft + 4f, lcdTop + lcdH * 0.34f, badgePaint)
            }
        }

        // 9E. Two Tactile Oval Rubber Buttons right below the LCD ("ON/OFF 0" & "mm/in")
        val btnRowY = bezelTop + bezelH + (h * 0.048f).coerceIn(10f, 15f)
        val btnW = (housingW * 0.19f).coerceIn(14f, 26f)
        val btnH = (h * 0.036f).coerceIn(7f, 11f)
        val pwrBtnX = housingLeft + housingW * 0.27f
        val unitBtnX = housingLeft + housingW * 0.62f

        val btnBrush = Brush.verticalGradient(listOf(Color(0xFFDAD5C5), Color(0xFFA9A391)))
        drawOval(btnBrush, Offset(pwrBtnX - btnW / 2f, btnRowY - btnH / 2f), Size(btnW, btnH))
        drawOval(Color(0xFF14181E), Offset(pwrBtnX - btnW / 2f, btnRowY - btnH / 2f), Size(btnW, btnH), style = Stroke(1.2f))

        drawOval(btnBrush, Offset(unitBtnX - btnW / 2f, btnRowY - btnH / 2f), Size(btnW, btnH))
        drawOval(Color(0xFF14181E), Offset(unitBtnX - btnW / 2f, btnRowY - btnH / 2f), Size(btnW, btnH), style = Stroke(1.2f))

        val btnLabelPaint = CanvasPaintCache.sansBold(
            colorArgb = Color(0xFFE2E8F0).toArgb(),
            textSizePx = (h * 0.034f).coerceIn(8f, 11f),
            align = Paint.Align.CENTER
        )
        drawContext.canvas.nativeCanvas.drawText("ON/OFF 0", pwrBtnX, btnRowY + btnH + 9f, btnLabelPaint)
        drawContext.canvas.nativeCanvas.drawText("mm/in", unitBtnX, btnRowY + btnH + 9f, btnLabelPaint)
    }

    // 10. EDUCATIONAL COMPONENT CALLOUTS (All 7 mechanical components clearly labeled)
    if (showCallouts || explodeProgress > 0.25f) {
        drawCalloutBadge("Anvil", Offset(anvilFaceX - anvilW * 0.4f, axisY - rodH * 0.5f), Offset(w * 0.11f, h * 0.095f))
        drawCalloutBadge("Spindle", Offset((spindleTipX + spindleRightX) / 2f, axisY - rodH * 0.5f), Offset(w * 0.28f, h * 0.095f))
        drawCalloutBadge("Sleeve", Offset(sleeveStartX + sleeveW * 0.35f, axisY - sleeveH * 0.5f), Offset(w * 0.55f, h * 0.095f))
        drawCalloutBadge("Thimble", Offset(gripLeft + thimbleBodyW * 0.48f, axisY - thimbleH * 0.5f), Offset(w * 0.79f, h * 0.095f))
        drawCalloutBadge("Frame", Offset(anvilFaceX - w * 0.058f, h * 0.67f), Offset(w * 0.09f, h * 0.91f))
        drawCalloutBadge(if (isDigital) "Lock Knob" else "Lock Lever", Offset(lockCenterX, lockCenterY), Offset(w * 0.46f, h * 0.91f))
        drawCalloutBadge("Ratchet Stop", Offset(ratchetLeft + ratchetW * 0.5f, axisY + ratchetH * 0.5f), Offset(w * 0.87f, h * 0.91f))
    }
}

/**
 * 3.5x Animated Magnifier Loupe focused on the Sleeve + Thimble datum intersection.
 * Guarantees exact visual-to-formula alignment across the full 0.00–25.00 mm range,
 * including exact .00 mm and .50 mm graduation alignment flush with the thimble bevel edge.
 */
@Composable
fun MicrometerSleeveLoupe(
    state: ManualMicrometerState,
    modifier: Modifier = Modifier
) {
    val animatedEffectiveUm by animateFloatAsState(
        targetValue = state.readingUm.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "loupe_effective_um"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(98.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A2233))
            .border(1.5.dp, CyanHighlight, RoundedCornerShape(14.dp))
            .testTag("micrometer_sleeve_loupe")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h * 0.50f
            val thimbleEdgeX = w * 0.65f
            val pxPerMm = (w * 0.072f).coerceIn(28f, 44f)

            // Satin-chrome sleeve (left) & tapered thimble bevel (right)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFB5C4CE), Color(0xFFF3F8FB), Color(0xFFD6E1E8), Color(0xFF8396A4))),
                topLeft = Offset.Zero,
                size = Size(thimbleEdgeX, h)
            )
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF9EB0BC), Color(0xFFF6FAFC), Color(0xFFCDD9E1), Color(0xFF778A98))),
                topLeft = Offset(thimbleEdgeX, 0f),
                size = Size(w - thimbleEdgeX, h)
            )
            // Crisp thimble bevel edge shadow + line
            drawLine(Color.Black.copy(alpha = 0.22f), Offset(thimbleEdgeX - 2f, 0f), Offset(thimbleEdgeX - 2f, h), 4f)
            drawLine(NavyDark, Offset(thimbleEdgeX, 0f), Offset(thimbleEdgeX, h), 2.8f)

            // Horizontal datum reference line
            drawLine(OrangeAccent, Offset(0f, midY), Offset(thimbleEdgeX, midY), 2.6f)

            val readingMm = (animatedEffectiveUm / 1000f).coerceAtLeast(0f)
            val activeHalfStep = MicrometerScaleGeometry.visibleSleeveHalfStepCount(state.snappedUm)
            val textPaint = CanvasPaintCache.monoBold(
                colorArgb = NavyDark.toArgb(),
                textSizePx = (h * 0.20f).coerceIn(18f, 24f),
                align = Paint.Align.CENTER
            )

            // Visible sleeve ticks up to readingMm (inclusive of exact .00 / .50 mark at bevel edge!)
            val maxVisibleHalf = (readingMm * 2f).toInt().coerceIn(0, 50)
            val minVisibleHalf = (maxVisibleHalf - 22).coerceAtLeast(0)
            for (hs in minVisibleHalf..maxVisibleHalf) {
                val mm = hs * 0.5f
                val x = thimbleEdgeX - (readingMm - mm) * pxPerMm
                if (x < 6f || x > thimbleEdgeX + 0.8f) continue
                val isLastExposed = hs == activeHalfStep
                if (hs % 2 == 0) {
                    val whole = hs / 2
                    val len = if (whole % 5 == 0) midY * 0.50f else midY * 0.34f
                    drawLine(
                        color = if (isLastExposed) TealSecondary else NavyDark,
                        start = Offset(x, midY),
                        end = Offset(x, midY - len),
                        strokeWidth = if (isLastExposed) 3.0f else if (whole % 5 == 0) 2.4f else 1.5f
                    )
                    if (whole % 5 == 0) {
                        drawContext.canvas.nativeCanvas.drawText("$whole", x, midY - len - 5f, textPaint)
                    }
                } else {
                    drawLine(
                        color = if (isLastExposed) OrangeAccent else TealSecondary,
                        start = Offset(x, midY),
                        end = Offset(x, midY + midY * 0.38f),
                        strokeWidth = if (isLastExposed) 3.0f else 2.0f
                    )
                }
            }

            // Smoothly scrolling Thimble divisions aligned with horizontal datum line
            val continuousDiv = MicrometerScaleGeometry.continuousThimbleDivision(animatedEffectiveUm)
            val baseDivInt = continuousDiv.roundToInt()
            val fracDiv = continuousDiv - baseDivInt
            val thimblePaint = CanvasPaintCache.monoBold(
                colorArgb = NavyDark.toArgb(),
                textSizePx = (h * 0.21f).coerceIn(19f, 25f),
                align = Paint.Align.LEFT
            )
            for (delta in -5..5) {
                val rawDiv = baseDivInt + delta
                val divVal = ((rawDiv % 50) + 50) % 50
                val rel = delta - fracDiv
                val y = midY - rel * (h / 9.2f)
                if (y < 6f || y > h - 6f) continue
                val isAligned = abs(rel) < 0.32f
                val isMajor = divVal % 5 == 0
                val tickW = if (isMajor) 28f else 16f
                drawLine(
                    color = if (isAligned) OrangeAccent else NavyDark,
                    start = Offset(thimbleEdgeX + 1f, y),
                    end = Offset(thimbleEdgeX + 1f + tickW, y),
                    strokeWidth = if (isAligned) 3.6f else if (isMajor) 2.1f else 1.3f
                )
                if (isMajor || isAligned) {
                    thimblePaint.color = if (isAligned) OrangeAccent.toArgb() else NavyDark.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        "$divVal",
                        thimbleEdgeX + tickW + 8f,
                        y + thimblePaint.textSize * 0.34f,
                        thimblePaint
                    )
                }
            }
        }
    }
}
