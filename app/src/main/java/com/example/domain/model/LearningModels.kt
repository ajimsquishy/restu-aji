package com.example.domain.model

data class LessonSection(
    val heading: String,
    val body: String,
    val formulaHighlight: String? = null,
    val keyPoints: List<String> = emptyList()
)

data class LessonTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val objectives: List<String>,
    val sections: List<LessonSection>,
    val relatedInstrument: InstrumentType? = null
)

enum class EvaluationCategory(val label: String) {
    PRE_TEST("Pre-Test Penelitian"),
    POST_TEST("Post-Test Penelitian"),
    SCALE_QUIZ("Latihan Baca Skala"),
    MACHINING_CHALLENGE("Machining Challenge")
}

data class QuizQuestion(
    val id: String,
    val category: EvaluationCategory,
    val prompt: String,
    val instrumentType: InstrumentType?,
    val simulatedPositionUm: Int? = null,
    val caliperResolution: CaliperResolution = CaliperResolution.RES_0_05_MM,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String
)

data class MachiningChallenge(
    val id: String,
    val title: String,
    val drawingCode: String,
    val workpieceId: String,
    val zoneId: String,
    val recommendedInstrument: InstrumentType,
    val nominalUm: Int,
    val upperTolUm: Int,
    val lowerTolUm: Int,
    val actualUm: Int,
    val expectedDecisionPass: Boolean,
    val engineeringNote: String
)

data class MeasurementRecord(
    val id: Long = 0,
    val participantCode: String,
    val instrumentType: InstrumentType,
    val workpieceId: String,
    val workpieceName: String,
    val zoneLabel: String,
    val measurementKind: MeasurementKind,
    val measuredUm: Int,
    val referenceUm: Int,
    val errorUm: Int,
    val toleranceStatus: ToleranceStatus,
    val durationSeconds: Int,
    val attemptCount: Int,
    val activeFaultsSummary: String,
    val taskId: Long? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class TeacherTask(
    val id: Long = 0,
    val title: String,
    val instructions: String,
    val allowedInstrument: InstrumentType,
    val workpieceId: String,
    val zoneId: String,
    val nominalUm: Int,
    val upperTolUm: Int,
    val lowerTolUm: Int,
    val isBlindMeasurement: Boolean,
    val timeLimitSeconds: Int,
    val isActive: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class EvaluationAttempt(
    val id: Long = 0,
    val participantCode: String,
    val category: EvaluationCategory,
    val scorePercent: Int,
    val correctCount: Int,
    val totalCount: Int,
    val procedureErrors: Int,
    val durationSeconds: Int,
    val timestampMillis: Long = System.currentTimeMillis()
)
