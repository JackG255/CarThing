package com.carthing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.carthing.data.dao.FuelEntryDao
import com.carthing.data.dao.MaintenanceItemDao
import com.carthing.data.dao.ServiceEntryDao
import com.carthing.data.dao.VehicleDao
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle

@Database(
    entities = [Vehicle::class, FuelEntry::class, ServiceEntry::class, MaintenanceItem::class],
    version = 3,
    exportSchema = true
)
abstract class CarThingDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelEntryDao(): FuelEntryDao
    abstract fun serviceEntryDao(): ServiceEntryDao
    abstract fun maintenanceItemDao(): MaintenanceItemDao

    companion object {
        @Volatile private var instance: CarThingDatabase? = null
        fun get(context: Context): CarThingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, CarThingDatabase::class.java, "carthing.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
