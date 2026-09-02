package si.jakobkreft.ontime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.jakobkreft.ontime.R
import si.jakobkreft.ontime.data.Preset
import si.jakobkreft.ontime.data.RunStatus
import si.jakobkreft.ontime.domain.DurationText
import si.jakobkreft.ontime.domain.Phase

private val OnColor = Color.White
private val OnColorMuted = Color.White.copy(alpha = 0.72f)
private val Well = Color.White.copy(alpha = 0.14f)

/**
 * The home screen. Everything is laid out to fit the window — there is no scrolling here, because
 * a presenter glancing at the screen should never have to wonder whether something is below the fold.
 */
@Composable
fun RunScreen(
    state: UiState,
    onOpenPresets: () -> Unit,
    onOpenAbout: () -> Unit,
    onEditTimes: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()

    CompositionLocalProvider(LocalContentColor provides OnColor) {
        BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            // Targeting API 37 means the window can be any size on a large screen, so the fixed
            // chrome shrinks whenever height is short — not only when the device is landscape.
            val shortWindow = maxHeight < 520.dp
            val sideBySide = shortWindow && maxWidth > maxHeight

            Column(Modifier.fillMaxSize()) {
                Header(
                    presetName = state.selected.name,
                    onOpenPresets = onOpenPresets,
                    onOpenAbout = onOpenAbout,
                )

                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(0.85f).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            ThresholdList(state.selected, onEditTimes)
                        }
                        Column(Modifier.weight(1.45f).fillMaxHeight()) {
                            Clock(state, Modifier.weight(1f))
                            ProgressTrack(state.snapshot.progress)
                            Controls(state, onToggle, onStop, compact = shortWindow)
                        }
                    }
                } else {
                    if (!shortWindow) Spacer(Modifier.height(8.dp))
                    ThresholdRow(state.selected, onEditTimes)
                    Clock(state, Modifier.weight(1f))
                    ProgressTrack(state.snapshot.progress)
                    Controls(state, onToggle, onStop, compact = shortWindow)
                }
            }
        }
    }
}

@Composable
private fun Header(
    presetName: String,
    onOpenPresets: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onOpenPresets)
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = presetName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = stringResource(R.string.choose_timer),
            )
        }
        IconButton(onClick = onOpenAbout) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.about),
            )
        }
    }
}

@Composable
private fun ThresholdRow(preset: Preset, onEditTimes: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        thresholds(preset).forEach { (labelRes, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Well)
                    .clickable(onClick = onEditTimes)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnColorMuted,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ThresholdList(preset: Preset, onEditTimes: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Well)
            .clickable(onClick = onEditTimes)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        thresholds(preset).forEach { (labelRes, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = OnColorMuted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun thresholds(preset: Preset): List<Pair<Int, String>> = listOf(
    R.string.total_time to DurationText.format(preset.totalMillis),
    R.string.yellow_warning to DurationText.format(preset.yellowMillis),
    R.string.red_warning to DurationText.format(preset.redMillis),
)

@Composable
private fun Clock(state: UiState, modifier: Modifier = Modifier) {
    val snapshot = state.snapshot
    val display = DurationText.format(snapshot.remainingMillis)
    val overtime = DurationText.format(snapshot.overtimeMillis)
    val phaseLabel = stringResource(
        when {
            snapshot.isOvertime -> R.string.phase_overtime
            snapshot.phase == Phase.RED -> R.string.phase_red
            snapshot.phase == Phase.YELLOW -> R.string.phase_yellow
            else -> R.string.phase_green
        },
    )
    val spoken = if (snapshot.isOvertime) {
        stringResource(R.string.time_over_by, overtime)
    } else {
        stringResource(
            R.string.time_remaining_of,
            display,
            DurationText.format(state.selected.totalMillis),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = phaseLabel,
            style = MaterialTheme.typography.labelLarge,
            color = OnColorMuted,
        )

        AutoSizeText(
            text = display,
            measureText = DurationText.widestForCountdown(state.selected.totalMillis),
            // Built from scratch rather than from a Material style so the line height follows the
            // font size; a fixed line height would make the fitting arithmetic meaningless.
            style = ClockStyle,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(vertical = 4.dp),
        )

        // The overtime line keeps its space at all times so nothing shifts the moment it appears.
        Text(
            text = stringResource(R.string.overtime_value, overtime),
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (snapshot.isOvertime) OnColor else Color.Transparent,
            maxLines = 1,
        )
    }
}

private val ClockStyle = TextStyle(
    color = OnColor,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = "tnum",
)

@Composable
private fun ProgressTrack(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
    ) {
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(OnColor),
            )
        }
    }
}

@Composable
private fun Controls(
    state: UiState,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    compact: Boolean,
) {
    val running = state.run.status == RunStatus.RUNNING
    val phaseColor = state.snapshot.phase.color

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 10.dp else 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(if (compact) 72.dp else 84.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = OnColor,
                contentColor = phaseColor,
            ),
        ) {
            Icon(
                painter = painterResource(if (running) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(
                    when {
                        running -> R.string.pause
                        state.run.status == RunStatus.PAUSED -> R.string.resume
                        else -> R.string.start
                    },
                ),
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.width(28.dp))

        OutlinedIconButton(
            onClick = onStop,
            enabled = state.run.isActive,
            modifier = Modifier.size(if (compact) 56.dp else 64.dp),
            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = OnColor),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stop),
                contentDescription = stringResource(R.string.stop),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
