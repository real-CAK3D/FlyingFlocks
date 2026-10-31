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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cak3d.biscuitmesh.ui.BluetoothScreen
import com.cak3d.biscuitmesh.ui.DeviceDetailScreen
import com.cak3d.biscuitmesh.ui.FleetScreen
import com.cak3d.biscuitmesh.ui.MiscScreen
import com.cak3d.biscuitmesh.ui.RunToolScreen
import com.cak3d.biscuitmesh.ui.WardriveScreen
import com.cak3d.biscuitmesh.ui.WifiScreen
import com.cak3d.biscuitmesh.ui.theme.BiscuitMeshTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiscuitMeshTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val vm: MainViewModel = viewModel()
                    PermissionGate { MainShell(vm) }
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("fleet", "Fleet", Icons.Default.Dashboard),
    Tab("wardrive", "Wardrive", Icons.Default.Map),
    Tab("wifi", "WiFi", Icons.Default.Wifi),
    Tab("bluetooth", "Bluetooth", Icons.Default.Bluetooth),
    Tab("misc", "Misc", Icons.Default.Apps),
)

@Composable
private fun MainShell(vm: MainViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TABS.any { it.route == currentRoute }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(navController = nav, startDestination = "fleet") {
                composable("fleet") {
                    FleetScreen(vm, onOpen = { address ->
                        vm.open(address)
                        nav.navigate("device")
                    })
                }
                composable("wardrive") { WardriveScreen(vm) }
                composable("wifi") { WifiScreen(onOpenTool = { nav.navigate("runtool/$it") }) }
                composable("bluetooth") { BluetoothScreen(onOpenTool = { nav.navigate("runtool/$it") }) }
                composable("misc") { MiscScreen(onOpenTool = { nav.navigate("runtool/$it") }) }
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
        }
        if (showBottomBar) {
            NavigationBar {
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
    }
}

/** Required BLE permissions differ across Android versions. */
private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun hasAll() = requiredPermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasAll()) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> granted = result.values.all { it } || hasAll() }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Bluetooth permission needed", style = MaterialTheme.typography.titleLarge)
            Text(
                "Biscuit Mesh uses Bluetooth to find and manage your boards. " +
                    "It never uses Bluetooth scans for location.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { launcher.launch(requiredPermissions()) },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Grant permission") }
        }
    }
}
