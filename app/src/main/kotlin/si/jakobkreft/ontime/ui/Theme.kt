package si.jakobkreft.ontime.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import si.jakobkreft.ontime.domain.Phase

/**
 * The three phase colours. Everything else in the app is built around them, so they are the only
 * colours defined by hand; keep them in sync with `values/colors.xml`.
 */
object PhaseColors {
    val Green = Color(0xFF47863E)
    val Yellow = Color(0xFFD7A614)
    val Red = Color(0xFFB31212)
}

val Phase.color: Color
    get() = when (this) {
        Phase.GREEN -> PhaseColors.Green
        Phase.YELLOW -> PhaseColors.Yellow
        Phase.RED -> PhaseColors.Red
    }

/**
 * A single dark scheme rather than a day/night pair. The run screen is a saturated colour field at
 * all times, and a white timer list is the wrong thing to hand someone standing at a lectern.
 */
private val OnTimeColors = darkColorScheme(
    primary = Color(0xFF8FD37F),
    onPrimary = Color(0xFF0B1A08),
    primaryContainer = Color(0xFF2E5A28),
    onPrimaryContainer = Color(0xFFD3F2CB),
    secondary = Color(0xFFBCCBB6),
    onSecondary = Color(0xFF26331F),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE4E9DF),
    surface = Color(0xFF11140F),
    onSurface = Color(0xFFE4E9DF),
    surfaceVariant = Color(0xFF20261D),
    onSurfaceVariant = Color(0xFFB6BFAF),
    surfaceContainer = Color(0xFF1A1F17),
    surfaceContainerHigh = Color(0xFF242A20),
    outline = Color(0xFF6C7566),
    outlineVariant = Color(0xFF3A4235),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun OnTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OnTimeColors, content = content)
}
