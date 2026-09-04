package com.srmfood.gag.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.srmfood.gag.core.ui.component.GagLoadingScreen
import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.feature.auth.login.LoginScreen
import com.srmfood.gag.feature.auth.onboarding.OnboardingScreen
import com.srmfood.gag.feature.auth.register.RegisterScreen
import com.srmfood.gag.feature.auth.splash.SplashViewModel
import com.srmfood.gag.feature.cart.CartScreen
import com.srmfood.gag.feature.checkout.CheckoutScreen
import com.srmfood.gag.feature.checkout.CheckoutViewModel
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
import com.srmfood.gag.feature.search.SearchScreen
import com.srmfood.gag.feature.vendor.dashboard.VendorDashboardScreen
import com.srmfood.gag.feature.vendor.orders.VendorOrderDetailScreen
import com.srmfood.gag.feature.vendor.orders.VendorOrdersScreen
import com.srmfood.gag.feature.vendor.menu.VendorMenuScreen
import com.srmfood.gag.feature.vendor.scanner.QRScannerScreen
import com.srmfood.gag.feature.vendor.analytics.VendorAnalyticsScreen
import com.srmfood.gag.feature.admin.dashboard.AdminDashboardScreen

/**
 * Root navigation graph.
 * Determines start destination based on session state.
 * Delegates to role-specific sub-graphs after authentication.
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
        !splashState.isLoggedIn -> Screen.Login.createRoute()
        else -> when (splashState.userRole) {
            UserRole.VENDOR -> Screen.VendorDashboard.route
            UserRole.ADMIN -> Screen.AdminDashboard.route
            else -> Screen.Home.route
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally { it } + fadeIn() },
        exitTransition = { slideOutHorizontally { -it } + fadeOut() },
        popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it } + fadeOut() }
    ) {
        // ─── Auth ─────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = { navController.navigate(Screen.Login.createRoute()) { popUpTo(Screen.Onboarding.route) { inclusive = true } } }
            )
        }
        composable(
            route = Screen.Login.route,
            arguments = listOf(navArgument(Screen.Login.ARG_ROLE) { type = NavType.StringType; defaultValue = "student" })
        ) {
            LoginScreen(
                onLoginSuccess = { role ->
                    val dest = when (role) {
                        UserRole.VENDOR -> Screen.VendorDashboard.route
                        UserRole.ADMIN -> Screen.AdminDashboard.route
                        else -> Screen.Home.route
                    }
                    navController.navigate(dest) { popUpTo(Screen.Login.route) { inclusive = true } }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Register.route) { inclusive = true } } },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        // ─── Student ─────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onOutletClick = { navController.navigate(Screen.OutletDetail.createRoute(it)) },
                onFoodClick = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
                onCartClick = { navController.navigate(Screen.Cart.route) },
                onNavigateBottom = { route -> navController.navigate(route) { launchSingleTop = true; popUpTo(Screen.Home.route) { saveState = true }; restoreState = true } }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onFoodClick = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
                onOutletClick = { navController.navigate(Screen.OutletDetail.createRoute(it)) },
                onAddToCart = { navController.navigate(Screen.Cart.route) }
            )
        }
        composable(Screen.OutletList.route) {
            OutletListScreen(
                onBack = { navController.popBackStack() },
                onOutletClick = { navController.navigate(Screen.OutletDetail.createRoute(it)) }
            )
        }
        composable(
            route = Screen.OutletDetail.route,
            arguments = listOf(navArgument(Screen.OutletDetail.ARG_OUTLET_ID) { type = NavType.StringType })
        ) {
            OutletDetailScreen(
                onBack = { navController.popBackStack() },
                onFoodClick = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
                onCartClick = { navController.navigate(Screen.Cart.route) }
            )
        }
        composable(
            route = Screen.FoodDetail.route,
            arguments = listOf(navArgument(Screen.FoodDetail.ARG_FOOD_ID) { type = NavType.StringType })
        ) {
            FoodDetailScreen(
                onBack = { navController.popBackStack() },
                onCartClick = { navController.navigate(Screen.Cart.route) }
            )
        }
        composable(Screen.Cart.route) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onCheckout = { navController.navigate(Screen.Checkout.route) },
                onBrowseFood = { navController.navigate(Screen.Home.route) }
            )
        }
        composable(Screen.Checkout.route) { entry ->
            val checkoutViewModel: CheckoutViewModel = hiltViewModel(entry)
            
            CheckoutScreen(
                onBack = { navController.popBackStack() },
                onOrderPlaced = { orderId -> navController.navigate(Screen.OrderConfirmation.createRoute(orderId)) { popUpTo(Screen.Home.route) } },
                viewModel = checkoutViewModel
            )
        }
        composable(
            route = Screen.OrderConfirmation.route,
            arguments = listOf(navArgument(Screen.OrderConfirmation.ARG_ORDER_ID) { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString(Screen.OrderConfirmation.ARG_ORDER_ID) ?: ""
            OrderDetailScreen(
                orderId = orderId,
                onBack = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } },
                onTrackOrder = { navController.navigate(Screen.LiveOrderTracking.createRoute(it)) },
                onShowQR = { navController.navigate(Screen.PickupQRCode.createRoute(it)) }
            )
        }
        composable(
            route = Screen.LiveOrderTracking.route,
            arguments = listOf(navArgument(Screen.LiveOrderTracking.ARG_ORDER_ID) { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString(Screen.LiveOrderTracking.ARG_ORDER_ID) ?: ""
            LiveOrderTrackingScreen(
                orderId = orderId,
                onBack = { navController.popBackStack() },
                onShowQR = { navController.navigate(Screen.PickupQRCode.createRoute(it)) }
            )
        }
        composable(
            route = Screen.PickupQRCode.route,
            arguments = listOf(navArgument(Screen.PickupQRCode.ARG_ORDER_ID) { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString(Screen.PickupQRCode.ARG_ORDER_ID) ?: ""
            PickupQRCodeScreen(orderId = orderId, onBack = { navController.popBackStack() })
        }
        composable(Screen.OrderHistory.route) {
            OrderHistoryScreen(
                onBack = { navController.popBackStack() },
                onOrderClick = { navController.navigate(Screen.OrderDetail.createRoute(it)) },
                onNavigateBottom = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        }
        composable(
            route = Screen.OrderDetail.route,
            arguments = listOf(navArgument(Screen.OrderDetail.ARG_ORDER_ID) { type = NavType.StringType })
        ) { entry ->
            val orderId = entry.arguments?.getString(Screen.OrderDetail.ARG_ORDER_ID) ?: ""
            OrderDetailScreen(
                orderId = orderId,
                onBack = { navController.popBackStack() },
                onTrackOrder = { navController.navigate(Screen.LiveOrderTracking.createRoute(it)) },
                onShowQR = { navController.navigate(Screen.PickupQRCode.createRoute(it)) }
            )
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onFoodClick = { navController.navigate(Screen.FoodDetail.createRoute(it)) },
                onNavigateBottom = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        }
        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOrderClick = { navController.navigate(Screen.OrderDetail.createRoute(it)) },
                onNavigateBottom = { route -> navController.navigate(route) { launchSingleTop = true } }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBottom = { route -> navController.navigate(route) { launchSingleTop = true } },
                onLogoutSuccess = { navController.navigate(Screen.Login.createRoute()) { popUpTo(0) { inclusive = true } } },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToHelp = { navController.navigate(Screen.HelpSupport.route) },
                onNavigateToOrders = { navController.navigate(Screen.OrderHistory.route) },
                onNavigateToFavorites = { navController.navigate(Screen.Favorites.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.HelpSupport.route) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        // ─── Vendor ───────────────────────────────────────────────
        composable(Screen.VendorDashboard.route) {
            VendorDashboardScreen(
                onOrderClick = { navController.navigate(Screen.VendorOrderDetail.createRoute(it)) },
                onScanQR = { navController.navigate(Screen.QRScanner.route) },
                onAllOrders = { navController.navigate(Screen.VendorOrders.route) },
                onMenu = { navController.navigate(Screen.VendorMenu.route) },
                onLogout = { navController.navigate(Screen.Login.createRoute("vendor")) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Screen.VendorOrders.route) {
            VendorOrdersScreen(
                onBack = { navController.popBackStack() },
                onOrderClick = { navController.navigate(Screen.VendorOrderDetail.createRoute(it)) }
            )
        }
        composable(
            route = Screen.VendorOrderDetail.route,
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

        // ─── Admin ────────────────────────────────────────────────
        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(
                onLogout = { navController.navigate(Screen.Login.createRoute("admin")) { popUpTo(0) { inclusive = true } } }
            )
        }
    }
}
