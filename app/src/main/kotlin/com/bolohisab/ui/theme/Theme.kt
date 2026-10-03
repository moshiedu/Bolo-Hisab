package com.bolohisab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EFDD),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9DC),
    onSecondaryContainer = Color(0xFF082018),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color.White,
    background = Color(0xFFF7F9F8),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF7F9F8),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F2),
    surfaceContainer = Color(0xFFEBEFED),
    surfaceContainerHigh = Color(0xFFE5E9E7),
    surfaceContainerHighest = Color(0xFFDFE4E1),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFC0C9C3),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7ED8B0),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFC9EFDD),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1E352C),
    secondaryContainer = Color(0xFF354B42),
    onSecondaryContainer = Color(0xFFCFE9DC),
    tertiary = Color(0xFFA5CCDD),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFC0C9C3),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF8A938E),
    outlineVariant = Color(0xFF404944),
    error = Color(0xFFFFB4AB),
)

/** Ledger meanings that Material's scheme has no slot for. Never use colour alone: pair with text. */
@Immutable
data class LedgerColors(
    val due: Color,
    val dueContainer: Color,
    val paid: Color,
    val paidContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
)

private val LightLedger = LedgerColors(
    due = Color(0xFFB3261E), dueContainer = Color(0xFFFCE8E6),
    paid = Color(0xFF0B7A43), paidContainer = Color(0xFFDDF4E6),
    attention = Color(0xFF8A5100), attentionContainer = Color(0xFFFFEDC7),
    expense = Color(0xFF4F5391), expenseContainer = Color(0xFFE3E4F9),
)

private val DarkLedger = LedgerColors(
    due = Color(0xFFFFB4AB), dueContainer = Color(0xFF4F1A15),
    paid = Color(0xFF7DDBA3), paidContainer = Color(0xFF0F3A25),
    attention = Color(0xFFFFB951), attentionContainer = Color(0xFF412B00),
    expense = Color(0xFFBFC2FF), expenseContainer = Color(0xFF2B2F5E),
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedger }

// Bangla glyphs carry tall vowel signs above and below, so line heights run looser than Latin defaults.
private val BaseType = Typography()
private val AppTypography = Typography(
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = BaseType.titleSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = BaseType.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = BaseType.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = BaseType.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    labelMedium = BaseType.labelMedium.copy(lineHeight = 18.sp),
    labelSmall = BaseType.labelSmall.copy(lineHeight = 16.sp),
)

/** Money figures: tabular digits so columns of amounts line up. */
val MoneyStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun BoloHisabTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLedgerColors provides if (dark) DarkLedger else LightLedger) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}

object LedgerTheme {
    val colors: LedgerColors @Composable get() = LocalLedgerColors.current
}
