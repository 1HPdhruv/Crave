package com.srmfood.gag.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.srmfood.gag.BuildConfig
import com.srmfood.gag.core.di.SupabaseModule
import com.srmfood.gag.data.local.dao.FoodItemDao
import com.srmfood.gag.data.local.dao.OutletDao
import com.srmfood.gag.data.repository.supabase.SupabaseFoodRepository
import com.srmfood.gag.data.repository.supabase.SupabaseOutletRepository
import com.srmfood.gag.domain.model.FoodSearchFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

/**
 * Manual integration test to verify Supabase catalogue integration.
 * This runs directly against the environment specified in BuildConfig (Staging/Prod).
 */
@RunWith(AndroidJUnit4::class)
class ManualSupabaseIntegrationTest {

    private lateinit var outletRepository: SupabaseOutletRepository
    private lateinit var foodRepository: SupabaseFoodRepository

    @Before
    fun setup() {
        // Initialize real Supabase client
        val client = SupabaseModule.provideSupabaseClient()
        val postgrest = SupabaseModule.provideSupabasePostgrest(client)
        val auth = SupabaseModule.provideSupabaseAuth(client)
        
        // Mock the local DAOs since we are testing remote integration
        val mockOutletDao = mock(OutletDao::class.java)
        val mockFoodDao = mock(FoodItemDao::class.java)

        outletRepository = SupabaseOutletRepository(postgrest, mockOutletDao)
        foodRepository = SupabaseFoodRepository(postgrest, auth, mockFoodDao)
    }

    @Test
    fun verifySupabaseInitialization() {
        assertNotNull(BuildConfig.SUPABASE_URL)
        assertNotEquals("", BuildConfig.SUPABASE_URL)
        assertFalse(BuildConfig.USE_MOCK)
    }

    @Test
    fun testRealOutletQuery() = runBlocking {
        val result = outletRepository.refreshOutlets()
        assertTrue("Outlet query should succeed", result.isSuccess)
        
        val outlets = result.getOrNull()
        assertNotNull("Outlets list should not be null", outlets)
        // Check for at least 1 outlet in the live DB
        assertTrue("Live DB should have at least 1 active outlet", outlets!!.isNotEmpty())
        
        val firstOutlet = outlets.first()
        assertNotNull("Outlet ID shouldn't be null", firstOutlet.id)
        assertNotNull("Outlet name shouldn't be null", firstOutlet.name)
        // Verify we map location info even if there is no direct location column
        assertNotNull("Outlet location building shouldn't be null", firstOutlet.location.building)
    }

    @Test
    fun testRealFoodQuery() = runBlocking {
        val filter = FoodSearchFilter(query = "", availableOnly = true)
        val result = foodRepository.searchFood(filter)
        
        assertTrue("Food search query should succeed", result.isSuccess)
        
        val foodItems = result.getOrNull()
        assertNotNull("Food items list should not be null", foodItems)
        // Even if empty, it shouldn't crash. If not empty, verify mapping.
        if (foodItems!!.isNotEmpty()) {
            val food = foodItems.first()
            assertNotNull("Food name shouldn't be null", food.name)
            // Verify our fix for nullable arrays/booleans worked:
            assertNotNull("Ingredients shouldn't be null (should map to emptyList)", food.ingredients)
            assertNotNull("Tags shouldn't be null", food.tags)
        }
    }
}
