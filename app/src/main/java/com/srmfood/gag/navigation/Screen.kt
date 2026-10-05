package com.srmfood.gag.navigation

/**
 * All navigation routes in the GaG / CRAVE ORBIT application.
 *
 * Student Orbit bottom-nav:  Home | Explore | Orders | Cart | Profile
 * Secondary destinations are pushed on top of any Orbit tab.
 * Vendor / Admin shells remain completely separate.
 */
sealed class Screen(val route: String) {

    // ─── Auth ────────────────────────────────────────────────────
    object Splash      : Screen("splash")
    object Onboarding  : Screen("onboarding")
    object Login       : Screen("login?role={role}") {
        fun createRoute(role: String = "student") = "login?role=$role"
        const val ARG_ROLE = "role"
    }
    object Register    : Screen("register")

    // ─── Student Orbit – Top-Level (bottom-nav) ──────────────────
    object Home        : Screen("home")
    object Explore     : Screen("explore?category={category}") {
        fun createRoute(category: String? = null) = if (category != null) "explore?category=$category" else "explore"
        const val ARG_CATEGORY = "category"
    }
    object OrderHistory: Screen("orders")
    object Cart        : Screen("cart")
    object Profile     : Screen("profile")

    // ─── Student Orbit – Secondary (pushed on top of a tab) ──────
    object FoodDetail  : Screen("food/{foodId}") {
        fun createRoute(foodId: String) = "food/$foodId"
        const val ARG_FOOD_ID = "foodId"
    }
    object OutletDetail : Screen("outlet/{outletId}") {
        fun createRoute(outletId: String) = "outlet/$outletId"
        const val ARG_OUTLET_ID = "outletId"
    }
    /**
     * Consolidated order detail / order confirmation screen.
     *
     * fromCheckout=true  → "Back" navigates to Home (clears Checkout from stack)
     * fromCheckout=false → "Back" pops to Orders
     */
    object OrderDetail : Screen("order/{orderId}?fromCheckout={fromCheckout}") {
        fun createRoute(orderId: String, fromCheckout: Boolean = false) =
            "order/$orderId?fromCheckout=$fromCheckout"
        const val ARG_ORDER_ID    = "orderId"
        const val ARG_FROM_CHECKOUT = "fromCheckout"
    }
    object LiveOrderTracking : Screen("track_order/{orderId}") {
        fun createRoute(orderId: String) = "track_order/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object PickupQRCode : Screen("pickup_qr/{orderId}") {
        fun createRoute(orderId: String) = "pickup_qr/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object Checkout        : Screen("checkout")
    object Favorites       : Screen("favorites")
    object Notifications   : Screen("notifications")
    object Settings        : Screen("settings")
    object HelpSupport     : Screen("help")

    // ─── Legacy / Preserved (not in new bottom-nav, kept for backwards compat) ──
    /** Full-featured search/filter screen – now the backing screen for Explore. */
    object Search : Screen("search?query={query}&category={category}") {
        fun createRoute(query: String = "", category: String? = null): String {
            val q = query.ifBlank { "" }
            return if (category != null) "search?query=$q&category=$category"
            else "search?query=$q"
        }
        const val ARG_QUERY    = "query"
        const val ARG_CATEGORY = "category"
    }
    /** Outlet list – reachable from Explore / Home "See All". */
    object OutletList : Screen("outlets")

    // ─── Vendor ──────────────────────────────────────────────────
    object VendorDashboard  : Screen("vendor/dashboard")
    object VendorReviews    : Screen("vendor/reviews")
    object VendorOrders     : Screen("vendor/orders")
    object VendorOrderDetail: Screen("vendor/order/{orderId}") {
        fun createRoute(orderId: String) = "vendor/order/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object VendorMenu       : Screen("vendor/menu")
    object VendorFoodEdit   : Screen("vendor/food/{foodId}") {
        fun createRoute(foodId: String) = "vendor/food/$foodId"
        const val ARG_FOOD_ID = "foodId"
    }
    object VendorAnalytics  : Screen("vendor/analytics")
    object VendorProfile    : Screen("vendor/profile")
    object QRScanner        : Screen("vendor/qr_scanner")
    /** Shown to PENDING_VENDOR users while awaiting admin approval. */
    object VendorPending    : Screen("vendor/pending")

    // ─── Management (Backend role: ADMIN) ────────────────────────
    object ManagementOverview   : Screen("management/overview")
    object ManagementOrders     : Screen("management/orders")
    object ManagementOrderDetail: Screen("management/order/{orderId}") {
        fun createRoute(orderId: String) = "management/order/$orderId"
        const val ARG_ORDER_ID = "orderId"
    }
    object ManagementOperations : Screen("management/operations")
    object ManagementInventoryDetail : Screen("management/inventory/{inventoryId}") {
        fun createRoute(inventoryId: String) = "management/inventory/$inventoryId"
        const val ARG_INVENTORY_ID = "inventoryId"
    }
    object ManagementPickupSlotDetail : Screen("management/pickup_slot/{slotId}") {
        fun createRoute(slotId: String) = "management/pickup_slot/$slotId"
        const val ARG_SLOT_ID = "slotId"
    }
    object ManagementAnalytics  : Screen("management/analytics")
    object ManagementContent    : Screen("management/content")
    object ManagementLiveCampus : Screen("management/live_campus")
    object ManagementMore       : Screen("management/more")
    object ManagementReviews    : Screen("management/reviews")
    object ManagementNotifications : Screen("management/notifications")
    object ManagementSupport    : Screen("management/support")
    object ManagementVendors    : Screen("management/vendors")
    object ManagementVendorDetail: Screen("management/vendor/{vendorId}") {
        fun createRoute(vendorId: String) = "management/vendor/$vendorId"
        const val ARG_VENDOR_ID = "vendorId"
    }
    object ManagementOutlets    : Screen("management/outlets")
    object ManagementOutletDetail: Screen("management/outlet/{outletId}") {
        fun createRoute(outletId: String) = "management/outlet/$outletId"
        const val ARG_OUTLET_ID = "outletId"
    }
    object ManagementMenu       : Screen("management/menu")
    object ManagementFoodDetail : Screen("management/food/{foodId}") {
        fun createRoute(foodId: String) = "management/food/$foodId"
        const val ARG_FOOD_ID = "foodId"
    }
    object ManagementPayments   : Screen("management/payments")
    object ManagementPaymentDetail: Screen("management/payment/{paymentId}") {
        fun createRoute(paymentId: String) = "management/payment/$paymentId"
        const val ARG_PAYMENT_ID = "paymentId"
    }
    object ManagementUsers      : Screen("management/users")
    object ManagementUserDetail : Screen("management/user/{userId}") {
        fun createRoute(userId: String) = "management/user/$userId"
        const val ARG_USER_ID = "userId"
    }
}
