package com.bolohisab.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bolohisab.R
import com.bolohisab.ui.customers.CustomerDetailScreen
import com.bolohisab.ui.customers.CustomersScreen
import com.bolohisab.ui.home.HomeScreen
import com.bolohisab.ui.reports.ReportsScreen
import com.bolohisab.ui.settings.SettingsScreen
import com.bolohisab.ui.stock.StockScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object CustomersRoute
@Serializable data object ReportsRoute
@Serializable data object StockRoute
@Serializable data object SettingsRoute
@Serializable data class CustomerRoute(val id: Long)

private data class Tab(
    val route: Any,
    val label: Int,
    val icon: ImageVector,
    val matches: (NavDestination) -> Boolean,
)

private val tabs = listOf(
    Tab(HomeRoute, R.string.tab_ledger, Icons.AutoMirrored.Rounded.MenuBook) { it.hasRoute<HomeRoute>() },
    Tab(CustomersRoute, R.string.tab_customers, Icons.Rounded.Groups) { it.hasRoute<CustomersRoute>() },
    Tab(ReportsRoute, R.string.tab_reports, Icons.Rounded.Assessment) { it.hasRoute<ReportsRoute>() },
    Tab(StockRoute, R.string.tab_stock, Icons.Rounded.Inventory2) { it.hasRoute<StockRoute>() },
    Tab(SettingsRoute, R.string.tab_settings, Icons.Rounded.Settings) { it.hasRoute<SettingsRoute>() },
)

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTopLevel = tabs.any { t -> destination?.hierarchy?.any(t.matches) == true }

    // Screens draw their own top bars (which take the status-bar inset), so this outer
    // scaffold adds no insets of its own; the navigation bar takes the bottom one.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTopLevel) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any(tab.matches) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable<HomeRoute> { HomeScreen() }
            composable<CustomersRoute> { CustomersScreen(onOpen = { nav.navigate(CustomerRoute(it)) }) }
            composable<CustomerRoute> { CustomerDetailScreen(onBack = nav::popBackStack) }
            composable<ReportsRoute> { ReportsScreen() }
            composable<StockRoute> { StockScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
