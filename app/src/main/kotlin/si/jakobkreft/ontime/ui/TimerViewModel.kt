package si.jakobkreft.ontime.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import si.jakobkreft.ontime.R
import si.jakobkreft.ontime.data.AppData
import si.jakobkreft.ontime.data.Preset
import si.jakobkreft.ontime.data.PresetRepository
import si.jakobkreft.ontime.data.RunState
import si.jakobkreft.ontime.data.RunStatus
import si.jakobkreft.ontime.domain.TimerSnapshot
import java.util.UUID

/** Everything the screens draw. */
data class UiState(
    val presets: List<Preset>,
    val selected: Preset,
    val run: RunState,
    val snapshot: TimerSnapshot,
    val activeRuns: Map<String, RunStatus>,
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PresetRepository(application)
    private var lastDeleted: DeletedPreset? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = repository.data
        .flatMapLatest { data -> ticks(data).map { now -> data.toUiState(now) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = repository.data.value.toUiState(SystemClock.elapsedRealtime()),
        )

    // --- Timer controls -------------------------------------------------------------------

    fun toggle() {
        val data = repository.data.value
        when (data.runOf(data.selectedId).status) {
            RunStatus.RUNNING -> pause()
            RunStatus.IDLE, RunStatus.PAUSED -> start()
        }
    }

    fun start() = updateRun { run ->
        RunState(
            status = RunStatus.RUNNING,
            startedAtRealtimeMillis = SystemClock.elapsedRealtime(),
            startedAtWallMillis = System.currentTimeMillis(),
            accumulatedMillis = run.accumulatedMillis,
        )
    }

    fun pause() = updateRun { run ->
        if (run.status != RunStatus.RUNNING) {
            run
        } else {
            RunState(
                status = RunStatus.PAUSED,
                accumulatedMillis = run.elapsedMillis(SystemClock.elapsedRealtime()),
            )
        }
    }

    fun stop() = updateRun { RunState() }

    // --- Presets --------------------------------------------------------------------------

    fun select(presetId: String) = repository.update { it.copy(selectedId = presetId) }

    /**
     * Creates a preset seeded from the factory defaults and returns it so the caller can edit it.
     * The selection is left alone: adding a timer should not silently switch the one on screen.
     */
    fun addPreset(): Preset {
        val created = Preset(
            id = UUID.randomUUID().toString(),
            name = repository.nextDefaultName(repository.data.value.presets),
            totalMillis = Preset.DEFAULT_TOTAL_MILLIS,
            yellowMillis = Preset.DEFAULT_YELLOW_MILLIS,
            redMillis = Preset.DEFAULT_RED_MILLIS,
        )
        repository.update { data -> data.copy(presets = data.presets + created) }
        return created
    }

    fun duplicate(presetId: String) = repository.update { data ->
        val source = data.presets.firstOrNull { it.id == presetId } ?: return@update data
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = getApplication<Application>().getString(R.string.copy_of, source.name),
        )
        data.copy(
            presets = data.presets.toMutableList().apply { add(data.presets.indexOf(source) + 1, copy) },
        )
    }

    /**
     * Saves an edited preset. Changing the durations of a timer that is running would leave the
     * run measured against numbers it never started with, so that run is cleared instead.
     */
    fun savePreset(preset: Preset) = repository.update { data ->
        val previous = data.presets.firstOrNull { it.id == preset.id }
        val timingChanged = previous != null && (
            previous.totalMillis != preset.totalMillis ||
                previous.yellowMillis != preset.yellowMillis ||
                previous.redMillis != preset.redMillis
            )
        data.copy(
            presets = data.presets.map { if (it.id == preset.id) preset else it },
            runs = if (timingChanged) data.runs - preset.id else data.runs,
        )
    }

    /**
     * Removes a preset, remembering enough to put it back. Returns false when this is the last
     * preset: the app always keeps one, so there is never an empty run screen.
     */
    fun deletePreset(presetId: String): Boolean {
        val data = repository.data.value
        if (data.presets.size <= 1) return false
        val index = data.presets.indexOfFirst { it.id == presetId }
        if (index < 0) return false
        lastDeleted = DeletedPreset(data.presets[index], index, data.runOf(presetId))
        repository.update {
            it.copy(
                presets = it.presets.filterNot { preset -> preset.id == presetId },
                runs = it.runs - presetId,
            )
        }
        return true
    }

    /** Puts the most recently deleted preset back where it was, still running if it was. */
    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        repository.update { data ->
            val presets = data.presets.toMutableList()
            presets.add(deleted.index.coerceIn(0, presets.size), deleted.preset)
            data.copy(
                presets = presets,
                runs = if (deleted.run.isActive) {
                    data.runs + (deleted.preset.id to deleted.run)
                } else {
                    data.runs
                },
            )
        }
    }

    // --- Internals ------------------------------------------------------------------------

    private fun updateRun(transform: (RunState) -> RunState) = repository.update { data ->
        data.copy(runs = data.runs + (data.selectedId to transform(data.runOf(data.selectedId))))
    }

    private fun AppData.toUiState(nowRealtimeMillis: Long): UiState {
        val preset = selected
        val run = runOf(preset.id)
        return UiState(
            presets = presets,
            selected = preset,
            run = run,
            snapshot = TimerSnapshot.of(preset, run.elapsedMillis(nowRealtimeMillis)),
            activeRuns = runs.mapValues { (_, state) -> state.status },
        )
    }

    /**
     * One emission per displayed second while the selected timer runs, and a single emission
     * otherwise. Each delay is measured to the next whole second *of the timer*, so the display
     * flips exactly on the second and never drifts, however late a tick is delivered.
     */
    private fun ticks(data: AppData): Flow<Long> = flow {
        val run = data.runOf(data.selectedId)
        while (true) {
            val now = SystemClock.elapsedRealtime()
            emit(now)
            if (run.status != RunStatus.RUNNING) return@flow
            val intoSecond = run.elapsedMillis(now).mod(1_000L)
            delay(1_000L - intoSecond)
        }
    }

    private data class DeletedPreset(val preset: Preset, val index: Int, val run: RunState)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
