package com.carthing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.carthing.notifications.MaintenanceCheckWorker
import com.carthing.notifications.MaintenanceNotifier
import com.carthing.ui.CarThingNavHost
import com.carthing.ui.CarThingTheme
import com.carthing.ui.LanguageSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Vehicle to open from a tapped notification; cleared once navigated. */
    private var openVehicleId by mutableStateOf<Long?>(null)

    // Android 13+ applies the per-app language itself; older phones get the in-app choice here.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LanguageSettings(newBase).wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MaintenanceNotifier.ensureChannel(this)
        MaintenanceCheckWorker.schedule(this)
        // A per-app language change recreates the activity, so this sees it.
        lifecycleScope.launch { (application as CarThingApp).container.relabeler.relabelIfLanguageChanged() }
        // On recreation the back stack is restored, so only handle the launch intent the first time.
        if (savedInstanceState == null) handle(intent)
        setContent {
            CarThingTheme { CarThingNavHost(openVehicleId, onOpenedVehicle = { openVehicleId = null }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getLongExtra(MaintenanceNotifier.EXTRA_VEHICLE_ID, 0L)?.takeIf { it != 0L }?.let { openVehicleId = it }
    }
}
