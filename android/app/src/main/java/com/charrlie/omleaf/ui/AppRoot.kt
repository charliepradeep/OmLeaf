@file:OptIn(ExperimentalMaterial3Api::class)

package com.charrlie.omleaf.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.charrlie.omleaf.data.Repository
import com.charrlie.omleaf.ui.history.ProgressScreen
import com.charrlie.omleaf.ui.meditate.MeditationScreen
import com.charrlie.omleaf.ui.home.PracticesScreen
import com.charrlie.omleaf.ui.session.SessionScreen
import com.charrlie.omleaf.ui.settings.SettingsScreen
import com.charrlie.omleaf.ui.setup.SetupScreen
import kotlinx.coroutines.launch

private object Routes {
    const val PRACTICES = "practices"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val SETUP = "setup/{id}"
    const val MEDITATE = "meditate"
    const val SESSION = "session"
    fun setup(id: String) = "setup/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selected: ImageVector)

private val tabs = listOf(
    Tab(Routes.PRACTICES, "Practice", Icons.Outlined.SelfImprovement, Icons.Filled.SelfImprovement),
    Tab(Routes.PROGRESS, "Progress", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AppRoot(repository: Repository) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(repository))
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(if (route == tab.route) tab.selected else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.PRACTICES,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(Routes.PRACTICES) {
                PracticesScreen(vm, onOpen = { nav.navigate(Routes.setup(it)) }, onMeditate = { nav.navigate(Routes.MEDITATE) })
            }
            composable(Routes.MEDITATE) {
                MeditationScreen(vm = vm, onBegin = { nav.navigate(Routes.SESSION) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.PROGRESS) { ProgressScreen(vm) }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
            composable(Routes.SETUP, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                SetupScreen(
                    vm = vm,
                    practiceId = it.arguments?.getString("id").orEmpty(),
                    onBegin = { nav.navigate(Routes.SESSION) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SESSION) {
                val spec = vm.pending
                if (spec == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    SessionScreen(vm = vm, spec = spec, onClose = { vm.pending = null; nav.popBackStack() })
                }
            }
        }
    }

    // A short health note the first time the app opens. Shown only after settings have loaded.
    val loaded = settings
    if (loaded != null && !loaded.acknowledgedNote) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Practise gently") },
            text = {
                Text(
                    "Breathing practices are not a medical treatment. Never strain, and stop if you feel dizzy or unwell. " +
                        "If you are pregnant, have a heart condition, high blood pressure, epilepsy or another medical " +
                        "condition, check with your doctor before practising, especially the forceful practices.",
                )
            },
            confirmButton = { TextButton(onClick = { scope.launch { repository.acknowledgeNote() } }) { Text("I understand") } },
        )
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
