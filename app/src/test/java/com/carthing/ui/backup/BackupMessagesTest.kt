package com.carthing.ui.backup

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.carthing.R
import com.carthing.data.backup.InvalidBackupException
import com.carthing.data.backup.InvalidBackupException.Reason
import com.carthing.ui.common.UiText
import com.carthing.ui.common.resolve
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class BackupMessagesTest {
    private val res: Resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private fun text(reason: Reason, count: Int = 0) = InvalidBackupException(reason, count = count).toUiText().resolve(res)

    @Test fun everyReasonHasAMessage() {
        assertEquals("This isn't a CarThing backup file.", text(Reason.NOT_A_BACKUP))
        assertEquals("The backup file is damaged.", text(Reason.DAMAGED))
        assertEquals("The backup file is too large.", text(Reason.TOO_LARGE))
        assertEquals("This backup was made by a newer version of CarThing. Update the app first.", text(Reason.NEWER_VERSION))
    }

    @Test fun missingPhotosPluralize() {
        assertEquals("The backup file is damaged (1 photo is missing).", text(Reason.PHOTOS_MISSING, 1))
        assertEquals("The backup file is damaged (3 photos are missing).", text(Reason.PHOTOS_MISSING, 3))
    }

    @Test @Config(qualifiers = "cs") fun czechMissingPhotos() {
        assertEquals("Záložní soubor je poškozený (chybí 1 fotka).", text(Reason.PHOTOS_MISSING, 1))
        assertEquals("Záložní soubor je poškozený (chybí 3 fotky).", text(Reason.PHOTOS_MISSING, 3))
        assertEquals("Záložní soubor je poškozený (chybí 5 fotek).", text(Reason.PHOTOS_MISSING, 5))
    }

    @Test @Config(qualifiers = "sk") fun slovakMissingPhotos() {
        assertEquals("Záložný súbor je poškodený (chýba 1 fotka).", text(Reason.PHOTOS_MISSING, 1))
        assertEquals("Záložný súbor je poškodený (chýbajú 3 fotky).", text(Reason.PHOTOS_MISSING, 3))
        assertEquals("Záložný súbor je poškodený (chýba 5 fotiek).", text(Reason.PHOTOS_MISSING, 5))
    }

    @Test fun uiTextResolvesPlainAndNestedText() {
        assertEquals("Backup saved", UiText(R.string.backup_message_saved).resolve(res))
        assertEquals("Last backup: 2 days", UiText(R.string.backup_last, listOf(UiText.plural(R.plurals.days, 2))).resolve(res))
    }
}
