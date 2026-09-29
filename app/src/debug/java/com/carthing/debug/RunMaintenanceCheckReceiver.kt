package com.carthing.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.carthing.notifications.MaintenanceCheckWorker

/**
 * Debug builds only. Runs the maintenance reminder check immediately:
 * adb shell am broadcast -n com.carthing/.debug.RunMaintenanceCheckReceiver
 */
class RunMaintenanceCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = MaintenanceCheckWorker.runOnce(context)
}
