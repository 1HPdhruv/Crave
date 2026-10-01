package com.srmfood.gag.feature.management

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.srmfood.gag.core.ui.component.GagBottomNavBar
import com.srmfood.gag.core.ui.component.managementBottomNavItems
import com.srmfood.gag.navigation.Screen

/**
 * CRAVE ORBIT Management Shell.
 *
 * Wraps every top-level management Orbit destination with a [Scaffold] that
 * owns the single bottom navigation bar for management operations.
 */
@Composable
fun ManagementShell(
    navController: NavController,
    currentRoute: String,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            GagBottomNavBar(
                items = managementBottomNavItems,
                currentRoute = currentRoute,
                onItemSelected = { route ->
                    navController.navigate(route) {
                        // Standard bottom-nav behavior: pop to the start destination
                        popUpTo(Screen.ManagementOverview.route) {
                            saveState = true
                            inclusive = false
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
