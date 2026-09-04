package com.srmfood.gag.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
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
import androidx.compose.ui.unit.sp
import com.srmfood.gag.core.ui.theme.BottomNavBackground
import com.srmfood.gag.core.ui.theme.BottomNavBorder
import com.srmfood.gag.core.ui.theme.GagOnSurfaceVariant
import com.srmfood.gag.core.ui.theme.GagOrange

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val studentBottomNavItems = listOf(
    BottomNavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, "home"),
    BottomNavItem("Orders", Icons.Filled.Receipt, Icons.Outlined.Receipt, "orders"),
    BottomNavItem("Favourites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "favourites"),
    BottomNavItem("Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications, "notifications"),
    BottomNavItem("Profile", Icons.Filled.Person, Icons.Outlined.Person, "profile")
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
            .background(BottomNavBackground)
            .border(
                width = 1.dp,
                color = BottomNavBorder,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = GagOnSurfaceVariant,
            tonalElevation = 0.dp,
            modifier = Modifier
                .selectableGroup()
                .navigationBarsPadding()
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val iconTint by animateColorAsState(
                    targetValue = if (selected) GagOrange else GagOnSurfaceVariant,
                    animationSpec = tween(200),
                    label = "icon_tint"
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { onItemSelected(item.route) },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(3.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                        .background(GagOrange)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
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
