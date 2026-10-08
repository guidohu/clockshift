package com.github.guidohu.clockshift.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.guidohu.clockshift.R
import com.github.guidohu.clockshift.data.AppState
import com.github.guidohu.clockshift.ui.ClockActions
import com.github.guidohu.clockshift.ui.day.DayScreen
import com.github.guidohu.clockshift.ui.history.HistoryScreen
import com.github.guidohu.clockshift.ui.overview.OverviewScreen
import com.github.guidohu.clockshift.ui.settings.SettingsScreen
import com.github.guidohu.clockshift.ui.today.TodayScreen
import java.time.LocalDate

private object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val OVERVIEW = "overview"
    const val SETTINGS = "settings"
    const val DAY = "day/{epochDay}?add={add}"
    const val ARG_EPOCH_DAY = "epochDay"
    const val ARG_ADD = "add"

    fun day(date: LocalDate, addEntry: Boolean) = "day/${date.toEpochDay()}?add=$addEntry"
}

private data class Destination(
    val route: String,
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val Destinations = listOf(
    Destination(Routes.TODAY, R.string.nav_today, Icons.Outlined.Schedule, Icons.Filled.Schedule),
    Destination(Routes.HISTORY, R.string.nav_history, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Destination(Routes.OVERVIEW, R.string.nav_overview, Icons.Outlined.Insights, Icons.Filled.Insights),
    Destination(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun MainNav(
    state: AppState,
    actions: ClockActions,
    openDayRequest: Long?,
    onOpenDayHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = Destinations.any { destination -> currentDestination?.hierarchy?.any { it.route == destination.route } == true }

    // A tap on the reminder notification asks to open a specific day.
    LaunchedEffect(openDayRequest) {
        if (openDayRequest != null) {
            navController.navigate(Routes.day(LocalDate.ofEpochDay(openDayRequest), addEntry = false))
            onOpenDayHandled()
        }
    }

    fun switchTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { switchTab(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    state = state,
                    actions = actions,
                    onOpenDay = { navController.navigate(Routes.day(it, addEntry = false)) },
                    onOpenOverview = { switchTab(Routes.OVERVIEW) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    state = state,
                    onOpenDay = { date, add -> navController.navigate(Routes.day(date, add)) },
                )
            }
            composable(Routes.OVERVIEW) { OverviewScreen(state) }
            composable(Routes.SETTINGS) { SettingsScreen(state, actions) }
            composable(
                route = Routes.DAY,
                arguments = listOf(
                    navArgument(Routes.ARG_EPOCH_DAY) { type = NavType.LongType },
                    navArgument(Routes.ARG_ADD) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val date = LocalDate.ofEpochDay(entry.arguments?.getLong(Routes.ARG_EPOCH_DAY) ?: LocalDate.now().toEpochDay())
                DayScreen(
                    state = state,
                    actions = actions,
                    date = date,
                    openNewEntry = entry.arguments?.getBoolean(Routes.ARG_ADD) ?: false,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
