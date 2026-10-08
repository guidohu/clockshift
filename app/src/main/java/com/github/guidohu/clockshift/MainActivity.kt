package com.clockshift.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clockshift.app.ui.AppRoot
import com.clockshift.app.ui.MainViewModel
import com.clockshift.app.ui.theme.ClockShiftTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container.repository) } }
    }

    /** Epoch day requested by a notification tap; consumed once the navigation happened. */
    private var openDayRequest by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openDayRequest = intent.requestedDay()
        setContent {
            ClockShiftTheme {
                AppRoot(
                    viewModel = viewModel,
                    openDayRequest = openDayRequest,
                    onOpenDayHandled = { openDayRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.requestedDay()?.let { openDayRequest = it }
    }

    private fun Intent.requestedDay(): Long? =
        if (hasExtra(EXTRA_OPEN_DAY)) getLongExtra(EXTRA_OPEN_DAY, 0L) else null

    companion object {
        const val EXTRA_OPEN_DAY = "open_day"
    }
}
