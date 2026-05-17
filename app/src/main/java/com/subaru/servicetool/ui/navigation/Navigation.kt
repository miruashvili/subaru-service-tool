package com.subaru.servicetool.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    val currentDest = navBackStack?.destination
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon    = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
                            label   = { Text(stringResource(screen.labelRes)) },
                            selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    paddingValues = innerPadding,
                    onNavigateToBluetooth = { navController.navigate("bluetooth_settings") },
                )
            }
            composable(Screen.Sensors.route)   { SensorsScreen(innerPadding) }
            composable(Screen.Service.route)   { ServiceScreen(innerPadding) }
            composable(Screen.Settings.route)  {
                SettingsScreen(
                    paddingValues     = innerPadding,
                    onChangeVehicle   = { navController.navigate("vehicle_picker") },
                    onOpenBluetooth   = { navController.navigate("bluetooth_settings") },
                )
            }
            composable("vehicle_picker") {
                OnboardingScreen(
                    isChangingVehicle = true,
                    onComplete = { navController.popBackStack() },
                )
            }
            composable("bluetooth_settings") {
                BluetoothSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
