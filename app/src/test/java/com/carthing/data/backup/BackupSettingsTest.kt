package com.carthing.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)

    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun storedSentenceFromAnOlderVersionBecomesOther() {
        prefs.edit().putString("auto_error", "Can't write to the folder").commit()
        assertEquals(AutoBackupError.OTHER, BackupSettings(context).state.value.autoError)
    }

    @Test fun noStoredErrorIsNull() {
        assertNull(BackupSettings(context).state.value.autoError)
    }

    @Test fun errorCodeRoundTrips() {
        BackupSettings(context).recordAutoError(AutoBackupError.FOLDER_UNAVAILABLE)
        assertEquals("FOLDER_UNAVAILABLE", prefs.getString("auto_error", null))
        assertEquals(AutoBackupError.FOLDER_UNAVAILABLE, BackupSettings(context).state.value.autoError)
    }

    @Test fun successClearsTheError() {
        val settings = BackupSettings(context)
        settings.recordAutoError(AutoBackupError.OTHER)
        settings.recordBackup(1L)
        assertNull(BackupSettings(context).state.value.autoError)
    }
}
