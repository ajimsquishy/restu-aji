package com.example

import com.example.domain.engine.DigitalCaliperEngine
import com.example.domain.engine.DigitalCaliperState
import com.example.domain.engine.DigitalMicrometerEngine
import com.example.domain.engine.DigitalMicrometerState
import com.example.domain.engine.ErrorAnalyzer
import com.example.domain.engine.FaultSimulationEngine
import com.example.domain.engine.ManualCaliperEngine
import com.example.domain.engine.ManualCaliperState
import com.example.domain.engine.ManualMicrometerEngine
import com.example.domain.engine.ManualMicrometerState
import com.example.domain.engine.MeasurementUnitConverter
import com.example.domain.engine.ToleranceAnalyzer
import com.example.domain.engine.ToolCompatibilityGuard
import com.example.domain.engine.VernierScaleGeometry
import com.example.domain.model.CaliperResolution
import com.example.domain.model.FaultConfig
import com.example.domain.model.InstrumentType
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.ToleranceSpec
import com.example.domain.model.ToleranceStatus
import com.example.domain.model.ToolCompatibilityResult
import com.example.ui.components.computeCaliperLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MechanicalSimulationEngineTest {

    @Test
    fun unitConverter_integerConversion_matchesExactIndustrialValues() {
        assertEquals("12.350", MeasurementUnitConverter.formatUmAsMm(12_350, decimals = 3))
        assertEquals("0.4862", MeasurementUnitConverter.formatUmAsInch(12_350, decimals = 4))
        assertEquals("24.86", MeasurementUnitConverter.formatUmAsMm(24_860, decimals = 2))
        assertEquals("-0.14", MeasurementUnitConverter.formatUmAsMm(-140, decimals = 2))
        assertEquals("+0.02", MeasurementUnitConverter.formatUmAsMm(20, decimals = 2, showPlusSign = true))
    }

    @Test
    fun manualCaliper_formulaMatchesAllResolutions() {
        // 0.05 mm resolution: 25.50 mm = 25 mm + 10 * 0.05 mm
        val s05 = ManualCaliperState(
            jawPositionUm = 25_500,
            resolution = CaliperResolution.RES_0_05_MM,
            workpieceContactUm = null
        )
        assertEquals(25, s05.mainScaleMm)
        assertEquals(10, s05.vernierCoincidentIndex)
        assertEquals(25_500, s05.readingUm)

        // 0.02 mm resolution: 18.36 mm = 18 mm + 18 * 0.02 mm
        val s02 = ManualCaliperState(
            jawPositionUm = 18_360,
            resolution = CaliperResolution.RES_0_02_MM,
            workpieceContactUm = null
        )
        assertEquals(18, s02.mainScaleMm)
        assertEquals(18, s02.vernierCoincidentIndex)
        assertEquals(18_360, s02.readingUm)

        // Manual Caliper supports strictly 0.05 mm and 0.02 mm
        assertEquals(
            listOf(CaliperResolution.RES_0_05_MM, CaliperResolution.RES_0_02_MM),
            CaliperResolution.entries
        )
        assertEquals(CaliperResolution.RES_0_05_MM, CaliperResolution.fromStepUm(100))
    }

    @Test
    fun manualCaliper_lockPreventsMovement_andCollisionStopsAtWorkpiece() {
        val initial = ManualCaliperState(
            jawPositionUm = 30_000,
            resolution = CaliperResolution.RES_0_05_MM,
            measurementKind = MeasurementKind.OUTSIDE_DIAMETER,
            workpieceContactUm = 25_500
        )
        // Moving below workpiece stops cleanly at contact limit
        val contacted = ManualCaliperEngine.moveBy(initial, -10_000)
        assertEquals(25_500, contacted.jawPositionUm)
        assertTrue(contacted.isInContactWithWorkpiece)

        // Locking prevents any movement
        val locked = ManualCaliperEngine.toggleLock(contacted)
        val attemptedMove = ManualCaliperEngine.moveBy(locked, 5_000)
        assertEquals(25_500, attemptedMove.jawPositionUm)

        // Reset unlocks and opens jaw away from workpiece
        val resetState = ManualCaliperEngine.reset(locked)
        assertEquals(false, resetState.isLocked)
        assertTrue(resetState.jawPositionUm > 25_500)
    }

    @Test
    fun manualCaliper_visualScaleTicksMatchCalculatedReading_acrossFullRangeAndModes() {
        // Verify for both 0.05 mm and 0.02 mm across 0..150 mm that:
        // (1) The visually drawn coincident Vernier tick lands on an EXACT integer mm on the Main Scale
        // (2) Every interior non-coincident tick has non-zero misalignment
        // (3) The coincident graduation ALWAYS sits inside the Magnifier Loupe visible range
        val testPositions05 = listOf(0, 50, 450, 12_350, 25_500, 68_950, 149_950, 150_000)
        val testPositions02 = listOf(0, 20, 480, 18_360, 25_900, 74_880, 149_980, 150_000)

        fun verifyVisualAlignment(posUm: Int, res: CaliperResolution) {
            val st = ManualCaliperState(jawPositionUm = posUm, resolution = res, workpieceContactUm = null)
            assertEquals(posUm, st.readingUm)

            val k = st.vernierCoincidentIndex
            val visualCoincidentUm = VernierScaleGeometry.vernierTickPositionUm(st.snappedUm, res, k)
            val expectedMainScaleUm = VernierScaleGeometry.coincidentMainScaleMm(st) * 1000
            assertEquals("Visual Vernier tick #$k must align at exact 0 µm error with Main Scale", expectedMainScaleUm, visualCoincidentUm)

            // Every other interior vernier mark must NOT coincide with any integer millimeter
            for (i in 1 until res.divisions) {
                if (i != k) {
                    val otherTickUm = VernierScaleGeometry.vernierTickPositionUm(st.snappedUm, res, i)
                    assertTrue("Non-coincident tick #$i must not align with integer mm", otherTickUm % 1000 != 0)
                }
            }

            // Loupe viewport always contains the coincident mark
            val loupeWindow = VernierScaleGeometry.loupeWindowRangeMm(st)
            val coincidentMm = expectedMainScaleUm / 1000f
            assertTrue("Coincident mark $coincidentMm mm must be inside loupe $loupeWindow", coincidentMm in loupeWindow)
        }

        testPositions05.forEach { verifyVisualAlignment(it, CaliperResolution.RES_0_05_MM) }
        testPositions02.forEach { verifyVisualAlignment(it, CaliperResolution.RES_0_02_MM) }

        // Verify all 4 measurement kinds stop accurately at workpiece contact limits
        val insideState = ManualCaliperState(
            jawPositionUm = 5_000,
            resolution = CaliperResolution.RES_0_02_MM,
            measurementKind = MeasurementKind.INSIDE_DIAMETER,
            workpieceContactUm = 15_020
        )
        assertEquals(15_020, ManualCaliperEngine.moveBy(insideState, 20_000).readingUm)

        val depthState = ManualCaliperState(
            jawPositionUm = 2_000,
            resolution = CaliperResolution.RES_0_05_MM,
            measurementKind = MeasurementKind.DEPTH,
            workpieceContactUm = 10_000
        )
        assertEquals(10_000, ManualCaliperEngine.moveBy(depthState, 15_000).readingUm)

        val stepState = ManualCaliperState(
            jawPositionUm = 25_000,
            resolution = CaliperResolution.RES_0_05_MM,
            measurementKind = MeasurementKind.STEP,
            workpieceContactUm = 18_400
        )
        assertEquals(18_400, ManualCaliperEngine.moveBy(stepState, -12_000).readingUm)
    }

    @Test
    fun digitalCaliper_powerZeroHoldUnitAndLockWorkDeterministically() {
        var state = DigitalCaliperState(jawPositionUm = 24_860, workpieceContactUm = null)
        assertEquals(24_860, state.displayedUm)
        assertEquals("24.86", state.formattedLcdText)

        // ZERO at 24.86 -> displays 0.00, moving +1.50 mm displays +1.50 mm
        state = DigitalCaliperEngine.pressZero(state)
        assertEquals(0, state.displayedUm)
        state = DigitalCaliperEngine.moveBy(state, 1_500)
        assertEquals(1_500, state.displayedUm)

        // HOLD freezes LCD while jaw continues moving
        state = DigitalCaliperEngine.toggleHold(state)
        state = DigitalCaliperEngine.moveBy(state, 3_000)
        assertEquals(1_500, state.displayedUm)

        // Unit conversion to inch
        state = DigitalCaliperEngine.toggleUnit(state)
        assertEquals(MeasurementUnit.INCH, state.unit)

        // OFF blanks display
        state = DigitalCaliperEngine.togglePower(state)
        assertNull(state.displayedUm)
    }

    @Test
    fun manualMicrometer_screwPitchFormulaAndRatchetStop() {
        // Initial specification: pitch = 0.5 mm (500 µm), 50 thimble divisions, 0.01 mm (10 µm) resolution, 0–25 mm range
        assertEquals(500, ManualMicrometerEngine.PITCH_UM)
        assertEquals(50, ManualMicrometerEngine.THIMBLE_DIVISIONS)
        assertEquals(10, ManualMicrometerEngine.RESOLUTION_UM)
        assertEquals(25_000, ManualMicrometerEngine.MAX_RANGE_UM)

        // 10.28 mm -> sleeve 10.0 mm + 28 * 0.01 mm
        val s1 = ManualMicrometerState(spindlePositionUm = 10_280, workpieceContactUm = null)
        assertEquals(10_000, s1.sleeveScaleUm)
        assertEquals(28, s1.thimbleDivision)
        assertEquals(10_280, s1.readingUm)

        // 6.77 mm -> sleeve 6.5 mm + 27 * 0.01 mm
        val s2 = ManualMicrometerState(spindlePositionUm = 6_770, workpieceContactUm = null)
        assertEquals(6_500, s2.sleeveScaleUm)
        assertEquals(27, s2.thimbleDivision)
        assertEquals(6_770, s2.readingUm)

        // One full 360° revolution moves spindle by pitch = 500 µm; 7.2° moves 10 µm (1 division)
        val rotated = ManualMicrometerEngine.rotateThimbleDegrees(s2, 360f)
        assertEquals(7_270, rotated.spindlePositionUm)
        val oneDiv = ManualMicrometerEngine.rotateThimbleDegrees(s2, 7.2f)
        assertEquals(6_780, oneDiv.spindlePositionUm)

        // Ratchet stop holds exact contact limit and counts clicks without over-pressure
        val openOnPart = ManualMicrometerState(spindlePositionUm = 11_000, workpieceContactUm = 10_280)
        val ratcheted = ManualMicrometerEngine.moveSpindleBy(openOnPart, -1_500, usingRatchet = true)
        assertEquals(10_280, ratcheted.spindlePositionUm)
        assertEquals(1, ratcheted.ratchetClickCount)
        assertEquals(false, ratcheted.isOverPressureWarning)
    }

    @Test
    fun manualMicrometer_visualScaleLimitsLockAndZeroSetting_areExact() {
        // Visual sleeve + thimble geometry must match exact calculated reading across 0.00..25.00 mm
        val samplePositions = listOf(0, 10, 490, 500, 6_770, 10_000, 10_280, 24_990, 25_000)
        for (posUm in samplePositions) {
            val st = ManualMicrometerState(spindlePositionUm = posUm, workpieceContactUm = null)
            val halfSteps = com.example.domain.engine.MicrometerScaleGeometry.visibleSleeveHalfStepCount(st.snappedUm)
            val divAtDatum = com.example.domain.engine.MicrometerScaleGeometry.thimbleDivisionAtDatum(st.snappedUm)
            val visualReconstructedUm = halfSteps * ManualMicrometerEngine.PITCH_UM + divAtDatum * ManualMicrometerEngine.RESOLUTION_UM
            assertEquals("Visual sleeve + thimble reading must equal $posUm µm", posUm, visualReconstructedUm)
            assertEquals(posUm, st.readingUm)
            assertEquals(posUm / 1000f, com.example.domain.engine.MicrometerScaleGeometry.thimbleBevelPositionMm(st.snappedUm.toFloat()), 0.0001f)
        }

        // Upper & lower spindle limiters
        val atTop = ManualMicrometerEngine.moveSpindleBy(
            ManualMicrometerState(spindlePositionUm = 24_800, workpieceContactUm = null),
            deltaUm = 1_000
        )
        assertEquals(25_000, atTop.spindlePositionUm)
        assertTrue(atTop.isAtUpperLimit)

        val atBottom = ManualMicrometerEngine.moveSpindleBy(
            ManualMicrometerState(spindlePositionUm = 200, workpieceContactUm = null),
            deltaUm = -500,
            usingRatchet = true
        )
        assertEquals(0, atBottom.spindlePositionUm)
        assertTrue(atBottom.isAtLowerLimit)
        assertEquals(1, atBottom.ratchetClickCount)

        // Lock lever blocks spindle motion
        val locked = ManualMicrometerEngine.toggleLock(ManualMicrometerState(spindlePositionUm = 10_280, workpieceContactUm = 10_280))
        val blocked = ManualMicrometerEngine.stepThimble(locked, 50)
        assertEquals(10_280, blocked.spindlePositionUm)
        assertTrue(blocked.isLocked)

        // Zero setting compensates zero error cleanly at closed anvil–spindle position (0.00 mm)
        val withZeroError = ManualMicrometerState(
            spindlePositionUm = 0,
            workpieceContactUm = null,
            faultConfig = FaultConfig(zeroErrorUm = 30)
        )
        assertEquals(30, withZeroError.readingUm)
        val calibrated = ManualMicrometerEngine.calibrateZero(withZeroError)
        assertEquals(0, calibrated.readingUm)
        assertTrue(calibrated.hasZeroOffset)
        val afterMove = ManualMicrometerEngine.moveSpindleBy(calibrated, 5_000)
        assertEquals(5_000, afterMove.readingUm)
    }

    @Test
    fun digitalMicrometer_precisionAndZeroSetting() {
        assertEquals(10, DigitalMicrometerEngine.RESOLUTION_UM)
        var state = DigitalMicrometerState(spindlePositionUm = 12_350, workpieceContactUm = null)
        assertEquals("12.35", state.formattedLcdText)

        // Stepping +0.01 mm (10 µm) updates LCD cleanly
        state = DigitalMicrometerEngine.moveSpindleBy(state, 10)
        assertEquals("12.36", state.formattedLcdText)

        // Unit toggle to inch at 12.35 mm
        state = DigitalMicrometerEngine.moveSpindleBy(state, -10)
        state = DigitalMicrometerEngine.toggleUnit(state)
        assertEquals("0.4862", state.formattedLcdText)

        state = DigitalMicrometerEngine.toggleUnit(state)
        state = DigitalMicrometerEngine.pressZero(state)
        assertEquals("0.00", state.formattedLcdText)
    }

    @Test
    fun toolCompatibilityGuard_rejectsUnsupportedMicrometerKinds() {
        val boreOnMic = ToolCompatibilityGuard.validate(
            InstrumentType.MANUAL_MICROMETER,
            MeasurementKind.INSIDE_DIAMETER,
            15_000
        )
        assertTrue(boreOnMic is ToolCompatibilityResult.Incompatible)

        val depthOnMic = ToolCompatibilityGuard.validate(
            InstrumentType.DIGITAL_MICROMETER,
            MeasurementKind.DEPTH,
            10_000
        )
        assertTrue(depthOnMic is ToolCompatibilityResult.Incompatible)

        val oversizeOnMic = ToolCompatibilityGuard.validate(
            InstrumentType.MANUAL_MICROMETER,
            MeasurementKind.OUTSIDE_DIAMETER,
            30_000
        )
        assertTrue(oversizeOnMic is ToolCompatibilityResult.Incompatible)

        val boreOnCaliper = ToolCompatibilityGuard.validate(
            InstrumentType.MANUAL_CALIPER,
            MeasurementKind.INSIDE_DIAMETER,
            15_000
        )
        assertTrue(boreOnCaliper is ToolCompatibilityResult.Compatible)
    }

    @Test
    fun toleranceAndErrorAnalyzers_andFaultSimulationAreAccurate() {
        val spec = ToleranceSpec(nominalUm = 12_000, upperToleranceUm = 50, lowerToleranceUm = 50)
        assertEquals(ToleranceStatus.IN_TOLERANCE, ToleranceAnalyzer.evaluate(spec, 12_010).status)
        assertEquals(ToleranceStatus.BORDERLINE_CHECK, ToleranceAnalyzer.evaluate(spec, 12_048).status)
        assertEquals(ToleranceStatus.OUT_OF_TOLERANCE, ToleranceAnalyzer.evaluate(spec, 12_350).status)

        // Error analysis from Mockup 1: 24.86 vs 25.00 -> -0.14 mm, 0.56%
        val err = ErrorAnalyzer.analyze(measuredUm = 24_860, referenceUm = 25_000)
        assertEquals(-140, err.absoluteErrorUm)
        assertEquals(56, err.relativeErrorPercentTimes100)
        assertEquals("0.56%", MeasurementUnitConverter.formatPercentTimes100(err.relativeErrorPercentTimes100))

        // Fault bias combines zero error + dirty surface + over-pressure
        val faultBias = FaultSimulationEngine.computeFaultBiasUm(
            basePositionUm = 25_000,
            faultConfig = FaultConfig(zeroErrorUm = 20, dirtySurfaceEnabled = true, dirtyParticleUm = 40, overPressureEnabled = true),
            hasWorkpieceContact = true
        )
        assertEquals(20 + 40 - 30, faultBias)
    }

    @Test
    fun workpieceCatalogAndGeometryEngine_allObjectsDeriveExactGeometryFromStoredDimensions() {
        val catalog = com.example.data.catalog.WorkpieceCatalog.defaultWorkpieces
        val presentShapes = catalog.map { it.shapeType }.toSet()

        // Verify all required machining objects exist distinctly in catalog
        val requiredShapes = listOf(
            com.example.domain.model.WorkpieceShapeType.CYLINDER_SHAFT,
            com.example.domain.model.WorkpieceShapeType.STEPPED_SHAFT,
            com.example.domain.model.WorkpieceShapeType.RING,
            com.example.domain.model.WorkpieceShapeType.BUSHING_RING,
            com.example.domain.model.WorkpieceShapeType.HEX_BOLT,
            com.example.domain.model.WorkpieceShapeType.HEX_NUT,
            com.example.domain.model.WorkpieceShapeType.FLAT_PLATE,
            com.example.domain.model.WorkpieceShapeType.PIPE,
            com.example.domain.model.WorkpieceShapeType.HOLED_BLOCK
        )
        requiredShapes.forEach { shape ->
            assertTrue("Catalog must contain $shape", shape in presentShapes)
        }

        // Every workpiece's rendered millimeter profile & zone guide spans must match stored actualUm data
        catalog.forEach { wp ->
            val profile = com.example.domain.engine.WorkpieceGeometryEngine.buildProfile(wp)
            assertTrue("Bounding width must be positive for ${wp.id}", profile.boundingWidthMm > 0f)
            assertTrue("Bounding height must be positive for ${wp.id}", profile.boundingHeightMm > 0f)
            assertEquals("Every zone must have a geometry guide for ${wp.id}", wp.zones.size, profile.zoneGuides.size)

            wp.zones.forEach { zone ->
                val guide = profile.zoneGuides[zone.zoneId]
                assertTrue("Guide missing for ${wp.id}/${zone.zoneId}", guide != null)
                assertEquals(
                    "Guide millimeter span must match stored actualUm for ${wp.id}/${zone.zoneId}",
                    zone.actualUm / 1000f,
                    guide!!.spanMm,
                    0.05f
                )
            }
        }

        // Proof of zero detached static images: changing stored actualUm directly alters parametric geometry
        val baseShaft = catalog.first { it.id == "wp_cyl_shaft" }
        val resizedShaft = baseShaft.copy(
            zones = baseShaft.zones.map { z ->
                if (z.zoneId == "z_od") z.copy(actualUm = 42_000) else z
            }
        )
        val baseProfile = com.example.domain.engine.WorkpieceGeometryEngine.buildProfile(baseShaft)
        val resizedProfile = com.example.domain.engine.WorkpieceGeometryEngine.buildProfile(resizedShaft)
        assertEquals(25.5f, baseProfile.primaryOuterDiaMm, 0.01f)
        assertEquals(42.0f, resizedProfile.primaryOuterDiaMm, 0.01f)
        assertEquals(42.0f, resizedProfile.zoneGuides["z_od"]!!.spanMm, 0.01f)
    }

    @Test
    fun audit_roundToStep_isSignSafeForNegativeAndPositiveValues() {
        assertEquals(-40, MeasurementUnitConverter.roundToStep(-40, 10))
        assertEquals(-50, MeasurementUnitConverter.roundToStep(-45, 10))
        assertEquals(50, MeasurementUnitConverter.roundToStep(45, 10))
        assertEquals(-40, MeasurementUnitConverter.roundToStep(-40, 20))
        assertEquals(-50, MeasurementUnitConverter.roundToStep(-40, 50))
        assertEquals(0, MeasurementUnitConverter.roundToStep(0, 50))
    }

    @Test
    fun audit_negativeAndPositiveZeroErrorCalibration_worksAcrossAllFourInstruments() {
        // 1. Digital Caliper (-0.04 mm zero error)
        var dc = DigitalCaliperState(
            jawPositionUm = 0,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = -40)
        )
        assertEquals(-40, dc.displayedUm)
        assertEquals("-0.04", dc.formattedLcdText)
        dc = DigitalCaliperEngine.pressZero(dc)
        assertEquals(0, dc.displayedUm)
        assertEquals("0.00", dc.formattedLcdText)
        dc = DigitalCaliperEngine.moveBy(dc, 10_000)
        assertEquals(10_000, dc.displayedUm)
        assertEquals("10.00", dc.formattedLcdText)

        // 2. Digital Micrometer (-0.04 mm zero error)
        var dm = DigitalMicrometerState(
            spindlePositionUm = 0,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = -40)
        )
        assertEquals(-40, dm.displayedUm)
        assertEquals("-0.04", dm.formattedLcdText)
        dm = DigitalMicrometerEngine.pressZero(dm)
        assertEquals(0, dm.displayedUm)
        dm = DigitalMicrometerEngine.moveSpindleBy(dm, 10_000)
        assertEquals(10_000, dm.displayedUm)

        // 3. Manual Micrometer (-0.04 mm zero error -> thimble at 46 before zeroing, 0 after)
        var mm = ManualMicrometerState(
            spindlePositionUm = 0,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = -40)
        )
        assertEquals(-40, mm.readingUm)
        assertEquals(46, mm.thimbleDivision)
        mm = ManualMicrometerEngine.calibrateZero(mm)
        assertEquals(0, mm.readingUm)
        assertEquals(0, mm.thimbleDivision)
        mm = ManualMicrometerEngine.moveSpindleBy(mm, 10_000)
        assertEquals(10_000, mm.readingUm)

        // 4. Manual Caliper (-0.04 mm zero error at 0.02 mm resolution)
        var mc = ManualCaliperState(
            jawPositionUm = 0,
            resolution = CaliperResolution.RES_0_02_MM,
            workpieceContactUm = null,
            isInContactWithWorkpiece = false,
            faultConfig = FaultConfig(zeroErrorUm = -40)
        )
        assertEquals(-40, mc.readingUm)
        mc = ManualCaliperEngine.calibrateZero(mc)
        assertEquals(0, mc.readingUm)
        mc = ManualCaliperEngine.moveBy(mc, 10_000)
        assertEquals(10_000, mc.readingUm)
    }

    @Test
    fun audit_lockBlocksCloseToZero_andMechanicalLimitsEmitWarnings() {
        val lockedCaliper = ManualCaliperState(
            jawPositionUm = 25_000,
            isLocked = true,
            workpieceContactUm = 25_000
        )
        val attemptedClose = ManualCaliperEngine.closeToZero(lockedCaliper)
        assertEquals(25_000, attemptedClose.jawPositionUm)
        assertTrue(attemptedClose.procedureWarning != null)

        // Mechanical upper and lower limits emit explicit warnings
        val calUpper = ManualCaliperEngine.moveBy(
            ManualCaliperState(jawPositionUm = 149_000, workpieceContactUm = null),
            deltaUm = 5_000
        )
        assertEquals(150_000, calUpper.jawPositionUm)
        assertTrue(calUpper.procedureWarning != null)

        val calLower = ManualCaliperEngine.moveBy(
            ManualCaliperState(jawPositionUm = 500, workpieceContactUm = null),
            deltaUm = -2_000
        )
        assertEquals(0, calLower.jawPositionUm)
        assertTrue(calLower.procedureWarning != null)

        val digMicUpper = DigitalMicrometerEngine.moveSpindleBy(
            DigitalMicrometerState(spindlePositionUm = 24_500, workpieceContactUm = null),
            deltaUm = 2_000
        )
        assertEquals(25_000, digMicUpper.spindlePositionUm)
        assertTrue(digMicUpper.procedureWarning != null)
    }

    @Test
    fun audit_errorAnalyzerZeroReference_andZeroWidthToleranceBand_neverMaskErrors() {
        val zeroRefErr = ErrorAnalyzer.analyze(measuredUm = 25_000, referenceUm = 0)
        assertEquals(25_000, zeroRefErr.absoluteErrorUm)
        assertTrue(zeroRefErr.isRelativeErrorUndefined)

        val exactSpec = ToleranceSpec(nominalUm = 12_000, upperToleranceUm = 0, lowerToleranceUm = 0)
        assertEquals(ToleranceStatus.IN_TOLERANCE, ToleranceAnalyzer.evaluate(exactSpec, 12_000).status)
        assertEquals(ToleranceStatus.OUT_OF_TOLERANCE, ToleranceAnalyzer.evaluate(exactSpec, 12_010).status)
    }

    @Test
    fun caliperLayout_insideDiameter_jawsAlignFlushWithInnerBoreWalls() {
        val boreSamplesUm = listOf(8_520, 12_020, 14_020, 15_020, 16_100, 20_050, 60_000)
        for (boreUm in boreSamplesUm) {
            val layout = computeCaliperLayout(1000f, MeasurementKind.INSIDE_DIAMETER, boreUm)
            // At 0.00 mm, both inside jaw measuring faces coincide cleanly
            assertEquals(layout.fixedInsideFaceX, layout.movableInsideFaceX(0), 0.001f)
            // At bore contact, right movable inside face sits flush on the right inner bore wall
            val expectedBoreRightX = layout.fixedInsideFaceX + (boreUm / 1000f) * layout.pxPerMm
            assertEquals(expectedBoreRightX, layout.movableInsideFaceX(boreUm), 0.01f)
            // Bore sits strictly inside canvas width with room for left cross-section wall
            assertTrue(layout.fixedInsideFaceX > 14f)
            assertTrue(expectedBoreRightX < layout.beamEndX)
        }
    }

    @Test
    fun caliperLayout_depthMode_neverClipsAndAlignsDepthRodTipWithHoleBottom() {
        val depthSamplesUm = listOf(2_760, 8_500, 10_000, 19_200, 45_000, 100_000, 150_000)
        for (depthUm in depthSamplesUm) {
            val layout = computeCaliperLayout(1000f, MeasurementKind.DEPTH, depthUm)
            val expectedHoleBottomX = layout.beamEndX + (depthUm / 1000f) * layout.pxPerMm
            // Depth rod tip reaches exact blind-hole bottom without premature clamping
            assertEquals(expectedHoleBottomX, layout.depthRodTipX(depthUm), 0.01f)
            // Blind hole bottom + wall fits cleanly inside the 1000 px canvas
            assertTrue(expectedHoleBottomX <= 1000f - 16f)
            // Depth rod is completely flush (0 px) when jaws are closed
            assertEquals(layout.beamEndX, layout.depthRodTipX(0), 0.001f)
        }
        // Free-measure DEPTH mode supports full 150 mm extension without clipping
        val freeDepthLayout = computeCaliperLayout(1000f, MeasurementKind.DEPTH, null)
        assertEquals(
            freeDepthLayout.beamEndX + 150f * freeDepthLayout.pxPerMm,
            freeDepthLayout.depthRodTipX(150_000),
            0.01f
        )
    }
}
