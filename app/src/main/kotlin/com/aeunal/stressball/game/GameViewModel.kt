package com.aeunal.stressball.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aeunal.stressball.core.GameEngine
import com.aeunal.stressball.core.GameState
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.OfflineReport
import com.aeunal.stressball.data.SaveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One-shot messages the UI shows as toasts / snackbars. */
sealed interface GameEvent {
    data class AchievementUnlocked(val id: String) : GameEvent
    data class Offline(val report: OfflineReport) : GameEvent
    data object OverdriveFired : GameEvent
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

    private val _view = MutableStateFlow(engine.view())
    val view: StateFlow<GameView> = _view.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableStateFlow<List<GameEvent>>(emptyList())
    val events: StateFlow<List<GameEvent>> = _events.asStateFlow()

    /** +1 / -1: the direction the ball is visually spinning. */
    private val _spinDirection = MutableStateFlow(1f)
    val spinDirection: StateFlow<Float> = _spinDirection.asStateFlow()

    private var loopJob: Job? = null
    private var fingerOmega = 0.0
    private var lastFingerInputNanos = 0L
    private var lastAutosaveNanos = 0L
    private var foreground = false

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

                if (now - lastFingerInputNanos < FINGER_HOLD_NANOS) {
                    engine.spin(fingerOmega)
                } else {
                    fingerOmega = 0.0
                }

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

    /**
     * Called by the spin gesture with the finger's angular velocity around
     * the ball centre in rad/s (signed). Values are smoothed to hide touch
     * sampling jitter.
     */
    fun onFingerSpin(omega: Double) {
        if (!omega.isFinite()) return
        val speed = abs(omega)
        fingerOmega = if (fingerOmega == 0.0) speed else fingerOmega * 0.6 + speed * 0.4
        lastFingerInputNanos = System.nanoTime()
        if (speed > 0.5) _spinDirection.value = if (omega >= 0) 1f else -1f
    }

    fun onFingerLift() {
        fingerOmega = 0.0
        lastFingerInputNanos = 0L
    }

    fun overdrive() {
        val before = engine.state
        if (engine.overdrive()) {
            emit(GameEvent.OverdriveFired)
            publish(before)
        }
    }

    fun buy(id: String) {
        val before = engine.state
        if (engine.buy(id)) publish(before)
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
        viewModelScope.launch { repository.save(snapshot) }
    }

    override fun onCleared() {
        loopJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val FRAME_MS = 16L
        const val FINGER_HOLD_NANOS = 120_000_000L
        const val AUTOSAVE_NANOS = 5_000_000_000L
    }
}
