package com.github.guidohu.clockshift

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.guidohu.clockshift.ui.AppRoot
import com.github.guidohu.clockshift.ui.MainViewModel
import com.github.guidohu.clockshift.ui.pro.LocalPro
import com.github.guidohu.clockshift.ui.pro.ProUpgradeDialog
import com.github.guidohu.clockshift.ui.pro.ProAccess
import com.github.guidohu.clockshift.ui.theme.ClockShiftTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container.repository) } }
    }

    /** Epoch day requested by a notification tap; consumed once the navigation happened. */
    private var openDayRequest by mutableStateOf<Long?>(null)

    /** Whether the "Enable Pro features?" dialog is showing; opened by tapping a locked feature. */
    private var proPromptOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openDayRequest = intent.requestedDay()
        setContent {
            CompositionLocalProvider(LocalPro provides rememberProAccess()) {
                ClockShiftTheme {
                    AppRoot(
                        viewModel = viewModel,
                        openDayRequest = openDayRequest,
                        onOpenDayHandled = { openDayRequest = null },
                    )
                    if (proPromptOpen) ProUpgradeDialog(onDismiss = { proPromptOpen = false })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The purchase may have changed in the Play Store, and location permissions or services in system settings.
        container.pro.refresh()
        lifecycleScope.launch(Dispatchers.Default) { container.autoTracker.sync() }
    }

    @Composable
    private fun rememberProAccess(): ProAccess {
        val pro = container.pro
        val isPro by pro.isPro.collectAsStateWithLifecycle()
        val offer by pro.offer.collectAsStateWithLifecycle()
        val status by pro.status.collectAsStateWithLifecycle()
        val pending by pro.pending.collectAsStateWithLifecycle()
        val debugUnlocked by pro.debugUnlock.collectAsStateWithLifecycle()
        return remember(isPro, offer, status, pending, debugUnlocked) {
            ProAccess(
                isPro = isPro,
                offer = offer,
                status = status,
                pending = pending,
                debugUnlockAvailable = BuildConfig.DEBUG,
                debugUnlocked = debugUnlocked,
                purchase = pro::purchase,
                refresh = pro::refresh,
                setDebugUnlock = pro::setDebugUnlock,
                showUpgrade = { proPromptOpen = true },
            )
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
