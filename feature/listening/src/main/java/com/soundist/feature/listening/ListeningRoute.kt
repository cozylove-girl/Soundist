package com.soundist.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

private const val LISTENING_VIEW_MODEL_KEY = "soundist:listening:shared"

/** A single activity-scoped entry used by Home, Sounds and Radio. */
@Composable
fun rememberListeningViewModel(
    repository: ListeningRepository,
    audioController: ListeningAudioController,
    generatedAudioRenderer: GeneratedAudioRenderer? = null,
): ListeningViewModel = viewModel(
    key = LISTENING_VIEW_MODEL_KEY,
    factory = ListeningViewModelFactory(repository, audioController, generatedAudioRenderer),
)

@Composable
fun ListeningRoute(
    destination: ListeningDestination,
    modifier: Modifier = Modifier,
    injectedViewModel: ListeningViewModel? = null,
    repository: ListeningRepository = StatefulListeningRepository(),
    audioController: ListeningAudioController = StatefulAudioController(),
    artworkPicker: StationArtworkPicker? = null,
    audioPicker: StationAudioPicker? = null,
    generatedAudioRenderer: GeneratedAudioRenderer? = null,
    reduceMotion: Boolean = false,
    systemAnimationScale: Float = 1f,
    onOpenSounds: () -> Unit = {},
) {
    val owner = injectedViewModel ?: rememberListeningViewModel(repository, audioController, generatedAudioRenderer)
    val state by owner.state.collectAsState()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (destination) {
            ListeningDestination.HOME -> ListeningHomeScreen(state, owner::dispatch, reduceMotion = reduceMotion || systemAnimationScale <= 0f, animationScale = systemAnimationScale, onOpenSounds = onOpenSounds)
            ListeningDestination.SOUNDS -> SoundLibraryScreen(state, owner::dispatch)
            ListeningDestination.RADIO -> RadioScreen(state, owner::dispatch, artworkPicker = artworkPicker, audioPicker = audioPicker, reduceMotion = reduceMotion || systemAnimationScale <= 0f)
        }
    }
}
