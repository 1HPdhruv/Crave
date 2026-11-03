package com.srmfood.gag.domain.model.vendor

import com.srmfood.gag.domain.model.OutletOrderingStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The vendor's own outlet (RLS only ever returns outlets where vendor_id = the signed-in user). */
data class VendorOutlet(
    val id: String,
    val name: String,
    val description: String,
    val phone: String,
    val isOpen: Boolean,
    val isActive: Boolean,
    val orderingStatus: OutletOrderingStatus,
    val openTime: String,
    val closeTime: String,
    val daysOpen: List<String>,
    val building: String,
    val floor: String,
    val locationDescription: String
)

data class FoodCategory(val id: String, val name: String, val emoji: String)

/** Editable fields of a food item. [id] == null means "create". */
data class VendorFoodDraft(
    val id: String? = null,
    val outletId: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String = "",
    val priceText: String = "",
    val prepTimeText: String = "5",
    val imageUrl: String = "",
    val isVeg: Boolean = true,
    val isAvailable: Boolean = true
)

data class VendorInventoryRow(
    val inventoryId: String,
    val foodItemId: String,
    val foodName: String,
    val outletId: String,
    val quantity: Int,
    val lowStockThreshold: Int,
    val isAvailable: Boolean,
    val updatedAt: String
) {
    val stockStatus: StockStatus get() = VendorRules.stockStatus(quantity, lowStockThreshold)
}

enum class StockStatus { OUT_OF_STOCK, LOW, OK, NOT_TRACKED }

enum class SlotStatus { AVAILABLE, LIMITED, FULL }

data class VendorSlot(
    val id: String,
    val outletId: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val capacity: Int,
    val booked: Int,
    val status: String
) {
    val remaining: Int get() = (capacity - booked).coerceAtLeast(0)
}

/** Thrown when a compare-and-set write lost a race (someone else changed the row first). */
class ConflictException(message: String) : Exception(message)

/** Pure business rules shared by the vendor screens (unit-tested; the database remains authoritative). */
object VendorRules {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    /** The seed trigger gives every new item 1,000,000 units; treat anything that large as "not tracked yet". */
    const val UNTRACKED_THRESHOLD = 100_000
    const val MAX_STOCK = 99_999
    const val MAX_SLOT_CAPACITY = 500
    const val MAX_PRICE = 10_000.0

    fun todayIst(now: Instant = Instant.now()): LocalDate = now.atZone(IST).toLocalDate()
    fun nowTimeIst(now: Instant = Instant.now()): LocalTime = now.atZone(IST).toLocalTime()

    // ── Food ──────────────────────────────────────────────────────────────────
    data class FoodErrors(
        val name: String? = null, val description: String? = null, val price: String? = null,
        val category: String? = null, val prep: String? = null, val image: String? = null, val outlet: String? = null
    ) { val any get() = listOf(name, description, price, category, prep, image, outlet).any { it != null } }

    fun parsePrice(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

    fun validateFood(d: VendorFoodDraft): FoodErrors {
        val price = parsePrice(d.priceText)
        val prep = d.prepTimeText.trim().toIntOrNull()
        val image = d.imageUrl.trim()
        return FoodErrors(
            name = when { d.name.trim().length < 2 -> "Enter a name (at least 2 characters)"; d.name.trim().length > 80 -> "Keep the name under 80 characters"; else -> null },
            description = if (d.description.trim().length > 500) "Keep the description under 500 characters" else null,
            price = when {
                price == null -> "Enter a price"
                price <= 0 -> "Price must be greater than 0"
                price > MAX_PRICE -> "Price looks too high (max ₹${MAX_PRICE.toInt()})"
                d.priceText.trim().toBigDecimalOrNull()?.stripTrailingZeros()?.scale()?.let { it > 2 } == true -> "Use at most 2 decimal places"
                else -> null
            },
            category = if (d.categoryId.isBlank()) "Choose a category" else null,
            prep = when { prep == null -> "Enter minutes"; prep !in 0..180 -> "Between 0 and 180 minutes"; else -> null },
            image = if (image.isNotEmpty() && !(image.startsWith("https://") && image.length <= 500 && !image.contains(' '))) "Use a full https:// link (or leave empty)" else null,
            outlet = if (d.outletId.isBlank()) "No outlet available for this account" else null
        )
    }

    // ── Inventory ─────────────────────────────────────────────────────────────
    fun stockStatus(quantity: Int, low: Int): StockStatus = when {
        quantity >= UNTRACKED_THRESHOLD -> StockStatus.NOT_TRACKED
        quantity <= 0 -> StockStatus.OUT_OF_STOCK
        quantity <= low -> StockStatus.LOW
        else -> StockStatus.OK
    }

    fun validateStock(text: String): String? {
        val q = text.trim().toIntOrNull() ?: return "Enter a whole number"
        return if (q !in 0..MAX_STOCK) "Between 0 and $MAX_STOCK" else null
    }

    // ── Pickup slots (Asia/Kolkata) ───────────────────────────────────────────
    /** Same thresholds as place_order: FULL at capacity, LIMITED from 80%. */
    fun slotStatus(booked: Int, capacity: Int): SlotStatus = when {
        booked >= capacity -> SlotStatus.FULL
        booked >= capacity * 0.8 -> SlotStatus.LIMITED
        else -> SlotStatus.AVAILABLE
    }

    fun isPast(date: LocalDate, end: LocalTime, now: Instant = Instant.now()): Boolean {
        val today = todayIst(now)
        return date.isBefore(today) || (date == today && !end.isAfter(nowTimeIst(now)))
    }

    fun validateNewSlot(date: LocalDate, start: LocalTime, end: LocalTime, capacity: Int, existing: List<VendorSlot>, now: Instant = Instant.now()): String? = when {
        !end.isAfter(start) -> "End time must be after start time"
        isPast(date, end, now) -> "That time has already passed (IST)"
        date == todayIst(now) && !start.isAfter(nowTimeIst(now)) -> "Start time has already passed (IST)"
        capacity !in 1..MAX_SLOT_CAPACITY -> "Capacity must be between 1 and $MAX_SLOT_CAPACITY"
        existing.any { it.date == date && it.start == start } -> "A slot starting at that time already exists"
        existing.any { it.date == date && start.isBefore(it.end) && end.isAfter(it.start) } -> "Overlaps an existing slot"
        else -> null
    }

    fun canEditSlot(slot: VendorSlot, now: Instant = Instant.now()): Boolean = !isPast(slot.date, slot.end, now)

    fun validateCapacityChange(slot: VendorSlot, newCapacity: Int, now: Instant = Instant.now()): String? = when {
        !canEditSlot(slot, now) -> "Past slots can't be changed"
        newCapacity !in 1..MAX_SLOT_CAPACITY -> "Capacity must be between 1 and $MAX_SLOT_CAPACITY"
        newCapacity < slot.booked -> "${slot.booked} orders are already booked in this slot"
        else -> null
    }

    fun canDeleteSlot(slot: VendorSlot, now: Instant = Instant.now()): String? = when {
        slot.booked > 0 -> "Slots with bookings can't be deleted"
        !canEditSlot(slot, now) -> "Past slots can't be changed"
        else -> null
    }

    // ── Outlet ────────────────────────────────────────────────────────────────
    fun validateHours(open: String, close: String): String? {
        val o = runCatching { LocalTime.parse(open) }.getOrNull() ?: return "Opening time must look like 08:00"
        val c = runCatching { LocalTime.parse(close) }.getOrNull() ?: return "Closing time must look like 22:00"
        return if (!c.isAfter(o)) "Closing time must be after opening time" else null
    }

    fun validatePhone(phone: String): String? {
        val p = phone.trim()
        return if (p.isEmpty() || p.matches(Regex("^[+0-9][0-9 ()-]{6,17}$"))) null else "Enter a valid phone number or leave empty"
    }
}
