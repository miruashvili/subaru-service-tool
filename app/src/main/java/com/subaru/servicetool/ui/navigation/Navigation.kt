package com.subaru.servicetool.ui.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
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
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning
import kotlinx.coroutines.delay
import java.util.Calendar

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
    val mainViewModel: MainViewModel = hiltViewModel()
    val ambientTemp by mainViewModel.ambientTemp.collectAsState()

    val isConnected by mainViewModel.isConnected.collectAsState()

    if (isLandscape) {
        // ── Landscape: full-screen NavHost with overlay pill + status bar ─
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavHost(
                navController    = navController,
                startDestination = Screen.Dashboard.route,
                modifier         = Modifier.fillMaxSize(),
            ) {
                landscapeComposables(navController)
            }
            if (showNavigation) {
                LandscapeNavPill(
                    navBackStack = navBackStack,
                    ambientTemp  = ambientTemp,
                    onNavigate   = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            LandscapeStatusBar(
                isConnected = isConnected,
                modifier    = Modifier.align(Alignment.TopEnd),
            )
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
private fun LandscapeNavPill(
    navBackStack: NavBackStackEntry?,
    ambientTemp: Float?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color  = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape  = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
        tonalElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
        ) {
            val currentDest = navBackStack?.destination
            bottomNavItems.forEach { screen ->
                val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                IconButton(onClick = { onNavigate(screen) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector        = screen.icon,
                        contentDescription = stringResource(screen.labelRes),
                        tint               = if (selected) MaterialTheme.colorScheme.primary
                                             else MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        modifier           = Modifier.size(22.dp),
                    )
                }
            }
            ambientTemp?.let { temp ->
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurface.copy(0.12f))
                Spacer(Modifier.height(4.dp))
                Icon(
                    imageVector        = Icons.Filled.DeviceThermostat,
                    contentDescription = null,
                    modifier           = Modifier.size(14.dp),
                    tint               = when {
                        temp > 35f -> DarkError
                        temp > 25f -> DarkWarning
                        temp < 5f  -> DarkPrimary
                        else       -> DarkSuccess
                    },
                )
                Text(
                    text  = "%.0f°".format(temp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun LandscapeStatusBar(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var batteryLevel by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0) batteryLevel = level * 100 / scale
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    var timeStr by remember { mutableStateOf("--:--") }
    var dateStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            timeStr = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            dateStr = "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]}"
            delay(30_000L)
        }
    }

    Surface(
        color  = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape  = RoundedCornerShape(bottomStart = 10.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(timeStr,
                fontSize = 12.sp, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface)
            Text(dateStr,
                fontSize = 11.sp, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
            if (batteryLevel >= 0) {
                Icon(Icons.Filled.BatteryFull, contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                Text("$batteryLevel%",
                    fontSize = 11.sp, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) DarkSuccess else MaterialTheme.colorScheme.onSurface.copy(0.25f))
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
