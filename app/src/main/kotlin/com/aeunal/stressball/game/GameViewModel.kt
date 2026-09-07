package com.aeunal.stressball.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aeunal.stressball.core.GameEngine
import com.aeunal.stressball.core.GameState
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.Language
import com.aeunal.stressball.core.OfflineReport
import com.aeunal.stressball.data.SaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One-shot messages the UI shows as toasts / dialogs. */
sealed interface GameEvent {
    data class AchievementUnlocked(val id: String) : GameEvent
    data class Offline(val report: OfflineReport) : GameEvent
}

/**
 * Owns the [GameEngine], runs the fixed-rate game loop while the app is in
 * the foreground, and persists progress.
 *
 * The engine is only ever touched from the main thread (viewModelScope), so
 * no locking is needed.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SaveRepository(application)
    private val engine = GameEngine()

    /** Saves must finish even if the ViewModel is cleared right after onStop. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _view = MutableStateFlow(engine.view())
    val view: StateFlow<GameView> = _view.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableStateFlow<List<GameEvent>>(emptyList())
    val events: StateFlow<List<GameEvent>> = _events.asStateFlow()

    /** In-app language override, null = device language. */
    val language: StateFlow<Language?> = repository.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var loopJob: Job? = null
    private var foreground = false

    // Finger state. Touch events only arrive while the finger moves, so a
    // finger that stops moving is detected by its samples going stale.
    private var fingerDown = false
    private var fingerOmega = 0.0
    private var lastMoveNanos = 0L
    private var turboHeld = false

    private var lastAutosaveNanos = 0L

    init {
        viewModelScope.launch {
            repository.load()?.let { engine.load(it) }
            _loaded.value = true
            _view.value = engine.view()
            if (foreground) startLoop()
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    fun onForeground() {
        foreground = true
        if (_loaded.value) startLoop()
    }

    fun onBackground() {
        foreground = false
        loopJob?.cancel()
        loopJob = null
        onFingerUp()
        setTurboHeld(false)
        saveNow()
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        engine.applyOfflineProgress(System.currentTimeMillis())?.let { report ->
            if (report.pointsEarned >= 1.0) emit(GameEvent.Offline(report))
        }
        loopJob = viewModelScope.launch {
            var last = System.nanoTime()
            lastAutosaveNanos = last
            while (isActive) {
                delay(FRAME_MS)
                val now = System.nanoTime()
                val dt = (now - last) / 1e9
                last = now

                if (fingerDown) {
                    val moving = now - lastMoveNanos < MOVE_HOLD_NANOS
                    engine.setFinger(true, if (moving) fingerOmega else 0.0)
                } else {
                    engine.setFinger(false)
                }
                engine.setTurbo(turboHeld)

                val before = engine.state
                engine.tick(dt)
                publish(before)

                if (now - lastAutosaveNanos > AUTOSAVE_NANOS) {
                    lastAutosaveNanos = now
                    saveNow()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    fun onFingerDown() {
        fingerDown = true
        fingerOmega = 0.0
        lastMoveNanos = 0L
        engine.setFinger(true, 0.0)
        _view.value = engine.view()
    }

    /**
     * Called by the spin gesture with the finger's angular velocity around
     * the ball centre in rad/s (signed, clockwise positive). Samples are
     * lightly smoothed to hide touch sampling jitter.
     */
    fun onFingerMove(omega: Double) {
        if (!omega.isFinite() || !fingerDown) return
        fingerOmega = if (lastMoveNanos == 0L) omega else fingerOmega * 0.5 + omega * 0.5
        lastMoveNanos = System.nanoTime()
    }

    fun onFingerUp() {
        fingerDown = false
        fingerOmega = 0.0
        lastMoveNanos = 0L
        engine.setFinger(false)
        _view.value = engine.view()
    }

    fun setTurboHeld(held: Boolean) {
        turboHeld = held
        engine.setTurbo(held)
        _view.value = engine.view()
    }

    fun buy(id: String) {
        val before = engine.state
        if (engine.buy(id)) publish(before)
    }

    fun buyCosmetic(id: String) {
        val before = engine.state
        if (engine.buyCosmetic(id)) publish(before)
    }

    fun equipCosmetic(id: String) {
        val before = engine.state
        if (engine.equipCosmetic(id)) publish(before)
    }

    fun setLanguage(language: Language?) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun prestige(): Boolean {
        val before = engine.state
        val ok = engine.prestige()
        if (ok) {
            publish(before)
            saveNow()
        }
        return ok
    }

    fun consumeEvent(event: GameEvent) {
        _events.update { it - event }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun publish(before: GameState) {
        val after = engine.state
        if (after.achievements.size > before.achievements.size) {
            (after.achievements - before.achievements).forEach { emit(GameEvent.AchievementUnlocked(it)) }
        }
        _view.value = engine.view()
    }

    private fun emit(event: GameEvent) {
        _events.update { it + event }
    }

    private fun saveNow() {
        if (!_loaded.value) return
        engine.markSaved(System.currentTimeMillis())
        val snapshot = engine.state
        saveScope.launch { repository.save(snapshot) }
    }

    override fun onCleared() {
        loopJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val FRAME_MS = 16L
        /** A finger with no movement sample for this long counts as held still. */
        const val MOVE_HOLD_NANOS = 80_000_000L
        const val AUTOSAVE_NANOS = 5_000_000_000L
    }
}
