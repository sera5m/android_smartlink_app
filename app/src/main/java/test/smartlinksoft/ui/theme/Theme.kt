package test.smartlinksoft.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HarnessScheme = darkColorScheme(
    primary = Title,
    onPrimary = B0,
    primaryContainer = B2,
    onPrimaryContainer = Title,
    secondary = IridA,
    onSecondary = B0,
    secondaryContainer = B2,
    tertiary = IridB,
    background = B0,
    onBackground = Tx,
    surface = B1,
    onSurface = Tx,
    surfaceVariant = B2,
    onSurfaceVariant = Tm,
    outline = B3,
    outlineVariant = B2,
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HarnessScheme,
        typography = Typography,
        content = content,
    )
}
