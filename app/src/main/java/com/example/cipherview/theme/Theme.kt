package com.example.cipherview.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultDarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Color(0xFF001F24),
    primaryContainer = CyanDark,
    onPrimaryContainer = CyanGlow,

    secondary = EmeraldSecurity,
    onSecondary = Color(0xFF00210B),
    secondaryContainer = EmeraldDark,
    onSecondaryContainer = EmeraldSecurity,

    tertiary = SecurityAmber,
    onTertiary = Color(0xFF261900),

    error = SecurityRed,
    onError = Color(0xFF330000),

    background = VaultBackground,
    onBackground = TextPrimary,

    surface = VaultSurface,
    onSurface = TextPrimary,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = CardBorder
)

private val VaultLightColorScheme = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF00363A),

    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF00332C),

    tertiary = Color(0xFFF57F17),
    onTertiary = Color.White,

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),

    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),

    outline = Color(0xFFCBD5E1)
)

@Composable
fun CipherViewTheme(
    darkTheme: Boolean = true, // Default to high-security dark theme for CipherView vault aesthetic
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
