package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.domain.model.WorkpieceShapeType
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.ToleranceGreen
import com.example.ui.theme.ToleranceRed

/**
 * High-craft 3D procedural machined workpiece renderer shared by Micrometer and Caliper canvases.
 *
 * Guarantees exact metrological contact fidelity: the outer left/right measuring surfaces always
 * sit precisely at [xLeft] and [xLeft + widthPx] (matching the Single Source of Truth dimension)
 * while rendering authentic 3D cylindrical shading, elliptical end-caps, chamfers, threads,
 * hex facets, bronze/steel materials, and contact highlights.
 */
internal fun DrawScope.drawRealistic3DWorkpiece(
    shapeType: WorkpieceShapeType?,
    xLeft: Float,
    centerY: Float,
    widthPx: Float,
    heightPx: Float,
    isInContact: Boolean,
    isOverPressure: Boolean = false,
    isRatchetContact: Boolean = false,
    isXRayMode: Boolean = false,
    dropShadowAlpha: Float = 0.28f
) {
    val w = widthPx.coerceAtLeast(6f)
    val h = heightPx.coerceAtLeast(18f)
    val top = centerY - h / 2f
    val bottom = centerY + h / 2f
    val xRight = xLeft + w

    val resolvedShape = shapeType ?: WorkpieceShapeType.CYLINDER_SHAFT
    val isBrassOrBronze = resolvedShape == WorkpieceShapeType.BUSHING_RING
    val isDarkSteel = resolvedShape == WorkpieceShapeType.HEX_BOLT || resolvedShape == WorkpieceShapeType.HEX_NUT

    val cylinderBrush = when {
        isXRayMode -> Brush.verticalGradient(
            listOf(CyanHighlight.copy(alpha = 0.28f), CyanHighlight.copy(alpha = 0.08f), CyanHighlight.copy(alpha = 0.24f))
        )
        isBrassOrBronze -> Brush.verticalGradient(
            0.00f to Color(0xFF8C692B),
            0.22f to Color(0xFFF3D999),
            0.42f to Color(0xFFD8AF59),
            0.74f to Color(0xFFA27B32),
            0.92f to Color(0xFF6E511D),
            1.00f to Color(0xFF8E6B2C)
        )
        isDarkSteel -> Brush.verticalGradient(
            0.00f to Color(0xFF475569),
            0.20f to Color(0xFFD7E0E8),
            0.38f to Color(0xFF94A3B8),
            0.72f to Color(0xFF526274),
            0.92f to Color(0xFF2D3845),
            1.00f to Color(0xFF4B5B6D)
        )
        else -> Brush.verticalGradient(
            0.00f to Color(0xFF6B7B88),
            0.18f to Color(0xFFE7EFF5),
            0.30f to Color(0xFFFAFDFF),
            0.52f to Color(0xFFBDCBD5),
            0.78f to Color(0xFF7B8C99),
            0.94f to Color(0xFF4C5B67),
            1.00f to Color(0xFF6C7D8A)
        )
    }

    val topFaceBrush = when {
        isXRayMode -> Brush.horizontalGradient(listOf(CyanHighlight.copy(alpha = 0.22f), CyanHighlight.copy(alpha = 0.10f)))
        isBrassOrBronze -> Brush.horizontalGradient(listOf(Color(0xFFE5C474), Color(0xFFF7E4B0), Color(0xFFC29A45)))
        isDarkSteel -> Brush.horizontalGradient(listOf(Color(0xFF9AA8B7), Color(0xFFC5D0DC), Color(0xFF6D7B8B)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFD3DEE6), Color(0xFFF2F7FA), Color(0xFFAEBCCA)))
    }

    val outlineColor = if (isXRayMode) CyanHighlight else Color(0xFF1F2A33)
    val shadowColor = if (isXRayMode) Color.Transparent else Color(0xFF101820).copy(alpha = dropShadowAlpha)

    // 3D directional ambient shadow beneath workpiece
    if (!isXRayMode) {
        drawOval(
            color = shadowColor,
            topLeft = Offset(xLeft + 2f, bottom - 3f),
            size = Size(w + 4f, 10f)
        )
    }

    when (resolvedShape) {
        WorkpieceShapeType.CYLINDER_SHAFT,
        WorkpieceShapeType.GROOVED_SHAFT -> {
            draw3DCylinderBody(
                xLeft = xLeft,
                top = top,
                widthPx = w,
                heightPx = h,
                bodyBrush = cylinderBrush,
                topCapBrush = topFaceBrush,
                outlineColor = outlineColor,
                hasGroove = resolvedShape == WorkpieceShapeType.GROOVED_SHAFT,
                isXRayMode = isXRayMode
            )
        }

        WorkpieceShapeType.STEPPED_SHAFT -> {
            // Central main measuring diameter (exact widthPx) + stepped upper coaxial journal
            val mainTop = top + h * 0.14f
            val mainH = h * 0.86f
            draw3DCylinderBody(
                xLeft = xLeft,
                top = mainTop,
                widthPx = w,
                heightPx = mainH,
                bodyBrush = cylinderBrush,
                topCapBrush = topFaceBrush,
                outlineColor = outlineColor,
                hasGroove = false,
                isXRayMode = isXRayMode
            )
            // Upper stepped coaxial boss (64% diameter) rising from top face
            if (w > 12f) {
                val bossW = w * 0.62f
                val bossLeft = xLeft + (w - bossW) / 2f
                val bossH = h * 0.24f
                draw3DCylinderBody(
                    xLeft = bossLeft,
                    top = top - bossH * 0.18f,
                    widthPx = bossW,
                    heightPx = bossH + 4f,
                    bodyBrush = cylinderBrush,
                    topCapBrush = topFaceBrush,
                    outlineColor = outlineColor,
                    hasGroove = false,
                    isXRayMode = isXRayMode
                )
            }
        }

        WorkpieceShapeType.RING,
        WorkpieceShapeType.BUSHING_RING,
        WorkpieceShapeType.PIPE -> {
            // 3D Hollow Cylinder / Bushing / Pipe with visible shaded 3D elliptical bore at top
            val capRy = (h * 0.14f).coerceIn(4f, 11f)
            val bodyPath = Path().apply {
                moveTo(xLeft, top + capRy)
                lineTo(xLeft, bottom - capRy)
                // Smooth bottom curved rim
                cubicTo(
                    xLeft, bottom + capRy * 0.35f,
                    xRight, bottom + capRy * 0.35f,
                    xRight, bottom - capRy
                )
                lineTo(xRight, top + capRy)
                close()
            }
            drawPath(bodyPath, brush = cylinderBrush)
            // Specular cylindrical vertical highlight band
            if (!isXRayMode && w > 10f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.28f to Color.White.copy(alpha = 0.42f),
                        0.45f to Color.White.copy(alpha = 0.14f),
                        1.0f to Color.Transparent
                    ),
                    topLeft = Offset(xLeft, top + capRy),
                    size = Size(w, h - capRy * 2f)
                )
            }
            drawPath(bodyPath, color = outlineColor, style = Stroke(1.4f))

            // Top 3D elliptical flange rim
            drawOval(
                brush = topFaceBrush,
                topLeft = Offset(xLeft, top),
                size = Size(w, capRy * 2f)
            )
            drawOval(
                color = outlineColor,
                topLeft = Offset(xLeft, top),
                size = Size(w, capRy * 2f),
                style = Stroke(1.3f)
            )
            // Recessed 3D hollow inner bore opening on the top face
            if (w > 12f) {
                val wallRatio = when (resolvedShape) {
                    WorkpieceShapeType.PIPE -> 0.16f
                    WorkpieceShapeType.RING -> 0.22f
                    else -> 0.25f
                }
                val insetX = (w * wallRatio).coerceAtLeast(3f)
                val insetY = (capRy * 0.34f).coerceAtLeast(1.8f)
                val boreW = (w - insetX * 2f).coerceAtLeast(4f)
                val boreH = (capRy * 2f - insetY * 2f).coerceAtLeast(3f)
                drawOval(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF151E26), Color(0xFF394956), Color(0xFF1C2731))
                    ),
                    topLeft = Offset(xLeft + insetX, top + insetY),
                    size = Size(boreW, boreH)
                )
                drawOval(
                    color = outlineColor,
                    topLeft = Offset(xLeft + insetX, top + insetY),
                    size = Size(boreW, boreH),
                    style = Stroke(1.0f)
                )
            }
        }

        WorkpieceShapeType.HEX_BOLT -> {
            // 3D Faceted Hex Head at top + Threaded Cylindrical Shank (exact widthPx across measuring zone)
            val headH = h * 0.34f
            val shankTop = top + headH
            val shankH = h - headH
            val chamfer = (w * 0.12f).coerceIn(2f, 6f)

            // 1. Threaded cylindrical shank (exact widthPx)
            draw3DCylinderBody(
                xLeft = xLeft,
                top = shankTop - 2f,
                widthPx = w,
                heightPx = shankH + 2f,
                bodyBrush = cylinderBrush,
                topCapBrush = topFaceBrush,
                outlineColor = outlineColor,
                hasGroove = false,
                isXRayMode = isXRayMode
            )
            // Angled 3D helical screw thread ridges across lower 70% of shank
            val threadStartY = shankTop + shankH * 0.24f
            val threadPitch = (shankH * 0.11f).coerceIn(4f, 7f)
            var ty = threadStartY
            while (ty < bottom - 4f) {
                drawLine(
                    color = if (isXRayMode) CyanHighlight.copy(alpha = 0.65f) else Color(0xFF222D37).copy(alpha = 0.68f),
                    start = Offset(xLeft + 1.5f, ty),
                    end = Offset(xRight - 1.5f, ty + 2.4f),
                    strokeWidth = 1.4f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.32f),
                    start = Offset(xLeft + 2f, ty + 1.3f),
                    end = Offset(xRight - 2f, ty + 3.6f),
                    strokeWidth = 0.9f
                )
                ty += threadPitch
            }

            // 2. Faceted 3D Hex Head (fits cleanly inside [xLeft .. xRight] so faces never exceed contact width)
            draw3DHexPrism(
                xLeft = xLeft,
                top = top,
                widthPx = w,
                heightPx = headH,
                chamfer = chamfer,
                outlineColor = outlineColor,
                isXRayMode = isXRayMode
            )
        }

        WorkpieceShapeType.HEX_NUT -> {
            // 3D Faceted Hexagonal Nut across exact widthPx (3 distinct shaded vertical faces + chamfered crown + center bore)
            val capH = (h * 0.18f).coerceIn(5f, 12f)
            val bodyH = h - capH
            val chamfer = (w * 0.10f).coerceIn(2f, 6f)

            // Top chamfered hex crown
            val crownPath = Path().apply {
                moveTo(xLeft + chamfer, top)
                lineTo(xRight - chamfer, top)
                lineTo(xRight, top + capH)
                lineTo(xLeft, top + capH)
                close()
            }
            drawPath(crownPath, brush = topFaceBrush)
            drawPath(crownPath, color = outlineColor, style = Stroke(1.3f))

            // 3 vertical hex facets
            draw3DHexPrism(
                xLeft = xLeft,
                top = top + capH,
                widthPx = w,
                heightPx = bodyH,
                chamfer = chamfer * 0.6f,
                outlineColor = outlineColor,
                isXRayMode = isXRayMode
            )
            // Threaded center bore opening on top face
            if (w > 14f) {
                val boreW = w * 0.48f
                val boreH = (capH * 0.66f).coerceAtLeast(3.5f)
                drawOval(
                    color = Color(0xFF1A252F),
                    topLeft = Offset(xLeft + (w - boreW) / 2f, top + (capH - boreH) / 2f),
                    size = Size(boreW, boreH)
                )
                drawOval(
                    color = outlineColor,
                    topLeft = Offset(xLeft + (w - boreW) / 2f, top + (capH - boreH) / 2f),
                    size = Size(boreW, boreH),
                    style = Stroke(1.0f)
                )
            }
        }

        WorkpieceShapeType.FLAT_PLATE,
        WorkpieceShapeType.HOLED_BLOCK,
        WorkpieceShapeType.STEPPED_BORE_BLOCK -> {
            // 3D Isometric-Beveled Machined Steel/Aluminum Plate or Billet Block
            val depthIsoY = (h * 0.14f).coerceIn(4f, 11f)
            val frontTop = top + depthIsoY
            val frontH = (h - depthIsoY).coerceAtLeast(10f)

            // Angled 3D top milled face (strictly within [xLeft .. xRight] for exact contact faces!)
            val topQuad = Path().apply {
                val skew = (w * 0.08f).coerceIn(2f, 8f)
                moveTo(xLeft + skew, top)
                lineTo(xRight - skew, top)
                lineTo(xRight, frontTop)
                lineTo(xLeft, frontTop)
                close()
            }
            drawPath(topQuad, brush = topFaceBrush)
            drawPath(topQuad, color = outlineColor, style = Stroke(1.2f))

            // Front machined metallic face with crisp vertical milled sheen
            val frontBrush = if (isXRayMode) {
                Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.22f), CyanHighlight.copy(alpha = 0.08f)))
            } else {
                Brush.linearGradient(
                    colors = listOf(Color(0xFFD5E1E9), Color(0xFFEDF4F8), Color(0xFFB3C3CE), Color(0xFF7B8E9C)),
                    start = Offset(xLeft, frontTop),
                    end = Offset(xRight, bottom)
                )
            }
            drawRect(
                brush = frontBrush,
                topLeft = Offset(xLeft, frontTop),
                size = Size(w, frontH)
            )
            // Subtle chamfer highlights along front edges
            if (!isXRayMode) {
                drawLine(Color.White.copy(alpha = 0.65f), Offset(xLeft + 1.5f, frontTop + 1.5f), Offset(xRight - 1.5f, frontTop + 1.5f), 1.6f)
                drawLine(Color.White.copy(alpha = 0.45f), Offset(xLeft + 1.5f, frontTop + 1.5f), Offset(xLeft + 1.5f, bottom - 1.5f), 1.4f)
            }
            drawRect(
                color = outlineColor,
                topLeft = Offset(xLeft, frontTop),
                size = Size(w, frontH),
                style = Stroke(1.5f)
            )

            // Machined bore/hole cue on front face for HOLED_BLOCK / STEPPED_BORE_BLOCK
            if ((resolvedShape == WorkpieceShapeType.HOLED_BLOCK || resolvedShape == WorkpieceShapeType.STEPPED_BORE_BLOCK) && w > 18f) {
                val boreR = (minOf(w, frontH) * 0.26f).coerceIn(4f, 14f)
                val c = Offset(xLeft + w / 2f, frontTop + frontH / 2f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF152029), Color(0xFF394D5C)), center = c, radius = boreR),
                    radius = boreR,
                    center = c
                )
                drawCircle(outlineColor, radius = boreR, center = c, style = Stroke(1.2f))
                if (resolvedShape == WorkpieceShapeType.STEPPED_BORE_BLOCK) {
                    drawCircle(
                        color = Color(0xFF0E161D),
                        radius = boreR * 0.56f,
                        center = c
                    )
                    drawCircle(Color.White.copy(alpha = 0.25f), radius = boreR * 0.56f, center = c, style = Stroke(0.9f))
                }
            }
        }
    }

    // Crisp precision contact sheen ONLY along the left & right measuring contact faces (never boxy borders!)
    if (isInContact && !isXRayMode) {
        val contactColor = when {
            isOverPressure -> ToleranceRed
            isRatchetContact -> ToleranceGreen
            else -> ToleranceGreen.copy(alpha = 0.90f)
        }
        drawLine(
            color = contactColor,
            start = Offset(xLeft, top + h * 0.14f),
            end = Offset(xLeft, bottom - h * 0.14f),
            strokeWidth = 2.6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = contactColor,
            start = Offset(xRight, top + h * 0.14f),
            end = Offset(xRight, bottom - h * 0.14f),
            strokeWidth = 2.6f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.draw3DCylinderBody(
    xLeft: Float,
    top: Float,
    widthPx: Float,
    heightPx: Float,
    bodyBrush: Brush,
    topCapBrush: Brush,
    outlineColor: Color,
    hasGroove: Boolean,
    isXRayMode: Boolean
) {
    val xRight = xLeft + widthPx
    val bottom = top + heightPx
    val capRy = (heightPx * 0.11f).coerceIn(3f, 9f)
    val chamfer = (widthPx * 0.08f).coerceIn(1.5f, 4.5f)

    // Main curved 3D cylinder barrel
    val cylPath = Path().apply {
        moveTo(xLeft, top + capRy)
        lineTo(xLeft, bottom - capRy)
        cubicTo(
            xLeft, bottom + capRy * 0.35f,
            xRight, bottom + capRy * 0.35f,
            xRight, bottom - capRy
        )
        lineTo(xRight, top + capRy)
        close()
    }
    drawPath(cylPath, brush = bodyBrush)

    // Realistic horizontal cylindrical specular reflection + fine lathe turning lines
    if (!isXRayMode && widthPx > 8f) {
        drawRect(
            brush = Brush.horizontalGradient(
                0.00f to Color.Black.copy(alpha = 0.18f),
                0.22f to Color.White.copy(alpha = 0.52f),
                0.36f to Color.White.copy(alpha = 0.22f),
                0.76f to Color.Transparent,
                1.00f to Color.Black.copy(alpha = 0.24f)
            ),
            topLeft = Offset(xLeft, top + capRy),
            size = Size(widthPx, (heightPx - capRy * 2f).coerceAtLeast(2f))
        )
        // Subtle horizontal lathe-turned micro-grooves
        var ly = top + capRy + 6f
        while (ly < bottom - capRy - 4f) {
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(xLeft + 2f, ly),
                end = Offset(xRight - 2f, ly),
                strokeWidth = 0.8f
            )
            ly += 8f
        }
    }

    // Undercut machined groove band for GROOVED_SHAFT
    if (hasGroove && widthPx > 12f) {
        val gTop = top + heightPx * 0.40f
        val gH = heightPx * 0.20f
        drawRect(
            color = Color(0xFF283540).copy(alpha = 0.65f),
            topLeft = Offset(xLeft + 2f, gTop),
            size = Size(widthPx - 4f, gH)
        )
        drawLine(outlineColor, Offset(xLeft, gTop), Offset(xRight, gTop), 1.1f)
        drawLine(outlineColor, Offset(xLeft, gTop + gH), Offset(xRight, gTop + gH), 1.1f)
    }

    drawPath(cylPath, color = outlineColor, style = Stroke(1.4f))

    // Top 3D elliptical chamfered end-cap
    drawOval(
        brush = topCapBrush,
        topLeft = Offset(xLeft, top),
        size = Size(widthPx, capRy * 2f)
    )
    drawOval(
        color = outlineColor,
        topLeft = Offset(xLeft, top),
        size = Size(widthPx, capRy * 2f),
        style = Stroke(1.2f)
    )
    // Inner chamfer ring on top face
    if (widthPx > chamfer * 4f) {
        drawOval(
            color = outlineColor.copy(alpha = 0.35f),
            topLeft = Offset(xLeft + chamfer, top + capRy * 0.28f),
            size = Size(widthPx - chamfer * 2f, capRy * 1.44f),
            style = Stroke(0.9f)
        )
    }
}

private fun DrawScope.draw3DHexPrism(
    xLeft: Float,
    top: Float,
    widthPx: Float,
    heightPx: Float,
    chamfer: Float,
    outlineColor: Color,
    isXRayMode: Boolean
) {
    val leftFacetW = widthPx * 0.24f
    val centerFacetW = widthPx * 0.52f
    val rightFacetW = widthPx - leftFacetW - centerFacetW

    // Left shadowed facet
    val leftColor = if (isXRayMode) CyanHighlight.copy(alpha = 0.14f) else Color(0xFF6B7B8C)
    // Center bright lit facet
    val centerBrush = if (isXRayMode) {
        Brush.verticalGradient(listOf(CyanHighlight.copy(alpha = 0.28f), CyanHighlight.copy(alpha = 0.14f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFCBD6E0), Color(0xFFF1F5F9), Color(0xFF9AAAB8)))
    }
    // Right dark-shaded facet
    val rightColor = if (isXRayMode) CyanHighlight.copy(alpha = 0.10f) else Color(0xFF475569)

    drawRoundRect(
        color = leftColor,
        topLeft = Offset(xLeft, top),
        size = Size(leftFacetW + 1f, heightPx),
        cornerRadius = CornerRadius(chamfer * 0.5f, chamfer * 0.5f)
    )
    drawRect(
        brush = centerBrush,
        topLeft = Offset(xLeft + leftFacetW, top),
        size = Size(centerFacetW, heightPx)
    )
    drawRoundRect(
        color = rightColor,
        topLeft = Offset(xLeft + leftFacetW + centerFacetW - 1f, top),
        size = Size(rightFacetW + 1f, heightPx),
        cornerRadius = CornerRadius(chamfer * 0.5f, chamfer * 0.5f)
    )
    // Vertical hex facet seam ridges
    drawLine(outlineColor.copy(alpha = 0.65f), Offset(xLeft + leftFacetW, top), Offset(xLeft + leftFacetW, top + heightPx), 1.1f)
    drawLine(outlineColor.copy(alpha = 0.65f), Offset(xLeft + leftFacetW + centerFacetW, top), Offset(xLeft + leftFacetW + centerFacetW, top + heightPx), 1.1f)
    drawRoundRect(
        color = outlineColor,
        topLeft = Offset(xLeft, top),
        size = Size(widthPx, heightPx),
        cornerRadius = CornerRadius(chamfer * 0.5f, chamfer * 0.5f),
        style = Stroke(1.4f)
    )
}
