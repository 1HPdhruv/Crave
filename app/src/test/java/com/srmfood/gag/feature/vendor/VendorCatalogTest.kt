package com.srmfood.gag.feature.vendor

import com.srmfood.gag.domain.model.OutletOrderingStatus
import com.srmfood.gag.domain.model.vendor.*
import com.srmfood.gag.domain.repository.VendorCatalogRepository
import com.srmfood.gag.feature.vendor.catalog.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class VendorRulesTest {
    // 2025-03-10 10:00 IST
    private val now: Instant = LocalDate.of(2025, 3, 10).atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant()
    private val today = LocalDate.of(2025, 3, 10)
    private fun slot(date: LocalDate = today, s: String = "12:00", e: String = "12:30", cap: Int = 10, booked: Int = 0) =
        VendorSlot("s1", "o1", date, LocalTime.parse(s), LocalTime.parse(e), cap, booked, "AVAILABLE")
    private fun good() = VendorFoodDraft(outletId = "o1", categoryId = "c1", name = "Samosa", priceText = "20", prepTimeText = "5")

    @Test fun `timezone is Asia Kolkata even when UTC date differs`() {
        val lateUtc = LocalDate.of(2025, 3, 10).atTime(20, 0).atZone(ZoneId.of("UTC")).toInstant() // 01:30 on the 11th in IST
        assertEquals(LocalDate.of(2025, 3, 11), VendorRules.todayIst(lateUtc))
    }

    @Test fun `food validation`() {
        assertFalse(VendorRules.validateFood(good()).any)
        assertNotNull(VendorRules.validateFood(good().copy(name = " a ")).name)
        assertEquals("Price must be greater than 0", VendorRules.validateFood(good().copy(priceText = "0")).price)
        assertNotNull(VendorRules.validateFood(good().copy(priceText = "abc")).price)
        assertNotNull(VendorRules.validateFood(good().copy(priceText = "12.345")).price)
        assertNull(VendorRules.validateFood(good().copy(priceText = "12.50")).price)
        assertNotNull(VendorRules.validateFood(good().copy(priceText = "NaN")).price)
        assertNotNull(VendorRules.validateFood(good().copy(categoryId = "")).category)
        assertNotNull(VendorRules.validateFood(good().copy(prepTimeText = "-1")).prep)
        assertNotNull(VendorRules.validateFood(good().copy(imageUrl = "http://x.test/a.png")).image)
        assertNull(VendorRules.validateFood(good().copy(imageUrl = "https://x.test/a.png")).image)
        assertNotNull(VendorRules.validateFood(good().copy(outletId = "")).outlet)
    }

    @Test fun `stock status including the untracked seed value`() {
        assertEquals(StockStatus.NOT_TRACKED, VendorRules.stockStatus(1_000_000, 5))
        assertEquals(StockStatus.OUT_OF_STOCK, VendorRules.stockStatus(0, 5))
        assertEquals(StockStatus.LOW, VendorRules.stockStatus(5, 5))
        assertEquals(StockStatus.OK, VendorRules.stockStatus(6, 5))
        assertNull(VendorRules.validateStock("0")); assertNotNull(VendorRules.validateStock("-1")); assertNotNull(VendorRules.validateStock("100000")); assertNotNull(VendorRules.validateStock("x"))
    }

    @Test fun `slot status thresholds match place_order`() {
        assertEquals(SlotStatus.AVAILABLE, VendorRules.slotStatus(7, 10))
        assertEquals(SlotStatus.LIMITED, VendorRules.slotStatus(8, 10))
        assertEquals(SlotStatus.FULL, VendorRules.slotStatus(10, 10))
    }

    @Test fun `new slot rules - past, order, capacity, duplicates and overlaps`() {
        fun l(t: String): LocalTime = LocalTime.parse(t)
        assertNull(VendorRules.validateNewSlot(today, l("12:00"), l("12:30"), 10, emptyList(), now))
        assertNotNull(VendorRules.validateNewSlot(today, l("12:30"), l("12:00"), 10, emptyList(), now))
        assertNotNull(VendorRules.validateNewSlot(today, l("09:00"), l("09:30"), 10, emptyList(), now))        // already passed
        assertNotNull(VendorRules.validateNewSlot(today, l("09:50"), l("10:30"), 10, emptyList(), now))        // started already
        assertNotNull(VendorRules.validateNewSlot(today.minusDays(1), l("12:00"), l("12:30"), 10, emptyList(), now))
        assertNull(VendorRules.validateNewSlot(today.plusDays(1), l("09:00"), l("09:30"), 10, emptyList(), now))
        assertNotNull(VendorRules.validateNewSlot(today, l("12:00"), l("12:30"), 0, emptyList(), now))
        assertNotNull(VendorRules.validateNewSlot(today, l("12:00"), l("12:30"), 501, emptyList(), now))
        assertNotNull(VendorRules.validateNewSlot(today, l("12:00"), l("12:30"), 10, listOf(slot()), now))      // same start
        assertNotNull(VendorRules.validateNewSlot(today, l("12:15"), l("12:45"), 10, listOf(slot()), now))      // overlap
        assertNull(VendorRules.validateNewSlot(today, l("12:30"), l("13:00"), 10, listOf(slot()), now))        // adjacent is fine
    }

    @Test fun `capacity changes never go below bookings and past slots are frozen`() {
        assertNull(VendorRules.validateCapacityChange(slot(booked = 4), 4, now))
        assertNotNull(VendorRules.validateCapacityChange(slot(booked = 4), 3, now))
        assertNotNull(VendorRules.validateCapacityChange(slot(date = today.minusDays(1)), 20, now))
        assertNotNull(VendorRules.validateCapacityChange(slot(s = "08:00", e = "09:00"), 20, now))               // ended earlier today
        assertNotNull(VendorRules.canDeleteSlot(slot(booked = 1), now))
        assertNull(VendorRules.canDeleteSlot(slot(booked = 0), now))
    }

    @Test fun `outlet hours and phone`() {
        assertNull(VendorRules.validateHours("08:00", "22:00")); assertNotNull(VendorRules.validateHours("22:00", "08:00")); assertNotNull(VendorRules.validateHours("8am", "22:00"))
        assertNull(VendorRules.validatePhone("")); assertNull(VendorRules.validatePhone("+91 98765 43210")); assertNotNull(VendorRules.validatePhone("abc"))
    }

    @Test fun `vendor error messages are friendly`() {
        assertTrue(vendorErrorMessage(ConflictException("Stock changed")).contains("Stock changed"))
        assertTrue(vendorErrorMessage(Exception("duplicate key value violates unique constraint")).contains("already exists"))
        assertTrue(vendorErrorMessage(Exception("new row violates row-level security policy")).contains("permission"))
        assertTrue(vendorErrorMessage(java.io.IOException("Unable to resolve host")).contains("connection"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VendorViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val outlet = VendorOutlet("o1", "Cafe", "", "", true, true, OutletOrderingStatus.ACTIVE, "08:00", "22:00", emptyList(), "", "", "")
    private fun row(q: Int) = VendorInventoryRow("i1", "f1", "Samosa", "o1", q, 5, true, "t")

    /** Scripted repository; only the methods under test are overridden. */
    private open class FakeRepo : VendorCatalogRepository by org.mockito.kotlin.mock() {
        var inventory: Result<List<VendorInventoryRow>> = Result.success(emptyList())
        var setStockResult: Result<Unit> = Result.success(Unit)
        val setStockCalls = mutableListOf<Triple<Int, Int, Int>>()
        var saveFoodResult: Result<String> = Result.success("new-id")
        var saveCalls = 0
        override suspend fun getInventory() = inventory
        override suspend fun setStock(inventoryId: String, expectedQuantity: Int, newQuantity: Int, lowStockThreshold: Int): Result<Unit> {
            setStockCalls += Triple(expectedQuantity, newQuantity, lowStockThreshold); return setStockResult
        }
        override suspend fun getMyOutlets(): Result<List<VendorOutlet>> = Result.success(listOf(VendorOutlet("o1", "Cafe", "", "", true, true, OutletOrderingStatus.ACTIVE, "08:00", "22:00", emptyList(), "", "", "")))
        override suspend fun getCategories(): Result<List<FoodCategory>> = Result.success(listOf(FoodCategory("c1", "Snacks", "")))
        override suspend fun saveFood(draft: VendorFoodDraft): Result<String> { saveCalls++; return saveFoodResult }
        override suspend fun getSlots(outletId: String, date: LocalDate): Result<List<VendorSlot>> = Result.success(emptyList())
    }

    @Test fun `inventory edit sends the value it displayed as the expected quantity`() = runTest(dispatcher) {
        val repo = FakeRepo().apply { inventory = Result.success(listOf(row(10))) }
        val vm = VendorInventoryViewModel(repo); advanceUntilIdle()
        vm.startEdit(vm.state.value.rows.single()); vm.setQty("25"); vm.setLow("3"); vm.saveEdit(); advanceUntilIdle()
        assertEquals(Triple(10, 25, 3), repo.setStockCalls.single())
    }

    @Test fun `a stock conflict is reported and the list reloaded`() = runTest(dispatcher) {
        val repo = FakeRepo().apply { inventory = Result.success(listOf(row(10))); setStockResult = Result.failure(ConflictException("Stock changed while you were editing")) }
        val vm = VendorInventoryViewModel(repo); advanceUntilIdle()
        repo.inventory = Result.success(listOf(row(7)))   // a student ordered 3
        vm.startEdit(vm.state.value.rows.single()); vm.setQty("25"); vm.saveEdit(); advanceUntilIdle()
        assertTrue(vm.state.value.message!!.contains("Stock changed"))
        assertEquals(7, vm.state.value.rows.single().quantity)
        assertNull(vm.state.value.editing)
    }

    @Test fun `invalid stock never reaches the repository and double taps write once`() = runTest(dispatcher) {
        val repo = FakeRepo().apply { inventory = Result.success(listOf(row(10))) }
        val vm = VendorInventoryViewModel(repo); advanceUntilIdle()
        vm.startEdit(vm.state.value.rows.single()); vm.setQty(""); vm.saveEdit(); advanceUntilIdle()
        assertTrue(repo.setStockCalls.isEmpty())
        vm.setQty("5"); vm.saveEdit(); vm.saveEdit(); advanceUntilIdle()
        assertEquals(1, repo.setStockCalls.size)
    }

    @Test fun `untracked seed stock opens the editor empty`() = runTest(dispatcher) {
        val repo = FakeRepo().apply { inventory = Result.success(listOf(row(1_000_000))) }
        val vm = VendorInventoryViewModel(repo); advanceUntilIdle()
        vm.startEdit(vm.state.value.rows.single())
        assertEquals("", vm.state.value.qtyText)
    }

    @Test fun `inventory load failure shows an error and a refresh failure keeps the last list`() = runTest(dispatcher) {
        val repo = FakeRepo().apply { inventory = Result.failure(java.io.IOException("offline")) }
        val vm = VendorInventoryViewModel(repo); advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        repo.inventory = Result.success(listOf(row(10))); vm.load(); advanceUntilIdle()
        repo.inventory = Result.failure(java.io.IOException("offline")); vm.load(); advanceUntilIdle()
        assertEquals(1, vm.state.value.rows.size); assertNull(vm.state.value.error); assertNotNull(vm.state.value.message)
    }

    @Test fun `food form validates before saving and saves once`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = VendorFoodEditViewModel(repo, androidx.lifecycle.SavedStateHandle(mapOf("foodId" to NEW_FOOD_ID))); advanceUntilIdle()
        vm.save(); advanceUntilIdle()
        assertEquals(0, repo.saveCalls); assertTrue(vm.state.value.showErrors)
        vm.edit { it.copy(name = "Samosa", priceText = "20", categoryId = "c1") }
        vm.save(); vm.save(); advanceUntilIdle()
        assertEquals(1, repo.saveCalls)
        assertTrue(vm.state.value.savedMessage!!.contains("Inventory"))
    }

    @Test fun `a failed save is surfaced and nothing is closed`() = runTest(dispatcher) {
        val repo = FakeRepo().apply { saveFoodResult = Result.failure(SecurityException("Nothing was saved: the item wasn't found or isn't yours.")) }
        val vm = VendorFoodEditViewModel(repo, androidx.lifecycle.SavedStateHandle(mapOf("foodId" to NEW_FOOD_ID))); advanceUntilIdle()
        vm.edit { it.copy(name = "Samosa", priceText = "20", categoryId = "c1") }
        vm.save(); advanceUntilIdle()
        assertTrue(vm.state.value.saveError!!.contains("Nothing was saved")); assertNull(vm.state.value.savedMessage); assertFalse(vm.state.value.saving)
    }

    @Test fun `slot dialog rejects a past or overlapping slot before calling the server`() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = VendorOutletViewModel(repo); advanceUntilIdle()
        vm.setDate(VendorRules.todayIst().minusDays(1)); advanceUntilIdle()
        vm.openNewSlot(); vm.submitDialog(); advanceUntilIdle()
        assertTrue(vm.state.value.dialog!!.error!!.contains("passed"))
    }
}
