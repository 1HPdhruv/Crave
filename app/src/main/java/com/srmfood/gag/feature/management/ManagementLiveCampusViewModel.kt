package com.srmfood.gag.feature.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srmfood.gag.domain.model.*
import com.srmfood.gag.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class LiveCampusUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True once at least one section loaded, so a later partial failure keeps the page and shows a banner. */
    val hasData: Boolean = false,
    
    // Order State
    val totalActiveOrders: Int = 0,
    val placedOrders: Int = 0,
    val acceptedOrders: Int = 0,
    val preparingOrders: Int = 0,
    val readyOrders: Int = 0,
    val recentActiveOrders: List<Order> = emptyList(),

    // Outlet State
    val totalOutlets: Int = 0,
    val openOutlets: Int = 0,
    val closedOutlets: Int = 0,
    val inactiveOutlets: Int = 0,
    val outletList: List<Outlet> = emptyList(),

    // Pickup State
    val totalSlotsToday: Int = 0,
    val totalCapacityToday: Int = 0,
    val totalBookedToday: Int = 0,
    val remainingCapacityToday: Int = 0,
    val fullSlotsCount: Int = 0,

    // Inventory State
    val totalTrackedItems: Int = 0,
    val outOfStockItems: Int = 0,
    val lowStockItems: Int = 0,
    val availableItems: Int = 0,
    
    // Alerts
    val alerts: List<String> = emptyList()
)

@HiltViewModel
class ManagementLiveCampusViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val outletRepository: OutletRepository,
    private val foodRepository: FoodRepository,
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveCampusUiState())
    val uiState: StateFlow<LiveCampusUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // A failed section must NOT be shown as zeros: it keeps its previous values and is reported.
                val failed = mutableListOf<String>()
                val previous = _uiState.value
                // Fetch Orders
                val ordersResult = orderRepository.getAdminOrders()
                if (ordersResult.isFailure) failed += "orders"
                val orders = ordersResult.getOrNull() ?: emptyList()
                
                val activeStatuses = listOf(
                    OrderStatus.PLACED, 
                    OrderStatus.ACCEPTED, 
                    OrderStatus.PREPARING, 
                    OrderStatus.READY
                )
                
                val activeOrders = orders.filter { it.status in activeStatuses }
                val placed = activeOrders.count { it.status == OrderStatus.PLACED }
                val accepted = activeOrders.count { it.status == OrderStatus.ACCEPTED }
                val preparing = activeOrders.count { it.status == OrderStatus.PREPARING }
                val ready = activeOrders.count { it.status == OrderStatus.READY }
                val recentQueue = activeOrders.sortedByDescending { it.createdAt }.take(5)

                // Fetch Outlets
                val outletsResult = outletRepository.getAdminOutlets()
                if (outletsResult.isFailure) failed += "outlets"
                val outlets = outletsResult.getOrNull() ?: emptyList()
                
                val openOutlets = outlets.count { it.isActive && it.isOpen }
                val closedOutlets = outlets.count { it.isActive && !it.isOpen }
                val inactiveOutlets = outlets.count { !it.isActive }

                // Fetch Pickup Slots Today
                val tz = TimeZone.getTimeZone("Asia/Kolkata")
                val cal = Calendar.getInstance(tz)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.timeZone = tz
                val todayStr = sdf.format(cal.time)
                
                val slotsResult = orderRepository.getAdminPickupSlots(todayStr)
                if (slotsResult.isFailure) failed += "pickup slots"
                val slots = slotsResult.getOrNull() ?: emptyList()
                
                val totalCapacity = slots.sumOf { it.capacity }
                val totalBooked = slots.sumOf { it.bookedCount }
                val remainingCapacity = totalCapacity - totalBooked
                val fullSlots = slots.count { (it.capacity - it.bookedCount) <= 0 }

                // Fetch Inventory
                val inventoryResult = adminRepository.getInventory()
                if (inventoryResult.isFailure) failed += "inventory"
                val inventoryItems = inventoryResult.getOrNull() ?: emptyList()
                
                val outOfStock = inventoryItems.count { it.stockStatus == com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem.StockStatus.OUT_OF_STOCK }
                val lowStock = inventoryItems.count { it.stockStatus == com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem.StockStatus.LOW_STOCK }
                val available = inventoryItems.count { it.stockStatus == com.srmfood.gag.domain.usecase.admin.ManagementInventoryItem.StockStatus.IN_STOCK }

                // Generate Alerts neutrally
                val generatedAlerts = mutableListOf<String>()
                val suspended = outlets.count { !it.orderingStatus.acceptsOrders && it.isActive }
                if (suspended > 0) generatedAlerts.add("$suspended outlets are suspended or deactivated for ordering.")
                if (closedOutlets > 0) generatedAlerts.add("$closedOutlets outlets are currently closed.")
                if (fullSlots > 0) generatedAlerts.add("$fullSlots pickup slots are full.")
                if (outOfStock > 0) generatedAlerts.add("$outOfStock tracked items are out of stock.")
                if (lowStock > 0) generatedAlerts.add("$lowStock tracked items are running low on stock.")

                val okO = "orders" !in failed; val okOu = "outlets" !in failed
                val okS = "pickup slots" !in failed; val okI = "inventory" !in failed
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        hasData = previous.hasData || failed.size < 4,
                        error = if (failed.isEmpty()) null else "Couldn't load ${failed.joinToString(", ")}. Those figures are not up to date.",
                        totalActiveOrders = if (okO) activeOrders.size else previous.totalActiveOrders,
                        placedOrders = if (okO) placed else previous.placedOrders,
                        acceptedOrders = if (okO) accepted else previous.acceptedOrders,
                        preparingOrders = if (okO) preparing else previous.preparingOrders,
                        readyOrders = if (okO) ready else previous.readyOrders,
                        recentActiveOrders = if (okO) recentQueue else previous.recentActiveOrders,
                        
                        totalOutlets = if (okOu) outlets.size else previous.totalOutlets,
                        openOutlets = if (okOu) openOutlets else previous.openOutlets,
                        closedOutlets = if (okOu) closedOutlets else previous.closedOutlets,
                        inactiveOutlets = if (okOu) inactiveOutlets else previous.inactiveOutlets,
                        outletList = if (okOu) outlets else previous.outletList,
                        
                        totalSlotsToday = if (okS) slots.size else previous.totalSlotsToday,
                        totalCapacityToday = if (okS) totalCapacity else previous.totalCapacityToday,
                        totalBookedToday = if (okS) totalBooked else previous.totalBookedToday,
                        remainingCapacityToday = if (okS) remainingCapacity else previous.remainingCapacityToday,
                        fullSlotsCount = if (okS) fullSlots else previous.fullSlotsCount,
                        
                        totalTrackedItems = if (okI) inventoryItems.size else previous.totalTrackedItems,
                        outOfStockItems = if (okI) outOfStock else previous.outOfStockItems,
                        lowStockItems = if (okI) lowStock else previous.lowStockItems,
                        availableItems = if (okI) available else previous.availableItems,
                        
                        alerts = if (failed.isEmpty()) generatedAlerts else previous.alerts
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to fetch live campus data.") }
            }
        }
    }
}
