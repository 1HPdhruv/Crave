package com.srmfood.gag.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.srmfood.gag.data.local.converter.RoomConverters
import com.srmfood.gag.data.local.dao.CartDao
import com.srmfood.gag.data.local.dao.FoodItemDao
import com.srmfood.gag.data.local.dao.OrderDao
import com.srmfood.gag.data.local.dao.OutletDao
import com.srmfood.gag.data.local.dao.UserDao
import com.srmfood.gag.data.local.entity.CartItemEntity
import com.srmfood.gag.data.local.entity.FoodItemEntity
import com.srmfood.gag.data.local.entity.OrderEntity
import com.srmfood.gag.data.local.entity.OutletEntity
import com.srmfood.gag.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        OutletEntity::class,
        FoodItemEntity::class,
        CartItemEntity::class,
        OrderEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class GagDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun outletDao(): OutletDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
}
