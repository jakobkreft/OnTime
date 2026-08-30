package si.jakobkreft.ontime.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Draws [text] as large as it will fit, sized so that [measureText] would also fit.
 *
 * Sizing against a fixed reference string rather than the live one is the point: a countdown that
 * sized itself would jump every time a digit dropped, which is exactly the moment a speaker is
 * looking at it.
 */
@Composable
fun AutoSizeText(
    text: String,
    measureText: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 24.sp,
    maxFontSize: TextUnit = 240.sp,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val availableWidth = with(density) { maxWidth.toPx() }
        val availableHeight = with(density) { maxHeight.toPx() }

        val fontSize = remember(measureText, availableWidth, availableHeight, style, density) {
            var smallest = minFontSize.value
            var largest = maxFontSize.value
            repeat(BISECTION_STEPS) {
                val candidate = (smallest + largest) / 2f
                val layout = measurer.measure(
                    text = AnnotatedString(measureText),
                    style = style.copy(fontSize = candidate.sp),
                    maxLines = 1,
                    softWrap = false,
                )
                if (layout.size.width <= availableWidth && layout.size.height <= availableHeight) {
                    smallest = candidate
                } else {
                    largest = candidate
                }
            }
            smallest.sp
        }

        Text(
            text = text,
            style = style.copy(fontSize = fontSize),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val BISECTION_STEPS = 12
