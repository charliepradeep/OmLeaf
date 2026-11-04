package com.charrlie.omleaf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Calm teal and warm saffron on a soft paper background; deep green-black at night. */
private val Teal = Color(0xFF1F7A74)
private val TealLight = Color(0xFF63CFC4)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EFEC),
    onPrimaryContainer = Color(0xFF0B3330),
    secondary = Color(0xFFD98A2B),
    tertiary = Color(0xFF7A5CC7),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF1B2220),
    surface = Color.White,
    onSurface = Color(0xFF1B2220),
    surfaceVariant = Color(0xFFEAE6DC),
    onSurfaceVariant = Color(0xFF66706C),
    outlineVariant = Color(0xFFDDD8CB),
    error = Color(0xFFC0392B),
)

private val Dark = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF062B28),
    primaryContainer = Color(0xFF14403C),
    onPrimaryContainer = Color(0xFFCFF1EC),
    secondary = Color(0xFFF2B35E),
    tertiary = Color(0xFFB8A4F2),
    background = Color(0xFF0E1412),
    onBackground = Color(0xFFE8EFEC),
    surface = Color(0xFF18201E),
    onSurface = Color(0xFFE8EFEC),
    surfaceVariant = Color(0xFF26302D),
    onSurfaceVariant = Color(0xFF9AA7A2),
    outlineVariant = Color(0xFF33403C),
    error = Color(0xFFFF8A7A),
)

private val base = Typography()
private val AppTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun OmLeafTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
