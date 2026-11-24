package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemePreference

private val WinkDarkColorScheme = darkColorScheme(
  primary = WinkBlueDark,
  onPrimary = Color(0xFF00243A),
  primaryContainer = WinkBlueContainerDark,
  onPrimaryContainer = WinkOnBlueContainerDark,
  secondary = WinkOrangeDark,
  onSecondary = Color(0xFF3A1600),
  secondaryContainer = WinkOrangeContainerDark,
  onSecondaryContainer = WinkOnOrangeContainerDark,
  tertiary = StatusOnlineGreen,
  onTertiary = Color(0xFF00291B),
  background = WinkSurfaceDark,
  onBackground = WinkOnSurfaceDark,
  surface = WinkCardDark,
  onSurface = WinkOnSurfaceDark,
  surfaceVariant = WinkCardVariantDark,
  onSurfaceVariant = WinkOnSurfaceVariantDark,
  outline = WinkOutlineDark,
  error = StatusDangerRed,
  onError = Color.White,
)

private val WinkLightColorScheme = lightColorScheme(
  primary = WinkBlueLight,
  onPrimary = Color.White,
  primaryContainer = WinkBlueContainerLight,
  onPrimaryContainer = WinkOnBlueContainerLight,
  secondary = WinkOrangeLight,
  onSecondary = Color.White,
  secondaryContainer = WinkOrangeContainerLight,
  onSecondaryContainer = WinkOnOrangeContainerLight,
  tertiary = StatusOnlineGreen,
  onTertiary = Color.White,
  background = WinkSurfaceLight,
  onBackground = WinkOnSurfaceLight,
  surface = WinkCardLight,
  onSurface = WinkOnSurfaceLight,
  surfaceVariant = WinkCardVariantLight,
  onSurfaceVariant = WinkOnSurfaceVariantLight,
  outline = WinkOutlineLight,
  error = StatusDangerRed,
  onError = Color.White,
)

private val WinkShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WinkRemoteAccessTheme(
  themePreference: ThemePreference = ThemePreference.SYSTEM,
  content: @Composable () -> Unit,
) {
  val isDark = when (themePreference) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
  }

  MaterialTheme(
    colorScheme = if (isDark) WinkDarkColorScheme else WinkLightColorScheme,
    typography = WinkTypography,
    shapes = WinkShapes,
    content = content,
  )
}
