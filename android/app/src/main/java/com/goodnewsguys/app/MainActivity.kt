package com.goodnewsguys.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { GngTheme { App(vm) } }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val p = LocalPalette.current
    val ui = UiData(vm.digests, vm.saved, vm.city)
    val a = vm.actions
    val homeList = rememberLazyListState()
    val depth = vm.stack.size
    // Remember the last depth so the slide runs the right way going back.
    var lastDepth by remember { mutableIntStateOf(depth) }
    val forward = depth >= lastDepth
    lastDepth = depth

    BackHandler(enabled = depth > 1, onBack = a.back)

    Surface(Modifier.fillMaxSize(), color = p.bg, contentColor = p.ink) {
        when {
            ui.digests.isEmpty() && vm.loading -> LoadingScreen()
            ui.digests.isEmpty() -> ErrorScreen(a.refresh)
            else -> AnimatedContent(
                targetState = vm.stack.last(),
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 6 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -dir * it / 6 })
                },
                label = "screen",
            ) { screen ->
                Surface(Modifier.fillMaxSize(), color = p.bg, contentColor = p.ink) {
                    when (screen) {
                        Screen.Home -> HomeScreen(ui, a, vm.refreshing, homeList)
                        is Screen.Detail -> StoryScreen(screen.story, ui, a)
                        is Screen.Topic -> TopicScreen(screen.slug, ui, a)
                        Screen.Local -> LocalScreen(ui, a)
                        Screen.Saved -> SavedScreen(ui, a)
                    }
                }
            }
        }
    }
}
