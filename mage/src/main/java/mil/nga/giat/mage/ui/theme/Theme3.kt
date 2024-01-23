package mil.nga.giat.mage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorPalette = lightColorScheme(
  primary = Blue600,
  secondary = OrangeA700,
  tertiary = Blue800,
  error = Red800,
  primaryContainer = OrangeA700,
  surfaceVariant = Color(red = 245, green = 242, blue = 245)
)

private val DarkColorPalette = darkColorScheme(
  primary = Grey800,
  secondary = BlueA200,
  tertiary = Grey800,
  error = Red300,
  onPrimary = Color.White
)

@Composable
fun MageTheme3(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) {
    DarkColorPalette
  } else {
    LightColorPalette
  }
  MaterialTheme(
    colorScheme = colorScheme,
    content = content
  )
}

val ColorScheme.onSurfaceDisabled: Color @Composable
  get() = onSurface.copy(alpha = 0.40f)

val ColorScheme.linkColor: Color @Composable get() {
  return if (isSystemInDarkTheme()) androidx.compose.material.MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium) else primary
}

val ColorScheme.warning: Color @Composable get() = Amber700
val ColorScheme.importantBackground: Color @Composable get() = OrangeA400
