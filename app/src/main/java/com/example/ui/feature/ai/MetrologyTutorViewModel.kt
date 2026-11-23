package com.example.ui.feature.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.InstrumentType
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "MetrologyTutorVM"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val INFERENCE_TIMEOUT_MS = 60_000L

enum class TutorFocusTopic(
    val id: String,
    val label: String,
    val shortLabel: String,
    val relatedInstrument: InstrumentType?,
    val quickPrompts: List<String>
) {
    ALL(
        id = "all",
        label = "Semua Alat Ukur",
        shortLabel = "Semua",
        relatedInstrument = null,
        quickPrompts = listOf(
            "Jelaskan perbedaan jangka sorong dan mikrometer sekrup beserta kapan masing-masing dipakai.",
            "Bagaimana langkah SOP pengukuran presisi yang benar di bengkel pemesinan?",
            "Apa saja kesalahan umum (paralaks & gaya tekan) saat mengukur dan cara menghindarinya?"
        )
    ),
    MANUAL_CALIPER(
        id = "manual_caliper",
        label = "Jangka Sorong Manual",
        shortLabel = "J. Sorong Manual",
        relatedInstrument = InstrumentType.MANUAL_CALIPER,
        quickPrompts = listOf(
            "Bagaimana cara membaca skala nonius jangka sorong ketelitian 0,05 mm dan 0,02 mm?",
            "Bagaimana posisi yang benar saat mengukur diameter dalam dan kedalaman lubang dengan jangka sorong?",
            "Apa fungsi sekrup pengunci (lock screw) dan roda penggerak (thumb wheel)?"
        )
    ),
    DIGITAL_CALIPER(
        id = "digital_caliper",
        label = "Jangka Sorong Digital",
        shortLabel = "J. Sorong Digital",
        relatedInstrument = InstrumentType.DIGITAL_CALIPER,
        quickPrompts = listOf(
            "Apa fungsi tombol ZERO, HOLD, dan mm/inch pada jangka sorong digital?",
            "Bagaimana cara merawat sensor kapasitif jangka sorong digital agar tetap akurat?",
            "Kapan kita menggunakan mode pengukuran komparatif dengan tombol ZERO?"
        )
    ),
    MANUAL_MICROMETER(
        id = "manual_micrometer",
        label = "Mikrometer Manual",
        shortLabel = "Mikro Manual",
        relatedInstrument = InstrumentType.MANUAL_MICROMETER,
        quickPrompts = listOf(
            "Bagaimana cara membaca skala sleeve (0,5 mm) dan thimble (0,01 mm) pada mikrometer manual?",
            "Mengapa wajib memutar Ratchet Stop (bukan thimble) saat spindle menyentuh benda kerja?",
            "Mengapa rangka mikrometer diberi isolator panas dan apa efek panas tangan pada hasil ukur?"
        )
    ),
    DIGITAL_MICROMETER(
        id = "digital_micrometer",
        label = "Mikrometer Digital",
        shortLabel = "Mikro Digital",
        relatedInstrument = InstrumentType.DIGITAL_MICROMETER,
        quickPrompts = listOf(
            "Bagaimana cara menggunakan tombol ABS/INC, ZERO/SET, dan mm/in pada mikrometer digital 0,001 mm?",
            "Bagaimana prosedur kalibrasi nol (zero setting) mikrometer digital sebelum mengukur?",
            "Mengapa mikrometer luar tidak boleh digunakan untuk mengukur diameter lubang dalam?"
        )
    ),
    CALIBRATION_TOLERANCE(
        id = "calibration_tolerance",
        label = "Kalibrasi & Toleransi",
        shortLabel = "Kalibrasi & Tol.",
        relatedInstrument = null,
        quickPrompts = listOf(
            "Bagaimana cara mengoreksi kesalahan titik nol positif (+0,04 mm) dan negatif (-0,02 mm)?",
            "Jelaskan arti toleransi dimensi 25,00 ± 0,05 mm dan cara menentukan status PASS/REJECT.",
            "Bagaimana cara menghitung galat absolut dan galat relatif dari hasil pengukuran?"
        )
    );

    companion object {
        fun fromRouteParam(param: String?): TutorFocusTopic {
            if (param.isNullOrBlank()) return ALL
            return entries.firstOrNull {
                it.id.equals(param, ignoreCase = true) ||
                    it.relatedInstrument?.name.equals(param, ignoreCase = true)
            } ?: ALL
        }
    }
}

enum class ChatAuthor {
    USER,
    MODEL
}

data class TutorChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val author: ChatAuthor,
    val text: String,
    val topicLabel: String? = null,
    val isStreaming: Boolean = false
)

sealed interface TutorStatus {
    data object Idle : TutorStatus
    data object Loading : TutorStatus
    data object Streaming : TutorStatus
    data class Error(val message: String) : TutorStatus
}

data class MetrologyTutorUiState(
    val selectedTopic: TutorFocusTopic = TutorFocusTopic.ALL,
    val messages: List<TutorChatMessage> = emptyList(),
    val status: TutorStatus = TutorStatus.Idle,
    val lastFailedPrompt: String? = null
)

class MetrologyTutorViewModel(
    initialTopic: TutorFocusTopic = TutorFocusTopic.ALL
) : ViewModel() {

    private val systemPromptText = """
        Kamu adalah "Instruktur AI MEASURE LAB", asisten pendidik metrologi dan alat ukur presisi yang ramah, akurat, dan terstruktur untuk siswa dan guru SMK Kelas X Teknik Pemesinan.

        Tugas utamamu:
        1. Mengajarkan pengetahuan penggunaan 4 alat ukur presisi utama di MEASURE LAB:
           - Jangka Sorong Manual (0–150 mm, ketelitian 0,05 mm dan 0,02 mm): komponen, pengukuran luar, dalam, kedalaman, tingkat (step), cara baca skala utama & nonius, penguncian, dan pencegahan paralaks.
           - Jangka Sorong Digital (0,01 mm): tombol ON/OFF, ZERO, HOLD, mm/inch, sensor kapasitif, dan perawatan.
           - Mikrometer Sekrup Manual (0–25 mm, pitch 0,5 mm, 50 garis thimble = 0,01 mm): fungsi frame, anvil, spindle, sleeve, thimble, ratchet stop (2–3 klik), lock lever, dan cara pembacaan lengkap.
           - Mikrometer Sekrup Digital (0,001 mm): layar LCD, tombol ZERO/ABS/mm-in, penggunaan ratchet stop, dan batas penggunaan (hanya diameter luar & ketebalan).
        2. Membimbing tentang SOP pengukuran bengkel, kalibrasi nol (zero error positif & negatif), analisis galat (absolut & relatif), toleransi dimensi & suaian ISO 286, serta perawatan alat ukur.
        3. Gaya jawaban:
           - Gunakan Bahasa Indonesia yang jelas, edukatif, ringkas, dan mudah dipahami siswa SMK.
           - Sertakan langkah bernomor atau contoh perhitungan singkat saat menjelaskan cara baca skala atau rumus.
           - Jika pengguna bertanya di luar topik metrologi, teknik pemesinan, atau alat ukur, arahkan kembali dengan sopan ke topik penggunaan alat ukur presisi.
           - Jangan mengarang spesifikasi alat yang keliru; selalu tekankan keselamatan kerja bengkel dan akurasi pengukuran.
    """.trimIndent()

    // Mandated lazy initialization so Firebase.ai is only evaluated on first inference
    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 1024
            },
            systemInstruction = content {
                text(systemPromptText)
            }
        )
    }

    private var activeChat: Chat? = null

    private val _uiState = MutableStateFlow(
        MetrologyTutorUiState(selectedTopic = initialTopic)
    )
    val uiState: StateFlow<MetrologyTutorUiState> = _uiState.asStateFlow()

    fun selectTopic(topic: TutorFocusTopic) {
        _uiState.update { it.copy(selectedTopic = topic) }
    }

    fun clearError() {
        _uiState.update {
            if (it.status is TutorStatus.Error) it.copy(status = TutorStatus.Idle) else it
        }
    }

    fun resetConversation() {
        activeChat = null
        _uiState.update {
            it.copy(
                messages = emptyList(),
                status = TutorStatus.Idle,
                lastFailedPrompt = null
            )
        }
    }

    fun retryLastMessage() {
        val prompt = _uiState.value.lastFailedPrompt ?: return
        sendMessage(prompt, isRetry = true)
    }

    fun sendMessage(rawPrompt: String, isRetry: Boolean = false) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return
        val currentStatus = _uiState.value.status
        if (currentStatus is TutorStatus.Loading || currentStatus is TutorStatus.Streaming) return

        val topic = _uiState.value.selectedTopic
        val assistantPlaceholderId = UUID.randomUUID().toString()

        _uiState.update { state ->
            val lastMessage = state.messages.lastOrNull()
            val shouldReuseLastUserBubble = isRetry &&
                lastMessage?.author == ChatAuthor.USER &&
                lastMessage.text == trimmed

            val updatedMessages = buildList {
                addAll(state.messages)
                if (!shouldReuseLastUserBubble) {
                    add(
                        TutorChatMessage(
                            author = ChatAuthor.USER,
                            text = trimmed,
                            topicLabel = topic.takeIf { it != TutorFocusTopic.ALL }?.shortLabel
                        )
                    )
                }
                add(
                    TutorChatMessage(
                        id = assistantPlaceholderId,
                        author = ChatAuthor.MODEL,
                        text = "",
                        topicLabel = topic.takeIf { it != TutorFocusTopic.ALL }?.shortLabel,
                        isStreaming = true
                    )
                )
            }

            state.copy(
                messages = updatedMessages,
                status = TutorStatus.Loading,
                lastFailedPrompt = null
            )
        }

        val promptForModel = if (topic == TutorFocusTopic.ALL) {
            trimmed
        } else {
            "[Konteks topik yang dipilih pengguna: ${topic.label}]\n$trimmed"
        }

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val chatSession = activeChat ?: generativeModel.startChat().also { activeChat = it }
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    chatSession.sendMessageStream(promptForModel).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            val updatedText = accumulated.toString()
                            _uiState.update { state ->
                                state.copy(
                                    status = TutorStatus.Streaming,
                                    messages = state.messages.map { msg ->
                                        if (msg.id == assistantPlaceholderId) {
                                            msg.copy(text = updatedText, isStreaming = true)
                                        } else {
                                            msg
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isNotBlank()) {
                    _uiState.update { state ->
                        state.copy(
                            status = TutorStatus.Idle,
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantPlaceholderId) {
                                    msg.copy(text = finalText, isStreaming = false)
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                } else {
                    handleInferenceError(
                        placeholderId = assistantPlaceholderId,
                        failedPrompt = trimmed,
                        errorMessage = "Model AI tidak mengembalikan jawaban. Silakan coba lagi."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini chat response timed out after ${INFERENCE_TIMEOUT_MS}ms", e)
                handleInferenceError(
                    placeholderId = assistantPlaceholderId,
                    failedPrompt = trimmed,
                    errorMessage = "Waktu permintaan habis (60 detik). Periksa koneksi internet Anda lalu coba lagi."
                )
            } catch (e: CancellationException) {
                // ALWAYS rethrow CancellationException to preserve cooperative cancellation
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI Logic chat failed", e)
                val rawMessage = e.localizedMessage.orEmpty()
                val detail = when {
                    rawMessage.contains("Default FirebaseApp is not initialized", ignoreCase = true) ->
                        "Konfigurasi Firebase belum terpasang pada APK build lokal ini (file app/google-services.json tidak ditemukan saat build di Android Studio). Salin file app/google-services.json ke folder app/ lalu build ulang APK."
                    rawMessage.contains("403") || rawMessage.contains("AppCheck", ignoreCase = true) ->
                        "Permintaan ditolak oleh Firebase App Check (HTTP 403). Pastikan koneksi internet aktif dan build menggunakan konfigurasi Firebase proyek ini."
                    rawMessage.isNotBlank() -> rawMessage
                    else -> "Gagal terhubung ke layanan Gemini AI. Periksa koneksi internet Anda lalu coba lagi."
                }
                handleInferenceError(
                    placeholderId = assistantPlaceholderId,
                    failedPrompt = trimmed,
                    errorMessage = detail
                )
            }
        }
    }

    private fun handleInferenceError(
        placeholderId: String,
        failedPrompt: String,
        errorMessage: String
    ) {
        _uiState.update { state ->
            val cleanedMessages = state.messages.filterNot {
                it.id == placeholderId && it.text.isBlank()
            }.map {
                if (it.id == placeholderId) it.copy(isStreaming = false) else it
            }
            state.copy(
                messages = cleanedMessages,
                status = TutorStatus.Error(errorMessage),
                lastFailedPrompt = failedPrompt
            )
        }
    }
}
