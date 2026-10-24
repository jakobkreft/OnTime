package si.jakobkreft.ontime.ui

import androidx.compose.foundation.layout.BoxWithConstraints
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
 * Finds the largest font size at which [measureText] fits, and hands [content] the [style] at that
 * size to draw the live text with.
 *
 * Sizing against a fixed reference string rather than the live one is the point: a countdown that
 * sized itself would jump every time a digit dropped, which is exactly the moment a speaker is
 * looking at it.
 */
@Composable
fun AutoSizeText(
    measureText: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 24.sp,
    maxFontSize: TextUnit = 240.sp,
    content: @Composable (fitted: TextStyle) -> Unit,
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

        content(style.copy(fontSize = fontSize))
    }
}

private const val BISECTION_STEPS = 12
