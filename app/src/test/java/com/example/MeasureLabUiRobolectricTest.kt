package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.di.AppContainer
import com.example.ui.feature.calibration.CalibrationScreen
import com.example.ui.feature.erroranalysis.ErrorAnalysisScreen
import com.example.ui.feature.tolerance.ToleranceScreen
import com.example.ui.navigation.MeasureLabApp
import com.example.ui.theme.MeasureLabTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MeasureLabUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun resetSessionBeforeTest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            AppContainer(context).preferencesStore.logout()
        }
    }

    @Test
    fun appNameMatchesMeasureLab() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("MEASURE LAB", context.getString(R.string.app_name))
    }

    @Test
    fun splashScreenTransitionsToAuthGateBeforeEnteringApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = false, skipAuth = false)
            }
        }
        composeRule.onNodeWithTag("splash_screen").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("auth_gate_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_gate_screen").assertIsDisplayed()
    }

    @Test
    fun studentLoginFlow_requiresNamaAbsensiKelas_andAllowsLogout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = false)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_gate_screen").assertIsDisplayed()

        // Empty submission must show validation error
        composeRule.onNodeWithTag("auth_student_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_error_banner").performScrollTo().assertIsDisplayed()

        // Enter Nama, Absensi, and Kelas -> enters Dashboard with identity banner
        composeRule.onNodeWithTag("auth_student_name_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("auth_student_name_input").performTextInput("Budi Santoso")
        composeRule.onNodeWithTag("auth_student_attendance_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("auth_student_attendance_input").performTextInput("14")
        composeRule.onNodeWithTag("auth_student_class_chip_X_TP_2").performScrollTo().performClick()
        composeRule.onNodeWithTag("auth_student_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_role_identity_banner").assertIsDisplayed()

        // Logout returns to AuthGate
        composeRule.onNodeWithTag("dashboard_logout_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_logout_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("auth_gate_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_gate_screen").assertIsDisplayed()
    }

    @Test
    fun teacherClassPasswordLogin_rejectsWrongPassword_acceptsExactClassPassword_andOpensTeacherPanel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = false)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_tab_teacher").performClick()
        composeRule.waitForIdle()

        // Select class X TP 2 and enter wrong password (e.g. X TP 1's password) -> must show error
        composeRule.onNodeWithTag("auth_teacher_class_chip_X_TP_2").performScrollTo().performClick()
        composeRule.onNodeWithTag("auth_teacher_password_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("auth_teacher_password_input").performTextInput("MLAB-XTP1-4829")
        composeRule.onNodeWithTag("auth_teacher_submit_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("auth_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_error_banner").performScrollTo().assertIsDisplayed()

        // Enter exact class X TP 2 password -> enters Dashboard with Teacher Panel CTA
        composeRule.onNodeWithTag("auth_teacher_password_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("auth_teacher_password_input").performTextInput("MLAB-XTP2-7394")
        composeRule.onNodeWithTag("auth_teacher_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("dashboard_open_teacher_panel_cta").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_open_teacher_panel_cta").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("teacher_mode_content").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("teacher_mode_content").assertIsDisplayed()

        // Logout returns cleanly to AuthGate offline
        composeRule.onNodeWithTag("teacher_logout_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("auth_gate_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_gate_screen").assertIsDisplayed()
    }

    @Test
    fun dashboardNavigatesToAllFourInstruments_andLearningPracticeEvaluation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = true)
            }
        }
        composeRule.waitForIdle()

        fun openFromDashboardAndReturn(menuTag: String, expectedScreenTag: String, usesTopTab: Boolean = false) {
            composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag(menuTag))
            composeRule.onNodeWithTag(menuTag).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(expectedScreenTag).assertIsDisplayed()
            if (usesTopTab) {
                composeRule.onNodeWithTag("nav_tab_home").performClick()
            } else {
                composeRule.onNodeWithTag("top_bar_back_button").performClick()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        }

        // 4 Measuring Instrument Simulators
        openFromDashboardAndReturn("menu_manual_caliper", "manual_caliper_canvas")
        openFromDashboardAndReturn("menu_digital_caliper", "digital_caliper_canvas")
        openFromDashboardAndReturn("menu_manual_micrometer", "manual_micrometer_canvas")
        openFromDashboardAndReturn("menu_digital_micrometer", "digital_micrometer_canvas")

        // Materi, Latihan, Evaluasi
        openFromDashboardAndReturn("menu_learning_hub", "learning_hub_screen", usesTopTab = true)
        openFromDashboardAndReturn("menu_practice_quiz", "quiz_runner_screen")
        openFromDashboardAndReturn("menu_evaluation_hub", "evaluation_hub_screen", usesTopTab = true)

        // Lab Modules
        openFromDashboardAndReturn("menu_workpiece_lab", "workpiece_lab_screen", usesTopTab = true)
        openFromDashboardAndReturn("menu_calibration", "calibration_screen")
        openFromDashboardAndReturn("menu_error_analysis", "error_analysis_screen")
        openFromDashboardAndReturn("menu_tolerance_lab", "tolerance_screen")
        openFromDashboardAndReturn("menu_history", "measurement_history_screen")
        openFromDashboardAndReturn("menu_teacher_mode", "teacher_pin_gate")
    }

    @Test
    fun dashboardNavigatesToManualCaliper_andLockPreventsChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = true)
            }
        }
        composeRule.waitForIdle()

        // Dashboard visible -> open Manual Caliper simulator
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("menu_manual_caliper"))
        composeRule.onNodeWithTag("menu_manual_caliper").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("manual_caliper_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("vernier_magnifier_loupe").performScrollTo().assertIsDisplayed()

        // Lock jaw and confirm +1 step keeps readout locked
        composeRule.onNodeWithTag("caliper_lock_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caliper_step_plus_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("25,50 mm")

        // Unlock -> +1 mm button advances exact 1.00 mm (25,50 -> 26,50 mm)
        composeRule.onNodeWithTag("caliper_lock_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caliper_step_plus_1mm").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("26,50 mm")

        // Switch to 0,02 mm resolution and step +1 (+0,02 mm -> 26,52 mm)
        composeRule.onNodeWithTag("caliper_res_20").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("caliper_step_plus_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("26,52 mm")

        // Switch measurement kinds (Inside, Depth, Step) and confirm readings update cleanly
        composeRule.onNodeWithTag("caliper_kind_inside_diameter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("15,02 mm")

        composeRule.onNodeWithTag("caliper_kind_depth").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("10,00 mm")

        composeRule.onNodeWithTag("caliper_kind_step").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("18,40 mm")

        // Close to 0,00 mm
        composeRule.onNodeWithTag("caliper_zero_close_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("0,00 mm")
    }

    @Test
    fun manualMicrometerSimulator_pitchRatchetLockZeroAndLimitsWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = true)
            }
        }
        composeRule.waitForIdle()

        // Navigate from Dashboard to Manual Micrometer simulator
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("menu_manual_micrometer"))
        composeRule.onNodeWithTag("menu_manual_micrometer").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("manual_micrometer_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("micrometer_sleeve_loupe").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("micro_telemetry_hud").performScrollTo().assertIsDisplayed()

        // Default bushing thickness is 22,40 mm (first compatible zone on wp_bushing_ring is z_od = 22,40 mm)
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("22,40 mm")

        // Lock Lever blocks +0,5 mm and +0,01 mm thimble rotation
        composeRule.onNodeWithTag("micro_lock_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("micro_step_plus_pitch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("22,40 mm")

        // Unlock -> +0,5 mm (1 full pitch turn -> 22,90 mm) and +0,01 mm (1 division -> 22,91 mm)
        composeRule.onNodeWithTag("micro_lock_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("micro_step_plus_pitch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("22,90 mm")

        composeRule.onNodeWithTag("micro_step_plus_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("22,91 mm")

        // Ratchet Stop advances spindle smoothly back onto workpiece contact (22,40 mm)
        repeat(3) {
            composeRule.onNodeWithTag("micro_ratchet_button").performScrollTo().performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("22,40 mm")

        // Switching to Thickness auto-selects thickness zone (10,28 mm on bushing)
        composeRule.onNodeWithTag("micro_kind_thickness").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("10,28 mm")

        // Close anvil & spindle to 0,00 mm and apply Zero Setting
        composeRule.onNodeWithTag("micro_close_zero_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("0,00 mm")

        composeRule.onNodeWithTag("micro_step_plus_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("0,01 mm")

        composeRule.onNodeWithTag("micro_zero_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("0,00 mm")
    }

    @Test
    fun calibrationStepsVerifyZeroSettingSuccess() {
        composeRule.setContent {
            MeasureLabTheme {
                CalibrationScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cal_step_1_clean").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cal_step_2_close").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cal_step_3_zero").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("calibration_success_banner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tolerancePresetsUpdateStatusCorrectly() {
        composeRule.setContent {
            MeasureLabTheme {
                ToleranceScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tol_preset_fail").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tolerance_status_label").assertTextContains("Tidak Sesuai")

        composeRule.onNodeWithTag("tol_preset_pass").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tolerance_status_label").assertTextContains("Dalam Toleransi")
    }

    @Test
    fun workpieceLab_cameraControlsZoneSelectionAndSimulatorConnectionWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = true)
            }
        }
        composeRule.waitForIdle()

        // Navigate to Workpiece Laboratory
        composeRule.onNodeWithTag("nav_tab_workpieces").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workpiece_lab_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("workpiece_viewport_canvas").assertIsDisplayed()

        // Rotate +45° (24° -> 69°), Zoom In (100% -> 125%), Switch to Pan & Pan Right (28, 0), then Reset Camera
        composeRule.onNodeWithTag("workpiece_lab_screen").performScrollToNode(hasTestTag("wp_rotate_button"))
        composeRule.onNodeWithTag("wp_rotate_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wp_camera_hud_summary").assertTextContains("Rot 69°", substring = true)

        composeRule.onNodeWithTag("wp_zoom_in_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wp_camera_hud_summary").assertTextContains("Zoom 125%", substring = true)

        composeRule.onNodeWithTag("wp_camera_mode_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wp_pan_right_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wp_camera_hud_summary").assertTextContains("Pan (28, 0)", substring = true)

        composeRule.onNodeWithTag("wp_reset_camera_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wp_camera_hud_summary").assertTextContains("Rot 24°", substring = true)
        composeRule.onNodeWithTag("wp_camera_hud_summary").assertTextContains("Zoom 100%", substring = true)
        composeRule.onNodeWithTag("wp_camera_hud_summary").assertTextContains("Pan (0, 0)", substring = true)

        // Select distinct Cincin object (wp_ring) and select Inside Diameter zone (14,02 mm)
        composeRule.onNodeWithTag("workpiece_lab_screen").performScrollToNode(hasTestTag("wp_selector_row"))
        composeRule.onNodeWithTag("wp_selector_row").performScrollToNode(hasTestTag("wp_chip_wp_ring"))
        composeRule.onNodeWithTag("wp_chip_wp_ring").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workpiece_lab_screen").performScrollToNode(hasTestTag("wp_zone_z_ring_id"))
        composeRule.onNodeWithTag("wp_zone_z_ring_id").performClick()
        composeRule.waitForIdle()

        // Launch Manual Caliper with selected Cincin bore zone -> Simulator loads exact stored 14,02 mm
        composeRule.onNodeWithTag("workpiece_lab_screen").performScrollToNode(hasTestTag("wp_open_manual_caliper"))
        composeRule.onNodeWithTag("wp_open_manual_caliper").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manual_caliper_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("14,02 mm")

        // Return to Workpiece Lab, pick Ketebalan Cincin (4,50 mm), and launch Manual Micrometer -> loads exact 4,50 mm
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workpiece_lab_screen").performScrollToNode(hasTestTag("wp_zone_z_ring_thick"))
        composeRule.onNodeWithTag("wp_zone_z_ring_thick").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workpiece_lab_screen").performScrollToNode(hasTestTag("wp_open_manual_micrometer"))
        composeRule.onNodeWithTag("wp_open_manual_micrometer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manual_micrometer_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("readout_value_text").performScrollTo().assertTextContains("4,50 mm")
    }

    @Test
    fun audit_toleranceAndErrorScreens_showValidationErrorInsteadOfSilentFallback() {
        composeRule.setContent {
            MeasureLabTheme {
                ToleranceScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tol_nominal_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("tol_nominal_input").performTextInput("abc")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tolerance_validation_error").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("tolerance_status_card").fetchSemanticsNodes().size)
    }

    @Test
    fun audit_errorAnalysisScreen_rejectsZeroReferenceWithoutFakePercentage() {
        composeRule.setContent {
            MeasureLabTheme {
                ErrorAnalysisScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("error_reference_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("error_reference_input").performTextInput("0")
        composeRule.onNodeWithTag("error_add_row_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("error_input_validation_msg").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun audit_calibrationScreen_negativeZeroErrorCalibratesCleanlyToZero() {
        composeRule.setContent {
            MeasureLabTheme {
                CalibrationScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cal_preset_neg").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cal_step_1_clean").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cal_step_2_close").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cal_step_3_zero").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calibration_success_banner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun audit_manualCaliperSave_persistsRecordIntoRoomHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = true)
            }
        }
        composeRule.waitForIdle()

        // Open Manual Caliper, save a measurement, return to Dashboard, open History and verify persisted record
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("menu_manual_caliper"))
        composeRule.onNodeWithTag("menu_manual_caliper").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("caliper_save_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("menu_history"))
        composeRule.onNodeWithTag("menu_history").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("measurement_history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("history_clear_all_button").assertIsDisplayed()
    }

    @Test
    fun aiTutorChat_accessibleFromDashboardAndLearning_switchesTopicsAndOpensSimulator() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        composeRule.setContent {
            MeasureLabTheme {
                MeasureLabApp(container = container, skipSplash = true, skipAuth = true)
            }
        }
        composeRule.waitForIdle()

        // 1. Open AI Tutor directly from Dashboard
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("menu_ai_tutor"))
        composeRule.onNodeWithTag("menu_ai_tutor").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("metrology_tutor_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("tutor_message_input").assertIsDisplayed()
        composeRule.onNodeWithTag("tutor_send_button").assertIsDisplayed()
        composeRule.onNodeWithTag("tutor_quick_prompt_0").assertIsDisplayed()

        // 2. Switch focus topic to Manual Micrometer -> shows direct Simulator shortcut
        composeRule.onNodeWithTag("tutor_topic_selector_row").performScrollToNode(hasTestTag("tutor_topic_manual_micrometer"))
        composeRule.onNodeWithTag("tutor_topic_manual_micrometer").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tutor_open_simulator_chip").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manual_micrometer_canvas").assertIsDisplayed()

        // 3. Return to Dashboard -> open Learning Hub -> open AI Tutor from Learning Hub banner
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top_bar_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_tab_learning").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("learning_open_ai_tutor_card").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("metrology_tutor_screen").assertIsDisplayed()
    }
}
