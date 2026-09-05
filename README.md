# 🍔 Crave (GaG) - SRMIST Food Ordering App

Welcome to the official repository for **Crave (GaG)**, a comprehensive food ordering and management application designed specifically for the SRMIST campus. 

This app streamlines the food ordering process by offering role-based access for Students, Vendors, and Administrators, providing a seamless experience from browsing menus to picking up orders.

## ✨ Features

### 👨‍🎓 For Students / Users
- **Browse Food Outlets:** Explore various food stalls and their menus available on campus.
- **Smart Cart & Checkout:** Customize food items and add them to the cart for a quick checkout.
- **Pickup Slots:** Schedule convenient pickup times to avoid long queues.
- **Live Order Tracking:** Get real-time updates on your order status.
- **Digital Tokens:** Secure QR code-based pickup tokens for easy collection.
- **Favorites & History:** Reorder your favorite meals in just a few taps.

### 🧑‍🍳 For Vendors
- **Order Management:** View, accept, and process incoming orders efficiently.
- **Menu Management:** Update food availability, variations, and prices in real-time.
- **QR Scanner:** Quickly scan student pickup tokens to verify and hand over orders.
- **Analytics Dashboard:** Track daily sales, popular items, and revenue.

### 👨‍💻 For Administrators
- **System Monitoring:** Oversee all platform activities and active outlets.
- **User Management:** Manage permissions and roles across the platform.
- **Global Settings:** Configure global pickup slots, tax rates, and policies.

## 🛠️ Tech Stack

This project is built using modern Android development practices:

- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) - fully declarative UI.
- **Language:** [Kotlin](https://kotlinlang.org/)
- **Architecture:** Clean Architecture with MVVM (Model-View-ViewModel)
- **Dependency Injection:** [Dagger-Hilt](https://dagger.dev/hilt/)
- **Backend & Database:** [Supabase](https://supabase.com/) (PostgreSQL, Realtime, Auth, Storage)
- **Local Database:** [Room](https://developer.android.com/training/data-storage/room)
- **Networking:** Retrofit / Ktor
- **Async Programming:** Kotlin Coroutines & Flow

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or latest)
- JDK 17+
- Supabase Project (with properly configured schemas and RLS policies)

### Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/1HPdhruv/Crave.git
   ```

2. **Configure Local Properties:**
   Create a `local.properties` file in the root directory and add your Supabase credentials:
   ```properties
   SUPABASE_URL="your_supabase_project_url"
   SUPABASE_ANON_KEY="your_supabase_anon_key"
   ```

3. **Run the Project:**
   Open the project in Android Studio, sync Gradle, and run the app on an emulator or physical device.

## 🗄️ Database Architecture

The backend uses a structured relational database with robust Row Level Security (RLS) policies. You can find the database migrations and policies in the `supabase/migrations/` directory. 

Key tables include:
- `profiles`, `outlets`, `categories`, `food_items`
- `orders`, `carts`, `payments`, `pickup_slots`

## 🤝 Contributing

Contributions are welcome! If you'd like to improve the app:
1. Fork the project.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---
*Built with ❤️ for SRMIST.*
