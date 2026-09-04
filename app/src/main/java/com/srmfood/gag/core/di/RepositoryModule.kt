package com.srmfood.gag.core.di

import com.srmfood.gag.BuildConfig
import com.srmfood.gag.data.repository.AdminRepositoryImpl
import com.srmfood.gag.data.repository.MockAuthRepository
import com.srmfood.gag.data.repository.MockCartRepository
import com.srmfood.gag.data.repository.MockFoodRepository
import com.srmfood.gag.data.repository.MockOrderRepository
import com.srmfood.gag.data.repository.MockOutletRepository
import com.srmfood.gag.data.repository.MockPaymentRepository
import com.srmfood.gag.data.repository.supabase.SupabaseAuthRepository
import com.srmfood.gag.data.repository.supabase.SupabaseCartRepository
import com.srmfood.gag.data.repository.supabase.SupabaseFoodRepository
import com.srmfood.gag.data.repository.supabase.SupabaseOrderRepository
import com.srmfood.gag.data.repository.supabase.SupabaseOutletRepository
import com.srmfood.gag.data.repository.supabase.SupabasePaymentRepository
import com.srmfood.gag.domain.repository.AdminRepository
import com.srmfood.gag.domain.repository.AuthRepository
import com.srmfood.gag.domain.repository.CartRepository
import com.srmfood.gag.domain.repository.FoodRepository
import com.srmfood.gag.domain.repository.OrderRepository
import com.srmfood.gag.domain.repository.PaymentRepository
import com.srmfood.gag.domain.repository.OutletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        mockImpl: MockAuthRepository,
        supabaseImpl: SupabaseAuthRepository
    ): AuthRepository {
        return if (BuildConfig.USE_MOCK) mockImpl else supabaseImpl
    }

    @Provides
    @Singleton
    fun provideOutletRepository(
        mockImpl: MockOutletRepository,
        supabaseImpl: SupabaseOutletRepository
    ): OutletRepository {
        return if (BuildConfig.USE_MOCK) mockImpl else supabaseImpl
    }

    @Provides
    @Singleton
    fun provideFoodRepository(
        mockImpl: MockFoodRepository,
        supabaseImpl: SupabaseFoodRepository
    ): FoodRepository {
        return if (BuildConfig.USE_MOCK) mockImpl else supabaseImpl
    }

    @Provides
    @Singleton
    fun provideCartRepository(
        mockImpl: MockCartRepository,
        supabaseImpl: SupabaseCartRepository
    ): CartRepository {
        return if (BuildConfig.USE_MOCK) mockImpl else supabaseImpl
    }

    @Provides
    @Singleton
    fun provideOrderRepository(
        mockImpl: MockOrderRepository,
        supabaseImpl: SupabaseOrderRepository
    ): OrderRepository {
        return if (BuildConfig.USE_MOCK) mockImpl else supabaseImpl
    }

    @Provides
    @Singleton
    fun providePaymentRepository(
        mockImpl: MockPaymentRepository,
        supabaseImpl: SupabasePaymentRepository
    ): PaymentRepository {
        return if (BuildConfig.USE_MOCK) mockImpl else supabaseImpl
    }

    @Provides
    @Singleton
    fun provideAdminRepository(
        impl: AdminRepositoryImpl
    ): AdminRepository = impl

    @Provides
    @Singleton
    fun provideNotificationRepository(
        impl: com.srmfood.gag.data.repository.supabase.SupabaseNotificationRepository
    ): com.srmfood.gag.domain.repository.NotificationRepository = impl

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        impl: com.srmfood.gag.data.repository.preferences.UserPreferencesRepositoryImpl
    ): com.srmfood.gag.domain.repository.UserPreferencesRepository = impl
}
