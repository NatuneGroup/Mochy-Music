// the audio lab's picture hooks that read the app's own audio tap, the app's obscured state and its window: they stay in :app with the tap they read, and the shared kit (AudioLabKit) keeps everything else of the lab

package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.eq.audio.AudioTap
import com.mochyy.mochymusic.ui.component.LocalNavAnimatedContentScope

/** whether pictures should move: not while the player covers the app, the page is opening or closing, or it is scrolled out of view */
@Composable
fun rememberPicturesHeld(onScreen: State<Boolean>): State<Boolean> {
    val obscured = LocalAppContentObscured.current
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    return remember(obscured, navTransition, onScreen) {
        derivedStateOf {
            !onScreen.value || obscured.value || (navTransition != null &&
                (navTransition.currentState != EnterExitState.Visible || navTransition.targetState != EnterExitState.Visible))
        }
    }
}

/** true while any part of this is inside the window; written only when it flips */
@Composable
fun Modifier.trackOnScreen(state: MutableState<Boolean>): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val visible = bounds.height > 0f && bounds.bottom > 0f && bounds.top < view.height
        if (visible != state.value) state.value = visible
    }
}

/** asks the audio tap to fill while this page shows a live picture */
@Composable
fun ListenToAudio() {
    DisposableEffect(Unit) {
        AudioTap.listen()
        onDispose { AudioTap.stopListening() }
    }
}

/** a smoothed music level 0..1 for pictures to breathe with, updated from the frame clock */
class LevelFollower {
    private val scratch = FloatArray(1024)
    var level = 0f
        private set
    var punch = 0f
        private set
    private var slow = 0f

    /** call once per frame with the seconds since the last one */
    fun update(dt: Float) {
        val now = AudioTap.level(scratch)
        level += (now - level) * (if (now > level) 0.5f else 0.12f)
        slow += (now - slow) * 0.03f
        // how far above its own recent average it is: a kick or a downbeat
        val p = ((now - slow * 1.25f) * 6f).coerceIn(0f, 1f)
        punch = if (p > punch) p else (punch - dt * 3f).coerceAtLeast(0f)
    }
}
