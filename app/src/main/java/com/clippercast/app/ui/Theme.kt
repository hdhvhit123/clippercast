package com.clippercast.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFFE4572E)
private val Ink = Color(0xFF1F2A44)

private val Light = lightColorScheme(
    primary = Color(0xFFB8401C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Ink, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE2F3), onSecondaryContainer = Ink,
    background = Color(0xFFFBF7F2), onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFBF7F2), onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFEFE7DF), onSurfaceVariant = Color(0xFF52463F),
    surfaceContainer = Color(0xFFF3ECE5), surfaceContainerHigh = Color(0xFFEDE5DD),
    outline = Color(0xFF85746B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB59C), onPrimary = Color(0xFF5E1700),
    primaryContainer = Color(0xFF842A0A), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283041),
    background = Color(0xFF141312), onBackground = Color(0xFFE8E1DC),
    surface = Color(0xFF141312), onSurface = Color(0xFFE8E1DC),
    surfaceVariant = Color(0xFF52463F), onSurfaceVariant = Color(0xFFD7C2B9),
    surfaceContainer = Color(0xFF211F1D), surfaceContainerHigh = Color(0xFF2B2927),
    outline = Color(0xFFA08D84),
)

@Composable
fun ClipperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@Suppress("unused") private val unused = Brand
