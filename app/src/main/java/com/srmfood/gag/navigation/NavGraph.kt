package com.srmfood.gag.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.srmfood.gag.core.ui.component.GagBottomNavBar
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.core.ui.component.studentBottomNavItems
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.feature.auth.login.LoginScreen
import com.srmfood.gag.feature.auth.onboarding.OnboardingScreen
import com.srmfood.gag.feature.auth.register.RegisterScreen
import com.srmfood.gag.feature.auth.splash.SplashViewModel
import com.srmfood.gag.feature.cart.CartScreen
import com.srmfood.gag.feature.checkout.CheckoutScreen
import com.srmfood.gag.feature.checkout.CheckoutViewModel
import com.srmfood.gag.feature.explore.ExploreScreen
import com.srmfood.gag.feature.favorites.FavoritesScreen
import com.srmfood.gag.feature.food.FoodDetailScreen
import com.srmfood.gag.feature.home.HomeScreen
import com.srmfood.gag.feature.notifications.NotificationsScreen
import com.srmfood.gag.feature.orders.LiveOrderTrackingScreen
import com.srmfood.gag.feature.orders.OrderDetailScreen
import com.srmfood.gag.feature.orders.OrderHistoryScreen
import com.srmfood.gag.feature.outlets.OutletDetailScreen
import com.srmfood.gag.feature.outlets.OutletListScreen
import com.srmfood.gag.feature.pickup.PickupQRCodeScreen
import com.srmfood.gag.feature.profile.HelpScreen
import com.srmfood.gag.feature.profile.ProfileScreen
import com.srmfood.gag.feature.profile.SettingsScreen
import com.srmfood.gag.feature.vendor.dashboard.VendorDashboardScreen
import com.srmfood.gag.feature.vendor.orders.VendorOrderDetailScreen
import com.srmfood.gag.feature.vendor.orders.VendorOrdersScreen
import com.srmfood.gag.feature.vendor.menu.VendorMenuScreen
import com.srmfood.gag.feature.vendor.scanner.QRScannerScreen
import com.srmfood.gag.feature.vendor.analytics.VendorAnalyticsScreen
import com.srmfood.gag.feature.vendor.pending.VendorPendingScreen
import com.srmfood.gag.feature.management.ManagementOverviewScreen
import com.srmfood.gag.feature.management.ManagementShell
import com.srmfood.gag.feature.management.ManagementOrdersScreen
import com.srmfood.gag.feature.management.ManagementOrderDetailScreen
import com.srmfood.gag.feature.management.ManagementVendorsScreen
import com.srmfood.gag.feature.management.ManagementVendorDetailScreen
import com.srmfood.gag.feature.management.ManagementOutletsScreen
import com.srmfood.gag.feature.management.ManagementOutletDetailScreen
import com.srmfood.gag.feature.management.ManagementMenuScreen
import com.srmfood.gag.feature.management.ManagementFoodDetailScreen

// ─── Top-level Orbit routes that own the bottom navigation bar ────────────────
private val ORBIT_TOP_LEVEL_ROUTES = setOf(
    Screen.Home.route,
    Screen.Explore.route,
    Screen.OrderHistory.route,
    Screen.Cart.route,
    Screen.Profile.route,
)

/**
 * Root navigation graph.
 * Determines start destination based on session state.
 * Delegates to role-specific destinations after authentication.
 *
 * Student destinations are wrapped in [StudentShell], which owns the single
 * CRAVE ORBIT bottom navigation bar.  Individual screens must NOT render
 * their own bottom bars.
 */
@Composable
fun GagNavGraph() {
    val navController = rememberNavController()
    val splashViewModel: SplashViewModel = hiltViewModel()
    val splashState by splashViewModel.splashState.collectAsState()

    if (!splashState.isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GagLoadingScreen()
        }
        return
    }

    val startDestination = when {
        !splashState.onboardingComplete -> Screen.Onboarding.route
        !splashState.isLoggedIn         -> Screen.Login.createRoute()
        else -> when (splashState.userRole) {
            UserRole.VENDOR         -> Screen.VendorDashboard.route
            UserRole.ADMIN          -> Screen.ManagementOverview.route
            UserRole.PENDING_VENDOR -> Screen.VendorPending.route
            else                    -> Screen.Home.route
        }
    }

    NavHost(
        navController       = navController,
        startDestination    = startDestination,
        enterTransition     = { slideInHorizontally { it } + fadeIn() },
        exitTransition      = { slideOutHorizontally { -it } + fadeOut() },
        popEnterTransition  = { slideInHorizontally { -it } + fadeIn() },
        popExitTransition   = { slideOutHorizontally { it } + fadeOut() }
    ) {
        // ─── Auth ─────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Login.createRoute()) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route     = Screen.Login.route,
            arguments = listOf(navArgument(Screen.Login.ARG_ROLE) {
                type         = NavType.StringType
                defaultValue = "student"
            })
        ) {
            LoginScreen(
                onLoginSuccess = { role ->
                    val dest = when (role) {
                        UserRole.VENDOR -> Screen.VendorDashboard.route
                        UserRole.ADMIN  -> Screen.ManagementOverview.route
                        else            -> Screen.Home.route
                    }
                    navController.navigate(dest) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        // ─── Student Orbit Shell (owns the bottom nav) ────────────
        // Every top-level student destination is a composable wrapped inside
        // StudentShell so they all share a single bottom nav instance.

        composable(Screen.Home.route) {
            StudentShell(navController = navController, currentRoute = Screen.Home.route) {
                HomeScreen(
                    onSearchClick    = { navController.navigate(Screen.Explore.createRoute()) {
                        launchSingleTop = true
                    }},
                    onCategoryClick  = { navController.navigate(Screen.Explore.createRoute(it)) {
                        launchSingleTop = true
                    }},
                    onOutletClick    = { navController.navigate(Screen.OutletDetail.createRoute(it)) },
                    onFoodClick      = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
                    onCartClick      = { navController.navigate(Screen.Cart.route) {
                        launchSingleTop = true; popUpTo(Screen.Home.route) { saveState = true }; restoreState = true
                    }},
                    onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                    onOrdersClick    = { navController.navigate(Screen.OrderHistory.route) {
                        launchSingleTop = true; popUpTo(Screen.Home.route) { saveState = true }; restoreState = true
                    }},
                    onSeeAllOutlets  = { navController.navigate(Screen.OutletList.route) },
                )
            }
        }

        composable(
            route = Screen.Explore.route,
            arguments = listOf(navArgument(Screen.Explore.ARG_CATEGORY) { type = NavType.StringType; nullable = true })
        ) {
            StudentShell(navController = navController, currentRoute = Screen.Explore.route) {
                ExploreScreen(
                    onFoodClick   = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
                    onOutletClick = { navController.navigate(Screen.OutletDetail.createRoute(it)) },
                    onAddToCart   = { navController.navigate(Screen.Cart.route) {
                        launchSingleTop = true
                    }},
                )
            }
        }

        composable(Screen.OrderHistory.route) {
            StudentShell(navController = navController, currentRoute = Screen.OrderHistory.route) {
                OrderHistoryScreen(
                    onOrderClick   = { navController.navigate(Screen.OrderDetail.createRoute(it, fromCheckout = false)) },
                    onBrowseFood   = { navController.navigate(Screen.Home.route) {
                        launchSingleTop = true; popUpTo(Screen.Home.route) { inclusive = false }
                    }},
                )
            }
        }

        composable(Screen.Cart.route) {
            StudentShell(navController = navController, currentRoute = Screen.Cart.route) {
                CartScreen(
                    onBack        = { navController.popBackStack() },
                    onCheckout    = { navController.navigate(Screen.Checkout.route) },
                    onBrowseFood  = { navController.navigate(Screen.Home.route) {
                        launchSingleTop = true
                    }},
                )
            }
        }

        composable(Screen.Profile.route) {
            StudentShell(navController = navController, currentRoute = Screen.Profile.route) {
                ProfileScreen(
                    onLogoutSuccess       = {
                        navController.navigate(Screen.Login.createRoute()) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToSettings  = { navController.navigate(Screen.Settings.route) },
                    onNavigateToHelp      = { navController.navigate(Screen.HelpSupport.route) },
                    onNavigateToOrders    = { navController.navigate(Screen.OrderHistory.route) {
                        launchSingleTop = true; popUpTo(Screen.Home.route) { saveState = true }; restoreState = true
                    }},
                    onNavigateToFavorites = { navController.navigate(Screen.Favorites.route) },
                    onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                )
            }
        }

        // ─── Student Secondary Destinations ───────────────────────
        // These are pushed on top of any Orbit tab; the shell's bottom nav
        // is NOT shown on these routes (ORBIT_TOP_LEVEL_ROUTES excludes them).

        composable(Screen.OutletList.route) {
            OutletListScreen(
                onBack        = { navController.popBackStack() },
                onOutletClick = { navController.navigate(Screen.OutletDetail.createRoute(it)) }
            )
        }

        composable(
            route     = Screen.OutletDetail.route,
            arguments = listOf(navArgument(Screen.OutletDetail.ARG_OUTLET_ID) { type = NavType.StringType })
        ) {
            OutletDetailScreen(
                onBack        = { navController.popBackStack() },
                onFoodClick   = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
                onCartClick   = { navController.navigate(Screen.Cart.route) {
                    launchSingleTop = true
                }}
            )
        }

        composable(
            route     = Screen.FoodDetail.route,
            arguments = listOf(navArgument(Screen.FoodDetail.ARG_FOOD_ID) { type = NavType.StringType })
        ) {
            FoodDetailScreen(
                onBack      = { navController.popBackStack() },
                onCartClick = { navController.navigate(Screen.Cart.route) {
                    launchSingleTop = true
                }}
            )
        }

        composable(Screen.Checkout.route) { entry ->
            val checkoutViewModel: CheckoutViewModel = hiltViewModel(entry)
            CheckoutScreen(
                onBack       = { navController.popBackStack() },
                onOrderPlaced = { orderId ->
                    navController.navigate(Screen.OrderDetail.createRoute(orderId, fromCheckout = true)) {
                        // Pop back to Home so pressing back from Order Confirmation
                        // does not return the user to Checkout / Payment state.
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                viewModel = checkoutViewModel
            )
        }

        composable(
            route     = Screen.OrderDetail.route,
            arguments = listOf(
                navArgument(Screen.OrderDetail.ARG_ORDER_ID) { type = NavType.StringType },
                navArgument(Screen.OrderDetail.ARG_FROM_CHECKOUT) {
                    type         = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            val orderId      = entry.arguments?.getString(Screen.OrderDetail.ARG_ORDER_ID) ?: ""
            val fromCheckout = entry.arguments?.getBoolean(Screen.OrderDetail.ARG_FROM_CHECKOUT) ?: false
            OrderDetailScreen(
                orderId      = orderId,
                fromCheckout = fromCheckout,
                onBack       = {
                    if (fromCheckout) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onTrackOrder = { navController.navigate(Screen.LiveOrderTracking.createRoute(it)) },
                onShowQR     = { navController.navigate(Screen.PickupQRCode.createRoute(it)) },
            )
        }

        composable(
            route     = Screen.LiveOrderTracking.route,
            arguments = listOf(navArgument(Screen.LiveOrderTracking.ARG_ORDER_ID) { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString(Screen.LiveOrderTracking.ARG_ORDER_ID) ?: ""
            LiveOrderTrackingScreen(
                orderId   = orderId,
                onBack    = { navController.popBackStack() },
                onShowQR  = { navController.navigate(Screen.PickupQRCode.createRoute(it)) }
            )
        }

        composable(
            route     = Screen.PickupQRCode.route,
            arguments = listOf(navArgument(Screen.PickupQRCode.ARG_ORDER_ID) { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString(Screen.PickupQRCode.ARG_ORDER_ID) ?: ""
            PickupQRCodeScreen(orderId = orderId, onBack = { navController.popBackStack() })
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onBack      = { navController.popBackStack() },
                onFoodClick = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onBack       = { navController.popBackStack() },
                onOrderClick = { navController.navigate(Screen.OrderDetail.createRoute(it, fromCheckout = false)) },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.HelpSupport.route) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        // ─── Vendor Shell ─────────────────────────────────────────
        composable(Screen.VendorDashboard.route) {
            VendorDashboardScreen(
                onOrderClick = { navController.navigate(Screen.VendorOrderDetail.createRoute(it)) },
                onScanQR     = { navController.navigate(Screen.QRScanner.route) },
                onAllOrders  = { navController.navigate(Screen.VendorOrders.route) },
                onMenu       = { navController.navigate(Screen.VendorMenu.route) },
                onLogout     = {
                    navController.navigate(Screen.Login.createRoute("vendor")) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.VendorOrders.route) {
            VendorOrdersScreen(
                onBack       = { navController.popBackStack() },
                onOrderClick = { navController.navigate(Screen.VendorOrderDetail.createRoute(it)) }
            )
        }
        composable(
            route     = Screen.VendorOrderDetail.route,
            arguments = listOf(navArgument(Screen.VendorOrderDetail.ARG_ORDER_ID) { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString(Screen.VendorOrderDetail.ARG_ORDER_ID) ?: ""
            VendorOrderDetailScreen(orderId = orderId, onBack = { navController.popBackStack() })
        }
        composable(Screen.VendorMenu.route) {
            VendorMenuScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.QRScanner.route) {
            QRScannerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.VendorAnalytics.route) {
            VendorAnalyticsScreen(onBack = { navController.popBackStack() })
        }

        // ─── Vendor Pending ───────────────────────────────────────
        composable(Screen.VendorPending.route) {
            VendorPendingScreen(
                onLogout = {
                    navController.navigate(Screen.Login.createRoute()) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ─── Management Shell ─────────────────────────────────────────
        composable(Screen.ManagementOverview.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementOverview.route) {
                ManagementOverviewScreen()
            }
        }
        composable(Screen.ManagementOrders.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementOrders.route) {
                ManagementOrdersScreen(
                    onNavigateToOrderDetail = { orderId ->
                        navController.navigate(Screen.ManagementOrderDetail.createRoute(orderId))
                    }
                )
            }
        }
        composable(Screen.ManagementOrderDetail.route) {
            // Note: Detail screen doesn't get the ManagementShell bottom nav
            ManagementOrderDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementOperations.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementOperations.route) {
                com.srmfood.gag.feature.management.ManagementOperationsScreen(
                    onNavigateToInventoryDetail = { inventoryId ->
                        navController.navigate(Screen.ManagementInventoryDetail.createRoute(inventoryId))
                    },
                    onNavigateToSlotDetail = { slotId ->
                        navController.navigate(Screen.ManagementPickupSlotDetail.createRoute(slotId))
                    }
                )
            }
        }
        composable(
            route = Screen.ManagementInventoryDetail.route,
            arguments = listOf(navArgument(Screen.ManagementInventoryDetail.ARG_INVENTORY_ID) { type = NavType.StringType })
        ) { entry ->
            val inventoryId = entry.arguments?.getString(Screen.ManagementInventoryDetail.ARG_INVENTORY_ID) ?: ""
            com.srmfood.gag.feature.management.ManagementInventoryDetailScreen(
                inventoryId = inventoryId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.ManagementPickupSlotDetail.route,
            arguments = listOf(navArgument(Screen.ManagementPickupSlotDetail.ARG_SLOT_ID) { type = NavType.StringType })
        ) { entry ->
            val slotId = entry.arguments?.getString(Screen.ManagementPickupSlotDetail.ARG_SLOT_ID) ?: ""
            com.srmfood.gag.feature.management.ManagementPickupSlotDetailScreen(
                slotId = slotId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementAnalytics.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementAnalytics.route) {
                com.srmfood.gag.feature.management.ManagementAnalyticsScreen()
            }
        }
        composable(Screen.ManagementVendors.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementMore.route) {
                ManagementVendorsScreen(
                    onNavigateToVendorDetail = { vendorId ->
                        navController.navigate(Screen.ManagementVendorDetail.createRoute(vendorId))
                    }
                )
            }
        }
        composable(Screen.ManagementVendorDetail.route) {
            ManagementVendorDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementOutlets.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementMore.route) {
                ManagementOutletsScreen(
                    onNavigateToOutletDetail = { outletId ->
                        navController.navigate(Screen.ManagementOutletDetail.createRoute(outletId))
                    }
                )
            }
        }
        composable(Screen.ManagementOutletDetail.route) {
            ManagementOutletDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementMenu.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementMore.route) {
                ManagementMenuScreen(
                    onNavigateToFoodDetail = { foodId ->
                        navController.navigate(Screen.ManagementFoodDetail.createRoute(foodId))
                    }
                )
            }
        }
        composable(Screen.ManagementFoodDetail.route) {
            ManagementFoodDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementPayments.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementMore.route) {
                com.srmfood.gag.feature.management.ManagementPaymentsScreen(
                    onNavigateToPaymentDetail = { paymentId ->
                        navController.navigate(Screen.ManagementPaymentDetail.createRoute(paymentId))
                    }
                )
            }
        }
        composable(Screen.ManagementPaymentDetail.route) {
            com.srmfood.gag.feature.management.ManagementPaymentDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementUsers.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementMore.route) {
                com.srmfood.gag.feature.management.ManagementUsersScreen(
                    onNavigateToUserDetail = { userId ->
                        navController.navigate(Screen.ManagementUserDetail.createRoute(userId))
                    }
                )
            }
        }
        composable(Screen.ManagementUserDetail.route) {
            com.srmfood.gag.feature.management.ManagementUserDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementMore.route) {
            ManagementShell(navController = navController, currentRoute = Screen.ManagementMore.route) {
                // Placeholder for Settings/Logout
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementVendors.route)
                        }) {
                            androidx.compose.material3.Text("Manage Vendors")
                        }
                        
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementOutlets.route)
                        }) {
                            androidx.compose.material3.Text("Manage Outlets")
                        }
                        
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementMenu.route)
                        }) {
                            androidx.compose.material3.Text("Manage Menu")
                        }
                        
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementPayments.route)
                        }) {
                            androidx.compose.material3.Text("Manage Payments")
                        }
                        
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementUsers.route)
                        }) {
                            androidx.compose.material3.Text("Manage Users")
                        }
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementSupport.route)
                        }) {
                            androidx.compose.material3.Text("Support")
                        }
                        
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementLiveCampus.route)
                        }) {
                            androidx.compose.material3.Text("Live Campus")
                        }
                        
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementContent.route)
                        }) {
                            androidx.compose.material3.Text("Content & Metadata")
                        }
                        
                        androidx.compose.material3.Button(onClick = {
                            navController.navigate(Screen.ManagementNotifications.route)
                        }) {
                            androidx.compose.material3.Text("Notifications")
                        }
                        
                        androidx.compose.material3.Button(
                            onClick = {
                                navController.navigate(Screen.Login.createRoute("admin")) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            androidx.compose.material3.Text("Logout")
                        }
                    }
                }
            }
        }
        composable(Screen.ManagementSupport.route) {
            com.srmfood.gag.feature.management.ManagementSupportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementLiveCampus.route) {
            com.srmfood.gag.feature.management.ManagementLiveCampusScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementContent.route) {
            com.srmfood.gag.feature.management.ManagementContentScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ManagementNotifications.route) {
            com.srmfood.gag.feature.management.ManagementNotificationsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * CRAVE ORBIT Student Shell.
 *
 * Wraps every top-level student Orbit destination with a [Scaffold] that
 * owns the single bottom navigation bar.  Secondary destinations (FoodDetail,
 * Checkout, etc.) are NOT wrapped in this shell; they navigate on top of the
 * main NavHost and never show a bottom bar.
 *
 * @param navController  The root NavController shared by the whole graph.
 * @param currentRoute   The route key of the currently active top-level tab,
 *                       used to highlight the correct bottom-nav item.
 * @param content        The tab's screen composable, rendered inside the scaffold.
 */
@Composable
private fun StudentShell(
    navController: NavController,
    currentRoute: String,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            GagBottomNavBar(
                items         = studentBottomNavItems,
                currentRoute  = currentRoute,
                onItemSelected = { route ->
                    navController.navigate(route) {
                        // Standard bottom-nav behavior: pop to the start destination
                        // of the graph, save & restore state for each tab.
                        popUpTo(Screen.Home.route) {
                            saveState    = true
                            inclusive    = false
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
