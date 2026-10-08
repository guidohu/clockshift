package com.clockshift.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockshift.app.ui.nav.MainNav
import com.clockshift.app.ui.setup.SetupScreen
import com.clockshift.app.ui.setup.SetupViewModel

private enum class Mode { Loading, Setup, Main }

@Composable
fun AppRoot(viewModel: MainViewModel, openDayRequest: Long?, onOpenDayHandled: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    val mode = when {
        current == null -> Mode.Loading
        !current.settings.setupDone -> Mode.Setup
        else -> Mode.Main
    }
    Crossfade(targetState = mode, label = "root") { target ->
        when (target) {
            Mode.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            Mode.Setup -> SetupScreen(viewModel<SetupViewModel>(), viewModel)
            Mode.Main -> current?.let { MainNav(it, viewModel, openDayRequest, onOpenDayHandled) }
        }
    }
}
