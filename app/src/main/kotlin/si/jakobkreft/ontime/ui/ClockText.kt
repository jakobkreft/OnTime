package si.jakobkreft.ontime.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import si.jakobkreft.ontime.domain.DurationText

/**
 * One line of clock text that can cover its seconds and leave whole minutes: `7:21` becomes `7:––`.
 *
 * The seconds are never taken out of the text, only made transparent, so the line keeps its exact
 * width and the minutes do not move. Two quiet dashes stand in for the digits and the separator
 * dims to match: the minutes are what stands out, and the line still reads as a clock that says
 * "seven and some", which a bare `7` would not.
 *
 * [duration] is the [DurationText.format] string inside [text] whose seconds are covered. For the
 * main clock the two are the same; the overtime line carries its `+` as well.
 */
@Composable
fun ClockText(
    text: String,
    duration: String,
    secondsHidden: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Covering is slow, so it reads as the clock settling rather than as something breaking.
    // Uncovering is quick, because whoever pressed pause wants to read the time now.
    val covered by animateFloatAsState(
        targetValue = if (secondsHidden) 1f else 0f,
        animationSpec = if (secondsHidden) {
            tween(COVER_MILLIS, easing = LinearEasing)
        } else {
            tween(UNCOVER_MILLIS, easing = FastOutSlowInEasing)
        },
        label = "secondsCovered",
    )
    val ink = style.color.takeOrElse { LocalContentColor.current }
    val field = remember(text, duration) {
        val start = text.lastIndexOf(duration)
        val seconds = DurationText.secondsField(duration)
        if (start < 0) null else seconds.first + start..seconds.last + start
    }

    // The digits are gone before the dashes arrive, so the two never sit on top of each other.
    val digitAlpha = (1f - covered * 2f).coerceIn(0f, 1f)
    val dashAlpha = (covered * 2f - 1f).coerceIn(0f, 1f) * COVERED_ALPHA
    val separatorAlpha = 1f - (1f - COVERED_ALPHA) * covered

    val annotated = if (field == null || covered == 0f) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(color = ink.faded(separatorAlpha)), field.first, field.first + 1)
            addStyle(SpanStyle(color = ink.faded(digitAlpha)), field.first + 1, field.last + 1)
        }
    }

    val dash = rememberDashGlyph(style)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = style,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val line = layout
            if (field == null || line == null || dashAlpha == 0f) return@drawWithContent
            val color = ink.faded(dashAlpha)
            val middle = line.firstBaseline + dash.digitMiddle
            for (index in field.first + 1..field.last) {
                val slot = line.getBoundingBox(index)
                translate(slot.center.x - dash.bounds.center.x, middle - dash.bounds.center.y) {
                    drawPath(dash.path, color)
                }
            }
        },
    )
}

/** This colour at a fraction of its own opacity, so a transparent line stays transparent. */
private fun Color.faded(fraction: Float) = copy(alpha = alpha * fraction)

/**
 * The outline of the font's own hyphen, and where the middle of a digit sits relative to the
 * baseline. Drawing the real glyph, raised to the middle of the digits, keeps each dash in the
 * weight of the clock beside it.
 */
private class DashGlyph(val path: Path, val bounds: Rect, val digitMiddle: Float)

/**
 * Compose reports where characters are, not the shapes of their glyphs, so the outlines come from
 * the platform paint, set up with the same typeface and size the text is drawn with.
 */
@Composable
private fun rememberDashGlyph(style: TextStyle): DashGlyph {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    return remember(style, fontFamilyResolver, density) {
        val paint = android.graphics.Paint().apply {
            typeface = fontFamilyResolver.resolveAsTypeface(
                fontFamily = style.fontFamily,
                fontWeight = style.fontWeight ?: FontWeight.Normal,
            ).value
            textSize = with(density) { style.fontSize.toPx() }
            fontFeatureSettings = style.fontFeatureSettings
        }
        val path = android.graphics.Path()
            .also { paint.getTextPath("-", 0, 1, 0f, 0f, it) }
            .asComposePath()
        val digit = android.graphics.Rect().also { paint.getTextBounds("0", 0, 1, it) }
        DashGlyph(path, path.getBounds(), digit.exactCenterY())
    }
}

/** How strongly the covered seconds still show: enough to keep the shape of the clock, no more. */
private const val COVERED_ALPHA = 0.4f
private const val COVER_MILLIS = 800
private const val UNCOVER_MILLIS = 250
