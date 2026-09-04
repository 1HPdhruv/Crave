package com.srmfood.gag.core.network

import com.srmfood.gag.BuildConfig

/**
 * Central API configuration.
 * All URLs and versioning go through here.
 * Change BASE_URL only in BuildConfig/local config – never hardcode.
 */
object ApiConfig {
    val BASE_URL: String = BuildConfig.BASE_URL
    val API_VERSION: String = BuildConfig.API_VERSION

    // Computed endpoints
    val API_BASE: String = "${BASE_URL}api/$API_VERSION/"

    // Endpoint paths (relative to API_BASE)
    object Endpoints {
        // Auth
        const val LOGIN = "auth/login"
        const val REGISTER = "auth/register"
        const val REFRESH = "auth/refresh"
        const val LOGOUT = "auth/logout"
        const val ME = "auth/me"
        const val FCM_TOKEN = "auth/fcm-token"

        // Outlets
        const val OUTLETS = "outlets"
        const val OUTLET_DETAIL = "outlets/{id}"
        const val OUTLET_MENU = "outlets/{id}/menu"
        const val OUTLET_QUEUE = "outlets/{id}/queue"

        // Food
        const val FOOD_SEARCH = "food/search"
        const val FOOD_DETAIL = "food/{id}"
        const val FOOD_POPULAR = "food/popular"
        const val FOOD_RECOMMENDED = "food/recommended"
        const val FOOD_CATEGORIES = "food/categories"

        // Cart (server-side cart optional, but client-side primary)
        const val CART = "cart"
        const val CART_ITEM = "cart/items/{id}"

        // Orders
        const val ORDERS = "orders"
        const val ORDER_DETAIL = "orders/{id}"
        const val ORDER_CANCEL = "orders/{id}/cancel"
        const val ORDER_QR_TOKEN = "orders/{id}/qr-token"

        // Pickup Slots
        const val PICKUP_SLOTS = "outlets/{outletId}/slots"

        // Vendor
        const val VENDOR_ORDERS = "vendor/orders"
        const val VENDOR_ORDER_ACCEPT = "vendor/orders/{id}/accept"
        const val VENDOR_ORDER_REJECT = "vendor/orders/{id}/reject"
        const val VENDOR_ORDER_PREPARING = "vendor/orders/{id}/preparing"
        const val VENDOR_ORDER_READY = "vendor/orders/{id}/ready"
        const val VENDOR_PICKUP_CONFIRM = "vendor/orders/pickup"
        const val VENDOR_MENU = "vendor/menu"
        const val VENDOR_FOOD = "vendor/food"

        // Admin
        const val ADMIN_USERS = "admin/users"
        const val ADMIN_OUTLETS = "admin/outlets"
        const val ADMIN_VENDORS = "admin/vendors"
        const val ADMIN_ORDERS = "admin/orders"
        const val ADMIN_ANALYTICS = "admin/analytics"

        // Notifications
        const val NOTIFICATIONS = "notifications"
        const val NOTIFICATION_READ = "notifications/{id}/read"

        // Reviews
        const val REVIEWS = "outlets/{outletId}/reviews"
        const val FOOD_REVIEWS = "food/{foodId}/reviews"

        // Favorites
        const val FAVORITES = "favorites"
        const val FAVORITE_TOGGLE = "favorites/{foodId}"
    }
}
