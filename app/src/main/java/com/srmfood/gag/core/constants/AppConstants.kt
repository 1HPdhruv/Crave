package com.srmfood.gag.core.constants

object AppConstants {
    // API
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L

    // Auth
    const val PREF_ACCESS_TOKEN = "access_token"
    const val PREF_REFRESH_TOKEN = "refresh_token"
    const val PREF_USER_ROLE = "user_role"
    const val PREF_USER_ID = "user_id"
    const val PREF_USER_NAME = "user_name"
    const val PREF_USER_EMAIL = "user_email"
    const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"

    // Database
    const val DATABASE_NAME = "gag_database"
    const val DATABASE_VERSION = 1

    // DataStore
    const val USER_PREFERENCES = "user_preferences"

    // Cache durations (milliseconds)
    const val OUTLET_CACHE_DURATION = 5 * 60 * 1000L      // 5 min
    const val MENU_CACHE_DURATION = 10 * 60 * 1000L        // 10 min
    const val SEARCH_CACHE_DURATION = 2 * 60 * 1000L       // 2 min

    // Cart
    const val MAX_CART_QUANTITY = 10
    const val MIN_CART_QUANTITY = 1

    // Notifications
    const val NOTIFICATION_CHANNEL_ORDERS = "gag_orders"
    const val NOTIFICATION_CHANNEL_PROMOS = "gag_promos"

    // Deep links
    const val DEEP_LINK_SCHEME = "gag"
    const val DEEP_LINK_HOST_ORDERS = "orders"

    // User roles
    const val ROLE_STUDENT = "STUDENT"
    const val ROLE_VENDOR = "VENDOR"
    const val ROLE_ADMIN = "ADMIN"

    // Order statuses
    const val ORDER_CREATED = "CREATED"
    const val ORDER_PLACED = "PLACED"
    const val ORDER_ACCEPTED = "ACCEPTED"
    const val ORDER_PREPARING = "PREPARING"
    const val ORDER_READY = "READY"
    const val ORDER_PICKED_UP = "PICKED_UP"
    const val ORDER_REJECTED = "REJECTED"
    const val ORDER_CANCELLED = "CANCELLED"
    const val ORDER_EXPIRED = "EXPIRED"
    const val ORDER_REFUNDED = "REFUNDED"

    // Payment
    const val PAYMENT_PAY_AT_COUNTER = "PAY_AT_COUNTER"
    const val PAYMENT_ONLINE = "ONLINE"

    // Slot status
    const val SLOT_AVAILABLE = "AVAILABLE"
    const val SLOT_LIMITED = "LIMITED"
    const val SLOT_FULL = "FULL"

    // QR Token
    const val QR_TOKEN_EXPIRY_MINUTES = 30

    // SRM KTR
    const val CAMPUS_NAME = "SRM KTR"
    const val CAMPUS_LOCATION = "SRM Institute of Science and Technology, Kattankulathur"
    const val CAMPUS_LAT = 12.8231
    const val CAMPUS_LNG = 80.0446
}
