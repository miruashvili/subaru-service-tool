package com.subaru.servicetool.ui.navigation

import android.content.res.Configuration
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.subaru.servicetool.R
import com.subaru.servicetool.ui.bluetooth.BluetoothSettingsScreen
import com.subaru.servicetool.ui.main.MainViewModel
import com.subaru.servicetool.ui.onboarding.OnboardingScreen
import com.subaru.servicetool.ui.dashboard.DashboardScreen
import com.subaru.servicetool.ui.screens.SensorsScreen
import com.subaru.servicetool.ui.screens.ServiceScreen
import com.subaru.servicetool.ui.screens.SettingsScreen

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", R.string.nav_dashboard, Icons.Filled.Speed)
    object Sensors   : Screen("sensors",   R.string.nav_sensors,   Icons.AutoMirrored.Filled.List)
    object Service   : Screen("service",   R.string.nav_service,   Icons.Filled.Build)
    object Settings  : Screen("settings",  R.string.nav_settings,  Icons.Filled.Settings)
}

private val bottomNavItems = listOf(Screen.Dashboard, Screen.Sensors, Screen.Service, Screen.Settings)
private val tabRoutes      = bottomNavItems.map { it.route }.toSet()

private val tabEnter = fadeIn(tween(300))
private val tabExit  = fadeOut(tween(300))

private val slideEnter     = slideInHorizontally(tween(300))  { it } + fadeIn(tween(300))
private val slideExit      = slideOutHorizontally(tween(250)) { it } + fadeOut(tween(200))
private val slidePopEnter  = slideInHorizontally(tween(300))  { -it } + fadeIn(tween(300))
private val slidePopExit   = slideOutHorizontally(tween(250)) { it } + fadeOut(tween(200))

@Composable
fun AppNavigation() {
    val mainViewModel: MainViewModel = hiltViewModel()
    val onboardingComplete by mainViewModel.isOnboardingComplete.collectAsState()

    when (onboardingComplete) {
        null  -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> OnboardingScreen()
        true  -> MainNavHost()
    }
}

@Composable
private fun MainNavHost() {
    val navController  = rememberNavController()
    val navBackStack   by navController.currentBackStackEntryAsState()
    val currentRoute   = navBackStack?.destination?.route
    val configuration  = LocalConfiguration.current
    val isLandscape    = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showNavigation = currentRoute in tabRoutes

    if (isLandscape) {
        // ── Landscape: NavigationRail on the left ─────────────────────────
        Row(Modifier.fillMaxSize()) {
            if (showNavigation) {
                AppNavigationRail(
                    navBackStack = navBackStack,
                    onNavigate   = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            NavHost(
                navController    = navController,
                startDestination = Screen.Dashboard.route,
                modifier         = Modifier.weight(1f),
            ) {
                landscapeComposables(navController)
            }
        }
    } else {
        // ── Portrait: NavigationBar at the bottom ──────────────────────────
        Scaffold(
            bottomBar = {
                if (showNavigation) {
                    AppNavigationBar(
                        navBackStack = navBackStack,
                        onNavigate   = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Dashboard.route,
            ) {
                portraitComposables(navController, innerPadding)
            }
        }
    }
}

@Composable
private fun AppNavigationBar(
    navBackStack: androidx.navigation.NavBackStackEntry?,
    onNavigate: (Screen) -> Unit,
) {
    NavigationBar {
        val currentDest = navBackStack?.destination
        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                icon     = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
                label    = { Text(stringResource(screen.labelRes)) },
                selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                onClick  = { onNavigate(screen) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    navBackStack: androidx.navigation.NavBackStackEntry?,
    onNavigate: (Screen) -> Unit,
) {
    NavigationRail(modifier = Modifier.width(72.dp)) {
        val currentDest = navBackStack?.destination
        bottomNavItems.forEach { screen ->
            NavigationRailItem(
                icon     = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
                label    = null,
                selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                onClick  = { onNavigate(screen) },
            )
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.landscapeComposables(
    navController: androidx.navigation.NavController,
) {
    composable(Screen.Dashboard.route, enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        DashboardScreen(
            paddingValues         = androidx.compose.foundation.layout.PaddingValues(),
            onNavigateToBluetooth = { navController.navigate("bluetooth_settings") },
        )
    }
    composable(Screen.Sensors.route,  enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        SensorsScreen(androidx.compose.foundation.layout.PaddingValues())
    }
    composable(Screen.Service.route,  enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        ServiceScreen(androidx.compose.foundation.layout.PaddingValues())
    }
    composable(Screen.Settings.route, enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        SettingsScreen(
            paddingValues   = androidx.compose.foundation.layout.PaddingValues(),
            onChangeVehicle = { navController.navigate("vehicle_picker") },
            onOpenBluetooth = { navController.navigate("bluetooth_settings") },
        )
    }
    subScreenComposables(navController)
}

private fun androidx.navigation.NavGraphBuilder.portraitComposables(
    navController: androidx.navigation.NavController,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    composable(Screen.Dashboard.route, enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        DashboardScreen(
            paddingValues         = innerPadding,
            onNavigateToBluetooth = { navController.navigate("bluetooth_settings") },
        )
    }
    composable(Screen.Sensors.route,  enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        SensorsScreen(innerPadding)
    }
    composable(Screen.Service.route,  enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        ServiceScreen(innerPadding)
    }
    composable(Screen.Settings.route, enterTransition = { tabEnter }, exitTransition = { tabExit }) {
        SettingsScreen(
            paddingValues   = innerPadding,
            onChangeVehicle = { navController.navigate("vehicle_picker") },
            onOpenBluetooth = { navController.navigate("bluetooth_settings") },
        )
    }
    subScreenComposables(navController)
}

private fun androidx.navigation.NavGraphBuilder.subScreenComposables(
    navController: androidx.navigation.NavController,
) {
    composable(
        "vehicle_picker",
        enterTransition    = { slideEnter },
        exitTransition     = { slideExit },
        popEnterTransition = { slidePopEnter },
        popExitTransition  = { slidePopExit },
    ) {
        OnboardingScreen(
            isChangingVehicle = true,
            onComplete = { navController.popBackStack() },
        )
    }
    composable(
        "bluetooth_settings",
        enterTransition    = { slideEnter },
        exitTransition     = { slideExit },
        popEnterTransition = { slidePopEnter },
        popExitTransition  = { slidePopExit },
    ) {
        BluetoothSettingsScreen(onBack = { navController.popBackStack() })
    }
}
