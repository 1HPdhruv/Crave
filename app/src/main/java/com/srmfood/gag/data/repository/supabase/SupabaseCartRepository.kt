package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.data.local.dao.CartDao
import com.srmfood.gag.data.mapper.toDomain
import com.srmfood.gag.data.mapper.toEntity
import com.srmfood.gag.domain.model.Cart
import com.srmfood.gag.domain.model.CartItem
import com.srmfood.gag.domain.model.SelectedCustomization
import com.srmfood.gag.domain.repository.CartRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class SupabaseCartItem(
    val id: String,
    @SerialName("cart_id")     val cartId: String,
    @SerialName("food_item_id") val foodItemId: String,
    val quantity: Int,
    val price: Double,
    @SerialName("is_veg")      val isVeg: Boolean = true
)

@Serializable
data class SupabaseCartItemCustomization(
    @SerialName("cart_item_id") val cartItemId: String,
    @SerialName("variant_id")   val variantId: String,
    @SerialName("option_id")    val optionId: String,
    @SerialName("extra_price")  val extraPrice: Double
)

@Serializable
data class SupabaseCart(
    val id: String,
    @SerialName("user_id")   val userId: String,
    @SerialName("outlet_id") val outletId: String,
    val subtotal: Double,
    val tax: Double,
    val total: Double
)

/** Minimal response to fetch only the cart ID for passing to place_order() RPC. */
@Serializable
data class CartIdResponse(
    val id: String
)

@Serializable
data class RemoteCartItemJoin(
    val id: String,
    @SerialName("food_item_id") val foodItemId: String,
    val quantity: Int,
    val price: Double,
    @SerialName("is_veg") val isVeg: Boolean,
    @SerialName("special_instructions") val specialInstructions: String? = null,
    val food_items: RemoteFoodItemJoin? = null
)

@Serializable
data class RemoteFoodItemJoin(
    val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val outlets: RemoteOutletJoin? = null
)

@Serializable
data class RemoteOutletJoin(
    val name: String
)

@Singleton
class SupabaseCartRepository @Inject constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val cartDao: CartDao
) : CartRepository {

    private var hasSyncedFromBackend = false

    override fun getCart(): Flow<Cart?> {
        return cartDao.observeCartItems().map { entities ->
            if (entities.isEmpty()) return@map null

            val items = entities.map { it.toDomain() }
            val outletId = entities.first().outletId
            val outletName = entities.first().outletName

            val subtotal = items.sumOf { it.itemTotal }
            val tax = subtotal * 0.05
            val total = subtotal + tax

            Cart(
                outletId = outletId,
                outletName = outletName,
                items = items,
                subtotal = subtotal,
                tax = tax,
                total = total,
                estimatedPrepMinutes = items.size * 5
            )
        }
    }

    override suspend fun syncCart(): Result<Unit> = runCatching {
        val userId = auth.currentSessionOrNull()?.user?.id ?: return@runCatching
        
        val existingCarts = postgrest["carts"].select {
            filter { eq("user_id", userId) }
        }.decodeList<SupabaseCart>()
        
        val localItems = cartDao.getCartItems()

        if (existingCarts.isEmpty()) {
            hasSyncedFromBackend = true
            if (localItems.isNotEmpty()) {
                trySyncCartToBackend()
            }
            return@runCatching
        }
        
        val cartId = existingCarts.first().id
        val cartOutletId = existingCarts.first().outletId
        
        val remoteItems = postgrest["cart_items"].select(
            io.github.jan.supabase.postgrest.query.Columns.raw("id, food_item_id, quantity, price, is_veg, special_instructions, food_items(name, image_url, outlets(name))")
        ) {
            filter { eq("cart_id", cartId) }
        }.decodeList<RemoteCartItemJoin>()
        
        if (remoteItems.isEmpty()) {
            hasSyncedFromBackend = true
            if (localItems.isNotEmpty()) {
                trySyncCartToBackend()
            } else {
                cartDao.clearCart()
            }
            return@runCatching
        }
        
        val itemIds = remoteItems.map { it.id }
        val remoteCustomizations = postgrest["cart_item_customizations"].select {
            filter { isIn("cart_item_id", itemIds) }
        }.decodeList<SupabaseCartItemCustomization>()
        
        // Identify local items that are NOT in the remote database (user added them while hasSyncedFromBackend was false)
        val remoteFoodItemIds = remoteItems.map { it.foodItemId }
        val pendingLocalItems = localItems.filter { it.foodItemId !in remoteFoodItemIds }

        // If local items exist and are from a different outlet than remote, let local win (user started a new cart)
        if (pendingLocalItems.isNotEmpty() && pendingLocalItems.first().outletId != cartOutletId) {
            hasSyncedFromBackend = true
            trySyncCartToBackend()
            return@runCatching
        }

        cartDao.clearCart()
        
        remoteItems.forEach { remote ->
            val foodName = remote.food_items?.name ?: "Unknown"
            val outletName = remote.food_items?.outlets?.name ?: "Unknown Outlet"
            val imageUrl = remote.food_items?.imageUrl
            
            val itemCustomizations = remoteCustomizations
                .filter { it.cartItemId == remote.id }
                .map { cust -> 
                    SelectedCustomization(
                        customizationId = cust.variantId,
                        customizationName = "", 
                        optionId = cust.optionId,
                        optionName = "",
                        extraPrice = cust.extraPrice
                    )
                }
                
            val domainItem = CartItem(
                id = remote.id,
                foodItemId = remote.foodItemId,
                foodName = foodName,
                foodImageUrl = imageUrl,
                outletId = cartOutletId,
                price = remote.price,
                quantity = remote.quantity,
                selectedCustomizations = itemCustomizations,
                isVeg = remote.isVeg,
                specialInstructions = remote.specialInstructions
            )
            cartDao.insertItem(domainItem.toEntity(outletName))
        }

        // Restore pending local items
        pendingLocalItems.forEach {
            cartDao.insertItem(it)
        }

        hasSyncedFromBackend = true

        if (pendingLocalItems.isNotEmpty()) {
            trySyncCartToBackend()
        }
    }

    override suspend fun addToCart(
        foodItemId: String,
        foodName: String,
        foodImageUrl: String?,
        outletId: String,
        outletName: String,
        price: Double,
        quantity: Int,
        isVeg: Boolean,
        selectedCustomizations: List<SelectedCustomization>,
        specialInstructions: String?
    ): Result<Cart> = runCatching {
        val currentOutletId = cartDao.getCartOutletId()
        if (currentOutletId != null && currentOutletId != outletId) {
            throw IllegalStateException("Cannot add items from different outlets")
        }

        val existingItems = cartDao.getCartItems()
        val existingItem = existingItems.find { 
            it.foodItemId == foodItemId && 
            it.selectedCustomizations == Json.encodeToString(selectedCustomizations) 
        }

        if (existingItem != null) {
            // Update quantity of existing item
            val updatedItem = existingItem.copy(quantity = existingItem.quantity + quantity)
            cartDao.updateItem(updatedItem)
        } else {
            // Add new item
            val cartItem = CartItem(
                id = UUID.randomUUID().toString(),
                foodItemId = foodItemId,
                foodName = foodName,
                foodImageUrl = foodImageUrl,
                outletId = outletId,
                price = price,
                quantity = quantity,
                selectedCustomizations = selectedCustomizations,
                isVeg = isVeg,
                specialInstructions = specialInstructions
            )
            cartDao.insertItem(cartItem.toEntity(outletName))
        }
        
        // Sync to backend
        trySyncCartToBackend()
        
        getCartSnapshot() ?: throw Exception("Cart empty after add")
    }

    override suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<Cart> = runCatching {
        if (quantity <= 0) {
            cartDao.deleteItem(cartItemId)
        } else {
            val items = cartDao.getCartItems()
            val item = items.find { it.id == cartItemId }
            if (item != null) {
                cartDao.updateItem(item.copy(quantity = quantity))
            }
        }
        
        trySyncCartToBackend()
        
        getCartSnapshot() ?: throw Exception("Cart empty")
    }

    override suspend fun removeItem(cartItemId: String): Result<Cart> = runCatching {
        cartDao.deleteItem(cartItemId)
        trySyncCartToBackend()
        getCartSnapshot() ?: throw Exception("Cart empty")
    }

    override suspend fun clearCart(): Result<Unit> = runCatching {
        cartDao.clearCart()
        try {
            // Also clear remote cart if logged in
            val userId = auth.currentSessionOrNull()?.user?.id
            if (userId != null) {
                postgrest["carts"].delete {
                    filter { eq("user_id", userId) }
                }
            }
        } catch (e: Exception) {
            // Ignore for now
        }
    }

    override suspend fun getCartOutletId(): String? {
        return cartDao.getCartOutletId()
    }
    
    private suspend fun trySyncCartToBackend() {
        try {
            val session = auth.currentSessionOrNull()
            val userId = session?.user?.id ?: return // User not logged in, just keep local cart
            
            if (!hasSyncedFromBackend) return // Prevent overwriting remote cart before fetching

            
            val snapshot = getCartSnapshot()
            if (snapshot == null) {
                // Cart is empty, delete backend cart
                postgrest["carts"].delete {
                    filter { eq("user_id", userId) }
                }
                return
            }
            
            // 1. Get or Create Cart
            val existingCarts = postgrest["carts"].select {
                filter { eq("user_id", userId) }
                order("updated_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<SupabaseCart>()
            
            val cartId = if (existingCarts.isNotEmpty()) {
                val cart = existingCarts.first()
                if (cart.outletId != snapshot.outletId) {
                    // Recreate cart for new outlet, but clean up ALL old ones first
                    existingCarts.forEach {
                        postgrest["carts"].delete { filter { eq("id", it.id) } }
                    }
                    createRemoteCart(userId, snapshot)
                } else {
                    cart.id
                }
            } else {
                createRemoteCart(userId, snapshot)
            }
            
            // 2. Update cart totals
            postgrest["carts"].update(
                {
                    set("subtotal", snapshot.subtotal)
                    set("tax", snapshot.tax)
                    set("total", snapshot.total)
                }
            ) {
                filter { eq("id", cartId) }
            }
            
            // 3. Sync cart items
            postgrest["cart_items"].delete {
                filter { eq("cart_id", cartId) }
            }
            
            val remoteItems = snapshot.items.map { item ->
                SupabaseCartItem(
                    id         = item.id,
                    cartId     = cartId,
                    foodItemId = item.foodItemId,
                    quantity   = item.quantity,
                    price      = item.price,
                    isVeg      = item.isVeg
                )
            }
            
            if (remoteItems.isNotEmpty()) {
                postgrest["cart_items"].insert(remoteItems)
                
                val remoteCustomizations = snapshot.items.flatMap { item ->
                    item.selectedCustomizations.map { custom ->
                        SupabaseCartItemCustomization(
                            cartItemId = item.id,
                            variantId  = custom.customizationId,
                            optionId   = custom.optionId,
                            extraPrice = custom.extraPrice
                        )
                    }
                }
                
                if (remoteCustomizations.isNotEmpty()) {
                    postgrest["cart_item_customizations"].insert(remoteCustomizations)
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            // In a real app we might retry later or ignore if offline.
            // For now, log the error clearly so we know if syncing fails.
            android.util.Log.e("SupabaseCartRepository", "Failed to sync cart to backend", e)
        }
    }
    
    private suspend fun createRemoteCart(userId: String, snapshot: Cart): String {
        val newCartId = UUID.randomUUID().toString()
        val newCart = SupabaseCart(
            id = newCartId,
            userId = userId,
            outletId = snapshot.outletId,
            subtotal = snapshot.subtotal,
            tax = snapshot.tax,
            total = snapshot.total
        )
        postgrest["carts"].insert(newCart)
        return newCartId
    }
    
    private suspend fun getCartSnapshot(): Cart? {
        val entities = cartDao.getCartItems()
        if (entities.isEmpty()) return null
        
        val items = entities.map { it.toDomain() }
        val subtotal = items.sumOf { it.itemTotal }
        val tax = subtotal * 0.05
        return Cart(
            outletId = entities.first().outletId,
            outletName = entities.first().outletName,
            items = items,
            subtotal = subtotal,
            tax = tax,
            total = subtotal + tax,
            estimatedPrepMinutes = items.size * 5
        )
    }
}
