package com.srmfood.gag.data.repository.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.srmfood.gag.domain.repository.HostelAddress
import com.srmfood.gag.domain.repository.OrderingMode
import com.srmfood.gag.domain.repository.OrderingModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [OrderingModeRepository].
 *
 * Reuses the SAME DataStore instance provided by [DataStoreModule] so
 * there is one file on disk, not two.  Keys are namespaced with a
 * "ordering_" prefix to avoid collisions with existing preference keys.
 */
@Singleton
class OrderingModeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : OrderingModeRepository {

    private object Keys {
        val MODE     = stringPreferencesKey("ordering_mode")
        val HOSTEL   = stringPreferencesKey("ordering_hostel")
        val BLOCK    = stringPreferencesKey("ordering_block")
        val ROOM     = stringPreferencesKey("ordering_room")
        val NOTES    = stringPreferencesKey("ordering_notes")
    }

    override val orderingMode: Flow<OrderingMode> = dataStore.data.map { prefs ->
        when (prefs[Keys.MODE]) {
            OrderingMode.DELIVERY.name -> OrderingMode.DELIVERY
            else -> OrderingMode.PICKUP          // Default: Pickup
        }
    }

    override val hostelAddress: Flow<HostelAddress> = dataStore.data.map { prefs ->
        HostelAddress(
            hostel = prefs[Keys.HOSTEL] ?: "",
            block  = prefs[Keys.BLOCK]  ?: "",
            room   = prefs[Keys.ROOM]   ?: "",
            notes  = prefs[Keys.NOTES]  ?: ""
        )
    }

    override suspend fun setOrderingMode(mode: OrderingMode) {
        dataStore.edit { prefs -> prefs[Keys.MODE] = mode.name }
    }

    override suspend fun setHostelAddress(address: HostelAddress) {
        dataStore.edit { prefs ->
            prefs[Keys.HOSTEL] = address.hostel
            prefs[Keys.BLOCK]  = address.block
            prefs[Keys.ROOM]   = address.room
            prefs[Keys.NOTES]  = address.notes
        }
    }
}
