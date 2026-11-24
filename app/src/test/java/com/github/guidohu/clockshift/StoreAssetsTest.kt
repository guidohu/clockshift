package com.github.guidohu.clockshift

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.location.LocationManager
import io.mockk.every
import io.mockk.mockk
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.domain.AutoTrackMode
import com.github.guidohu.clockshift.domain.AutoTrackSettings
import com.github.guidohu.clockshift.domain.Entry
import com.github.guidohu.clockshift.domain.EntryKind
import com.github.guidohu.clockshift.domain.ScheduleVersion
import com.github.guidohu.clockshift.domain.Settings
import com.github.guidohu.clockshift.domain.TimesheetBuilder
import com.github.guidohu.clockshift.domain.WeekSchedule
import com.github.guidohu.clockshift.export.TimesheetExport
import com.github.guidohu.clockshift.export.TimesheetPdf
import com.github.guidohu.clockshift.ui.NoOpActions
import com.github.guidohu.clockshift.ui.components.LocalClock
import com.github.guidohu.clockshift.ui.day.DayScreen
import com.github.guidohu.clockshift.ui.history.HistoryScreen
import com.github.guidohu.clockshift.ui.overview.OverviewScreen
import com.github.guidohu.clockshift.ui.pro.LocalPro
import com.github.guidohu.clockshift.ui.pro.ProAccess
import com.github.guidohu.clockshift.ui.settings.AutoTrackSection
import com.github.guidohu.clockshift.ui.settings.SettingsCard
import com.github.guidohu.clockshift.ui.theme.ClockShiftTheme
import com.github.guidohu.clockshift.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.random.Random

/**
 * Renders the screens for the Play Store listing at full resolution, with demo data and a fixed clock
 * (Thursday afternoon). play-store-assets/make_listing_graphics.py then frames and captions them.
 *
 * Runs only with `-PstoreAssets`, which also compiles against SDK 35 because Paparazzi 1.3.4 can't render
 * SDK 36 yet; see play-store-assets/listing.md for the steps.
 */
class StoreAssetsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 1080,
            screenHeight = 2340,
            xdpi = 420,
            ydpi = 420,
            density = Density.create(420), // 411 dp wide, a typical phone
        ),
        theme = "android:Theme.Material.Light.NoActionBar",
        // Paparazzi otherwise scales the image down to 1000 px tall.
        useDeviceResolution = true,
    )

    @Test
    fun shot_today() = shot { TabFrame(selected = 0) { Today() } }

    @Test
    fun shot_todayDark() = shot(dark = true) { TabFrame(selected = 0) { Today() } }

    @Test
    fun shot_overview() = shot { TabFrame(selected = 2) { OverviewScreen(state) } }

    @Test
    fun shot_history() = shot { TabFrame(selected = 1) { HistoryScreen(state, NoOpActions, onOpenDay = { _, _ -> }) } }

    @Test
    fun shot_day() = shot { DayScreen(state, NoOpActions, date = SHOWCASE_DAY, openNewEntry = false, onBack = {}) }

    @Test
    fun shot_autoCheckIn() = shot { TabFrame(selected = 3) { AutoCheckInSettings() } }

    /** First page of last month's PDF timesheet, at twice the PDF's point size. */
    @Test
    fun shot_timesheet() {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = DeviceConfig(
                screenWidth = TimesheetPdf.PAGE_WIDTH * 2,
                screenHeight = TimesheetPdf.PAGE_HEIGHT * 2,
                xdpi = 320,
                ydpi = 320,
                density = Density.XHIGH,
            ),
        )
        shot {
            val context = LocalContext.current
            val lastMonth = NOW.toLocalDate().minusMonths(1).withDayOfMonth(1)
            val sheet = remember {
                TimesheetBuilder.build(
                    state.schedules, state.entries, state.daysOff, state.settings,
                    lastMonth, lastMonth.plusMonths(1).minusDays(1), NOW,
                )!!
            }
            val pdf = remember { TimesheetPdf(TimesheetExport.labels(context), WeekFields.of(Locale.US).firstDayOfWeek) }
            Canvas(Modifier.fillMaxSize().background(Color.White)) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.scale(size.width / TimesheetPdf.PAGE_WIDTH, size.height / TimesheetPdf.PAGE_HEIGHT)
                    pdf.drawFirstPage(canvas.nativeCanvas, sheet, entries = false)
                }
            }
        }
    }

    private fun shot(dark: Boolean = false, content: @Composable () -> Unit) {
        val view = ComposeView(paparazzi.context).apply {
            setContent {
                val viewModels = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                CompositionLocalProvider(
                    LocalContext provides remember { ServicesContext(paparazzi.context) },
                    LocalViewModelStoreOwner provides viewModels,
                    LocalActivityResultRegistryOwner provides NoOpResultRegistryOwner,
                    LocalClock provides Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()),
                    // Shots show the app with Pro, so the Pro features are visible rather than their upsell.
                    LocalPro provides ProAccess(isPro = true),
                ) {
                    ClockShiftTheme(darkTheme = dark, dynamicColor = false) {
                        Surface { content() }
                    }
                }
            }
        }
        paparazzi.snapshot(view)
    }

    /** The app's bottom navigation around a tab, as MainNav draws it (MainNav needs a real back stack to show it). */
    @Composable
    private fun TabFrame(selected: Int, content: @Composable () -> Unit) {
        val tabs = listOf(
            Triple(R.string.nav_today, Icons.Outlined.Schedule, Icons.Filled.Schedule),
            Triple(R.string.nav_history, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
            Triple(R.string.nav_overview, Icons.Outlined.Insights, Icons.Filled.Insights),
            Triple(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
        )
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, (label, icon, selectedIcon) ->
                        NavigationBarItem(
                            selected = index == selected,
                            onClick = {},
                            icon = { Icon(if (index == selected) selectedIcon else icon, contentDescription = null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            },
        ) { padding -> Surface(Modifier.padding(padding)) { content() } }
    }

    @Composable
    private fun Today() = TodayScreen(state, NoOpActions, onOpenDay = {}, onOpenOverview = {})

    /** The Settings tab scrolled to the automatic check-in card. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AutoCheckInSettings() {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
            Column(
                Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsCard(title = stringResource(R.string.settings_auto)) {
                    AutoTrackSection(settings = state.settings.autoTrack, onChange = {})
                }
            }
        }
    }

    private companion object {
        /** A Thursday afternoon, checked in since the morning. */
        val NOW: LocalDateTime = LocalDateTime.of(2026, 11, 19, 15, 12, 37)
        val START: LocalDate = LocalDate.of(2026, 8, 10)

        /** A past day with a morning block, lunch and an afternoon block, for the day editor shot. */
        val SHOWCASE_DAY: LocalDate = LocalDate.of(2026, 11, 17)

        val VACATION: Set<LocalDate> = (12..16).map { LocalDate.of(2026, 10, it) }.toSet()
        val DAYS_OFF: Set<LocalDate> = VACATION + LocalDate.of(2026, 9, 8) + LocalDate.of(2026, 11, 2)

        val state: AppState by lazy {
            AppState(
                settings = Settings(
                    setupDone = true,
                    startDate = START,
                    openingBalanceMinutes = 2 * 60 + 15,
                    reminderEnabled = true,
                    reminderMinute = 18 * 60,
                    autoTrack = AutoTrackSettings(
                        mode = AutoTrackMode.Wifi,
                        wifiNames = listOf("Office", "Office-5G"),
                    ),
                ),
                schedules = listOf(ScheduleVersion(START, WeekSchedule.standard())),
                entries = demoEntries(),
                daysOff = DAYS_OFF,
            )
        }

        private fun demoEntries(): List<Entry> {
            val random = Random(11)
            val entries = mutableListOf<Entry>()
            fun day(date: LocalDate, vararg blocks: Pair<String, String>) {
                blocks.forEachIndexed { index, (from, to) ->
                    entries += Entry(
                        id = entries.size + 1L,
                        start = date.atTime(LocalTime.parse(from)),
                        end = to.takeIf { it.isNotEmpty() }?.let { date.atTime(LocalTime.parse(it)) },
                        kind = if (index % 2 == 1) EntryKind.Break else EntryKind.Work,
                    )
                }
            }
            // Cycled rather than random, so the weekly bars stay even; 11 values against 5 weekdays.
            val overtime = listOf(-25, 20, 5, 35, -10, 15, 30, 0, 45, 10, -15)
            var workday = 0
            var date = START
            val today = NOW.toLocalDate()
            while (date < today) {
                val weekday = date.dayOfWeek < DayOfWeek.SATURDAY
                when {
                    date == SHOWCASE_DAY -> day(date, "07:52" to "11:58", "11:58" to "12:40", "12:40" to "17:31")
                    date == LocalDate.of(2026, 10, 3) -> day(date, "10:00" to "11:45") // a Saturday release
                    !weekday || date in DAYS_OFF -> Unit
                    else -> {
                        // Three of every five working days are logged; the rest count as standard days.
                        val n = workday++
                        if (n % 5 != 2 && n % 5 != 4) {
                            val start = LocalTime.of(8, 0).plusMinutes(random.nextLong(-28, 40))
                            val lunch = LocalTime.of(12, 0).plusMinutes(random.nextLong(0, 35))
                            val lunchEnd = lunch.plusMinutes(random.nextLong(28, 52))
                            val afternoon = 8 * 60L + overtime[n % overtime.size] - Duration.between(start, lunch).toMinutes()
                            day(date, "$start" to "$lunch", "$lunch" to "$lunchEnd", "$lunchEnd" to "${lunchEnd.plusMinutes(afternoon)}")
                        }
                    }
                }
                date = date.plus(1, ChronoUnit.DAYS)
            }
            day(today, "08:04" to "12:02", "12:02" to "12:36", "12:36" to "")
            return entries
        }

        /** Layoutlib has no location or notification service; the permission helpers need both. */
        private class ServicesContext(base: Context) : ContextWrapper(base) {
            private val location = mockk<LocationManager> { every { isLocationEnabled } returns true }
            private val notifications = mockk<NotificationManager> { every { areNotificationsEnabled() } returns true }

            override fun getSystemService(name: String): Any? = when (name) {
                LOCATION_SERVICE -> location
                NOTIFICATION_SERVICE -> notifications
                else -> super.getSystemService(name)
            }
        }

        private val NoOpResultRegistryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
            }
        }
    }
}
