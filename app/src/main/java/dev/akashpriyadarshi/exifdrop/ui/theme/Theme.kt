package dev.akashpriyadarshi.exifdrop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ExifDrop design system. Locked by the independent design audit (workbench / austere register):
 * warm paper+ink (no pure black), ONE safety-green accent reserved for verified/clean states,
 * blue focus rings for contrast, mono only for trust data (hash / filename / status readout).
 */
object ExifDropColors {
    val Paper = Color(0xFFFBF9F2)
    val Paper2 = Color(0xFFF4F1E8)
    val Ink = Color(0xFF2A2620)
    val Muted = Color(0xFF6E675C)
    val Rule = Color(0xFFE4DECF)
    val Accent = Color(0xFF2E7D4F)   // verified / clean only
    val Error = Color(0xFFB3261E)
    val FocusRing = Color(0xFF005A9C)
    val AccentInk = Color(0xFFFFFFFF)
}

object ExifDropDarkColors {
    val Paper = Color(0xFF12100E)
    val Paper2 = Color(0xFF1C1A16)
    val Ink = Color(0xFFF0EDE5)
    val Muted = Color(0xFFA79F92)
    val Rule = Color(0xFF2A2620)
    val Accent = Color(0xFF2E7D4F)
    val Error = Color(0xFFE5A18F)
    val FocusRing = Color(0xFF66A1DC)
    val AccentInk = Color(0xFF0E0E0C)
}

@Composable
fun ExifDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            background = ExifDropDarkColors.Paper,
            surface = ExifDropDarkColors.Paper,
            surfaceVariant = ExifDropDarkColors.Paper2,
            primary = ExifDropDarkColors.Accent,
            onPrimary = ExifDropDarkColors.AccentInk,
            onBackground = ExifDropDarkColors.Ink,
            onSurface = ExifDropDarkColors.Ink,
            onSurfaceVariant = ExifDropDarkColors.Muted,
            error = ExifDropDarkColors.Error,
            outline = ExifDropDarkColors.Rule,
            outlineVariant = ExifDropDarkColors.Rule,
        )
    } else {
        lightColorScheme(
            background = ExifDropColors.Paper,
            surface = ExifDropColors.Paper,
            surfaceVariant = ExifDropColors.Paper2,
            primary = ExifDropColors.Accent,
            onPrimary = ExifDropColors.AccentInk,
            onBackground = ExifDropColors.Ink,
            onSurface = ExifDropColors.Ink,
            onSurfaceVariant = ExifDropColors.Muted,
            error = ExifDropColors.Error,
            outline = ExifDropColors.Rule,
            outlineVariant = ExifDropColors.Rule,
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(
            // Display: Roboto (system SansSerif) Medium, tight — app title + status wordmark.
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.02).sp,
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
            // Body: Roboto Regular 16, lh 1.5.
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            // Mono slot: hash / filename / status readout only.
            labelSmall = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        ),
        content = content,
    )
}