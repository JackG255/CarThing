package com.carthing.data.repository

import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelValidationTest {
    private val vehicle = Vehicle(id = 1, name = "Car", initialOdometerKm = 500.0)
    private fun fill(id: Long, date: Long, odo: Double, liters: Double = 30.0, full: Boolean = true) =
        FuelEntry(id = id, vehicleId = 1, dateEpochMillis = date, odometerKm = odo, liters = liters, isFullTank = full)

    private val existing = listOf(fill(1, date = 100, odo = 1000.0), fill(2, date = 200, odo = 1500.0))

    @Test fun validEntryHasNoIssues() {
        assertTrue(FuelValidation.validate(fill(0, 300, 2000.0), vehicle, existing).isEmpty())
    }

    @Test fun nonPositiveLitersIsError() {
        val issues = FuelValidation.validate(fill(0, 300, 2000.0, liters = 0.0), vehicle, existing)
        assertEquals(listOf(FuelIssue.NonPositiveLiters), issues)
    }

    @Test fun belowInitialOdometerIsError() {
        val issues = FuelValidation.validate(fill(0, 50, 400.0), vehicle, emptyList())
        assertEquals(listOf(FuelIssue.BelowInitialOdometer(500.0)), issues)
    }

    @Test fun lowerOdometerThanEarlierEntryIsError() {
        val issues = FuelValidation.validate(fill(0, 300, 1200.0), vehicle, existing)
        assertEquals(listOf(FuelIssue.OdometerOutOfOrder(existing[1])), issues)
    }

    @Test fun backdatedEntryHigherThanLaterEntryIsError() {
        val issues = FuelValidation.validate(fill(0, 150, 1600.0), vehicle, existing)
        assertEquals(listOf(FuelIssue.OdometerOutOfOrder(existing[1])), issues)
    }

    @Test fun backdatedEntryBetweenNeighboursIsValid() {
        assertTrue(FuelValidation.validate(fill(0, 150, 1200.0), vehicle, existing).isEmpty())
    }

    @Test fun sameTimestampDifferentOdometerIsValid() {
        assertTrue(FuelValidation.validate(fill(0, 200, 1800.0), vehicle, existing).isEmpty())
    }

    @Test fun duplicateFullTankIsError() {
        val issues = FuelValidation.validate(fill(0, 300, 1500.0), vehicle, existing)
        assertEquals(listOf(FuelIssue.DuplicateFullTank(existing[1])), issues)
        assertTrue(issues.single().isError)
    }

    @Test fun sameOdometerPartialTopOffIsWarning() {
        val issue = FuelValidation.validate(fill(0, 300, 1500.0, full = false), vehicle, existing).single()
        assertEquals(FuelIssue.SameOdometer(existing[1]), issue)
        assertTrue(!issue.isError)
    }

    @Test fun editingEntryIgnoresItself() {
        val edited = existing[1].copy(liters = 32.0)
        assertTrue(FuelValidation.validate(edited, vehicle, existing).isEmpty())
    }
}
