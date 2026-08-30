package si.jakobkreft.ontime.data

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import si.jakobkreft.ontime.R
import si.jakobkreft.ontime.domain.DurationText
import java.util.UUID

/**
 * The single source of truth, backed by shared preferences holding one JSON document.
 *
 * Reads are synchronous so the first frame already has real data — the app is meant to be usable
 * the instant it opens, and a loading state on the run screen would be a regression.
 */
class PresetRepository(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    /** Applies [transform], then persists. Every mutation in the app goes through here. */
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value).normalised()
        _data.value = next
        preferences.edit { putString(KEY_DATA, json.encodeToString(next)) }
    }

    /** A name that does not collide with the presets already saved. */
    fun nextDefaultName(existing: List<Preset>): String {
        val taken = existing.map { it.name }.toSet()
        var index = existing.size + 1
        while (appContext.getString(R.string.default_preset_name, index) in taken) index++
        return appContext.getString(R.string.default_preset_name, index)
    }

    private fun load(): AppData {
        val stored = preferences.getString(KEY_DATA, null)
        if (stored != null) {
            runCatching { json.decodeFromString<AppData>(stored) }
                .getOrNull()
                ?.takeIf { it.presets.isNotEmpty() }
                ?.let { return it.normalised().withCoherentRuns() }
        }
        val migrated = migrateLegacy()
        val data = migrated ?: AppData(
            presets = listOf(newPreset(appContext.getString(R.string.default_preset_name, 1))),
            selectedId = "",
        )
        return data.normalised().also { normalised ->
            preferences.edit { putString(KEY_DATA, json.encodeToString(normalised)) }
        }
    }

    /**
     * Reads the 2.x format — a Gson array of timers under a different preferences file — so an
     * upgrade keeps the presets someone has already set up. The old format appended a blank
     * default timer to the end of the list as its "add" affordance; that one is not a real preset.
     */
    private fun migrateLegacy(): AppData? {
        val legacyPreferences =
            appContext.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = legacyPreferences.getString(LEGACY_KEY, null) ?: return null
        val legacy = try {
            json.decodeFromString<List<LegacyTimer>>(stored)
        } catch (_: SerializationException) {
            null
        }
        legacyPreferences.edit { remove(LEGACY_KEY) }
        if (legacy.isNullOrEmpty()) return null

        val timers = if (legacy.size > 1 && legacy.last().isFactoryDefault) legacy.dropLast(1) else legacy
        val presets = timers.mapIndexed { index, timer ->
            Preset(
                id = UUID.randomUUID().toString(),
                name = appContext.getString(R.string.default_preset_name, index + 1),
                totalMillis = timer.totalMillis,
                yellowMillis = timer.yellowMillis,
                redMillis = timer.redMillis,
            ).coerceValid()
        }
        return AppData(presets = presets, selectedId = presets.first().id)
    }

    /** Drops runs whose preset is gone, repairs an unknown selection, and never allows zero presets. */
    private fun AppData.normalised(): AppData {
        val safePresets = presets.map { it.coerceValid() }
            .ifEmpty { listOf(newPreset(appContext.getString(R.string.default_preset_name, 1))) }
        val ids = safePresets.map { it.id }.toSet()
        return AppData(
            presets = safePresets,
            selectedId = selectedId.takeIf { it in ids } ?: safePresets.first().id,
            runs = runs.filterKeys { it in ids }.filterValues { it.isActive },
        )
    }

    /** Clears any run whose realtime origin did not survive a reboot. */
    private fun AppData.withCoherentRuns(): AppData {
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        return copy(runs = runs.filterValues { it.isCoherent(nowRealtime, nowWall) })
    }

    private fun newPreset(name: String) = Preset(
        id = UUID.randomUUID().toString(),
        name = name,
        totalMillis = Preset.DEFAULT_TOTAL_MILLIS,
        yellowMillis = Preset.DEFAULT_YELLOW_MILLIS,
        redMillis = Preset.DEFAULT_RED_MILLIS,
    )

    /**
     * Pulls a preset back inside the rules rather than dropping it. Hand-edited or corrupted
     * preferences should cost the user a wrong warning time, not their whole list.
     */
    private fun Preset.coerceValid(): Preset {
        if (isValid) return this
        val total = totalMillis.coerceIn(1L, DurationText.MAX_MILLIS)
        val yellow = yellowMillis.coerceIn(0L, total - 1)
        val red = redMillis.coerceIn(0L, yellow)
        return copy(
            name = name.ifBlank { appContext.getString(R.string.default_preset_name, 1) },
            totalMillis = total,
            yellowMillis = yellow,
            redMillis = red,
        )
    }

    @Serializable
    private data class LegacyTimer(
        val totalMillis: Long = 0L,
        val yellowMillis: Long = 0L,
        val redMillis: Long = 0L,
    ) {
        val isFactoryDefault: Boolean
            get() = totalMillis == Preset.DEFAULT_TOTAL_MILLIS &&
                yellowMillis == Preset.DEFAULT_YELLOW_MILLIS &&
                redMillis == Preset.DEFAULT_RED_MILLIS
    }

    private companion object {
        const val PREFERENCES_NAME = "ontime_state"
        const val KEY_DATA = "data"
        const val LEGACY_PREFERENCES_NAME = "MultiTimerPrefs"
        const val LEGACY_KEY = "timers"
    }
}
