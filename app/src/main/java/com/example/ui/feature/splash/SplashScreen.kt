package com.example.ui.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.TealSecondary
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.82f) }
    val alpha = remember { Animatable(0f) }
    var navigated by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!navigated) {
            navigated = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        alpha.animateTo(1f, tween(420))
        delay(650)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NavyDark, NavyPrimary, Color(0xFF0A4E6E))
                )
            )
            .clickable { finishOnce() }
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 12.dp,
                color = Color(0xFF0B3855),
                modifier = Modifier
                    .size(112.dp)
                    .scale(scale.value)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_measure_lab_logo),
                    contentDescription = "Logo MEASURE LAB",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "MEASURE LAB",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(alpha.value)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Virtual Machining Measurement Laboratory",
                style = MaterialTheme.typography.titleSmall,
                color = CyanHighlight,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha.value)
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = OrangeAccent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.alpha(alpha.value)
            ) {
                Text(
                    text = "SMK Kelas X • Teknik Pemesinan • 100% Offline",
                    style = MaterialTheme.typography.labelMedium,
                    color = OrangeAccent,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                color = CyanHighlight,
                trackColor = TealSecondary.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(width = 160.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}
