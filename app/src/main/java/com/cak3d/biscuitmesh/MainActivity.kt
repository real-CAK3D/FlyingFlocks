package com.cak3d.biscuitmesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cak3d.biscuitmesh.ui.BluetoothScreen
import com.cak3d.biscuitmesh.ui.DeviceDetailScreen
import com.cak3d.biscuitmesh.ui.DrivesScreen
import com.cak3d.biscuitmesh.ui.FindsScreen
import com.cak3d.biscuitmesh.ui.FleetScreen
import com.cak3d.biscuitmesh.ui.AircraftScreen
import com.cak3d.biscuitmesh.ui.CrewScreen
import com.cak3d.biscuitmesh.ui.DeadZonesScreen
import com.cak3d.biscuitmesh.ui.LabBenchScreen
import com.cak3d.biscuitmesh.ui.LiveScreen
import com.cak3d.biscuitmesh.ui.LocatorScreen
import com.cak3d.biscuitmesh.ui.ProximityBanner
import com.cak3d.biscuitmesh.ui.SatellitesScreen
import com.cak3d.biscuitmesh.ui.SensorsHubScreen
import com.cak3d.biscuitmesh.ui.TrackerScreen
import com.cak3d.biscuitmesh.ui.WigleStatsScreen
import com.cak3d.biscuitmesh.ui.ConstellationBackground
import com.cak3d.biscuitmesh.ui.MiscScreen
import com.cak3d.biscuitmesh.ui.PlacesScreen
import com.cak3d.biscuitmesh.ui.RareBirdsScreen
import com.cak3d.biscuitmesh.ui.RunToolScreen
import com.cak3d.biscuitmesh.ui.SettingsScreen
import com.cak3d.biscuitmesh.ui.StatsScreen
import com.cak3d.biscuitmesh.ui.SubGhzScreen
import com.cak3d.biscuitmesh.ui.WardriveScreen
import com.cak3d.biscuitmesh.ui.WifiScreen
import com.cak3d.biscuitmesh.ui.theme.BiscuitMeshTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleUsbIntent(intent)
        setContent {
            BiscuitMeshTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val vm: MainViewModel = viewModel()
                    val keepAwake by vm.keepAwake.collectAsStateWithLifecycle()
                    androidx.compose.runtime.DisposableEffect(keepAwake) {
                        if (keepAwake) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        onDispose { }
                    }
                    PermissionGate { MainShell(vm) }
                }
            }
        }
    }

    // singleTask: a dongle plugged in while we're running re-delivers through onNewIntent.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    /** Plugging in the WTF dongle launches/foregrounds us with this action — auto-connect. */
    private fun handleUsbIntent(intent: android.content.Intent?) {
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            (application as? BiscuitMeshApp)?.wtfSerial?.connect()
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("fleet", "Fleet", Icons.Default.Dashboard),
    Tab("wardrive", "Wardrive", Icons.Default.Map),
    Tab("wifi", "WiFi", Icons.Default.Wifi),
    Tab("bluetooth", "Bluetooth", Icons.Default.Bluetooth),
    Tab("subghz", "Sub-GHz", Icons.Default.SettingsInputAntenna),
    Tab("misc", "Misc", Icons.Default.Apps),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(vm: MainViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTab = TABS.firstOrNull { it.route == currentRoute }
    val isTopLevel = currentTab != null
    val onMap = currentRoute == "wardrive"
    val scanning by vm.scanning.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (isTopLevel) {
                TopAppBar(
                    title = { Text(currentTab!!.label.uppercase()) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { nav.navigate("places") }) {
                            Icon(Icons.Default.Place, contentDescription = "Places")
                        }
                        IconButton(onClick = { nav.navigate("rarebirds") }) {
                            Icon(Icons.Default.Radar, contentDescription = "Field Guide")
                        }
                        IconButton(onClick = { nav.navigate("drives") }) {
                            Icon(Icons.Default.History, contentDescription = "Drives")
                        }
                        IconButton(onClick = { nav.navigate("stats") }) {
                            Icon(Icons.Default.Insights, contentDescription = "Stats & Data")
                        }
                        IconButton(onClick = { nav.navigate("sensors") }) {
                            Icon(Icons.Default.SatelliteAlt, contentDescription = "Sensors & Sky")
                        }
                        IconButton(onClick = { nav.navigate("live") }) {
                            Icon(Icons.Default.Terminal, contentDescription = "Live data")
                        }
                        IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == "fleet") {
                ExtendedFloatingActionButton(
                    onClick = { vm.toggleScan() },
                    icon = {
                        Icon(
                            if (scanning) Icons.Default.Stop else Icons.Default.BluetoothSearching,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (scanning) "Stop" else "Scan") },
                )
            }
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(containerColor = NavigationBarDefaults.containerColor.copy(alpha = 0.92f)) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo("fleet") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        var touch by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Offset?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (!onMap) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
                                    touch = if (change != null && change.pressed) change.position else null
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            // Living constellation backdrop behind everything except the live map.
            if (!onMap) ConstellationBackground(Modifier.fillMaxSize(), touch)
            NavHost(navController = nav, startDestination = "fleet") {
                composable("fleet") {
                    FleetScreen(vm, onOpen = { address ->
                        vm.open(address)
                        nav.navigate("device")
                    })
                }
                composable("wardrive") { WardriveScreen(vm) }
                composable("wifi") { WifiScreen(vm, onOpenTool = { nav.navigate("runtool/$it") }) }
                composable("bluetooth") { BluetoothScreen(vm, onOpenTool = { nav.navigate("runtool/$it") }) }
                composable("subghz") {
                    SubGhzScreen(vm, onOpenMap = {
                        nav.navigate("wardrive") {
                            popUpTo("fleet") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    })
                }
                composable("misc") { MiscScreen(onOpenTool = { nav.navigate("runtool/$it") }) }
                composable("rarebirds") {
                    RareBirdsScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onOpenFinds = { kind -> nav.navigate("finds/$kind") },
                        onOpenStats = { nav.navigate("stats") },
                    )
                }
                composable("finds/{kind}") { entry ->
                    FindsScreen(vm = vm, kind = entry.arguments?.getString("kind") ?: "WIFI",
                        onBack = { nav.popBackStack() })
                }
                composable("places") {
                    PlacesScreen(
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onRouteStarted = {
                            nav.navigate("wardrive") {
                                popUpTo("fleet") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable("settings") {
                    SettingsScreen(vm = vm, onBack = { nav.popBackStack() },
                        onOpenDeadZones = { nav.navigate("deadzones") },
                        onOpenWigle = { nav.navigate("wiglestats") })
                }
                composable("wiglestats") { WigleStatsScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("deadzones") { DeadZonesScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("labbench") { LabBenchScreen(onBack = { nav.popBackStack() }) }
                composable("sensors") { SensorsHubScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
                composable("satellites") { SatellitesScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("aircraft") { AircraftScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("locator") { LocatorScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("trackers") { TrackerScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("crew") { CrewScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("drives") { DrivesScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("stats") { StatsScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("live") { LiveScreen(vm = vm, onBack = { nav.popBackStack() }) }
                composable("device") {
                    val address by vm.selected.collectAsStateWithLifecycle()
                    DeviceDetailScreen(vm = vm, address = address, onBack = { nav.popBackStack() })
                }
                composable("runtool/{key}") { entry ->
                    RunToolScreen(
                        vm = vm,
                        toolKey = entry.arguments?.getString("key"),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            // Global proximity alert — slides in at the top when near a mapped device
            // (Meshtastic node or camera). Doubles as a locator; taps jump to the map.
            ProximityBanner(vm) {
                if (currentRoute != "wardrive") nav.navigate("wardrive") {
                    popUpTo("fleet") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}

/**
 * Core permissions the app can't run without: location (GPS wardriving + BLE scan
 * results) and, on Android 12+, the granular Bluetooth scan/connect permissions.
 */
private fun corePermissions(): Array<String> {
    val perms = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms += Manifest.permission.BLUETOOTH_SCAN
        perms += Manifest.permission.BLUETOOTH_CONNECT
    }
    return perms.toTypedArray()
}

/** Core permissions plus the nice-to-haves (notifications) requested in the same prompt. */
private fun allRequestedPermissions(): Array<String> {
    val perms = corePermissions().toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
        perms += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    return perms.toTypedArray()
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Only the CORE set gates the app; notifications being denied must not block it.
    fun hasCore() = corePermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasCore()) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasCore() }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Permissions needed", style = MaterialTheme.typography.titleLarge)
            Text(
                "Flying Flocks needs Location (for GPS wardriving and to read nearby Wi-Fi/BLE) " +
                    "and Bluetooth (to find your boards and sense nearby devices). It never uses " +
                    "Bluetooth scans for location, and notifications are used for camera and speed alerts.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { launcher.launch(allRequestedPermissions()) },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Grant permissions") }
        }
    }
}
