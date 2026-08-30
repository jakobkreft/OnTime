package si.jakobkreft.ontime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import si.jakobkreft.ontime.R

private enum class Screen { RUN, PRESETS, ABOUT }

private val ScreenSaver = Saver<Screen, String>(
    save = { it.name },
    restore = { runCatching { Screen.valueOf(it) }.getOrDefault(Screen.RUN) },
)

@Composable
fun OnTimeApp(viewModel: TimerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf(Screen.RUN) }
    var editingPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    // Set when the editor was opened on a timer that was created for it, so backing out of
    // "New timer" does not leave an untouched timer behind.
    var editingIsNew by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val stopToEditMessage = stringResource(R.string.stop_to_edit)

    BackHandler(enabled = screen != Screen.RUN) { screen = Screen.RUN }

    val background = when (screen) {
        Screen.RUN -> state.snapshot.phase.color
        else -> MaterialTheme.colorScheme.background
    }

    Box(Modifier.fillMaxSize().background(background)) {
        when (screen) {
            Screen.RUN -> RunScreen(
                state = state,
                onOpenPresets = { screen = Screen.PRESETS },
                onOpenAbout = { screen = Screen.ABOUT },
                onEditTimes = {
                    if (state.run.isActive) {
                        scope.launch { snackbarHostState.showSnackbar(stopToEditMessage) }
                    } else {
                        editingPresetId = state.selected.id
                        editingIsNew = false
                    }
                },
                onToggle = viewModel::toggle,
                onStop = viewModel::stop,
                modifier = Modifier.safeDrawingPadding(),
            )

            Screen.PRESETS -> PresetsScreen(
                presets = state.presets,
                selectedId = state.selected.id,
                activeRuns = state.activeRuns,
                snackbarHostState = snackbarHostState,
                onBack = { screen = Screen.RUN },
                onSelect = { preset ->
                    viewModel.select(preset.id)
                    screen = Screen.RUN
                },
                onAdd = {
                    editingPresetId = viewModel.addPreset().id
                    editingIsNew = true
                },
                onEdit = {
                    editingPresetId = it.id
                    editingIsNew = false
                },
                onDuplicate = { viewModel.duplicate(it.id) },
                onDelete = { viewModel.deletePreset(it.id) },
                onUndoDelete = viewModel::undoDelete,
            )

            Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.RUN })
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
        )
    }

    val editing = editingPresetId?.let { id -> state.presets.firstOrNull { it.id == id } }
    if (editing != null) {
        PresetEditorDialog(
            preset = editing,
            isActive = state.activeRuns.containsKey(editing.id),
            onDismiss = {
                if (editingIsNew) viewModel.deletePreset(editing.id)
                editingPresetId = null
                editingIsNew = false
            },
            onConfirm = { updated ->
                viewModel.savePreset(updated)
                editingPresetId = null
                editingIsNew = false
            },
        )
    }
}
