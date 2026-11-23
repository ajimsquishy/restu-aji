package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MeasureLabColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = NavyDark,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealDark,
    tertiary = OrangeAccent,
    onTertiary = Color.White,
    tertiaryContainer = OrangeContainer,
    onTertiaryContainer = NavyDark,
    background = LabBackground,
    onBackground = TextPrimary,
    surface = LabSurface,
    onSurface = TextPrimary,
    surfaceVariant = LabSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = LabOutline,
    error = ToleranceRed,
    onError = Color.White,
    errorContainer = ToleranceRedBg,
    onErrorContainer = ToleranceRed
)

val MeasureLabShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MeasureLabTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MeasureLabColorScheme,
        typography = MeasureLabTypography,
        shapes = MeasureLabShapes,
        content = content
    )
}
