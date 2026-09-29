package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val vin: String? = null,
    val licensePlate: String? = null,
    /** Odometer at the time the vehicle was added, in km. */
    val initialOdometerKm: Double = 0.0
)
