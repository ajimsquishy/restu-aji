package com.example.ui.feature.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.catalog.LearningAndQuizCatalog
import com.example.domain.model.InstrumentType
import com.example.ui.components.MeasureLabTopBar
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabOutline
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.TealSecondary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToleranceGreen

@Composable
fun LearningHubScreen(
    onBack: (() -> Unit)? = null,
    onOpenTopic: (String) -> Unit,
    onOpenAiTutor: (String?) -> Unit = {}
) {
    val topics = LearningAndQuizCatalog.lessonTopics

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = "Materi Pembelajaran",
                subtitle = "8 Modul Kurikulum SMK Kelas X Teknik Pemesinan",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("learning_hub_screen"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyPrimary)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tujuan Pembelajaran Umum", style = MaterialTheme.typography.titleMedium, color = CyanHighlight)
                        Text(
                            "Memahami komponen, prinsip kerja, prosedur pengukuran, pembacaan skala, kalibrasi, analisis galat, dan toleransi alat ukur presisi sesuai standar pemesinan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAiTutor(null) }
                        .testTag("learning_open_ai_tutor_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, OrangeAccent.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(OrangeContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Tanya Asisten AI Alat Ukur",
                                tint = OrangeAccent
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tanya Asisten AI Alat Ukur",
                                style = MaterialTheme.typography.titleMedium,
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Diskusi interaktif cara pakai, baca skala & tips penggunaan alat ukur",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Buka Asisten AI",
                            tint = OrangeAccent
                        )
                    }
                }
            }
            items(topics, key = { it.id }) { topic ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTopic(topic.id) }
                        .testTag("lesson_card_${topic.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(TealSecondary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = topic.title, tint = TealSecondary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = OrangeContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = topic.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NavyDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(topic.title, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                            Text(topic.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Buka Materi", tint = TealSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun LessonDetailScreen(
    topicId: String,
    onBack: () -> Unit,
    onLaunchSimulator: (InstrumentType) -> Unit,
    onOpenAiTutor: (String?) -> Unit = {}
) {
    val topic = LearningAndQuizCatalog.lessonTopics.firstOrNull { it.id == topicId }
        ?: LearningAndQuizCatalog.lessonTopics.first()

    Scaffold(
        containerColor = LabBackground,
        topBar = {
            MeasureLabTopBar(
                title = topic.category,
                subtitle = topic.title,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("lesson_detail_screen"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(topic.title, style = MaterialTheme.typography.headlineSmall, color = NavyPrimary)
                        Text("Tujuan Pembelajaran:", style = MaterialTheme.typography.titleSmall, color = TealSecondary)
                        topic.objectives.forEach { obj ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ToleranceGreen,
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(obj, style = MaterialTheme.typography.bodyMedium, color = NavyDark)
                            }
                        }
                    }
                }
            }

            items(topic.sections) { section ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LabOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.heading, style = MaterialTheme.typography.titleMedium, color = NavyPrimary)
                        Text(section.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        if (!section.formulaHighlight.isNullOrBlank()) {
                            Surface(
                                color = OrangeContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, OrangeAccent)
                            ) {
                                Text(
                                    text = section.formulaHighlight,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NavyDark,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                )
                            }
                        }
                        section.keyPoints.forEach { pt ->
                            Text("• $pt", style = MaterialTheme.typography.bodySmall, color = NavyDark)
                        }
                    }
                }
            }

            topic.relatedInstrument?.let { instrument ->
                item {
                    Button(
                        onClick = { onLaunchSimulator(instrument) },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("lesson_try_simulator_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Praktikkan")
                        Spacer(Modifier.width(6.dp))
                        Text("Praktikkan di Simulator ${instrument.displayName}")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { onOpenAiTutor(topic.relatedInstrument?.name) },
                    border = BorderStroke(1.dp, NavyPrimary),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("lesson_ask_ai_button")
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Tanya AI tentang Topik Ini",
                        tint = OrangeAccent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Tanya Asisten AI tentang Topik Ini", color = NavyPrimary)
                }
            }
        }
    }
}
