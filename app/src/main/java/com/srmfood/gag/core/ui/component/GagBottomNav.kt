package com.srmfood.gag.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.srmfood.gag.core.ui.theme.OrbitLime
import com.srmfood.gag.core.ui.theme.OrbitOnLime

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

/**
 * CRAVE ORBIT student bottom navigation.
 * Tabs: Home | Explore | Orders | Cart | Profile
 * Design: clean neutral background · thin border · lime active indicator · minimal
 */
val studentBottomNavItems = listOf(
    BottomNavItem("Discover", Icons.Filled.Home,         Icons.Outlined.Home,         "home"),
    BottomNavItem("Explore",  Icons.Filled.Explore,      Icons.Outlined.Explore,      "explore"),
    BottomNavItem("Orders",   Icons.Filled.Receipt,      Icons.Outlined.Receipt,      "orders"),
    BottomNavItem("Cart",     Icons.Filled.ShoppingCart,  Icons.Outlined.ShoppingCart, "cart"),
    BottomNavItem("Profile",  Icons.Filled.Person,        Icons.Outlined.Person,       "profile")
)

val managementBottomNavItems = listOf(
    BottomNavItem("Overview",   Icons.Filled.Dashboard,  Icons.Outlined.Dashboard,  "management/overview"),
    BottomNavItem("Orders",     Icons.Filled.Receipt,    Icons.Outlined.Receipt,    "management/orders"),
    BottomNavItem("Operations", Icons.Filled.Storefront, Icons.Outlined.Storefront, "management/operations"),
    BottomNavItem("Analytics",  Icons.Filled.BarChart,   Icons.Outlined.BarChart,   "management/analytics"),
    BottomNavItem("More",       Icons.Filled.Menu,       Icons.Outlined.Menu,       "management/more")
)

@Composable
fun GagBottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            modifier = Modifier
                .selectableGroup()
                .navigationBarsPadding()
        ) {
            items.forEach { item ->
                val baseRoute = currentRoute?.substringBefore("?")
                val selected = baseRoute == item.route
                val iconTint by animateColorAsState(
                    targetValue = if (selected) OrbitLime else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "icon_tint_${item.route}"
                )
                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) 20.dp else 0.dp,
                    animationSpec = tween(200),
                    label = "indicator_${item.route}"
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { onItemSelected(item.route) },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Top lime indicator pill when selected
                            Box(
                                modifier = Modifier
                                    .width(indicatorWidth)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(OrbitLime)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = iconTint
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}
