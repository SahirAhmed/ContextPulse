package com.contextpulse.phase0

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.contextpulse.phase0.model.ContextTimeframeMode
import com.contextpulse.phase0.model.RoomProfile
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.security.AppLockManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomProfileRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("contextpulse_room_profiles_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("contextpulse_security_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun seedsDefaultRoomsAndPersistsProfileUpdatesAcrossInstances() {
        val repo1 = RoomProfileRepository(context)
        val rooms = repo1.rooms.value

        assertEquals(4, rooms.size)
        val bedroom = rooms.first { it.name == "Bedroom" }
        assertTrue(bedroom.profile.dndEnabled)
        assertEquals(25, bedroom.profile.brightnessPercent)

        val updatedModes = bedroom.profile.normalizedModes().map { mode ->
            if (mode.isDefault) {
                mode.copy(
                    dndEnabled = false,
                    brightnessEnabled = true,
                    brightnessPercent = 88,
                    appRestrictionsEnabled = true,
                    blockedApps = listOf("com.instagram.android", "com.zhiliaoapp.musically")
                )
            } else {
                mode
            }
        }
        val updatedProfile = bedroom.profile.copy(modes = updatedModes).syncFromModes()
        repo1.updateRoomProfile(bedroom.id, updatedProfile)
        repo1.setActiveRoom(bedroom.id)

        val repo2 = RoomProfileRepository(context)
        val reloadedBedroom = repo2.getRoomById(bedroom.id)
        assertNotNull(reloadedBedroom)
        assertTrue(reloadedBedroom!!.isActive)
        assertFalse(reloadedBedroom.profile.dndEnabled)
        assertEquals(88, reloadedBedroom.profile.brightnessPercent)
        assertTrue(reloadedBedroom.profile.appRestrictionsEnabled)
        assertEquals(2, reloadedBedroom.profile.blockedApps.size)
        assertEquals(bedroom.id, repo2.activeRoom.value?.id)

        repo2.setActiveRoom(null)
        assertNull(repo2.activeRoom.value)
    }

    @Test
    fun deletingAndReAddingContextCreatesCompletelyCleanProfileWithoutOldModes() {
        val repo = RoomProfileRepository(context)
        val oldBedroom = repo.rooms.value.first { it.name == "Bedroom" }
        assertTrue(oldBedroom.profile.normalizedModes().size > 1)

        // Delete Bedroom and add a brand-new Bedroom context
        repo.deleteRoom(oldBedroom.id)
        val newBedroom = repo.addRoom("Bedroom")

        // Brand-new context must only have 1 blank Default Mode and no old Custom Modes
        val modes = newBedroom.profile.normalizedModes()
        assertEquals(1, modes.size)
        assertTrue(modes.first().isDefault)
        assertFalse(modes.first().dndEnabled)
        assertFalse(modes.first().brightnessEnabled)
        assertFalse(modes.first().appRestrictionsEnabled)
    }

    @Test
    fun disablingCustomModeFallsBackToDefaultMode() {
        val customMode = ContextTimeframeMode(
            id = "custom_work",
            name = "Work Focus",
            isDefault = false,
            isEnabled = true,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            daysOfWeek = setOf(Calendar.MONDAY)
        )
        val defaultMode = ContextTimeframeMode(
            id = "default_mode",
            name = "Default",
            isDefault = true,
            isEnabled = true
        )
        val profileEnabled = RoomProfile(
            spaceName = "Study",
            modes = listOf(defaultMode, customMode)
        )
        // At 10:00 AM on Monday, enabled Custom Mode takes priority over Default Mode
        val resolvedWhenEnabled = profileEnabled.resolveActiveMode(10 * 60, Calendar.MONDAY)
        assertEquals("custom_work", resolvedWhenEnabled.id)

        // When Custom Mode is toggled off (isEnabled = false), Default Mode applies
        val profileDisabled = RoomProfile(
            spaceName = "Study",
            modes = listOf(defaultMode, customMode.copy(isEnabled = false))
        )
        val resolvedWhenDisabled = profileDisabled.resolveActiveMode(10 * 60, Calendar.MONDAY)
        assertEquals("default_mode", resolvedWhenDisabled.id)
    }

    @Test
    fun recoveryCodeVerificationDisablesAppLock() {
        val lockManager = AppLockManager.getInstance(context)
        val recoveryCode = lockManager.setupPinAndGenerateRecoveryCode("123456")
        assertNotNull(recoveryCode)
        assertEquals(16, recoveryCode!!.length)
        assertTrue(lockManager.isLockEnabled.value)
        assertTrue(lockManager.verifyPin("123456"))

        // Using the 16-digit recovery code disables App Lock
        val recovered = lockManager.verifyRecoveryCodeAndDisableLock(recoveryCode)
        assertTrue(recovered)
        assertFalse(lockManager.isLockEnabled.value)
    }
}
