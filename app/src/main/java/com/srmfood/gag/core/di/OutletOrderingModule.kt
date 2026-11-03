package com.srmfood.gag.core.di

import com.srmfood.gag.data.repository.supabase.SupabaseOutletOrderingRepository
import com.srmfood.gag.domain.repository.OutletOrderingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OutletOrderingModule {
    @Binds
    abstract fun bindOutletOrderingRepository(impl: SupabaseOutletOrderingRepository): OutletOrderingRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VendorCatalogModule {
    @Binds
    abstract fun bindVendorCatalogRepository(
        impl: com.srmfood.gag.data.repository.supabase.SupabaseVendorCatalogRepository
    ): com.srmfood.gag.domain.repository.VendorCatalogRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushTokenModule {
    @Binds
    abstract fun bindPushTokenRepository(
        impl: com.srmfood.gag.data.repository.supabase.SupabasePushTokenRepository
    ): com.srmfood.gag.domain.repository.PushTokenRepository
}
