# Crave — Campus Food Ordering App

**Crave** is a campus-focused food ordering and management application built for the SRMIST community. It brings students, food vendors, and administrators together in one platform, making it easier to discover campus food outlets, place orders, manage preparation, and collect meals.

## Download the app

Crave will be published on the **Google Play Store & App Store on 10th December**.

<!-- **[Download Crave from Google Play](https://play.google.com/store/search?q=Crave&c=apps)** --> 

<!--  Open the Play Store link and search for **Crave** if the app listing does not open directly. --> 

## Features

### For students
- **Discover campus outlets:** Browse participating food outlets and their menus.
- **Food catalogue:** Explore food items, descriptions, prices, and availability.
- **Cart and checkout:** Add items and review an order before placing it.
- **Scheduled pickup:** Choose an available pickup slot where supported.
- **Order updates:** Follow order progress and view order history.
- **Digital pickup tokens:** Use QR-based tokens for order collection.
- **Favourites:** Keep track of preferred food items.

### For vendors
- **Order processing:** View and manage incoming orders.
- **Menu management:** Maintain food listings, prices, variations, and availability.
- **QR verification:** Scan pickup tokens when handing over orders.
- **Business insights:** View sales and item-performance information where enabled.

### For administrators
- **Platform oversight:** Monitor outlets and platform activity.
- **User and role management:** Manage access across the platform.
- **Configuration:** Manage supported platform-wide settings and policies.

> Feature availability may depend on account role, outlet configuration, and the currently deployed backend environment.

## Technology

| Area | Technology |
|---|---|
| Android application | Kotlin, Jetpack Compose |
| Architecture | Clean Architecture, MVVM |
| Dependency injection | Dagger Hilt |
| Backend platform | Supabase |
| Database | PostgreSQL |
| Authentication | Supabase Auth |
| Local persistence | Room, DataStore |
| Networking | Retrofit, OkHttp, Ktor |
| Asynchronous operations | Kotlin Coroutines, Flow |
| Notifications | Firebase Cloud Messaging |
| QR functionality | ZXing, ML Kit, CameraX |
| Payments | Razorpay SDK integration |

## Project structure

The Android source is organized by application feature and architectural responsibility.

```text
Crave/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/srmfood/
│       │   │   ├── core/
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   ├── feature/
│       │   │   └── navigation/
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── supabase/
│   └── migrations/
├── gradle/
└── README.md
```

The feature modules cover areas such as authentication, food discovery, cart, checkout, orders, outlets, pickup, vendor operations, administration, notifications, reviews, favourites, and support.

## Run locally

### Requirements
- Android Studio (current stable version recommended)
- JDK 17
- Android SDK required by the project
- A Supabase project configured with the required database schema, migrations, and Row Level Security policies

### 1. Clone the repository

```bash
git clone https://github.com/1HPdhruv/Crave.git
cd Crave
```

### 2. Configure local credentials

Create a `local.properties` file in the project root. Add the appropriate client-side Supabase project URL and publishable/anonymous key for the environment you intend to run.

```properties
SUPABASE_URL="https://YOUR_PROJECT_REF.supabase.co"
SUPABASE_ANON_KEY="YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY"
```

Do not commit `local.properties`, service-role keys, secret API keys, signing credentials, or other private configuration.

The Gradle configuration defines separate mock, staging, and production environments. Use the matching environment configuration and build variant; production builds require the production backend configuration.

### 3. Apply database migrations

Review the SQL files in `supabase/migrations/` and apply the migrations to your own Supabase project in the intended order. Verify the resulting schema, database functions, triggers, and RLS policies before running the app.

### 4. Open and run

Open the project in Android Studio, allow Gradle to sync, select the appropriate build variant, and run it on an Android emulator or physical device.

## Backend and database

Crave uses Supabase with PostgreSQL. Database migrations are maintained in `supabase/migrations/`.

The schema and policies support core application concepts including:
- User profiles and roles
- Food outlets, categories, and food items
- Carts, orders, and payments
- Pickup slots and order collection
- Reviews and platform operations

When deploying your own instance, inspect every migration and policy and configure your Supabase environment independently. Never assume that the public repository includes access to the hosted production backend.

## Testing

The project includes unit-test and Android instrumentation-test source sets. Run the checks available in your local environment before submitting changes. Test against a non-production backend and synthetic accounts/data whenever possible.

## Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a focused branch: `git checkout -b feature/your-change`.
3. Make and test your changes.
4. Commit with a descriptive message.
5. Push your branch and open a pull request explaining the change and how it was tested.

Please avoid committing credentials, real user data, generated build outputs, local IDE files, or temporary diagnostic artifacts.

## Security

If you discover a security issue, do not publish credentials, personal data, or exploitable details in a public issue. Contact the repository maintainer privately with a concise description and reproduction steps.

---

Built with care for the SRMIST campus community.
