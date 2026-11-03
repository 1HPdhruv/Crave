package com.srmfood.gag.domain.repository

import com.srmfood.gag.domain.model.vendor.*
import java.time.LocalDate
import java.time.LocalTime

/**
 * The signed-in vendor's own catalogue, inventory, outlet and pickup slots.
 * Authorization is enforced by the database (RLS: vendor_id = auth.uid(), plus guard triggers); every write
 * here also verifies that a row was actually affected, so a silently-filtered update is reported as a failure.
 */
interface VendorCatalogRepository {
    suspend fun getMyOutlets(): Result<List<VendorOutlet>>
    suspend fun getCategories(): Result<List<FoodCategory>>

    suspend fun getFood(foodId: String): Result<VendorFoodDraft>
    /** Creates (id == null) or updates a food item and returns its id. */
    suspend fun saveFood(draft: VendorFoodDraft): Result<String>

    suspend fun getInventory(): Result<List<VendorInventoryRow>>
    /** Compare-and-set: fails with [ConflictException] if the stored quantity is no longer [expectedQuantity]. */
    suspend fun setStock(inventoryId: String, expectedQuantity: Int, newQuantity: Int, lowStockThreshold: Int): Result<Unit>
    suspend fun setFoodAvailability(foodId: String, available: Boolean): Result<Unit>

    suspend fun updateOutlet(
        outletId: String, isOpen: Boolean, description: String, phone: String,
        openTime: String, closeTime: String, daysOpen: List<String>
    ): Result<Unit>

    suspend fun getSlots(outletId: String, date: LocalDate): Result<List<VendorSlot>>
    suspend fun createSlot(outletId: String, date: LocalDate, start: LocalTime, end: LocalTime, capacity: Int): Result<Unit>
    /** Compare-and-set on booked_count so a booking that lands meanwhile can't be overbooked. */
    suspend fun setSlotCapacity(slot: VendorSlot, newCapacity: Int): Result<Unit>
    suspend fun deleteSlot(slot: VendorSlot): Result<Unit>
}
