package com.alal.yft.feature.downloads

import com.alal.yft.download.BackgroundHealth
import com.alal.yft.download.BackgroundSystemState
import com.alal.yft.download.InMemoryBackgroundHealthStore
import com.alal.yft.download.isXiaomi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P34: when Downloads shows the battery and notifications cards (G4). */
class BackgroundCardsTest {
    private val limited = BackgroundSystemState(
        unrestricted = false,
        notificationsVisible = true,
        xiaomi = false,
    )
    private val started = BackgroundHealth(downloadStarted = true)

    @Test
    fun batteryCardShowsOnceADownloadStartedWhileAndroidLimitsYft() {
        val card = backgroundCards(limited, started).batteryCard

        assertNotNull(card)
        assertTrue(card!!.canAllow)
        assertNull(card.freezeMessage)
        assertFalse(card.xiaomiSteps)
        assertNull(backgroundCards(limited, BackgroundHealth()).batteryCard)
        assertNull(backgroundCards(limited.copy(unrestricted = true), started).batteryCard)
        assertNull(backgroundCards(limited, started.copy(batteryCardHidden = true)).batteryCard)
    }

    @Test
    fun xiaomiStepsShowOnlyOnXiaomiPhones() {
        val xiaomi = backgroundCards(limited.copy(xiaomi = true), started).batteryCard

        assertTrue(xiaomi!!.xiaomiSteps)
        assertTrue(isXiaomi("Xiaomi"))
        assertTrue(isXiaomi("Redmi"))
        assertTrue(isXiaomi("POCO"))
        assertFalse(isXiaomi("samsung"))
        assertFalse(isXiaomi(null))
    }

    @Test
    fun aFreezeBringsTheCardBackWithHowLongYftWasPaused() {
        val store = InMemoryBackgroundHealthStore()
        store.markDownloadStarted()
        store.hideBatteryCard()
        assertNull(backgroundCards(limited, store.health.value).batteryCard)

        store.recordFreeze(100_000)

        val card = backgroundCards(limited, store.health.value).batteryCard
        assertEquals("Your phone paused YFT in the background for 1 min 40 s.", card?.freezeMessage)
        // Even with battery limits lifted, a phone that froze YFT still gets the card.
        val unlimited = limited.copy(unrestricted = true)
        assertFalse(backgroundCards(unlimited, store.health.value).batteryCard!!.canAllow)
        assertEquals(1, store.health.value.freezeCount)
    }

    @Test
    fun withTheBatteryCardOffOnlyAFreezeShowsIt() {
        assertNull(backgroundCards(limited, started, batteryCard = false).batteryCard)
        assertNotNull(
            backgroundCards(
                limited,
                started.copy(lastFreezeLostMs = 30_000, freezeCount = 1),
                batteryCard = false,
            ).batteryCard,
        )
    }

    @Test
    fun notificationsCardShowsWhileNotificationsAreOffUntilDismissed() {
        val off = limited.copy(notificationsVisible = false)
        val store = InMemoryBackgroundHealthStore()

        assertTrue(backgroundCards(off, store.health.value).notificationsCard)
        assertFalse(backgroundCards(limited, store.health.value).notificationsCard)
        store.hideNotificationsCard()
        assertFalse(backgroundCards(off, store.health.value).notificationsCard)
        store.resetNotificationsCard()
        assertTrue(backgroundCards(off, store.health.value).notificationsCard)
    }

    @Test
    fun settingsRowSaysAllowedOrLimited() {
        assertEquals("Limited", backgroundDownloadsValue(limited))
        assertEquals("Allowed", backgroundDownloadsValue(BackgroundSystemState.Unlimited))
    }
}
