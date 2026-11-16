package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.local.entities.AppThemeMode

data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = SuccessGreen,
        onSuccess = PureWhite,
        successContainer = SuccessGreenBg,
        warning = WarningAmber,
        warningContainer = WarningAmberBg,
        danger = ErrorCrimson,
        dangerContainer = ErrorCrimsonBg
    )
}

private val LightColors = lightColorScheme(
    primary = BrandOrange,
    onPrimary = PureWhite,
    primaryContainer = BrandOrangeTint,
    onPrimaryContainer = BrandOrangeDark,
    secondary = CharcoalInk,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFE4E4E7),
    onSecondaryContainer = CharcoalInk,
    tertiary = SuccessGreen,
    background = SoftNeutralBg,
    onBackground = CharcoalInk,
    surface = PureWhite,
    onSurface = CharcoalInk,
    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = MutedSlate,
    outline = SlateBorder,
    error = ErrorCrimson,
    onError = PureWhite,
    errorContainer = ErrorCrimsonBg
)

private val DarkColors = darkColorScheme(
    primary = BrandOrangeLight,
    onPrimary = CharcoalBg,
    primaryContainer = Color(0xFF431407),
    onPrimaryContainer = BrandOrangeTint,
    secondary = Color(0xFFE4E4E7),
    onSecondary = CharcoalBg,
    secondaryContainer = CharcoalCardAlt,
    onSecondaryContainer = PureWhite,
    tertiary = Color(0xFF4ADE80),
    background = CharcoalBg,
    onBackground = Color(0xFFF4F4F5),
    surface = CharcoalSurface,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = CharcoalCardAlt,
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF2E323B),
    error = Color(0xFFF87171),
    onError = CharcoalBg,
    errorContainer = Color(0xFF450A0A)
)

private val OrangeBrandColors = lightColorScheme(
    primary = BrandOrangeDark,
    onPrimary = PureWhite,
    primaryContainer = BrandOrange,
    onPrimaryContainer = PureWhite,
    secondary = Color(0xFF431407),
    onSecondary = PureWhite,
    secondaryContainer = BrandOrangeTint,
    onSecondaryContainer = Color(0xFF431407),
    tertiary = SuccessGreen,
    background = BrandCreamBg,
    onBackground = Color(0xFF1F130B),
    surface = PureWhite,
    onSurface = Color(0xFF1F130B),
    surfaceVariant = Color(0xFFFED7AA),
    onSurfaceVariant = Color(0xFF7C2D12),
    outline = Color(0xFFFDBA74),
    error = ErrorCrimson,
    onError = PureWhite,
    errorContainer = ErrorCrimsonBg
)

private val HisabPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun HisabPulseTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> LightColors
        AppThemeMode.DARK -> DarkColors
        AppThemeMode.ORANGE -> OrangeBrandColors
    }

    val semantic = if (themeMode == AppThemeMode.DARK) {
        SemanticColors(
            success = Color(0xFF4ADE80),
            onSuccess = CharcoalBg,
            successContainer = Color(0xFF14532D),
            warning = Color(0xFFFBBF24),
            warningContainer = Color(0xFF451A03),
            danger = Color(0xFFF87171),
            dangerContainer = Color(0xFF450A0A)
        )
    } else {
        SemanticColors(
            success = SuccessGreen,
            onSuccess = PureWhite,
            successContainer = SuccessGreenBg,
            warning = WarningAmber,
            warningContainer = WarningAmberBg,
            danger = ErrorCrimson,
            dangerContainer = ErrorCrimsonBg
        )
    }

    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HisabPulseTypography,
            shapes = HisabPulseShapes,
            content = content
        )
    }
}
