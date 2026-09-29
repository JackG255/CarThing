package com.carthing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.carthing.data.dao.FuelEntryDao
import com.carthing.data.dao.ServiceEntryDao
import com.carthing.data.dao.VehicleDao
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle

@Database(
    entities = [Vehicle::class, FuelEntry::class, ServiceEntry::class],
    version = 1,
    exportSchema = true
)
abstract class CarThingDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelEntryDao(): FuelEntryDao
    abstract fun serviceEntryDao(): ServiceEntryDao

    companion object {
        @Volatile private var instance: CarThingDatabase? = null
        fun get(context: Context): CarThingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, CarThingDatabase::class.java, "carthing.db"
                ).build().also { instance = it }
            }
    }
}
