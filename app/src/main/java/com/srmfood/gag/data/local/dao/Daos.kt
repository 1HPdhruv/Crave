package com.srmfood.gag.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.srmfood.gag.data.local.entity.CartItemEntity
import com.srmfood.gag.data.local.entity.FoodItemEntity
import com.srmfood.gag.data.local.entity.OrderEntity
import com.srmfood.gag.data.local.entity.OutletEntity
import com.srmfood.gag.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users LIMIT 1")
    fun observeCurrentUser(): Flow<UserEntity?>

    @Query("DELETE FROM users")
    suspend fun clearAll(): Int
}

@Dao
@JvmSuppressWildcards
interface OutletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(outlets: List<OutletEntity>): List<Long>

    @Query("SELECT * FROM outlets WHERE isActive = 1 ORDER BY name ASC")
    fun observeOutlets(): Flow<List<OutletEntity>>

    @Query("SELECT * FROM outlets WHERE id = :id")
    suspend fun getOutletById(id: String): OutletEntity?

    @Query("SELECT cachedAt FROM outlets LIMIT 1")
    suspend fun getCacheTime(): Long?

    @Query("DELETE FROM outlets")
    suspend fun clearAll(): Int
}

@Dao
@JvmSuppressWildcards
interface FoodItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FoodItemEntity>): List<Long>

    @Query("SELECT * FROM food_items WHERE outletId = :outletId")
    fun observeMenuByOutlet(outletId: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFoodById(id: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE isFavorite = 1")
    fun observeFavorites(): Flow<List<FoodItemEntity>>

    @Query("UPDATE food_items SET isFavorite = :isFavorite WHERE id = :foodId")
    suspend fun updateFavorite(foodId: String, isFavorite: Boolean): Int

    @Query("SELECT isFavorite FROM food_items WHERE id = :foodId")
    suspend fun isFavorite(foodId: String): Boolean?

    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' AND isAvailable = 1")
    suspend fun searchFood(query: String): List<FoodItemEntity>

    @Query("SELECT * FROM food_items WHERE isPopular = 1 LIMIT 10")
    fun observePopular(): Flow<List<FoodItemEntity>>

    @Query("DELETE FROM food_items")
    suspend fun clearAll(): Int
}

@Dao
@JvmSuppressWildcards
interface CartDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CartItemEntity): Long

    @Query("SELECT * FROM cart_items")
    fun observeCartItems(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items")
    suspend fun getCartItems(): List<CartItemEntity>

    @Update
    suspend fun updateItem(item: CartItemEntity): Int

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun deleteItem(id: String): Int

    @Query("DELETE FROM cart_items")
    suspend fun clearCart(): Int

    @Query("SELECT outletId FROM cart_items LIMIT 1")
    suspend fun getCartOutletId(): String?

    @Query("SELECT COUNT(*) FROM cart_items")
    fun observeCartCount(): Flow<Int>
}

@Dao
@JvmSuppressWildcards
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<OrderEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY')")
    fun observeActiveOrders(): Flow<List<OrderEntity>>

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateStatus(orderId: String, status: String): Int

    @Query("DELETE FROM orders WHERE cachedAt < :threshold")
    suspend fun clearOldOrders(threshold: Long): Int
}
