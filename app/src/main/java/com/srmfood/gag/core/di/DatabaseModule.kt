package com.srmfood.gag.core.di

import android.content.Context
import androidx.room.Room
import com.srmfood.gag.core.constants.AppConstants
import com.srmfood.gag.core.database.GagDatabase
import com.srmfood.gag.data.local.dao.CartDao
import com.srmfood.gag.data.local.dao.FoodItemDao
import com.srmfood.gag.data.local.dao.OrderDao
import com.srmfood.gag.data.local.dao.OutletDao
import com.srmfood.gag.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGagDatabase(
        @ApplicationContext context: Context
    ): GagDatabase = Room.databaseBuilder(
        context,
        GagDatabase::class.java,
        AppConstants.DATABASE_NAME
    )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideUserDao(db: GagDatabase): UserDao = db.userDao()

    @Provides
    fun provideOutletDao(db: GagDatabase): OutletDao = db.outletDao()

    @Provides
    fun provideFoodItemDao(db: GagDatabase): FoodItemDao = db.foodItemDao()

    @Provides
    fun provideCartDao(db: GagDatabase): CartDao = db.cartDao()

    @Provides
    fun provideOrderDao(db: GagDatabase): OrderDao = db.orderDao()
}
