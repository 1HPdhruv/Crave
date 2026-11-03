package com.srmfood.gag.data.repository.supabase

import com.srmfood.gag.domain.model.OutletOrderingStatus
import com.srmfood.gag.domain.model.vendor.*
import com.srmfood.gag.domain.repository.VendorCatalogRepository
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class HoursDto(val openTime: String = "08:00", val closeTime: String = "22:00", val daysOpen: List<String> = emptyList())

@Serializable private data class VOutletRow(
    val id: String, val name: String, val description: String? = null, val phone: String? = null,
    @SerialName("is_open") val isOpen: Boolean = false, @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("ordering_status") val orderingStatus: String? = null,
    @SerialName("operating_hours") val hours: HoursDto? = null,
    val building: String? = null, val floor: String? = null,
    @SerialName("location_description") val locationDescription: String? = null
)

@Serializable private data class CategoryRow(val id: String, val name: String, val emoji: String = "")

@Serializable private data class FoodRow(
    val id: String, @SerialName("outlet_id") val outletId: String, @SerialName("category_id") val categoryId: String,
    val name: String, val description: String = "", @SerialName("image_url") val imageUrl: String? = null,
    val price: Double, @SerialName("is_veg") val isVeg: Boolean? = true,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("prep_time_minutes") val prepTime: Int? = 5
)

@Serializable private data class FoodInsert(
    @SerialName("outlet_id") val outletId: String, @SerialName("category_id") val categoryId: String,
    val name: String, val description: String, @SerialName("image_url") val imageUrl: String?,
    val price: Double, @SerialName("is_veg") val isVeg: Boolean, @SerialName("is_available") val isAvailable: Boolean,
    @SerialName("prep_time_minutes") val prepTime: Int
)

@Serializable private data class FoodRef(val id: String, val name: String, @SerialName("is_available") val isAvailable: Boolean = true, @SerialName("outlet_id") val outletId: String)

@Serializable private data class InvRow(
    val id: String, @SerialName("food_item_id") val foodItemId: String,
    @SerialName("quantity_available") val quantity: Int, @SerialName("low_stock_threshold") val low: Int = 5,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("food_items") val food: FoodRef
)

@Serializable private data class SlotRow(
    val id: String, @SerialName("outlet_id") val outletId: String, @SerialName("slot_date") val date: String,
    @SerialName("start_time") val start: String, @SerialName("end_time") val end: String,
    val capacity: Int, @SerialName("booked_count") val booked: Int = 0, val status: String = "AVAILABLE"
)

@Serializable private data class SlotInsert(
    @SerialName("outlet_id") val outletId: String, @SerialName("slot_date") val date: String,
    @SerialName("start_time") val start: String, @SerialName("end_time") val end: String, val capacity: Int
)

private fun String.toTime(): LocalTime = LocalTime.parse(this.take(5))
private fun LocalTime.db(): String = "%02d:%02d:00".format(hour, minute)

@Singleton
class SupabaseVendorCatalogRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) : VendorCatalogRepository {

    private fun requireUser(): String = auth.currentSessionOrNull()?.user?.id ?: throw IllegalStateException("Not signed in")

    /** Outlets are always fetched filtered by the caller: ids we may touch are only ever the vendor's own. */
    private suspend fun myOutletRows(): List<VOutletRow> {
        val uid = requireUser()
        return postgrest["outlets"].select(Columns.ALL) { filter { eq("vendor_id", uid) } }.decodeList<VOutletRow>()
    }

    private suspend fun requireOwned(outletId: String) {
        if (myOutletRows().none { it.id == outletId }) throw SecurityException("That outlet doesn't belong to your account.")
    }

    override suspend fun getMyOutlets(): Result<List<VendorOutlet>> = runCatching {
        myOutletRows().sortedBy { it.name }.map {
            VendorOutlet(
                id = it.id, name = it.name, description = it.description.orEmpty(), phone = it.phone.orEmpty(),
                isOpen = it.isOpen, isActive = it.isActive, orderingStatus = OutletOrderingStatus.fromString(it.orderingStatus),
                openTime = it.hours?.openTime ?: "08:00", closeTime = it.hours?.closeTime ?: "22:00", daysOpen = it.hours?.daysOpen ?: emptyList(),
                building = it.building.orEmpty(), floor = it.floor.orEmpty(), locationDescription = it.locationDescription.orEmpty()
            )
        }
    }

    override suspend fun getCategories(): Result<List<FoodCategory>> = runCatching {
        postgrest["categories"].select(Columns.raw("id,name,emoji")) { order("name", Order.ASCENDING) }
            .decodeList<CategoryRow>().map { FoodCategory(it.id, it.name, it.emoji) }
    }

    override suspend fun getFood(foodId: String): Result<VendorFoodDraft> = runCatching {
        val owned = myOutletRows().map { it.id }.toSet()
        val row = postgrest["food_items"].select(Columns.ALL) { filter { eq("id", foodId) } }.decodeSingleOrNull<FoodRow>()
        if (row == null || row.outletId !in owned) throw NoSuchElementException("Food item not found.")
        VendorFoodDraft(
            id = row.id, outletId = row.outletId, categoryId = row.categoryId, name = row.name, description = row.description,
            priceText = row.price.toBigDecimal().stripTrailingZeros().toPlainString(), prepTimeText = (row.prepTime ?: 5).toString(),
            imageUrl = row.imageUrl.orEmpty(), isVeg = row.isVeg ?: true, isAvailable = row.isAvailable
        )
    }

    override suspend fun saveFood(draft: VendorFoodDraft): Result<String> = runCatching {
        val errors = VendorRules.validateFood(draft)
        require(!errors.any) { "Please fix the highlighted fields." }
        requireOwned(draft.outletId)
        val body = FoodInsert(
            outletId = draft.outletId, categoryId = draft.categoryId, name = draft.name.trim(), description = draft.description.trim(),
            imageUrl = draft.imageUrl.trim().ifEmpty { null }, price = VendorRules.parsePrice(draft.priceText)!!, isVeg = draft.isVeg,
            isAvailable = draft.isAvailable, prepTime = draft.prepTimeText.trim().toInt()
        )
        if (draft.id == null) {
            postgrest["food_items"].insert(body) { select() }.decodeSingle<FoodRow>().id
        } else {
            val rows = postgrest["food_items"].update({
                set("category_id", body.categoryId); set("name", body.name); set("description", body.description)
                set("image_url", body.imageUrl); set("price", body.price); set("is_veg", body.isVeg)
                set("is_available", body.isAvailable); set("prep_time_minutes", body.prepTime)
            }) { select(); filter { eq("id", draft.id); eq("outlet_id", draft.outletId) } }.decodeList<FoodRow>()
            if (rows.isEmpty()) throw SecurityException("Nothing was saved: the item wasn't found or isn't yours.")
            draft.id
        }
    }

    override suspend fun getInventory(): Result<List<VendorInventoryRow>> = runCatching {
        val ids = myOutletRows().map { it.id }
        if (ids.isEmpty()) return@runCatching emptyList()
        postgrest["inventory"].select(Columns.raw("id,food_item_id,quantity_available,low_stock_threshold,updated_at,food_items!inner(id,name,is_available,outlet_id)")) {
            filter { isIn("food_items.outlet_id", ids) }
        }.decodeList<InvRow>().map {
            VendorInventoryRow(it.id, it.foodItemId, it.food.name, it.food.outletId, it.quantity, it.low, it.food.isAvailable, it.updatedAt)
        }.sortedBy { it.foodName.lowercase() }
    }

    override suspend fun setStock(inventoryId: String, expectedQuantity: Int, newQuantity: Int, lowStockThreshold: Int): Result<Unit> = runCatching {
        VendorRules.validateStock(newQuantity.toString())?.let { throw IllegalArgumentException(it) }
        require(lowStockThreshold in 0..10_000) { "Low-stock level must be between 0 and 10000" }
        // One atomic UPDATE ... WHERE quantity_available = expected: a booking that decremented stock in the
        // meantime makes this match zero rows instead of silently overwriting the newer number.
        val rows = postgrest["inventory"].update({
            set("quantity_available", newQuantity); set("low_stock_threshold", lowStockThreshold)
        }) { select(Columns.raw("id")); filter { eq("id", inventoryId); eq("quantity_available", expectedQuantity) } }.decodeList<SlotIdRow>()
        if (rows.isEmpty()) throw ConflictException("Stock changed while you were editing (an order may have just been placed). The list was refreshed — please try again.")
    }

    override suspend fun setFoodAvailability(foodId: String, available: Boolean): Result<Unit> = runCatching {
        val owned = myOutletRows().map { it.id }
        val rows = postgrest["food_items"].update({ set("is_available", available) }) {
            select(Columns.raw("id")); filter { eq("id", foodId); isIn("outlet_id", owned) }
        }.decodeList<SlotIdRow>()
        if (rows.isEmpty()) throw SecurityException("Nothing was changed: the item wasn't found or isn't yours.")
    }

    override suspend fun updateOutlet(
        outletId: String, isOpen: Boolean, description: String, phone: String,
        openTime: String, closeTime: String, daysOpen: List<String>
    ): Result<Unit> = runCatching {
        VendorRules.validateHours(openTime, closeTime)?.let { throw IllegalArgumentException(it) }
        VendorRules.validatePhone(phone)?.let { throw IllegalArgumentException(it) }
        requireOwned(outletId)
        val uid = requireUser()
        // Only these columns are ever sent: never vendor_id, is_active, rating or ordering_status.
        val rows = postgrest["outlets"].update({
            set("is_open", isOpen); set("description", description.trim()); set("phone", phone.trim().ifEmpty { null })
            set("operating_hours", HoursDto(openTime, closeTime, daysOpen))
        }) { select(Columns.raw("id")); filter { eq("id", outletId); eq("vendor_id", uid) } }.decodeList<SlotIdRow>()
        if (rows.isEmpty()) throw SecurityException("Nothing was saved: the outlet wasn't found or isn't yours.")
    }

    override suspend fun getSlots(outletId: String, date: LocalDate): Result<List<VendorSlot>> = runCatching {
        requireOwned(outletId)
        postgrest["pickup_slots"].select(Columns.ALL) {
            filter { eq("outlet_id", outletId); eq("slot_date", date.toString()) }
            order("start_time", Order.ASCENDING)
        }.decodeList<SlotRow>().map { it.toDomain() }
    }

    private fun SlotRow.toDomain() = VendorSlot(id, outletId, LocalDate.parse(date), start.toTime(), end.toTime(), capacity, booked, status)

    override suspend fun createSlot(outletId: String, date: LocalDate, start: LocalTime, end: LocalTime, capacity: Int): Result<Unit> = runCatching {
        requireOwned(outletId)
        val existing = getSlots(outletId, date).getOrThrow()
        VendorRules.validateNewSlot(date, start, end, capacity, existing)?.let { throw IllegalArgumentException(it) }
        // UNIQUE (outlet_id, slot_date, start_time) is the real guard against duplicates created concurrently.
        postgrest["pickup_slots"].insert(SlotInsert(outletId, date.toString(), start.db(), end.db(), capacity))
    }

    override suspend fun setSlotCapacity(slot: VendorSlot, newCapacity: Int): Result<Unit> = runCatching {
        requireOwned(slot.outletId)
        VendorRules.validateCapacityChange(slot, newCapacity)?.let { throw IllegalArgumentException(it) }
        val status = VendorRules.slotStatus(slot.booked, newCapacity).name
        val rows = postgrest["pickup_slots"].update({ set("capacity", newCapacity); set("status", status) }) {
            select(Columns.raw("id")); filter { eq("id", slot.id); eq("booked_count", slot.booked) }
        }.decodeList<SlotIdRow>()
        if (rows.isEmpty()) throw ConflictException("A booking was just made or the slot changed. Reloaded — please try again.")
    }

    override suspend fun deleteSlot(slot: VendorSlot): Result<Unit> = runCatching {
        requireOwned(slot.outletId)
        VendorRules.canDeleteSlot(slot)?.let { throw IllegalArgumentException(it) }
        val rows = postgrest["pickup_slots"].delete {
            select(Columns.raw("id")); filter { eq("id", slot.id); eq("booked_count", 0) }
        }.decodeList<SlotIdRow>()
        if (rows.isEmpty()) throw ConflictException("The slot got a booking or was already removed. Reloaded.")
    }
}

@Serializable private data class SlotIdRow(val id: String)
