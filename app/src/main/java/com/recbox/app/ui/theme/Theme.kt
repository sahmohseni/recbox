package com.recbox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.recbox.app.R

private val RecboxColors = darkColorScheme(
    primary = Color(0xFFA98BFF),
    onPrimary = Color(0xFF171321),
    secondary = Color(0xFF8DE8BD),
    background = Color(0xFF0D0E15),
    surface = Color(0xFF151722),
    surfaceVariant = Color(0xFF1C1E2B),
    onBackground = Color(0xFFF6F3FB),
    onSurface = Color(0xFFF6F3FB),
    onSurfaceVariant = Color(0xFF9495A7),
)

private val IranSansFamily = FontFamily(
    Font(R.font.iran_sans_fanum_light, FontWeight.Light),
    Font(R.font.iran_sans_fanum_regular, FontWeight.Normal),
    Font(R.font.iran_sans_fanum_bold, FontWeight.Bold),
)

private val defaultTypography = Typography()
private val RecboxTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = IranSansFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = IranSansFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = IranSansFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = IranSansFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = IranSansFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = IranSansFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = IranSansFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = IranSansFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = IranSansFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = IranSansFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = IranSansFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = IranSansFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = IranSansFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = IranSansFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = IranSansFamily)
)

@Composable
fun RecboxTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = RecboxColors,
            typography = RecboxTypography,
            content = content,
        )
    }
}
