package com.aeunal.stressball.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aeunal.stressball.core.BallState
import com.aeunal.stressball.core.BuffKind
import com.aeunal.stressball.core.FingerSample
import com.aeunal.stressball.core.GameEngine
import com.aeunal.stressball.core.GameState
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.Language
import com.aeunal.stressball.core.OfflineReport
import com.aeunal.stressball.core.SkinSlot
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
    data class ChestOpened(val ball: BallState) : GameEvent
    data class SparkCaught(val kind: BuffKind) : GameEvent
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

    /** In-app language override, null = the game's default (Turkish). */
    val language: StateFlow<Language?> = repository.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var loopJob: Job? = null
    private var foreground = false

    // Finger state. Touch events only arrive while the finger moves, so a
    // finger that stops moving is detected by its samples going stale.
    private var fingerDown = false
    private var twist = 0.0
    private var drag = 0.0
    private var lastMoveNanos = 0L

    // Pinch state. The squeeze rate is the positive derivative of the weight.
    private var pinchWeight = 0.0
    private var squeezeRate = 0.0
    private var lastPinchNanos = 0L

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
        onPinchEnd()
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
                    if (moving) engine.setFinger(true, twist, drag) else engine.setFinger(true)
                } else {
                    engine.setFinger(false)
                }
                val squeezing = now - lastPinchNanos < MOVE_HOLD_NANOS
                engine.setTurbo(pinchWeight, if (squeezing) squeezeRate else 0.0)

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
        twist = 0.0
        drag = 0.0
        lastMoveNanos = 0L
        engine.setFinger(true)
        _view.value = engine.view()
    }

    /** A motion sample from the spin gesture. Lightly smoothed to hide touch jitter. */
    fun onFingerMove(sample: FingerSample) {
        if (!fingerDown) return
        if (!sample.twistOmega.isFinite() || !sample.dragOmega.isFinite()) return
        if (lastMoveNanos == 0L) {
            twist = sample.twistOmega
            drag = sample.dragOmega
        } else {
            twist = twist * 0.5 + sample.twistOmega * 0.5
            drag = drag * 0.5 + sample.dragOmega * 0.5
        }
        lastMoveNanos = System.nanoTime()
    }

    fun onFingerUp() {
        fingerDown = false
        lastMoveNanos = 0L
        engine.setFinger(false)
        _view.value = engine.view()
    }

    /** Squeeze weight 0..1 from the pinch gesture. */
    fun onPinch(weight: Float) {
        val w = weight.toDouble().coerceIn(0.0, 1.0)
        val now = System.nanoTime()
        if (lastPinchNanos != 0L) {
            val dt = (now - lastPinchNanos) / 1e9
            if (dt > 0.0) {
                val rate = ((w - pinchWeight) / dt).coerceAtLeast(0.0)
                squeezeRate = squeezeRate * 0.5 + rate * 0.5
            }
        }
        pinchWeight = w
        lastPinchNanos = now
    }

    fun onPinchEnd() {
        pinchWeight = 0.0
        squeezeRate = 0.0
        lastPinchNanos = 0L
        engine.setTurbo(0.0)
        _view.value = engine.view()
    }

    fun setTopView(top: Boolean) {
        if (engine.setTopView(top)) {
            onFingerUp()
            _view.value = engine.view()
        }
    }

    fun tapSpark() {
        val before = engine.state
        engine.tapSpark()?.let { emit(GameEvent.SparkCaught(it)) }
        publish(before)
    }

    // ------------------------------------------------------------------
    // Shop, garage, skins
    // ------------------------------------------------------------------

    fun buy(id: String) = mutate { engine.buy(id) }

    fun topUpGems() = mutate { engine.topUpGems(); true }

    fun buySkin(id: String) = mutate { engine.buySkin(id) }

    fun equipSkin(id: String) = mutate { engine.equipSkin(id) }

    fun unequipSkin(slot: SkinSlot) = mutate { engine.unequipSkin(slot); true }

    fun openChest() {
        val before = engine.state
        val ball = engine.openChest()
        if (ball != null) {
            emit(GameEvent.ChestOpened(ball))
            publish(before)
            saveNow()
        }
    }

    fun switchBall(id: String) {
        onFingerUp()
        onPinchEnd()
        mutate { engine.switchBall(id) }
    }

    fun sellBall(id: String) {
        onFingerUp()
        onPinchEnd()
        if (mutate { engine.sellBall(id) }) saveNow()
    }

    fun setLanguage(language: Language?) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun prestige(): Boolean {
        val ok = mutate { engine.prestige() }
        if (ok) saveNow()
        return ok
    }

    fun consumeEvent(event: GameEvent) {
        _events.update { it - event }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun mutate(action: () -> Boolean): Boolean {
        val before = engine.state
        val ok = action()
        if (ok) publish(before)
        return ok
    }

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
        /** A finger with no movement sample for this long counts as held still (or a pinch as not tightening). */
        const val MOVE_HOLD_NANOS = 80_000_000L
        const val AUTOSAVE_NANOS = 5_000_000_000L
    }
}
