package com.srmfood.gag.navigation

/**
 * All navigation routes in the GaG application.
 * Uses sealed classes for type safety.
 */
sealed class Screen(val route: String) {

    // ─── Auth ────────────────────────────────────────────────────
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login?role={role}") {
        fun createRoute(role: String = "student") = "login?role=$role"
        const val ARG_ROLE = "role"
    }
    object Register : Screen("register")

    // ─── Student ─────────────────────────────────────────────────
    object Home : Screen("home")
    object Search : Screen("search?query={query}") {
        fun createRoute(query: String = "") = "search?query=$query"
        const val ARG_QUERY = "query"
    }
    object SearchResults : Screen("search_results?query={query}") {
        fun createRoute(query: String) = "search_results?query=$query"
        const val ARG_QUERY = "query"
    }
    object OutletList : Screen("outlets")
    object OutletDetail : Screen("outlet/{outletId}") {
        fun createRoute(outletId: String) = "outlet/$outletId"
        const val ARG_OUTLET_ID = "outletId"
    }
    object FoodDetail : Screen("food/{foodId}") {
        fun createRoute(foodId: String) = "food/$foodId"
        const val ARG_FOOD_ID = "foodId"
    }
    object Cart : Screen("cart")
    object Checkout : Screen("checkout")
    object OrderConfirmation : Screen("order_confirmation/{orderId}") {
        fun createRoute(orderId: String) = "order_confirmation/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object LiveOrderTracking : Screen("track_order/{orderId}") {
        fun createRoute(orderId: String) = "track_order/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object PickupQRCode : Screen("pickup_qr/{orderId}") {
        fun createRoute(orderId: String) = "pickup_qr/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object OrderHistory : Screen("orders")
    object OrderDetail : Screen("order/{orderId}") {
        fun createRoute(orderId: String) = "order/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object Favorites : Screen("favourites")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object HelpSupport : Screen("help")

    // ─── Vendor ──────────────────────────────────────────────────
    object VendorDashboard : Screen("vendor/dashboard")
    object VendorOrders : Screen("vendor/orders")
    object VendorOrderDetail : Screen("vendor/order/{orderId}") {
        fun createRoute(orderId: String) = "vendor/order/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object VendorMenu : Screen("vendor/menu")
    object VendorFoodEdit : Screen("vendor/food/{foodId}") {
        fun createRoute(foodId: String) = "vendor/food/$foodId"
        const val ARG_FOOD_ID = "foodId"
    }
    object VendorAnalytics : Screen("vendor/analytics")
    object VendorProfile : Screen("vendor/profile")
    object QRScanner : Screen("vendor/qr_scanner")

    // ─── Admin ───────────────────────────────────────────────────
    object AdminDashboard : Screen("admin/dashboard")
    object AdminUsers : Screen("admin/users")
    object AdminVendors : Screen("admin/vendors")
    object AdminOutlets : Screen("admin/outlets")
    object AdminOrders : Screen("admin/orders")
    object AdminAnalytics : Screen("admin/analytics")
    object AdminSettings : Screen("admin/settings")
}
