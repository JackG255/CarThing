package com.carthing.data.dao

import androidx.room.Embedded
import com.carthing.data.entity.Vehicle

data class VehicleWithOdometer(
    @Embedded val vehicle: Vehicle,
    val currentOdometerKm: Double
)
