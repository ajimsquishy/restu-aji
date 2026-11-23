package com.example.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.preferences.UserPreferences
import com.example.di.AppContainer
import com.example.domain.auth.UserRole
import com.example.domain.model.EvaluationCategory
import com.example.domain.model.InstrumentType
import com.example.ui.feature.ai.MetrologyTutorScreen
import com.example.ui.feature.auth.AuthGateScreen
import com.example.ui.feature.calibration.CalibrationScreen
import com.example.ui.feature.caliper.DigitalCaliperScreen
import com.example.ui.feature.caliper.ManualCaliperScreen
import com.example.ui.feature.dashboard.DashboardScreen
import com.example.ui.feature.erroranalysis.ErrorAnalysisScreen
import com.example.ui.feature.evaluation.EvaluationHubScreen
import com.example.ui.feature.evaluation.MachiningChallengeScreen
import com.example.ui.feature.evaluation.QuizRunnerScreen
import com.example.ui.feature.history.MeasurementHistoryScreen
import com.example.ui.feature.learning.LearningHubScreen
import com.example.ui.feature.learning.LessonDetailScreen
import com.example.ui.feature.micrometer.DigitalMicrometerScreen
import com.example.ui.feature.micrometer.ManualMicrometerScreen
import com.example.ui.feature.splash.SplashScreen
import com.example.ui.feature.teacher.TeacherModeScreen
import com.example.ui.feature.tolerance.ToleranceScreen
import com.example.ui.feature.workpiece.WorkpieceLabScreen
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MeasureLabRoutes {
    const val SPLASH = "splash"
    const val AUTH_GATE = "auth_gate"
    const val DASHBOARD = "dashboard"
    const val WORKPIECE_LAB = "workpiece_lab"
    const val LEARNING_HUB = "learning_hub"
    const val LESSON_DETAIL = "lesson_detail/{topicId}"
    const val CALIBRATION = "calibration"
    const val ERROR_ANALYSIS = "error_analysis"
    const val TOLERANCE_LAB = "tolerance_lab"
    const val EVALUATION_HUB = "evaluation_hub"
    const val QUIZ_RUNNER = "quiz_runner/{category}"
    const val MACHINING_CHALLENGE = "machining_challenge"
    const val HISTORY = "history"
    const val TEACHER_MODE = "teacher_mode"
    const val AI_TUTOR = "ai_tutor?topic={topic}"

    const val MANUAL_CALIPER = "manual_caliper?wpId={wpId}&zoneId={zoneId}&taskId={taskId}"
    const val DIGITAL_CALIPER = "digital_caliper?wpId={wpId}&zoneId={zoneId}&taskId={taskId}"
    const val MANUAL_MICROMETER = "manual_micrometer?wpId={wpId}&zoneId={zoneId}&taskId={taskId}"
    const val DIGITAL_MICROMETER = "digital_micrometer?wpId={wpId}&zoneId={zoneId}&taskId={taskId}"

    fun aiTutorRoute(topic: String? = null): String = "ai_tutor?topic=${topic ?: ""}"

    fun simulatorRoute(
        instrument: InstrumentType,
        wpId: String? = null,
        zoneId: String? = null,
        taskId: Long? = null
    ): String {
        val base = when (instrument) {
            InstrumentType.MANUAL_CALIPER -> "manual_caliper"
            InstrumentType.DIGITAL_CALIPER -> "digital_caliper"
            InstrumentType.MANUAL_MICROMETER -> "manual_micrometer"
            InstrumentType.DIGITAL_MICROMETER -> "digital_micrometer"
        }
        return "$base?wpId=${wpId ?: ""}&zoneId=${zoneId ?: ""}&taskId=${taskId ?: -1L}"
    }
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun MeasureLabApp(
    container: AppContainer,
    skipSplash: Boolean = false,
    skipAuth: Boolean = false
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val primaryTabs = listOf(
        TopLevelTab(MeasureLabRoutes.DASHBOARD, "Beranda", Icons.Default.Home, "nav_tab_home"),
        TopLevelTab(MeasureLabRoutes.WORKPIECE_LAB, "Benda", Icons.Default.Category, "nav_tab_workpieces"),
        TopLevelTab(MeasureLabRoutes.simulatorRoute(InstrumentType.MANUAL_CALIPER), "Simulator", Icons.Default.Straighten, "nav_tab_simulator"),
        TopLevelTab(MeasureLabRoutes.LEARNING_HUB, "Materi", Icons.Default.MenuBook, "nav_tab_learning"),
        TopLevelTab(MeasureLabRoutes.EVALUATION_HUB, "Evaluasi", Icons.Default.Quiz, "nav_tab_evaluation")
    )

    val showPrimaryNav = currentRoute in setOf(
        MeasureLabRoutes.DASHBOARD,
        MeasureLabRoutes.WORKPIECE_LAB,
        MeasureLabRoutes.LEARNING_HUB,
        MeasureLabRoutes.EVALUATION_HUB
    )

    fun navigateTopTab(route: String) {
        if (route.startsWith("manual_caliper")) {
            navController.navigate(route)
        } else {
            navController.navigate(route) {
                popUpTo(MeasureLabRoutes.DASHBOARD) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp && showPrimaryNav

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(containerColor = Color.White) {
                    primaryTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = OrangeAccent,
                                selectedTextColor = NavyPrimary,
                                indicatorColor = OrangeContainer,
                                unselectedIconColor = TextSecondary
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                bottomBar = {
                    if (showPrimaryNav && !useRail) {
                        NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
                            primaryTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = OrangeAccent,
                                        selectedTextColor = NavyPrimary,
                                        indicatorColor = OrangeContainer,
                                        unselectedIconColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                val startDest = when {
                    !skipSplash -> MeasureLabRoutes.SPLASH
                    !skipAuth -> MeasureLabRoutes.AUTH_GATE
                    else -> MeasureLabRoutes.DASHBOARD
                }
                fun navigateToAuthGateClearingBackStack() {
                    navController.navigate(MeasureLabRoutes.AUTH_GATE) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    composable(MeasureLabRoutes.SPLASH) {
                        SplashScreen(
                            onFinished = {
                                scope.launch {
                                    val savedPrefs = container.preferencesStore.preferencesFlow.first()
                                    val nextRoute = if (skipAuth || savedPrefs.session.isAuthenticated) {
                                        MeasureLabRoutes.DASHBOARD
                                    } else {
                                        MeasureLabRoutes.AUTH_GATE
                                    }
                                    withContext(Dispatchers.Main.immediate) {
                                        navController.navigate(nextRoute) {
                                            popUpTo(MeasureLabRoutes.SPLASH) { inclusive = true }
                                        }
                                    }
                                }
                            }
                        )
                    }

                    composable(MeasureLabRoutes.AUTH_GATE) {
                        AuthGateScreen(
                            container = container,
                            onAuthenticated = {
                                navController.navigate(MeasureLabRoutes.DASHBOARD) {
                                    popUpTo(MeasureLabRoutes.AUTH_GATE) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(MeasureLabRoutes.DASHBOARD) {
                        DashboardScreen(
                            container = container,
                            onOpenManualCaliper = { navController.navigate(MeasureLabRoutes.simulatorRoute(InstrumentType.MANUAL_CALIPER)) },
                            onOpenDigitalCaliper = { navController.navigate(MeasureLabRoutes.simulatorRoute(InstrumentType.DIGITAL_CALIPER)) },
                            onOpenManualMicrometer = { navController.navigate(MeasureLabRoutes.simulatorRoute(InstrumentType.MANUAL_MICROMETER)) },
                            onOpenDigitalMicrometer = { navController.navigate(MeasureLabRoutes.simulatorRoute(InstrumentType.DIGITAL_MICROMETER)) },
                            onOpenWorkpieceLab = { navigateTopTab(MeasureLabRoutes.WORKPIECE_LAB) },
                            onOpenLearningHub = { navigateTopTab(MeasureLabRoutes.LEARNING_HUB) },
                            onOpenPracticeQuiz = { navController.navigate("quiz_runner/${EvaluationCategory.SCALE_QUIZ.name}") },
                            onOpenCalibration = { navController.navigate(MeasureLabRoutes.CALIBRATION) },
                            onOpenErrorAnalysis = { navController.navigate(MeasureLabRoutes.ERROR_ANALYSIS) },
                            onOpenToleranceLab = { navController.navigate(MeasureLabRoutes.TOLERANCE_LAB) },
                            onOpenEvaluationHub = { navigateTopTab(MeasureLabRoutes.EVALUATION_HUB) },
                            onOpenHistory = { navController.navigate(MeasureLabRoutes.HISTORY) },
                            onOpenTeacherMode = { navController.navigate(MeasureLabRoutes.TEACHER_MODE) },
                            onStartTask = { inst, wpId, zoneId, taskId ->
                                navController.navigate(MeasureLabRoutes.simulatorRoute(inst, wpId, zoneId, taskId))
                            },
                            onLogout = { navigateToAuthGateClearingBackStack() },
                            onOpenAiTutor = { navController.navigate(MeasureLabRoutes.aiTutorRoute()) }
                        )
                    }

                    val simArgs = listOf(
                        navArgument("wpId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("zoneId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("taskId") { type = NavType.LongType; defaultValue = -1L }
                    )

                    composable(MeasureLabRoutes.MANUAL_CALIPER, arguments = simArgs) { entry ->
                        val wpId = entry.arguments?.getString("wpId")?.ifBlank { null }
                        val zoneId = entry.arguments?.getString("zoneId")?.ifBlank { null }
                        val taskId = entry.arguments?.getLong("taskId")?.takeIf { it > 0L }
                        ManualCaliperScreen(
                            container = container,
                            workpieceId = wpId,
                            zoneId = zoneId,
                            taskId = taskId,
                            onBack = { navController.popBackStack() },
                            onOpenLearning = { navController.navigate("lesson_detail/topic_baca_skala") }
                        )
                    }

                    composable(MeasureLabRoutes.DIGITAL_CALIPER, arguments = simArgs) { entry ->
                        val wpId = entry.arguments?.getString("wpId")?.ifBlank { null }
                        val zoneId = entry.arguments?.getString("zoneId")?.ifBlank { null }
                        val taskId = entry.arguments?.getLong("taskId")?.takeIf { it > 0L }
                        DigitalCaliperScreen(
                            container = container,
                            workpieceId = wpId,
                            zoneId = zoneId,
                            taskId = taskId,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(MeasureLabRoutes.MANUAL_MICROMETER, arguments = simArgs) { entry ->
                        val wpId = entry.arguments?.getString("wpId")?.ifBlank { null }
                        val zoneId = entry.arguments?.getString("zoneId")?.ifBlank { null }
                        val taskId = entry.arguments?.getLong("taskId")?.takeIf { it > 0L }
                        ManualMicrometerScreen(
                            container = container,
                            workpieceId = wpId,
                            zoneId = zoneId,
                            taskId = taskId,
                            onBack = { navController.popBackStack() },
                            onOpenLearning = { navController.navigate("lesson_detail/topic_komponen_micrometer") }
                        )
                    }

                    composable(MeasureLabRoutes.DIGITAL_MICROMETER, arguments = simArgs) { entry ->
                        val wpId = entry.arguments?.getString("wpId")?.ifBlank { null }
                        val zoneId = entry.arguments?.getString("zoneId")?.ifBlank { null }
                        val taskId = entry.arguments?.getLong("taskId")?.takeIf { it > 0L }
                        DigitalMicrometerScreen(
                            container = container,
                            workpieceId = wpId,
                            zoneId = zoneId,
                            taskId = taskId,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(MeasureLabRoutes.WORKPIECE_LAB) {
                        WorkpieceLabScreen(
                            container = container,
                            onBack = null,
                            onLaunchSimulator = { inst, wpId, zoneId ->
                                navController.navigate(MeasureLabRoutes.simulatorRoute(inst, wpId, zoneId))
                            }
                        )
                    }

                    composable(MeasureLabRoutes.LEARNING_HUB) {
                        LearningHubScreen(
                            onBack = null,
                            onOpenTopic = { topicId -> navController.navigate("lesson_detail/$topicId") },
                            onOpenAiTutor = { topic -> navController.navigate(MeasureLabRoutes.aiTutorRoute(topic)) }
                        )
                    }

                    composable(
                        route = MeasureLabRoutes.LESSON_DETAIL,
                        arguments = listOf(navArgument("topicId") { type = NavType.StringType })
                    ) { entry ->
                        val topicId = entry.arguments?.getString("topicId") ?: "topic_komponen_caliper"
                        LessonDetailScreen(
                            topicId = topicId,
                            onBack = { navController.popBackStack() },
                            onLaunchSimulator = { inst ->
                                navController.navigate(MeasureLabRoutes.simulatorRoute(inst))
                            },
                            onOpenAiTutor = { topic ->
                                navController.navigate(MeasureLabRoutes.aiTutorRoute(topic))
                            }
                        )
                    }

                    composable(
                        route = MeasureLabRoutes.AI_TUTOR,
                        arguments = listOf(
                            navArgument("topic") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { entry ->
                        val topic = entry.arguments?.getString("topic")?.ifBlank { null }
                        MetrologyTutorScreen(
                            initialTopicParam = topic,
                            onBack = { navController.popBackStack() },
                            onLaunchSimulator = { inst ->
                                navController.navigate(MeasureLabRoutes.simulatorRoute(inst))
                            }
                        )
                    }

                    composable(MeasureLabRoutes.CALIBRATION) {
                        CalibrationScreen(onBack = { navController.popBackStack() })
                    }

                    composable(MeasureLabRoutes.ERROR_ANALYSIS) {
                        ErrorAnalysisScreen(onBack = { navController.popBackStack() })
                    }

                    composable(MeasureLabRoutes.TOLERANCE_LAB) {
                        ToleranceScreen(onBack = { navController.popBackStack() })
                    }

                    composable(MeasureLabRoutes.EVALUATION_HUB) {
                        EvaluationHubScreen(
                            container = container,
                            onBack = null,
                            onStartQuiz = { cat -> navController.navigate("quiz_runner/${cat.name}") },
                            onOpenMachiningChallenge = { navController.navigate(MeasureLabRoutes.MACHINING_CHALLENGE) }
                        )
                    }

                    composable(
                        route = MeasureLabRoutes.QUIZ_RUNNER,
                        arguments = listOf(navArgument("category") { type = NavType.StringType })
                    ) { entry ->
                        val cat = entry.arguments?.getString("category")
                            ?.let { runCatching { EvaluationCategory.valueOf(it) }.getOrNull() }
                            ?: EvaluationCategory.SCALE_QUIZ
                        QuizRunnerScreen(
                            container = container,
                            category = cat,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(MeasureLabRoutes.MACHINING_CHALLENGE) {
                        MachiningChallengeScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenSimulatorForChallenge = { inst, wpId, zoneId ->
                                navController.navigate(MeasureLabRoutes.simulatorRoute(inst, wpId, zoneId))
                            }
                        )
                    }

                    composable(MeasureLabRoutes.HISTORY) {
                        MeasurementHistoryScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenManualCaliper = {
                                navController.navigate(MeasureLabRoutes.simulatorRoute(InstrumentType.MANUAL_CALIPER))
                            }
                        )
                    }

                    composable(MeasureLabRoutes.TEACHER_MODE) {
                        TeacherModeScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onLaunchTask = { inst, wpId, zoneId, taskId ->
                                navController.navigate(MeasureLabRoutes.simulatorRoute(inst, wpId, zoneId, taskId))
                            },
                            onLogout = { navigateToAuthGateClearingBackStack() }
                        )
                    }
                }
            }
        }
    }
}
