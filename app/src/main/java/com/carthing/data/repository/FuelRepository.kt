package com.carthing.data.repository

import com.carthing.data.dao.FuelEntryDao
import com.carthing.data.dao.VehicleDao
import com.carthing.data.entity.FuelEntry
import kotlinx.coroutines.flow.Flow

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    /** Nothing was written. If [issues] are all warnings, retry with `acceptWarnings = true` after the user confirms. */
    data class Rejected(val issues: List<FuelIssue>) : SaveResult
}

class FuelRepository(private val dao: FuelEntryDao, private val vehicleDao: VehicleDao) {
    fun observeForVehicle(vehicleId: Long): Flow<List<FuelEntry>> = dao.observeForVehicle(vehicleId)

    suspend fun save(entry: FuelEntry, acceptWarnings: Boolean = false): SaveResult {
        val vehicle = requireNotNull(vehicleDao.getById(entry.vehicleId)) { "Unknown vehicle ${entry.vehicleId}" }
        val issues = FuelValidation.validate(entry, vehicle, dao.getForVehicle(entry.vehicleId))
        if (issues.any { it.isError } || (issues.isNotEmpty() && !acceptWarnings)) return SaveResult.Rejected(issues)
        val id = dao.upsert(entry)
        // @Upsert returns -1 when it updated an existing row.
        return SaveResult.Saved(if (id == -1L) entry.id else id)
    }

    suspend fun delete(entry: FuelEntry) = dao.delete(entry)
}
